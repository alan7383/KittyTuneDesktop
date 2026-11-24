# SPDX-License-Identifier: OFL-1.1
"""Build the bundled KittyTune Flex TTF with Python 3.10+ and fonttools 4.68.

Run python scripts/fonts/build_cyrillic.py from the checkout. The unmodified
Google Sans Flex 3.007 is the input to this build.
"""

import argparse
from copy import deepcopy
from pathlib import Path

from fontTools.misc.fixedTools import otRound
from fontTools.otlLib import builder as layout
from fontTools.ttLib import TTFont
from fontTools.ttLib.tables import _g_l_y_f as glyf, otTables
from fontTools.ttLib.tables.TupleVariation import TupleVariation
from fontTools.ttLib.tables.ttProgram import Program
from fontTools.varLib.builder import buildVarDevTable
from fontTools.varLib.models import VariationModel, normalizeLocation
from fontTools.varLib.varStore import NO_VARIATION_INDEX, OnlineVarStoreBuilder

from cyrillic_design import ENCLOSING, HOMOGLYPHS, combining, coverage, draw_design


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "scripts/fonts/google_sans_flex_original.ttf"
OUTPUT = ROOT / "src/main/resources/fonts/kittytune_flex.ttf"
RUBLE = 0x20BD


def master_locations():
    locations = [{}]
    for axis, values in (
            ("opsz", (6, 14, 36, 72, 144)), ("wdth", (25, 65, 125, 151)),
            ("wght", (1, 100, 250, 550, 700, 900, 1000)), ("GRAD", (100,)),
            ("ROND", (100,)), ("slnt", (-10,))):
        locations.extend({axis: value} for value in values)
    # Condensed and optical masters grow differently as weight increases.
    # Rounded terminals also depend on stem thickness.
    for weight in (1, 700, 900, 1000):
        for axis, values in (("wdth", (25, 151)), ("opsz", (6, 14, 144)),
                             ("ROND", (100,)), ("GRAD", (100,))):
            locations.extend({"wght": weight, axis: value} for value in values)
    for width in (25, 151):
        locations.extend({"wdth": width, axis: value} for axis, value in
                         (("slnt", -10), ("ROND", 100), ("opsz", 6), ("opsz", 144)))
    # The app uses rounded headlines at optical size 14, including Black.
    locations.extend({"wght": weight, "opsz": 14, "ROND": 100} for weight in (700, 900, 1000))
    locations.extend({"wght": 1000, "wdth": width, "ROND": 100} for width in (25, 151))
    return locations


def coordinates(outline):
    return glyf.GlyphCoordinates(
        (otRound(x), otRound(y)) for contour in outline.contours for x, y, _ in contour
    )


def add_glyph(font, name, outlines, model):
    default = outlines[0]
    drawing = glyf.Glyph()
    drawing.numberOfContours = len(default.contours)
    drawing.coordinates = coordinates(default)
    drawing.flags = bytearray(on for contour in default.contours for _, _, on in contour)
    drawing.flags[0] |= glyf.flagOverlapSimple
    end = -1
    drawing.endPtsOfContours = []
    for contour in default.contours:
        end += len(contour)
        drawing.endPtsOfContours.append(end)
    drawing.program = Program()
    drawing.program.fromBytecode([])
    drawing.recalcBounds(font["glyf"])
    font["glyf"][name] = drawing
    font["hmtx"][name] = otRound(default.advance), drawing.xMin

    master_coords = []
    for outline in outlines:
        assert [len(c) for c in outline.contours] == [len(c) for c in default.contours], name
        coords = coordinates(outline)
        # The right phantom point also carries the width for gvar renderers.
        coords.extend([(0, 0), (otRound(outline.advance), 0), (0, 0), (0, 0)])
        master_coords.append(coords)

    variations = []
    for support, delta in zip(model.supports[1:], model.getDeltas(master_coords)[1:]):
        delta.toInt()
        if any(x or y for x, y in delta):
            variation = TupleVariation(support, list(delta))
            variation.optimize(master_coords[0], drawing.endPtsOfContours)
            variations.append(variation)
    font["gvar"].variations[name] = variations


def append_var_store(target, source):
    """Append variation data without changing existing glyph or layout indices."""
    regions = target.VarRegionList.Region

    def key(region):
        return tuple((axis.StartCoord, axis.PeakCoord, axis.EndCoord) for axis in region.VarRegionAxis)

    indices = {key(region): i for i, region in enumerate(regions)}
    region_map = []
    for region in source.VarRegionList.Region:
        coord = key(region)
        if coord not in indices:
            indices[coord] = len(regions)
            regions.append(region)
        region_map.append(indices[coord])
    base = len(target.VarData) << 16
    for data in source.VarData:
        data.VarRegionIndex = [region_map[i] for i in data.VarRegionIndex]
        target.VarData.append(data)
    target.VarRegionList.RegionCount = len(regions)
    target.VarDataCount = len(target.VarData)
    return base


def width_variations(font, model, advances):
    table = font["HVAR"].table
    store = OnlineVarStoreBuilder(model.axisOrder)
    store.setModel(model)
    indices = {name: store.storeMasters(widths, round=otRound)[1]
               for name, widths in advances.items() if any(w != widths[0] for w in widths)}
    offset = append_var_store(table.VarStore, store.finish())
    table.AdvWidthMap.mapping.update({name: offset + index for name, index in indices.items()})
    table.AdvWidthMap.mapping.update({name: NO_VARIATION_INDEX for name in advances.keys() - indices.keys()})


def anchor_location(design, code, position):
    character = chr(code)
    l, bottom, r, top = design.final(character).bounds()
    if position == "enclosing":
        height = (bottom + top) / 2
    elif position == "below":
        height = min(0, bottom)
    else:
        height = top
    return (l + r) / 2, height


def layout_variations(font, model, designs, glyphs, added):
    table = font["GPOS"].table
    glyph_map = font.getReverseGlyphMap()
    lookups = table.LookupList.Lookup
    gdef = font["GDEF"].table
    store = OnlineVarStoreBuilder(model.axisOrder)
    store.setModel(model)
    offset = len(gdef.VarStore.VarData) << 16

    def varying(values):
        rounded = list(map(otRound, values))
        if all(value == rounded[0] for value in rounded):
            return rounded[0], None
        base, index = store.storeMasters(rounded, round=otRound)
        return base, buildVarDevTable(index + offset)

    def anchor(code, position):
        xs, ys = zip(*(anchor_location(design, code, position) for design in designs))
        x, x_device = varying(xs)
        y, y_device = varying(ys)
        return layout.buildAnchor(x, y, deviceX=x_device, deviceY=y_device)

    def replace_records(subtable, prefix, record_type, new_records):
        old_coverage = getattr(subtable, prefix + "Coverage")
        array = getattr(subtable, prefix + "Array")
        record_name = record_type + "Record"
        records = dict(zip(old_coverage.glyphs, getattr(array, record_name)))
        records.update(new_records)
        result = layout.buildCoverage(records, glyph_map)
        setattr(subtable, prefix + "Coverage", result)
        setattr(array, record_name, [records[name] for name in result.glyphs])
        setattr(array, record_type + "Count", len(records))

    top_codes = [code for code in added if combining(code) and chr(code) not in ENCLOSING]
    top_marks = {glyphs[code]: (0, layout.buildAnchor(0, 0)) for code in top_codes}
    mark_array = layout.buildMarkArray(top_marks, glyph_map)
    mark_names = layout.buildCoverage(top_marks, glyph_map).glyphs
    mark_records = dict(zip(mark_names, mark_array.MarkRecord))
    tagged = {glyphs[code]: 3 if combining(code) else 1 for code in added}
    gdef.GlyphClassDef.classDefs.update(tagged)

    for lookup in lookups:
        subtable = lookup.SubTable[0]
        if lookup.LookupType == 4:
            marks = set(subtable.MarkCoverage.glyphs)
            if not marks.intersection({"acutecomb", "uni0327"}):
                continue
            top = "acutecomb" in marks
            records = {}
            for code in added:
                if not combining(code):
                    record = otTables.BaseRecord()
                    record.BaseAnchor = [anchor(code, "above" if top else "below")]
                    records[glyphs[code]] = record
            replace_records(subtable, "Base", "Base", records)
            if top:
                replace_records(subtable, "Mark", "Mark", mark_records)
        if lookup.LookupType == 6 and "acutecomb" in subtable.Mark1Coverage.glyphs:
            replace_records(subtable, "Mark1", "Mark", mark_records)
            stack = {}
            for code in top_codes:
                record = otTables.Mark2Record()
                record.Mark2Anchor = [anchor(code, "above")]
                stack[glyphs[code]] = record
            replace_records(subtable, "Mark2", "Mark2", stack)

    def add_lookup(feature_tag, subtables):
        feature = next(f.Feature for f in table.FeatureList.FeatureRecord if f.FeatureTag == feature_tag)
        feature.LookupListIndex.append(len(lookups))
        feature.LookupCount = len(feature.LookupListIndex)
        lookups.append(layout.buildLookup(subtables))
        table.LookupList.LookupCount = len(lookups)

    enclosures = {glyphs[ord(char)]: (0, layout.buildAnchor(0, 0)) for char in ENCLOSING}
    enclosure_bases = {glyphs[code]: {0: anchor(code, "enclosing")} for code in glyphs if not combining(code)}
    circle = font.getBestCmap()[0x25CC]
    enclosure_bases[circle] = {0: layout.buildAnchor(600, 510)}
    add_lookup("mark", [layout.buildMarkBasePosSubtable(enclosures, enclosure_bases, glyph_map)])

    # Tighten the spaces made by the Cyrillic overhangs and diagonals.
    kern_pairs = {}
    for leading, trailing, adjustment in (
            ("ГҐ", "АаДдЛлОоСсЕеу", -106), ("УЎ", "АаДдЛлОоя", -81),
            ("Т", "ДдЛля", -119), ("Р", "ДЛЖ", -58), ("Ф", "ДЛ", -57),
            ("Л", "ТУ", -43)):
        for left in leading:
            for right in trailing:
                amount, device = varying([adjustment * d.scales[True] for d in designs])
                values = {"XAdvance": amount}
                if device is not None:
                    values["XAdvDevice"] = device
                kern_pairs[glyphs[ord(left)], glyphs[ord(right)]] = (layout.buildValue(values), None)
    add_lookup("kern", layout.buildPairPosGlyphs(kern_pairs, glyph_map))
    append_var_store(gdef.VarStore, store.finish())

    scripts = table.ScriptList.ScriptRecord
    cyrillic = otTables.ScriptRecord()
    cyrillic.ScriptTag = "cyrl"
    cyrillic.Script = deepcopy(next(record.Script for record in scripts if record.ScriptTag == "DFLT"))
    scripts.append(cyrillic)
    scripts.sort(key=lambda record: record.ScriptTag)
    table.ScriptList.ScriptCount = len(scripts)


def rename(font):
    for record in font["name"].names:
        if record.nameID in (0, 7, 8, 9, 11, 12, 13, 14):
            continue
        value = record.toUnicode().replace("Google Sans Flex", "KittyTune Flex").replace(
            "GoogleSansFlex", "KittyTuneFlex"
        ).replace(";GOOG;", ";KTTY;")
        if record.nameID == 10:
            value = "KittyTune Flex is a Cyrillic extension of Google Sans Flex 3.007."
        record.string = value
    font["OS/2"].achVendID = "KTTY"


def build(source, output):
    font = TTFont(source, recalcTimestamp=False)
    original_cmap = font.getBestCmap()
    codes = sorted(coverage() + [RUBLE])
    version = font["name"].getDebugName(5) or ""
    if (not version.startswith("Version 3.007;") or font["head"].unitsPerEm != 2000
            or original_cmap.keys() & set(codes)):
        raise ValueError("The build expects the original, Latin Google Sans Flex 3.007.")
    axes = {a.axisTag: (a.minValue, a.defaultValue, a.maxValue) for a in font["fvar"].axes}
    locations = master_locations()
    model = VariationModel([normalizeLocation(loc, axes) for loc in locations], axisOrder=list(axes))
    designs = [draw_design(font, loc) for loc in locations]

    glyphs, added = {}, {}
    for code in codes:
        latin = HOMOGLYPHS.get(chr(code))
        if latin:
            glyphs[code] = original_cmap[ord(latin)]
        else:
            glyphs[code] = added[code] = f"uni{code:04X}" if code <= 0xFFFF else f"u{code:05X}"
    font.setGlyphOrder(font.getGlyphOrder() + list(added.values()))
    for cmap in font["cmap"].tables:
        if cmap.isUnicode():
            cmap.cmap.update({code: name for code, name in glyphs.items() if code <= 0xFFFF or cmap.format == 12})

    advances = {}
    for code, name in added.items():
        outlines = [design.final(chr(code)) for design in designs]
        add_glyph(font, name, outlines, model)
        advances[name] = [otRound(outline.advance) for outline in outlines]
    width_variations(font, model, advances)
    layout_variations(font, model, designs, glyphs, added)
    rename(font)
    font["OS/2"].recalcUnicodeRanges(font)
    font["OS/2"].setCodePageRanges(font["OS/2"].getCodePageRanges() | {2})
    font.save(output)
    print(f"{output}: {len(codes)} new code points, {len(added)} drawn glyphs, {len(locations)} masters")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=SOURCE)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    args = parser.parse_args()
    build(args.source, args.output)
