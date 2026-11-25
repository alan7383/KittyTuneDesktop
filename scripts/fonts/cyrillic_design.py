# SPDX-License-Identifier: OFL-1.1
"""Cyrillic drawings in the coordinates of the original 2000-unit em.

Letters with an identical Latin outline share it. The remaining outlines are
drawn here from strokes, bowls, and the components of this typeface.
"""

from math import cos, hypot, pi, radians, sin, tan
from unicodedata import decomposition

from cyrillic_outlines import Letter, Outline, Pen, source_outline


def pairs(cyrillic, latin):
    return dict(zip(cyrillic, latin, strict=True))


HOMOGLYPHS = pairs(
    "АВЕКМНОРСТХаеорсухЅІЈѕіјҮһӀӏӔӕԁԌԚԛԜԝ",
    "ABEKMHOPCTXaeopcyxSIJsijYhIlÆædGQqWw",
)

MODIFIERS = (
    "абвгдежзиклмопрстуфхцчшыэюꚉәіјөүӏ"
    "абвгдежзиклопсуфхцчшъыґіѕџ"
    "ҫꙑұ"
)
# U+2DF5 is combining ES-TE. U+2DF6 and U+2DF7 are A and IE.
COMBINING_A = "бвгджзклмнопрстхцчшщѳ" + "\0" + "аеꙉꙋѣюꙗѧѫѭ"


def coverage():
    ranges = (
        (0x0400, 0x052F), (0x1C80, 0x1C8A), (0x2DE0, 0x2DFF),
        (0xA640, 0xA69F), (0xFE2E, 0xFE2F), (0x1E030, 0x1E06D),
    )
    return sorted({0x030F, 0x0311, 0x1D2B, 0x1D78, 0x1E08F} |
                  {code for start, end in ranges for code in range(start, end + 1)})


def is_cap(character):
    return character.isupper() or character == "\u1c89"


class Design:
    def __init__(self, font, location):
        self.cmap = font.getBestCmap()
        self.drawings = {}
        self.source = {}
        self.upright = font.getGlyphSet(location=location | {"slnt": 0})
        self.slant = -tan(radians(location.get("slnt", 0)))
        roundness = location.get("ROND", 0) / 100
        metrics = font.getGlyphSet(location=location | {"slnt": 0, "ROND": 0})
        normal = font.getGlyphSet(location=location | {"slnt": 0, "ROND": 0, "wdth": 100, "GRAD": 0})
        h = source_outline(metrics, "H")
        n = source_outline(metrics, "n")
        normal_h = source_outline(normal, "H")
        self.scales = {True: h.advance / normal_h.advance, False: n.advance / normal["n"].width}
        stem = h.contours[1]
        stem_width = stem[8][0] - stem[4][0]
        nominal = normal_h.contours[1]
        nominal_width = nominal[8][0] - nominal[4][0]
        crossbar = h.contours[2][1][1] - h.contours[2][0][1]
        small_stem = n.contours[0][32][0] - n.contours[0][0][0]
        self.profiles = {
            True: (1432, stem_width / self.scales[True], crossbar,
                   h.bounds()[0] / self.scales[True] / 160, nominal_width, roundness),
            False: (n.contours[0][1][1], small_stem / self.scales[False], crossbar * .94,
                    n.bounds()[0] / self.scales[False] / 126, nominal_width, roundness),
        }

    def letter(self, cap, width, left=None, right=None, growth=.62):
        side = 160 if cap else 126
        return Letter(self.profiles[cap], width, side if left is None else left,
                      side if right is None else right, growth)

    def put(self, character, drawing):
        outline = drawing.outline if isinstance(drawing, Pen) else drawing
        self.drawings[character] = Outline(outline.advance, list(outline.contours))

    def original(self, character):
        if character not in self.source:
            scale = self.scales[character.isupper()]
            self.source[character] = source_outline(self.upright, self.cmap[ord(character)]).transform(sx=1 / scale)
        return self.source[character]

    def get(self, character, cap=None):
        outline = self.drawings.get(character)
        if outline is None:
            original = HOMOGLYPHS.get(character, character)
            outline = self.original(original)
        if cap is not None and is_cap(character) != cap:
            # Move between cap and lowercase proportions using the original font's metrics.
            sx = self.scales[is_cap(character)] / self.scales[cap]
            outline = outline.transform(sx=sx)
        return outline

    def final(self, character):
        cap = is_cap(character)
        outline = self.get(character)
        height = self.profiles[cap][0]
        if outline.advance == 0:
            height = 0
        return outline.transform(sx=self.scales[cap], shear=self.slant, dx=-self.slant * height / 2)


def legs(p, count, floor=False, stem_scale=1.0):
    stem_w = p.v * stem_scale
    corner_r = min(stem_w * .32 * p.roundness, p.h * .46)
    for index in range(count):
        x = p.left + index * (p.width - stem_w) / (count - 1)
        if floor:
            p.stem(x, bottom=max(0.0, p.bottom * .08), top=p.tip, weight=stem_scale, bottom_r=corner_r)
        else:
            p.stem(x, bottom=p.bottom, top=p.top, weight=stem_scale, top_r=corner_r)
    bar_radius = min(p.h * .50 * p.roundness, p.v * .32 * p.roundness)
    y = max(0, p.bottom * .08) + p.h / 2 if floor else p.top - p.h / 2
    p.bar(p.left + .35 * stem_w, y, p.right - .35 * stem_w, radius=bar_radius)


def tail(p, x, depth=350, direction=1, width_scale=1.0):
    half = p.v * .45 * width_scale
    foot_r = min(p.roundness * half * .85, p.radius * .70)
    p.polygon([(x - half, .52 * p.h), (x + half, .52 * p.h),
               (x + half + direction * .24 * p.v, -depth),
               (x - half + direction * .24 * p.v, -depth)], radius=foot_r)


def cyrillic_i(p):
    """Unified N-mirrored single-contour Cyrillic и with native Google Sans Flex joins."""
    l, r, b, h, tip = p.left, p.right, p.bottom, p.top, p.tip
    w = p.width
    v = min(p.v, w * .32)
    wt = min(max((p.v - 170.0) / 230.0, 0.0), 1.0)
    top_cap_r = v * .50 * p.roundness
    top_drop = tip - top_cap_r
    bot_rise = b + top_cap_r
    diag_w = min(v * (.85 - .08 * wt), max(36.0, (w - 1.55 * v) * .62))
    ax, ay = l + v * .76, b + v * .42
    bx, by = r - v * .76, h - v * .42
    dx, dy = bx - ax, by - ay
    dist = max(hypot(dx, dy), 1.0)
    nx, ny = -dy / dist, dx / dist
    ox, oy = nx * diag_w * .5, ny * diag_w * .5
    crotch_low = min(h * .36, max(p.h * .65, (ay + oy) + (l + v - (ax + ox)) * dy / max(dx, 1.0)))
    crotch_high = max(h * .64, min(h - p.h * .65, (by - oy) + (r - v - (bx - ox)) * dy / max(dx, 1.0)))
    notch = min(h * .22, v * .65)
    t_round = p.roundness
    diag_sh_x = min(v * (1.14 + .24 * t_round), (w - v) * .46)
    diag_sh_y = min(top_cap_r * .58, h * .10)
    corner_x1 = v * (.55 + .40 * t_round)
    corner_x2 = v * (.50 + .16 * t_round)
    notch_in = 4.0 * t_round + 2.0
    p.outline.contours.append([
        (l + v * .5, b, True), (l, b, False), (l, bot_rise, True),
        (l, top_drop, True), (l, tip, False), (l + v * .5, tip, True),
        (l + v, tip, False), (l + v, top_drop, True),
        (l + v, crotch_low + notch, True), (l + v - notch_in, crotch_low, True),
        (r - diag_sh_x, tip - diag_sh_y, True),
        (r - corner_x1, tip, False), (r - corner_x2, tip, True),
        (r, tip, False), (r, top_drop, True),
        (r, bot_rise, True), (r, b, False), (r - v * .5, b, True),
        (r - v, b, False), (r - v, bot_rise, True),
        (r - v, crotch_high - notch, True), (r - v + notch_in, crotch_high, True),
        (l + diag_sh_x, b + diag_sh_y, True),
        (l + corner_x1, b, False),
    ])


def el(p):
    """Architectural Google Sans Cyrillic Л/л with constant-thickness monotonic left arch."""
    l, r, b, h, tip, w = p.left, p.right, p.bottom, p.top, p.tip, p.width
    v = min(p.v, w * .32)
    hbar = min(p.h * .94, h * .23)
    diag_w = min(v * .92, w * .30)
    shoulder = l + max(w * .34, v * 1.10)
    bend = min(v * .32 * p.roundness, hbar * .46, w * .10)
    stem_cap = v * .50 * p.roundness
    foot_r = min(v * .32 * p.roundness, hbar * .36)
    foot_h = min(b + max(hbar * .52, diag_w * .32), h * .14)
    span = shoulder - l
    outer_ctrl = (shoulder - span * .26, h * .38)
    inner_ctrl = (shoulder - span * .26 + diag_w * 1.02, h * .38 - diag_w * .14)
    foot_inner = l + diag_w * 1.06
    p.outline.contours.append([
        (l + foot_r, b, True), (l, b, False), (l, b + foot_r, True),
        (l, foot_h - foot_r, True), (l, foot_h, False), (l + foot_r * .9, foot_h, True),
        outer_ctrl + (False,),
        (shoulder - span * .02, h - bend, True),
        (shoulder, h, False), (shoulder + bend, h, True),
        (r - bend, h, True), (r, h, False), (r, h - bend, True),
        (r, b + stem_cap, True), (r, b, False), (r - v * .5, b, True),
        (r - v, b, False), (r - v, b + stem_cap, True),
        (r - v, h - hbar, True),
        (shoulder + diag_w * .86, h - hbar, True),
        inner_ctrl + (False,),
        (foot_inner, b + foot_r * .45, True),
        (foot_inner - diag_w * .24, b, False),
    ])


def angled_branches(p, axis, side, y_mid=None, span_scale=1.0, thickness_scale=1.0):
    """Native Google Sans K/k-style two-piece polygon arm + lower leg flush inside [bottom..tip]."""
    span = (p.right - axis if side > 0 else axis - p.left) * span_scale
    reach = lambda dx: axis + side * dx
    mid_y = p.top * .47 if y_mid is None else y_mid
    v = min(p.v * thickness_scale, span * .48)
    arm_foot = min(v * 1.02, span * .39)
    leg_foot = min(v * 1.02, span * .39)
    arm_r = min(arm_foot * .49, v * .44 * p.roundness)
    leg_r = min(leg_foot * .49, v * .44 * p.roundness)
    join_half = max(p.h * thickness_scale * .55, min(v * .60, p.top * .22))
    upper_x = max(span - v * .05, arm_foot + v * .15)
    lower_x = max(span, leg_foot + v * .15)
    arm_start_x = -v * .08
    arm_lo_y = mid_y - join_half
    arm_top_end_l = upper_x - arm_foot
    p.polygon([
        (reach(arm_start_x), arm_lo_y),
        (reach(arm_start_x), mid_y + join_half),
        (reach(arm_top_end_l), p.tip),
        (reach(upper_x), p.tip),
    ], radius=arm_r)
    branch_lo_x = max(span * .12, v * .18)
    t_lo = min(max((branch_lo_x - arm_start_x) / max(arm_top_end_l - arm_start_x, 1.0), 0.0), .62)
    branch_lo_y = arm_lo_y + t_lo * (p.tip - arm_lo_y)
    branch_x = min(lower_x - v * .25, branch_lo_x + arm_foot * .56)
    t_hi = min(max((branch_x - arm_start_x) / max(arm_top_end_l - arm_start_x, 1.0), 0.10), .70)
    branch_y = (mid_y + branch_lo_y) * .5 + t_hi * (p.tip - mid_y) * .52
    p.polygon([
        (reach(branch_lo_x), branch_lo_y),
        (reach(branch_x), branch_y),
        (reach(lower_x), p.bottom),
        (reach(lower_x - leg_foot), p.bottom),
    ], radius=leg_r)


def che_curve(p, l, r, pen=None):
    pen = p if pen is None else pen
    h, tip = p.top, p.tip
    bowl_low = h * .38
    pen.stroke(
        (l, tip - pen.v * .48 * pen.roundness), (l, h * .63),
        ((l, bowl_low - p.h * .02), (r - (r - l) * .16, bowl_low - p.h * .05), (r, h * .45)),
        weight=.92,
        cap=pen.roundness * .95,
    )


def che(p):
    p.stem(p.right - p.v)
    che_curve(p, p.left + p.v * .48, p.right - p.v * .62)


def ze(p, reversed=False):
    """Single-contour Google Sans 3-aligned Cyrillic З/з with true waist taper and circular bowls."""
    l, r, h, w = p.left, p.right, p.top, p.width
    ov = p.curve_ov
    v_s = min(p.v * .92, w * .34)
    h_s = min(p.h * .90, h * .22)
    h_mid = min(p.h * .84, h * .19)
    mid_y = h * .515
    t_out, b_out = h + ov, -ov
    top_r = r - w * .045
    bot_r = r
    in_top_r = max(l + w * .38, top_r - v_s * .94)
    in_bot_r = max(l + w * .38, bot_r - v_s * .96)
    bar_l = max(l + w * .35, min(l + w * .44, in_top_r - v_s * .35))
    bar_b, bar_t = mid_y - h_mid * .5, mid_y + h_mid * .5
    waist_x = min(in_top_r - v_s * .05, bar_l + (in_top_r - bar_l) * .45)
    top_cx = l + w * .47
    bot_cx = l + w * .49
    in_t = t_out - h_s
    in_b = b_out + h_s
    y_tr = h * .75
    y_br = h * .265
    waist_r = min(top_r - w * .06, l + w * .76)
    tip_r = min(v_s * .34 * p.roundness, h_s * .44)
    tip_bot_l = l + w * .01
    tip_bot_y = h * .225
    tip_top_l = l + w * .04
    tip_top_y = h * .77
    in_tip_bx = l + w * .175
    in_tip_by = h * .265
    in_tip_tx = l + w * .195
    in_tip_ty = h * .73
    # Closed single contour tracing outer lower -> tips/counters -> upper -> right waist
    p.outline.contours.append([
        (bot_cx, b_out, True),
        (l + w * .19, b_out, False), (tip_bot_l, tip_bot_y - tip_r, True),
        (tip_bot_l, tip_bot_y + tip_r * .4, True), (l + w * .06, in_tip_by + tip_r * .8, False),
        (in_tip_bx, in_tip_by, True),
        (in_tip_bx + w * .09, in_b, False), (bot_cx, in_b, True),
        (bot_cx + (in_bot_r - bot_cx) * .46, in_b, False),
        (in_bot_r, in_b + (bar_b - in_b) * .28, False), (in_bot_r, (in_b + bar_b) * .5, True),
        (in_bot_r, bar_b - (in_b - bar_b) * .26, False),
        (waist_x + (in_bot_r - waist_x) * .55, bar_b, False), (waist_x, bar_b, True),
        (bar_l + tip_r, bar_b, True), (bar_l, bar_b, False), (bar_l, bar_b + tip_r, True),
        (bar_l, bar_t - tip_r, True), (bar_l, bar_t, False), (bar_l + tip_r, bar_t, True),
        (waist_x, bar_t, True),
        (waist_x + (in_top_r - waist_x) * .55, bar_t, False),
        (in_top_r, bar_t + (in_t - bar_t) * .24, False), (in_top_r, (bar_t + in_t) * .5, True),
        (in_top_r, in_t - (in_t - bar_t) * .26, False),
        (top_cx + (in_top_r - top_cx) * .46, in_t, False), (top_cx, in_t, True),
        (in_tip_tx + w * .08, in_t, False), (in_tip_tx, in_tip_ty, True),
        (tip_top_l + w * .06, tip_top_y - tip_r, False), (tip_top_l, tip_top_y, True),
        (tip_top_l, tip_top_y + tip_r * 1.4, False), (top_cx * .48, t_out, False), (top_cx, t_out, True),
        (top_cx + (top_r - top_cx) * .48, t_out, False), (top_r, y_tr + (t_out - y_tr) * .52, False), (top_r, y_tr, True),
        (top_r, mid_y + h * .10, False), (waist_r, mid_y + h * .01, True),
        (waist_r, mid_y - h * .01, True),
        (bot_r, mid_y - h * .10, False), (bot_r, y_br, True),
        (bot_r, b_out + (y_br - b_out) * .48, False), (bot_cx + (bot_r - bot_cx) * .48, b_out, False),
    ])
    if reversed:
        p.outline = p.outline.transform(sx=-1, dx=p.outline.advance)


def mirror(drawing):
    return drawing.transform(sx=-1, dx=drawing.advance)


def add_shape(p, shape, x, y=0, sx=1, sy=1):
    p.outline.add(shape.transform(sx=sx, sy=sy, dx=x, dy=y))


def stem_bowl(p, stem, bowl_top=None):
    p.stem(stem)
    p.bowl(stem, p.bottom * .55, p.right, p.top * .62 if bowl_top is None else bowl_top)


def open_e(p):
    """Clean C-like oval spine with Google Sans center arm for Є/Э/є/э."""
    l, c, r, h = p.left, p.mid, p.right, p.top
    pen = Pen(p.outline, p.v * .95, min(p.h, h * .255), p.roundness)
    x = l + pen.v * .48
    ov = p.v * .038 * p.roundness + 8
    high, mid, low = h + ov - pen.h * .48, h * .505, -ov + pen.h * .48
    pen.stroke(
        (r - .11 * p.width, h * .80),
        ((r - .22 * p.width, high), (c + .12 * p.width, high), (c, high)),
        ((l + .22 * p.width, high), (x, h * .75), (x, mid)),
        ((x, h * .25), (l + .22 * p.width, low), (c, low)),
        ((c + .14 * p.width, low), (r - .20 * p.width, low),
         (r - .10 * p.width, h * .20)),
        cap=p.roundness * .90,
    )
    p.bar(x, mid, l + p.width * .66, thickness=min(p.h * .92, h * .18))


def oval_counter(outline):
    # The O and o of the source typeface each have a single interior contour.
    return Outline(0, [outline.contours[1]]).bounds()


def modern_letters(d):
    for cap in (True, False):
        def put(characters, drawing):
            d.put(characters[0 if cap else 1], drawing)

        p = d.letter(cap, 880 if cap else 690, right=78)
        l, r, h = p.left, p.right, p.top
        corner_r = min(p.v * .32 * p.roundness, p.h * .46)
        bar_r = min(p.h * .50 * p.roundness, p.v * .32 * p.roundness)
        p.stem(l, top=h, top_r=corner_r)
        p.bar(l + .35 * p.v, h - p.h / 2, r, radius=bar_r)
        put("Гг", p)

        p = d.letter(cap, 930 if cap else 730, right=77)
        l, r, h = p.left, p.right, p.top
        corner_r = min(p.v * .32 * p.roundness, p.h * .46)
        bar_r = min(p.h * .50 * p.roundness, p.v * .32 * p.roundness)
        p.stem(l, top=h, top_r=corner_r)
        p.bar(l + .35 * p.v, h - p.h / 2, r, radius=bar_r)
        p.stem(r - p.v * .60, bottom=h - p.h, top=h + (175 if cap else 135), weight=.60)
        put("Ґґ", p)

        p = d.letter(cap, 1065 if cap else 885)
        legs(p, 2)
        put("Пп", p)

        p = d.letter(cap, 1080 if cap else 885, growth=.62 if cap else 1.02)
        p.stem(p.left)
        p.stem(p.right - p.v)
        p.bar(p.left + .45 * p.v, p.top * .505, p.right - .45 * p.v,
              thickness=None if cap else min(p.h * .92, p.top * .22))
        if not cap:
            put("Нн", p)

        if cap:
            put("Ии", mirror(d.get("N")))
        else:
            p = d.letter(False, 910, growth=.85)
            cyrillic_i(p)
            put("Ии", p)

        p = d.letter(cap, 1095 if cap else 885)
        legs(p, 2, floor=True)
        tail(p, p.right - p.v * .38, 290 if cap else 235, 0, width_scale=.84)
        put("Цц", p)

        p = d.letter(cap, 1055 if cap else 855)
        legs(p, 2, floor=True)
        tail(p, p.mid, 295 if cap else 240, 0, width_scale=.86)
        put("Џџ", p)

        p = d.letter(cap, 1520 if cap else 1245, growth=1.22)
        legs(p, 3, floor=True, stem_scale=.90)
        put("Шш", p)

        p = d.letter(cap, 1540 if cap else 1265, growth=1.22)
        legs(p, 3, floor=True, stem_scale=.90)
        tail(p, p.right - p.v * .38, 290 if cap else 235, 0, width_scale=.84)
        put("Щщ", p)

        p = d.letter(cap, 1040 if cap else 885, left=56, growth=.82 if cap else .85)
        el(p)
        put("Лл", p)

        p = d.letter(cap, 1150 if cap else 960, left=55, right=64, growth=.90)
        el(p)
        ext_l, ext_r = p.v * .18, p.v * .18
        l, r, b = p.left - ext_l, p.right + ext_r, max(0.0, p.bottom * .08)
        y = b + min(p.h * .80, p.top * .16)
        foot = min(p.v * .74, p.width * .17)
        depth = 290 if cap else 240
        p.bar(l, (b + y) * .5, r, thickness=y - b, radius=min((y - b) * .35 * p.roundness, 32.0))
        p.stem(l, bottom=-depth, top=y, weight=foot / p.v)
        p.stem(r - foot, bottom=-depth, top=y, weight=foot / p.v)
        p.outline = p.outline.transform(dx=ext_l)
        p.outline.advance += ext_l + ext_r
        put("Дд", p)

        p = d.letter(cap, 1550 if cap else 1310, left=36, right=36, growth=1.10)
        p.stem(p.mid - p.v * .43, weight=.86)
        angled_branches(p, p.mid, -1, thickness_scale=.84)
        angled_branches(p, p.mid, 1, thickness_scale=.84)
        put("Жж", p)

        if not cap:
            p = d.letter(cap, 885, right=35, growth=1.04)
            p.stem(p.left)
            angled_branches(p, p.left + .54 * p.v, 1)
            put("Кк", p)

            # Google Sans Flex M-aligned lowercase м reaching baseline cleanly.
            p = d.letter(cap, 1140, growth=1.04)
            l, r, b, h, tip = p.left, p.right, p.bottom, p.top, p.tip
            v = min(p.v * .92, p.width * .28)
            wt = min(max((p.v - 160.0) / 240.0, 0.0), 1.0)
            cap_r = v * .50 * p.roundness
            bot_r = min(cap_r, v * .28 * p.roundness)
            top_diag = min(v * 1.44, p.width * .39)
            apex_half = min(v * .31, p.width * .125)
            valley_y = h * (.18 + .15 * wt)
            notch_y = h * (.80 - .26 * wt)
            p.outline.contours.append([
                (l + v * .5, b, True), (l, b, False), (l, b + cap_r, True),
                (l, tip - cap_r, True), (l, tip, False), (l + top_diag * .48, tip, True),
                (l + top_diag * .68, tip, False), (l + top_diag * .94, tip - cap_r * .38, True),
                (p.mid - 4, valley_y, True), (p.mid + 4, valley_y, True),
                (r - top_diag * .94, tip - cap_r * .38, True),
                (r - top_diag * .68, tip, False), (r - top_diag * .48, tip, True),
                (r, tip, False), (r, tip - cap_r, True),
                (r, b + cap_r, True), (r, b, False), (r - v * .5, b, True),
                (r - v, b, False), (r - v, b + cap_r, True),
                (r - v, notch_y, True),
                (p.mid + apex_half, b + bot_r, True),
                (p.mid, b, False),
                (p.mid - apex_half, b + bot_r, True),
                (l + v, notch_y, True),
                (l + v, b + cap_r, True),
                (l + v, b, False),
            ])
            put("Мм", p)

            p = d.letter(cap, 910, left=44, right=44)
            p.bar(p.left, p.top - .5 * p.h, p.right, radius=p.h * .50 * p.roundness)
            p.stem(p.mid - .5 * p.v, top=p.top - .25 * p.h)
            put("Тт", p)

        if cap:
            put("Зз", d.get("3").transform())
        else:
            p = d.letter(cap, 800, left=72, right=68, growth=.80)
            ze(p)
            put("Зз", p)

        if cap:
            p = d.letter(cap, 1005, right=80)
            corner_r = min(p.v * .32 * p.roundness, p.h * .46)
            p.stem(p.left, top=p.top, top_r=corner_r)
            p.bowl(p.left, p.bottom * .55, p.right, p.top * .62 + p.h * .07)
            p.bar(p.left + p.v * .32, p.top - p.h / 2, p.right - p.width * .065,
                  radius=min(p.h * .50 * p.roundness, p.v * .32 * p.roundness))
            put("Бб", p)
        else:
            p = d.letter(cap, 1048, left=72, right=72, growth=.78)
            l, r, h, w = p.left, p.right, p.top, p.width
            ov = p.curve_ov
            p.oval(l, -ov, r, h + ov, wall=.95)
            pen = Pen(p.outline, p.v * .88, min(p.h * .92, h * .24), p.roundness)
            asc_y = 1432 + 10 * min(max((p.v - 170.0) / 230.0, 0.0), 1.0) - pen.h * .50
            end_x = r - pen.v * .46 - w * .04
            pen.stroke(
                (l + pen.v * .50, h * .49),
                ((l + pen.v * .48, h * 1.00), (l + w * .22, asc_y), (min(end_x, l + w * .44), asc_y)),
                (end_x, asc_y),
                weight=1.0,
                cap=p.roundness * .95,
            )
            put("Бб", p)

            # Native Google Sans B-matched lowercase в at x-height with 2-handle circular bowls.
            p = d.letter(cap, 910, left=126, right=78, growth=.85)
            l, r, h, w = p.left, p.right, p.top, p.width
            v_s = p.v * .92
            r_c = min(v_s * .54 * p.roundness, h * .20)
            y_mid = h * .52
            r_top = r - w * .065
            waist_x = l + v_s + (r - l - v_s) * .58
            h_outer = min(p.h * .86, h * .185)
            h_mid = min(p.h * .80, h * .175)
            wall_bot = min(p.v * .90, w * .28, max(w * .14, (r - l - v_s) * .45))
            wall_top = min(p.v * .88, (r_top - l - v_s) * .45)
            sh_top = l + w * .42
            sh_bot = l + w * .44
            y_tc = (y_mid + h) * .51
            y_bc = y_mid * .49
            p.outline.contours.append([
                (l + r_c, 0, True), (l, 0, False), (l, r_c, True),
                (l, h - r_c, True), (l, h, False), (l + r_c, h, True),
                (sh_top, h, True),
                (sh_top + (r_top - sh_top) * .42, h, False),
                (r_top, y_tc + (h - y_tc) * .54, False),
                (r_top, y_tc, True),
                (r_top, y_mid + (y_tc - y_mid) * .35, False),
                (waist_x, y_mid + h * .015, True),
                (waist_x, y_mid - h * .015, True),
                (r, y_mid - (y_mid - y_bc) * .38, False),
                (r, y_bc, True),
                (r, y_bc * .46, False),
                (sh_bot + (r - sh_bot) * .42, 0, False),
                (sh_bot, 0, True),
            ])
            c_l = l + v_s
            top_mid = y_mid - h_mid * .5
            bot_mid = y_mid + h_mid * .5
            in_rb = r - wall_bot
            in_rt = r_top - wall_top
            sh_in_b = min(sh_bot, c_l + max(0.0, (in_rb - c_l) * .40))
            sh_in_t = min(sh_top, c_l + max(0.0, (in_rt - c_l) * .40))
            mid_b_h = (h_outer + top_mid) * .5
            mid_t_h = (bot_mid + h - h_outer) * .5
            p.outline.contours.append([
                (c_l, h_outer, True), (sh_in_b, h_outer, True),
                (sh_in_b + (in_rb - sh_in_b) * .44, h_outer, False),
                (in_rb, mid_b_h - (mid_b_h - h_outer) * .54, False),
                (in_rb, mid_b_h, True),
                (in_rb, mid_b_h + (top_mid - mid_b_h) * .54, False),
                (sh_in_b + (in_rb - sh_in_b) * .44, top_mid, False),
                (sh_in_b, top_mid, True), (c_l, top_mid, True),
            ])
            p.outline.contours.append([
                (c_l, bot_mid, True), (sh_in_t, bot_mid, True),
                (sh_in_t + (in_rt - sh_in_t) * .44, bot_mid, False),
                (in_rt, mid_t_h - (mid_t_h - bot_mid) * .54, False),
                (in_rt, mid_t_h, True),
                (in_rt, mid_t_h + (h - h_outer - mid_t_h) * .54, False),
                (sh_in_t + (in_rt - sh_in_t) * .44, h - h_outer, False),
                (sh_in_t, h - h_outer, True), (c_l, h - h_outer, True),
            ])
            put("Вв", p)

        p = d.letter(cap, 955 if cap else 805, right=82)
        stem_bowl(p, p.left)
        put("Ьь", p)

        p = d.letter(cap, 1135 if cap else 945, left=44, right=77, growth=.85)
        corner_r = min(p.v * .32 * p.roundness, p.h * .46)
        stem = p.left + max(p.width * .23, p.v * .65)
        p.stem(stem, top=p.top, top_r=corner_r)
        p.bowl(stem, p.bottom * .55, p.right, p.top * .62)
        p.bar(p.left, p.top - p.h / 2, stem + p.v * .72,
              radius=min(p.h * .50 * p.roundness, p.v * .32 * p.roundness))
        put("Ъъ", p)

        p = d.letter(cap, 1410 if cap else 1205, growth=1.15)
        stem_w = .92
        p.stem(p.right - p.v * stem_w, weight=stem_w)
        p.stem(p.left, weight=stem_w)
        p.bowl(p.left, p.bottom * .55, p.right - p.width * .24 - p.v * .48, p.top * .63)
        put("Ыы", p)

        p = d.letter(cap, 1090 if cap else 890, growth=.82)
        che(p)
        put("Чч", p)

        p = d.letter(cap, 1445 if cap else 1255, left=76, right=76, growth=.68)
        low, high = (p.top * .10, p.top * .90) if cap else (-p.curve_ov, p.top + p.curve_ov)
        min_gap = p.top * (.165 if cap else .175)
        wall = min(p.v * .85, max(p.v * .42, (p.width - p.v) / 2 - min_gap))
        Pen(p.outline, wall, min(p.h * .92, (high - low) * .28), p.roundness).oval(p.left, low, p.right, high)
        p.stem(p.mid - p.v / 2, p.bottom if cap else -431, p.tip if cap else 1432 + p.v * .045 * p.roundness)
        put("Фф", p)

        if cap:
            # Unified seamless Y/y-aligned uppercase У bounded inside [l..r] and [bottom..tip].
            p = d.letter(cap, 1110, left=38, right=42, growth=.88)
            l, r, h, b, tip, w = p.left, p.right, p.top, p.bottom, p.tip, p.width
            v_a = min(p.v, w * .34)
            arm_foot = min(v_a * 1.12, w * .34)
            cap_r = min(arm_foot * .48, v_a * .45 * p.roundness)
            h_bar = min(p.h * .94, h * .22)
            fork_y = h * .38
            inner_vy = h * .55
            tail_l = max(l + w * .04, l + v_a * .08)
            tail_r = min(cap_r, h_bar * .48)
            p.outline.contours.append([
                (tail_l + tail_r, b, True),
                (tail_l, b, False), (tail_l, b + tail_r, True),
                (tail_l, b + h_bar - tail_r, True),
                (tail_l, b + h_bar, False), (tail_l + tail_r, b + h_bar, True),
                (tail_l + w * .18, b + h_bar, False),
                (p.mid - v_a * .34, fork_y + h_bar * .34, True),
                (l + cap_r * .3, tip - cap_r, True),
                (l, tip, False), (l + cap_r, tip, True),
                (l + arm_foot - cap_r, tip, True),
                (l + arm_foot, tip, False), (l + arm_foot + v_a * .08, tip - cap_r, True),
                (p.mid - 4, inner_vy, True), (p.mid + 4, inner_vy, True),
                (r - arm_foot - v_a * .08, tip - cap_r, True),
                (r - arm_foot, tip, False), (r - arm_foot + cap_r, tip, True),
                (r - cap_r, tip, True),
                (r, tip, False), (r - cap_r * .3, tip - cap_r, True),
                (p.mid + v_a * .38, fork_y, True),
                (p.mid + w * .06, b + h_bar * .12, False),
                (tail_l + w * .30, b, False),
            ])
            put("Уу", p)

        if cap:
            c_base = d.get("C")
            l_c, b_c, r_c, t_c = c_base.bounds()
            p = d.letter(cap, 1)
            p.outline = c_base.transform()
            p.bar(l_c + p.v * .55, (b_c + t_c) / 2, l_c + (r_c - l_c) * .63,
                  thickness=min(p.h, (t_c - b_c) * .18),
                  radius=min(p.h * .50 * p.roundness, p.v * .32 * p.roundness))
            put("Єє", p)
            put("Ээ", mirror(p.outline))
        else:
            c_base = d.get("c")
            l_c, b_c, r_c, t_c = c_base.bounds()
            p = d.letter(cap, 1)
            p.outline = c_base.transform()
            p.bar(l_c + p.v * .55, (b_c + t_c) / 2, l_c + (r_c - l_c) * .64,
                  thickness=min(p.h * .92, (t_c - b_c) * .18),
                  radius=min(p.h * .46 * p.roundness, p.v * .32 * p.roundness))
            put("Єє", p)
            put("Ээ", mirror(p.outline))

        p = d.letter(cap, 1)
        o = d.get("O" if cap else "o")
        p.stem(p.left, weight=.94)
        origin = p.left + p.v * .50 + (310 if cap else 255)
        add_shape(p, o, origin, sx=.91)
        p.bar(p.left + p.v * .35, p.top * .505, origin + o.bounds()[0] * .91 + p.v * .45, thickness=p.h * .90)
        p.outline.advance = origin + o.advance * .91
        put("Юю", p)

        if cap:
            put("Яя", mirror(d.get("R")))
        else:
            # Native Google Sans R-mirrored lowercase я with flush baseline leg and open bowl.
            p = d.letter(cap, 900, left=42, right=126, growth=.96)
            l, r, b, h = p.left, p.right, p.bottom, p.top
            p.stem(r - p.v)
            bowl_p = d.letter(cap, 1)
            bowl_p.bowl(0, h * .36, p.width, h, bar_scale=.80, wall_scale=.86)
            p.outline.add(bowl_p.outline.transform(sx=-1, dx=r))
            leg_foot = min(p.v * .98, p.width * .30)
            leg_r = min(leg_foot * .49, p.v * .44 * p.roundness)
            p.polygon([
                (p.mid - p.v * .04, h * .43),
                (p.mid + leg_foot * .84, h * .43),
                (l + leg_foot, b),
                (l, b),
            ], radius=leg_r)
            put("Яя", p)

        if cap:
            p = d.letter(cap, 1306, left=86, right=95, growth=.11)
            l, c, r, h = p.left, p.mid, p.right, p.top
            x0, x1 = l + p.v / 2, r - p.v / 2
            y = h * .501
            high, low = h + 29 - p.h / 2, -29 + p.h / 2
            p.stroke((l + .18 * p.width, .83 * h),
                     ((l + .46 * p.width, high + 132), (x1, high + 90), (x1, y)),
                     ((x1, h * .195), (r - .22 * p.width, low), (c, low)),
                     ((l + .25 * p.width, low), (x0, h * .192), (x0, y)),
                     weight=1.02)
            p.bar(l + p.v * .25, y, r - p.v * .29)
        else:
            p = d.letter(cap, 1)
            base = d.get("e")
            _, low, _, high = base.bounds()
            p.outline = base.transform(sx=-1, sy=-1, dx=base.advance, dy=high + low)
        put("Әә", p)

        p = d.letter(cap, 1)
        p.outline = d.get("O" if cap else "o").transform()
        l, _, r, _ = p.outline.bounds()
        _, low, _, high = oval_counter(p.outline)
        p.bar(l + p.v * .37, (low + high) / 2, r - p.v * .37,
              thickness=min(p.h * .92, (high - low) * .40))
        put("Өө", p)


def ornament(d, cap, source, form, place=1):
    p = d.letter(cap, 1)
    p.outline = d.get(source, cap).transform()
    l, b, r, h = p.outline.bounds()
    x = l + (r - l) * place
    if form == "descender":
        tail(p, x - p.v * .37, 344 if cap else 272, 0)
        p.outline.advance += .12 * p.v
    elif form == "tail":
        step = p.v * .40
        depth = 320 if cap else 255
        p.polygon([(x - p.v * .76, .42 * p.h), (x - .08 * p.v, .42 * p.h),
                   (x - .08 * p.v, -.12 * p.h), (x + step, -.12 * p.h),
                   (x + step - .82 * p.v, -depth), (x - .86 * p.v, -depth),
                   (x - .52 * p.v, -.28 * p.h), (x - p.v * .76, -.28 * p.h)],
                  radius=min(p.v * .32 * p.roundness, 36.0))
        p.outline.advance += .28 * p.v
    elif form == "cedilla":
        p.stroke((x + p.v * .12, b + p.h * .33), (x - p.v * .08, b - 96),
                 ((x + 200, b - 92), (x + 161, b - 404), (x - 100, b - 286)),
                 weight=.62)
    elif form in ("hook", "left hook"):
        side = 1 if form == "hook" else -1
        x -= side * p.v * .47
        p.stroke((x, b + p.h * .47), (x, b - 101),
                 ((x, b - 248), (x + side * 134, b - 342), (x + side * 253, b - 248)),
                 weight=.83)
        if side > 0:
            p.outline.advance += 165
        else:
            p.outline = p.outline.transform(dx=165)
            p.outline.advance += 165
    elif form == "tick":
        p.diagonal(x - p.v * .23 - 40, h * (.445 if cap else .21),
                   x + 135, h * (.12 if cap else -.095), weight=.69)
    elif form == "bar":
        p.bar(x - (r - l) * .36, h * .53, x + (r - l) * .36)
    elif form == "vertical":
        p.box(x - p.v * .30, h * .24, x + p.v * .30, h * .76)
    elif form == "slash":
        p.diagonal(x - (r - l) * .07, h * .94, x + (r - l) * .25, h * .64, weight=.71)
    else:
        raise ValueError(form)
    return p


def serbian_letters(d, cap, put):
    for letters, base in (("Љљ", "Лл"), ("Њњ", "Нн")):
        p = d.letter(cap, 1)
        p.outline = d.get(base[0 if cap else 1]).transform()
        _, _, r, _ = p.outline.bounds()
        bowl_w = (705 if cap else 600) + max(0.0, p.v - 170.0) * .45
        p.bowl(r - p.v, p.bottom, r + bowl_w, p.top * .63)
        p.outline.advance += bowl_w
        put(letters, p)

    for letters, hook in (("Ђђ", True), ("Ћћ", False)):
        p = d.letter(cap, 1040 if cap else 895, left=83, growth=.88)
        l, r, h = p.left, p.right, p.top
        shoulder = h * (.73 if cap else 1.027)
        high = h if cap else 1432
        stem = l + (p.width * (.19 if hook else .21) if cap else p.width * .075)
        p.stem(stem, top=high)
        p.bar(l, high - p.h * (.50 if cap else 1.47),
              l + p.width * (.80 if cap else .46))
        x = r - p.v * .54
        curve = [
            ((stem + p.width * .44, shoulder + p.h * .01), (x, shoulder + p.h * .04),
             (x, shoulder - h * .23)),
        ]
        if hook and cap:
            end = max(p.mid + .08 * p.v, stem + p.v + p.h * .49 * p.roundness + 51)
            bottom = p.bottom + p.h * .47
            curve.append(((x, h * .12), (end + (x - end) * .62, bottom), (end, bottom)))
        elif hook:
            curve.extend([(x, -h * .10),
                          ((x, -h * .31), (r - p.width * .31, -h * .36),
                           (p.mid + p.v * .08, -h * .255))])
        else:
            curve.append((x, p.bottom + p.roundness * p.h * .42))
        p.stroke((stem + p.v * .68, shoulder - h * .145), *curve, weight=.91 if hook else 1)
        put(letters, p)


def extended_letters(d):
    for cap in (True, False):
        i = 0 if cap else 1

        def put(characters, drawing):
            d.put(characters[i], drawing)

        serbian_letters(d, cap, put)

        forms = (
            ("Җҗ", "Жж", "descender", .95), ("Ққ", "Кк", "descender", .95),
            ("Ңң", "Нн", "descender", 1), ("Ҳҳ", "Хх", "descender", .94),
            ("Ҷҷ", "Чч", "descender", 1),
            ("Ԥԥ", "Пп", "descender", 1),
            ("Ԯԯ", "Лл", "descender", 1), ("Ӷӷ", "Гг", "descender", .23),
            ("Ҙҙ", "Зз", "cedilla", .57), ("Ҫҫ", "Сс", "cedilla", .54),
            ("Ӆӆ", "Лл", "tail", 1), ("Ӊӊ", "Нн", "tail", 1),
            ("Ӎӎ", "Мм", "tail", 1), ("Ӽӽ", "Хх", "hook", .92),
            ("Ԩԩ", "Нн", "left hook", 0),
            ("Ҏҏ", "Рр", "tick", .81), ("Ҹҹ", "Чч", "vertical", .56),
            ("Ҝҝ", "Кк", "vertical", .33),
            ("Ԟԟ", "Кк", "slash", .55), ("Ӿӿ", "Хх", "bar", .52),
        )
        for characters, base, shape, place in forms:
            put(characters, ornament(d, cap, base[i], shape, place))

        p = d.letter(cap, 1)
        p.outline = d.get("Кк"[i]).transform()
        l, _, r, h = p.outline.bounds()
        p.bar(l - .15 * (r - l), h * .87, l + .31 * (r - l))
        p.outline = p.outline.transform(dx=.06 * (r - l))
        p.outline.advance += .06 * (r - l)
        put("Ҟҟ", p)

        p = d.letter(cap, 1)
        p.outline = d.get("Тт"[i]).transform()
        x = (p.outline.bounds()[0] + p.outline.bounds()[2]) / 2 + .20 * p.v
        p.box(x, -.56 * p.h, x + .83 * p.v, .49 * p.h)
        p.box(x + p.v * .61, -328 if cap else -274, x + 1.40 * p.v, -.16 * p.h)
        put("Ҭҭ", p)

        p = d.letter(cap, 1120 if cap else 890, right=107)
        x = p.right - p.v * .52
        p.stem(p.left, top=p.top if cap else 1432)
        p.stroke((p.left + p.v * .69, p.top * (.54 if cap else .69)),
                 ((p.mid, p.top * (.85 if cap else 1.12)),
                  (x, p.top * (.89 if cap else 1.13)), (x, p.top * .57)),
                 (x, p.bottom + p.roundness * p.h * .46))
        if cap:
            put("Һһ", p)
        put("Ԧԧ", ornament(d, cap, "Һһ"[i], "descender"))

        p = d.letter(cap, 918 if cap else 743, left=34, right=30, growth=.58)
        if cap:
            p.outline = d.get("Y").transform()
        else:
            p.stem(p.mid - p.v / 2, bottom=-431, top=p.top * .36)
            inset = min(p.width * .30, p.v * 1.04)
            p.polygon([(p.left, p.top), (p.left + inset, p.top),
                       (p.mid, p.top * .34 + p.h * .6), (p.right - inset, p.top),
                       (p.right, p.top), (p.mid + .46 * p.v, p.top * .23),
                       (p.mid - .46 * p.v, p.top * .23)])
            put("Үү", p)
        p.bar(p.outline.advance * .5 - 240, p.top * (.38 if cap else .20), p.outline.advance * .5 + 240)
        put("Ұұ", p)

        p = d.letter(cap, 1)
        p.outline = d.get("Гг"[i]).transform()
        l, _, _, _ = p.outline.bounds()
        p.bar(l - 108, p.top * .53, l + p.v + 257)
        p.outline = p.outline.transform(dx=70)
        p.outline.advance += 70
        put("Ғғ", p)

        p = d.letter(cap, 960 if cap else 777, right=70)
        p.stem(p.left)
        p.bar(p.left + p.v * .39, p.top - p.h / 2, p.right - .21 * p.width)
        x = p.right - p.v * .5
        p.stroke((p.left + p.v * .58, p.top * .62),
                 ((p.mid + p.v * .42, p.top * .61), (x, p.top * .47), (x, p.top * .21)),
                 ((x, -p.h * .25), (p.mid - .05 * p.width, -p.h * .95), (p.left, -p.h * .70 - 82)))
        put("Ҕҕ", p)

        p = ornament(d, cap, "Ғғ"[i], "cedilla", .15)
        put("Ӻӻ", p)

        p = d.letter(cap, 1047 if cap else 858, left=83, right=25)
        stem = p.left + p.width * .17
        p.stem(stem)
        p.bar(p.left, p.top - p.h / 2, stem + p.v * .76)
        angled_branches(p, stem + p.v * .56, 1)
        put("Ҡҡ", p)

        p = d.letter(cap, 1480 if cap else 1220, growth=.99, right=70)
        p.stem(p.left)
        joint = p.mid + p.v * .32
        p.stem(joint)
        p.bar(p.left + .36 * p.v, p.top * .51, joint + .67 * p.v)
        p.bar(joint + .30 * p.v, p.top - p.h / 2, p.right)
        put("Ҥҥ", p)

        p = d.letter(cap, 1515 if cap else 1270, growth=1.12, right=74)
        joint = p.mid + p.v * .13
        p.stem(p.left)
        p.stem(joint)
        p.bar(p.left + .35 * p.v, p.top - p.h / 2, joint + .65 * p.v)
        p.stroke((joint + p.v * .53, p.top * .57),
                 ((p.right + p.v * .29, p.top * .55), (p.right + p.v * .23, -p.h * .95 - 65),
                  (joint + p.v * .27, -p.h * .59 - 119)),
                 weight=.95)
        put("Ҧҧ", p)

        p = d.letter(cap, 1282 if cap else 1060, left=80, right=51)
        l, r, h = p.left, p.right, p.top
        x0, x1, high, low = l + p.v / 2, r - p.v / 2, h + 26 - p.h / 2, -27 + p.h / 2
        p.stroke((l + .59 * p.width, high),
                 ((l + .28 * p.width, high + 43), (x0, h * .76), (x0, h * .49)),
                 ((x0, h * .20), (l + .22 * p.width, low), (p.mid, low)),
                 ((r - .22 * p.width, low), (x1, h * .24), (x1, h * .55)),
                 ((x1, h * .98), (l + .60 * p.width, h * 1.002), (l + .60 * p.width, h * .54)),
                 ((l + .60 * p.width, h * .26), (r - .16 * p.width, low), (r, low)),
                 weight=.93)
        put("Ҩҩ", p)

        p = d.letter(cap, 1)
        p.outline = d.get("Цц"[i]).transform(dx=(250 if cap else 195))
        l, _, r, h = p.outline.bounds()
        p.box(l - (292 if cap else 238), h - p.h, r - p.v * .77, h)
        p.outline.advance += 250 if cap else 195
        put("Ҵҵ", p)

        p = d.letter(cap, 1)
        p.outline = d.get("e", cap).transform(sx=1.32 if cap else 1.0,
                                             sy=p.top / d.profiles[False][0] if cap else 1)
        p.outline = p.outline.transform(dx=120)
        l, _, _, _ = p.outline.bounds()
        y = p.top * .47
        p.stroke((l + p.v * .67, y), (l - 29, y),
                 ((l - 173, y), (l - 185, y + 120), (l - 121, y + 206)),
                 weight=.72)
        p.outline.advance += 120
        put("Ҽҽ", p)
        put("Ҿҿ", ornament(d, cap, "Ҽҽ"[i], "descender", .59))

        p = d.letter(cap, 874 if cap else 738, right=67)
        l, r, h = p.left, p.right, p.top
        p.stem(l)
        p.polygon([(l + p.v * .41, h * .47), (r - 1.25 * p.v, h),
                   (r, h), (l + p.v * 1.10, h * .445)])
        x = r - .48 * p.v
        p.stroke((l + .58 * p.v, h * .49),
                 ((x, h * .40), (x, h * .16), (x, -p.h * .35)),
                 ((x, -.39 * h), (l + p.v * .63, -.35 * h), (l + p.v * .48, -.236 * h)),
                 weight=.97)
        put("Ӄӄ", p)

        p = d.letter(cap, 1)
        p.outline = d.get("Нн"[i]).transform()
        l, _, r, _ = p.outline.bounds()
        end = -max(214, p.h * .59 + 79)
        p.stroke((r - p.v * .51, p.h * .55),
                 ((r + p.v * .05, -384), (l + p.v * .93, end - 163), (l + p.v * .27, end)))
        put("Ӈӈ", p)

        p = d.letter(cap, 1060 if cap else 850)
        che(p)
        tail(p, p.right - p.v * .50, 354 if cap else 289, -1)
        p.box(p.right - .97 * p.v, -190, p.right - .26 * p.v, p.h * .4)
        put("Ӌӌ", p)

        p = d.letter(cap, 842 if cap else 724, left=52, right=49)
        p.stroke((p.left + p.width * .09, p.top - p.h * .46),
                 (p.right - p.v * .30, p.top - p.h * .46),
                 (p.mid - p.v * .31, p.top * .53),
                 ((p.right, p.top * .53), (p.right + p.v * .07, -176),
                  (p.left + p.width * .10, p.h * .92)), cap=p.roundness * .75)
        put("Ӡӡ", p)

        for letters, base in (("Ԡԡ", "Лл"), ("Ԣԣ", "Нн")):
            p = d.letter(cap, 1)
            p.outline = d.get(base[i]).transform()
            r = p.outline.bounds()[2]
            x = r - p.v * .43
            length = (625 if cap else 502) + (p.v - 170) * .61
            p.stroke((x, p.top * .59),
                     ((x + length, p.top * .64), (r + length, -.24 * p.top),
                      (x + max(.37 * p.v, p.v * .85 - 35), -.204 * p.top)), weight=.89)
            p.outline.advance += length * .86
            put(letters, p)

        p = d.letter(cap, 1)
        p.outline = d.get("Лл"[i]).transform()
        x = p.outline.bounds()[2] - .50 * p.v
        p.stroke((x, .48 * p.h), (x, -.49 * p.h),
                 ((x, -309), (x - 139, -335), (x - 228, -248)), weight=.90)
        put("Ԓԓ", p)

        p = d.letter(cap, 1)
        p.outline = d.get("Яя"[i]).transform()
        component = d.get("E" if cap else "e")
        x = p.outline.bounds()[2] - component.bounds()[0] - p.v * .40
        add_shape(p, component, x, sx=.76)
        p.outline.advance = x + component.advance * .76
        put("Ԙԙ", p)

        p = d.letter(cap, 1)
        p.outline = d.get("Лл"[i]).transform()
        x0 = p.outline.bounds()[2]
        ha = d.get("X" if cap else "x")
        x = x0 - ha.bounds()[0] * .87 - min(p.v * .49, 146)
        add_shape(p, ha, x, sx=.87)
        p.outline.advance = x + ha.advance * .87
        put("Ԕԕ", p)

        p = d.letter(cap, 1)
        base = d.get("P" if cap else "p")
        p.outline = base.transform()
        branch = d.letter(cap, 930 if cap else 740, right=25)
        angled_branches(branch, branch.left + branch.v * .6, 1)
        x = base.bounds()[2] - branch.left - branch.v * 1.34
        add_shape(p, branch.outline, x, sx=.82)
        p.outline.advance = x + branch.outline.advance * .82
        put("Ԗԗ", p)


def join(p, body, x, y=None, sx=1, sy=1):
    add_shape(p, body, x, sx=sx, sy=sy)
    l, _, _, _ = body.bounds()
    p.bar(p.left + .45 * p.v, p.top * .51 if y is None else y, x + l * sx + p.v * .42)
    p.outline.advance = x + body.advance * sx
    return p


def terminal_hook(p, x, y, width, direction=1):
    edge = x + direction * width
    end_y = y + width * .65
    p.stroke((x, y + width * .58), (x, y),
             ((x, y - width * .63), (edge, y - width * .77), (edge, y + width * .04)),
             (edge, end_y), weight=.84)


def komi_and_supplement(d):
    for cap in (True, False):
        i = 0 if cap else 1

        def put(characters, drawing):
            d.put(characters[i], drawing)

        p = d.letter(cap, 944 if cap else 950, left=70)
        if cap:
            stem = p.right - p.v
            bowl = d.letter(cap, 1)
            bowl.bowl(p.left, p.bottom, p.right, p.top * .59)
            p.outline.add(bowl.outline.transform(sx=-1, dx=p.left + p.right))
            p.stem(stem)
            put("Ԁԁ", p)

        for characters, base in (("Ԃԃ", "Ԁԁ"), ("Ԉԉ", "Лл"), ("Ԋԋ", "Нн")):
            p = d.letter(cap, 1)
            p.outline = d.get(base[i]).transform()
            _, _, r, _ = p.outline.bounds()
            size = 480 if cap else 390
            terminal_hook(p, r - p.v / 2, p.h * .31, size)
            p.outline.advance += size - 30
            put(characters, p)

        p = d.letter(cap, 875 if cap else 735, left=85 if cap else 65, right=82 if cap else 64)
        ze(p)
        put("Ԅԅ", ornament(d, cap, "Зз"[i], "hook", .93))
        put("Ԇԇ", ornament(d, cap, "Зз"[i], "descender", .94))
        ze(p, reversed=True)
        put("Ԑԑ", p)

        if not cap:
            p = d.letter(False, 1)
            p.outline = d.get("G", False).transform(sx=.78, sy=d.profiles[False][0] / 1432)
            put("Ԍԍ", p)

        p = d.letter(cap, 1120 if cap else 870, left=48, right=68)
        x = p.left + p.width * .37 + p.v / 2
        edge = p.right - p.v * .47
        p.bar(p.left, p.top - p.h * .5, p.right - 125)
        p.stroke((x, p.top - p.h * .52), (x, p.h * .61),
                 ((x, -.15 * p.h), (edge, -.32 * p.h), (edge, p.h * .67)),
                 (edge, p.top * .48))
        put("Ԏԏ", p)

        p = d.letter(cap, 1010 if cap else 821, left=52, right=72)
        stem_bowl(p, p.left + .28 * p.width, p.top * .59)
        p.bar(p.left, p.top - p.h / 2, p.right - .17 * p.width)
        put("Ᲊᲊ", p)

        p = d.letter(cap, 1)
        p.outline = d.get("Дд"[i]).transform()
        ka = d.get("Кк"[i])
        x = p.outline.bounds()[2] - .81 * p.v - ka.bounds()[0] * .81
        add_shape(p, ka, x, sx=.81)
        p.outline.advance = x + ka.advance * .81
        put("Ԫԫ", p)

        p = d.letter(cap, 1110 if cap else 947, left=83, right=77, growth=1.03)
        l, r, h = p.left, p.right, p.top
        wall = min(p.v, p.width * .177)
        floor = min(p.h, h * .155)
        p.stem(r - p.v)
        shoulder = Pen(p.outline, wall, min(p.h, h * .182), p.roundness)
        che_curve(p, l + wall / 2, r - p.v * .73, pen=shoulder)
        p.polygon([(l, p.bottom + floor * .46), (l + wall * 1.25, p.bottom + floor * .52),
                   (r - .33 * p.v, h), (r - .37 * p.v - wall, h)], radius=p.radius * .36)
        p.bar(l, p.bottom + floor / 2, r, thickness=floor)
        tail(p, p.left + .40 * p.v, 302 if cap else 249, 0)
        tail(p, p.right - .43 * p.v, 302 if cap else 249, 0)
        put("Ԭԭ", p)


def yus(p, big=False, closed=False, blend=False):
    l, c, r, h, b = p.left, p.mid, p.right, p.top, p.bottom
    w = p.width
    stem = min(p.v * .85, w * .145)
    if big:
        tip = h * .422
        inset = w * (.16 if blend else .095)
        shoulder = min(p.h, h * .150)
        eye_half = (w / 2 - inset) * (1 - shoulder / (h - tip)) - min(p.v * .65, w * .105)
        eye_tip = min(h * .658, tip + p.h * .69)
        p.polygon([(l + inset, h), (r - inset, h), (c + stem * .47, tip),
                   (c - stem * .47, tip)], radius=p.radius * .45)
        p.polygon([(c - eye_half, h - shoulder), (c, eye_tip),
                   (c + eye_half, h - shoulder)], radius=p.radius * .37, hole=True)
        if blend:
            p.bar(l + w * .17, h * .272, r - w * .17, thickness=min(p.h * .66, h * .111))
        for direction in (-1, 1):
            x = c + direction * (w / 2 - stem / 2)
            p.stroke((c, tip + h * .026), ((x - direction * w * .23, h * .37),
                                          (x, h * .22), (x, b + p.roundness * p.h * .32)),
                     weight=min(.91, w * .177 / p.v))
        p.box(c - stem / 2, b, c + stem / 2, tip + h * .04)
    elif closed:
        peak = min(p.v * .44, w * .072)
        channel = min(p.v * .65, w * .12)
        p.polygon([(l, b), (c - peak, h), (c + peak, h), (r, b)],
                  radius=p.radius * .74)
        p.polygon([(l + w * .214 + channel, h * .44), (c, h - min(p.h * .91, h * .228)),
                   (r - w * .214 - channel, h * .44)],
                  radius=p.radius * .37, hole=True)
        edge = max(h * .083, min(p.h, h * .141))
        p.polygon([(l + w * .092 + channel, edge), (l + w * .166 + channel, h * .311),
                   (r - w * .166 - channel, h * .311), (r - w * .092 - channel, edge)],
                  radius=p.radius * .33, hole=True)
    else:
        feet = min(p.v * 1.08, w * .205)
        peak = min(p.v * .49, w * .082)
        p.polygon([(l, b), (c - peak, h), (c + peak, h), (r, b),
                   (r - feet, b), (c, h * .780), (l + feet, b)], radius=p.radius * .73)
        p.bar(l + .25 * w, h * .402, r - .25 * w, thickness=min(p.h, h * .145))
        p.box(c - stem / 2, b, c + stem / 2, h * .43)


def izhitsa(p, loop=False):
    l, c, r, h, b = p.left, p.mid, p.right, p.top, p.bottom
    p.polygon([(l, h), (l + p.v * 1.16, h), (c + p.v * .30, h * .17),
               (c - .40 * p.v, b), (c - .63 * p.v, b)])
    p.stroke((c, b + p.h * .43), (r - p.width * .23, h * .85),
             ((r - .20 * p.width + 51, h + 105), (r - p.v * .70, h + 151),
              (r - p.v * .40, h - 30)), weight=.92)
    if loop:
        pen = Pen(p.outline, min(p.v * .62, p.width * .158), min(p.h * .66, h * .125), p.roundness)
        pen.oval(c - .265 * p.width, -.52 * h, c + .265 * p.width, .173 * h)


def omega(p, broad=False):
    l, c, r, h, b = p.left, p.mid, p.right, p.top, p.bottom
    x0, x1, cy = l + p.v / 2, r - p.v / 2, h * .42
    if broad:
        half_opening = .58 * p.v + 22
        p.stroke((c - half_opening, h - .52 * p.h),
                 ((l + .16 * p.width, h + .03 * p.h), (x0, .75 * h), (x0, cy)),
                 ((x0, .04 * h), (l + .19 * p.width, -.13 * h),
                  (c, b + .76 * p.h)),
                 ((r - .19 * p.width, -.13 * h), (x1, .04 * h), (x1, cy)),
                 ((x1, .75 * h), (r - .16 * p.width, h + .03 * p.h),
                  (c + half_opening, h - .52 * p.h)), weight=.92)
    else:
        pen = Pen(Outline(0), p.v, min(p.h, h * .25), p.roundness)
        pen.stroke((x0, h - p.roundness * pen.h * .45),
                   ((x0, .71 * h), (l + .12 * p.width, .25 * h),
                    (l + .28 * p.width, b + pen.h * .47)),
                   ((l + .39 * p.width, .16 * h), (c, .46 * h), (c, .70 * h)),
                   weight=.98)
        p.outline.add(pen.outline)
        p.outline.add(pen.outline.transform(sx=-1, dx=2 * c))


def historical_letters(d):
    for cap in (True, False):
        i = 0 if cap else 1

        def put(characters, drawing):
            d.put(characters[i], drawing)

        for characters, shelf in (("Ѣѣ", .43), ("Ҍҍ", .23)):
            p = d.letter(cap, 955 if cap else 818, left=52, right=71)
            stem = p.left + p.width * .178
            p.stem(stem, top=p.top + (62 if cap else 330))
            p.bowl(stem, p.bottom, p.right, p.top * .60)
            p.bar(p.left, p.top - p.h * .57, stem + p.width * shelf)
            put(characters, p)

        for characters, base, scale in (("Ѥѥ", "Єє", .88), ("Ꙗꙗ", "Аа", .93), ("Ꙓꙓ", "Ѣѣ", .87)):
            p = d.letter(cap, 1)
            p.stem(p.left)
            body = d.get(base[i])
            x = p.left + p.v + (265 if cap else 193)
            if characters == "Ꙓꙓ":
                p.bar(p.left + p.v * .45, p.top - .54 * p.h, x + p.v * .59)
                add_shape(p, body, x, sx=scale)
                p.outline.advance = x + scale * body.advance
            else:
                join(p, body, x, sx=scale)
                if characters == "Ꙗꙗ" and cap:
                    l, _, r, _ = body.bounds()
                    p.bar(p.left + .45 * p.v, p.top * .51, x + (l + (r - l) * .34) * scale)
            put(characters, p)

        for characters, big, closed, blend in (("Ѧѧ", False, False, False),
                                                  ("Ѫѫ", True, False, False),
                                                  ("Ꙙꙙ", False, True, False),
                                                  ("Ꙛꙛ", True, False, True)):
            p = d.letter(cap, 1085 if cap else 880, left=46, right=42, growth=.91)
            yus(p, big, closed, blend)
            put(characters, p)

        for characters, base in (("Ѩѩ", "Ѧѧ"), ("Ѭѭ", "Ѫѫ"), ("Ꙝꙝ", "Ꙙꙙ")):
            p = d.letter(cap, 1)
            p.stem(p.left)
            body = d.get(base[i])
            x = p.left + p.v + (180 if cap else 141)
            y = p.top * .42
            add_shape(p, body, x, sx=.96)
            p.outline.advance = x + body.advance * .96
            p.bar(p.left + .45 * p.v, y, x + body.advance * .48,
                  thickness=min(p.h, p.top * .145))
            put(characters, p)

        p = d.letter(cap, 887 if cap else 750, left=76, right=73)
        ze(p)
        l, c, r, h = p.left, p.mid, p.right, p.top
        p.diagonal(c - p.width * .19, h + 217, c + 35, h - p.h * .10, .63)
        p.diagonal(c + p.width * .15, h + 221, c - 47, h - p.h * .14, .63)
        p.stroke((c, 20), ((l - 172, 78), (l - 69, -430), (c + 48, -289)),
                 ((c + 122, -232), (r - 151, -247), (r - 133, -300)), weight=.74)
        put("Ѯѯ", p)

        p = d.letter(cap, 1280 if cap else 1050, left=56, right=52)
        p.stem(p.mid - p.v / 2, bottom=p.bottom if cap else -409,
               top=p.top if cap else 1432)
        y = p.top * (.15 if cap else .10) + p.h * .38
        x0, x1 = p.left + p.v * .50, p.right - p.v * .50
        p.stroke((x0, p.top - p.roundness * p.h * .42), (x0, p.top * .51),
                 ((x0, y - .04 * p.top), (p.mid - .10 * p.width, y), (p.mid, y)),
                 ((p.mid + .10 * p.width, y), (x1, y - .04 * p.top), (x1, p.top * .51)),
                 (x1, p.top - p.roundness * p.h * .42), weight=.91)
        put("Ѱѱ", p)

        p = d.letter(cap, 1)
        p.outline = d.get("O" if cap else "o").transform()
        l, _, r, _ = p.outline.bounds()
        span = r - l
        _, low, _, high = oval_counter(p.outline)
        mid = (low + high) / 2
        wave = min(p.h, (high - low) * .27)
        pen = Pen(p.outline, p.v * .84, min(p.h * .84, (high - low) * .28), p.roundness)
        pen.stroke((l + .49 * p.v, mid - wave * .17),
                   ((l + .41 * span, mid + .93 * wave), (l + .43 * span, mid - .73 * wave),
                    (l + .59 * span, mid - .48 * wave)),
                   ((l + .72 * span, mid - .78 * wave), (r - .91 * p.v, mid + .50 * wave),
                    (r - .38 * p.v, mid + wave * .16)))
        put("Ѳѳ", p)

        p = d.letter(cap, 1142 if cap else 875, left=33, right=38)
        izhitsa(p)
        put("Ѵѵ", p)

        p = d.letter(cap, 1120 if cap else 745, left=33, right=35)
        if cap:
            h = p.top
            pen = Pen(p.outline, min(p.v * .84, p.width * .185), min(p.h * .79, h * .132), p.roundness)
            pen.oval(p.left, p.bottom - 24, p.right, h * .56)
            p.diagonal(p.left + .11 * p.width, h * .93, p.mid + .065 * p.width, h * .44, .91)
            pen.stroke((p.right - .11 * p.width, h * .94), (p.mid - .09 * p.width, h * .345),
                       ((p.mid - .20 * p.width, h * .204), (p.mid + .03 * p.width, h * .174),
                        (p.mid + .146 * p.width, h * .232)))
        else:
            izhitsa(p, loop=True)
        put("Ꙋꙋ", p)

        p = d.letter(cap, 1)
        p.outline = d.get("O" if cap else "o").transform(sx=.85)
        u = d.get("у", cap)
        x = p.outline.advance - (56 if cap else 38)
        add_shape(p, u, x, sx=.91, sy=1.12 if cap else .94, y=144 if cap else 84)
        p.outline.advance = x + u.advance * .91
        put("Ѹѹ", p)

        p = d.letter(cap, 1313 if cap else 973, left=73, right=72, growth=.39)
        p.oval(p.left, -31, p.right, p.top + 32)
        p.dot(p.mid, p.top + 9, diameter=p.h * 1.12)
        p.dot(p.mid, -7, diameter=p.h * 1.12)
        put("Ѻѻ", p)

        for letters, broad in (("Ѡѡ", False), ("Ꙍꙍ", True), ("Ѽѽ", True), ("Ѿѿ", False)):
            width = (1760 if cap else 1440) if broad else (1550 if cap else 1262)
            p = d.letter(cap, width, left=48, right=48, growth=.92)
            omega(p, broad)
            x, h = p.mid, p.top
            if letters == "Ѽѽ":
                rise = p.h * .29
                p.stroke((x - 305, h + 171 + rise), ((x - 131, h + 437 + rise), (x + 61, h + 164 + rise),
                                                    (x + 312, h + 349 + rise)), weight=.47)
                p.stroke((x + 17, h + 268 + rise), ((x + 203, h + 157 + rise), (x + 199, h + 71 + rise),
                                                    (x + 32, h + 23 + rise)), weight=.38)
            if letters == "Ѿѿ":
                hat = h + max(116, p.h * .50 + 55)
                p.bar(x - 407, hat, x + 407)
                thickness = min(p.v, p.width * .15)
                p.box(x - thickness / 2, h * .58, x + thickness / 2, hat)
            put(letters, p)

        p = d.letter(cap, 1144 if cap else 870, left=78, right=71)
        l, r, h = p.left, p.right, p.top
        x = l + p.v * .50
        y = h * .07 + p.h * .23
        p.stroke((r - 40, h * .86),
                 ((p.mid, h * 1.14), (x, h * .83), (x, h * .51)),
                 ((x, h * .18), (l + .25 * p.width, y), (r - .34 * p.width, y)),
                 weight=.97)
        p.stem(r - .34 * p.width - .54 * p.v, bottom=-347 if cap else -284, top=y + .45 * p.h)
        put("Ҁҁ", p)


def more_historical_letters(d):
    for cap in (True, False):
        i = 0 if cap else 1

        def put(characters, drawing):
            d.put(characters[i], drawing)

        for characters, bar in (("Ꙁꙁ", False), ("Ꙃꙃ", True)):
            p = d.letter(cap, 927 if cap else 764, left=48, right=51)
            l, r, h = p.left, p.right, p.top
            p.bar(l + 20, h - p.h / 2, r)
            p.polygon([(l, .36 * p.h), (l + p.v * 1.06, .41 * p.h),
                       (r, h - .36 * p.h), (r - p.v * 1.13, h - .40 * p.h)])
            pen = Pen(p.outline, min(p.v, 272), min(p.h, 220), p.roundness)
            pen.stroke((l + p.v * .51, p.h * .32), (r - p.width * .36, p.h * .32),
                       ((r + 144, p.h * .32), (r + 63, -439), (r - p.width * .34, -308)),
                       ((r - p.width * .61, -234), (l + p.width * .32, -166), (l + 68, -291)),
                       weight=.78)
            if bar:
                p.bar(p.mid - p.width * .34, h * .54, p.mid + p.width * .34)
            put(characters, p)

        put("Ꙅꙅ", mirror(d.get("S" if cap else "s")))

        p = d.letter(cap, 360 if cap else 300, right=70)
        x = p.left + p.v / 2
        p.stroke((x, p.top), (x, .90 * p.h),
                 ((x, -.27 * p.h), (p.right - .15 * p.v, -.29 * p.h),
                  (p.right - .24 * p.v, 1.14 * p.h)))
        put("Ꙇꙇ", p)

        p = d.letter(cap, 1000 if cap else 810, left=76, right=73)
        h = p.top
        x0, x1 = p.left + p.v / 2, p.right - p.v / 2
        p.stroke((x0, p.bottom), (x0, .25 * h),
                 ((x0, .68 * h), (x1, .68 * h), (x1, .25 * h)), (x1, p.bottom))
        p.stem(p.mid - .45 * p.v, bottom=h * .52)
        p.bar(p.mid - .34 * p.width, h * .785, p.mid + .34 * p.width)
        put("Ꙉꙉ", p)

        p = d.letter(cap, 980 if cap else 775, left=52, right=77)
        stem = p.left + p.width * .20
        stem_bowl(p, stem)
        x = stem + p.v * .50
        p.stroke((x, p.top - p.h), ((x, p.top + p.h * .65), (x - 126, p.top + 226), (p.left + 23, p.top + 177)),
                 weight=.91)
        put("Ꙏꙏ", p)

        p = d.letter(cap, 1)
        p.outline = d.get("Ъъ"[i]).transform()
        x = p.outline.advance + (150 if cap else 110)
        p.stem(x)
        p.outline.advance = x + p.v + (160 if cap else 126)
        put("Ꙑꙑ", p)

        put("Ꙕꙕ", mirror(d.get("Юю"[i])))
        put("Ꙡꙡ", mirror(d.get("Цц"[i])))

        p = d.letter(cap, 976 if cap else 790, left=42, right=33)
        x = p.mid
        p.polygon([(p.left, p.h * .12), (x - p.v * .35, p.top),
                   (x + p.v * .35, p.top), (p.right, p.h * .12),
                   (p.right - p.v * 1.11, p.h * .12), (x, p.top * .74),
                   (p.left + p.v * 1.04, p.h * .12)])
        p.stem(x - p.v / 2, bottom=-471 if cap else -353, top=p.top * .83)
        p.stroke((p.left + p.v * .22, p.h + 37),
                 ((p.left + p.v * .22, -.06 * p.h), (p.left + p.v * .84, -.20 * p.h),
                  (p.left + p.v * 1.29, p.h * .69)), weight=.68)
        put("Ꙟꙟ", p)

        for characters, base in (("Ꙣꙣ", "Дд"), ("Ꙥꙥ", "Лл"), ("Ꙧꙧ", "Мм")):
            p = d.letter(cap, 1)
            p.outline = d.get(base[i]).transform()
            r = p.outline.bounds()[2]
            extension = 295 if cap else 236
            p.bar(r - p.v * .62, p.top - p.h * .5, r + extension)
            p.outline.advance += extension
            put(characters, p)

        for characters, eyes in (("Ꙩꙩ", 1), ("Ꙫꙫ", 2), ("Ꚛꚛ", 0)):
            p = d.letter(cap, 1)
            p.outline = d.get("O" if cap else "o").transform()
            l, bottom, r, top = p.outline.bounds()
            if not eyes:
                ring = Pen(Outline(p.outline.advance), min(p.v, (r - l) * .255),
                           min(p.h, (top - bottom) * .242), p.roundness)
                ring.oval(l, bottom, r, top)
                p.outline = ring.outline
            x0, y0, x1, y1 = oval_counter(p.outline)
            center, mid = (x0 + x1) / 2, (y0 + y1) / 2
            w, h = x1 - x0, y1 - y0
            if eyes:
                for eye in range(eyes):
                    p.dot(center + (eye - (eyes - 1) / 2) * .46 * w, mid,
                          diameter=min(p.h * .89 + 62, w * (.45 if eyes == 1 else .28), h * .52))
            else:
                cross = min(p.v, w * .32)
                p.bar(l + p.v * .46, mid, r - p.v * .46, thickness=min(p.h * .91, h * .32))
                p.box(center - cross / 2, (bottom + y0) / 2,
                      center + cross / 2, (top + y1) / 2)
            put(characters, p)

        for characters, eye in (("Ꙭꙭ", True), ("Ꚙꚙ", False)):
            p = d.letter(cap, 1)
            body = d.get("O" if cap else "o")
            l, _, r, _ = body.bounds()
            sx = .78
            x = (r - l) * sx - .26 * p.v
            p.outline = body.transform(sx=sx)
            add_shape(p, body, x, sx=sx)
            if eye:
                x0, y0, x1, y1 = oval_counter(body)
                radius = min(p.h * .86 + 48, (x1 - x0) * sx * .44, (y1 - y0) * .43)
                for center in ((x0 + x1) * sx / 2, x + (x0 + x1) * sx / 2):
                    p.dot(center, (y0 + y1) / 2, diameter=radius)
            p.outline.advance += x
            put(characters, p)

        p = d.letter(cap, 1095 if cap else 905, left=68, right=78, growth=1.04)
        h = p.top
        slab = min(p.h, h * .209)
        stem = p.mid - p.width * .18
        p.stroke((p.left + .64 * p.v, slab * .48),
                 ((stem, .49 * h), (stem + p.v * .53, .76 * h), (stem + p.v * .53, h + 167)),
                 weight=.83, cap=p.roundness * .5)
        p.stem(p.right - p.v, top=h * .77)
        p.bar(stem + .41 * p.v, h * .77 - .50 * slab, p.right - .30 * p.v, thickness=slab)
        p.bar(p.left, slab / 2, p.right, thickness=slab)
        tail(p, p.left + p.v * .43, 310 if cap else 239, 0)
        tail(p, p.right - p.v * .41, 310 if cap else 239, 0)
        put("Ꚁꚁ", p)

        p = d.letter(cap, 1)
        p.outline = d.get("S" if cap else "s").transform(sx=.82, sy=1.13, dy=-179 if cap else -158)
        put("Ꚃꚃ", p)

        p = d.letter(cap, 1)
        p.outline = d.get("Зз"[i]).transform(sx=.82)
        ka = d.get("Кк"[i])
        x = p.outline.bounds()[2] - ka.bounds()[0] * .90 - min(.67 * p.v, 118)
        add_shape(p, ka, x, sx=.90)
        p.bar(x - .65 * p.v, p.top * .505, x + p.v * 1.21,
              thickness=min(p.h, p.top * .17))
        p.outline.advance = x + ka.advance * .90
        put("Ꚅꚅ", p)

        p = d.letter(cap, 1480 if cap else 1225, growth=1.18)
        che_curve(p, p.left + p.v * .50, p.mid)
        che_curve(p, p.mid, p.right - p.v * .61)
        p.stem(p.right - p.v)
        put("Ꚇꚇ", p)

        p = d.letter(cap, 1)
        p.outline = d.get("Дд"[i]).transform()
        r = p.outline.bounds()[2]
        z = d.letter(cap, 742, left=32, right=54)
        z.v *= .71
        z.h *= .67
        ze(z)
        add_shape(p, z.outline, r - min(.69 * p.v, 150), -433 if cap else -352, sx=.47, sy=.45)
        p.outline.advance += z.outline.advance * .35
        put("Ꚉꚉ", p)

        p = d.letter(cap, 1000 if cap else 837, left=55, right=60)
        h = p.top
        l, r = p.left, p.right
        x = l + p.width * .35
        p.bar(l, h - p.h / 2, r - p.width * .11)
        p.stem(x, bottom=h * .16)
        p.stroke((x + p.v * .52, h * .61),
                 ((r - p.v * .22, h * .58), (r + p.v * .14, -p.h * .47),
                  (r - p.width * .32, -p.h * .60)),
                 ((x + .34 * p.v, -p.h * .73), (x - p.v * .19, -p.h * .48),
                  (x - p.v * .25, -p.h * .25)), weight=.89)
        put("Ꚋꚋ", p)

        for characters, split in (("Ꚍꚍ", False), ("Ꚑꚑ", True)):
            p = d.letter(cap, 862 if cap else 715, left=50, right=44)
            x, h = p.mid, p.top
            p.bar(p.left, h - p.h / 2, p.right)
            p.stroke((x, h - p.h * .46), (x, p.h * .55),
                     ((x, -p.h * .05), (x + 158, -p.h * .13), (x + 179, p.h * .09)))
            if split:
                p.stroke((x - p.h * .10, h * .26), (x - p.h * .10, -.55 * p.h),
                         ((x - p.h * .10, -1.07 * p.h), (x + 147, -1.12 * p.h),
                          (x + 167, -.92 * p.h)), weight=.83)
            put(characters, p)

        for characters, stems in (("Ꚏꚏ", 2), ("Ꚗꚗ", 3)):
            width = (1090 if cap else 865) if stems == 2 else (1465 if cap else 1195)
            p = d.letter(cap, width, growth=.75 if stems == 2 else 1.27)
            legs(p, stems, floor=True)
            l, r = p.left, p.right
            x = r - p.v * .47
            p.stroke((x, p.h * .48), ((x + 226, -57), (x + 310, -361), (x + 66, -385)),
                     (l + 146, -385),
                     ((l - 62, -385), (l - 67, -174), (l + 148, -174)), (x - .21 * (r - l), -174),
                     weight=.58)
            p.outline.advance += 136
            put(characters, p)

        p = d.letter(cap, 1110 if cap else 900, left=64, right=75)
        che(p)
        p.bar(p.left - 84, p.top - p.h / 2, p.left + p.width * .57)
        put("Ꚓꚓ", p)

        p = d.letter(cap, 1040 if cap else 815, left=114, right=106)
        x0, x1 = p.left + p.v * .5, p.right - p.v * .5
        high, h = (p.top if cap else 1440), p.top
        p.stroke((x0, p.bottom), (x0, high * .68),
                 ((x0, high - .29 * p.h), (p.mid - 62, high + .12 * p.h),
                  (p.mid + 177, high - .55 * p.h)), weight=.96)
        p.stroke((x0, h * .50), ((p.mid, h * .82), (x1, h * .82), (x1, h * .48)),
                 (x1, p.bottom))
        put("Ꚕꚕ", p)

    p = d.letter(False, 2020, left=85, right=85, growth=.75)
    cell = p.width / 4.1
    wall = Pen(p.outline, min(p.v * .46, cell * .168), min(p.h * .46, p.top * .081), p.roundness)
    for row, number in enumerate((3, 4, 3)):
        cy = p.top * (.18 + row * .482)
        for column in range(number):
            cx = p.mid + (column - (number - 1) / 2) * cell
            wall.oval(cx - cell * .517, cy - .254 * p.top, cx + cell * .517, cy + .254 * p.top)
            p.dot(cx, cy, diameter=min(p.v * .40 + 33, cell * .28, p.top * .137))
    d.put("ꙮ", p)

    p = d.letter(False, 853, left=32)
    el(p)
    d.put("ᴫ", p)

    d.put("ᲂ", d.get("о").transform(sx=.71))
    d.put("ᲃ", d.get("с").transform(sx=1.22))

    p = d.letter(False, 753, left=68, right=65)
    l, r, h = p.left, p.right, p.top * 1.10
    pen = Pen(p.outline, min(p.v, p.width * .26), min(p.h, h * .225), p.roundness)
    x, y = l + pen.v * .47, h * .57
    bottom, top = .38 * pen.h, h - .43 * pen.h
    pen.stroke((p.mid + .14 * p.width, top),
               ((l + .30 * p.width, top), (x, h * .81), (x, y)),
               ((x, h * .26), (l + .24 * p.width, bottom), (p.mid, bottom)),
               weight=1.10)
    pen.stroke((p.mid, bottom),
               ((r - .07 * p.width, bottom), (r + .03 * p.width, y), (p.mid, y)),
               ((x, y), (p.mid - .08 * p.width, top), (p.mid + .15 * p.width, top)),
               ((r + .04 * p.width, top), (r, y - pen.h * .18), (p.mid, y)),
               weight=.86)
    d.put("ᲀ", p)

    p = d.letter(False, 699, left=58, right=119)
    x, h = p.right - p.v * .50, 1440
    p.stem(x - p.v * .5, top=h)
    p.stroke((p.left, h - p.h),
             ((p.left + .37 * p.width, h + 231), (x - p.width * .32, h - 264), (x, h - p.h * .5)),
             weight=.77)
    d.put("ᲄ", p)

    for char, yat in (("ᲆ", False), ("ᲇ", True)):
        p = d.letter(False, 826, left=61, right=75, growth=1.11)
        stem = p.left + .27 * p.width
        p.stem(stem, top=1540 if yat else 1432)
        p.bowl(stem, p.bottom, p.right, .68 * p.top)
        p.bar(p.left, 1260 if yat else 1432 - p.h * .5, p.right if yat else stem + .78 * p.v)
        d.put(char, p)

    p = d.letter(False, 721, left=58, right=55)
    p.oval(p.left + .14 * p.width, -400, p.right - .16 * p.width, .43 * p.top, wall=.71)
    p.diagonal(p.left + .08 * p.width, p.top, p.mid + .02 * p.width, .32 * p.top, .90)
    p.stroke((p.mid, .28 * p.top), (p.right - .13 * p.width, p.top * .82),
             ((p.right, p.top * 1.08), (p.right + 123, p.top * 1.04),
              (p.right + 116, p.top * .89)), weight=.81)
    p.outline.advance += 116
    d.put("ᲈ", p)
    p = d.letter(False, 1)
    p.outline = d.get("д").transform()
    l, _, r, _ = p.outline.bounds()
    p.stem(l, bottom=-441, top=p.h * .35)
    p.stem(r - p.v, bottom=-441, top=p.h * .35)
    d.put("ᲁ", p)
    p = d.letter(False, 1210, growth=1.26)
    legs(p, 3)
    d.put("ᲅ", p)


ENCLOSING = "҈҉꙰꙱꙲"


def combining(code):
    return (
        code in (0x030F, 0x0311, 0x1E08F)
        or 0x0483 <= code <= 0x0489
        or 0x2DE0 <= code <= 0x2DFF
        or 0xA66F <= code <= 0xA672
        or 0xA674 <= code <= 0xA67D
        or 0xA69E <= code <= 0xA69F
        or 0xFE2E <= code <= 0xFE2F
    )


def scaled_letter(outline, scale, baseline=0, top=None, mark=False):
    l, _, r, high = outline.bounds()
    dx = -(l + r) * scale / 2 if mark else 0
    dy = top - high * scale if top is not None else baseline
    result = outline.transform(sx=scale, sy=scale, dx=dx, dy=dy)
    if mark:
        result.advance = 0
    return result


def marks_and_symbols(d):
    _, v, h, _, _, rounded = d.profiles[False]

    p = Pen(Outline(690), v, h, rounded)
    p.stroke((150, -312), (547, 809), weight=.83)
    p.stroke((150, 125), (528, -10), weight=.77)
    p.stroke((267, 439), (647, 304), weight=.77)
    d.put("҂", p)

    for char, mode in (("҃", "titlo"), ("҄", "peak"), ("҇", "slope"), ("꙯", "vzmet"),
                       ("︮", "left"), ("︯", "right")):
        p = Pen(Outline(0), v, h, rounded)
        if mode == "titlo":
            p.stroke((-296, 200), ((-210, 354), (98, 163), (299, 302)), weight=.62)
        elif mode == "peak":
            p.stroke((-285, 225), ((-106, 153), (38, 442), (171, 340)),
                     ((231, 291), (246, 248), (286, 214)), weight=.58)
        elif mode == "slope":
            p.stroke((-287, 235), ((-167, 463), (111, 196), (299, 232)), weight=.62)
        elif mode == "vzmet":
            p.stroke((-283, 192), ((-249, 355), (-213, 280), (0, 280)),
                     ((217, 280), (244, 355), (283, 192)), weight=.61)
        elif mode == "left":
            p.stroke((-279, 193), ((-144, 433), (120, 258), (409, 297)), weight=.62)
        else:
            p.stroke((-409, 297), ((-120, 258), (144, 166), (279, 410)), weight=.62)
        d.put(char, p)

    for char, direction in (("҅", 1), ("҆", -1), ("꙽", 1)):
        p = Pen(Outline(0), v, h, rounded)
        p.dot(72 * direction, 348, diameter=v * .59 + 61)
        p.stroke((51 * direction, 345), ((-100 * direction, 302), (-96 * direction, 210),
                                          (99 * direction, 169)), weight=.57)
        if char == "꙽":
            p.outline = p.outline.transform(sx=-1, dx=50)
        d.put(char, p)

    p = Pen(Outline(0), v, h, rounded)
    p.stroke((-257, 350), ((-235, 128), (235, 128), (257, 350)), weight=.63)
    d.put("꙼", p)
    d.put("꙾", p.outline.transform(dx=365, dy=645))
    d.drawings["꙾"].advance = 730
    d.put("ꙿ", d.get("꙽").transform(dx=180, dy=659))
    d.drawings["ꙿ"].advance = 360

    for character in ENCLOSING:
        p = Pen(Outline(0), v, h, rounded)
        if character in "҈҉꙰":
            for step in range(8):
                angle = step * pi / 4
                if character == "҈":
                    x, y = 1030 * cos(angle), 1000 * sin(angle)
                    p.stroke((x - 95, y), ((x - 85, y + 124), (x + 85, y + 124), (x + 95, y)), weight=.51)
                elif character == "҉":
                    x, y = cos(angle), sin(angle)
                    p.stroke((x * 854, y * 900), (x * 1180, y * 1160), weight=.53)
                else:
                    x, y = 1040 * cos(angle), 1000 * sin(angle)
                    p.stroke((x - 72, y - 93), (x + 72, y + 93), weight=.48)
                    p.stroke((x + 72, y - 93), (x - 72, y + 93), weight=.48)
        elif character == "꙱":
            for y, direction in ((-994, 1), (994, -1)):
                p.stroke((-997, y + 243 * direction), (-997, y), (997, y),
                         (997, y + 243 * direction), weight=.55, cap=0)
        else:
            for x in (-234, 0, 234):
                p.stroke((x, -1124), (x, -850), weight=.54)
            for x, side in ((-920, -1), (920, 1)):
                for y in (-342, 0, 342):
                    p.stroke((x, y), (x + side * 270, y), weight=.54)
            p.stroke((-139, 969), (139, 969), weight=.55)
            p.stroke((0, 831), (0, 1107), weight=.55)
        d.put(character, p)

    p = Pen(Outline(780), v, h, rounded)
    outer, inner = [], []
    for step in range(12):
        angle = step * pi / 6
        radius = 320 if step % 2 == 0 else 215
        outer.append((390 + radius * cos(angle), 1030 + radius * sin(angle)))
        inner.append((390 + (radius - 65) * cos(angle), 1030 + (radius - 65) * sin(angle)))
    p.polygon(outer, radius=14)
    p.polygon(inner, radius=10, hole=True)
    d.put("꙳", p)

    p = Pen(Outline(0), min(v, 285), min(h, 245), rounded)
    for x in (-173, 173):
        p.stroke((x - 94, 444), (x + 82, 195), weight=.69)
    d.put("\u030f", p)
    p = Pen(Outline(0), v, h, rounded)
    p.stroke((-242, 202), ((-206, 446), (206, 446), (242, 202)), weight=.72)
    d.put("\u0311", p)

    # Native P-aligned Ruble sign (₽) preserving exact Google Sans bowl geometry.
    p = d.letter(True, 1)
    base = d.get("P").transform()
    l_p, _, r_p, _ = base.bounds()
    ext = min(135.0, p.v * .62 + 25.0)
    p.outline = base.transform(dx=ext)
    p.outline.advance = base.advance + ext
    y_bot = base.contours[0][15][1]
    y_top = base.contours[0][18][1]
    thick = min(p.h * .92, y_bot * .52)
    bar_r = min((y_top - y_bot) * .50 * p.roundness, p.v * .32 * p.roundness)
    low_r = min(thick * .50 * p.roundness, p.v * .32 * p.roundness)
    p.bar(l_p, (y_bot + y_top) * .5, l_p + ext + p.v * .92, thickness=y_top - y_bot, radius=bar_r)
    p.bar(l_p, y_bot * .44, l_p + ext + p.v + (r_p - l_p) * .36, thickness=thick, radius=low_r)
    d.put("₽", p)

def raised_letters(d):
    for target, source in (("ꚜ", "ъ"), ("ꚝ", "ь"), ("ᵸ", "н")):
        d.put(target, scaled_letter(d.get(source), .61, top=1440))
    for code, source in enumerate(MODIFIERS, 0x1E030):
        subscript = 0x1E051 <= code <= 0x1E06A
        d.put(chr(code), scaled_letter(d.get(source), .58,
                                       baseline=-345 if subscript else 0, top=None if subscript else 1460))

    s, t = d.get("с"), d.get("т")
    es_te = s.transform()
    es_te.add(t.transform(dx=s.advance - 32))
    for start, sources in ((0x2DE0, COMBINING_A), (0xA674, "єиїуъыьѡ"),
                           (0xA69E, "фѥ"), (0x1E08F, "і")):
        for code, source in enumerate(sources, start):
            outline = es_te if source == "\0" else d.get(source)
            d.put(chr(code), scaled_letter(outline, .49, baseline=200, mark=True))


def accent_letters(d):
    for code in coverage():
        character = chr(code)
        parts = decomposition(character).split()
        if len(parts) != 2 or parts[0].startswith("<"):
            continue
        base, accent = (chr(int(part, 16)) for part in parts)
        if base == "і":
            base = "ı"
        cap = is_cap(character)
        glyph = d.get(base, cap).transform()
        l, _, r, top = glyph.bounds()
        top = max(top, d.profiles[cap][0])
        mark = d.get(accent, cap)
        x = (l + r) / 2
        if accent != "\u030f":
            # Native accents are drawn above the x-height. Match their existing
            # position on the Latin bases before raising them to the cap line.
            top -= d.profiles[False][0]
        if accent == "\u0306":
            wt = min(max((d.profiles[True][1] - 170.0) / 230.0, 0.0), 1.0)
            top -= (28 + 46 * wt) if cap else (8 + 20 * wt)
        glyph.add(mark.transform(dx=x, dy=top))
        d.put(character, glyph)

    for char, base in (("Ҋ", "Й"), ("ҋ", "й")):
        cap = is_cap(char)
        d.put(char, ornament(d, cap, base, "tail"))


def draw_design(font, location):
    design = Design(font, location)
    modern_letters(design)
    extended_letters(design)
    komi_and_supplement(design)
    historical_letters(design)
    more_historical_letters(design)
    marks_and_symbols(design)
    accent_letters(design)
    raised_letters(design)
    return design
