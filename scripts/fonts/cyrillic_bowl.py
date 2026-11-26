# SPDX-License-Identifier: OFL-1.1
"""Curved, bowl, oval, Serbian, and historical Cyrillic glyphs for KittyTune Flex.

Exposes ``draw_bowl_letters(d)`` (invoked after ``draw_arch_letters`` and
``draw_diag_letters``) drawing directly in final master coordinates across all
six variable axes (wght, wdth, opsz, ROND, GRAD, slnt) with strict gvar
topology invariance.
"""

from __future__ import annotations

from math import hypot

from fontTools.varLib.models import piecewiseLinearMap

from cyrillic_outlines import ARC_K, Outline, Pen, _pill_cap, shear_slnt


_YERU_COUNTER_FRACTIONS = {
    True: {
        1: 0.960, 100: 0.930, 250: 0.880, 400: 0.820,
        550: 0.730, 700: 0.620, 900: 0.415, 1000: 0.375,
    },
    False: {
        1: 0.959, 100: 0.926, 250: 0.870, 400: 0.795,
        550: 0.665, 700: 0.500, 900: 0.400, 1000: 0.420,
    },
}


def _make_letter(d, cap, adv, lsb, rsb, ref="H"):
    """Construct a final-space Letter with exact target advance and bearings."""
    m = d.metrics
    width = max(m.stem(cap) * 1.15, adv - lsb - rsb)
    return d.letter(cap, width=width, left=lsb, right=rsb, ref=ref)


def _mirror(outline):
    """Mirror an outline horizontally within its advance box."""
    return outline.transform(sx=-1.0, dx=outline.advance)


def _add_shape(p, shape, x=0.0, y=0.0, sx=1.0, sy=1.0):
    """Composite a transformed copy of ``shape`` onto ``p.outline``."""
    p.outline.add(shape.transform(sx=sx, sy=sy, dx=x, dy=y))


def _oval_counter(outline):
    """Return bounds of the single interior contour of native O/o."""
    return Outline(0, [outline.contours[1]]).bounds()


def _signed_area(contour):
    return sum(
        contour[i][0] * contour[(i + 1) % len(contour)][1]
        - contour[(i + 1) % len(contour)][0] * contour[i][1]
        for i in range(len(contour))
    )


def _append_cw(p, contour):
    """Append an outer contour ensuring clockwise winding."""
    pts = list(contour)
    if _signed_area(pts) > 0:
        pts.reverse()
    p.outline.contours.append(pts)


def _pill_end_quads(p0, p1, scale):
    """Emit a 2-off-curve semicircular cap from p0 to p1 without sharp corners."""
    return _pill_cap(p0, p1, scale)


def _draw_be_cap(d):
    """Draw uppercase Б with a B-style bowl and a straight top arm."""
    m = d.metrics
    adv = m.B_adv * 1.02
    lsb = m.B_lsb
    rsb = d.sb(78.0, True)
    p = _make_letter(d, True, adv, lsb, rsb, ref="B")

    sw = m.H_stem
    top_h = m.B_top
    bot_h = m.B_bot
    wall = m.B_rwall_bot
    spine_r = sw * 0.55 * m.roundness
    bar_r = top_h * 0.50 * m.roundness
    bowl_top = m.cap_h * 0.605
    arm_r = max(p.left + sw * 1.4, p.left + p.width * 0.84)

    p.stem(
        p.left,
        bottom=0.0,
        top=m.cap_h,
        weight=sw / p.v,
        corner_radii=(spine_r, spine_r, 0.0, 0.0),
        sharp_corners=(2, 3),
    )
    p.bar(
        p.left + sw * 0.48,
        m.cap_h - top_h * 0.5,
        arm_r,
        thickness=top_h,
        overhang_right=True,
        corner_radii=(0.0, 0.0, bar_r, bar_r),
        sharp_corners=(0, 1),
    )
    p.curved_bowl(
        p.left,
        0.0,
        p.right,
        bowl_top,
        stem_w=sw,
        wall=wall,
        top_bar=top_h,
        bot_bar=bot_h,
        flat_base=True,
        top_ov=0.0,
        draw_spine=False,
    )
    d.set_anchor_x("Б", p.left + (arm_r - p.left) * 0.48)
    d.put("Б", p)


def _draw_be_lower(d):
    """Continue the bowl of б into a single curved shoulder and a gently rising arm."""
    m = d.metrics
    p = _make_letter(d, False, m.o_adv * 0.99, m.o_lsb, m.o_lsb, ref="o")
    left, right, width = p.left, p.right, p.width
    bowl_top = m.x_h + m.low_ov
    p.squircle(
        left,
        -m.low_ov,
        right,
        bowl_top,
        wall_side=m.o_side,
        wall_top=m.o_top,
        wall_bot=m.o_top,
        cap=False,
    )
    exterior = p.outline.contours[0]
    # Replace the upper-left quarter of the bowl. Both ends of the new sweep
    # follow the tangents of the oval, leaving its counter as one closed path.
    _, side_y, _ = exterior[4]
    headroom = max(24.0, m.cap_h - bowl_top)
    slope = min(0.16, headroom * 0.43 / width)
    half_arm = min(m.f_bar * 0.90, headroom * 0.66) * 0.5
    normal_y = 1.0 / hypot(1.0, slope)
    tip_center_x = right - width * 0.083 - half_arm * normal_y * m.roundness
    tip_dx = half_arm * normal_y * slope
    upper_tip = (tip_center_x - tip_dx, m.cap_h)
    lower_tip = (tip_center_x + tip_dx, m.cap_h - 2.0 * half_arm * normal_y)

    shoulder_x = left + width * 0.39
    shoulder_y = upper_tip[1] - slope * (upper_tip[0] - shoulder_x)
    outer_handle = (shoulder_x - width * 0.19, shoulder_y - slope * width * 0.19)
    inner_x = p.mid
    inner_y = lower_tip[1] - slope * (lower_tip[0] - inner_x)
    neck = min(width * 0.41, m.n_stem * 0.90 + width * 0.06)
    turn_x = left + neck
    handle_x = inner_x - (inner_x - turn_x) * 0.71
    inner_handle = (handle_x, inner_y - slope * (inner_x - handle_x))
    p.outline.contours[0] = [
        *exterior[:5],
        (left, side_y + (shoulder_y - side_y) * 0.56, False),
        (*outer_handle, False),
        (shoulder_x, shoulder_y, True),
        (*upper_tip, True),
        *_pill_end_quads(upper_tip, lower_tip, m.roundness),
        (*lower_tip, True),
        (inner_x, inner_y, True),
        (*inner_handle, False),
        (turn_x, bowl_top, False),
        *exterior[7:],
    ]
    d.set_anchor_x("б", p.mid)
    d.put("б", p)


def _draw_ve_lower(d):
    """Draw lowercase в: true x-height miniature of native B."""
    m = d.metrics
    adv = m.B_adv * 0.90
    lsb = m.n_lsb
    rsb = d.sb(70.0, False)
    p = _make_letter(d, False, adv, lsb, rsb, ref="B")

    sw = m.n_stem * 0.96
    o_top_safe = max(m.O_top, 1.0)
    o_side_safe = max(m.O_side, 1.0)

    top_bar = m.o_top * (m.B_top / o_top_safe) * 0.92
    bot_bar = m.o_top * (m.B_bot / o_top_safe) * 0.92
    mid_bar = min(m.e_bar * 1.15, m.o_top * (m.B_mid / o_top_safe) * 0.94)
    wall_top = m.o_side * (m.B_rwall_top / o_side_safe) * 0.94
    wall_bot = m.o_side * (m.B_rwall_bot / o_side_safe) * 0.96

    right_bot = p.right
    right_top = p.right - max(10.0, p.width * 0.042)
    p.double_bowl(
        p.left,
        0.0,
        right_top,
        right_bot,
        m.x_h,
        mid_y=m.x_h * 0.517,
        stem_w=sw,
        wall_top=wall_top,
        wall_bot=wall_bot,
        top_bar=top_bar,
        mid_bar=mid_bar,
        bot_bar=bot_bar,
    )
    # A round right edge needs a smaller bearing than the flat left stem.
    text_scale = max(0.0, min(1.0, (144.0 - m.opsz) / 126.0))
    p.outline.advance -= d.sb(27.0, False) * text_scale
    d.put("в", p)


def _draw_ze(p, m, cap, mirror=False):
    """Build 2-handle Google Sans 3-aligned spline with 23-deg terminal cuts & pill ends."""
    l, r, h, w = p.left, p.right, p.top, p.width
    ov = m.cap_ov if cap else m.low_ov
    b_out = -ov
    t_out = h + ov
    rnd = m.roundness

    mp = m.squircle_params(cap=cap)
    kx, ky = mp["kx"], mp["ky"]
    ikx, iky = mp["ikx"], mp["iky"]

    raw_wall = min(m.O_side * 0.96 if cap else m.o_side * 0.96, w * 0.35)
    raw_bar = min(m.B_top * 0.98 if cap else m.o_top * 0.96, h * 0.25)
    raw_mid = min(m.B_mid * 1.02 if cap else m.e_bar * 1.14, raw_bar * 0.92)

    mid_y = h * 0.51
    min_ch = max(12.0, h * 0.11)
    avail_u = max(16.0, (t_out - mid_y) - min_ch)
    avail_l = max(16.0, (mid_y - b_out) - min_ch)
    if raw_bar + raw_mid * 0.5 > avail_u or raw_bar + raw_mid * 0.5 > avail_l:
        lim = min(avail_u, avail_l)
        s = lim / max(1e-6, raw_bar + raw_mid * 0.5)
        raw_bar *= s
        raw_mid *= s

    top_r = r - max(6.0, w * 0.045)
    bot_r = r
    in_t = t_out - raw_bar
    in_b = b_out + raw_bar
    bar_t = mid_y + raw_mid * 0.5
    bar_b = mid_y - raw_mid * 0.5

    min_cw = max(16.0, w * 0.14)
    in_top_r = max(l + w * 0.38 + min_cw * 0.5, top_r - raw_wall)
    in_bot_r = max(l + w * 0.38 + min_cw * 0.5, bot_r - raw_wall)
    bar_l = max(l + w * 0.31, min(in_top_r - raw_wall * 0.45, l + w * 0.42))
    bar_l = min(bar_l, in_top_r - min_cw * 0.65, in_bot_r - min_cw * 0.65)
    waist_in_x = min(in_top_r - 4.0, bar_l + (in_top_r - bar_l) * 0.42)

    waist_x = max(bar_l + raw_wall * 0.55, r - min(w * 0.25, max(20.0, w * 0.22)))
    waist_x = min(waist_x, top_r - 8.0)

    top_cx = l + w * 0.50
    bot_cx = l + w * 0.52

    cut_dx = min(w * 0.17, raw_wall * 0.90)
    cut_dy = min(raw_bar * 0.34, h * 0.065)

    bo_x = l + w * 0.015
    bo_y = max(b_out + raw_bar * 0.85, h * 0.225)
    bi_x = min(bot_cx - 10.0, bo_x + cut_dx)
    bi_y = min(in_b + (bar_b - in_b) * 0.40, bo_y + cut_dy)
    b_pill_pull = hypot(bi_x - bo_x, bi_y - bo_y) * 0.32 * rnd
    bo_xp = bo_x + b_pill_pull * 0.35
    bo_yp = bo_y - b_pill_pull * 0.10
    bi_xp = bi_x + b_pill_pull * 0.35
    bi_yp = bi_y - b_pill_pull * 0.10

    to_x = l + w * 0.040
    to_y = min(t_out - raw_bar * 0.85, h * 0.775)
    ti_x = min(top_cx - 10.0, to_x + cut_dx)
    ti_y = max(in_t - (in_t - bar_t) * 0.40, to_y - cut_dy)
    t_pill_pull = hypot(ti_x - to_x, ti_y - to_y) * 0.32 * rnd
    to_xp = to_x + t_pill_pull * 0.35
    to_yp = to_y + t_pill_pull * 0.10
    ti_xp = ti_x + t_pill_pull * 0.35
    ti_yp = ti_y + t_pill_pull * 0.10

    y_tr = (mid_y + 4.0 + t_out) * 0.51
    y_br = (b_out + mid_y - 4.0) * 0.49
    y_itr = (bar_t + in_t) * 0.50
    y_ibr = (in_b + bar_b) * 0.50

    bar_tip_x = bar_l + raw_mid * 0.45 * rnd
    pts = [
        (bot_cx, b_out, True),
        (bot_cx - (bot_cx - bo_xp) * (kx * 1.15), b_out, False),
        (bo_xp + (bot_cx - bo_xp) * 0.18, bo_yp - (bo_yp - b_out) * 0.52, False),
        (bo_xp, bo_yp, True),
    ]
    pts.extend(_pill_end_quads((bo_xp, bo_yp), (bi_xp, bi_yp), rnd))
    pts.extend([
        (bi_xp, bi_yp, True),
        (bi_xp + (bot_cx - bi_xp) * 0.25, in_b + (bi_yp - in_b) * 0.18, False),
        (bot_cx - (bot_cx - bi_xp) * (ikx * 0.92), in_b, False),
        (bot_cx, in_b, True),
        (bot_cx + (in_bot_r - bot_cx) * ikx, in_b, False),
        (in_bot_r, y_ibr - max(2.0, y_ibr - in_b) * iky, False),
        (in_bot_r, y_ibr, True),
        (in_bot_r, y_ibr + max(2.0, bar_b - y_ibr) * iky, False),
        (waist_in_x + (in_bot_r - waist_in_x) * ikx, bar_b, False),
        (waist_in_x, bar_b, True),
        (bar_tip_x, bar_b, True),
    ])
    pts.extend(_pill_end_quads((bar_tip_x, bar_b), (bar_tip_x, bar_t), rnd))
    pts.extend([
        (bar_tip_x, bar_t, True),
        (waist_in_x, bar_t, True),
        (waist_in_x + (in_top_r - waist_in_x) * ikx, bar_t, False),
        (in_top_r, y_itr - max(2.0, y_itr - bar_t) * iky, False),
        (in_top_r, y_itr, True),
        (in_top_r, y_itr + max(2.0, in_t - y_itr) * iky, False),
        (top_cx + (in_top_r - top_cx) * ikx, in_t, False),
        (top_cx, in_t, True),
        (top_cx - (top_cx - ti_xp) * (ikx * 0.92), in_t, False),
        (ti_xp + (top_cx - ti_xp) * 0.25, in_t - (in_t - ti_yp) * 0.18, False),
        (ti_xp, ti_yp, True),
    ])
    pts.extend(_pill_end_quads((ti_xp, ti_yp), (to_xp, to_yp), rnd))
    pts.extend([
        (to_xp, to_yp, True),
        (to_xp + (top_cx - to_xp) * 0.18, to_yp + (t_out - to_yp) * 0.52, False),
        (top_cx - (top_cx - to_xp) * (kx * 1.15), t_out, False),
        (top_cx, t_out, True),
        (top_cx + (top_r - top_cx) * kx, t_out, False),
        (top_r, y_tr + (t_out - y_tr) * ky, False),
        (top_r, y_tr, True),
        (top_r, mid_y + 4.0 + (y_tr - mid_y - 4.0) * ky * 0.85, False),
        (waist_x + (top_r - waist_x) * 0.38, mid_y + 4.0 + (y_tr - mid_y - 4.0) * 0.20, False),
        (waist_x, mid_y + 4.0, True),
        (waist_x, mid_y - 4.0, True),
        (waist_x + (bot_r - waist_x) * 0.42, mid_y - 4.0 - (mid_y - 4.0 - y_br) * 0.20, False),
        (bot_r, y_br + (mid_y - 4.0 - y_br) * ky * 0.85, False),
        (bot_r, y_br, True),
        (bot_r, b_out + (y_br - b_out) * ky, False),
        (bot_cx + (bot_r - bot_cx) * kx, b_out, False),
    ])
    _append_cw(p, pts)
    if mirror:
        p.outline = _mirror(p.outline)


def _draw_ze_pair(d):
    """Fit the native figure's smooth bowls to the two Cyrillic letter sizes."""
    m = d.metrics
    nat3 = d.native("3", upright=True)
    left, bottom, right, top = nat3.bounds()
    dl = d.sb(18.0, True)
    dr = d.sb(15.0, True)
    z_cap = nat3.transform(sx=1.055, dx=dl - left * 0.025)
    z_cap.advance += dl + dr
    d.put("З", z_cap)

    adv = m.o_adv * 0.80
    lsb = d.sb(58.0, False)
    rsb = d.sb(64.0, False)
    p = _make_letter(d, False, adv, lsb, rsb, ref="o")
    sx = p.width / (right - left)
    sy = (m.x_h + 2.0 * m.low_ov) / (top - bottom)
    p.outline.contours = nat3.transform(
        sx=sx, sy=sy, dx=p.left - left * sx, dy=-m.low_ov - bottom * sy,
    ).contours
    d.put("з", p)


def _draw_ef(d, cap):
    """Draw Ф / ф with genuine Google Sans O/o circularity, lightness, and open counters."""
    m = d.metrics
    wt = min(max((m.wght - 400.0) / 500.0, 0.0), 1.2)

    if cap:
        native_o = d.native("O", upright=True)
        left, bottom, right, top = native_o.bounds()
        bowl_w = (right - left) * (1.01 - 0.015 * min(wt, 1.0))
        bowl_h = (top - bottom) * 0.835
        bearing = (native_o.advance - bowl_w) * 0.50
        display = max(0.0, (m.opsz - 36.0) / 108.0)
        condensed = max(0.0, (100.0 - m.wdth) / 75.0)
        bearing += display * (22.0 + 14.0 * condensed)
        p = d.letter(True, width=bowl_w, left=bearing, right=bearing, ref="O")

        wall_fac = 0.92 - 0.17 * min(wt, 1.1)
        stem_fac = 0.93 - 0.13 * min(wt, 1.1)
        top_fac = 0.87 - 0.13 * min(wt, 1.1)
        sw = min(m.H_stem * stem_fac, p.width * 0.31)
        avail_w = max(24.0, p.width - sw)
        min_gap = max(18.0, avail_w * 0.20)
        wall_s = min(m.O_side * wall_fac, max(10.0, (avail_w - 2.0 * min_gap) * 0.5))
        wall_t = min(m.O_top * top_fac, bowl_h * 0.33)

        # Keep O's native curves and give its counter room for the additional stem.
        for contour, width, height in (
            (native_o.contours[0], bowl_w, bowl_h),
            (native_o.contours[1], bowl_w - 2.0 * wall_s, bowl_h - 2.0 * wall_t),
        ):
            oval = Outline(0, [contour])
            l, b, r, t = oval.bounds()
            sx, sy = width / (r - l), height / (t - b)
            p.outline.add(oval.transform(
                sx=sx, sy=sy, dx=p.mid - (l + r) * 0.50 * sx,
                dy=m.cap_h * 0.50 - (b + t) * 0.50 * sy,
            ))
        tip_radius = sw * 0.50 * (0.18 + 0.82 * m.roundness)
        p.stem(
            p.mid - sw * 0.5,
            bottom=-m.rond_ov_cap,
            top=m.cap_h + m.rond_ov_cap,
            weight=sw / p.v,
            top_r=tip_radius,
            bottom_r=tip_radius,
        )
        d.put("Ф", p)
    else:
        mp = m.squircle_params(cap=False)
        o_w = max(m.n_stem * 2.2, m.o_adv - 2.0 * m.o_lsb)
        bowl_w = o_w * (1.035 - 0.055 * min(wt, 1.1))
        lsb = rsb = m.o_lsb * 1.12
        adv = bowl_w + lsb + rsb
        p = _make_letter(d, False, adv, lsb, rsb, ref="o")

        wall_fac = 0.89 - 0.12 * min(wt, 1.1)
        stem_fac = 0.90 - 0.13 * min(wt, 1.1)
        top_fac = 0.87 - 0.12 * min(wt, 1.1)
        sw = min(m.n_stem * stem_fac, p.width * 0.31)
        avail_w = max(24.0, p.width - sw)
        min_gap = max(18.0, avail_w * 0.15)
        wall_s = min(m.o_side * wall_fac, max(10.0, (avail_w - 2.0 * min_gap) * 0.5))
        wall_t = m.o_top * top_fac

        p.squircle(
            p.left,
            -m.low_ov,
            p.right,
            m.x_h + m.low_ov,
            wall_side=wall_s,
            wall_top=wall_t,
            wall_bot=wall_t,
            kx=mp["kx"] * 0.96,
            ky=mp["ky"] * 0.98,
            ikx=mp["ikx"] * 0.96,
            iky=mp["iky"] * 0.98,
            cap=False,
        )
        p.stem(
            p.mid - sw * 0.5,
            bottom=m.desc_y - m.rond_ov_low,
            top=m.cap_h + m.rond_ov_cap,
            weight=sw / p.v,
        )
        d.put("ф", p)


def _draw_yer_bowl(
    p, m, stem_x, right, top, *, cap, stem_w=None, draw_spine=False, stroke_scale=1.0,
    min_counter_width=None,
):
    """Attach a flat-base B/b-style curved bowl with B_mid heavy-weight modulation."""
    sw = m.stem(cap) if stem_w is None else stem_w
    wall = m.B_rwall_bot if cap else m.o_side * 0.94
    if cap:
        top_bar = min(m.B_top * 0.92, m.B_mid * 1.12)
        bot_bar = min(m.B_bot * 0.92, m.B_mid * 1.15)
    else:
        mod = m.B_mid / max(m.B_top, 1.0)
        top_bar = bot_bar = m.o_top * 0.92 * (0.35 + 0.65 * mod)
    p.curved_bowl(
        stem_x,
        0.0,
        right,
        top,
        stem_w=sw,
        wall=wall * stroke_scale,
        top_bar=top_bar * stroke_scale,
        bot_bar=bot_bar * stroke_scale,
        flat_base=True,
        top_ov=0.0,
        spine_top_r=0.0,
        spine_bot_r=0.0,
        draw_spine=draw_spine,
        min_counter_width=min_counter_width,
    )


def _draw_yers(d, cap):
    """Draw Ь/ь, Ъ/ъ, Ы/ы with generous open counter windows and flat baseline bowls."""
    m = d.metrics
    ch = m.cap_h if cap else m.x_h
    sw = m.stem(cap)
    spine_r = sw * 0.55 * m.roundness
    rond_ov = m.rond_ov(cap)
    rond_r = sw * 0.50 * m.roundness
    bowl_top = ch * (0.635 if cap else 0.680)

    # Ь / ь
    adv = m.B_adv * 1.00 if cap else m.o_adv * 0.89
    lsb = m.B_lsb if cap else m.n_lsb
    rsb = d.sb(78.0 if cap else 72.0, cap)
    p = _make_letter(d, cap, adv, lsb, rsb, ref="B" if cap else "o")
    p.stem(
        p.left,
        bottom=0.0,
        top=ch + rond_ov,
        weight=sw / p.v,
        corner_radii=(spine_r, rond_r, rond_r, 0.0),
        sharp_corners=(3,),
    )
    soft_top = bowl_top + ch * 0.024 if cap else bowl_top
    _draw_yer_bowl(
        p, m, p.left, p.right, soft_top, cap=cap, stem_w=sw,
        stroke_scale=0.94 if cap else 1.0,
    )
    d.set_anchor_x("Ь" if cap else "ь", p.left + sw * 1.15)
    d.put("Ь" if cap else "ь", p)

    # Ъ / ъ
    adv_hard = m.B_adv * 1.09 if cap else m.o_adv * 0.97
    lsb_hard = d.sb(46.0 if cap else 42.0, cap)
    p = _make_letter(d, cap, adv_hard, lsb_hard, rsb, ref="B" if cap else "o")
    top_h = m.B_top if cap else m.f_bar
    bar_r = top_h * 0.50 * m.roundness
    flag_w = max(sw * 1.20, p.width * 0.24)
    stem_x = min(p.right - sw * 2.0, p.left + flag_w)
    flag_trim = 0.58 * max(0.0, stem_x - p.left) if cap else 0.0
    p.stem(
        stem_x,
        bottom=0.0,
        top=ch,
        weight=sw / p.v,
        corner_radii=(spine_r, 0.0, spine_r, 0.0),
        sharp_corners=(1, 3),
    )
    p.bar(
        p.left + flag_trim,
        ch - top_h * 0.5,
        stem_x + sw * 0.52,
        thickness=top_h,
        overhang_left=True,
        corner_radii=(bar_r, bar_r, 0.0, 0.0),
        sharp_corners=(2, 3),
    )
    _draw_yer_bowl(p, m, stem_x, p.right, bowl_top, cap=cap, stem_w=sw)
    # Bring the whole glyph left after trimming so the shorter flag keeps its optical bearing.
    shift = flag_trim * 0.48
    p.outline = p.outline.transform(dx=-shift)
    p.outline.advance -= shift
    d.set_anchor_x("Ъ" if cap else "ъ", stem_x + sw * 0.54 - shift)
    d.put("Ъ" if cap else "ъ", p)

    # Ы / ы
    adv_y = m.M_adv * 1.00 if cap else m.o_adv * 1.24
    lsb_y = m.H_lsb if cap else m.n_lsb
    p = _make_letter(d, cap, adv_y, lsb_y, lsb_y, ref="M" if cap else "o")
    sw_y = min(sw * 0.97, p.width * 0.26)
    rnd_y = sw_y * 0.50 * m.roundness
    sp_y = sw_y * 0.55 * m.roundness
    w_scale = m.wdth_ratios["H" if cap else "n"]
    reference_gap = max(sw_y * 0.34, min(sw_y * 0.92, sw_y * 0.84 * w_scale))
    bowl_r = max(p.left + sw_y * 2.2, p.right - sw_y - reference_gap)
    open_gap = p.right - sw_y - bowl_r
    gap = max(open_gap * 0.58, min(open_gap * 0.84, 44.0))
    stem_left = bowl_r + gap
    p.stem(
        p.left,
        bottom=0.0,
        top=ch + rond_ov,
        weight=sw_y / p.v,
        corner_radii=(sp_y, rnd_y, rnd_y, 0.0),
        sharp_corners=(3,),
    )
    # Fit the window to the bowl's available width, including condensed display styles.
    counter_fraction = piecewiseLinearMap(m.wght, _YERU_COUNTER_FRACTIONS[cap])
    display = max(0.0, (m.opsz - 36.0) / 108.0)
    condensed = max(0.0, (100.0 - m.wdth) / 75.0)
    counter_fraction += (1.0 - counter_fraction) * 0.28 * display * condensed
    counter_width = (bowl_r - p.left - sw_y) * counter_fraction
    _draw_yer_bowl(
        p, m, p.left, bowl_r, bowl_top, cap=cap, stem_w=sw_y,
        stroke_scale=0.90, min_counter_width=counter_width,
    )
    p.stem(stem_left, bottom=-rond_ov, top=ch + rond_ov, weight=sw_y / p.v)
    p.outline.advance -= open_gap - gap
    d.put("Ы" if cap else "ы", p)


def _draw_open_e(d, cap, upright=True, *, bar_extension=0.0):
    """Build Є/є (or upright base for Э/э) from native C/c with safe lip clearance."""
    m = d.metrics
    ch = m.cap_h if cap else m.x_h
    c_up = d.native("C" if cap else "c", upright=True)
    c_target = c_up if upright else d.native("C" if cap else "c", upright=False)

    in_l = c_up.contours[0][23 if cap else 25][0]
    top_in = c_up.contours[0][20 if cap else 22][0]
    lip_gap_y = c_up.contours[0][14 if cap else 15][1] - c_up.contours[0][33][1]

    raw_bar_h = m.E_mid if cap else m.e_bar
    bar_h = min(raw_bar_h, max(16.0, lip_gap_y * 0.72))
    bar_r = bar_h * 0.50 * m.roundness
    x0 = in_l - m.stem(cap) * 0.38
    x1 = top_in + (top_in - in_l) * ((0.14 if cap else 0.18) + bar_extension)

    bar_pen = d.letter(cap, 1)
    bar_pen.outline = Outline(c_target.advance)
    bar_pen.bar(
        x0,
        ch * 0.505,
        x1,
        thickness=bar_h,
        overhang_right=False,
        corner_radii=(0.0, 0.0, bar_r, bar_r),
        sharp_corners=(0, 1),
    )
    bar_out = bar_pen.outline
    if not upright and m.slant:
        bar_out = shear_slnt(bar_out, m.slant, m.slant_pivot)

    out = c_target.copy()
    out.add(bar_out)
    return out


def _draw_round_e_pair(d, cap):
    """Draw Є/є, Э/э, Ө/ө from native C/c and O/o."""
    je_out = _draw_open_e(d, cap, upright=True)
    d.put("Є" if cap else "є", je_out)
    e = _draw_open_e(d, cap, bar_extension=0.10) if cap else je_out
    d.put("Э" if cap else "э", _mirror(e))

    m = d.metrics
    o_up = d.native("O" if cap else "o", upright=True)
    l_o, _, r_o, _ = o_up.bounds()
    il, low_c, ir, high_c = _oval_counter(o_up)
    bar_h = min(m.E_mid * 0.94 if cap else m.e_bar, max(14.0, (high_c - low_c) * 0.38))
    p = d.letter(cap, 1)
    p.outline = o_up.copy()
    x0 = max(l_o + 4.0, il - m.stem(cap) * 0.45)
    x1 = min(r_o - 4.0, ir + m.stem(cap) * 0.45)
    p.bar(x0, (low_c + high_c) * 0.5, x1, thickness=bar_h, sharp_corners=(0, 1, 2, 3))
    d.put("Ө" if cap else "ө", p)


def _draw_yu(d, cap):
    """Draw Ю / ю: full stem + compact rhythm gap + 0.91*O / 0.90*o circle."""
    m = d.metrics
    ch = m.cap_h if cap else m.x_h
    sw = m.stem(cap)
    lsb = m.ref_lsb(cap)
    w_scale = m.wdth_ratios["O" if cap else "o"]

    # Compact gap staying tight like Ы (~96-112u cap, ~84-96u lower) instead of ballooning with sw.
    if m.wght <= 400.0:
        base_gap = 76.0 + 20.0 * max(0.0, m.wght / 400.0)
    else:
        wt_up = min(max((m.wght - 400.0) / 500.0, 0.0), 1.2)
        base_gap = 96.0 + 18.0 * (1.0 - abs(1.5 * min(wt_up, 1.0) - 0.75)) - 6.0 * wt_up
    gap = max(24.0, base_gap * (1.0 if cap else 0.87) * w_scale)

    sx = 0.91 if cap else 0.90
    o_up = d.native("O" if cap else "o", upright=True)
    o_l, _, _, _ = o_up.bounds()
    origin_x = lsb + sw + gap - o_l * sx

    p = d.letter(cap, 1)
    p.stem(lsb, bottom=-m.rond_ov(cap), top=ch + m.rond_ov(cap), weight=sw / p.v)
    _add_shape(p, o_up, x=origin_x, sx=sx)

    side_wall = m.O_side if cap else m.o_side
    bridge_r = origin_x + (o_l + side_wall * 0.55) * sx
    bridge_h = m.E_mid * 0.94 if cap else min(m.e_bar * 1.04, m.o_top * 0.88)
    p.bar(lsb + sw * 0.48, ch * 0.505, bridge_r, thickness=bridge_h, sharp_corners=(0, 1, 2, 3))
    p.outline.advance = origin_x + o_up.advance * sx
    d.put("Ю" if cap else "ю", p)


def _draw_ya(d, cap):
    """Draw Я (mirrored native R with matching leg kickout) & я (x-height R miniature)."""
    m = d.metrics
    if cap:
        r_up = d.native("R", upright=True)
        leg = Outline(0, [r_up.contours[1]])
        _, bottom, _, top = leg.bounds()
        # Extend the foot outwards while leaving the joint at the bowl in place.
        light_scale = min(1.0, (m.wght - 1.0) / 99.0)
        reach = min(m.cap_h * 0.044, m.H_stem * 0.38) * light_scale
        lean = reach / (top - bottom)
        r_up.contours[1] = leg.transform(shear=-lean, dx=lean * top).contours[0]
        mir = _mirror(r_up)
        l_m = mir.bounds()[0]
        target_lsb = max(12.0, d.sb(24.0, True))
        dl = max(0.0, target_lsb - l_m)
        dr = -d.sb(8.0, True)
        out = mir.transform(dx=dl)
        out.advance = max(out.bounds()[2] + d.sb(96.0, True), mir.advance + dl + dr)
        d.put("Я", out)
        return

    adv = m.n_adv * 1.02
    lsb = d.sb(28.0, False)
    rsb = m.n_lsb
    p = _make_letter(d, False, adv, lsb, rsb, ref="n")

    l, r, w, xh = p.left, p.right, p.width, m.x_h
    sw = m.n_stem
    rond_ov = m.rond_ov_low
    rond_r = sw * 0.50 * m.roundness
    spine_r = sw * 0.55 * m.roundness

    top_bar = m.o_top * 0.92
    bot_bar = min(m.e_bar * 1.12, m.o_top * 0.86)
    wall = m.o_side * 0.92
    bowl_bot = xh * 0.39
    bowl_l = l + max(sw * 0.45, w * 0.115)

    p.stem(
        r - sw,
        bottom=-rond_ov,
        top=xh,
        weight=sw / p.v,
        corner_radii=(rond_r, 0.0, spine_r, rond_r),
        sharp_corners=(1,),
    )

    bowl_p = d.letter(False, 1)
    bowl_p.curved_bowl(
        0.0,
        bowl_bot,
        max(sw + 24.0, r - bowl_l),
        xh,
        stem_w=sw,
        wall=wall,
        top_bar=top_bar,
        bot_bar=bot_bar,
        flat_base=True,
        top_ov=0.0,
        spine_top_r=0.0,
        spine_bot_r=0.0,
        draw_spine=False,
    )
    p.outline.add(bowl_p.outline.transform(sx=-1.0, dx=r))

    # Extended R-matched lower-left leg: clear kickout past bowl_l at BOTH ROND=0 and ROND=100.
    # Avoid Pen.polygon acute-corner bisector pullback by placing tangent points explicitly.
    leg_foot = min(sw * 1.06, w * 0.31)
    foot_l = l
    foot_r = foot_l + leg_foot
    leg_top_l = max(bowl_l + w * 0.18, r - sw - leg_foot * 0.48)
    leg_top_r = min(r - sw * 0.16, leg_top_l + leg_foot * 0.86)
    join_y_l = bowl_bot + bot_bar * 0.60
    join_y_r = bowl_bot + bot_bar * 0.72
    rnd = m.roundness

    dlx, dly = leg_top_l - foot_l, join_y_l - (-rond_ov)
    dl_len = max(1e-6, hypot(dlx, dly))
    ulx, uly = dlx / dl_len, dly / dl_len

    drx, dry = leg_top_r - foot_r, join_y_r - (-rond_ov)
    dr_len = max(1e-6, hypot(drx, dry))
    urx, ury = drx / dr_len, dry / dr_len

    w_perp = leg_foot * ((uly + ury) * 0.5)
    r_leg = min(w_perp * 0.45, leg_foot * 0.36) * rnd
    pull_l = min(dl_len * 0.35, r_leg * 0.62)
    pull_r = min(dr_len * 0.45, r_leg * 1.42)

    pl_x, pl_y = foot_l + ulx * pull_l, -rond_ov + uly * pull_l
    pr_x, pr_y = foot_r + urx * pull_r, -rond_ov + ury * pull_r
    bot_x = min(pr_x - 4.0, foot_l + max(r_leg * 1.15, (foot_r - foot_l) * 0.18 * (1.0 - rnd) + r_leg * 1.15))
    bot_y = -rond_ov

    # Tangent intersection of left edge line with y=bot_y and right edge line with y=bot_y:
    # place 2-off-curve handles along edge tangents and horizontal baseline!
    tan_l_dx = max(0.0, (bot_y - pl_y) / (-max(1e-3, uly)) * (-ulx))
    tan_r_dx = max(0.0, (bot_y - pr_y) / (-max(1e-3, ury)) * urx)
    v_lx = max(foot_l, pl_x - tan_l_dx)
    v_rx = min(foot_r, pr_x + tan_r_dx)
    h_scale = ARC_K
    _append_cw(p, [
        (leg_top_l, join_y_l, True),
        (leg_top_r, join_y_r, True),
        (pr_x, pr_y, True),
        (pr_x + (v_rx - pr_x) * h_scale, pr_y + (bot_y - pr_y) * h_scale, False),
        (bot_x + (v_rx - bot_x) * h_scale, bot_y, False),
        (bot_x, bot_y, True),
        (bot_x - (bot_x - v_lx) * h_scale, bot_y, False),
        (pl_x - (pl_x - v_lx) * h_scale, pl_y - (pl_y - bot_y) * h_scale, False),
        (pl_x, pl_y, True),
    ])
    d.put("я", p)


def _draw_serbian_dje_tshe(d, cap, hook):
    """Draw Ђ/ђ and Ћ/ћ with safe ascender bar height and h-matched arch / g-hook."""
    m = d.metrics
    ch = m.cap_h
    xh = m.x_h
    sw = m.stem(cap)
    rond_ov = m.rond_ov(cap)

    if cap:
        adv = m.H_adv * 0.87
        lsb = d.sb(58.0, True)
        rsb = m.H_lsb * 0.65
        p = _make_letter(d, True, adv, lsb, rsb, ref="H")
        stem_x = p.left + max(p.width * 0.19, sw * 0.85)
        top_h = m.H_bar
        bar_r = top_h * 0.50 * m.roundness
        bar_end = min(p.right, stem_x + sw + max(p.width * 0.34, sw * 1.25))
        p.stem(stem_x, bottom=-rond_ov, top=ch - top_h * 0.4, weight=sw / p.v)
        p.bar(
            p.left,
            ch - top_h * 0.5,
            bar_end,
            thickness=top_h,
            overhang_left=True,
            overhang_right=True,
            radius=bar_r,
        )
        arch_top = ch * 0.68
        if not hook:
            p.curved_arch(
                stem_x + sw,
                0.0,
                p.right,
                arch_top,
                leg_w=sw * 0.96,
                top_bar=m.H_bar * 0.95,
                arch_ov=m.cap_ov * 0.60,
                bottom_y=-rond_ov,
                inktrap=True,
            )
        else:
            right_leg = max(stem_x + sw * 1.6, p.right)
            leg_w = min(sw * 0.94, (right_leg - (stem_x + sw)) * 0.42)
            p.curved_arch(
                stem_x + sw,
                arch_top * 0.36,
                right_leg,
                arch_top,
                leg_w=leg_w,
                top_bar=m.H_bar * 0.95,
                arch_ov=m.cap_ov * 0.60,
                bottom_y=arch_top * 0.28,
                pill_r=0.0,
                inktrap=True,
            )
            hook_end_x = max(stem_x + sw + leg_w * 0.55, p.mid + sw * 0.10)
            hook_y = -m.cap_ov * 0.35 + m.H_bar * 0.48
            x_ctr = right_leg - leg_w * 0.5
            hook_sw = min(leg_w, 290.0)
            hook_pen = Pen(p.outline, hook_sw, min(m.H_bar * 0.94, 240.0), m.roundness, metrics=m)
            hook_pen.stroke(
                (x_ctr, arch_top * 0.46),
                ((x_ctr, hook_y + 40.0), (hook_end_x + (x_ctr - hook_end_x) * 0.55, hook_y), (hook_end_x, hook_y)),
                weight=1.0,
                cap_start=0.0,
                cap_end=m.roundness,
            )
    else:
        adv = m.n_adv * 0.96
        lsb = d.sb(54.0, False)
        rsb = m.n_lsb * 0.82
        p = _make_letter(d, False, adv, lsb, rsb, ref="n")
        stem_x = p.left + max(p.width * 0.14, sw * 0.55)
        p.stem(stem_x, bottom=-rond_ov, top=ch + m.rond_ov_cap, weight=sw / p.v)

        arch_peak = xh + m.low_ov
        avail_asc = max(30.0, ch - arch_peak)
        bar_h = min(m.f_bar * 0.90, avail_asc * 0.56)
        bar_cy = arch_peak + bar_h * 0.5 + max(18.0, (avail_asc - bar_h) * 0.48)
        bar_end = min(p.right, stem_x + sw + max(p.width * 0.28, sw * 1.15))
        p.bar(
            p.left,
            bar_cy,
            bar_end,
            thickness=bar_h,
            overhang_left=True,
            overhang_right=True,
            radius=bar_h * 0.50 * m.roundness,
        )

        if not hook:
            p.curved_arch(
                stem_x + sw,
                0.0,
                p.right,
                xh,
                leg_w=sw,
                top_bar=m.o_top,
                arch_ov=m.low_ov,
                bottom_y=-rond_ov,
                inktrap=True,
            )
        else:
            right_leg = max(stem_x + sw * 1.6, p.right)
            leg_w = min(sw * 0.96, (right_leg - (stem_x + sw)) * 0.42)
            p.curved_arch(
                stem_x + sw,
                0.0,
                right_leg,
                xh,
                leg_w=leg_w,
                top_bar=m.o_top,
                arch_ov=m.low_ov,
                bottom_y=xh * 0.08,
                pill_r=0.0,
                inktrap=True,
            )
            x_ctr = right_leg - leg_w * 0.5
            hook_bot = max(-420.0 + m.o_top * 0.5, m.desc_y * 0.88 + m.o_top * 0.5)
            hook_end_x = max(stem_x + sw * 0.65, p.mid - sw * 0.05)
            hook_pen = Pen(p.outline, leg_w, m.o_top * 0.94, m.roundness, metrics=m)
            hook_pen.stroke(
                (x_ctr, xh * 0.22),
                (x_ctr, -xh * 0.06),
                (
                    (x_ctr, hook_bot),
                    (hook_end_x + (x_ctr - hook_end_x) * 0.52, hook_bot - 12.0),
                    (hook_end_x, hook_bot + xh * 0.08),
                ),
                weight=1.0,
                cap_start=0.0,
                cap_end=m.roundness,
            )

    d.set_anchor_x(("Ђ" if hook else "Ћ") if cap else ("ђ" if hook else "ћ"), stem_x + sw * 0.72)
    d.put(("Ђ" if hook else "Ћ") if cap else ("ђ" if hook else "ћ"), p)


def _draw_lj_nj(d, cap, chars, base_char):
    """Build Љ/љ and Њ/њ by combining new Л/л/Н/н with enlarged flat-base curved_bowl."""
    m = d.metrics
    ch = m.cap_h if cap else m.x_h
    sw = m.stem(cap)
    base = d.get(base_char).copy()
    _, _, r_base, _ = base.bounds()
    bowl_top = ch * (0.635 if cap else 0.680)

    bowl_w = (m.B_adv - m.B_lsb - d.sb(78.0, True)) * 0.76 if cap else (m.o_adv - m.o_lsb * 2.0) * 0.69
    bowl_w = max(sw * 2.2, bowl_w)
    stem_x = r_base - sw

    p = d.letter(cap, 1)
    p.outline = base
    _draw_yer_bowl(p, m, stem_x, stem_x + bowl_w, bowl_top, cap=cap, stem_w=sw, draw_spine=True)
    p.outline.advance = stem_x + bowl_w + d.sb(72.0 if cap else 66.0, cap)
    d.put(chars[0 if cap else 1], p)


def _draw_schwa(d, cap):
    """Draw Ә (cap inverted-e) and ә (native inverted lowercase e)."""
    m = d.metrics
    e_up = d.native("e", upright=True)
    _, e_low, _, e_high = e_up.bounds()
    e_inv = e_up.transform(sx=-1.0, sy=-1.0, dx=e_up.advance, dy=e_high + e_low)
    if not cap:
        d.put("ә", e_inv)
        return

    adv = m.O_adv * 0.85
    lsb = m.O_lsb
    rsb = d.sb(84.0, True)
    p = _make_letter(d, True, adv, lsb, rsb, ref="O")
    el, _, er, _ = e_inv.bounds()
    sx = p.width / max(1.0, er - el)
    sy = m.cap_h / max(1.0, m.x_h)
    scaled = e_inv.transform(sx=sx, sy=sy, dx=p.left - el * sx)
    scaled.advance = p.outline.advance
    d.put("Ә", scaled)


def _draw_cedilla(d, cap, chars, base_char, is_c=False):
    """Draw Ҫ/ҫ (from native Ccedilla/ccedilla) and Ҙ/ҙ (with native uni0327)."""
    if is_c:
        d.put(chars[0 if cap else 1], d.native("Ccedilla" if cap else "ccedilla", upright=True))
        return

    base = d.get(base_char).copy()
    bl, _, br, _ = base.bounds()
    ced = d.native("uni0327", upright=True)
    cx = bl + (br - bl) * 0.50
    p = d.letter(cap, 1)
    p.outline = base
    _add_shape(p, ced, x=cx - 10.0, y=0.0)
    d.put(chars[0 if cap else 1], p)


def _safe_tail_depth(m, cap, raw_depth):
    """Keep extended descenders well inside sTypoDescender (-572) across wght=1000."""
    limit = 450.0 if cap else 440.0
    return min(raw_depth, limit)


def _draw_descender_peg(p, m, x, cap, depth=290.0, direction=0, width_scale=0.84):
    """Draw a Google Sans tapered descender peg with sharp top join & pill tip."""
    sw = m.stem(cap) * width_scale
    half = sw * 0.50
    foot_r = min(half * 0.96, sw * 0.46) * m.roundness
    bar_h = m.H_bar if cap else m.o_top
    safe_d = _safe_tail_depth(m, cap, depth)
    dx = direction * sw * 0.24
    p.polygon(
        [
            (x - half, 0.52 * bar_h),
            (x + half, 0.52 * bar_h),
            (x + half * 0.92 + dx, -safe_d),
            (x - half * 0.92 + dx, -safe_d),
        ],
        corner_radii=(None, None, foot_r, foot_r),
        sharp_corners=(0, 1),
    )


def _draw_curved_tail_hook(p, m, x, b_y, cap, side=1, depth=300.0):
    """Draw a smooth curvature-safe curved hook (for Ԅ/ԅ, etc.) with zero inner kinks."""
    w_scale = max(0.50, m.wdth_ratios["H" if cap else "n"])
    reach_dx = max(140.0, m.stem(cap) * 1.15) * w_scale
    sw = min(m.stem(cap) * 0.72, 210.0, reach_dx * 0.72)
    bar_h = min(m.H_bar if cap else m.o_top, 195.0, reach_dx * 0.72)
    half_stroke = bar_h * 0.42
    max_y_center = -min(depth, 415.0 - half_stroke)
    start_y = b_y + bar_h * 0.35
    drop_y = max(max_y_center + 55.0, min(start_y - 75.0, b_y - (130.0 if cap else 105.0)))
    reach_x = x + side * reach_dx
    ctrl_y = max(max_y_center, drop_y - (95.0 if cap else 75.0))

    pen = Pen(p.outline, sw, bar_h * 0.86, m.roundness, metrics=m)
    pen.stroke(
        (x, start_y),
        ((x, ctrl_y), (x + side * reach_dx * 0.58, ctrl_y - 14.0), (reach_x, drop_y)),
        weight=1.0,
        cap_start=0.0,
        cap_end=m.roundness,
    )
    return reach_x


def _draw_terminal_hook(p, m, x, y, width, cap, direction=1):
    """Curvature-safe terminal hook for Ԃ/ԃ, Ԉ/ԉ, Ԋ/ԋ bounded >= -450."""
    w_scale = max(0.55, min(1.30, m.wdth_ratios["H" if cap else "n"]))
    sw = min(m.stem(cap) * 0.80, 260.0)
    bh = min(m.H_bar if cap else m.o_top, 220.0)
    w = width * w_scale
    edge = x + direction * w
    min_ctrl_y = max(-300.0, y - w * 0.68)
    end_y = min(y + w * 0.58, (m.cap_h if cap else m.x_h) * 0.58)
    pen = Pen(p.outline, sw, bh, m.roundness, metrics=m)
    pen.stroke(
        (x, y + w * 0.40),
        (x, max(-80.0, y)),
        ((x, min_ctrl_y), (edge, min_ctrl_y - 18.0), (edge, max(-50.0, y + w * 0.04))),
        (edge, end_y),
        weight=1.0,
        cap_start=0.0,
        cap_end=m.roundness,
    )
    return edge


def _draw_komi_and_yer_ext(d, cap):
    """Draw Ԁ, Ԃ/ԃ, Ԉ/ԉ, Ԋ/ԋ, Ᲊ/ᲊ, Ѣ/ѣ, Ҍ/ҍ, Ꙏ/ꙏ, Ꙑ/ꙑ, ᲆ/ᲇ."""
    m = d.metrics
    ch = m.cap_h if cap else m.x_h
    sw = m.stem(cap)
    rond_ov = m.rond_ov(cap)
    rond_r = sw * 0.50 * m.roundness
    spine_r = sw * 0.55 * m.roundness
    bowl_top = ch * (0.635 if cap else 0.680)
    i = 0 if cap else 1

    if cap:
        adv = m.B_adv * 0.98
        p = _make_letter(d, True, adv, d.sb(78.0, True), m.B_lsb, ref="B")
        p.stem(
            p.right - sw,
            bottom=0.0,
            top=ch + rond_ov,
            weight=sw / p.v,
            corner_radii=(0.0, rond_r, rond_r, spine_r),
            sharp_corners=(0,),
        )
        bp = d.letter(True, 1)
        _draw_yer_bowl(bp, m, 0.0, p.width, bowl_top, cap=True, stem_w=sw)
        p.outline.add(bp.outline.transform(sx=-1.0, dx=p.right))
        d.put("Ԁ", p)

    for chars, base_ch in (("Ԃԃ", "Ԁԁ"), ("Ԉԉ", "Лл"), ("Ԋԋ", "Нн")):
        base = d.get(base_ch[i]).copy()
        _, _, r_b, _ = base.bounds()
        p = d.letter(cap, 1)
        p.outline = base
        size = 440.0 if cap else 350.0
        edge = _draw_terminal_hook(p, m, r_b - sw * 0.5, (m.H_bar if cap else m.o_top) * 0.30, size, cap)
        p.outline.advance = max(p.outline.advance, edge + sw * 0.5 + d.sb(68.0, cap))
        d.put(chars[i], p)

    adv_tje = (m.B_adv * 1.00 if cap else m.o_adv * 0.89)
    p = _make_letter(d, cap, adv_tje, d.sb(52.0, cap), d.sb(72.0, cap), ref="B" if cap else "o")
    top_h = m.B_top if cap else m.f_bar
    bar_r = top_h * 0.50 * m.roundness
    sw_tje = min(sw, p.width * 0.36)
    rnd_tje = sw_tje * 0.55 * m.roundness
    stem_x = min(p.right - sw_tje * 2.0, p.left + max(p.width * 0.25, sw_tje * 1.10))
    p.stem(stem_x, bottom=0.0, top=ch, weight=sw_tje / p.v, corner_radii=(rnd_tje, 0.0, 0.0, 0.0), sharp_corners=(1, 2, 3))
    p.bar(
        p.left,
        ch - top_h * 0.5,
        max(stem_x + sw_tje * 1.2, p.right - p.width * 0.16),
        thickness=top_h,
        overhang_left=True,
        overhang_right=True,
        radius=bar_r,
    )
    _draw_yer_bowl(p, m, stem_x, p.right, bowl_top, cap=cap, stem_w=sw_tje)
    d.put("Ᲊᲊ"[i], p)

    for chars, shelf, is_yat in (("Ѣѣ", 0.43, True), ("Ҍҍ", 0.23, False)):
        adv_y = (m.B_adv * 0.98 if cap else m.o_adv * 0.88)
        p = _make_letter(d, cap, adv_y, d.sb(52.0, cap), d.sb(72.0, cap), ref="B" if cap else "o")
        stem_x = min(p.right - sw * 2.0, p.left + max(p.width * 0.18, sw * 0.85))
        stem_top = (ch + 62.0 + rond_ov) if (cap and is_yat) else (m.cap_h * 0.94 + m.rond_ov_cap if (not cap and is_yat) else ch + rond_ov)
        p.stem(
            stem_x,
            bottom=0.0,
            top=stem_top,
            weight=sw / p.v,
            corner_radii=(spine_r, rond_r, rond_r, 0.0),
            sharp_corners=(3,),
        )
        _draw_yer_bowl(p, m, stem_x, p.right, bowl_top, cap=cap, stem_w=sw)
        bar_h = m.H_bar if cap else m.f_bar
        if not cap and is_yat:
            bar_y = m.x_h + (stem_top - m.rond_ov_cap - m.x_h) * 0.46
        else:
            bar_y = ch - bar_h * 0.57
        bar_r = bar_h * 0.50 * m.roundness
        p.bar(
            p.left,
            bar_y,
            min(p.right, stem_x + p.width * shelf),
            thickness=bar_h,
            overhang_left=True,
            overhang_right=True,
            radius=bar_r,
        )
        d.put(chars[i], p)

    p = _make_letter(d, cap, m.B_adv * 1.00 if cap else m.o_adv * 0.88, d.sb(52.0, cap), d.sb(76.0, cap), ref="B" if cap else "o")
    stem_x = min(p.right - sw * 2.0, p.left + max(p.width * 0.22, sw * 0.95))
    p.stem(stem_x, bottom=0.0, top=ch, weight=sw / p.v, corner_radii=(spine_r, 0.0, spine_r, 0.0), sharp_corners=(1, 3))
    _draw_yer_bowl(p, m, stem_x, p.right, bowl_top, cap=cap, stem_w=sw)
    hx = stem_x + sw * 0.50
    top_h = m.B_top if cap else m.o_top
    hook_sw = min(sw * 0.80, max(20.0, (hx - p.left) * 0.68))
    hook_bh = min(top_h * 0.82, 220.0)
    hpen = Pen(p.outline, hook_sw, hook_bh, m.roundness, metrics=m)
    hook_top = ch + max(150.0, hook_bh * 0.85)
    hpen.stroke(
        (hx, ch - top_h * 0.5),
        ((hx, hook_top), (p.left + hook_sw * 0.4, hook_top + 10.0), (p.left + hook_sw * 0.2, ch + hook_bh * 0.65)),
        weight=1.0,
        cap_start=0.0,
        cap_end=m.roundness,
    )
    d.put("Ꙏꙏ"[i], p)

    base = d.get("Ъ" if cap else "ъ").copy()
    _, _, r_b, _ = base.bounds()
    p = d.letter(cap, 1)
    p.outline = base
    sw_y = min(sw * 0.97, 440.0)
    gap = max(sw_y * 0.34, min(sw_y * 0.92, sw_y * 0.85 * m.wdth_ratios["H" if cap else "n"]))
    sx = r_b + gap
    p.stem(sx, bottom=-rond_ov, top=ch + rond_ov, weight=sw_y / p.v)
    p.outline.advance = sx + sw_y + m.ref_lsb(cap)
    d.put("Ꙑꙑ"[i], p)

    if not cap:
        for ch_name, is_yat in (("ᲆ", False), ("ᲇ", True)):
            p = _make_letter(d, False, m.o_adv * 0.90, d.sb(60.0, False), d.sb(74.0, False), ref="o")
            stem_x = min(p.right - sw * 2.0, p.left + max(p.width * 0.26, sw * 1.15))
            top_y = m.cap_h * 1.04 if is_yat else m.cap_h
            p.stem(stem_x, bottom=0.0, top=top_y + m.rond_ov_cap, weight=sw / p.v, corner_radii=(spine_r, rond_r, rond_r, 0.0), sharp_corners=(3,))
            _draw_yer_bowl(p, m, stem_x, p.right, ch * 0.70, cap=False, stem_w=sw)
            bar_h = m.f_bar
            bar_y = (m.cap_h * 0.87) if is_yat else (m.cap_h - bar_h * 0.5)
            p.bar(
                p.left,
                bar_y,
                p.right if is_yat else min(p.right, stem_x + sw * 1.15),
                thickness=bar_h,
                overhang_left=True,
                overhang_right=is_yat,
                radius=bar_h * 0.50 * m.roundness,
            )
            d.put(ch_name, p)


def _draw_extended_curved(d, cap):
    """Draw Ҙ/ҙ, Ҫ/ҫ, Ҏ/ҏ, Ҩ/ҩ, Ӡ/ӡ, Һ, Ԧ/ԧ, Ҽ/ҽ, Ҿ/ҿ, Ԅ/ԅ, Ԇ/ԇ, ԍ, Ԑ/ԑ, Ԗ/ԗ, Ԙ/ԙ."""
    m = d.metrics
    ch = m.cap_h if cap else m.x_h
    sw = m.stem(cap)
    rond_ov = m.rond_ov(cap)
    i = 0 if cap else 1
    w_scale = max(0.45, m.wdth_ratios["H" if cap else "n"])

    _draw_cedilla(d, cap, "Ҫҫ", "С", is_c=True)
    _draw_cedilla(d, cap, "Ҙҙ", "З" if cap else "з", is_c=False)

    base_p = d.get("Р" if cap else "р").copy()
    bl, _, br, bh = base_p.bounds()
    p = d.letter(cap, 1)
    p.outline = base_p
    tx = bl + (br - bl) * 0.82
    p.diagonal(
        tx - sw * 0.25 - 30.0 * w_scale,
        bh * (0.44 if cap else 0.22),
        tx + 125.0 * w_scale,
        bh * (0.12 if cap else -0.09),
        weight=0.68,
    )
    d.put("Ҏҏ"[i], p)

    # Ҩ / ҩ (Abkhaz Ha) — width-clamped stroke weights avoid condensed wdth=25 self-intersections
    adv_ha = m.O_adv * 0.88 if cap else m.o_adv * 0.99
    p = _make_letter(d, cap, adv_ha, d.sb(78.0, cap), d.sb(56.0, cap), ref="O" if cap else "o")
    l, r = p.left, p.right
    ov = (m.cap_ov if cap else m.low_ov) * 0.85
    bar_h = min(m.O_top if cap else m.o_top, ch * 0.21, p.width * 0.24)
    ha_sw = min(sw * 0.88, p.width * 0.19)
    x0, x1 = l + ha_sw * 0.50, r - ha_sw * 0.50
    high = ch + ov - bar_h * 0.48
    low = -ov + bar_h * 0.48
    pen = Pen(p.outline, ha_sw, bar_h * 0.92, m.roundness, metrics=m)
    pen.stroke(
        (l + p.width * 0.56, high),
        ((l + p.width * 0.28, high + 26.0), (x0, ch * 0.76), (x0, ch * 0.50)),
        ((x0, ch * 0.20), (l + p.width * 0.22, low), (p.mid, low)),
        ((r - p.width * 0.22, low), (x1, ch * 0.24), (x1, ch * 0.56)),
        ((x1, ch * 0.96), (l + p.width * 0.58, ch * 0.98), (l + p.width * 0.58, ch * 0.54)),
        ((l + p.width * 0.58, ch * 0.26), (r - p.width * 0.16, low), (r, low)),
        weight=1.0,
        cap=m.roundness,
    )
    d.put("Ҩҩ"[i], p)

    # Ӡ / ӡ (Abkhaz Dze)
    p = _make_letter(d, cap, m.B_adv * 0.85 if cap else m.o_adv * 0.78, d.sb(52.0, cap), d.sb(52.0, cap), ref="3")
    bar_h = min(m.E_mid if cap else m.o_top, ch * 0.24)
    p.bar(p.left + p.width * 0.08, ch - bar_h * 0.5, p.right - sw * 0.30, thickness=bar_h, overhang_left=True, radius=bar_h * 0.50 * m.roundness)
    d_pen = Pen(p.outline, min(sw * 0.88, p.width * 0.28), bar_h * 0.92, m.roundness, metrics=m)
    d_pen.stroke(
        (p.right - sw * 0.35, ch - bar_h * 0.50),
        (p.mid - sw * 0.28, ch * 0.53),
        ((p.right, ch * 0.53), (p.right + sw * 0.05, -145.0), (p.left + p.width * 0.10, bar_h * 0.90)),
        weight=1.0,
        cap_start=0.0,
        cap_end=m.roundness * 0.85,
    )
    d.put("Ӡӡ"[i], p)

    if cap:
        p = _make_letter(d, True, m.H_adv * 0.90, m.H_lsb, m.H_lsb * 0.75, ref="H")
        p.stem(p.left, bottom=-rond_ov, top=ch + rond_ov, weight=sw / p.v)
        p.curved_arch(
            p.left + sw,
            0.0,
            p.right,
            ch * 0.68,
            leg_w=sw * 0.96,
            top_bar=m.H_bar * 0.95,
            arch_ov=m.cap_ov * 0.60,
            bottom_y=-rond_ov,
            inktrap=True,
        )
        d.put("Һ", p)

    base_sh = d.get("Һ" if cap else "һ").copy()
    _, _, r_sh, _ = base_sh.bounds()
    p = d.letter(cap, 1)
    p.outline = base_sh
    _draw_descender_peg(p, m, r_sh - sw * 0.42, cap, depth=310.0 if cap else 255.0)
    p.outline.advance += sw * 0.12
    d.put("Ԧԧ"[i], p)

    e_up = d.native("e", upright=True)
    sx_e = (1.30 if cap else 1.00)
    sy_e = (m.cap_h / max(1.0, m.x_h)) if cap else 1.00
    e_scaled = e_up.transform(sx=sx_e, sy=sy_e)
    el, _, er, _ = e_scaled.bounds()
    w_e = m.wdth_ratios["o" if not cap else "O"]
    hook_reach = max(sw * 0.85, 195.0 * w_e)
    shift_x = max(120.0 * w_e, hook_reach + d.sb(46.0, cap) - el)
    p = d.letter(cap, 1)
    p.outline = e_scaled.transform(dx=shift_x)
    p.outline.advance = e_scaled.advance + shift_x
    l_new = el + shift_x
    y_h = ch * 0.48
    h_pen = Pen(p.outline, min(sw * 0.72, 260.0), min(m.E_mid if cap else m.e_bar, 200.0), m.roundness, metrics=m)
    h_pen.stroke(
        (l_new + sw * 0.55, y_h),
        (l_new - 24.0 * w_e, y_h),
        ((l_new - hook_reach * 0.92, y_h), (l_new - hook_reach * 0.98, y_h + ch * 0.12), (l_new - hook_reach * 0.62, y_h + ch * 0.21)),
        weight=1.0,
        cap_start=0.0,
        cap_end=m.roundness,
    )
    cur_l = p.outline.bounds()[0]
    min_lsb = max(36.0, d.sb(42.0, cap))
    if cur_l < min_lsb:
        extra = min_lsb - cur_l
        p.outline = p.outline.transform(dx=extra)
        p.outline.advance += extra
    d.put("Ҽҽ"[i], p)

    p_desc = d.letter(cap, 1)
    p_desc.outline = p.outline.copy()
    dl, _, dr, _ = p_desc.outline.bounds()
    _draw_descender_peg(p_desc, m, dl + (dr - dl) * 0.58 - sw * 0.20, cap, depth=300.0 if cap else 250.0)
    d.put("Ҿҿ"[i], p_desc)

    z_base = d.get("З" if cap else "з").copy()
    _, _, zr, _ = z_base.bounds()
    p_zh = d.letter(cap, 1)
    p_zh.outline = z_base.copy()
    end_x = _draw_curved_tail_hook(p_zh, m, zr - sw * 0.44, (m.H_bar if cap else m.o_top) * 0.35, cap, side=1)
    p_zh.outline.advance = max(p_zh.outline.advance, end_x + sw * 0.45 + d.sb(52.0, cap))
    d.put("Ԅԅ"[i], p_zh)

    p_zd = d.letter(cap, 1)
    p_zd.outline = z_base.copy()
    _draw_descender_peg(p_zd, m, zr - sw * 0.40, cap, depth=310.0 if cap else 250.0)
    p_zd.outline.advance += sw * 0.12
    d.put("Ԇԇ"[i], p_zd)

    p_rev = _make_letter(d, cap, (m.O_adv * 0.72 if cap else m.o_adv * 0.79), d.sb(68.0 if cap else 58.0, cap), d.sb(72.0 if cap else 62.0, cap), ref="3")
    _draw_ze(p_rev, m, cap=cap, mirror=True)
    d.put("Ԑԑ"[i], p_rev)

    if not cap:
        g_up = d.native("G", upright=True)
        d.put("ԍ", g_up.transform(sx=0.78, sy=m.x_h / max(1.0, m.cap_h)))

    base_r = d.get("Р" if cap else "р").copy()
    _, _, br_r, _ = base_r.bounds()
    k_base = d.get("К" if cap else "к").copy()
    kl, _, _, _ = k_base.bounds()
    p_rk = d.letter(cap, 1)
    p_rk.outline = base_r
    kx_off = br_r - kl * 0.82 - sw * 0.92
    _add_shape(p_rk, k_base, x=kx_off, sx=0.82)
    p_rk.outline.advance = kx_off + k_base.advance * 0.82
    d.put("Ԗԗ"[i], p_rk)

    base_ya = d.get("Я" if cap else "я").copy()
    _, _, yr_r, _ = base_ya.bounds()
    e_comp = d.get("Е" if cap else "е").copy()
    ecl, _, _, _ = e_comp.bounds()
    p_yae = d.letter(cap, 1)
    p_yae.outline = base_ya
    ex_off = yr_r - ecl * 0.76 - sw * 0.42
    _add_shape(p_yae, e_comp, x=ex_off, sx=0.76)
    p_yae.outline.advance = ex_off + e_comp.advance * 0.76
    d.put("Ԙԙ"[i], p_yae)


def _draw_o_and_omega_historical(d, cap):
    """Draw Ѥ/ѥ, Ꙗ/ꙗ, Ꙓ/ꙓ, Ѯ/ѯ, Ѱ/ѱ, Ѳ/ѳ, Ꙋ/ꙋ, Ѹ/ѹ, Ѻ/ѻ, Ѡ/ѡ, Ꙍ/ꙍ, Ѽ/ѽ, Ѿ/ѿ, Ҁ/ҁ, Ꙁ..ꙇ, Ꙉ/ꙉ, Ꙕ..ꙥ, Ꙩ..ꚛ, Ꚁ..ꚗ."""
    m = d.metrics
    ch = m.cap_h if cap else m.x_h
    sw = m.stem(cap)
    rond_ov = m.rond_ov(cap)
    i = 0 if cap else 1
    w_scale = max(0.45, m.wdth_ratios["H" if cap else "n"])
    o_up = d.native("O" if cap else "o", upright=True)
    l_o, _, r_o, _ = o_up.bounds()
    il, low_c, ir, high_c = _oval_counter(o_up)

    for chars, base_ch, sx in (("Ѥѥ", "Єє", 0.88), ("Ꙗꙗ", "Аа", 0.92), ("Ꙓꙓ", "Ѣѣ", 0.87)):
        p = d.letter(cap, 1)
        lsb = m.ref_lsb(cap)
        p.stem(lsb, bottom=-rond_ov, top=ch + rond_ov, weight=sw / p.v)
        body = d.get(base_ch[i]).copy()
        bl, _, br, _ = body.bounds()
        gap = max(sw * 0.45, min(250.0 if cap else 185.0, (240.0 if cap else 180.0) * w_scale))
        x_pos = lsb + sw + gap - bl * sx
        _add_shape(p, body, x=x_pos, sx=sx)
        if chars == "Ꙓꙓ":
            top_h = m.H_bar if cap else m.f_bar
            p.bar(lsb + sw * 0.45, ch - top_h * 0.52, x_pos + (bl + sw * 0.65) * sx, thickness=top_h, sharp_corners=(0, 1, 2, 3))
        else:
            mid_h = m.E_mid if cap else min(m.e_bar * 1.05, m.o_top * 0.88)
            end_x = x_pos + (bl + (br - bl) * 0.34) * sx if (chars == "Ꙗꙗ" and cap) else x_pos + (bl + sw * 0.55) * sx
            p.bar(lsb + sw * 0.45, ch * 0.51, end_x, thickness=mid_h, sharp_corners=(0, 1, 2, 3))
        p.outline.advance = x_pos + body.advance * sx
        d.put(chars[i], p)

    p = _make_letter(d, cap, m.O_adv * 0.72 if cap else m.o_adv * 0.78, d.sb(72.0, cap), d.sb(72.0, cap), ref="3")
    _draw_ze(p, m, cap=cap, mirror=False)
    l, c, r = p.left, p.mid, p.right
    top_rise = min(210.0 if cap else 175.0, ch * 0.16)
    d_pen = Pen(p.outline, min(sw * 0.65, 220.0), min((m.H_bar if cap else m.o_top) * 0.65, 190.0), m.roundness, metrics=m)
    d_pen.stroke((c - p.width * 0.18, ch + top_rise), (c + 30.0 * w_scale, ch - (m.H_bar if cap else m.o_top) * 0.10), weight=1.0)
    d_pen.stroke((c + p.width * 0.15, ch + top_rise), (c - 40.0 * w_scale, ch - (m.H_bar if cap else m.o_top) * 0.14), weight=1.0)
    tail_low = max(-420.0, -340.0 if cap else -290.0)
    d_pen.stroke(
        (c, 20.0),
        ((l - 120.0 * w_scale, 40.0), (l - 45.0 * w_scale, tail_low), (r - 110.0 * w_scale, tail_low * 0.82)),
        weight=1.0,
        cap_start=0.0,
        cap_end=m.roundness,
    )
    cur_l = p.outline.bounds()[0]
    if cur_l < 36.0:
        shift = 36.0 - cur_l
        p.outline = p.outline.transform(dx=shift)
        p.outline.advance += shift
    d.put("Ѯѯ"[i], p)

    p = _make_letter(d, cap, m.O_adv * 0.96 if cap else m.o_adv * 1.05, d.sb(56.0, cap), d.sb(52.0, cap), ref="O" if cap else "o")
    p.stem(p.mid - sw * 0.5, bottom=-rond_ov if cap else max(-420.0, m.desc_y * 0.94) - rond_ov, top=ch + rond_ov, weight=sw / p.v)
    cup_y = ch * (0.20 if cap else 0.16) + (m.H_bar if cap else m.o_top) * 0.42
    x0, x1 = p.left + sw * 0.48, p.right - sw * 0.48
    up_tip = ch + rond_ov * 0.6
    cpen = Pen(p.outline, min(sw * 0.88, p.width * 0.28), min((m.H_bar if cap else m.o_top) * 0.90, ch * 0.22), m.roundness, metrics=m)
    cpen.stroke(
        (x0, up_tip),
        (x0, ch * 0.52),
        ((x0, cup_y), (p.mid - p.width * 0.10, cup_y), (p.mid, cup_y)),
        ((p.mid + p.width * 0.10, cup_y), (x1, cup_y), (x1, ch * 0.52)),
        (x1, up_tip),
        weight=1.0,
        cap=m.roundness,
    )
    d.put("Ѱѱ"[i], p)

    # Ѳ / ѳ (Fita) — gentle wave amplitude avoids w900 curve pinch
    mid_c = (low_c + high_c) * 0.5
    p = d.letter(cap, 1)
    p.outline = o_up.copy()
    span = r_o - l_o
    wave_h = min(m.E_mid * 0.86 if cap else m.e_bar, max(14.0, (high_c - low_c) * 0.28), 160.0)
    wave_amp = min(wave_h * 0.55, (high_c - low_c) * 0.16)
    wpen = Pen(p.outline, min(sw * 0.72, 220.0), wave_h, m.roundness, metrics=m)
    wpen.stroke(
        (max(l_o + 6.0, il - sw * 0.35), mid_c - wave_amp * 0.15),
        ((l_o + span * 0.38, mid_c + wave_amp * 0.85), (l_o + span * 0.62, mid_c - wave_amp * 0.85), (min(r_o - 6.0, ir + sw * 0.35), mid_c + wave_amp * 0.15)),
        weight=1.0,
        cap=0.0,
    )
    d.put("Ѳѳ"[i], p)

    # Ꙋ / ꙋ & ᲈ
    for target in ("Ꙋ", "ꙋ", "ᲈ"):
        if (target == "Ꙋ" and not cap) or (target in ("ꙋ", "ᲈ") and cap):
            continue
        is_cap_target = (target == "Ꙋ")
        adv_u = m.Y_adv * 0.95 if is_cap_target else m.o_adv * 0.76
        p = _make_letter(d, is_cap_target, adv_u, d.sb(42.0, is_cap_target), d.sb(42.0, is_cap_target), ref="Y")
        l, r, c, h = p.left, p.right, p.mid, p.top
        sw_u = min(sw * 0.84, p.width * 0.25)
        bar_u = min(m.O_top if is_cap_target else m.o_top, h * 0.16)
        if is_cap_target:
            p.squircle(l, -m.cap_ov * 0.75, r, h * 0.56, wall_side=sw_u, wall_top=bar_u, wall_bot=bar_u, cap=True)
            arm_r = sw_u * 0.46 * m.roundness
            p.polygon(
                [(l + p.width * 0.08, h + rond_ov), (l + p.width * 0.08 + sw_u, h + rond_ov), (c + sw_u * 0.25, h * 0.44), (c - sw_u * 0.25, h * 0.44)],
                corner_radii=(arm_r, arm_r, None, None),
                sharp_corners=(2, 3),
            )
            p.polygon(
                [(r - p.width * 0.08 - sw_u, h + rond_ov), (r - p.width * 0.08, h + rond_ov), (c + sw_u * 0.25, h * 0.44), (c - sw_u * 0.25, h * 0.44)],
                corner_radii=(arm_r, arm_r, None, None),
                sharp_corners=(2, 3),
            )
        else:
            loop_bot = max(-432.0, m.desc_y * 0.96)
            p.squircle(
                c - p.width * 0.29,
                loop_bot,
                c + p.width * 0.29,
                h * 0.40,
                wall_side=min(sw_u * 0.76, p.width * 0.17),
                wall_top=min(bar_u * 0.80, h * 0.13),
                wall_bot=min(bar_u * 0.80, h * 0.13),
                cap=False,
            )
            arm_r = sw_u * 0.44 * m.roundness
            p.polygon(
                [(l, h + rond_ov), (l + sw_u * 1.05, h + rond_ov), (c + sw_u * 0.25, h * 0.22), (c - sw_u * 0.45, 0.0), (c - sw_u * 0.65, 0.0)],
                corner_radii=(arm_r, arm_r, None, None, None),
                sharp_corners=(2, 3, 4),
            )
            p.polygon(
                [(r - sw_u * 1.02, h + rond_ov), (r, h + rond_ov), (c + sw_u * 0.45, 0.0), (c - sw_u * 0.18, 0.0), (c - sw_u * 0.20, h * 0.22)],
                corner_radii=(arm_r, arm_r, None, None, None),
                sharp_corners=(2, 3, 4),
            )
        d.put(target, p)

    # Ѹ / ѹ (Uk digraph) — keep lowercase у at x_h proportion so descender >= -432
    p = d.letter(cap, 1)
    o_uk = o_up.transform(sx=0.85)
    u_uk = d.get("у").copy()
    x_uk = o_uk.advance - (56.0 if cap else 38.0) * w_scale
    p.outline = o_uk
    _add_shape(p, u_uk, x=x_uk, y=(144.0 if cap else 84.0), sx=0.91, sy=1.18 if cap else 0.94)
    p.outline.advance = x_uk + u_uk.advance * 0.91
    d.put("Ѹѹ"[i], p)

    p = _make_letter(d, cap, (m.O_adv * 0.88 if cap else m.o_adv * 0.92), d.sb(72.0, cap), d.sb(72.0, cap), ref="O" if cap else "o")
    ov = m.cap_ov if cap else m.low_ov
    p.squircle(p.left, -ov, p.right, ch + ov, cap=cap)
    dot_d = min(m.H_stem * 1.12, p.width * 0.28)
    p.dot(p.mid, ch + 8.0, diameter=dot_d)
    p.dot(p.mid, -8.0, diameter=dot_d)
    d.put("Ѻѻ"[i], p)

    # Ѡ/ѡ, Ꙍ/ꙍ, Ѽ/ѽ, Ѿ/ѿ (Omegas) — clean symmetric halves with wide turn radii across wd25..w1000
    for chars, broad in (("Ѡѡ", False), ("Ꙍꙍ", True), ("Ѽѽ", True), ("Ѿѿ", False)):
        adv_w = (m.M_adv * 1.06 if broad else m.M_adv * 0.94) if cap else (m.m_adv * 0.92 if broad else m.m_adv * 0.82)
        p = _make_letter(d, cap, adv_w, d.sb(48.0, cap), d.sb(48.0, cap), ref="M" if cap else "m")
        l, c, r, h = p.left, p.mid, p.right, p.top
        sw_w = min(sw * 0.82, p.width * 0.105)
        bh_w = min(m.O_top if cap else m.o_top, h * 0.19)
        x0 = l + sw_w * 0.5
        bot_y = -m.low_ov * 0.35 + bh_w * 0.52
        half_pen = Pen(Outline(0), sw_w, bh_w * 0.90, m.roundness, metrics=m)
        if broad:
            open_l = max(l + sw_w * 1.85, c - p.width * 0.21)
            half_pen.stroke(
                (open_l, h - bh_w * 0.55),
                ((open_l - (open_l - x0) * 0.48, h + 2.0), (x0, h * 0.72), (x0, h * 0.44)),
                ((x0, h * 0.14), (l + p.width * 0.24, bot_y - 4.0), (c, bot_y)),
                weight=1.0,
                cap_start=m.roundness,
                cap_end=0.0,
            )
        else:
            half_pen.stroke(
                (x0, h - m.roundness * bh_w * 0.45),
                ((x0, h * 0.62), (x0, h * 0.24), (l + p.width * 0.28, bot_y)),
                ((c - p.width * 0.12, h * 0.18), (c, h * 0.44), (c, h * 0.68)),
                weight=0.96,
                cap=m.roundness,
            )
        p.outline.add(half_pen.outline)
        p.outline.add(half_pen.outline.transform(sx=-1.0, dx=2.0 * c))
        if chars == "Ѽѽ":
            rise = bh_w * 0.30
            m_pen = Pen(p.outline, sw_w * 0.50, bh_w * 0.48, m.roundness, metrics=m)
            m_pen.stroke((c - 260.0 * w_scale, h + 150.0 + rise), ((c - 110.0 * w_scale, h + 380.0 + rise), (c + 50.0 * w_scale, h + 145.0 + rise), (c + 270.0 * w_scale, h + 310.0 + rise)), weight=1.0)
            m_pen.stroke((c + 15.0 * w_scale, h + 235.0 + rise), ((c + 175.0 * w_scale, h + 135.0 + rise), (c + 170.0 * w_scale, h + 60.0 + rise), (c + 25.0 * w_scale, h + 20.0 + rise)), weight=0.84)
        elif chars == "Ѿѿ":
            hat_h = m.H_bar if cap else m.f_bar
            hat_y = h + max(116.0, hat_h * 0.50 + 55.0)
            p.bar(c - 380.0 * w_scale, hat_y, c + 380.0 * w_scale, thickness=hat_h, overhang_left=True, overhang_right=True, radius=hat_h * 0.50 * m.roundness)
            th = min(sw_w, p.width * 0.15)
            p.box(c - th * 0.5, h * 0.58, c + th * 0.5, hat_y, sharp_corners=(0, 1, 2, 3))
        d.put(chars[i], p)

    p = _make_letter(d, cap, m.O_adv * 0.78 if cap else m.o_adv * 0.80, d.sb(74.0, cap), d.sb(68.0, cap), ref="C" if cap else "c")
    l, r, h = p.left, p.right, p.top
    bar_k = min(m.O_top if cap else m.o_top, h * 0.22)
    sw_k = min(sw * 0.88, p.width * 0.24, h * 0.24)
    x_k = l + sw_k * 0.50
    y_k = h * 0.09 + bar_k * 0.25
    kpen = Pen(p.outline, sw_k, bar_k * 0.92, m.roundness, metrics=m)
    kpen.stroke(
        (r - 36.0 * w_scale, h * 0.85),
        ((p.mid, h * 1.10), (x_k, h * 0.82), (x_k, h * 0.50)),
        ((x_k, h * 0.20), (l + p.width * 0.24, y_k), (r - p.width * 0.32, y_k)),
        weight=1.0,
        cap=m.roundness,
    )
    tail_bot = max(-380.0, -340.0 if cap else -280.0) - rond_ov
    p.stem(r - p.width * 0.34 - sw * 0.52, bottom=tail_bot, top=y_k + bar_k * 0.44, weight=0.96)
    d.put("Ҁҁ"[i], p)

    for chars, mid_bar in (("Ꙁꙁ", False), ("Ꙃꙃ", True)):
        p = _make_letter(d, cap, m.B_adv * 0.86 if cap else m.o_adv * 0.74, d.sb(50.0, cap), d.sb(52.0, cap), ref="Z" if cap else "z")
        l, r, h = p.left, p.right, p.top
        top_h = m.H_bar if cap else m.o_top
        p.bar(l + 18.0 * w_scale, h - top_h * 0.5, r, thickness=top_h, overhang_left=True, overhang_right=True, radius=top_h * 0.50 * m.roundness)
        diag_r = sw * 0.30 * m.roundness
        p.polygon([(l, 0.36 * top_h), (l + sw * 1.04, 0.40 * top_h), (r, h - 0.36 * top_h), (r - sw * 1.08, h - 0.40 * top_h)], radius=diag_r)
        zpen = Pen(p.outline, min(sw * 0.76, 240.0), min(top_h * 0.80, 195.0), m.roundness, metrics=m)
        tail_low = max(-380.0, -350.0 if cap else -290.0)
        zpen.stroke(
            (l + sw * 0.50, top_h * 0.32),
            (r - p.width * 0.35, top_h * 0.32),
            ((r + 125.0 * w_scale, top_h * 0.32), (r + 55.0 * w_scale, tail_low), (r - p.width * 0.33, tail_low * 0.75)),
            ((r - p.width * 0.58, tail_low * 0.58), (l + p.width * 0.32, tail_low * 0.42), (l + 65.0 * w_scale, tail_low * 0.70)),
            weight=1.0,
            cap_start=0.0,
            cap_end=m.roundness,
        )
        if mid_bar:
            mid_h = m.E_mid if cap else m.e_bar
            p.bar(p.mid - p.width * 0.34, h * 0.54, p.mid + p.width * 0.34, thickness=mid_h, overhang_left=True, overhang_right=True, radius=mid_h * 0.50 * m.roundness)
        max_x = p.outline.bounds()[2]
        p.outline.advance = max(p.outline.advance, max_x + max(40.0, d.sb(50.0, cap)))
        d.put(chars[i], p)

    d.put("Ꙅꙅ"[i], _mirror(d.native("S" if cap else "s", upright=True)))

    # Ꙇ / ꙇ (Iota) — clean J-hook proportions so heavy weight never pinches inner curve
    p = _make_letter(d, cap, m.H_adv * 0.42 if cap else m.n_adv * 0.44, d.sb(68.0, cap), d.sb(68.0, cap), ref="J")
    sw_j = min(sw * 0.88, p.width * 0.44)
    bar_j = min(m.H_bar if cap else m.o_top, p.width * 0.40)
    xj = p.left + sw_j * 0.50
    ipen = Pen(p.outline, sw_j, bar_j, m.roundness, metrics=m)
    ipen.stroke(
        (xj, ch + rond_ov),
        (xj, bar_j * 0.95),
        ((xj, -bar_j * 0.25), (p.right - sw_j * 0.15, -bar_j * 0.25), (p.right - sw_j * 0.22, bar_j * 1.25)),
        weight=1.0,
        cap=m.roundness,
    )
    d.put("Ꙇꙇ"[i], p)

    p = _make_letter(d, cap, m.H_adv * 0.84 if cap else m.n_adv * 0.86, d.sb(72.0, cap), d.sb(72.0, cap), ref="H" if cap else "n")
    x0, x1 = p.left + sw * 0.50, p.right - sw * 0.50
    bar_dj = m.H_bar if cap else m.o_top
    dpen = Pen(p.outline, min(sw * 0.90, p.width * 0.30), bar_dj * 0.95, m.roundness, metrics=m)
    dpen.stroke((x0, -rond_ov), (x0, ch * 0.25), ((x0, ch * 0.68), (x1, ch * 0.68), (x1, ch * 0.25)), (x1, -rond_ov), weight=1.0, cap=m.roundness)
    p.stem(p.mid - sw * 0.45, bottom=ch * 0.52, top=ch + rond_ov, weight=0.90)
    top_dj = m.H_bar if cap else m.f_bar
    bar_cy = min(ch - top_dj * 0.52, ch * 0.785)
    p.bar(p.mid - p.width * 0.34, bar_cy, p.mid + p.width * 0.34, thickness=top_dj, overhang_left=True, overhang_right=True, radius=top_dj * 0.50 * m.roundness)
    d.put("Ꙉꙉ"[i], p)

    d.put("Ꙕꙕ"[i], _mirror(d.get("Юю"[i])))
    d.put("Ꙡꙡ"[i], _mirror(d.get("Цц"[i])))
    for chars, base_ch in (("Ꙣꙣ", "Дд"), ("Ꙥꙥ", "Лл")):
        base = d.get(base_ch[i]).copy()
        _, _, br_b, _ = base.bounds()
        p = d.letter(cap, 1)
        p.outline = base
        ext = (280.0 if cap else 225.0) * w_scale
        top_h = m.H_bar if cap else m.o_top
        p.bar(br_b - sw * 0.55, ch - top_h * 0.5, br_b + ext, thickness=top_h, overhang_right=True, radius=top_h * 0.50 * m.roundness)
        p.outline.advance += ext
        d.put(chars[i], p)

    for chars, eyes in (("Ꙩꙩ", 1), ("Ꙫꙫ", 2), ("Ꚛꚛ", 0)):
        p = d.letter(cap, 1)
        if eyes:
            p.outline = o_up.copy()
        else:
            p.outline = Outline(o_up.advance)
            p.squircle(
                l_o,
                -(m.cap_ov if cap else m.low_ov),
                r_o,
                ch + (m.cap_ov if cap else m.low_ov),
                wall_side=min(sw, (r_o - l_o) * 0.255),
                wall_top=min(m.O_top if cap else m.o_top, ch * 0.242),
                wall_bot=min(m.O_top if cap else m.o_top, ch * 0.242),
                cap=cap,
            )
        x0, y0, x1, y1 = _oval_counter(p.outline)
        cx, cy = (x0 + x1) * 0.5, (y0 + y1) * 0.5
        cw, ch_in = x1 - x0, y1 - y0
        if eyes:
            diam = min((m.H_stem if cap else m.n_stem) * 0.89 + 52.0, cw * (0.45 if eyes == 1 else 0.28), ch_in * 0.52)
            for eye in range(eyes):
                p.dot(cx + (eye - (eyes - 1) * 0.5) * 0.46 * cw, cy, diameter=diam)
        else:
            cross_w = min(sw * 0.90, cw * 0.32)
            cross_h = min(m.E_mid if cap else m.e_bar, ch_in * 0.32)
            p.bar(l_o + sw * 0.46, cy, r_o - sw * 0.46, thickness=cross_h, sharp_corners=(0, 1, 2, 3))
            p.box(cx - cross_w * 0.5, (-m.cap_ov + y0) * 0.5, cx + cross_w * 0.5, (ch + m.cap_ov + y1) * 0.5, sharp_corners=(0, 1, 2, 3))
        d.put(chars[i], p)

    for chars, eye in (("Ꙭꙭ", True), ("Ꚙꚙ", False)):
        p = d.letter(cap, 1)
        sx = 0.78
        dx_join = (r_o - l_o) * sx - 0.26 * sw
        p.outline = o_up.transform(sx=sx)
        _add_shape(p, o_up, x=dx_join, sx=sx)
        if eye:
            radius = min((m.H_stem if cap else m.n_stem) * 0.85 + 42.0, (ir - il) * sx * 0.44, (high_c - low_c) * 0.43)
            for cx_eye in ((il + ir) * sx * 0.5, dx_join + (il + ir) * sx * 0.5):
                p.dot(cx_eye, (low_c + high_c) * 0.5, diameter=radius)
        p.outline.advance += dx_join
        d.put(chars[i], p)

    p = _make_letter(d, cap, m.H_adv * 0.90 if cap else m.n_adv * 0.92, d.sb(68.0, cap), d.sb(78.0, cap), ref="H" if cap else "n")
    slab = min(m.H_bar if cap else m.o_top, ch * 0.20)
    st_dwe = p.mid - p.width * 0.18
    dwe_pen = Pen(p.outline, min(sw * 0.84, p.width * 0.28), slab, m.roundness, metrics=m)
    dwe_pen.stroke(
        (p.left + sw * 0.62, slab * 0.48),
        ((st_dwe, 0.49 * ch), (st_dwe + sw * 0.52, 0.76 * ch), (st_dwe + sw * 0.52, ch + min(160.0, ch * 0.12) + rond_ov)),
        weight=1.0,
        cap_start=0.0,
        cap_end=m.roundness,
    )
    p.stem(p.right - sw, bottom=0.0, top=ch * 0.77, weight=sw / p.v, sharp_corners=(0, 3))
    p.bar(st_dwe + 0.40 * sw, ch * 0.77 - 0.50 * slab, p.right - 0.30 * sw, thickness=slab, sharp_corners=(0, 1, 2, 3))
    p.bar(p.left, slab * 0.5, p.right, thickness=slab, overhang_left=True, overhang_right=True, radius=slab * 0.48 * m.roundness)
    _draw_descender_peg(p, m, p.left + sw * 0.43, cap, depth=300.0 if cap else 235.0)
    _draw_descender_peg(p, m, p.right - sw * 0.41, cap, depth=300.0 if cap else 235.0)
    d.put("Ꚁꚁ"[i], p)

    s_up = d.native("S" if cap else "s", upright=True)
    out_dzwe = s_up.transform(sx=0.82, sy=1.13, dy=-175.0 if cap else -155.0)
    dl_dz = max(0.0, d.sb(42.0, cap) - out_dzwe.bounds()[0])
    out_dzwe = out_dzwe.transform(dx=dl_dz)
    out_dzwe.advance = max(out_dzwe.bounds()[2] + d.sb(42.0, cap), out_dzwe.advance + dl_dz)
    d.put("Ꚃꚃ"[i], out_dzwe)

    z_zh = d.get("З" if cap else "з").transform(sx=0.82)
    ka_zh = d.get("К" if cap else "к")
    kl, _, _, _ = ka_zh.bounds()
    p = d.letter(cap, 1)
    p.outline = z_zh
    x_ka = z_zh.bounds()[2] - kl * 0.90 - min(0.67 * sw, 118.0)
    _add_shape(p, ka_zh, x=x_ka, sx=0.90)
    p.bar(x_ka - 0.65 * sw, ch * 0.505, x_ka + sw * 1.20, thickness=min(m.E_mid if cap else m.e_bar, ch * 0.17), sharp_corners=(0, 1, 2, 3))
    p.outline.advance = x_ka + ka_zh.advance * 0.90
    d.put("Ꚅꚅ"[i], p)

    d_base = d.get("Д" if cap else "д").copy()
    dr_d = d_base.bounds()[2]
    p = d.letter(cap, 1)
    p.outline = d_base
    pz = _make_letter(d, cap, 742.0 * w_scale, 32.0 * w_scale, 54.0 * w_scale, ref="3")
    _draw_ze(pz, m, cap=cap, mirror=False)
    sy_z = 0.44 if cap else 0.42
    dy_z = -410.0 if cap else -340.0
    _add_shape(p, pz.outline, x=dr_d - min(0.69 * sw, 150.0), y=dy_z, sx=0.47, sy=sy_z)
    p.outline.advance += pz.outline.advance * 0.35
    d.put("Ꚉꚉ"[i], p)

    p = _make_letter(d, cap, m.T_adv * 0.95 if cap else m.n_adv * 0.92, d.sb(55.0, cap), d.sb(60.0, cap), ref="T")
    top_h = m.H_bar if cap else m.f_bar
    x_tm = p.left + p.width * 0.35
    p.bar(p.left, ch - top_h * 0.5, p.right - p.width * 0.11, thickness=top_h, overhang_left=True, overhang_right=True, radius=top_h * 0.50 * m.roundness)
    p.stem(x_tm, bottom=ch * 0.16, top=ch - top_h * 0.4, weight=sw / p.v)
    t_pen = Pen(p.outline, min(sw * 0.82, 260.0), min(top_h * 0.84, 200.0), m.roundness, metrics=m)
    ctrl_lo = max(-310.0, -top_h * 0.70)
    t_pen.stroke(
        (x_tm + sw * 0.52, ch * 0.61),
        ((p.right - sw * 0.22, ch * 0.58), (p.right + sw * 0.12, ctrl_lo * 0.65), (p.right - p.width * 0.32, ctrl_lo * 0.82)),
        ((x_tm + 0.34 * sw, ctrl_lo), (x_tm - sw * 0.19, ctrl_lo * 0.66), (x_tm - sw * 0.25, -top_h * 0.25)),
        weight=1.0,
        cap_start=0.0,
        cap_end=m.roundness,
    )
    d.put("Ꚋꚋ"[i], p)

    for chars, split in (("Ꚍꚍ", False), ("Ꚑꚑ", True)):
        p = _make_letter(d, cap, m.T_adv * 0.88 if cap else m.n_adv * 0.84, d.sb(50.0, cap), d.sb(44.0, cap), ref="T")
        top_h = m.H_bar if cap else m.f_bar
        x_t = p.mid - p.width * 0.10
        p.bar(p.left, ch - top_h * 0.5, p.right, thickness=top_h, overhang_left=True, overhang_right=True, radius=top_h * 0.50 * m.roundness)
        tw_sw = min(sw * 0.78, 165.0, p.width * 0.20)
        tw_bh = min(top_h * 0.78, 138.0, tw_sw * 0.92)
        reach = min(p.right - x_t, max(p.width * 0.46, tw_sw * 2.25))
        y_bot = tw_bh * 0.50
        y_bend = min(ch * 0.48, max(tw_bh * 1.85, reach * 0.68))
        p.stem(x_t - tw_sw * 0.5, bottom=y_bend - 14.0, top=ch - top_h * 0.45, weight=tw_sw / p.v, bottom_r=0.0, top_r=0.0)
        tw_pen = Pen(p.outline, tw_sw, tw_bh, m.roundness, metrics=m)
        tw_pen.stroke(
            (x_t, y_bend),
            ((x_t, y_bot), (x_t + reach * 0.58, y_bot), (x_t + reach, y_bot + tw_bh * 0.26)),
            weight=1.0,
            cap_start=0.0,
            cap_end=m.roundness,
        )
        if split:
            sp_sw = min(sw * 0.68, 150.0, p.width * 0.18)
            sp_bh = min(top_h * 0.66, 130.0, sp_sw * 0.90)
            half_sp = sp_bh * 0.50
            loop_y = max(-430.0 + half_sp, -0.96 * top_h)
            sp_bend = loop_y + max(sp_bh * 1.65, reach * 0.52)
            sp_x = x_t - top_h * 0.08
            p.stem(sp_x - sp_sw * 0.5, bottom=sp_bend - 14.0, top=max(ch * 0.24, y_bot + tw_bh * 0.45), weight=sp_sw / p.v, bottom_r=0.0, top_r=0.0)
            sp_pen = Pen(p.outline, sp_sw, sp_bh, m.roundness, metrics=m)
            sp_pen.stroke(
                (sp_x, sp_bend),
                ((sp_x, loop_y), (sp_x + reach * 0.58, loop_y), (sp_x + reach, loop_y + sp_bh * 0.26)),
                weight=1.0,
                cap_start=0.0,
                cap_end=m.roundness,
            )
        d.put(chars[i], p)

    for chars, stems in (("Ꚏꚏ", 2), ("Ꚗꚗ", 3)):
        adv_sh = (m.H_adv * 0.98 if cap else m.n_adv * 0.96) if stems == 2 else (m.M_adv * 1.00 if cap else m.m_adv * 0.88)
        p = _make_letter(d, cap, adv_sh, m.ref_lsb(cap), m.ref_lsb(cap), ref="H" if stems == 2 else "M")
        sw_s = min(sw * (0.97 if stems == 3 else 1.0), p.width / (stems * 1.35))
        bar_s = m.H_bar if cap else m.o_top
        corner_r = sw_s * 0.55 * m.roundness
        for idx in range(stems):
            sx = p.left + idx * (p.width - sw_s) / (stems - 1)
            p.stem(sx, bottom=0.0, top=ch + rond_ov, weight=sw_s / p.v, corner_radii=(corner_r, sw_s * 0.5 * m.roundness, sw_s * 0.5 * m.roundness, corner_r))
        p.bar(p.left + sw_s * 0.45, bar_s * 0.5, p.right - sw_s * 0.45, thickness=bar_s, sharp_corners=(0, 1, 2, 3))
        l, r = p.left, p.right
        x_lp = r - sw_s * 0.47
        lp_sw = min(sw * 0.58, 210.0)
        lp_bh = min(bar_s * 0.58, 180.0)
        bot_lp = max(-440.0 + lp_bh * 0.5, -370.0 if cap else -340.0)
        mid_lp = max(bot_lp + lp_bh * 1.35, -170.0)
        lp_pen = Pen(p.outline, lp_sw, lp_bh, m.roundness, metrics=m)
        left_x = l + 90.0 * w_scale
        end_x = max(left_x + 40.0, x_lp - 0.22 * (r - l))
        lp_pen.stroke(
            (x_lp, bar_s * 0.48),
            ((x_lp + 210.0 * w_scale, -55.0), (x_lp + 280.0 * w_scale, bot_lp + 20.0), (x_lp + 65.0 * w_scale, bot_lp)),
            (left_x, bot_lp),
            ((left_x - 140.0 * w_scale, bot_lp), (left_x - 140.0 * w_scale, mid_lp), (left_x, mid_lp)),
            (end_x, mid_lp),
            weight=1.0,
            cap_start=0.0,
            cap_end=m.roundness,
        )
        p.outline.advance += 130.0 * w_scale
        d.put(chars[i], p)

    p = _make_letter(d, cap, m.H_adv * 0.92 if cap else m.n_adv * 0.94, d.sb(110.0, cap), d.sb(104.0, cap), ref="H" if cap else "n")
    x0 = p.left + sw * 0.5
    high = ch if cap else m.cap_h
    bar_hw = m.H_bar if cap else m.o_top
    hpen = Pen(p.outline, min(sw * 0.94, p.width * 0.28), min(bar_hw * 0.94, 240.0), m.roundness, metrics=m)
    hpen.stroke(
        (x0, -rond_ov),
        (x0, high * 0.68),
        ((x0, high - 0.29 * bar_hw), (p.mid - 58.0 * w_scale, high + 0.12 * bar_hw), (p.mid + 165.0 * w_scale, high - 0.55 * bar_hw)),
        weight=1.0,
        cap=m.roundness,
    )
    p.curved_arch(p.left + sw, 0.0, p.right, ch, leg_w=sw * 0.96, top_bar=bar_hw, arch_ov=m.cap_ov if cap else m.low_ov, bottom_y=-rond_ov, inktrap=True)
    d.put("Ꚕꚕ"[i], p)


def _draw_lowercase_specials(d):
    """Draw multiocular ꙮ and lowercase-only Old Slavonic variants ᲀ, ᲁ, ᲂ, ᲃ, ᲄ."""
    m = d.metrics
    xh = m.x_h
    sw = m.n_stem

    p = _make_letter(d, False, m.m_adv * 1.25, d.sb(84.0, False), d.sb(84.0, False), ref="m")
    cell = p.width / 4.1
    wall_s = min(sw * 0.46, cell * 0.168)
    wall_t = min(m.o_top * 0.46, xh * 0.081)
    for row, number in enumerate((3, 4, 3)):
        cy = xh * (0.18 + row * 0.482)
        for col in range(number):
            cx = p.mid + (col - (number - 1) * 0.5) * cell
            p.squircle(cx - cell * 0.517, cy - 0.254 * xh, cx + cell * 0.517, cy + 0.254 * xh, wall_side=wall_s, wall_top=wall_t, wall_bot=wall_t, cap=False)
            p.dot(cx, cy, diameter=min(sw * 0.40 + 30.0, cell * 0.28, xh * 0.137))
    d.put("ꙮ", p)

    d.put("ᲂ", d.native("o", upright=True).transform(sx=0.71))
    d.put("ᲃ", d.native("c", upright=True).transform(sx=1.22))

    # ᲀ (Rounded Ve) — built cleanly from double_bowl so curves never self-intersect
    p = _make_letter(d, False, m.o_adv * 0.80, d.sb(68.0, False), d.sb(65.0, False), ref="o")
    sw_v = min(sw * 0.88, p.width * 0.25)
    bh_v = min(m.o_top * 0.88, xh * 0.20)
    p.double_bowl(
        p.left,
        0.0,
        p.right - max(8.0, p.width * 0.04),
        p.right,
        xh * 1.08,
        mid_y=xh * 0.56,
        stem_w=sw_v,
        wall_top=sw_v * 0.88,
        wall_bot=sw_v * 0.92,
        top_bar=bh_v,
        mid_bar=bh_v * 0.86,
        bot_bar=bh_v,
        spine_r=sw_v * 0.90 * m.roundness,
    )
    d.put("ᲀ", p)

    p = _make_letter(d, False, m.f_adv * 1.15, d.sb(58.0, False), d.sb(110.0, False), ref="f")
    x_t, h_t = p.right - sw * 0.50, m.cap_h
    p.stem(x_t - sw * 0.5, bottom=-m.rond_ov_low, top=h_t + m.rond_ov_cap, weight=sw / p.v)
    tpen = Pen(p.outline, min(sw * 0.72, p.width * 0.30, 220.0), min(m.f_bar * 0.78, 190.0), m.roundness, metrics=m)
    amp = min(145.0, p.width * 0.34)
    tpen.stroke((p.left, h_t - m.f_bar * 0.75), ((p.left + 0.36 * p.width, h_t + amp), (x_t - p.width * 0.30, h_t - amp * 1.15), (x_t, h_t - m.f_bar * 0.5)), weight=1.0, cap_start=m.roundness, cap_end=0.0)
    d.put("ᲄ", p)

    d_low = d.get("д").copy()
    dl, _, dr, _ = d_low.bounds()
    p = d.letter(False, 1)
    p.outline = d_low
    p.stem(dl, bottom=-420.0 - m.rond_ov_low, top=m.o_top * 0.35, weight=0.88)
    p.stem(dr - sw * 0.88, bottom=-420.0 - m.rond_ov_low, top=m.o_top * 0.35, weight=0.88)
    d.put("ᲁ", p)


def draw_bowl_letters(d):
    """Draw all modern, Serbian, extended, and historical bowl/oval Cyrillic glyphs."""
    _draw_be_cap(d)
    _draw_be_lower(d)
    _draw_ve_lower(d)
    _draw_ze_pair(d)

    for cap in (True, False):
        _draw_ef(d, cap)
        _draw_yers(d, cap)
        _draw_round_e_pair(d, cap)
        _draw_yu(d, cap)
        _draw_ya(d, cap)
        _draw_serbian_dje_tshe(d, cap, hook=True)
        _draw_serbian_dje_tshe(d, cap, hook=False)
        _draw_lj_nj(d, cap, "Љљ", "Л" if cap else "л")
        _draw_lj_nj(d, cap, "Њњ", "Н" if cap else "н")
        _draw_schwa(d, cap)
        _draw_komi_and_yer_ext(d, cap)
        _draw_extended_curved(d, cap)
        _draw_o_and_omega_historical(d, cap)

    _draw_lowercase_specials(d)
