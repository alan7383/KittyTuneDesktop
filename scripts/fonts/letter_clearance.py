# SPDX-License-Identifier: OFL-1.1
"""Keep adjacent letter outlines separated across the variable masters."""

from itertools import product
from unicodedata import category

import numpy as np
from fontTools.misc.fixedTools import otRound
from fontTools.otlLib import builder as layout
from fontTools.varLib.builder import buildVarDevTable
from fontTools.varLib.models import supportScalar
from fontTools.varLib.varStore import OnlineVarStoreBuilder, VarStoreInstancer

from cyrillic_spacing import _EdgePen


def clearance_locations(masters):
    """Include combined extremes and the app and website's text styles."""
    extremes = {
        "opsz": (6, 144), "wdth": (25, 151), "wght": (1, 1000),
        "ROND": (0, 100), "slnt": (-10, 0), "GRAD": (0, 100),
    }
    endpoints = [dict(zip(extremes, values)) for values in product(*extremes.values())]
    styles = [
        {"opsz": 14, "wght": 500, "ROND": 100},
        {"opsz": 18, "wght": 500, "ROND": 100},
        {"opsz": 14, "wght": 800, "wdth": 65, "ROND": 100},
        {"opsz": 72, "wght": 900, "ROND": 100},
        {"opsz": 14, "wght": 800, "ROND": 100, "slnt": -5, "GRAD": 50},
        {"opsz": 16, "wght": 600, "wdth": 83, "ROND": 70, "slnt": -4, "GRAD": 44},
        {"opsz": 18, "wght": 700, "ROND": 100},
    ]
    return [*masters, *endpoints, *styles]


def _letter_names(font):
    return list(dict.fromkeys(
        glyph for code, glyph in font.getBestCmap().items()
        if category(chr(code)).startswith("L") and font["hmtx"][glyph][0] > 0
    ))


def _minimum_gap(font, names, location):
    glyphs = font.getGlyphSet(location=location)
    left, right = [], []
    for name in names:
        glyph = glyphs[name]
        pen = _EdgePen(24.0, first_row=-42, last_row=108)
        pen.glyphSet = glyphs
        glyph.draw(pen)
        left.append(pen.left)
        right.append([glyph.width - x for x in pen.right])

    leading = np.asarray(left, dtype=np.float32)
    trailing = np.asarray(right, dtype=np.float32)
    gap = np.full((len(names), len(names)), np.inf, dtype=np.float32)
    row = np.empty_like(gap)
    for left_edge, right_edge in zip(leading.T, trailing.T):
        np.add(right_edge[:, None], left_edge[None, :], out=row)
        np.minimum(gap, row, out=gap)
    return gap


def _advance(record):
    value = record.Value1
    if value is None:
        return 0, -1
    device = getattr(value, "XAdvDevice", None)
    index = -1 if device is None else (device.StartSize << 16) | device.EndSize
    return getattr(value, "XAdvance", 0), index


def _pair_kerning(font, names):
    """Decode the pair lookups so a guard accounts for all earlier kerning."""
    gpos = font["GPOS"].table
    feature = next(record.Feature for record in gpos.FeatureList.FeatureRecord if record.FeatureTag == "kern")
    by_name = {name: index for index, name in enumerate(names)}
    shape = (len(names), len(names))
    base = np.zeros(shape, dtype=np.int16)
    variations = []

    for lookup_index in feature.LookupListIndex:
        lookup = gpos.LookupList.Lookup[lookup_index]
        applied = np.zeros(shape, dtype=bool)
        device = np.full(shape, -1, dtype=np.int32)
        for table in lookup.SubTable:
            if lookup.LookupType == 9:
                table = table.ExtSubTable
            if table.Format == 1:
                for name, group in zip(table.Coverage.glyphs, table.PairSet):
                    if name not in by_name:
                        continue
                    left = by_name[name]
                    for pair in group.PairValueRecord:
                        right = by_name.get(pair.SecondGlyph)
                        if right is not None and not applied[left, right]:
                            amount, index = _advance(pair)
                            base[left, right] += amount
                            device[left, right] = index
                            applied[left, right] = True
            else:
                left_classes, right_classes = {}, {}
                for name in table.Coverage.glyphs:
                    if name in by_name:
                        left_classes.setdefault(table.ClassDef1.classDefs.get(name, 0), []).append(by_name[name])
                for name, right in by_name.items():
                    right_classes.setdefault(table.ClassDef2.classDefs.get(name, 0), []).append(right)
                for first, left in left_classes.items():
                    records = table.Class1Record[first].Class2Record
                    for second, right in right_classes.items():
                        group = np.ix_(left, right)
                        pending = ~applied[group]
                        if pending.any():
                            amount, index = _advance(records[second])
                            base[group] += np.where(pending, amount, 0).astype(np.int16)
                            device[group] = np.where(pending, index, device[group])
                            applied[group] = True
        indices, positions = np.unique(device, return_inverse=True)
        variations.append((indices, positions.reshape(shape).astype(np.int32)))

    def at(location):
        instancer = VarStoreInstancer(font["GDEF"].table.VarStore, font["fvar"].axes, location)
        result = base.copy()
        for indices, positions in variations:
            amounts = [0 if index < 0 else otRound(instancer[int(index)]) for index in indices]
            result += np.asarray(amounts, dtype=np.int16)[positions]
        return result

    return at


def _corrections(font, names, masters):
    kerning = _pair_kerning(font, names)
    default = {axis.axisTag: axis.defaultValue for axis in font["fvar"].axes}
    cases = {}
    for location in clearance_locations(masters):
        normalized = {axis: value for axis, value in font.normalizeLocation(location).items() if value}
        cases[tuple(sorted(normalized.items()))] = location
    ordered = sorted(cases.items(), key=lambda case: (len(case[0]), sum(abs(v) for _, v in case[0])))
    base = np.zeros(len(names) ** 2, dtype=np.uint16)
    variable = []

    for normalized_axes, location in ordered:
        normalized = dict(normalized_axes)
        gaps = (_minimum_gap(font, names, location) + kerning(normalized)).ravel()
        already = base.astype(np.float32)
        for support, amounts in variable:
            weight = supportScalar(normalized, support)
            if weight:
                already += weight * amounts

        optical_size = max(16.0, location.get("opsz", default["opsz"]))
        clearance = max(32.0, font["head"].unitsPerEm * 0.76 / optical_size)
        # Leave room for integer rounding in the glyph advances and variation values.
        needed = np.maximum(0, np.ceil(clearance + 4 - gaps - already)).astype(np.uint16)
        if not normalized:
            base = needed
        elif np.any(needed):
            # Nonnegative deltas and supports can only open up a pair. Sparse masters
            # cannot subtract this clearance when several axes move together.
            support = {
                axis: (0.0, value, 1.0) if value > 0 else (-1.0, value, 0.0)
                for axis, value in normalized.items()
            }
            variable.append((support, needed))
    return base, variable


def build_clearance_kerning(font, masters):
    names = _letter_names(font)
    base, variable = _corrections(font, names, masters)
    supports = [support for support, _ in variable]
    needs_clearance = base > 0
    for _, amounts in variable:
        needs_clearance |= amounts > 0
    active = np.flatnonzero(needs_clearance)
    by_pair = np.column_stack([amounts[active] for _, amounts in variable]) if variable else np.empty((len(active), 0))
    builder = OnlineVarStoreBuilder([axis.axisTag for axis in font["fvar"].axes])
    offset = len(font["GDEF"].table.VarStore.VarData) << 16
    glyph_order = font.getReverseGlyphMap()
    pairs = {}
    subtables = []
    for index, deltas in zip(active, by_pair):
        left, right = divmod(int(index), len(names))
        record = {"XAdvance": int(base[index])}
        regions = np.flatnonzero(deltas)
        if regions.size:
            builder.setSupports([supports[i] for i in regions])
            variation = builder.storeDeltas(deltas[regions])
            record["XAdvDevice"] = buildVarDevTable(offset + variation)
        pairs[names[left], names[right]] = layout.buildValue(record), None
        if len(pairs) >= 3500:
            subtables.extend(layout.buildPairPosGlyphs(pairs, glyph_order))
            pairs.clear()
    if pairs:
        subtables.extend(layout.buildPairPosGlyphs(pairs, glyph_order))
    print(f"Letter clearance: {len(active)} pairs, {len(supports)} variation supports", flush=True)
    return subtables, builder.finish()
