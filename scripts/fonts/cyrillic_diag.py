# SPDX-License-Identifier: OFL-1.1
"""Diagonal, fork, and Yus Cyrillic glyphs matched to Google Sans Flex.

Exposes ``draw_diag_letters(d)``, running after ``draw_arch_letters(d)`` and
before ``draw_bowl_letters(d)``. All geometry is constructed directly in final
master coordinates using ``MasterMetrics`` and ``Letter`` primitives from
``cyrillic_outlines.py`` with strict gvar point/flag invariance across all 6 axes.
"""

from __future__ import annotations

from math import hypot, sqrt

from cyrillic_outlines import ARC_K, Letter, Outline, Pen, _signed_area, _unit


def _clamp(val, lo, hi):
    return lo if val < lo else (hi if val > hi else val)


def _wt(m, cap=True):
    st = m.stem(cap)
    return _clamp((st - 170.0) / 260.0, 0.0, 1.0)


def _diag_cap_pts(
    a_end,
    a_prev,
    b_end,
    b_prev,
    y_end,
    rond_ov_max,
    roundness,
    sign_y,
    ov_scale=1.0,
):
    """Build an 8-point Google Sans diagonal/corner pill cap matching N/K/k/M/X/Y.

    At ``roundness == 0`` emits 4 coincident points at ``a_end`` and 4 at ``b_end``
    on ``y = y_end``. At ``roundness == 1`` solves the exact inscribed circle tangent
    to incoming edge ``a_prev -> a_end``, outgoing edge ``b_prev -> b_end``, and the
    overshoot line ``y = y_end + sign_y * rond_ov_max * ov_scale``.
    For intermediate ``roundness``, linearly interpolates between ROND=0 and ROND=100
    to match true gvar delta interpolation.
    """
    ax, _ = float(a_end[0]), float(a_end[1])
    bx, _ = float(b_end[0]), float(b_end[1])
    ye = float(y_end)
    pts0 = [
        (ax, ye, True),
        (ax, ye, False),
        (ax, ye, False),
        (ax, ye, True),
        (bx, ye, True),
        (bx, ye, False),
        (bx, ye, False),
        (bx, ye, True),
    ]
    if roundness <= 1e-6:
        return pts0

    uax, uay, la = _unit(ax - a_prev[0], ye - a_prev[1])
    ubx, uby, lb = _unit(bx - b_prev[0], ye - b_prev[1])
    if la < 1e-4 or lb < 1e-4 or abs(uay) < 1e-4 or abs(uby) < 1e-4:
        return pts0

    abx = bx - ax
    sx_ab = 1.0 if abx >= 0.0 else -1.0

    # Inward unit normals pointing into the stroke/wedge between edges A and B.
    nax, nay = -uay, uax
    if nax * sx_ab < 0.0:
        nax, nay = -nax, -nay
    nbx, nby = -uby, ubx
    if nbx * (-sx_ab) < 0.0:
        nbx, nby = -nbx, -nby

    y_tip = ye + sign_y * rond_ov_max * ov_scale
    m00, m01 = nax, -(1.0 + sign_y * nay)
    rhs0 = ax * nax + ye * nay - nay * y_tip
    m10, m11 = nbx, -(1.0 + sign_y * nby)
    rhs1 = bx * nbx + ye * nby - nby * y_tip
    det = m00 * m11 - m01 * m10
    if abs(det) < 1e-6:
        return pts0

    cx = (rhs0 * m11 - m01 * rhs1) / det
    r = (m00 * rhs1 - rhs0 * m10) / det
    if r <= 0.5:
        return pts0
    cy = y_tip - sign_y * r

    pa_x, pa_y = cx - r * nax, cy - r * nay
    pb_x, pb_y = cx - r * nbx, cy - r * nby

    # Guard tangent points against running backward past a_prev / b_prev on stubby strokes.
    s_a = (ax - pa_x) * uax + (ye - pa_y) * uay
    s_b = (bx - pb_x) * ubx + (ye - pb_y) * uby
    max_s = max(s_a / la, s_b / lb)
    clamp_s = 1.0
    if max_s > 0.78:
        clamp_s = 0.78 / max_s

    ta = (y_tip - pa_y) / uay
    tb = (y_tip - pb_y) / uby
    va_x, va_y = pa_x + ta * uax, y_tip
    vb_x, vb_y = pb_x + tb * ubx, y_tip
    p_tip = (cx, y_tip)

    cos_a = _clamp(uax * sx_ab, -0.98, 0.98)
    cos_ha = sqrt((1.0 + cos_a) * 0.5)
    ka = cos_ha / (1.0 + cos_ha)

    cos_b = _clamp((-ubx) * sx_ab, -0.98, 0.98)
    cos_hb = sqrt((1.0 + cos_b) * 0.5)
    kb = cos_hb / (1.0 + cos_hb)

    c1 = (pa_x + ka * (va_x - pa_x), pa_y + ka * (va_y - pa_y))
    c2 = (p_tip[0] + ka * (va_x - p_tip[0]), p_tip[1] + ka * (va_y - p_tip[1]))
    c3 = (p_tip[0] + kb * (vb_x - p_tip[0]), p_tip[1] + kb * (vb_y - p_tip[1]))
    c4 = (pb_x + kb * (vb_x - pb_x), pb_y + kb * (vb_y - pb_y))

    pts1 = [
        (pa_x, pa_y, True),
        (c1[0], c1[1], False),
        (c2[0], c2[1], False),
        (p_tip[0], p_tip[1], True),
        (p_tip[0], p_tip[1], True),
        (c3[0], c3[1], False),
        (c4[0], c4[1], False),
        (pb_x, pb_y, True),
    ]
    t = _clamp(roundness * clamp_s, 0.0, 1.0)
    return [
        (p0[0] + t * (p1[0] - p0[0]), p0[1] + t * (p1[1] - p0[1]), p0[2])
        for p0, p1 in zip(pts0, pts1)
    ]


def _pill_end_pts(x0, x1, y, r, sign_y, roundness):
    """Build an 8-point semicircular vertical stem pill cap."""
    r_eff = _clamp(r, 0.0, abs(x1 - x0) * 0.5)
    rx = x0 + (x1 - x0) * 0.5
    if roundness <= 1e-6 or r_eff <= 0.1:
        return [
            (x0, y, True), (x0, y, False), (x0, y, False), (x0, y, True),
            (x1, y, True), (x1, y, False), (x1, y, False), (x1, y, True),
        ]
    s_x = 1.0 if x1 >= x0 else -1.0
    dx = r_eff * s_x
    cy = y - sign_y * r_eff
    return [
        (x0, cy, True),
        (x0, cy + sign_y * r_eff * (1.0 - ARC_K), False),
        (rx - dx * (1.0 - ARC_K), y, False),
        (rx, y, True),
        (rx, y, True),
        (rx + dx * (1.0 - ARC_K), y, False),
        (x1, cy + sign_y * r_eff * (1.0 - ARC_K), False),
        (x1, cy, True),
    ]


def _ensure_cw(contour):
    if _signed_area([(x, y) for x, y, _ in contour]) > 0.0:
        contour.reverse()
    return contour


def _append_cw(outline, contour):
    outline.contours.append(_ensure_cw(list(contour)))


def _line_y_at_x(p0, p1, x):
    dx = p1[0] - p0[0]
    if abs(dx) < 1e-6:
        return (p0[1] + p1[1]) * 0.5
    return p0[1] + (x - p0[0]) * (p1[1] - p0[1]) / dx


def _line_x_at_y(p0, p1, y):
    dy = p1[1] - p0[1]
    if abs(dy) < 1e-6:
        return (p0[0] + p1[0]) * 0.5
    return p0[0] + (y - p0[1]) * (p1[0] - p0[0]) / dy


def _draw_i_cap(d):
    """Mirror native N with smooth inner joins on the diagonal."""
    n_cap = d.native("N", upright=True)
    contour = n_cap.contours[0]
    trim = d.metrics.H_stem * (0.22 + 0.16 * d.metrics.roundness)
    # In Google Sans Flex 3.007, each inner join has two points for a small ink trap.
    for index in (31, 12):
        x, y, _ = contour[index]
        prev_x, prev_y, _ = contour[index - 1]
        next_x, next_y, _ = contour[index + 2]
        ux, uy, before = _unit(prev_x - x, prev_y - y)
        vx, vy, after = _unit(next_x - x, next_y - y)
        reach = min(trim, before * 0.40, after * 0.45)
        contour[index:index + 2] = [
            (x + reach * ux, y + reach * uy, True),
            (x, y, False),
            (x + reach * vx, y + reach * vy, True),
        ]
    d.put("И", n_cap.transform(sx=-1.0, dx=n_cap.advance))


def _draw_i_lower(d):
    """Build n-matched lowercase Cyrillic и with N-style inktraps and 2-handle pills."""
    m = d.metrics
    p = d.adv_letter(False, adv_ratio=1.0, ref="n")
    l, r, h = p.left, p.right, p.top
    w = p.width
    wt = _wt(m, False)

    st = min(m.n_stem * (1.0 - 0.035 * wt), w * 0.39)
    gap = max(20.0, w - 2.0 * st)
    diag_w = min(st * (0.89 - 0.04 * wt), max(22.0, gap * 0.84))
    trap = min(8.0, st * 0.055)
    apex_w = min(6.0, st * 0.14)

    x_li = l + st
    x_ri = r - st

    # Diagonal centerline runs bottom-left to top-right inside stems.
    ax, ay = l + st * 0.56, 0.0
    bx, by = r - st * 0.56, h
    tx, ty, _ = _unit(bx - ax, by - ay)
    nx, ny = -ty, tx  # upward-left normal

    u0 = (ax + nx * diag_w * 0.5, ay + ny * diag_w * 0.5)
    u1 = (bx + nx * diag_w * 0.5, by + ny * diag_w * 0.5)
    l0 = (ax - nx * diag_w * 0.5, ay - ny * diag_w * 0.5)
    l1 = (bx - nx * diag_w * 0.5, by - ny * diag_w * 0.5)

    top_sh_x = _clamp(_line_x_at_y(u0, u1, h), x_ri - st * 0.35, r - st * 0.72)
    bot_sh_x = _clamp(_line_x_at_y(l0, l1, 0.0), l + st * 0.72, x_li + st * 0.35)

    crotch_low = _clamp(
        _line_y_at_x(u0, u1, x_li - trap),
        max(18.0, h * 0.14),
        h * 0.58,
    )
    crotch_high = _clamp(
        _line_y_at_x(l0, l1, x_ri + trap),
        h * 0.42,
        min(h - 18.0, h * 0.86),
    )
    step_y = min(st * 1.45, max(24.0, (h - crotch_low) * 0.36))
    crotch_low_stem = min(h * 0.75, crotch_low + step_y)
    crotch_high_stem = max(h * 0.25, crotch_high - step_y)

    r_stem = st * 0.5 * m.roundness
    ov_l = m.rond_ov_low
    ov_l_max = m.rond_ov_low_max

    bl_corner = _diag_cap_pts(
        (bot_sh_x, 0.0), (x_ri + trap - apex_w, crotch_high),
        (l, 0.0), (l, h),
        0.0, ov_l_max, m.roundness, -1.0, ov_scale=1.35,
    )
    tl_pill = _pill_end_pts(l, x_li, h + ov_l, r_stem, 1.0, m.roundness)
    tr_corner = _diag_cap_pts(
        (top_sh_x, h), (x_li - trap + apex_w, crotch_low),
        (r, h), (r, 0.0),
        h, ov_l_max, m.roundness, 1.0, ov_scale=1.35,
    )
    br_pill = _pill_end_pts(r, x_ri, -ov_l, r_stem, -1.0, m.roundness)

    contour = (
        bl_corner
        + tl_pill
        + [
            (x_li, crotch_low_stem, True),
            (x_li - trap, crotch_low, True),
            (x_li - trap + apex_w, crotch_low, True),
        ]
        + tr_corner
        + br_pill
        + [
            (x_ri, crotch_high_stem, True),
            (x_ri + trap, crotch_high, True),
            (x_ri + trap - apex_w, crotch_high, True),
        ]
    )
    _append_cw(p.outline, contour)
    d.put("и", p)


def _draw_k_lower(d):
    """Extract native k's exact arm+leg (C0, C2) and shorten stem C1 to x_h."""
    m = d.metrics
    k_native = d.native("k", upright=True)
    # Native k contours: C0=upper arm (10 pts), C1=ascender stem (16 pts), C2=lower leg (10 pts).
    c_arm = list(k_native.contours[0])
    c_stem = k_native.contours[1]
    c_leg = list(k_native.contours[2])

    kx0 = min(x for x, _, _ in c_stem)
    kx1 = max(x for x, _, _ in c_stem)
    kw = max(4.0, kx1 - kx0)

    out = Outline(k_native.advance)
    out.contours.append(c_arm)
    pen = Pen(out, kw, m.o_top, m.roundness, m)
    pen.box(kx0, -m.rond_ov_low, kx1, m.x_h + m.rond_ov_low, radius=kw * 0.5 * m.roundness)
    out.contours.append(c_leg)
    d.put("к", out)


def _k_branch_contours(
    p,
    axis_x,
    side,
    top_y,
    bot_y,
    outer_x,
    stem_w,
    arm_w,
    leg_w,
    m,
    cap=True,
):
    """Build K/k-matched 2-contour arm+leg pair on ``side`` (+1 right, -1 left) of ``axis_x``.

    Returns ``(arm_contour, leg_contour, leg_tip_center_x, leg_tip_width)``.
    Inner joins inside the stem and inside the upper arm stay 100% sharp even at ROND=100.
    """
    h = top_y - bot_y
    span = abs(outer_x - axis_x)
    wt = _wt(m, cap)
    ov_max = m.rond_ov_cap_max if cap else m.rond_ov_low_max

    # Hidden stem overlap buried cleanly inside center stem [axis_x - stem_w/2 .. axis_x + stem_w/2].
    inner_x = axis_x - side * min(stem_w * 0.14, 14.0)
    join_mid_y = bot_y + h * (0.455 + 0.015 * wt)

    # Upper arm outer tip stays close to lower leg reach so ROND=100 top center stays balanced.
    arm_span = max(stem_w * 0.72, span * (0.97 + 0.015 * m.roundness))
    arm_tip_out = axis_x + side * arm_span

    # Centerline of arm from inner stem edge to tip.
    arm_c0 = (axis_x + side * stem_w * 0.12, join_mid_y)
    arm_c1 = (arm_tip_out - side * arm_w * 0.56, top_y)
    ux_a, uy_a, _ = _unit(arm_c1[0] - arm_c0[0], arm_c1[1] - arm_c0[1])
    sin_a = max(0.35, abs(uy_a))
    cut_arm_tip = min(arm_w / sin_a, max(arm_w * 0.85, arm_span * 0.58))
    arm_tip_in = arm_tip_out - side * cut_arm_tip

    # Taper diagonal slightly into center stem to prevent heavy-weight center clot.
    arm_w_in = arm_w * (0.78 - 0.10 * wt)
    arm_half_y_in = max(arm_w_in * 0.5 / max(0.35, abs(ux_a)), arm_w_in * 0.54)
    arm_in_bot = _clamp(join_mid_y - arm_half_y_in, bot_y + h * 0.20, top_y - h * 0.38)
    arm_in_top = _clamp(join_mid_y + arm_half_y_in, arm_in_bot + arm_w_in * 0.80, top_y - h * 0.28)

    if side > 0:
        arm_cap = _diag_cap_pts(
            (arm_tip_in, top_y), (inner_x, arm_in_top),
            (arm_tip_out, top_y), (inner_x, arm_in_bot),
            top_y, ov_max, m.roundness, 1.0,
        )
        arm_contour = [(inner_x, arm_in_bot, True), (inner_x, arm_in_top, True)] + arm_cap
    else:
        arm_cap = _diag_cap_pts(
            (arm_tip_out, top_y), (inner_x, arm_in_bot),
            (arm_tip_in, top_y), (inner_x, arm_in_top),
            top_y, ov_max, m.roundness, 1.0,
        )
        arm_contour = arm_cap + [(inner_x, arm_in_top, True), (inner_x, arm_in_bot, True)]
    _ensure_cw(arm_contour)

    # Lower leg (K/k C2-style): top join hidden strictly INSIDE upper arm with wide stem clearance.
    leg_tip_out = outer_x
    t_lo = 0.22 + 0.03 * wt
    t_hi = 0.35 + 0.02 * wt
    # Keep inner leg crotch well clear of center stem so upper V-cleft never pinches into a narrow slit.
    stem_edge_x = axis_x + side * stem_w * 0.50
    min_c_lo = stem_edge_x + side * max(span * 0.12, arm_w * 0.18)
    c_lo_x = inner_x + t_lo * ((arm_tip_in + arm_tip_out) * 0.5 - inner_x)
    if side > 0:
        c_lo_x = _clamp(max(c_lo_x, min_c_lo), stem_edge_x + 4.0, arm_tip_in - 12.0)
    else:
        c_lo_x = _clamp(min(c_lo_x, min_c_lo), arm_tip_in + 12.0, stem_edge_x - 4.0)
    c_hi_x = inner_x + t_hi * ((arm_tip_in + arm_tip_out) * 0.5 - inner_x)
    if side > 0:
        c_hi_x = min(arm_tip_in - 6.0, max(c_hi_x, c_lo_x + min(arm_w * 0.42, span * 0.14)))
    else:
        c_hi_x = max(arm_tip_in + 6.0, min(c_hi_x, c_lo_x - min(arm_w * 0.42, span * 0.14)))

    y_arm_bot_at_lo = _line_y_at_x((inner_x, arm_in_bot), (arm_tip_out, top_y), c_lo_x)
    y_arm_top_at_lo = _line_y_at_x((inner_x, arm_in_top), (arm_tip_in, top_y), c_lo_x)
    y_arm_bot_at_hi = _line_y_at_x((inner_x, arm_in_bot), (arm_tip_out, top_y), c_hi_x)
    y_arm_top_at_hi = _line_y_at_x((inner_x, arm_in_top), (arm_tip_in, top_y), c_hi_x)

    c_lo_y = y_arm_bot_at_lo + 0.30 * max(8.0, y_arm_top_at_lo - y_arm_bot_at_lo)
    c_hi_y = min(bot_y + h * (0.59 + 0.03 * wt), y_arm_bot_at_hi + 0.48 * max(8.0, y_arm_top_at_hi - y_arm_bot_at_hi))
    c_hi_y = max(c_lo_y + 6.0, c_hi_y)

    ux_l, uy_l, _ = _unit(leg_tip_out - c_hi_x, bot_y - c_hi_y)
    sin_l = max(0.35, abs(uy_l))
    cut_leg_tip = min(leg_w / sin_l, max(leg_w * 0.85, span * 0.62))
    leg_tip_in = leg_tip_out - side * cut_leg_tip

    if side > 0:
        leg_cap = _diag_cap_pts(
            (leg_tip_out, bot_y), (c_hi_x, c_hi_y),
            (leg_tip_in, bot_y), (c_lo_x, c_lo_y),
            bot_y, ov_max, m.roundness, -1.0,
        )
        leg_contour = leg_cap + [(c_lo_x, c_lo_y, True), (c_hi_x, c_hi_y, True)]
    else:
        leg_cap = _diag_cap_pts(
            (leg_tip_in, bot_y), (c_lo_x, c_lo_y),
            (leg_tip_out, bot_y), (c_hi_x, c_hi_y),
            bot_y, ov_max, m.roundness, -1.0,
        )
        leg_contour = [(c_hi_x, c_hi_y, True), (c_lo_x, c_lo_y, True)] + leg_cap
    _ensure_cw(leg_contour)

    p.outline.contours.append(arm_contour)
    p.outline.contours.append(leg_contour)

    # Center of rounded bottom tip for attaching descenders (e.g. Җ/җ) seamlessly.
    tip_cx = (leg_cap[3][0] + leg_cap[4][0]) * 0.5
    return arm_contour, leg_contour, tip_cx, cut_leg_tip


def _draw_zhe_branches(p, m, stem_w, weight):
    """Draw two mirrored forks whose outer diagonals meet at mid-height."""
    right = Outline(0)
    waist = p.top * 0.50
    half_width = p.width * 0.5
    overshoot_x = min(stem_w * 0.16, half_width * 0.08) * m.roundness * weight
    span = half_width + overshoot_x
    diagonal = min(stem_w * ((0.90 if p.cap else 0.85) - 0.21 * weight), span * 0.32)
    tip_width = min(diagonal * hypot(span, waist) / waist, span * 0.60)
    tip_x = p.mid + span
    root_x = p.mid + stem_w * 0.22
    joint = (p.mid + stem_w * 0.50 + diagonal * 0.22, waist)
    join_half = min(diagonal * 0.65, waist * 0.23)
    roots = [(root_x, waist + join_half), (root_x, waist - join_half)]

    terminals = []
    overshoot = m.rond_ov_cap_max if p.cap else m.rond_ov_low_max
    for direction, root in zip((1, -1), roots):
        tip_y = waist + direction * waist
        terminal = _diag_cap_pts(
            (tip_x - tip_width, tip_y), root,
            (tip_x, tip_y), joint,
            tip_y, overshoot, m.roundness, direction,
        )
        # One on-curve point at the tip keeps the flat-to-round transition continuous.
        left_tip, right_tip = terminal[3], terminal[4]
        terminal[3:5] = [((left_tip[0] + right_tip[0]) * 0.5, left_tip[1], True)]
        terminals.append(terminal)
    upper, lower = terminals
    _append_cw(right, [(*roots[0], True), *upper, (*joint, True), *reversed(lower), (*roots[1], True)])
    p.outline.add(right.transform(sx=-1.0, dx=2.0 * p.mid))
    p.outline.add(right)


def _zhe_form(d, cap, *, crossing_diagonals=False):
    m = d.metrics
    wt = _wt(m, cap)
    if cap:
        adv = 0.70 * m.X_adv + 0.37 * m.M_adv + max(0.0, m.H_stem - 170.0) * 0.42 * (m.wdth / 100.0)
        lsb = max(8.0, d.sb(38.0, True))
    else:
        adv = 0.48 * m.m_adv + 0.44 * m.k_adv + max(0.0, m.n_stem - 170.0) * 0.46 * (m.wdth / 100.0)
        lsb = max(6.0, d.sb(34.0, False))

    st = m.stem(cap) * (0.98 - 0.03 * wt)
    width = max(st * 2.2, adv - 2.0 * lsb)
    p = d.letter(cap, width=width, left=lsb, right=lsb)

    st = min(st, p.width * 0.31)
    p.stem(p.mid - st * 0.5, weight=st / p.v)
    if crossing_diagonals:
        _draw_zhe_branches(p, m, st, wt)
    else:
        half_span = p.width * 0.5
        arm_w = min(st * (0.84 - 0.14 * wt), half_span * 0.44)
        leg_w = min(st * (0.88 - 0.14 * wt), half_span * 0.46)
        _k_branch_contours(p, p.mid, -1.0, p.top, 0.0, p.left, st, arm_w, leg_w, m, cap=cap)
        _k_branch_contours(p, p.mid, 1.0, p.top, 0.0, p.right, st, arm_w, leg_w, m, cap=cap)
    return p


def _draw_zhe(d):
    d.put("Ж", _zhe_form(d, True, crossing_diagonals=True))
    zhe = _zhe_form(d, False, crossing_diagonals=True).outline
    # Match the bearings of the native diagonal x, with room for the extra stroke.
    x = d.native("x", upright=True)
    left, _, right, _ = x.bounds()
    m = d.metrics
    heavy = max(0.0, (m.wght - 700.0) / 300.0)
    minimum = max(14.0, heavy * max(70.0, m.n_lsb * 0.55))
    bearing = max(minimum, (left + x.advance - right) * 0.50 + d.sb(14.0, False))
    zhe_left, _, zhe_right, _ = zhe.bounds()
    zhe = zhe.transform(dx=bearing - zhe_left)
    zhe.advance = zhe_right - zhe_left + 2.0 * bearing
    d.put("ж", zhe)


def _draw_m_lower(d):
    """Build true x_h miniature of native M (48-pt topology, M inktraps, open valley)."""
    m = d.metrics
    adv = 0.62 * m.n_adv + 0.39 * m.m_adv
    sb_x = m.n_lsb
    st_base = m.n_stem
    width = max(st_base * 2.5, adv - 2.0 * sb_x)
    p = d.letter(False, width=width, left=sb_x, right=sb_x)
    l, r, h, w = p.left, p.right, p.top, p.width
    wt = _wt(m, False)

    st = min(st_base * (0.98 - 0.03 * wt), w * 0.29)
    inner_gap = max(24.0, w - 2.0 * st)
    diag_w = min(st * (0.86 - 0.09 * wt), inner_gap * 0.54)
    trap = min(8.0, st * 0.055)
    ink_apex = min(6.0, st * 0.14)

    cond_lift = max(0.0, (100.0 - m.wdth) / 75.0) * wt * 0.10
    w_light = _clamp(st_base / 170.0, 0.0, 1.0)
    valley_y = h * (0.04 + 0.14 * w_light + 0.18 * wt + cond_lift)
    valley_y = _clamp(valley_y, 12.0, h * 0.52)
    apex_top_half = min(4.0, inner_gap * 0.08)

    # Top diagonal shoulder matches M (~1.35 * stem at 400, narrowing cleanly in black/condensed).
    top_sh = min(w * 0.43, st + max(18.0, min(st * 0.40, inner_gap * 0.24)))
    bot_apex_half = max(3.5, min(diag_w * 0.45, inner_gap * 0.20))

    # Diagonal line from top shoulder to valley apex and bottom V-apex to inner stem inktrap.
    x_li = l + st
    x_ri = r - st
    # Lower edge parallel to upper diagonal at perpendicular offset diag_w.
    l_u0, l_u1 = (l + top_sh, h), (p.mid - apex_top_half, valley_y)
    dx_d, dy_d, len_d = _unit(l_u1[0] - l_u0[0], l_u1[1] - l_u0[1])
    # Downward-left normal from upper edge to lower edge: (dy_d, -dx_d) since dx_d > 0, dy_d < 0 -> wait:
    # (-dy_d, dx_d) points (-(-), +) -> no, (dy_d, -dx_d) points (-, -): left and down!
    nx_l, ny_l = dy_d, -dx_d
    if nx_l > 0:
        nx_l, ny_l = -nx_l, -ny_l
    l_d0 = (l_u0[0] + nx_l * diag_w, l_u0[1] + ny_l * diag_w)
    l_d1 = (l_u1[0] + nx_l * diag_w, l_u1[1] + ny_l * diag_w)

    # Anchor lower diagonal edge through (p.mid - bot_apex_half, 0) while preserving thickness.
    x_at_zero = _line_x_at_y(l_d0, l_d1, 0.0)
    shift_x = (p.mid - bot_apex_half) - x_at_zero
    l_b0 = (l_d0[0] + shift_x * 0.5, l_d0[1])
    l_b1 = (p.mid - bot_apex_half, 0.0)

    notch_y = _clamp(
        _line_y_at_x(l_b0, l_b1, x_li - trap),
        max(18.0, h * 0.20),
        h - max(18.0, st * 0.35),
    )
    notch_step = min(st * 1.45, max(20.0, notch_y * 0.26))
    stem_drop_y = max(10.0, notch_y - notch_step)

    r_stem = st * 0.5 * m.roundness
    ov_l = m.rond_ov_low
    ov_l_max = m.rond_ov_low_max

    bl_pill = _pill_end_pts(x_li, l, -ov_l, r_stem, -1.0, m.roundness)
    tl_corner = _diag_cap_pts(
        (l, h), (l, 0.0),
        (l + top_sh, h), (p.mid - apex_top_half, valley_y),
        h, ov_l_max, m.roundness, 1.0, ov_scale=1.55,
    )
    tr_corner = _diag_cap_pts(
        (r - top_sh, h), (p.mid + apex_top_half, valley_y),
        (r, h), (r, 0.0),
        h, ov_l_max, m.roundness, 1.0, ov_scale=1.55,
    )
    br_pill = _pill_end_pts(r, x_ri, -ov_l, r_stem, -1.0, m.roundness)
    bot_v_cap = _diag_cap_pts(
        (p.mid + bot_apex_half, 0.0), (x_ri + trap - ink_apex, notch_y),
        (p.mid - bot_apex_half, 0.0), (x_li - trap + ink_apex, notch_y),
        0.0, ov_l_max, m.roundness, -1.0, ov_scale=1.0,
    )

    contour = (
        bl_pill
        + tl_corner
        + [(p.mid - apex_top_half, valley_y, True), (p.mid + apex_top_half, valley_y, True)]
        + tr_corner
        + br_pill
        + [
            (x_ri, stem_drop_y, True),
            (x_ri + trap, notch_y, True),
            (x_ri + trap - ink_apex, notch_y, True),
        ]
        + bot_v_cap
        + [
            (x_li - trap + ink_apex, notch_y, True),
            (x_li - trap, notch_y, True),
            (x_li, stem_drop_y, True),
        ]
    )
    _append_cw(p.outline, contour)
    d.put("м", p)


def _draw_u_cap(d):
    """Build cap У from native Y upper V-arms flowing into a smooth Bezier tail."""
    m = d.metrics
    y_native = d.native("Y", upright=True)
    c0 = y_native.contours[0]  # 22 pts: [0]=fork_l, [1..20]=upper arms+crotch, [21]=fork_r
    fork_l = c0[0]
    fork_r = c0[21]
    p_fork_x = (fork_l[0] + fork_r[0]) * 0.5
    w_fork = max(2.0, fork_r[0] - fork_l[0])

    tip_width = min(w_fork * 0.91, max(m.H_stem * 0.84, m.H_bar * 0.92))
    tip_roundness = 0.24 + 0.76 * m.roundness
    x_left_bound = min(x for x, _, _ in c0[1:9])
    x_end = _clamp(
        x_left_bound + tip_width * (0.20 + tip_roundness * 0.50),
        m.Y_adv * 0.06, max(m.Y_adv * 0.08, fork_l[0] - 24.0),
    )
    y_end = tip_width * 0.50 - m.rond_ov_cap

    out = Outline(y_native.advance)
    out.contours.append(_u_cap_single_contour(c0, tip_width, x_end, y_end, tip_roundness))
    d.put("У", out)
    d.set_anchor_x("У", p_fork_x)


def _u_cap_single_contour(c0, tip_width, x_end, y_end, tip_roundness):
    """Build single seamless CW contour merging native Y upper arms (c0[1..20]) with monotonic outer/inner cubics."""
    from cyrillic_outlines import _fit_quads, _pill_cap

    fork_l = c0[0]
    fork_r = c0[21]
    hw1 = tip_width * 0.5
    p_out_end = (x_end, y_end - hw1)
    p_in_end = (x_end, y_end + hw1)

    # Outer & inner cubics with strict control-point dominance: co_k.x >= ci_k.x and co_k.y <= ci_k.y
    # guarantees zero curve crossing even at wght=1 hairlines (fork width = 2u) and opsz=144 wght=1000.
    dx_out = max(10.0, fork_r[0] - p_out_end[0])
    dy_out = max(10.0, fork_r[1] - p_out_end[1])
    co2 = (p_out_end[0] + dx_out * 0.54, p_out_end[1])

    dx_in = max(8.0, fork_l[0] - p_in_end[0])
    in_end_y = min(p_in_end[1], fork_l[1] - max(1.0, min(18.0, fork_l[1] * 0.03)))
    p_in_adj = (p_in_end[0], in_end_y)
    dy_in = max(2.0, fork_l[1] - in_end_y)
    ci1 = (p_in_adj[0] + dx_in * 0.48, in_end_y)
    ci2 = (fork_l[0] - dx_in * 0.08, in_end_y + dy_in * 0.46)
    co1 = (max(ci2[0], fork_r[0] - dx_out * 0.17), p_out_end[1] + dy_out * 0.44)

    steps = 8
    pts_outer, pts_inner = [], []
    for step in range(steps + 1):
        t = step / steps
        u = 1.0 - t
        ox = u**3 * fork_r[0] + 3.0 * u*u*t * co1[0] + 3.0 * u*t*t * co2[0] + t**3 * p_out_end[0]
        oy = u**3 * fork_r[1] + 3.0 * u*u*t * co1[1] + 3.0 * u*t*t * co2[1] + t**3 * p_out_end[1]
        pts_outer.append((ox, oy))
        ix = u**3 * fork_l[0] + 3.0 * u*u*t * ci2[0] + 3.0 * u*t*t * ci1[0] + t**3 * p_in_adj[0]
        iy = u**3 * fork_l[1] + 3.0 * u*u*t * ci2[1] + 3.0 * u*t*t * ci1[1] + t**3 * p_in_adj[1]
        pts_inner.append((ix, iy))

    outer_c = _fit_quads(pts_outer)
    inner_c = _fit_quads(pts_inner)
    tip_pts = _pill_cap(p_out_end, p_in_adj, tip_roundness)
    tail_pts = outer_c + tip_pts + inner_c[::-1]

    contour = list(c0[1:21]) + tail_pts
    return _ensure_cw(contour)


def _draw_straight_y_and_bar(d):
    """Build y-matched straight-stemmed lowercase ү and Kazakhstan Ұ / ұ."""
    m = d.metrics
    y_native = d.native("y", upright=True)
    c0 = y_native.contours[0]  # 30 pts; [6..25] are exact upper V-arms + crotch!
    wt = _wt(m, False)

    crotch_l = c0[15]
    crotch_r = c0[16]
    cx = (crotch_l[0] + crotch_r[0]) * 0.5
    crotch_y = (crotch_l[1] + crotch_r[1]) * 0.5

    arm_span = max(20.0, c0[24][0] - c0[7][0])
    st = min(m.n_stem * (0.98 - 0.03 * wt), arm_span * 0.54)
    sx0, sx1 = cx - st * 0.5, cx + st * 0.5

    # Bottom fork outer corners stay strictly below crotch_y even at hairline wght=1 (crotch_y ~ 3).
    y_fork = min(crotch_y - 6.0, max(-18.0, crotch_y - max(st * 0.65, 16.0)))

    out = Outline(y_native.advance)
    arms = [(sx0 + 1.5, y_fork, True)] + list(c0[6:26]) + [(sx1 - 1.5, y_fork, True)]
    _append_cw(out, arms)

    stem_top = min(m.x_h * 0.62, max(24.0, crotch_y + max(18.0, st * 0.28)))
    stem_bot = max(-462.0, m.desc_y - m.rond_ov_low)
    pen = Pen(out, st, m.o_top, m.roundness, m)
    pen.box(sx0, stem_bot, sx1, stem_top, radius=st * 0.5 * m.roundness)
    d.put("ү", out)

    # Ұ (uppercase): native Y + horizontal bar across lower vertical stem.
    y_cap = d.native("Y", upright=True)
    yc0, yc1 = y_cap.contours[0], y_cap.contours[1]
    y_crotch = min(y for _, y, _ in yc0)
    y_stem_top = max(y for _, y, _ in yc1)
    stem_cx = (min(x for x, _, _ in yc1) + max(x for x, _, _ in yc1)) * 0.5
    y_bar = 0.46 * min(y_crotch, y_stem_top)
    bar_h = min(m.E_mid * 0.88, max(20.0, y_bar * 0.65))
    half_bw = min(m.Y_adv * 0.27, m.H_stem * 1.40)

    p_big = Letter.from_metrics(m, cap=True, width=1.0)
    p_big.outline = y_cap.copy()
    p_big.bar(
        stem_cx - half_bw, y_bar, stem_cx + half_bw,
        thickness=bar_h,
        overhang_left=True,
        overhang_right=True,
    )
    d.put("Ұ", p_big)

    # ұ (lowercase): ү + horizontal bar cleanly centered on descender stem below baseline.
    y_bar_low = m.desc_y * 0.41
    bar_h_low = min(m.o_top * 0.80, abs(m.desc_y) * 0.32)
    half_bw_low = min(y_native.advance * 0.27, m.n_stem * 1.38)

    p_low = Letter.from_metrics(m, cap=False, width=1.0)
    p_low.outline = out.copy()
    p_low.bar(
        cx - half_bw_low, y_bar_low, cx + half_bw_low,
        thickness=bar_h_low,
        overhang_left=True,
        overhang_right=True,
    )
    d.put("ұ", p_low)


def _draw_che_body(p, l, r, m, cap):
    """Draw right stem + u-matched 2-handle cup of Ч/ч without self-intersections."""
    wt = _wt(m, cap)
    st = min(m.stem(cap) * (1.0 - 0.03 * wt), (r - l) * 0.42)
    p.stem(r - st, weight=st / p.v)

    waist_y = p.top * (0.39 - 0.04 * wt)
    cup_bar = (m.E_mid * 0.94 if cap else (0.55 * m.o_top + 0.45 * m.e_bar))
    cup_bar = min(cup_bar, (p.top - waist_y) * 0.56)
    leg_w = min(st * 0.96, (r - l) * 0.40)
    arch_ov = (m.cap_ov if cap else m.low_ov) * 0.35
    join_x = r - st * 0.52

    p.curved_arch(
        join_x,
        p.top,
        l,
        waist_y,
        leg_w=leg_w,
        top_bar=cup_bar,
        join_frac=0.50 if cap else 0.55,
        arch_ov=arch_ov,
        bottom_y=p.tip,
        inktrap=True,
        invert=True,
        mirror=True,
    )
    return st, waist_y, cup_bar


def _draw_che(d):
    """Build modern Ч and ч using u-matched curved_arch."""
    m = d.metrics
    for cap in (True, False):
        ch = "Ч" if cap else "ч"
        p = d.adv_letter(cap, adv_ratio=0.94 if cap else 0.98, ref="H" if cap else "n")
        _draw_che_body(p, p.left, p.right, m, cap)
        d.put(ch, p)


def _add_descender(p, x_center, y_join, depth, desc_w, m, cap):
    """Add a subtly tapered descender with sharp hidden top join and circular pill tip."""
    ov_max = m.rond_ov_cap_max if cap else m.rond_ov_low_max
    hw_top = desc_w * 0.5
    hw_bot = hw_top * 0.88
    tip_y = -depth
    pts = [
        (x_center - hw_top, y_join, True),
        (x_center + hw_top, y_join, True),
    ] + _diag_cap_pts(
        (x_center + hw_bot, tip_y), (x_center + hw_top, y_join),
        (x_center - hw_bot, tip_y), (x_center - hw_top, y_join),
        tip_y, ov_max, m.roundness, -1.0,
    )
    _append_cw(p.outline, pts)


def _append_j_hook_cw(p, start_x, start_y, y_bot_outer, tip_x, hook_w, m, dir_x=-1.0):
    """Append an analytic ROND-clean CW curved J-hook curling left (dir_x=-1) or right (dir_x=+1)."""
    hw = hook_w * 0.5
    r_tip = hw * m.roundness
    y_bot_in = y_bot_outer + hook_w
    y_tip_mid = y_bot_outer + hw
    wx_out = start_x - dir_x * hw
    wx_in = start_x + dir_x * hw
    if dir_x < 0.0:
        tip_x = min(tip_x, wx_in - hw - 6.0)
    else:
        tip_x = max(tip_x, wx_in + hw + 6.0)
    tip_face_x = tip_x - dir_x * r_tip
    y_turn_out = y_bot_outer * 0.35
    y_turn_in = y_bot_in * 0.35
    pts = [
        (wx_in, start_y, True),
        (wx_out, start_y, True),
        (wx_out, y_turn_out, True),
        (wx_out, y_bot_outer * 0.90, False),
        (tip_face_x + (wx_out - tip_face_x) * 0.48, y_bot_outer, False),
        (tip_face_x, y_bot_outer, True),
        (tip_face_x + dir_x * r_tip * ARC_K, y_bot_outer, False),
        (tip_x, y_tip_mid - r_tip * ARC_K, False),
        (tip_x, y_tip_mid, True),
        (tip_x, y_tip_mid + r_tip * ARC_K, False),
        (tip_face_x + dir_x * r_tip * ARC_K, y_bot_in, False),
        (tip_face_x, y_bot_in, True),
        (tip_face_x + (wx_in - tip_face_x) * 0.45, y_bot_in, False),
        (wx_in, y_bot_in * 0.85, False),
        (wx_in, y_turn_in, True),
    ]
    if dir_x > 0.0:
        pts.reverse()
    _append_cw(p.outline, pts)


def _k_leg_anchor(leg_c, y_join):
    """Return ROND-invariant (center_x, width) of a K/k/Ж/ж bottom-right leg at y_join."""
    # Straight inner/outer leg edges in 10-pt K/k/Ж contours are always [4]->[3] and [5]->[6]
    # when ordered CW with crotch at [4],[5]; fallback cleanly by sorting y-coordinates.
    pts_top = sorted([(i, p) for i, p in enumerate(leg_c) if p[1] > 180.0], key=lambda item: item[1])
    if len(leg_c) == 10 and leg_c[4][1] > 150.0 and leg_c[5][1] > 150.0:
        x_l = _line_x_at_y(leg_c[4], leg_c[3], y_join)
        x_r = _line_x_at_y(leg_c[5], leg_c[6], y_join)
    elif len(pts_top) >= 2:
        i0, p0 = pts_top[0]
        i1, p1 = pts_top[1]
        n = len(leg_c)
        n0 = leg_c[(i0 - 1) % n] if leg_c[(i0 - 1) % n][1] < p0[1] else leg_c[(i0 + 1) % n]
        n1 = leg_c[(i1 + 1) % n] if leg_c[(i1 + 1) % n][1] < p1[1] else leg_c[(i1 - 1) % n]
        x_l = _line_x_at_y(p0, n0, y_join)
        x_r = _line_x_at_y(p1, n1, y_join)
    else:
        xs = [p[0] for p in leg_c if p[1] <= max(y_join * 2.0, 120.0)]
        return (min(xs) + max(xs)) * 0.5, max(20.0, max(xs) - min(xs))
    if x_l > x_r:
        x_l, x_r = x_r, x_l
    return (x_l + x_r) * 0.5, max(20.0, x_r - x_l)


def _x_leg_anchor(x_out, y_join):
    """Return ROND-invariant (center_x, width) of X/x bottom-right leg at y_join."""
    c0 = x_out.contours[0]  # 20 pts: [4]->[3] inner edge, [15]->[16] outer edge
    x_l = _line_x_at_y(c0[4], c0[3], y_join)
    x_r = _line_x_at_y(c0[15], c0[16], y_join)
    if x_l > x_r:
        x_l, x_r = x_r, x_l
    return (x_l + x_r) * 0.5, max(20.0, x_r - x_l)


def _draw_ka_derived(d):
    """Draw К/к-derived extended letters: Қ/қ, Ҝ/ҝ, Ҟ/ҟ, Ԟ/ԟ, Ҡ/ҡ."""
    m = d.metrics
    for cap in (True, False):
        ch_k = "К" if cap else "к"
        wt = _wt(m, cap)
        st = m.stem(cap)
        top_h = m.cap_h if cap else m.x_h

        # Қ / қ: К/к with sleek tapered descender inside bottom-right leg.
        k_out = d.get(ch_k).copy()
        y_join = min(st * 0.45, top_h * 0.10)
        leg_cx, leg_w = _k_leg_anchor(k_out.contours[2], y_join)
        p = Letter.from_metrics(m, cap=cap, width=1.0)
        p.outline = k_out
        desc_w = min(st * (0.74 - 0.08 * wt), leg_w * 0.72)
        depth = min(285.0, 225.0 + 0.14 * st) if cap else min(225.0, 178.0 + 0.10 * st)
        _add_descender(p, leg_cx, y_join, depth, desc_w, m, cap)
        p.outline.advance += st * 0.14
        d.put("Қ" if cap else "қ", p)

        # Ҝ / ҝ: К/к with vertical bar cleanly centered over arm-leg junction (ROND-invariant).
        k_out = d.get(ch_k).copy()
        stem_l = min(x for x, _, _ in k_out.contours[1])
        stem_r = max(x for x, _, _ in k_out.contours[1])
        fork_x = max(k_out.contours[2][4][0], k_out.contours[2][5][0])
        adv_k = k_out.advance
        bar_x = max(stem_r + st * 0.34, fork_x * 0.96)
        bar_w = min(st * (0.64 - 0.08 * wt), max(24.0, (adv_k - stem_r) * 0.24))
        p = Letter.from_metrics(m, cap=cap, width=1.0)
        p.outline = k_out
        p.stem(bar_x - bar_w * 0.5, bottom=top_h * 0.22, top=top_h * 0.78, weight=bar_w / p.v)
        d.put("Ҝ" if cap else "ҝ", p)

        # Ҟ / ҟ: К/к with upper-left horizontal bar across stem (ROND-invariant).
        k_out = d.get(ch_k).copy()
        stem_l = min(x for x, _, _ in k_out.contours[1])
        span_est = max(st * 1.5, k_out.advance - stem_l * 1.1)
        ext_l = max(34.0, span_est * 0.15)
        shift_x = ext_l * 0.65
        k_shifted = k_out.transform(dx=shift_x)
        p = Letter.from_metrics(m, cap=cap, width=1.0)
        p.outline = k_shifted
        p.outline.advance = k_out.advance + shift_x
        sl = stem_l + shift_x
        bar_h = min((m.E_mid if cap else m.f_bar) * 0.84, top_h * 0.16)
        p.bar(
            sl - ext_l, top_h * (0.77 if cap else 0.75), sl + st + span_est * 0.20,
            thickness=bar_h,
            overhang_left=True,
            overhang_right=True,
        )
        d.put("Ҟ" if cap else "ҟ", p)

        # Ԟ / ԟ: К/к with diagonal slash across upper arm (ROND-invariant).
        k_out = d.get(ch_k).copy()
        stem_l = min(x for x, _, _ in k_out.contours[1])
        arm_c = k_out.contours[0]
        arm_mid_x = (arm_c[1][0] + arm_c[2][0]) * 0.5 + (k_out.advance - stem_l) * 0.03
        arm_mid_y = (arm_c[1][1] + arm_c[2][1]) * 0.5
        slash_dx = max(st * 0.68, (k_out.advance - stem_l) * 0.15)
        slash_dy = max(st * 0.64, top_h * 0.145)
        p = Letter.from_metrics(m, cap=cap, width=1.0)
        p.outline = k_out
        p.diagonal(
            arm_mid_x - slash_dx * 0.70, min(top_h * 0.95, arm_mid_y + slash_dy * 0.92),
            arm_mid_x + slash_dx * 0.86, max(top_h * 0.54, arm_mid_y - slash_dy * 0.86),
            weight=0.58 - 0.08 * wt,
        )
        d.put("Ԟ" if cap else "ԟ", p)

        # Ҡ / ҡ (Bashkir Qa): Г-style top-left overhang bar + stem + K/k arm+leg.
        p = d.adv_letter(cap, adv_ratio=0.92, ref="K" if cap else "k", left=45.0, right=25.0)
        ext_l = max(st * 0.72, p.width * 0.18)
        stem_x = p.left + ext_l
        bar_h = m.E_mid if cap else m.f_bar
        p.stem(stem_x, bottom=-m.rond_ov(cap), top=p.top, top_r=0.0, weight=st / p.v)
        p.bar(
            p.left, p.top - bar_h * 0.5, stem_x + st * 0.85,
            thickness=bar_h,
            overhang_left=True,
            corner_radii=(bar_h * 0.5 * m.roundness, bar_h * 0.5 * m.roundness, m.spine_r(st), 0.0),
            sharp_corners=(3,),
        )
        span = max(st * 1.4, p.right - (stem_x + st * 0.5))
        arm_w = min(st * (0.90 - 0.10 * wt), span * 0.48)
        leg_w = min(st * (0.94 - 0.10 * wt), span * 0.50)
        _k_branch_contours(
            p, stem_x + st * 0.5, 1.0, p.top, 0.0, p.right, st, arm_w, leg_w, m, cap=cap
        )
        d.put("Ҡ" if cap else "ҡ", p)


def _draw_zhe_x_derived(d):
    """Draw Ж/Х-derived extended letters: Җ/җ, Ҳ/ҳ, Ӽ/ӽ, Ӿ/ӿ."""
    m = d.metrics
    for cap in (True, False):
        wt = _wt(m, cap)
        st = m.stem(cap)
        top_h = m.cap_h if cap else m.x_h
        depth = min(285.0, 225.0 + 0.14 * st) if cap else min(225.0, 178.0 + 0.10 * st)
        desc_w = st * (0.74 - 0.08 * wt)
        y_join = min(st * 0.45, top_h * 0.10)

        # Җ / җ retain the leg geometry used by the descender join.
        zhe = _zhe_form(d, cap).outline
        leg_cx, leg_w = _k_leg_anchor(zhe.contours[4], y_join)
        p = Letter.from_metrics(m, cap=cap, width=1.0)
        p.outline = zhe
        dw = min(desc_w, leg_w * 0.72)
        _add_descender(p, leg_cx, y_join, depth, dw, m, cap)
        p.outline.advance += st * 0.14
        d.put("Җ" if cap else "җ", p)

        # Ҳ / ҳ (X/x + tapered descender on bottom-right leg with sharp hidden join)
        x_out = d.native("X" if cap else "x", upright=True)
        leg_cx, leg_w = _x_leg_anchor(x_out, y_join)
        p = Letter.from_metrics(m, cap=cap, width=1.0)
        p.outline = x_out.copy()
        dw = min(desc_w, leg_w * 0.72)
        _add_descender(p, leg_cx, y_join, depth, dw, m, cap)
        p.outline.advance += st * 0.14
        d.put("Ҳ" if cap else "ҳ", p)

        # Ӽ / ӽ (X/x + bounded hook on bottom-right leg, >= -350)
        x_out = d.native("X" if cap else "x", upright=True)
        leg_cx, leg_w = _x_leg_anchor(x_out, y_join)
        p = Letter.from_metrics(m, cap=cap, width=1.0)
        p.outline = x_out.copy()
        y_bot_outer = -(255.0 if cap else 205.0) - 0.12 * st
        hook_w = min(st * (0.76 - 0.08 * wt), leg_w * 0.76, abs(y_bot_outer) * 0.60)
        hook_reach = max(hook_w * 1.25 + 10.0, 195.0 if cap else 155.0)
        _append_j_hook_cw(
            p, leg_cx, min(st * 0.55, top_h * 0.12), y_bot_outer,
            leg_cx + hook_reach, hook_w, m, dir_x=1.0,
        )
        p.outline.advance += max(110.0, hook_reach * 0.78)
        d.put("Ӽ" if cap else "ӽ", p)

        # Ӿ / ӿ (X/x + middle horizontal bar, ROND-invariant)
        x_out = d.native("X" if cap else "x", upright=True)
        c0, c1 = x_out.contours[0], x_out.contours[1]
        cx_cross = x_out.advance * 0.5
        half_bar = max(st * 0.90, x_out.advance * 0.33)
        p = Letter.from_metrics(m, cap=cap, width=1.0)
        p.outline = x_out.copy()
        bar_h = (m.E_mid * 0.88 if cap else m.o_top * 0.85)
        p.bar(
            cx_cross - half_bar, top_h * 0.50, cx_cross + half_bar,
            thickness=bar_h,
            overhang_left=True,
            overhang_right=True,
        )
        d.put("Ӿ" if cap else "ӿ", p)


def _draw_che_m_n_k_hook_derived(d):
    """Draw Ҷ/ҷ, Ҹ/ҹ, Ӌ/ӌ, Ӎ/ӎ, Ӄ/ӄ, Ӈ/ӈ, Ꙧ/ꙧ, Ꚇ/ꚇ, Ꚓ/ꚓ."""
    m = d.metrics
    for cap in (True, False):
        wt = _wt(m, cap)
        st = m.stem(cap)
        top_h = m.cap_h if cap else m.x_h
        depth = min(280.0, 220.0 + 0.14 * st) if cap else min(220.0, 174.0 + 0.10 * st)
        desc_w = st * (0.74 - 0.08 * wt)

        # Ҷ / ҷ (Ч/ч + tapered descender aligned on right stem)
        che_out = d.get("Ч" if cap else "ч").copy()
        _, _, cr, _ = che_out.bounds()
        p = Letter.from_metrics(m, cap=cap, width=1.0)
        p.outline = che_out
        _add_descender(p, cr - desc_w * 0.5, min(st * 0.45, top_h * 0.10), depth, desc_w, m, cap)
        d.put("Ҷ" if cap else "ҷ", p)

        # Ҹ / ҹ (Ч/ч + centered vertical bar across cup bottom, ROND-invariant)
        che_out = d.get("Ч" if cap else "ч").copy()
        cl, _, cr, _ = che_out.bounds()
        p = Letter.from_metrics(m, cap=cap, width=1.0)
        p.outline = che_out
        bar_w = min(st * (0.64 - 0.08 * wt), (cr - cl) * 0.22)
        bar_cx = cl + (cr - cl) * 0.45
        p.stem(bar_cx - bar_w * 0.5, bottom=top_h * 0.20, top=top_h * 0.74, weight=bar_w / p.v)
        d.put("Ҹ" if cap else "ҹ", p)

        # Ӌ / ӌ (Ч/ч + left-curling hook descender, bounded >= -350)
        che_out = d.get("Ч" if cap else "ч").copy()
        cl, _, cr, _ = che_out.bounds()
        p = Letter.from_metrics(m, cap=cap, width=1.0)
        p.outline = che_out
        y_bot_outer = -(260.0 if cap else 208.0) - 0.12 * st
        hook_w = min(st * (0.76 - 0.08 * wt), abs(y_bot_outer) * 0.60, max(12.0, (cr - cl) * 0.28))
        sx = cr - st * 0.50
        curl_len = min(max(hook_w * 1.20, st * 1.10), abs(y_bot_outer) * 1.25)
        _append_j_hook_cw(
            p, sx, min(st * 0.45, top_h * 0.10), y_bot_outer,
            sx - curl_len, hook_w, m, dir_x=-1.0,
        )
        d.put("Ӌ" if cap else "ӌ", p)

        # Ӎ / ӎ (М/м + right-stem descender + subtle shelf)
        em_out = d.get("М" if cap else "м").copy()
        _, _, er, _ = em_out.bounds()
        p = Letter.from_metrics(m, cap=cap, width=1.0)
        p.outline = em_out
        shelf_h = min(m.E_mid if cap else m.o_top, top_h * 0.14) * 0.85
        ext_r = st * 0.16
        p.bar(er - st * 0.5, shelf_h * 0.5, er + ext_r, thickness=shelf_h,
              corner_radii=(0.0, 0.0, min(shelf_h * 0.45 * m.roundness, 28.0), 0.0),
              sharp_corners=(0, 1, 3))
        _add_descender(p, er + ext_r - desc_w * 0.5, shelf_h * 0.6, depth, desc_w, m, cap)
        p.outline.advance += ext_r
        d.put("Ӎ" if cap else "ӎ", p)

        # Ӄ / ӄ (Ka with hook — fix -657 overflow -> strictly bounded >= -350!)
        p = d.adv_letter(cap, adv_ratio=0.82 if cap else 0.78, ref="K" if cap else "k", right=48.0)
        l, r, h = p.left, p.right, p.top
        st_k = min(st, p.width * 0.38)
        p.stem(l, weight=st_k / p.v)
        # Upper K-matched diagonal arm
        span = max(st_k * 1.2, r - (l + st_k * 0.5))
        arm_w = min(st_k * (0.90 - 0.10 * wt), span * 0.48)
        inner_x = l + st_k * 0.5
        arm_tip_out = r
        ux_a, uy_a, _ = _unit(arm_tip_out - inner_x, h * 0.58)
        cut_arm = min(arm_w / max(0.35, abs(uy_a)), arm_w * 1.4, span * 0.58)
        arm_tip_in = arm_tip_out - cut_arm
        arm_in_bot = h * (0.37 - 0.03 * wt)
        arm_in_top = min(h * 0.72, arm_in_bot + max(arm_w * 1.15, st_k * 0.90))
        ov_max = m.rond_ov_cap_max if cap else m.rond_ov_low_max
        arm_cap = _diag_cap_pts(
            (arm_tip_in, h), (inner_x, arm_in_top),
            (arm_tip_out, h), (inner_x, arm_in_bot),
            h, ov_max, m.roundness, 1.0,
        )
        _append_cw(p.outline, [(inner_x, arm_in_bot, True), (inner_x, arm_in_top, True)] + arm_cap)
        # Bounded curved hook dropping from fork and curling left (analytic CW, ROND 0..100 clean)
        y_bot_outer = -(280.0 if cap else 220.0) - 0.12 * st_k
        hook_w = min(st_k * (0.76 - 0.10 * wt), span * 0.38, abs(y_bot_outer) * 0.56)
        hw = hook_w * 0.5
        r_tip = hw * m.roundness
        y_bot_in = y_bot_outer + hook_w
        y_tip_mid = y_bot_outer + hw
        hx_out = max(l + st_k + hook_w + 16.0, r)
        hx_in = hx_out - hook_w
        curl_dx = min(max(st_k * 1.15, span * 0.52), span * 0.72)
        tip_x = max(l + st_k * 0.55, min(hx_in - hw - 10.0, hx_out - curl_dx))
        fork_top = min(h * 0.54, arm_in_bot + max(hook_w * 0.42, st_k * 0.36))
        fork_bot = max(fork_top * 0.16, fork_top - max(hook_w * 1.08, 4.0))
        fork_mid_out = fork_top * 0.46
        fork_mid_in = max(0.0, fork_mid_out - max(hook_w * 0.35, 2.0))
        y_turn_out = y_bot_outer * 0.35
        y_turn_in = y_bot_in * 0.35
        _append_cw(
            p.outline,
            [
                (inner_x, fork_top, True),
                (inner_x + (hx_out - inner_x) * 0.58, fork_top, False),
                (hx_out, fork_top - (fork_top - fork_mid_out) * 0.50, False),
                (hx_out, fork_mid_out, True),
                (hx_out, y_turn_out, True),
                (hx_out, y_bot_outer * 0.90, False),
                (tip_x + r_tip + (hx_out - (tip_x + r_tip)) * 0.48, y_bot_outer, False),
                (tip_x + r_tip, y_bot_outer, True),
                (tip_x + r_tip * (1.0 - ARC_K), y_bot_outer, False),
                (tip_x, y_tip_mid - r_tip * ARC_K, False),
                (tip_x, y_tip_mid, True),
                (tip_x, y_tip_mid + r_tip * ARC_K, False),
                (tip_x + r_tip * (1.0 - ARC_K), y_bot_in, False),
                (tip_x + r_tip, y_bot_in, True),
                (tip_x + r_tip + (hx_in - (tip_x + r_tip)) * 0.45, y_bot_in, False),
                (hx_in, y_bot_in * 0.85, False),
                (hx_in, y_turn_in, True),
                (hx_in, fork_mid_in, True),
                (hx_in, fork_bot - (fork_bot - fork_mid_in) * 0.50, False),
                (inner_x + (hx_in - inner_x) * 0.58, fork_bot, False),
                (inner_x, fork_bot, True),
            ],
        )
        d.put("Ӄ" if cap else "ӄ", p)

        # Ӈ / ӈ (Н/н + bounded curved descender hook, fix -578 overflow -> >= -350!)
        n_src = d.get("Н" if cap else "н").copy()
        nl, _, nr, _ = n_src.bounds()
        p = Letter.from_metrics(m, cap=cap, width=1.0)
        p.outline = n_src
        y_bot_outer = -(275.0 if cap else 215.0) - 0.14 * st
        hook_w = min(st * (0.82 - 0.08 * wt), (nr - nl) * 0.32, abs(y_bot_outer) * 0.60)
        sx = nr - st * 0.50
        curl_len = min(max(hook_w * 1.20, (nr - nl) * 0.42), abs(y_bot_outer) * 1.25)
        _append_j_hook_cw(
            p, sx, min(st * 0.45, top_h * 0.10), y_bot_outer,
            sx - curl_len, hook_w, m, dir_x=-1.0,
        )
        d.put("Ӈ" if cap else "ӈ", p)

        # Ꙧ / ꙧ (М/м + right horizontal top bar at flat cap_h/x_h, 0 top overshoot)
        em_out = d.get("М" if cap else "м").copy()
        _, _, er, _ = em_out.bounds()
        p = Letter.from_metrics(m, cap=cap, width=1.0)
        p.outline = em_out
        bar_h = m.E_mid if cap else m.o_top
        ext_r = max(st * 1.10, (295.0 if cap else 236.0) * (p.outline.advance / (1732.0 if cap else 1378.0)))
        p.bar(
            er - st * 0.62, top_h - bar_h * 0.5, er + ext_r,
            thickness=bar_h,
            overhang_right=True,
        )
        p.outline.advance += ext_r
        d.put("Ꙧ" if cap else "ꙧ", p)

        # Ꚇ / ꚇ (double Ч/ч)
        p = d.adv_letter(cap, adv_ratio=1.42 if cap else 1.46, ref="H" if cap else "n")
        l, r, h = p.left, p.right, p.top
        st_c = min(st, p.width * 0.24)
        mid_st_x = p.mid - st_c * 0.48
        waist_y = h * (0.39 - 0.04 * wt)
        cup_bar = (m.E_mid * 0.92 if cap else (0.55 * m.o_top + 0.45 * m.e_bar))
        cup_bar = min(cup_bar, (h - waist_y) * 0.54)
        arch_ov = (m.cap_ov if cap else m.low_ov) * 0.35

        # Left cup joining middle stem
        p.stem(mid_st_x, weight=st_c / p.v)
        p.curved_arch(
            mid_st_x + st_c * 0.48, h, l, waist_y,
            leg_w=st_c * 0.94, top_bar=cup_bar, join_frac=0.58 if cap else 0.55,
            arch_ov=arch_ov, bottom_y=p.tip,
            inktrap=True, invert=True, mirror=True,
        )
        # Right cup joining right stem
        p.stem(r - st_c, weight=st_c / p.v)
        p.curved_arch(
            r - st_c * 0.52, h, mid_st_x + st_c * 0.08, waist_y,
            leg_w=st_c * 0.94, top_bar=cup_bar, join_frac=0.58 if cap else 0.55,
            arch_ov=arch_ov, bottom_y=p.tip,
            inktrap=True, invert=True, mirror=True,
        )
        d.put("Ꚇ" if cap else "ꚇ", p)

        # Ꚓ / ꚓ (Ч/ч + T-style left top overhang bar)
        p = d.adv_letter(cap, adv_ratio=0.98 if cap else 1.02, ref="H" if cap else "n", left=64.0)
        ext_l = max(st * 0.55, 84.0 * (p.width / 1050.0))
        cup_l = p.left + ext_l
        st_t, _, _ = _draw_che_body(p, cup_l, p.right, m, cap)
        bar_h = m.E_mid if cap else m.f_bar
        p.bar(
            p.left, p.top - bar_h * 0.5, cup_l + (p.right - cup_l) * 0.56,
            thickness=bar_h,
            overhang_left=True,
            overhang_right=True,
        )
        d.put("Ꚓ" if cap else "ꚓ", p)


def _draw_izhitsa(d):
    """Build Ѵ and ѵ with V-matched left arm and bounded curved right scroll."""
    m = d.metrics
    for cap in (True, False):
        wt = _wt(m, cap)
        st = m.stem(cap)
        p = d.adv_letter(cap, adv_ratio=0.92 if cap else 0.88, ref="Y" if cap else "k", left=33.0, right=38.0)
        l, r, h = p.left, p.right, p.top
        ov_max = m.rond_ov_cap_max if cap else m.rond_ov_low_max
        arm_w = min(st * (0.98 - 0.06 * wt), p.width * 0.34)
        apex_half = max(3.5, min(st * 0.24, p.width * 0.10))
        v_cx = l + p.width * 0.44

        # Left diagonal arm with diagonal top pill cap and rounded V-bottom apex.
        l_top_l, l_top_r = l, l + min(arm_w * 1.18, p.width * 0.34)
        l_bot_l, l_bot_r = v_cx - apex_half, v_cx + apex_half * 0.8

        top_cap = _diag_cap_pts((l_top_l, h), (l_bot_l, 0.0), (l_top_r, h), (l_bot_r, h * 0.22), h, ov_max, m.roundness, 1.0)
        bot_cap = _diag_cap_pts((l_bot_r, 0.0), (l_top_r, h), (l_bot_l, 0.0), (l_top_l, h), 0.0, ov_max, m.roundness, -1.0)
        _append_cw(p.outline, top_cap + bot_cap)

        # Right sweeping curved scroll bounded strictly within [0 .. h + cap_ov].
        from cyrillic_outlines import _fit_quads, _pill_cap
        sc_w = min(st * (0.86 - 0.10 * wt), p.width * 0.28)
        y_top_outer = h + (m.cap_ov if cap else m.low_ov)
        scroll_r = max(v_cx + sc_w + 18.0, r)
        p_out0 = (v_cx + min(apex_half * 0.75, sc_w * 0.35), 0.0)
        p_in0 = (v_cx - apex_half * 0.25, min(h * 0.22, max(16.0, sc_w * 0.65)))
        tip_x = scroll_r - sc_w * 0.5 * m.roundness
        p_in_end = (tip_x, y_top_outer)
        p_out_end = (tip_x, y_top_outer - sc_w)
        ci1 = (v_cx + (tip_x - v_cx) * 0.22, h * 0.62)
        ci2 = (tip_x - (tip_x - v_cx) * 0.32, y_top_outer)
        co1 = (p_out0[0] + (tip_x - p_out0[0]) * 0.28, h * 0.50)
        co2 = (tip_x - (tip_x - p_out0[0]) * 0.34, y_top_outer - sc_w)
        pts_in, pts_out = [], []
        for step in range(9):
            t = step / 8.0
            u = 1.0 - t
            pts_in.append((
                u**3 * p_in0[0] + 3.0 * u*u*t * ci1[0] + 3.0 * u*t*t * ci2[0] + t**3 * p_in_end[0],
                u**3 * p_in0[1] + 3.0 * u*u*t * ci1[1] + 3.0 * u*t*t * ci2[1] + t**3 * p_in_end[1],
            ))
            pts_out.append((
                u**3 * p_out0[0] + 3.0 * u*u*t * co1[0] + 3.0 * u*t*t * co2[0] + t**3 * p_out_end[0],
                u**3 * p_out0[1] + 3.0 * u*u*t * co1[1] + 3.0 * u*t*t * co2[1] + t**3 * p_out_end[1],
            ))
        in_c = _fit_quads(pts_in)
        out_c = _fit_quads(pts_out)
        tip_pts = _pill_cap(p_in_end, p_out_end, m.roundness)
        _append_cw(p.outline, in_c + tip_pts + out_c[::-1])
        d.put("Ѵ" if cap else "ѵ", p)


def _draw_ligatures(d):
    """Draw Ԕ/ԕ (Л+Х), Ԫ/ԫ (Д+К), Ԭ/ԭ (Д+Ч)."""
    m = d.metrics
    for cap in (True, False):
        wt = _wt(m, cap)
        st = m.stem(cap)

        # Ԕ / ԕ (Л/л + Х/х)
        el_out = d.get("Л" if cap else "л").copy()
        _, _, er, _ = el_out.bounds()
        ha_out = d.native("X" if cap else "x", upright=True)
        hl, _, _, _ = ha_out.bounds()
        sx_ha = 0.86
        gap = max(st * 0.55, er - hl * sx_ha - min(st * 0.48, 150.0))
        p = Letter.from_metrics(m, cap=cap, width=1.0)
        p.outline = el_out
        p.outline.add(ha_out.transform(sx=sx_ha, dx=gap))
        p.outline.advance = gap + ha_out.advance * sx_ha
        d.put("Ԕ" if cap else "ԕ", p)

        # Ԫ / ԫ (Д/д + К/к)
        de_out = d.get("Д" if cap else "д").copy()
        _, _, dr, _ = de_out.bounds()
        ka_out = d.get("К" if cap else "к").copy()
        kl, _, _, _ = ka_out.bounds()
        sx_ka = 0.84
        # Align К's left stem cleanly onto Д's right stem so shared stem has no double step.
        x_ka = dr - st * 0.95 - kl * sx_ka
        p = Letter.from_metrics(m, cap=cap, width=1.0)
        p.outline = de_out
        p.outline.add(ka_out.transform(sx=sx_ka, dx=x_ka))
        p.outline.advance = x_ka + ka_out.advance * sx_ka
        d.put("Ԫ" if cap else "ԫ", p)

        # Ԭ / ԭ (Д + Ч ligature: left Д-leg + Ч cup + shelf + 2 descenders)
        p = d.adv_letter(cap, adv_ratio=1.04 if cap else 1.06, ref="H" if cap else "n", left=68.0, right=68.0)
        l, r, h = p.left, p.right, p.top
        shelf = min(m.E_mid if cap else m.o_top, h * 0.15) * 0.88
        st_r = min(st * (0.96 - 0.03 * wt), p.width * 0.28)
        cup_w = min(st_r * 0.92, p.width * 0.24)
        waist_y = h * (0.40 - 0.04 * wt)
        cup_bar = min(m.E_mid * 0.90 if cap else (0.55 * m.o_top + 0.45 * m.e_bar), (h - waist_y) * 0.54)
        arch_ov = (m.cap_ov if cap else m.low_ov) * 0.35

        # Right stem + Ч cup
        p.stem(r - st_r, bottom=0.0, top=p.tip, weight=st_r / p.v)
        p.curved_arch(
            r - st_r * 0.52, h, l + cup_w * 0.25, waist_y,
            leg_w=cup_w, top_bar=cup_bar, join_frac=0.58 if cap else 0.55,
            arch_ov=arch_ov, bottom_y=p.tip,
            inktrap=True, invert=True, mirror=True,
        )
        # Left slanted leg from top of cup/right stem down to baseline shelf
        leg_diag_w = min(st * 0.86, p.width * 0.22)
        p.polygon([
            (l + st * 0.12, shelf * 0.45),
            (l + leg_diag_w * 1.18, shelf * 0.50),
            (r - st_r * 0.32, h),
            (r - st_r * 0.38 - leg_diag_w, h),
        ], corner_radii=(None, None, m.spine_r(st) * 0.5, m.spine_r(st) * 0.5), sharp_corners=(0, 1))
        # Baseline shelf + 2 bounded descenders
        sh_r = min(shelf * 0.45 * m.roundness, 24.0)
        p.bar(l, shelf * 0.5, r, thickness=shelf, corner_radii=(0.0, sh_r, sh_r, 0.0), sharp_corners=(0, 3))
        depth = min(265.0 if cap else 210.0, 210.0 + 0.12 * st) if cap else min(210.0, 168.0 + 0.08 * st)
        desc_w = min(st * (0.72 - 0.06 * wt), p.width * 0.22)
        _add_descender(p, l + desc_w * 0.5, shelf * 0.65, depth, desc_w, m, cap)
        _add_descender(p, r - desc_w * 0.5, shelf * 0.65, depth, desc_w, m, cap)
        d.put("Ԭ" if cap else "ԭ", p)


def _draw_yus_single(p, m, cap, big=False, closed=False, blend=False):
    """Build Google Sans Yus shapes (Ѧ/ѧ, Ѫ/ѫ, Ꙙ/ꙙ, Ꙛ/ꙛ) with zero self-intersections."""
    l, c, r, h = p.left, p.mid, p.right, p.top
    w = p.width
    wt = _wt(m, cap)
    st_base = m.stem(cap)
    ov_max = m.rond_ov_cap_max if cap else m.rond_ov_low_max
    stem = min(st_base * (0.86 - 0.08 * wt), w * 0.16)

    if big:
        tip = h * (0.40 + 0.02 * wt)
        inset = w * (0.14 if blend else 0.09)
        shoulder = min((m.E_mid if cap else m.e_bar) * 0.84, h * 0.135)
        top_w = max(stem * 1.4, (r - inset) - (l + inset))
        eye_half = max(12.0, (top_w * 0.5) * (1.0 - shoulder / max(10.0, h - tip)) - min(st_base * 0.60, w * 0.110))
        eye_tip = min(h * 0.56, tip + max(shoulder * 0.82, 20.0))
        if eye_tip >= h - shoulder - 10.0:
            eye_tip = (tip + (h - shoulder)) * 0.5

        cap_r = min(st_base * 0.36 * m.roundness, shoulder * 0.45)
        p.polygon(
            [(l + inset, h), (r - inset, h), (c + stem * 0.46, tip), (c - stem * 0.46, tip)],
            corner_radii=(cap_r, cap_r, cap_r * 0.45, cap_r * 0.45),
        )
        hole_r = min(cap_r * 0.55, eye_half * 0.25, (h - shoulder - eye_tip) * 0.22)
        p.polygon(
            [(c - eye_half, h - shoulder), (c, eye_tip), (c + eye_half, h - shoulder)],
            radius=hole_r,
            hole=True,
        )
        if blend:
            bar_t = min(m.E_mid * 0.72 if cap else m.e_bar * 0.88, h * 0.12)
            p.bar(l + w * 0.16, h * 0.275, r - w * 0.16, thickness=bar_t)

        # Curved outer legs sweeping from fork down to baseline cleanly.
        leg_w = min(stem * 0.96, w * 0.175)
        for side in (-1.0, 1.0):
            x_foot = l + leg_w * 0.52 if side < 0 else r - leg_w * 0.52
            p.curved_leg(
                (c, tip + h * 0.03),
                (c + side * w * 0.24, h * 0.36),
                (x_foot, h * 0.22),
                (x_foot, 0.0),
                start_width=leg_w * 0.90,
                end_width=leg_w,
                flat_end_y=0.0,
                cap_start=0.0,
                foot_radius=min(leg_w * 0.38 * m.roundness, (m.E_mid if cap else m.o_top) * 0.35),
            )
        p.stem(c - stem * 0.5, bottom=-m.rond_ov(cap), top=tip + h * 0.04, weight=stem / p.v)
    elif closed:
        peak = min(st_base * 0.45, w * 0.078)
        channel = min(st_base * 0.66, w * 0.13)
        foot_r = min(st_base * 0.35 * m.roundness, w * 0.08)
        p.polygon(
            [(l, 0.0), (c - peak, h), (c + peak, h), (r, 0.0)],
            radius=foot_r,
        )
        upper_bar = min(m.E_mid * 0.92 if cap else m.o_top * 0.92, h * 0.22)
        eye_l = min(c - 6.0, l + w * 0.21 + channel)
        eye_r = max(c + 6.0, r - w * 0.21 - channel)
        eye_bot = min(h * 0.45, h - upper_bar - 10.0)
        hole_r = min(foot_r * 0.45, (eye_r - eye_l) * 0.20, max(1.0, (h - upper_bar - eye_bot) * 0.20))
        p.polygon([(eye_l, eye_bot), (c, h - upper_bar), (eye_r, eye_bot)], radius=hole_r, hole=True)

        bot_bar = max(h * 0.085, min(m.E_mid if cap else m.o_top, h * 0.145))
        trap_l0 = min(c - 8.0, l + w * 0.09 + channel)
        trap_r0 = max(c + 8.0, r - w * 0.09 - channel)
        trap_l1 = min(c - 6.0, l + w * 0.17 + channel)
        trap_r1 = max(c + 6.0, r - w * 0.17 - channel)
        trap_top = max(bot_bar + 10.0, h * 0.32)
        hole_r2 = min(foot_r * 0.40, (trap_r1 - trap_l1) * 0.20, (trap_top - bot_bar) * 0.20)
        p.polygon([(trap_l0, bot_bar), (trap_l1, trap_top), (trap_r1, trap_top), (trap_r0, bot_bar)],
                  radius=hole_r2, hole=True)
    else:
        feet = min(st_base * 1.02, w * 0.22)
        peak = min(st_base * 0.48, w * 0.082)
        crotch_y = h * (0.76 - 0.06 * wt)
        crotch_y = min(crotch_y, h * 0.82)
        l_cap = _diag_cap_pts((l + feet, 0.0), (c, crotch_y), (l, 0.0), (c - peak, h), 0.0, ov_max, m.roundness, -1.0)
        t_cap = _diag_cap_pts((c - peak, h), (l, 0.0), (c + peak, h), (r, 0.0), h, ov_max, m.roundness, 1.0)
        r_cap = _diag_cap_pts((r, 0.0), (c + peak, h), (r - feet, 0.0), (c, crotch_y), 0.0, ov_max, m.roundness, -1.0)
        _append_cw(p.outline, l_cap + t_cap + r_cap + [(c, crotch_y, True)])
        bar_t = min(m.E_mid * 0.90 if cap else m.o_top * 0.90, h * 0.15)
        p.bar(l + w * 0.24, h * 0.405, r - w * 0.24, thickness=bar_t)
        p.stem(c - stem * 0.5, bottom=-m.rond_ov(cap), top=h * 0.43, weight=stem / p.v)


def _draw_yus_family(d):
    """Build all Yus glyphs: Ѧ/ѧ, Ѩ/ѩ, Ѫ/ѫ, Ѭ/ѭ, Ꙙ/ꙙ, Ꙛ/ꙛ, Ꙝ/ꙝ, Ꙟ/ꙟ."""
    m = d.metrics
    for cap in (True, False):
        wt = _wt(m, cap)
        st = m.stem(cap)

        for chars, big, closed, blend in (
            ("Ѧѧ", False, False, False),
            ("Ѫѫ", True, False, False),
            ("Ꙙꙙ", False, True, False),
            ("Ꙛꙛ", True, False, True),
        ):
            p = d.adv_letter(cap, adv_ratio=0.92 if cap else 0.94, ref="A" if False else ("M" if big else "H"),
                             left=46.0, right=42.0)
            # Match A/Ѧ proportions cleanly (~1200 cap, ~980 lower at 400).
            target_adv = (m.H_adv * 0.90 if cap else m.n_adv * 0.94) if not big else (m.M_adv * 0.78 if cap else m.m_adv * 0.68)
            sb_x = d.sb(46.0, cap)
            p = d.letter(cap, width=max(st * 2.8, target_adv - 2.0 * sb_x), left=sb_x, right=sb_x)
            _draw_yus_single(p, m, cap, big=big, closed=closed, blend=blend)
            d.put(chars[0 if cap else 1], p)

        for chars, base_pair in (("Ѩѩ", "Ѧѧ"), ("Ѭѭ", "Ѫѫ"), ("Ꙝꙝ", "Ꙙꙙ")):
            body = d.get(base_pair[0 if cap else 1])
            p = d.letter(cap, width=1.0)
            p.stem(p.left, weight=st / p.v)
            gap = max(st * 0.65, 165.0 if cap else 130.0)
            sx_b = 0.92
            x_b = p.left + st + gap
            p.outline.add(body.transform(sx=sx_b, dx=x_b))
            p.outline.advance = x_b + body.advance * sx_b
            bar_t = min(m.E_mid if cap else m.o_top, p.top * 0.15)
            p.bar(p.left + st * 0.45, p.top * 0.42, x_b + body.advance * 0.48, thickness=bar_t)
            d.put(chars[0 if cap else 1], p)

        # Ꙟ / ꙟ (Yn): inverted V + bounded central descender + left curved hook
        p = d.adv_letter(cap, adv_ratio=1.02 if cap else 1.04, ref="H" if cap else "n", left=42.0, right=34.0)
        l, r, h, c = p.left, p.right, p.top, p.mid
        ov_max = m.rond_ov_cap_max if cap else m.rond_ov_low_max
        bar_t = min(m.E_mid * 0.88 if cap else m.o_top * 0.88, h * 0.145)
        y_base_top = bar_t * 0.85
        arm_w = min(st * (0.84 - 0.08 * wt), p.width * 0.24)
        peak_h = min(st * 0.36, p.width * 0.10)
        crotch_y = h * (0.76 - 0.04 * wt)

        # Inverted A-shell pointing up (feet at y_base_top, peak at top)
        l_cap = _diag_cap_pts((l + arm_w, y_base_top), (c, crotch_y), (l, y_base_top), (c - peak_h, h),
                              y_base_top, ov_max, m.roundness, -1.0, ov_scale=0.35)
        t_cap = _diag_cap_pts((c - peak_h, h), (l, y_base_top), (c + peak_h, h), (r, y_base_top),
                              h, ov_max, m.roundness, 1.0)
        r_cap = _diag_cap_pts((r, y_base_top), (c + peak_h, h), (r - arm_w, y_base_top), (c, crotch_y),
                              y_base_top, ov_max, m.roundness, -1.0, ov_scale=0.35)
        _append_cw(p.outline, l_cap + t_cap + r_cap + [(c, crotch_y, True)])

        mid_st = min(st * (0.86 - 0.06 * wt), p.width * 0.20)
        desc_bot = -(335.0 if cap else 265.0) - 0.08 * st
        p.stem(c - mid_st * 0.5, bottom=desc_bot, top=h * 0.84, weight=mid_st / p.v)
        hook_w = min(st * (0.64 - 0.06 * wt), p.width * 0.18, max(8.0, bar_t * 0.90))
        y_bot_outer = -max(38.0, bar_t * 0.65 + hook_w * 0.85)
        hook_w = min(hook_w, abs(y_bot_outer) * 0.60)
        hook_sx = l + max(arm_w * 0.34, hook_w * 0.55)
        tip_x = hook_sx + max(hook_w * 1.35 + 12.0, arm_w * 0.95)
        _append_j_hook_cw(
            p, hook_sx, y_base_top + max(18.0, bar_t * 0.25),
            y_bot_outer, tip_x, hook_w, m, dir_x=1.0,
        )
        d.put("Ꙟ" if cap else "ꙟ", p)


def draw_diag_letters(d):
    """Draw all diagonal, fork, and Yus Cyrillic glyphs onto Design ``d``."""
    # 1. И (cap)
    _draw_i_cap(d)

    # 2. и (lower)
    _draw_i_lower(d)

    # 3. к (lower only; К is homoglyph K)
    _draw_k_lower(d)

    # 4. Ж, ж
    _draw_zhe(d)

    # 5. м (lower only; М is homoglyph M)
    _draw_m_lower(d)

    # 6. У (cap only; у is homoglyph y)
    _draw_u_cap(d)

    # 7. ү (lower only; Ү is homoglyph Y) + Ұ, ұ
    _draw_straight_y_and_bar(d)

    # 8. Ч, ч
    _draw_che(d)

    # 9. Extended diagonal/hook/ligature/Yus families
    _draw_ka_derived(d)
    _draw_zhe_x_derived(d)
    _draw_che_m_n_k_hook_derived(d)
    _draw_izhitsa(d)
    _draw_ligatures(d)
    _draw_yus_family(d)
