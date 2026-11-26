# SPDX-License-Identifier: OFL-1.1
"""Arch, square, and descender Cyrillic glyphs in final master coordinates.

Exposes ``draw_arch_letters(d)`` for ``cyrillic_design.py`` and constructs every
glyph directly in final Google Sans Flex master space via ``Design`` and
``Letter`` primitives (no unscaled horizontal distortion).
"""

from __future__ import annotations

from math import sqrt

from cyrillic_outlines import ARC_K, _pill_cap, _signed_area, _unit


def _corner_arc(prev_pt, vx, vy, next_pt, radius):
    """Build a 4-point 2-off-curve circular corner matching ``Pen.polygon`` topology."""
    ux1, uy1, l1 = _unit(prev_pt[0] - vx, prev_pt[1] - vy)
    ux2, uy2, l2 = _unit(next_pt[0] - vx, next_pt[1] - vy)
    cos_a = max(-0.9999, min(0.9999, ux1 * ux2 + uy1 * uy2))
    di = 0.0
    if radius > 0.0:
        tan_half = sqrt(max(0.0, (1.0 + cos_a) / max(1e-9, 1.0 - cos_a)))
        di = min(radius * tan_half, l1 * 0.45, l2 * 0.45)
    cos_half = sqrt(max(0.0, (1.0 - cos_a) * 0.5))
    s = cos_half / (1.0 + cos_half)
    return [
        (vx + di * ux1, vy + di * uy1, True),
        (vx + di * (1.0 - s) * ux1, vy + di * (1.0 - s) * uy1, False),
        (vx + di * (1.0 - s) * ux2, vy + di * (1.0 - s) * uy2, False),
        (vx + di * ux2, vy + di * uy2, True),
    ]


def _bar_thickness(m, cap, kind="arch"):
    """Return native-matched horizontal stroke thickness for arch glyphs."""
    if cap:
        if kind == "mid":
            return m.E_mid
        if kind == "F_mid":
            return m.F_mid
        if kind == "shelf":
            return m.H_bar * 0.85
        if kind == "u_bar":
            return m.H_bar * 0.94
        return m.H_bar
    if kind == "mid":
        return 0.38 * m.e_bar + 0.62 * m.o_top
    if kind == "t_bar":
        return m.f_bar
    if kind == "shelf":
        return m.o_top * 0.85
    if kind == "u_bar":
        return m.o_top * 0.94
    return m.o_top


def _gamma_adv_width(d, cap, extra_stem=0.0):
    """Compute F/f-harmonized advance width for Г-family letters."""
    m = d.metrics
    if cap:
        return m.F_adv * 0.98 + extra_stem * m.H_stem
    # Blend f_adv and n_adv so wght=900/1000 keeps generous arm overhang beside н/п.
    return 0.5 * (m.f_adv * 1.10) + 0.5 * (m.n_adv * 0.76) + extra_stem * m.n_stem


def _draw_gamma_body(p, m, cap, *, serif=False, desc_left_foot=False, arm_scale=1.0):
    """Draw a unified single-contour Г / Ґ / Ӷ spine + top bar (and optional serif/foot)."""
    l, top = p.left, p.top
    r = l + p.width * arm_scale
    sw = p.v
    bar_h = _bar_thickness(m, cap, "arch")
    rnd = m.roundness
    bot = 0.0 if desc_left_foot else -m.rond_ov(cap)
    stem_r = sw * 0.50 * rnd
    spine_r = sw * 0.55 * rnd
    bar_r = bar_h * 0.50 * rnd

    if serif:
        min_gap = max(24.0, (r - l - sw) * 0.18)
        serif_w = min(sw * 0.68, max(8.0, (r - l - sw - min_gap) * 0.65))
        serif_h = top * (0.135 if cap else 0.165)
        serif_r = serif_w * 0.50 * rnd
        elbow_r = min(spine_r, serif_w * 0.55 * rnd, bar_h * 0.55 * rnd)
        verts = [
            (l, bot),
            (l, top),
            (r - serif_w, top),
            (r - serif_w, top + serif_h),
            (r, top + serif_h),
            (r, top - bar_h),
            (l + sw, top - bar_h),
            (l + sw, bot),
        ]
        radii = [stem_r, spine_r, None, serif_r, serif_r, elbow_r, None, stem_r]
        p.polygon(verts, corner_radii=radii)
        return

    bar_end = r + (m.h_overhang(cap) if arm_scale >= 0.95 else 0.0)
    if desc_left_foot:
        ext = sw * 0.20
        shelf_h = min(_bar_thickness(m, cap, "shelf"), top * 0.18)
        desc_w = min(sw * 0.78, (r - l) * 0.45)
        desc_depth = 265.0 if cap else 205.0
        r_shelf = l + sw + ext
        desc_l = r_shelf - desc_w
        elbow_r = min(ext * 0.85, shelf_h * 0.48) * rnd
        desc_r = desc_w * 0.50 * rnd
        verts = [
            (l, 0.0),
            (l, top),
            (bar_end, top),
            (bar_end, top - bar_h),
            (l + sw, top - bar_h),
            (l + sw, shelf_h),
            (r_shelf, shelf_h),
            (r_shelf, -desc_depth),
            (desc_l, -desc_depth),
            (desc_l, 0.0),
        ]
        radii = [spine_r, spine_r, bar_r, bar_r, None, None, elbow_r, desc_r, desc_r, None]
        p.polygon(verts, corner_radii=radii)
        return

    verts = [
        (l, bot),
        (l, top),
        (bar_end, top),
        (bar_end, top - bar_h),
        (l + sw, top - bar_h),
        (l + sw, bot),
    ]
    radii = [stem_r, spine_r, bar_r, bar_r, None, stem_r]
    p.polygon(verts, corner_radii=radii)


def _draw_gamma_body_at(p, m, cap, *, stem_l, bar_r_x):
    """Draw a unified Г spine + top bar anchored at ``stem_l`` extending to ``bar_r_x``."""
    top = p.top
    sw = p.v
    bar_h = _bar_thickness(m, cap, "arch")
    rnd = m.roundness
    bot = -m.rond_ov(cap)
    stem_r = sw * 0.50 * rnd
    spine_r = sw * 0.55 * rnd
    bar_r = bar_h * 0.50 * rnd
    bar_end = bar_r_x + m.h_overhang(cap)
    verts = [
        (stem_l, bot),
        (stem_l, top),
        (bar_end, top),
        (bar_end, top - bar_h),
        (stem_l + sw, top - bar_h),
        (stem_l + sw, bot),
    ]
    p.polygon(verts, corner_radii=[stem_r, spine_r, bar_r, bar_r, None, stem_r])


def _draw_pe_body(p, m, cap, *, right_desc=False, desc_depth=265.0):
    """Draw a unified single-contour П / п (or Ԥ / ԥ with right shelf descender)."""
    l, r, top = p.left, p.right, p.top
    sw = p.v
    bar_h = _bar_thickness(m, cap, "arch")
    rnd = m.roundness
    bot = -m.rond_ov(cap)
    stem_r = sw * 0.50 * rnd
    spine_r = sw * 0.55 * rnd

    if not right_desc:
        verts = [
            (l, bot),
            (l, top),
            (r, top),
            (r, bot),
            (r - sw, bot),
            (r - sw, top - bar_h),
            (l + sw, top - bar_h),
            (l + sw, bot),
        ]
        radii = [stem_r, spine_r, spine_r, stem_r, stem_r, None, None, stem_r]
        p.polygon(verts, corner_radii=radii)
        return

    ext = sw * 0.16
    r_stem = r - ext
    shelf_h = min(_bar_thickness(m, cap, "shelf"), top * 0.18)
    desc_w = min(sw * 0.78, (r_stem - l) * 0.28)
    desc_r = desc_w * 0.50 * rnd
    elbow_r = min(ext * 0.85, shelf_h * 0.48) * rnd
    verts = [
        (l, bot),
        (l, top),
        (r_stem, top),
        (r_stem, shelf_h),
        (r, shelf_h),
        (r, -desc_depth),
        (r - desc_w, -desc_depth),
        (r - desc_w, 0.0),
        (r_stem - sw, 0.0),
        (r_stem - sw, top - bar_h),
        (l + sw, top - bar_h),
        (l + sw, bot),
    ]
    radii = [stem_r, spine_r, spine_r, None, elbow_r, desc_r, desc_r, None, spine_r, None, None, stem_r]
    p.polygon(verts, corner_radii=radii)


def _draw_en_body(p, m, cap, *, tail_mode=None, desc_depth=265.0):
    """Draw Н/н-structured two-stem body (lowercase н, plus extended Н/н variants)."""
    l, r, top = p.left, p.right, p.top
    sw = p.v
    rnd = m.roundness
    stem_r = sw * 0.50 * rnd
    mid_bar_h = _bar_thickness(m, cap, "mid")

    if tail_mode == "descender":
        ext = sw * 0.16
        r_stem = r - ext
        shelf_h = min(_bar_thickness(m, cap, "shelf"), top * 0.18)
        desc_w = min(sw * 0.78, (r_stem - l) * 0.28)
        desc_r = desc_w * 0.50 * rnd
        elbow_r = min(ext * 0.85, shelf_h * 0.48) * rnd
        p.stem(l)
        p.bar(l + sw * 0.45, top * 0.505, r_stem - sw * 0.45, thickness=mid_bar_h, radius=0.0)
        verts = [
            (r_stem - sw, 0.0),
            (r_stem - sw, p.tip),
            (r_stem, p.tip),
            (r_stem, shelf_h),
            (r, shelf_h),
            (r, -desc_depth),
            (r - desc_w, -desc_depth),
            (r - desc_w, 0.0),
        ]
        radii = [stem_r, stem_r, stem_r, None, elbow_r, desc_r, desc_r, None]
        p.polygon(verts, corner_radii=radii)
        return

    if tail_mode == "tail":
        flare = sw * 0.22
        r_stem = r - flare
        p.stem(l)
        p.stem(r_stem - sw)
        p.bar(l + sw * 0.45, top * 0.505, r_stem - sw * 0.45, thickness=mid_bar_h, radius=0.0)
        _draw_tail_foot(p, m, cap, r_stem - sw, r_stem, flare=flare, depth=desc_depth)
        return

    p.stem(l)
    p.stem(r - sw)
    p.bar(l + sw * 0.45, top * 0.505, r - sw * 0.45, thickness=mid_bar_h, radius=0.0)


def _draw_tail_foot(p, m, cap, stem_l, stem_r, *, flare, depth):
    """Draw a clean flared right-leg tail with sharp hidden top join and pill bottom."""
    sw = stem_r - stem_l
    tail_w = sw * 0.82
    x_top_l = stem_r - tail_w
    bot_r = tail_w * 0.50 * m.roundness
    bar_h = _bar_thickness(m, cap, "shelf")
    p.polygon(
        [
            (x_top_l, bar_h * 0.45),
            (stem_r, bar_h * 0.45),
            (stem_r + flare, -depth),
            (stem_r + flare - tail_w, -depth),
        ],
        corner_radii=[None, None, bot_r, bot_r],
    )


def _draw_el_contour(
    p,
    m,
    cap,
    *,
    l_body,
    r_body,
    stem_bot=None,
    round_right_bot=True,
):
    """Draw the curved Л body used by the extended Cyrillic letters."""
    top = p.top
    foot_y = 0.0
    sw = p.v
    bar_h = _bar_thickness(m, cap, "u_bar") if cap else _bar_thickness(m, cap, "arch")
    rnd = m.roundness
    width = max(sw * 2.2, r_body - l_body)

    min_cnt = max(36.0, (width - sw) * 0.16)
    leg_w = min(sw * (0.88 if cap else 0.87), max(16.0, (width - sw - min_cnt) * 0.56))
    foot_w = leg_w * 1.06
    avail = max(24.0, width - sw - leg_w)
    target_cnt = min(162.0 if cap else 154.0, avail * 0.48)
    sweep = min(width * 0.31, max(avail * 0.36, avail - target_cnt))
    sh_x = l_body + sweep
    sh_in = sh_x + leg_w

    sp_r_left = min(leg_w * 0.55 * rnd, sweep * 0.65, (r_body - sh_x) * 0.45, (top - bar_h) * 0.45)
    sp_r_right = min(sw * 0.55 * rnd, (r_body - sh_x) * 0.45, (top - bar_h) * 0.45)

    y_bot_r = (-m.rond_ov(cap) if stem_bot is None else stem_bot)
    stem_r = (sw * 0.50 * rnd) if round_right_bot else 0.0

    # G1-smooth 2-span quadratic S-inflection: C1 vertical at top shoulder, C2 approaching foot.
    span_h = max(40.0, top - foot_y)
    c1_out = (sh_x, min(top - sp_r_left - 18.0, foot_y + span_h * 0.56))
    c2_out = (l_body + sweep * 0.18, foot_y + span_h * 0.25)
    m_out = ((c1_out[0] + c2_out[0]) * 0.5, (c1_out[1] + c2_out[1]) * 0.5)

    c1_in = (sh_in, min(top - bar_h - 18.0, foot_y + span_h * 0.52 - bar_h * 0.20))
    c2_in = (l_body + foot_w + sweep * 0.18, max(foot_y + 18.0, foot_y + span_h * 0.23 - leg_w * 0.12))
    m_in = ((c1_in[0] + c2_in[0]) * 0.5, (c1_in[1] + c2_in[1]) * 0.5)

    foot_roundness = 0.16 + 0.84 * rnd if cap else rnd
    toe_r = min(leg_w * (0.48 if cap else 0.40), span_h * 0.15) * foot_roundness
    heel_r = min(leg_w * (0.36 if cap else 0.22), span_h * 0.12) * foot_roundness

    toe_pt = (l_body, foot_y)
    heel_pt = (l_body + foot_w, foot_y)

    c_toe = _corner_arc(heel_pt, toe_pt[0], toe_pt[1], c2_out, toe_r)
    c_tl = _corner_arc(c1_out, sh_x, top, (r_body, top), sp_r_left)
    c_tr = _corner_arc((sh_x, top), r_body, top, (r_body, y_bot_r), sp_r_right)
    c_br = _corner_arc((r_body, top), r_body, y_bot_r, (r_body - sw, y_bot_r), stem_r)
    c_bl = _corner_arc((r_body, y_bot_r), r_body - sw, y_bot_r, (r_body - sw, top - bar_h), stem_r)
    c_heel = _corner_arc(c2_in, heel_pt[0], heel_pt[1], toe_pt, heel_r)

    contour = (
        c_toe
        + [(c2_out[0], c2_out[1], False), (m_out[0], m_out[1], True), (c1_out[0], c1_out[1], False)]
        + c_tl
        + c_tr
        + c_br
        + c_bl
        + [(r_body - sw, top - bar_h, True), (sh_in, top - bar_h, True)]
        + [(c1_in[0], c1_in[1], False), (m_in[0], m_in[1], True), (c2_in[0], c2_in[1], False)]
        + c_heel
    )
    p.outline.contours.append(contour)


def _draw_lintel(p, m):
    """Draw a straight, gently rounded Л/л with an open counter."""
    left, right = p.left, p.right
    stem = min(p.v, p.width * 0.34)
    left_stem = stem * 0.89
    sweep = min(p.width * 0.26, p.width * 0.85 - stem - left_stem)
    bottom = -m.rond_ov(p.cap)
    bar_h = min(_bar_thickness(m, p.cap, "u_bar" if p.cap else "arch"), p.top * 0.27)
    inner_top = p.top - bar_h
    inner_sweep = (inner_top - bottom) / (p.top - bottom)
    top_left = left + sweep
    points = [
        (left, bottom),
        (top_left, p.top),
        (right, p.top),
        (right, bottom),
        (right - stem, bottom),
        (right - stem, inner_top),
        (left + left_stem + sweep * inner_sweep, inner_top),
        (left + left_stem, bottom),
    ]
    shoulder = min(stem * 0.48, bar_h * 0.66) * m.roundness
    inner = min(stem, bar_h) * 0.12 * m.roundness
    left_foot = left_stem * 0.43 * m.roundness
    right_foot = stem * 0.48 * m.roundness
    p.polygon(points, corner_radii=(
        left_foot, shoulder, shoulder, right_foot, right_foot, inner, inner, left_foot,
    ))
    return (top_left + right) * 0.5


def _draw_de(p, m, *, shelf_h, desc_depth):
    """Draw a mirrored roof and a wider, rounded base for Д/д."""
    rnd = m.roundness
    spread = min(p.width * 0.038, p.v * 0.25)
    left, right = p.left + spread, p.right + spread
    shelf_left, shelf_right = p.left, p.right + 2.0 * spread
    inset = min(p.v * 0.52, p.width * 0.098)
    body_width = p.width - 2.0 * inset
    stem = min(p.v, body_width * 0.31)
    sweep = min(body_width * 0.108, body_width * 0.425 - stem)
    wall_bottom = shelf_h * 0.45
    side_at_shelf = inset + sweep * (shelf_h - wall_bottom) / (p.top - wall_bottom)
    bar_h = min(_bar_thickness(m, p.cap, "u_bar" if p.cap else "arch"), p.top * 0.27)
    inner_top = p.top - bar_h
    inner_inset = inset + stem + sweep * (inner_top - wall_bottom) / (p.top - wall_bottom)

    soft = 0.12 + 0.88 * rnd
    desc_w = min(p.v * (0.91 if p.cap else 0.88), (shelf_right - shelf_left) * 0.22)
    desc_r = desc_w * 0.50 * soft
    shelf_r = min((side_at_shelf + spread) * 0.58, shelf_h * 0.52) * soft
    join = min(side_at_shelf + spread, shelf_h) * 0.24 * soft
    underside = min(shelf_h * 0.28, desc_w * 0.20) * soft
    shoulder = min(stem * 0.48, bar_h * 0.66) * rnd
    inner = min(stem, bar_h) * 0.12 * rnd
    counter_bottom = min(shelf_h * 0.20, stem * 0.15) * soft
    outline = [
        (shelf_left, -desc_depth),
        (shelf_left, shelf_h),
        (left + side_at_shelf, shelf_h),
        (left + inset + sweep, p.top),
        (right - inset - sweep, p.top),
        (right - side_at_shelf, shelf_h),
        (shelf_right, shelf_h),
        (shelf_right, -desc_depth),
        (shelf_right - desc_w, -desc_depth),
        (shelf_right - desc_w, 0.0),
        (shelf_left + desc_w, 0.0),
        (shelf_left + desc_w, -desc_depth),
    ]
    p.polygon(outline, corner_radii=(
        desc_r, shelf_r, join, shoulder, shoulder, join,
        shelf_r, desc_r, desc_r, underside, underside, desc_r,
    ))
    p.polygon([
        (left + side_at_shelf + stem, shelf_h),
        (left + inner_inset, inner_top),
        (right - inner_inset, inner_top),
        (right - side_at_shelf - stem, shelf_h),
    ], hole=True, corner_radii=(counter_bottom, inner, inner, counter_bottom))
    p.outline.advance += 2.0 * spread


def _draw_u_comb(
    p,
    m,
    cap,
    *,
    stems=2,
    right_desc=False,
    center_desc=False,
    desc_depth=265.0,
    desc_reach=0.0,
    left_t_bar=False,
):
    """Draw unified single-contour Ц, ц, Џ, џ, Ш, ш, Щ, щ, Ҵ, ҵ."""
    l, r, top, tip = p.left, p.right, p.top, p.tip
    rnd = m.roundness
    bar_h = _bar_thickness(m, cap, "u_bar")
    base_sw = p.v
    min_gap = max(22.0, p.width * (0.075 if stems == 3 else 0.10))
    raw_sw = base_sw * 0.96 if stems == 3 else base_sw

    ext = (raw_sw * 0.16) if right_desc else 0.0
    body_l = l + min(base_sw * 0.85, p.width * 0.22) if left_t_bar else l
    r_stem = r - ext
    body_w = max(48.0, r_stem - body_l)
    sw = min(raw_sw, (body_w - (stems - 1) * min_gap) / stems)
    if right_desc:
        ext = sw * 0.16
        r_stem = r - ext
        body_w = max(48.0, r_stem - body_l)
        sw = min(raw_sw, (body_w - (stems - 1) * min_gap) / stems)

    stem_r = sw * 0.50 * rnd
    spine_r = sw * 0.55 * rnd

    verts = [(body_l, tip), (body_l + sw, tip)]
    radii = [stem_r, stem_r]

    if stems == 3:
        mid_x = (body_l + r_stem) * 0.5
        mid_l = mid_x - sw * 0.5
        mid_r = mid_x + sw * 0.5
        verts.extend([
            (body_l + sw, bar_h),
            (mid_l, bar_h),
            (mid_l, tip),
            (mid_r, tip),
            (mid_r, bar_h),
            (r_stem - sw, bar_h),
        ])
        radii.extend([None, None, stem_r, stem_r, None, None])
    else:
        verts.extend([
            (body_l + sw, bar_h),
            (r_stem - sw, bar_h),
        ])
        radii.extend([None, None])

    verts.extend([(r_stem - sw, tip), (r_stem, tip)])
    radii.extend([stem_r, stem_r])

    if right_desc:
        shelf_h = min(_bar_thickness(m, cap, "shelf"), top * 0.18)
        desc_w = min(sw * 0.78, body_w * 0.28)
        desc_r = desc_w * 0.50 * rnd
        extra_reach = min(sw * desc_reach, (p.outline.advance - r) * 0.48)
        desc_right = r + extra_reach
        elbow_r = min((ext + extra_reach) * 0.85, shelf_h * 0.48) * rnd
        verts.extend([
            (r_stem, shelf_h),
            (desc_right, shelf_h),
            (desc_right, -desc_depth),
            (desc_right - desc_w, -desc_depth),
            (desc_right - desc_w, 0.0),
            (body_l, 0.0),
        ])
        radii.extend([None, elbow_r, desc_r, desc_r, None, spine_r])
    elif center_desc:
        desc_w = min(sw * 0.80, body_w * 0.28)
        desc_r = desc_w * 0.50 * rnd
        mid_c = (body_l + r_stem) * 0.5
        verts.extend([
            (r_stem, 0.0),
            (mid_c + desc_w * 0.5, 0.0),
            (mid_c + desc_w * 0.5, -desc_depth),
            (mid_c - desc_w * 0.5, -desc_depth),
            (mid_c - desc_w * 0.5, 0.0),
            (body_l, 0.0),
        ])
        radii.extend([spine_r, None, desc_r, desc_r, None, spine_r])
    else:
        verts.extend([(r_stem, 0.0), (body_l, 0.0)])
        radii.extend([spine_r, spine_r])

    if left_t_bar:
        t_bar_h = _bar_thickness(m, cap, "arch" if cap else "t_bar")
        t_bar_l = l - m.h_overhang(cap)
        bar_r = t_bar_h * 0.50 * rnd
        verts.extend([
            (body_l, top - t_bar_h),
            (t_bar_l, top - t_bar_h),
            (t_bar_l, top),
            (body_l, top),
        ])
        radii.extend([None, bar_r, bar_r, None])

    p.polygon(verts, corner_radii=radii)


def _draw_side_hook(p, m, cap, stem_x, r_limit, *, hook_top_y, desc_target, weight_scale=0.88):
    """Draw a smooth 2-off-curve quadratic right hook (Ҕ/ҕ, Ҧ/ҧ, Ԡ/ԡ, Ԣ/ԣ)."""
    sw = p.v
    rnd = m.roundness
    bar_h = min(_bar_thickness(m, cap, "arch") * weight_scale, hook_top_y * 0.44)
    wall = min(sw * weight_scale, max(24.0, (r_limit - (stem_x + sw)) * 0.44))

    y_out_top = hook_top_y + bar_h * 0.50
    y_in_top = y_out_top - bar_h
    x_attach = stem_x + sw * 0.50
    x_stem_r = stem_x + sw
    x_out_r = max(x_stem_r + wall + 20.0, r_limit)
    x_in_r = max(x_stem_r + 14.0, x_out_r - wall)

    max_depth = min(desc_target, 420.0)
    y_out_bot = -max(bar_h * 1.15, min(max_depth, 340.0 if cap else 270.0))
    y_in_bot = min(-bar_h * 0.25, y_out_bot + bar_h)

    y_out_mid = (y_out_top + y_out_bot) * 0.50
    y_in_mid = (y_in_top + y_in_bot) * 0.50
    sh_out_x = min(x_out_r - 12.0, x_stem_r + (x_out_r - x_stem_r) * 0.36)
    sh_in_x = min(x_in_r - 6.0, x_stem_r + (x_in_r - x_stem_r) * 0.34)
    bot_cx = max(x_stem_r + 8.0, (x_stem_r + x_out_r) * 0.48)
    bot_in_cx = min(x_in_r - 6.0, max(x_stem_r + 6.0, bot_cx))

    # Tip opens leftwards above y_out_bot, terminating gracefully near x_stem_r.
    tip_x = max(stem_x + sw * 0.15, bot_cx - wall * 0.65)
    tip_in_y = min(y_in_mid - 4.0, y_in_bot + max(8.0, bar_h * 0.22))
    tip_out_y = max(y_out_bot + 10.0, tip_in_y - bar_h * 0.92)
    tip_in_x = min(x_in_r - 6.0, tip_x + wall * 0.38)

    kx = m.kx if cap else m.o_kx
    ky = m.ky if cap else m.o_ky
    ikx = m.ikx if cap else m.o_ikx
    iky = m.iky if cap else m.o_iky

    contour = [
        (x_attach, y_out_top, True),
        (sh_out_x, y_out_top, True),
        (sh_out_x + (x_out_r - sh_out_x) * kx, y_out_top, False),
        (x_out_r, y_out_mid + (y_out_top - y_out_mid) * ky, False),
        (x_out_r, y_out_mid, True),
        (x_out_r, y_out_mid - (y_out_mid - y_out_bot) * ky, False),
        (bot_cx + (x_out_r - bot_cx) * kx, y_out_bot, False),
        (bot_cx, y_out_bot, True),
        (bot_cx - (bot_cx - tip_x) * 0.45, y_out_bot, False),
        (tip_x, tip_out_y, True),
    ]
    contour.extend(_pill_cap((tip_x, tip_out_y), (tip_in_x, tip_in_y), rnd))
    contour.extend([
        (tip_in_x, tip_in_y, True),
        (bot_in_cx - (bot_in_cx - tip_in_x) * 0.40, y_in_bot, False),
        (bot_in_cx, y_in_bot, True),
        (bot_in_cx + (x_in_r - bot_in_cx) * ikx, y_in_bot, False),
        (x_in_r, y_in_mid - (y_in_mid - y_in_bot) * iky, False),
        (x_in_r, y_in_mid, True),
        (x_in_r, y_in_mid + (y_in_top - y_in_mid) * iky, False),
        (sh_in_x + (x_in_r - sh_in_x) * ikx, y_in_top, False),
        (sh_in_x, y_in_top, True),
        (x_attach, y_in_top, True),
    ])
    if _signed_area([(x, y) for x, y, _ in contour]) > 0:
        # Side hook is constructed clockwise; keep deterministic order across all masters.
        pass
    p.outline.contours.append(contour)


def _draw_bottom_hook(p, m, cap, stem_l, stem_r, *, direction=-1, desc_target=305.0, min_x=None):
    """Draw a smooth 2-off-curve J-style bottom descender hook (Ԓ/ԓ, Ԩ/ԩ)."""
    sw = stem_r - stem_l
    rnd = m.roundness
    bar_h = min(_bar_thickness(m, cap, "arch"), sw * 1.05)
    hook_w = min(sw * 0.85, max(8.0, bar_h * 0.90))
    raw_reach = max(sw * 1.05, 215.0 if cap else 170.0)
    if min_x is not None and direction < 0:
        # Leave room for the semicircular pill cap so tip never crosses min_x.
        raw_reach = min(raw_reach, max(sw * 0.55, stem_l - min_x - hook_w * 0.55))

    x_out_s = stem_r if direction < 0 else stem_l
    x_in_s = stem_l if direction < 0 else stem_r
    y_top = bar_h * 0.45

    y_out_bot = -min(desc_target, 420.0)
    y_in_bot = min(-max(4.0, bar_h * 0.25), y_out_bot + hook_w)
    y_out_mid = y_out_bot * 0.42
    y_in_mid = y_in_bot * 0.42
    bot_cx = x_in_s + direction * raw_reach * 0.46
    tip_x = x_in_s + direction * raw_reach
    turn_h = max(32.0 if cap else 26.0, hook_w * 0.72)
    tip_out_y = min(y_in_bot - 10.0, y_out_bot + turn_h)
    tip_in_x = tip_x - direction * hook_w * 0.30
    tip_in_y = tip_out_y + hook_w * 0.88

    kx = m.kx if cap else m.o_kx
    ky = m.ky if cap else m.o_ky
    ikx = m.ikx if cap else m.o_ikx
    iky = m.iky if cap else m.o_iky
    left_pt = (tip_x, tip_out_y)
    right_pt = (tip_in_x, tip_in_y)

    contour = [
        (x_out_s, y_top, True),
        (x_out_s, y_out_mid, True),
        (x_out_s, y_out_mid - (y_out_mid - y_out_bot) * ky, False),
        (bot_cx - direction * abs(x_out_s - bot_cx) * kx, y_out_bot, False),
        (bot_cx, y_out_bot, True),
        (bot_cx + direction * abs(tip_x - bot_cx) * 0.50, y_out_bot, False),
        (left_pt[0], left_pt[1], True),
    ]
    if direction < 0:
        contour.extend(_pill_cap(left_pt, right_pt, rnd))
    else:
        contour.extend(_pill_cap(right_pt, left_pt, rnd)[::-1])
    contour.extend([
        (right_pt[0], right_pt[1], True),
        (bot_cx + direction * abs(tip_in_x - bot_cx) * 0.42, y_in_bot, False),
        (bot_cx, y_in_bot, True),
        (bot_cx - direction * abs(x_in_s - bot_cx) * ikx, y_in_bot, False),
        (x_in_s, y_in_mid - (y_in_mid - y_in_bot) * iky, False),
        (x_in_s, y_in_mid, True),
        (x_in_s, y_top, True),
    ])
    if direction > 0:
        contour.reverse()
    p.outline.contours.append(contour)


def _draw_tje(p, m, cap):
    """Draw Komi Tje (Ԏ / ԏ): T-style top bar + U-hook curving up-right inside bounds."""
    l, r, top = p.left, p.right, p.top
    sw = p.v
    rnd = m.roundness
    bar_h = _bar_thickness(m, cap, "arch" if cap else "t_bar")
    bar_r = bar_h * 0.50 * rnd

    p.bar(l, top - bar_h * 0.5, r, thickness=bar_h, radius=bar_r, overhang_left=True, overhang_right=True)

    # U-hook bounded cleanly inside [l .. r] across all widths/weights.
    hook_l = l + p.width * 0.30
    hook_r = r - p.width * 0.06
    min_gap = max(24.0, (hook_r - hook_l) * 0.16)
    leg_w = min(sw * 0.88, (hook_r - hook_l - min_gap) * 0.46)
    y_bot = -m.overshoot(cap) * 0.85
    y_tip = top * 0.54
    p.curved_arch(
        hook_l,
        top * 0.48,
        hook_r,
        0.0,
        leg_w=leg_w,
        top_bar=min(bar_h * 0.88, top * 0.24),
        arch_ov=-y_bot,
        bottom_y=y_tip + m.rond_ov(cap),
        inktrap=False,
        invert=True,
    )
    # Connect left U-leg cleanly straight up into the top bar.
    p.stem(
        hook_l,
        bottom=top * 0.34,
        top=top - bar_h * 0.45,
        weight=leg_w / sw,
        corner_radii=(None, None, None, None),
    )


def _draw_te_descender(p, m, cap, t_outline):
    """Attach a crisp unified stepped right-hand descender to T/т for Ҭ/ҭ."""
    p.outline = t_outline.copy()
    l, _, r, _ = p.outline.bounds()
    sw = p.v
    rnd = m.roundness
    shelf_h = min(_bar_thickness(m, cap, "shelf"), p.top * 0.18)
    desc_w = min(sw * 0.78, (r - l) * 0.28)
    desc_depth = 265.0 if cap else 205.0
    stem_mid = (l + r) * 0.5
    x0 = stem_mid - sw * 0.05
    step_r = x0 + sw * 1.25
    desc_l = step_r - desc_w
    desc_r = desc_w * 0.50 * rnd
    elbow_r = min(sw * 0.42, shelf_h * 0.48) * rnd

    p.polygon(
        [
            (x0, shelf_h * 0.65),
            (step_r, shelf_h * 0.65),
            (step_r, -desc_depth),
            (desc_l, -desc_depth),
            (desc_l, -shelf_h * 0.25),
            (x0, -shelf_h * 0.25),
        ],
        corner_radii=[None, elbow_r, desc_r, desc_r, None, None],
    )
    if step_r + m.ref_lsb(cap) * 0.35 > p.outline.advance:
        p.outline.advance = step_r + m.ref_lsb(cap) * 0.35


def _draw_gamma_cedilla(d, cap, base_char, target_char):
    """Attach a clean 2-off-curve cedilla-style hook under Ғ/ғ stem for Ӻ/ӻ."""
    m = d.metrics
    base = d.get(base_char).copy()
    bl, _, _, _ = base.bounds()
    sw = m.stem(cap)
    rnd = m.roundness
    left_ext = max(sw * 0.62, 90.0 if cap else 75.0)
    stem_cx = bl + left_ext + sw * 0.50

    y_top = 12.0
    neck_h = 80.0 if cap else 62.0
    y_neck = -neck_h
    y_bot = -(365.0 if cap else 295.0)
    loop_span = abs(y_bot) - neck_h
    ced_bar = min(sw * 0.56, _bar_thickness(m, cap, "arch") * 0.68, loop_span * 0.28)
    ced_wall = min(sw * 0.60, 175.0)
    loop_r = max(ced_wall * 1.35, 175.0 if cap else 140.0)
    y_in_top = y_neck - ced_bar
    y_in_bot = y_bot + ced_bar

    x_neck_l = stem_cx - ced_wall * 0.42
    x_neck_r = stem_cx + ced_wall * 0.42
    x_out_r = stem_cx + loop_r
    x_in_r = max(x_neck_r + 16.0, x_out_r - ced_wall)
    y_mid = (y_neck + y_bot) * 0.50
    y_in_mid = (y_in_top + y_in_bot) * 0.50
    tip_x = stem_cx - loop_r * 0.50
    tip_out_y = y_bot + ced_bar * 0.35
    tip_in_x = min(x_neck_l - 8.0, tip_x + ced_wall * 0.26)
    tip_in_y = min(y_in_top - 12.0, tip_out_y + ced_bar * 0.90)

    kx, ky = m.kx, m.ky
    contour = [
        (x_neck_l, y_top, True),
        (x_neck_r, y_top, True),
        (x_neck_r, y_neck, True),
        (x_neck_r + (x_out_r - x_neck_r) * 0.45, y_neck, False),
        (x_out_r, y_mid + (y_neck - y_mid) * ky, False),
        (x_out_r, y_mid, True),
        (x_out_r, y_mid - (y_mid - y_bot) * ky, False),
        (stem_cx + (x_out_r - stem_cx) * kx, y_bot, False),
        (stem_cx, y_bot, True),
        (stem_cx - (stem_cx - tip_x) * 0.45, y_bot, False),
        (tip_x, tip_out_y, True),
    ]
    contour.extend(_pill_cap((tip_x, tip_out_y), (tip_in_x, tip_in_y), rnd))
    contour.extend([
        (tip_in_x, tip_in_y, True),
        (stem_cx - (stem_cx - tip_in_x) * 0.35, y_in_bot, False),
        (stem_cx, y_in_bot, True),
        (stem_cx + (x_in_r - stem_cx) * kx, y_in_bot, False),
        (x_in_r, y_in_mid - (y_in_mid - y_in_bot) * ky, False),
        (x_in_r, y_in_mid, True),
        (x_in_r, y_in_mid + (y_in_top - y_in_mid) * ky, False),
        (x_neck_l + (x_in_r - x_neck_l) * 0.42, y_in_top, False),
        (x_neck_l, y_in_top, True),
    ])
    if _signed_area([(x, y) for x, y, _ in contour]) > 0:
        # Constructed CW around neck and loop.
        pass

    p = d.letter(cap, width=1.0)
    p.outline = base
    p.outline.contours.append(contour)
    cl, _, _, _ = p.outline.bounds()
    shift = max(0.0, d.sb(48.0, cap) - cl)
    p.outline = p.outline.transform(dx=shift)
    p.outline.advance += shift
    d.put(target_char, p)


def _draw_pe_three_leg(p, m):
    """Draw unified 3-stem top-bar ᲅ (tall/three-legged Te)."""
    l, r, top = p.left, p.right, p.top
    rnd = m.roundness
    bar_h = _bar_thickness(m, False, "arch")
    min_gap = max(22.0, p.width * 0.075)
    sw = min(p.v * 0.95, (p.width - 2.0 * min_gap) / 3.0)
    bot = -m.rond_ov_low
    stem_r = sw * 0.50 * rnd
    spine_r = sw * 0.55 * rnd
    mid_x = (l + r) * 0.5
    mid_l = mid_x - sw * 0.5
    mid_r = mid_x + sw * 0.5
    verts = [
        (l, bot),
        (l, top),
        (r, top),
        (r, bot),
        (r - sw, bot),
        (r - sw, top - bar_h),
        (mid_r, top - bar_h),
        (mid_r, bot),
        (mid_l, bot),
        (mid_l, top - bar_h),
        (l + sw, top - bar_h),
        (l + sw, bot),
    ]
    radii = [stem_r, spine_r, spine_r, stem_r, stem_r, None, None, stem_r, stem_r, None, None, stem_r]
    p.polygon(verts, corner_radii=radii)


def draw_arch_letters(d):
    """Draw all modern and extended arch/square/descender Cyrillic letters."""
    m = d.metrics

    for cap in (True, False):
        def put(pair, drawing):
            d.put(pair[0 if cap else 1], drawing)

        # --- Г / г ---
        gamma_adv = _gamma_adv_width(d, cap)
        ref_gamma = "F" if cap else "f"
        gamma_ratio = gamma_adv / max(1.0, m.ref_adv(ref_gamma, cap))
        p = d.adv_letter(cap, adv_ratio=gamma_ratio, ref=ref_gamma, left=160 if cap else 126, right=108 if cap else 86)
        _draw_gamma_body(p, m, cap, serif=False)
        put("Гг", p)
        g_l, _, g_r, _ = p.outline.bounds()
        d.set_anchor_x("Г" if cap else "г", g_l + (g_r - g_l) * 0.44)

        # --- Ґ / ґ ---
        gup_adv = _gamma_adv_width(d, cap, extra_stem=0.38)
        gup_ratio = gup_adv / max(1.0, m.ref_adv(ref_gamma, cap))
        p = d.adv_letter(cap, adv_ratio=gup_ratio, ref=ref_gamma, left=160 if cap else 126, right=82 if cap else 66)
        _draw_gamma_body(p, m, cap, serif=True)
        put("Ґґ", p)
        gu_l, _, gu_r, _ = p.outline.bounds()
        d.set_anchor_x("Ґ" if cap else "ґ", gu_l + (gu_r - gu_l) * 0.40)

        # --- П / п ---
        p = d.adv_letter(cap, adv_ratio=1.0, ref="H" if cap else "n")
        _draw_pe_body(p, m, cap)
        put("Пп", p)

        # --- н (lowercase only; uppercase Н is homoglyph H) ---
        if not cap:
            p = d.adv_letter(False, adv_ratio=1.0, ref="n")
            _draw_en_body(p, m, False)
            d.put("н", p)

        # --- т (lowercase only; uppercase Т is homoglyph T) ---
        if not cap:
            p = d.adv_letter(False, adv_ratio=0.88, ref="T", left=44, right=44)
            bar_h = _bar_thickness(m, False, "t_bar")
            bar_r = bar_h * 0.50 * m.roundness
            stem_r = p.v * 0.50 * m.roundness
            p.bar(
                p.left,
                p.top - bar_h * 0.5,
                p.right,
                thickness=bar_h,
                radius=bar_r,
                overhang_left=True,
                overhang_right=True,
            )
            p.stem(
                p.mid - p.v * 0.5,
                bottom=-m.rond_ov_low,
                top=p.top - bar_h * 0.45,
                corner_radii=(stem_r, None, None, stem_r),
            )
            d.put("т", p)

        # --- Л / л ---
        p = d.adv_letter(
            cap,
            adv_ratio=0.96 if cap else 1.00,
            ref="H" if cap else "n",
            left=52 if cap else 46,
            right=160 if cap else 126,
        )
        roof_center = _draw_lintel(p, m)
        put("Лл", p)
        d.set_anchor_x("Л" if cap else "л", roof_center)

        # --- Д / д ---
        p = d.adv_letter(
            cap,
            adv_ratio=1.02 if cap else 1.06,
            ref="H" if cap else "n",
            left=48 if cap else 44,
            right=48 if cap else 44,
        )
        shelf_h = min(_bar_thickness(m, cap, "shelf") * 1.06, p.top * 0.197)
        _draw_de(
            p,
            m,
            shelf_h=shelf_h,
            desc_depth=355.0 if cap else 259.0,
        )
        put("Дд", p)
        d.set_anchor_x("Д" if cap else "д", p.outline.advance * 0.50)

        # --- Ш / ш ---
        p = d.adv_letter(cap, adv_ratio=1.03 if cap else 0.89, ref="M" if cap else "m")
        _draw_u_comb(p, m, cap, stems=3)
        put("Шш", p)

        # --- Щ / щ ---
        p = d.adv_letter(
            cap,
            adv_ratio=1.045 if cap else 0.905,
            ref="M" if cap else "m",
            left=160 if cap else 126,
            right=128 if cap else 95,
        )
        _draw_u_comb(
            p, m, cap, stems=3, right_desc=True,
            desc_depth=265.0 if cap else 205.0, desc_reach=0.24 if cap else 0.0,
        )
        put("Щщ", p)

        # --- Ц / ц ---
        p = d.adv_letter(
            cap,
            adv_ratio=1.01 if cap else 1.02,
            ref="H" if cap else "n",
            left=160 if cap else 126,
            right=128 if cap else 95,
        )
        _draw_u_comb(
            p, m, cap, stems=2, right_desc=True,
            desc_depth=265.0 if cap else 205.0, desc_reach=0.24 if cap else 0.0,
        )
        put("Цц", p)

        # --- Џ / џ ---
        p = d.adv_letter(cap, adv_ratio=1.00, ref="H" if cap else "n")
        _draw_u_comb(p, m, cap, stems=2, center_desc=True, desc_depth=265.0 if cap else 205.0)
        put("Џџ", p)

        # ================= Extended Arch Letters =================

        # --- Ғ / ғ (Ghe with stroke) ---
        sw = m.stem(cap)
        left_ext = max(sw * 0.62, 90.0 if cap else 75.0)
        bar_h_mid = _bar_thickness(m, cap, "F_mid" if cap else "mid")
        p = d.adv_letter(
            cap,
            adv_ratio=(gamma_adv + left_ext * 0.65) / max(1.0, m.ref_adv(ref_gamma, cap)),
            ref=ref_gamma,
            left=105 if cap else 85,
            right=100 if cap else 80,
        )
        stem_l = p.left + left_ext
        _draw_gamma_body_at(p, m, cap, stem_l=stem_l, bar_r_x=p.right)
        right_arm = min(p.right - sw * 0.12, stem_l + sw + max(sw * 1.25, p.width * 0.34))
        p.bar(
            p.left,
            p.top * 0.52,
            right_arm,
            thickness=bar_h_mid,
            radius=bar_h_mid * 0.50 * m.roundness,
            overhang_left=True,
            overhang_right=True,
        )
        put("Ғғ", p)

        # --- Ӻ / ӻ (Ghe with stroke and hook) ---
        _draw_gamma_cedilla(d, cap, "Ғ" if cap else "ғ", "Ӻ" if cap else "ӻ")

        # --- Ӷ / ӷ (Ghe with descender under vertical stem) ---
        p = d.adv_letter(cap, adv_ratio=gamma_ratio, ref=ref_gamma, left=160 if cap else 126, right=108 if cap else 86)
        _draw_gamma_body(p, m, cap, serif=False, desc_left_foot=True)
        put("Ӷӷ", p)

        # --- Ҕ / ҕ (Ghe with middle hook) ---
        p = d.adv_letter(
            cap,
            adv_ratio=(gamma_adv + sw * 0.65) / max(1.0, m.ref_adv(ref_gamma, cap)),
            ref=ref_gamma,
            left=160 if cap else 126,
            right=75 if cap else 65,
        )
        _draw_gamma_body(p, m, cap, serif=False, arm_scale=0.84)
        _draw_side_hook(
            p,
            m,
            cap,
            p.left,
            p.right,
            hook_top_y=p.top * 0.60,
            desc_target=295.0 if cap else 235.0,
            weight_scale=0.88,
        )
        put("Ҕҕ", p)

        # --- Ҥ / ҥ (En-Ghe ligature) ---
        p = d.adv_letter(cap, adv_ratio=1.24 if cap else 1.26, ref="H" if cap else "n", left=160 if cap else 126, right=86 if cap else 70)
        joint_x = p.left + (p.width - sw) * 0.55
        p.stem(p.left)
        p.bar(p.left + sw * 0.45, p.top * 0.505, joint_x + sw * 0.55, thickness=_bar_thickness(m, cap, "mid"), radius=0.0)
        _draw_gamma_body_at(p, m, cap, stem_l=joint_x, bar_r_x=p.right)
        put("Ҥҥ", p)

        # --- Ҧ / ҧ (Pe with middle hook) ---
        p = d.adv_letter(cap, adv_ratio=1.28 if cap else 1.30, ref="H" if cap else "n", left=160 if cap else 126, right=75 if cap else 65)
        pe_r = p.left + p.width * 0.64
        p_pe = d.letter(cap, width=pe_r - p.left, left=p.left, right=0.0)
        _draw_pe_body(p_pe, m, cap)
        p.outline.contours.extend(p_pe.outline.contours)
        _draw_side_hook(
            p,
            m,
            cap,
            pe_r - sw,
            p.right,
            hook_top_y=p.top * 0.58,
            desc_target=295.0 if cap else 235.0,
            weight_scale=0.88,
        )
        put("Ҧҧ", p)

        # --- Ԥ / ԥ (Pe with descender) ---
        p = d.adv_letter(cap, adv_ratio=1.01 if cap else 1.02, ref="H" if cap else "n", left=160 if cap else 126, right=128 if cap else 95)
        _draw_pe_body(p, m, cap, right_desc=True, desc_depth=265.0 if cap else 205.0)
        put("Ԥԥ", p)

        # --- Ԯ / ԯ (El with descender) ---
        p = d.adv_letter(cap, adv_ratio=0.98 if cap else 1.02, ref="H" if cap else "n", left=52 if cap else 46, right=128 if cap else 95)
        ext = sw * 0.16
        r_stem = p.right - ext
        shelf_h = min(_bar_thickness(m, cap, "shelf"), p.top * 0.18)
        desc_w = min(sw * 0.78, (r_stem - p.left) * 0.28)
        desc_depth = 265.0 if cap else 205.0
        _draw_el_contour(p, m, cap, l_body=p.left, r_body=r_stem, stem_bot=0.0, round_right_bot=False)
        elbow_r = min(ext * 0.85, shelf_h * 0.48) * m.roundness
        desc_r = desc_w * 0.50 * m.roundness
        p.polygon(
            [
                (r_stem - sw * 0.5, shelf_h),
                (p.right, shelf_h),
                (p.right, -desc_depth),
                (p.right - desc_w, -desc_depth),
                (p.right - desc_w, 0.0),
                (r_stem - sw * 0.5, 0.0),
            ],
            corner_radii=[None, elbow_r, desc_r, desc_r, None, None],
        )
        put("Ԯԯ", p)

        # --- Ӆ / ӆ (El with tail) ---
        p = d.adv_letter(cap, adv_ratio=1.02 if cap else 1.06, ref="H" if cap else "n", left=52 if cap else 46, right=126 if cap else 102)
        flare = sw * 0.22
        _draw_el_contour(p, m, cap, l_body=p.left, r_body=p.right - flare)
        _draw_tail_foot(p, m, cap, p.right - flare - sw, p.right - flare, flare=flare, depth=265.0 if cap else 205.0)
        put("Ӆӆ", p)

        # --- Ӊ / ӊ (En with tail) ---
        p = d.adv_letter(cap, adv_ratio=1.05 if cap else 1.06, ref="H" if cap else "n", left=160 if cap else 126, right=126 if cap else 102)
        _draw_en_body(p, m, cap, tail_mode="tail", desc_depth=265.0 if cap else 205.0)
        put("Ӊӊ", p)

        # --- Ԩ / ԩ (En with left hook) ---
        p = d.adv_letter(cap, adv_ratio=1.00, ref="H" if cap else "n", left=160 if cap else 126, right=160 if cap else 126)
        _draw_en_body(p, m, cap)
        _draw_bottom_hook(p, m, cap, p.left, p.left + sw, direction=-1, desc_target=305.0 if cap else 245.0)
        bl_x, _, _, _ = p.outline.bounds()
        shift_x = max(0.0, d.sb(62.0, cap) - bl_x)
        p.outline = p.outline.transform(dx=shift_x)
        p.outline.advance += shift_x
        put("Ԩԩ", p)

        # --- Ң / ң (En with descender) ---
        p = d.adv_letter(cap, adv_ratio=1.01 if cap else 1.02, ref="H" if cap else "n", left=160 if cap else 126, right=128 if cap else 95)
        _draw_en_body(p, m, cap, tail_mode="descender", desc_depth=265.0 if cap else 205.0)
        put("Ңң", p)

        # --- Ҵ / ҵ (Te-Tse ligature) ---
        p = d.adv_letter(cap, adv_ratio=1.18 if cap else 1.18, ref="H" if cap else "n", left=78 if cap else 64, right=128 if cap else 95)
        _draw_u_comb(p, m, cap, stems=2, right_desc=True, desc_depth=265.0 if cap else 205.0, left_t_bar=True)
        put("Ҵҵ", p)

        # --- Ҭ / ҭ (Te with descender) ---
        t_base = d.native("T", upright=True) if cap else d.get("т")
        p = d.letter(cap, width=1.0)
        _draw_te_descender(p, m, cap, t_base)
        put("Ҭҭ", p)

        # --- Ԡ / ԡ (El with middle hook) ---
        p = d.adv_letter(cap, adv_ratio=1.26 if cap else 1.28, ref="H" if cap else "n", left=52 if cap else 46, right=75 if cap else 65)
        el_r = p.left + p.width * 0.65
        _draw_el_contour(p, m, cap, l_body=p.left, r_body=el_r)
        _draw_side_hook(
            p,
            m,
            cap,
            el_r - sw,
            p.right,
            hook_top_y=p.top * 0.58,
            desc_target=295.0 if cap else 235.0,
            weight_scale=0.88,
        )
        put("Ԡԡ", p)

        # --- Ԣ / ԣ (En with middle hook) ---
        p = d.adv_letter(cap, adv_ratio=1.28 if cap else 1.30, ref="H" if cap else "n", left=160 if cap else 126, right=75 if cap else 65)
        en_r = p.left + p.width * 0.65
        p_en = d.letter(cap, width=en_r - p.left, left=p.left, right=0.0)
        _draw_en_body(p_en, m, cap)
        p.outline.contours.extend(p_en.outline.contours)
        _draw_side_hook(
            p,
            m,
            cap,
            en_r - sw,
            p.right,
            hook_top_y=p.top * 0.58,
            desc_target=295.0 if cap else 235.0,
            weight_scale=0.88,
        )
        put("Ԣԣ", p)

        # --- Ԓ / ԓ (El with hook) ---
        p = d.adv_letter(cap, adv_ratio=0.96 if cap else 1.00, ref="H" if cap else "n", left=52 if cap else 46, right=160 if cap else 126)
        _draw_el_contour(p, m, cap, l_body=p.left, r_body=p.right)
        _draw_bottom_hook(p, m, cap, p.right - sw, p.right, direction=-1, desc_target=295.0 if cap else 235.0, min_x=p.left)
        put("Ԓԓ", p)

        # --- Ԏ / ԏ (Komi Tje) ---
        p = d.adv_letter(cap, adv_ratio=1.06 if cap else 1.08, ref="H" if cap else "n", left=48 if cap else 44, right=65 if cap else 56)
        _draw_tje(p, m, cap)
        put("Ԏԏ", p)

    # --- ᲅ (tall 3-legged lowercase Т) ---
    p = d.adv_letter(False, adv_ratio=0.89, ref="m")
    _draw_pe_three_leg(p, m)
    d.put("ᲅ", p)

    # --- ᴫ (small capital El at x-height) ---
    p = d.adv_letter(False, adv_ratio=0.90, ref="n", left=42, right=112)
    _draw_el_contour(p, m, False, l_body=p.left, r_body=p.right)
    d.put("ᴫ", p)
