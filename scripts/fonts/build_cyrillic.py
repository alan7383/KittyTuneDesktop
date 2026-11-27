# SPDX-License-Identifier: OFL-1.1
"""Build the bundled KittyTune Flex TTF using Python 3.10+, fontTools and NumPy.

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
from cyrillic_spacing import fit_cyrillic_text
from letter_clearance import build_clearance_kerning


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
                             ("ROND", (100,)), ("GRAD", (100,)), ("slnt", (-10,))):
            locations.extend({"wght": weight, axis: value} for value in values)
    for width in (25, 151):
        locations.extend({"wdth": width, axis: value} for axis, value in
                         (("slnt", -10), ("ROND", 100), ("opsz", 6), ("opsz", 14), ("opsz", 144), ("GRAD", 100)))
    for opsz in (6, 14, 144):
        locations.extend({"opsz": opsz, axis: value} for axis, value in
                         (("GRAD", 100), ("ROND", 100), ("slnt", -10)))
    locations.extend([{"GRAD": 100, "ROND": 100}, {"GRAD": 100, "slnt": -10}, {"ROND": 100, "slnt": -10}])
    # The app uses rounded headlines at optical size 14 across widths and weights.
    for weight in (700, 900, 1000):
        locations.append({"wght": weight, "opsz": 14, "ROND": 100})
    for width in (25, 151):
        locations.append({"opsz": 14, "wdth": width, "ROND": 100})
        for weight in (1, 700, 900, 1000):
            locations.append({"opsz": 14, "wdth": width, "wght": weight})
            locations.append({"opsz": 14, "wdth": width, "wght": weight, "ROND": 100})
            locations.append({"wdth": width, "wght": weight, "ROND": 100})
            locations.append({"wdth": width, "wght": weight, "slnt": -10})
    for opsz in (6, 144):
        for weight in (1, 700, 900, 1000):
            locations.append({"opsz": opsz, "wght": weight, "ROND": 100})
            locations.append({"opsz": opsz, "wght": weight, "slnt": -10})
        for width in (25, 151):
            locations.append({"opsz": opsz, "wdth": width, "ROND": 100})
            locations.append({"opsz": opsz, "wdth": width, "slnt": -10})
            for weight in (1, 1000):
                locations.append({"opsz": opsz, "wdth": width, "wght": weight})
                locations.append({"opsz": opsz, "wdth": width, "wght": weight, "ROND": 100})
                locations.append({"opsz": opsz, "wdth": width, "wght": weight, "slnt": -10})

    defaults = {"opsz": 18, "wdth": 100, "wght": 400, "GRAD": 0, "ROND": 0, "slnt": 0}
    deduped, seen = [], set()
    for loc in locations:
        clean = {k: v for k, v in loc.items() if v != defaults[k]}
        key = tuple(sorted(clean.items()))
        if key not in seen:
            seen.add(key)
            deduped.append(clean)
    return deduped


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


def _design_heights(design):
    metrics = getattr(design, "metrics", None)
    cap_h = getattr(design, "cap_h", None)
    if cap_h is None and metrics is not None:
        cap_h = getattr(metrics, "cap_h", None)
    if cap_h is None and hasattr(design, "profiles"):
        cap_h = design.profiles[True][0]
    if cap_h is None:
        cap_h = 1432.0

    x_h = getattr(design, "x_h", None)
    if x_h is None and metrics is not None:
        x_h = getattr(metrics, "x_h", None)
    if x_h is None and hasattr(design, "profiles"):
        x_h = design.profiles[False][0]
    if x_h is None:
        x_h = 1020.0
    return cap_h, x_h


_OPTICAL_ABOVE_FRACTION = {
    "Ґ": 0.40, "ґ": 0.40,
    "Г": 0.44, "Ѓ": 0.44, "г": 0.44, "ѓ": 0.44, "Ғ": 0.44, "ғ": 0.44, "Ӷ": 0.44, "ӷ": 0.44,
    "К": 0.45, "Ќ": 0.45, "к": 0.45, "ќ": 0.45, "Р": 0.45, "Ҏ": 0.45, "Б": 0.46, "Ь": 0.44, "ь": 0.44,
    "Д": 0.54, "д": 0.54, "Ъ": 0.54, "ъ": 0.54,
    "Л": 0.56, "л": 0.56, "Љ": 0.56, "љ": 0.56,
}


def anchor_location(design, code, position):
    character = chr(code)
    outline = design.final(character)
    l, bottom, r, top = outline.bounds()
    if combining(code):
        if position == "enclosing":
            height = (bottom + top) / 2
        elif position == "below":
            height = bottom
        else:
            height = top
        return (l + r) / 2, height

    cap_h, x_h = _design_heights(design)
    cap = character.isupper() or character == "\u1c89"
    ascender = not cap and character in "бфђћѣꙉꙋᲄᲆᲇ"
    ref_h = cap_h if (cap or ascender) else x_h
    if position == "enclosing":
        height = ref_h / 2
    elif position == "below":
        height = 0.0
    else:
        height = ref_h

    if position == "above" and hasattr(design, "anchor_x"):
        ax = design.anchor_x(character)
        if ax is not None:
            return ax, height

    slant = getattr(design, "slant", None)
    if slant is None:
        metrics = getattr(design, "metrics", None)
        slant = getattr(metrics, "slant", 0.0) if metrics is not None else 0.0
    if slant:
        ul, _, ur, _ = outline.transform(shear=-slant).bounds()
    else:
        ul, ur = l, r
    frac = _OPTICAL_ABOVE_FRACTION.get(character, 0.50) if position == "above" else 0.50
    return ul + frac * (ur - ul) + slant * height, height


def _kern_scale(amount, kind, loc, design):
    if loc is None:
        scale_w = design.scales[True] if hasattr(design, "scales") else 1.0
        return amount * scale_w
    wdth = loc.get("wdth", 100.0)
    wght = loc.get("wght", 400.0)
    opsz = loc.get("opsz", 18.0)
    slnt = loc.get("slnt", 0.0)
    text_factor = 1.0
    if kind.startswith("text_"):
        kind = kind.removeprefix("text_")
        # Condensed display masters already have tight bearings.
        display = max(0.0, (opsz - 18.0) / 126.0)
        condensed = max(0.0, (100.0 - wdth) / 75.0)
        text_factor = (1.0 - 0.74 * display) * (1.0 - 0.46 * condensed)

    if kind == "yi_pos":
        wd_s = (0.86 + 0.14 * (wdth - 25.0) / 75.0) if wdth < 100.0 else (1.0 - 0.15 * (wdth - 100.0) / 51.0)
        op_s = (0.80 + 0.20 * (opsz - 6.0) / 12.0) if opsz < 18.0 else (1.0 + 0.35 * (opsz - 18.0) / 126.0)
        wg_s = (0.75 + 0.25 * (wght - 1.0) / 399.0) if wght < 400.0 else (1.0 + 0.75 * (wght - 400.0) / 600.0)
        sl_s = 1.0 + 0.25 * (-slnt / 10.0)
    else:
        wd_s = (0.52 + 0.48 * (wdth - 25.0) / 75.0) if wdth < 100.0 else (1.0 + 0.45 * (wdth - 100.0) / 51.0)
        op_s = (0.82 + 0.18 * (opsz - 6.0) / 12.0) if opsz < 18.0 else (1.0 + 0.28 * (opsz - 18.0) / 126.0)
        if kind == "overhang":
            wg_s = (1.12 - 0.12 * (wght - 1.0) / 399.0) if wght < 400.0 else (1.0 - 0.24 * (wght - 400.0) / 600.0)
            sl_s = 1.0 - 0.12 * (-slnt / 10.0)
        else:
            wg_s = (0.96 + 0.04 * (wght - 1.0) / 399.0) if wght < 400.0 else (1.0 + 0.10 * (wght - 400.0) / 600.0)
            sl_s = 1.0 - 0.08 * (-slnt / 10.0)
    return amount * wd_s * op_s * wg_s * sl_s * text_factor


def _build_cyrillic_kern(font, glyphs, added, designs, locations, varying, text_indices):
    """Build the class rules and fit text pairs against their starting values."""
    added_names = set(added.values())
    orig_cmap = font.getBestCmap()

    def names_for(chars):
        result = []
        for ch in chars:
            gn = glyphs.get(ord(ch)) or orig_cmap.get(ord(ch))
            if gn:
                result.append(gn)
        return result

    left_map, right_map = {}, {}

    def assign(target, key, chars):
        for gn in names_for(chars):
            if gn not in target:
                target[gn] = key

    # Left (leading) optical classes: more specific shapes assigned first.
    assign(left_map, "CAP_YI", "Ї")
    assign(left_map, "CAP_TALL_STEM", "ЁЙЀЍЌӢӤ")
    assign(left_map, "CAP_GHE_UP", "Ґ")
    assign(left_map, "CAP_GAMMA", "ГЃҒӶҔ")
    assign(left_map, "CAP_T", "ТҬ")
    assign(left_map, "CAP_Y_BREVE", "Ў")
    assign(left_map, "CAP_Y", "УҮҰ")
    assign(left_map, "CAP_P", "РҎ")
    assign(left_map, "CAP_EF", "Ф")
    assign(left_map, "CAP_BE", "Б")
    assign(left_map, "CAP_B", "В")
    assign(left_map, "CAP_O", "ОӨҨЮѲѺѼꙨꙪꚚ")
    assign(left_map, "CAP_ZE", "ЗӞ")
    assign(left_map, "CAP_E_REV", "ЭӬ")
    assign(left_map, "CAP_C", "СЄЅ")
    assign(left_map, "CAP_A", "АӐӒ")
    assign(left_map, "CAP_L_STEM", "ЛӅԮԒ")
    assign(left_map, "CAP_D_STEM", "Д")
    assign(left_map, "CAP_SOFT", "ЬЪЉЊ")
    assign(left_map, "CAP_K", "КҚҜҞӃ")
    assign(left_map, "CAP_X", "ХЖҖҲӼӾ")
    assign(left_map, "CAP_STEM", "ІНПШЩЦИМЯЧЫЕӔЈҺӀҤҢҦҸҴҶӇӉӋӍԀԌԚԜЂЋЏ₽")

    assign(left_map, "LOW_YI", "ї")
    assign(left_map, "LOW_EO", "ё")
    assign(left_map, "LOW_ACC_STEM", "йѐѝќӣӥ")
    assign(left_map, "LOW_Y_BREVE", "ў")
    assign(left_map, "LOW_GHE_UP", "ґ")
    assign(left_map, "LOW_GHE", "гѓғӷ")
    assign(left_map, "LOW_TE", "тҭ")
    assign(left_map, "LOW_Y", "уүұ")
    assign(left_map, "LOW_BE", "б")
    assign(left_map, "LOW_EF", "ф")
    assign(left_map, "LOW_ASC_STEM", "іӏђћѣ")
    assign(left_map, "LOW_ROUND_O", "орвьъөюҩѳ")
    assign(left_map, "LOW_ROUND_E", "есэє")
    assign(left_map, "LOW_A", "аӑӓ")
    assign(left_map, "LOW_ZE", "зҙӟ")
    assign(left_map, "LOW_K", "кқҝҟӄ")
    assign(left_map, "LOW_X", "хжҗҳӽӿ")
    assign(left_map, "LOW_L_STEM", "лљӆԯԓ")
    assign(left_map, "LOW_D_STEM", "д")
    assign(left_map, "LOW_STEM", "инпшщцмчыяјһԁԛԝӕњџңҥҧҵҷҹӈӊӌӎ")

    assign(left_map, "P_PERIOD", ".…")
    assign(left_map, "P_COMMA", ",„")
    assign(left_map, "P_COLON", ":;")
    assign(left_map, "P_HYPHEN", "-")
    assign(left_map, "P_DASH", "–—")
    assign(left_map, "P_QUOTE_OPEN", "‘“")
    assign(left_map, "P_QUOTE_CLOSE", "’”\"'")
    assign(left_map, "P_GUIL_OPEN", "«‹")
    assign(left_map, "P_GUIL_CLOSE", "»›")
    assign(left_map, "P_PAREN_OPEN", "(")

    # Right (trailing) optical classes.
    assign(right_map, "CAP_YI", "Ї")
    assign(right_map, "CAP_TALL_ACC", "ЁЙЀЍЌӢӤҐ")
    assign(right_map, "CAP_A", "АӐӒ")
    assign(right_map, "CAP_DL", "ДЛЉӅԮԒ")
    assign(right_map, "CAP_ROUND", "ОСЄӨФҨѲѺѼꙨꙪꚚ")
    assign(right_map, "CAP_E_REV", "ЗЭӞӬ")
    assign(right_map, "CAP_T", "ТҬ")
    assign(right_map, "CAP_Y_BREVE", "Ў")
    assign(right_map, "CAP_Y_CYR", "УҰ")
    assign(right_map, "CAP_Y_HOMO", "Ү")
    assign(right_map, "CAP_DIAG", "ХЖҖҲӼӾ")
    assign(right_map, "CAP_YA", "Я")
    assign(right_map, "CAP_CHE", "ЧҶҸӋ")
    assign(right_map, "CAP_FLAG", "Ъ")
    assign(right_map, "CAP_STEM", "ІНПШЩЦБВГЃЕИКМРЬЫЮЅЈҺӀӔҒҔӶҎҚҢҤҦҸҴЂЋЏԀԌԚԜ₽")

    assign(right_map, "LOW_YI", "ї")
    assign(right_map, "LOW_EO", "ё")
    assign(right_map, "LOW_ACC", "йѐѝќӣӥ")
    assign(right_map, "LOW_Y_BREVE", "ў")
    assign(right_map, "LOW_ASC", "іӏђћѣ")
    assign(right_map, "LOW_BE", "б")
    assign(right_map, "LOW_EF", "ф")
    assign(right_map, "LOW_DL", "длљӆԯԓ")
    assign(right_map, "LOW_ROUND", "аеосэєөзвӑӓӕҙӟ")
    assign(right_map, "LOW_Y", "уүұ")
    assign(right_map, "LOW_X", "хжҗҳӽӿ")
    assign(right_map, "LOW_TE", "тҭ")
    assign(right_map, "LOW_YA", "я")
    assign(right_map, "LOW_HARD", "ъ")
    assign(right_map, "LOW_STEM", "инпшщцмгґѓкрьыюјһԁԛԝңҥҧҵҷҹӈӊӌӎ")

    assign(right_map, "P_PERIOD", ".…")
    assign(right_map, "P_COMMA", ",„")
    assign(right_map, "P_COLON", ":;")
    assign(right_map, "P_HYPHEN", "-")
    assign(right_map, "P_DASH", "–—")
    assign(right_map, "P_QUOTE_OPEN", "‘“")
    assign(right_map, "P_QUOTE_CLOSE", "’”\"'")
    assign(right_map, "P_GUIL_OPEN", "«‹")
    assign(right_map, "P_GUIL_CLOSE", "»›")
    assign(right_map, "P_PAREN_CLOSE", ")")
    assign(right_map, "P_QUESTION", "?")

    # Split each shape key into (key, is_drawn) so (orig, orig) pairs are never emitted
    # and never double-kerned against native Latin Lookup 0.
    def bucket(mapping):
        groups = {}
        for gn, key in mapping.items():
            groups.setdefault((key, gn in added_names), []).append(gn)
        return {k: tuple(sorted(v)) for k, v in groups.items()}

    l_buckets = bucket(left_map)
    r_buckets = bucket(right_map)

    # Compact rule table: (left_keys, right_keys, base_amount, scale_kind)
    rules = [
        # --- Uppercase Overhangs -> Uppercase & Lowercase & Punct ---
        (("CAP_GAMMA",), ("CAP_A",), -135, "overhang"),
        (("CAP_GAMMA",), ("CAP_DL",), -125, "overhang"),
        (("CAP_GAMMA",), ("CAP_ROUND",), -50, "overhang"),
        (("CAP_GAMMA",), ("CAP_E_REV", "CAP_YA"), -45, "overhang"),
        (("CAP_GAMMA",), ("CAP_CHE",), -25, "overhang"),
        (("CAP_GAMMA",), ("LOW_ROUND", "LOW_EO", "LOW_BE"), -125, "overhang"),
        (("CAP_GAMMA",), ("LOW_DL",), -135, "overhang"),
        (("CAP_GAMMA",), ("LOW_STEM", "LOW_HARD"), -100, "overhang"),
        (("CAP_GAMMA",), ("LOW_YA",), -105, "overhang"),
        (("CAP_GAMMA",), ("LOW_Y", "LOW_Y_BREVE"), -90, "overhang"),
        (("CAP_GAMMA",), ("LOW_X",), -110, "overhang"),
        (("CAP_GAMMA",), ("LOW_TE",), -65, "overhang"),
        (("CAP_GAMMA",), ("LOW_EF",), -65, "overhang"),
        (("CAP_GAMMA",), ("P_PERIOD", "P_COMMA"), -185, "overhang"),
        (("CAP_GAMMA",), ("P_DASH",), -90, "overhang"),

        (("CAP_GHE_UP",), ("CAP_A",), -125, "overhang"),
        (("CAP_GHE_UP",), ("CAP_DL",), -115, "overhang"),
        (("CAP_GHE_UP",), ("CAP_ROUND",), -45, "overhang"),
        (("CAP_GHE_UP",), ("CAP_E_REV", "CAP_YA"), -40, "overhang"),
        (("CAP_GHE_UP",), ("LOW_ROUND", "LOW_EO", "LOW_BE"), -115, "overhang"),
        (("CAP_GHE_UP",), ("LOW_DL",), -125, "overhang"),
        (("CAP_GHE_UP",), ("LOW_STEM", "LOW_HARD", "LOW_YA"), -90, "overhang"),
        (("CAP_GHE_UP",), ("LOW_Y", "LOW_Y_BREVE", "LOW_X"), -85, "overhang"),
        (("CAP_GHE_UP",), ("LOW_TE", "LOW_EF"), -55, "overhang"),
        (("CAP_GHE_UP",), ("P_PERIOD", "P_COMMA"), -175, "overhang"),

        (("CAP_T",), ("CAP_A",), -165, "overhang"),
        (("CAP_T",), ("CAP_DL",), -145, "overhang"),
        (("CAP_T",), ("CAP_ROUND",), -70, "overhang"),
        (("CAP_T",), ("CAP_E_REV", "CAP_YA"), -60, "overhang"),
        (("CAP_T",), ("CAP_Y", "CAP_Y_CYR", "CAP_Y_HOMO"), 20, "wedge"),
        (("CAP_T",), ("LOW_ROUND", "LOW_EO", "LOW_BE"), -185, "overhang"),
        (("CAP_T",), ("LOW_DL",), -155, "overhang"),
        (("CAP_T",), ("LOW_STEM",), -140, "overhang"),
        (("CAP_T",), ("LOW_YA",), -125, "overhang"),
        (("CAP_T",), ("LOW_Y",), -110, "overhang"),
        (("CAP_T",), ("LOW_X",), -140, "overhang"),
        (("CAP_T",), ("LOW_TE",), -55, "overhang"),
        (("CAP_T",), ("LOW_EF",), -75, "overhang"),
        (("CAP_T",), ("P_PERIOD", "P_COMMA"), -207, "overhang"),
        (("CAP_T",), ("P_COLON",), -38, "overhang"),
        (("CAP_T",), ("P_HYPHEN",), -47, "overhang"),
        (("CAP_T",), ("P_DASH",), -140, "overhang"),

        (("CAP_Y", "CAP_Y_BREVE"), ("CAP_A",), -175, "wedge"),
        (("CAP_Y", "CAP_Y_BREVE"), ("CAP_DL",), -145, "wedge"),
        (("CAP_Y", "CAP_Y_BREVE"), ("CAP_ROUND",), -90, "wedge"),
        (("CAP_Y", "CAP_Y_BREVE"), ("CAP_E_REV", "CAP_YA"), -70, "wedge"),
        (("CAP_Y", "CAP_Y_BREVE"), ("CAP_T",), 20, "wedge"),
        (("CAP_Y", "CAP_Y_BREVE"), ("LOW_ROUND", "LOW_EO", "LOW_BE"), -180, "overhang"),
        (("CAP_Y", "CAP_Y_BREVE"), ("LOW_DL",), -150, "overhang"),
        (("CAP_Y", "CAP_Y_BREVE"), ("LOW_STEM",), -150, "overhang"),
        (("CAP_Y", "CAP_Y_BREVE"), ("LOW_YA",), -115, "overhang"),
        (("CAP_Y", "CAP_Y_BREVE"), ("LOW_Y", "LOW_X"), -115, "overhang"),
        (("CAP_Y", "CAP_Y_BREVE"), ("LOW_TE",), -65, "overhang"),
        (("CAP_Y", "CAP_Y_BREVE"), ("LOW_EF",), -80, "overhang"),
        (("CAP_Y", "CAP_Y_BREVE"), ("P_PERIOD", "P_COMMA"), -215, "overhang"),
        (("CAP_Y", "CAP_Y_BREVE"), ("P_COLON",), -65, "overhang"),
        (("CAP_Y", "CAP_Y_BREVE"), ("P_HYPHEN",), -55, "overhang"),
        (("CAP_Y", "CAP_Y_BREVE"), ("P_DASH",), -160, "overhang"),
        (("CAP_Y", "CAP_Y_BREVE"), ("P_GUIL_OPEN",), -180, "overhang"),
        (("CAP_Y", "CAP_Y_BREVE"), ("P_GUIL_CLOSE",), -115, "overhang"),

        (("CAP_P",), ("CAP_A",), -128, "overhang"),
        (("CAP_P",), ("CAP_DL",), -95, "overhang"),
        (("CAP_P",), ("CAP_DIAG",), -60, "overhang"),
        (("CAP_P",), ("CAP_Y_HOMO",), -53, "overhang"),
        (("CAP_P",), ("CAP_Y_CYR", "CAP_Y_BREVE"), -35, "overhang"),
        (("CAP_P",), ("CAP_E_REV", "CAP_YA", "CAP_CHE"), -30, "overhang"),
        (("CAP_P",), ("LOW_DL",), -55, "overhang"),
        (("CAP_P",), ("LOW_ROUND", "LOW_EO", "LOW_BE"), -40, "overhang"),
        (("CAP_P",), ("LOW_STEM", "LOW_YA"), -25, "overhang"),
        (("CAP_P",), ("LOW_X",), -25, "overhang"),
        (("CAP_P",), ("P_PERIOD", "P_COMMA"), -225, "overhang"),

        (("CAP_EF",), ("CAP_A", "CAP_DL"), -60, "wedge"),
        (("CAP_EF",), ("CAP_T",), -70, "wedge"),
        (("CAP_EF",), ("CAP_Y_HOMO",), -85, "wedge"),
        (("CAP_EF",), ("CAP_Y_CYR", "CAP_Y_BREVE"), -45, "wedge"),
        (("CAP_EF",), ("CAP_DIAG",), -65, "wedge"),
        (("CAP_EF",), ("CAP_E_REV", "CAP_YA", "CAP_CHE", "CAP_FLAG"), -35, "wedge"),
        (("CAP_EF",), ("LOW_ROUND", "LOW_EO", "LOW_BE"), -24, "text_wedge"),
        (("CAP_EF",), ("LOW_DL",), -30, "wedge"),
        (("CAP_EF",), ("LOW_X",), -30, "wedge"),
        (("CAP_EF",), ("P_PERIOD",), -44, "wedge"),
        (("CAP_EF",), ("P_COMMA",), -34, "wedge"),

        (("CAP_BE",), ("CAP_T",), -35, "wedge"),
        (("CAP_BE",), ("CAP_Y_HOMO",), -55, "wedge"),
        (("CAP_BE",), ("CAP_Y_CYR", "CAP_Y_BREVE"), -35, "wedge"),
        (("CAP_BE",), ("CAP_DIAG", "CAP_A", "CAP_DL"), -25, "wedge"),
        (("CAP_BE",), ("LOW_ROUND", "LOW_EO", "LOW_BE"), -25, "wedge"),

        (("CAP_B",), ("CAP_T",), -38, "wedge"),
        (("CAP_B",), ("CAP_Y_HOMO",), -63, "wedge"),
        (("CAP_B",), ("CAP_Y_CYR", "CAP_Y_BREVE"), -35, "wedge"),
        (("CAP_B",), ("CAP_DIAG",), -30, "wedge"),
        (("CAP_B",), ("LOW_Y", "LOW_Y_BREVE"), -20, "wedge"),

        # --- Uppercase Round Bowls ---
        (("CAP_O",), ("CAP_A",), -66, "wedge"),
        (("CAP_O",), ("CAP_DL",), -55, "wedge"),
        (("CAP_O",), ("CAP_T",), -75, "wedge"),
        (("CAP_O",), ("CAP_Y_HOMO",), -97, "wedge"),
        (("CAP_O",), ("CAP_Y_CYR", "CAP_Y_BREVE"), -45, "wedge"),
        (("CAP_O",), ("CAP_DIAG",), -71, "wedge"),
        (("CAP_O",), ("CAP_E_REV", "CAP_YA", "CAP_CHE", "CAP_FLAG"), -38, "wedge"),
        (("CAP_O",), ("LOW_DL", "LOW_Y", "LOW_X"), -30, "wedge"),
        (("CAP_O",), ("P_PERIOD",), -44, "wedge"),
        (("CAP_O",), ("P_COMMA",), -34, "wedge"),
        (("CAP_O",), ("P_PAREN_CLOSE",), -24, "wedge"),

        (("CAP_E_REV",), ("CAP_T",), -55, "wedge"),
        (("CAP_E_REV",), ("CAP_Y_HOMO",), -80, "wedge"),
        (("CAP_E_REV",), ("CAP_Y_CYR", "CAP_Y_BREVE"), -45, "wedge"),
        (("CAP_E_REV",), ("CAP_A", "CAP_DL"), -45, "wedge"),
        (("CAP_E_REV",), ("CAP_DIAG",), -55, "wedge"),
        (("CAP_E_REV",), ("LOW_ROUND", "LOW_EO", "LOW_BE", "LOW_DL", "LOW_Y"), -25, "wedge"),
        (("CAP_E_REV",), ("P_PERIOD", "P_COMMA"), -35, "wedge"),

        (("CAP_ZE",), ("CAP_T",), -35, "wedge"),
        (("CAP_ZE",), ("CAP_Y_HOMO",), -55, "wedge"),
        (("CAP_ZE",), ("CAP_Y_CYR", "CAP_Y_BREVE"), -30, "wedge"),
        (("CAP_ZE",), ("CAP_A", "CAP_DL", "CAP_DIAG"), -25, "wedge"),
        (("CAP_ZE",), ("P_PERIOD", "P_COMMA"), -25, "wedge"),

        (("CAP_C",), ("CAP_A", "CAP_DL"), -20, "wedge"),
        (("CAP_C",), ("CAP_ROUND",), -45, "wedge"),
        (("CAP_C",), ("LOW_ROUND", "LOW_EO", "LOW_BE", "LOW_DL"), -35, "wedge"),
        (("CAP_C",), ("LOW_Y", "LOW_Y_BREVE"), -20, "wedge"),

        # --- Uppercase Triangles, Soft/Hard Bowls, Diagonals ---
        (("CAP_A",), ("CAP_T",), -165, "wedge"),
        (("CAP_A",), ("CAP_GAMMA",), -135, "wedge"),
        (("CAP_A",), ("CAP_Y_HOMO",), -180, "wedge"),
        (("CAP_A",), ("CAP_Y_CYR", "CAP_Y_BREVE"), -45, "wedge"),
        (("CAP_A",), ("CAP_ROUND",), -66, "wedge"),
        (("CAP_A",), ("CAP_E_REV",), -35, "wedge"),
        (("CAP_A",), ("CAP_CHE",), -50, "wedge"),
        (("CAP_A",), ("CAP_FLAG",), -90, "wedge"),
        (("CAP_A",), ("LOW_ROUND", "LOW_EO", "LOW_BE"), -45, "wedge"),
        (("CAP_A",), ("LOW_Y", "LOW_Y_BREVE"), -150, "wedge"),
        (("CAP_A",), ("LOW_TE",), -85, "wedge"),
        (("CAP_A",), ("LOW_X",), -20, "wedge"),
        (("CAP_A",), ("P_DASH",), -55, "wedge"),
        (("CAP_A",), ("P_QUOTE_CLOSE", "P_QUOTE_OPEN"), -110, "wedge"),
        (("CAP_A",), ("P_QUESTION",), -140, "wedge"),

        (("CAP_L_STEM",), ("CAP_T",), -35, "wedge"),
        (("CAP_L_STEM",), ("CAP_Y_HOMO",), -125, "wedge"),
        (("CAP_L_STEM",), ("CAP_Y_CYR", "CAP_Y_BREVE"), -40, "wedge"),
        (("CAP_L_STEM",), ("CAP_CHE",), -30, "wedge"),
        (("CAP_L_STEM",), ("LOW_Y", "LOW_Y_BREVE"), -100, "wedge"),

        (("CAP_D_STEM",), ("CAP_T",), -20, "wedge"),
        (("CAP_D_STEM",), ("CAP_Y_HOMO",), -100, "wedge"),
        (("CAP_D_STEM",), ("CAP_Y_CYR", "CAP_Y_BREVE"), -35, "wedge"),
        (("CAP_D_STEM",), ("CAP_CHE",), -25, "wedge"),
        (("CAP_D_STEM",), ("CAP_FLAG",), -50, "text_wedge"),
        (("CAP_D_STEM",), ("LOW_Y", "LOW_Y_BREVE"), -80, "wedge"),

        (("CAP_SOFT",), ("CAP_T",), -95, "wedge"),
        (("CAP_SOFT",), ("CAP_Y_HOMO",), -120, "wedge"),
        (("CAP_SOFT",), ("CAP_Y_CYR", "CAP_Y_BREVE"), -60, "wedge"),
        (("CAP_SOFT",), ("CAP_A",), -45, "wedge"),
        (("CAP_SOFT",), ("CAP_DL",), -35, "wedge"),
        (("CAP_SOFT",), ("CAP_DIAG",), -45, "wedge"),
        (("CAP_SOFT",), ("CAP_CHE", "CAP_YA"), -30, "wedge"),
        (("CAP_SOFT",), ("LOW_Y", "LOW_Y_BREVE", "LOW_X"), -45, "wedge"),
        (("CAP_SOFT",), ("P_PERIOD", "P_COMMA"), -75, "wedge"),

        (("CAP_K",), ("CAP_ROUND",), -49, "wedge"),
        (("CAP_K",), ("LOW_ROUND", "LOW_EO", "LOW_BE", "LOW_DL"), -55, "wedge"),
        (("CAP_K",), ("LOW_Y", "LOW_Y_BREVE"), -77, "wedge"),
        (("CAP_K",), ("LOW_TE", "LOW_EF"), -45, "wedge"),
        (("CAP_K",), ("LOW_STEM",), -20, "wedge"),
        (("CAP_K",), ("P_DASH",), -50, "wedge"),
        (("CAP_K",), ("P_GUIL_OPEN",), -60, "wedge"),

        (("CAP_X",), ("CAP_ROUND",), -70, "wedge"),
        (("CAP_X",), ("CAP_DIAG",), -30, "wedge"),
        (("CAP_X",), ("LOW_ROUND", "LOW_EO", "LOW_BE", "LOW_DL"), -55, "wedge"),
        (("CAP_X",), ("LOW_STEM",), -60, "text_wedge"),
        (("CAP_X",), ("LOW_Y", "LOW_Y_BREVE", "LOW_TE", "LOW_EF"), -50, "wedge"),
        (("CAP_X",), ("P_DASH",), -50, "wedge"),

        # --- Lowercase Overhangs, Rounds, Diagonals, Stems ---
        (("LOW_GHE",), ("LOW_A",), -35, "overhang"),
        (("LOW_GHE",), ("LOW_ROUND", "LOW_EO", "LOW_DL"), -65, "text_overhang"),
        (("LOW_GHE",), ("LOW_BE",), -52, "overhang"),
        (("LOW_GHE",), ("LOW_STEM", "LOW_ACC"), -68, "text_overhang"),
        (("LOW_GHE",), ("LOW_X",), -30, "overhang"),
        (("LOW_GHE",), ("CAP_A",), -128, "overhang"),
        (("LOW_GHE",), ("CAP_DL",), -110, "overhang"),
        (("LOW_GHE",), ("CAP_T",), -107, "overhang"),
        (("LOW_GHE",), ("CAP_Y_HOMO", "CAP_Y_CYR"), -85, "overhang"),
        (("LOW_GHE",), ("CAP_DIAG",), -70, "overhang"),
        (("LOW_GHE",), ("P_PERIOD",), -180, "overhang"),
        (("LOW_GHE",), ("P_COMMA",), -160, "overhang"),

        (("LOW_GHE_UP",), ("LOW_ROUND", "LOW_EO", "LOW_BE", "LOW_DL"), -35, "overhang"),
        (("LOW_GHE_UP",), ("P_PERIOD",), -150, "overhang"),
        (("LOW_GHE_UP",), ("P_COMMA",), -135, "overhang"),

        (("LOW_TE",), ("LOW_ROUND", "LOW_EO", "LOW_DL"), -50, "text_overhang"),
        (("LOW_TE",), ("LOW_BE",), -20, "overhang"),
        (("LOW_TE",), ("LOW_STEM", "LOW_ACC"), -62, "text_overhang"),
        (("LOW_TE",), ("CAP_T", "CAP_Y_HOMO", "CAP_Y_CYR", "CAP_A", "CAP_DL"), -68, "overhang"),
        (("LOW_TE",), ("P_PERIOD", "P_COMMA"), -50, "overhang"),

        (("LOW_Y", "LOW_Y_BREVE"), ("LOW_ROUND", "LOW_EO", "LOW_BE", "LOW_DL"), -48, "wedge"),
        (("LOW_Y",), ("CAP_A",), -155, "wedge"),
        (("LOW_Y",), ("CAP_DL",), -130, "wedge"),
        (("LOW_Y",), ("CAP_T",), -110, "wedge"),
        (("LOW_Y",), ("CAP_Y_HOMO",), -107, "wedge"),
        (("LOW_Y",), ("CAP_Y_CYR",), -80, "wedge"),
        (("LOW_Y",), ("CAP_DIAG",), -53, "wedge"),
        (("LOW_Y", "LOW_Y_BREVE"), ("P_PERIOD", "P_COMMA"), -120, "wedge"),

        (("LOW_ROUND_O",), ("LOW_Y", "LOW_Y_BREVE", "LOW_EF"), -30, "wedge"),
        (("LOW_ROUND_O",), ("LOW_X",), -48, "wedge"),
        (("LOW_ROUND_O",), ("LOW_TE",), -43, "text_wedge"),
        (("LOW_ROUND_O",), ("LOW_YA",), -34, "text_wedge"),
        (("LOW_ROUND_O",), ("CAP_A",), -45, "wedge"),
        (("LOW_ROUND_O",), ("CAP_DL",), -35, "wedge"),
        (("LOW_ROUND_O",), ("CAP_T",), -180, "overhang"),
        (("LOW_ROUND_O",), ("CAP_Y_HOMO",), -185, "overhang"),
        (("LOW_ROUND_O",), ("CAP_Y_CYR", "CAP_Y_BREVE"), -125, "overhang"),
        (("LOW_ROUND_O",), ("CAP_DIAG",), -60, "wedge"),
        (("LOW_ROUND_O",), ("P_PERIOD",), -25, "wedge"),
        (("LOW_ROUND_O",), ("P_COMMA",), -33, "wedge"),
        (("LOW_ROUND_O",), ("P_QUOTE_CLOSE", "P_QUOTE_OPEN"), -40, "wedge"),
        (("LOW_ROUND_O",), ("P_QUESTION",), -100, "wedge"),

        (("LOW_ROUND_E", "LOW_EO"), ("LOW_Y", "LOW_Y_BREVE"), -22, "wedge"),
        (("LOW_ROUND_E", "LOW_EO"), ("LOW_X",), -32, "wedge"),
        (("LOW_ROUND_E", "LOW_EO"), ("LOW_TE",), -46, "text_wedge"),
        (("LOW_ROUND_E", "LOW_EO"), ("CAP_T",), -185, "overhang"),
        (("LOW_ROUND_E", "LOW_EO"), ("CAP_Y_HOMO",), -160, "overhang"),
        (("LOW_ROUND_E", "LOW_EO"), ("CAP_Y_CYR", "CAP_Y_BREVE"), -115, "overhang"),
        (("LOW_ROUND_E", "LOW_EO"), ("CAP_DIAG",), -29, "wedge"),

        (("LOW_BE",), ("LOW_Y", "LOW_Y_BREVE", "LOW_X"), -25, "wedge"),
        (("LOW_BE",), ("P_PERIOD", "P_COMMA"), -25, "wedge"),

        (("LOW_EF",), ("LOW_Y", "LOW_Y_BREVE", "LOW_X"), -25, "wedge"),
        (("LOW_EF",), ("CAP_T", "CAP_Y_HOMO", "CAP_Y_CYR"), -55, "wedge"),
        (("LOW_EF",), ("P_PERIOD", "P_COMMA"), -25, "wedge"),

        (("LOW_A",), ("LOW_TE",), -48, "text_wedge"),
        (("LOW_A",), ("LOW_Y", "LOW_Y_BREVE"), -36, "wedge"),
        (("LOW_A",), ("LOW_X",), -20, "wedge"),
        (("LOW_A",), ("CAP_T",), -215, "overhang"),
        (("LOW_A",), ("CAP_Y_HOMO",), -215, "overhang"),
        (("LOW_A",), ("CAP_Y_CYR", "CAP_Y_BREVE"), -145, "overhang"),
        (("LOW_A",), ("P_QUESTION",), -110, "wedge"),

        (("LOW_K",), ("LOW_ROUND", "LOW_EO", "LOW_BE", "LOW_DL"), -31, "wedge"),
        (("LOW_K",), ("CAP_ROUND",), -40, "wedge"),
        (("LOW_K",), ("CAP_T",), -107, "overhang"),
        (("LOW_K",), ("CAP_Y_HOMO",), -105, "overhang"),
        (("LOW_K",), ("CAP_Y_CYR", "CAP_Y_BREVE"), -75, "overhang"),

        (("LOW_X",), ("LOW_ROUND", "LOW_EO", "LOW_BE", "LOW_DL"), -45, "wedge"),
        (("LOW_X",), ("CAP_ROUND",), -21, "wedge"),
        (("LOW_X",), ("CAP_T",), -140, "overhang"),
        (("LOW_X",), ("CAP_Y_HOMO",), -125, "overhang"),
        (("LOW_X",), ("CAP_Y_CYR", "CAP_Y_BREVE"), -85, "overhang"),

        (("LOW_ZE",), ("LOW_Y", "LOW_Y_BREVE"), -25, "wedge"),
        (("LOW_ZE",), ("CAP_T",), -140, "overhang"),
        (("LOW_ZE",), ("CAP_Y_HOMO",), -135, "overhang"),
        (("LOW_ZE",), ("CAP_Y_CYR", "CAP_Y_BREVE"), -95, "overhang"),

        (("LOW_STEM", "LOW_L_STEM"), ("LOW_Y", "LOW_Y_BREVE"), -35, "wedge"),
        (("LOW_STEM", "LOW_L_STEM"), ("LOW_TE",), -43, "text_wedge"),
        (("LOW_STEM", "LOW_L_STEM"), ("CAP_T",), -165, "overhang"),
        (("LOW_STEM", "LOW_L_STEM"), ("CAP_Y_HOMO",), -185, "overhang"),
        (("LOW_STEM", "LOW_L_STEM"), ("CAP_Y_CYR", "CAP_Y_BREVE"), -125, "overhang"),
        (("LOW_D_STEM",), ("LOW_Y", "LOW_Y_BREVE"), -30, "wedge"),
        (("LOW_D_STEM",), ("CAP_T",), -135, "overhang"),
        (("LOW_D_STEM",), ("CAP_Y_HOMO",), -150, "overhang"),
        (("LOW_D_STEM",), ("CAP_Y_CYR", "CAP_Y_BREVE"), -105, "overhang"),

        # --- Punctuation -> Letters ---
        (("P_PERIOD", "P_COMMA"), ("CAP_T",), -207, "overhang"),
        (("P_PERIOD", "P_COMMA"), ("CAP_Y_HOMO", "CAP_Y_CYR", "CAP_Y_BREVE"), -215, "overhang"),
        (("P_PERIOD", "P_COMMA"), ("CAP_ROUND",), -38, "wedge"),
        (("P_PERIOD", "P_COMMA"), ("LOW_TE",), -90, "overhang"),
        (("P_PERIOD", "P_COMMA"), ("LOW_Y", "LOW_Y_BREVE"), -125, "overhang"),
        (("P_COLON",), ("CAP_T",), -38, "overhang"),
        (("P_COLON",), ("CAP_Y_HOMO", "CAP_Y_CYR", "CAP_Y_BREVE"), -75, "overhang"),
        (("P_HYPHEN",), ("CAP_T",), -47, "overhang"),
        (("P_HYPHEN",), ("CAP_Y_HOMO", "CAP_Y_CYR", "CAP_Y_BREVE"), -56, "overhang"),
        (("P_DASH",), ("CAP_A",), -56, "wedge"),
        (("P_DASH",), ("CAP_T",), -140, "overhang"),
        (("P_DASH",), ("CAP_Y_HOMO", "CAP_Y_CYR", "CAP_Y_BREVE"), -164, "overhang"),
        (("P_DASH",), ("CAP_DIAG",), -105, "wedge"),
        (("P_GUIL_OPEN",), ("CAP_T", "CAP_Y_HOMO", "CAP_Y_CYR", "CAP_Y_BREVE"), -120, "overhang"),
        (("P_GUIL_CLOSE",), ("CAP_T", "CAP_Y_HOMO", "CAP_Y_CYR", "CAP_Y_BREVE"), -185, "overhang"),
        (("P_GUIL_CLOSE",), ("CAP_A", "CAP_DL", "CAP_DIAG"), -45, "wedge"),
        (("P_GUIL_CLOSE",), ("LOW_Y", "LOW_Y_BREVE"), -40, "wedge"),
        (("P_QUOTE_OPEN", "P_QUOTE_CLOSE"), ("CAP_A",), -140, "wedge"),
        (("P_QUOTE_OPEN", "P_QUOTE_CLOSE"), ("LOW_ROUND", "LOW_EO"), -65, "wedge"),

        # --- Ї / ї Positive Dot-Collision Kerning (+20..+60 matching Ï / ï) ---
        (("CAP_STEM", "CAP_L_STEM", "CAP_D_STEM", "CAP_ROUND", "CAP_O", "CAP_C", "CAP_BE",
          "CAP_B", "CAP_P", "CAP_EF", "CAP_A", "CAP_SOFT", "CAP_ZE", "CAP_E_REV",
          "LOW_BE", "LOW_EF", "LOW_ASC_STEM", "LOW_GHE_UP", "LOW_ACC_STEM", "LOW_EO", "LOW_Y_BREVE"),
         ("CAP_YI",), 25, "yi_pos"),
        (("CAP_GAMMA", "CAP_GHE_UP", "CAP_T", "CAP_Y", "CAP_K", "CAP_X"), ("CAP_YI",), 30, "yi_pos"),
        (("CAP_YI", "CAP_TALL_STEM", "CAP_Y_BREVE"), ("CAP_YI",), 50, "yi_pos"),
        (("CAP_YI",), ("CAP_STEM", "CAP_A", "CAP_DL", "CAP_ROUND", "CAP_E_REV", "CAP_YA",
                       "CAP_CHE", "CAP_FLAG", "CAP_Y_CYR", "CAP_Y_HOMO"), 25, "yi_pos"),
        (("CAP_YI",), ("CAP_T", "CAP_DIAG"), 30, "yi_pos"),
        (("CAP_YI",), ("CAP_TALL_ACC", "CAP_Y_BREVE"), 50, "yi_pos"),
        (("CAP_YI",), ("LOW_ASC", "LOW_ACC", "LOW_EO", "LOW_BE", "LOW_EF", "LOW_Y_BREVE", "LOW_YI"), 35, "yi_pos"),

        (("CAP_STEM", "CAP_L_STEM", "CAP_D_STEM", "CAP_O", "CAP_C", "CAP_BE", "CAP_B",
          "CAP_P", "CAP_EF", "CAP_A", "CAP_SOFT", "CAP_ZE", "CAP_E_REV"), ("LOW_YI",), 25, "yi_pos"),
        (("CAP_GAMMA", "CAP_GHE_UP", "CAP_T"), ("LOW_YI",), 40, "yi_pos"),
        (("CAP_Y", "CAP_Y_BREVE", "CAP_X"), ("LOW_YI",), 30, "yi_pos"),
        (("CAP_K",), ("LOW_YI",), 60, "yi_pos"),
        (("CAP_YI", "CAP_TALL_STEM"), ("LOW_YI",), 45, "yi_pos"),
        (("LOW_BE", "LOW_EF", "LOW_ASC_STEM", "LOW_GHE_UP", "LOW_ACC_STEM", "LOW_EO", "LOW_Y_BREVE"), ("LOW_YI",), 40, "yi_pos"),
        (("LOW_K",), ("LOW_YI",), 35, "yi_pos"),
        (("LOW_YI",), ("LOW_YI",), 48, "yi_pos"),
        (("P_QUOTE_OPEN", "P_QUOTE_CLOSE"), ("LOW_YI", "CAP_YI"), 110, "yi_pos"),

        (("LOW_YI",), ("CAP_STEM", "CAP_A", "CAP_DL", "CAP_ROUND", "CAP_E_REV", "CAP_YA",
                       "CAP_CHE", "CAP_FLAG", "CAP_DIAG"), 25, "yi_pos"),
        (("LOW_YI",), ("CAP_T",), 40, "yi_pos"),
        (("LOW_YI",), ("CAP_Y_HOMO", "CAP_Y_CYR"), 30, "yi_pos"),
        (("LOW_YI",), ("CAP_TALL_ACC", "CAP_Y_BREVE"), 45, "yi_pos"),
        (("LOW_YI",), ("LOW_ASC", "LOW_ACC", "LOW_EO", "LOW_BE", "LOW_EF", "LOW_Y_BREVE"), 40, "yi_pos"),
        (("LOW_YI",), ("P_QUOTE_OPEN", "P_QUOTE_CLOSE"), 110, "yi_pos"),
    ]

    master_cache = {}

    def master_values(base_amt, kind):
        key = (base_amt, kind)
        if key not in master_cache:
            if locations is not None:
                master_cache[key] = [_kern_scale(base_amt, kind, loc, d) for loc, d in zip(locations, designs)]
            else:
                master_cache[key] = [_kern_scale(base_amt, kind, None, d) for d in designs]
        return master_cache[key]

    def value_pair(values):
        amount, device = varying(values)
        record = {"XAdvance": amount}
        if device is not None:
            record["XAdvDevice"] = device
        return layout.buildValue(record), None

    val_cache = {}
    class_pairs = {}
    class_rules = {}
    for l_keys, r_keys, amt, kind in rules:
        key = (amt, kind)
        if key not in val_cache:
            val_cache[key] = value_pair(master_values(amt, kind))
        for lk in l_keys:
            for rk in r_keys:
                class_rules[lk, rk] = key
                for l_drawn in (False, True):
                    lg = l_buckets.get((lk, l_drawn))
                    if not lg:
                        continue
                    for r_drawn in (False, True):
                        if not (l_drawn or r_drawn):
                            continue
                        rg = r_buckets.get((rk, r_drawn))
                        if not rg:
                            continue
                        class_pairs[(lg, rg)] = val_cache[key]

    zero = [0] * len(designs)

    def inherited(left, right):
        rule = class_rules.get((left_map.get(left), right_map.get(right)))
        amounts = master_values(*rule) if rule is not None else zero
        return [amounts[i] for i in text_indices]

    glyph_map = font.getReverseGlyphMap()
    optical = fit_cyrillic_text([designs[i] for i in text_indices], glyphs, added_names, inherited)
    subtable = layout.buildPairPosClassesSubtable(class_pairs, glyph_map)
    return subtable, optical


def _build_text_kern(optical, model, store, offset, glyph_map):
    """Fade extra text kerning toward the slanted and graded extremes."""
    slant = {"slnt": (-1.0, -1.0, 0.0)}
    grade = {"GRAD": (0.0, 1.0, 1.0)}
    # Multiply every region by (1 - slant) * (1 - grade). Explicit products
    # keep the fade stable when both axes move together in a sparse master grid.
    store.setSupports([region | fade for fade in ({}, slant, grade, slant | grade)
                       for region in model.supports])
    pairs = {}
    for pair, amounts in optical.items():
        deltas = model.getDeltas(amounts, round=otRound)
        negative = [-delta for delta in deltas]
        index = store.storeDeltas(deltas[1:] + negative + negative + deltas)
        value = layout.buildValue({"XAdvance": deltas[0], "XAdvDevice": buildVarDevTable(offset + index)})
        pairs[pair] = value, None
    return layout.buildPairPosGlyphs(pairs, glyph_map)


def layout_variations(font, model, designs, glyphs, added, locations=None):
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

    def add_lookup(feature_tag, subtables, flags=0, extension=False):
        feature = next(f.Feature for f in table.FeatureList.FeatureRecord if f.FeatureTag == feature_tag)
        feature.LookupListIndex.append(len(lookups))
        feature.LookupCount = len(feature.LookupListIndex)
        lookups.append(layout.buildLookup(subtables, flags=flags, table="GPOS", extension=extension))
        table.LookupList.LookupCount = len(lookups)

    enclosures = {glyphs[ord(char)]: (0, layout.buildAnchor(0, 0)) for char in ENCLOSING}
    enclosure_bases = {glyphs[code]: {0: anchor(code, "enclosing")} for code in glyphs if not combining(code)}
    circle = font.getBestCmap()[0x25CC]
    enclosure_bases[circle] = {0: layout.buildAnchor(600, 510)}
    add_lookup("mark", [layout.buildMarkBasePosSubtable(enclosures, enclosure_bases, glyph_map)])

    text_model, text_indices = model.getSubModel([
        i if d.metrics.GRAD == 0 and d.metrics.slant == 0 else None
        for i, d in enumerate(designs)
    ])
    class_kern, optical = _build_cyrillic_kern(font, glyphs, added, designs, locations, varying, text_indices)
    add_lookup("kern", [class_kern], flags=8)
    if optical:
        add_lookup("kern", _build_text_kern(optical, text_model, store, offset, glyph_map), flags=8)
    append_var_store(gdef.VarStore, store.finish())

    clearance, guard_store = build_clearance_kerning(font, locations)
    if clearance:
        append_var_store(gdef.VarStore, guard_store)
        add_lookup("kern", clearance, flags=8, extension=True)

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
    layout_variations(font, model, designs, glyphs, added, locations=locations)
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
