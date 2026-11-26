# SPDX-License-Identifier: OFL-1.1
"""TrueType contours and native metric extraction for six-axis KittyTune Flex.

Provides:
1. ``extract_master_metrics(font, location)`` -> ``MasterMetrics`` dataclass
   measuring exact final-space dimensions directly from native Google Sans Flex
   reference glyphs (stems, bar contrast, overshoots, curvature ratios, ROND
   terminals, slant pivot, and per-glyph width ratios).
2. ``native_outline`` and ``shear_slnt`` helpers with Google Sans Flex's true
   ``y = x_h / 2`` slant pivot.
3. Final-coordinate contour builders on ``Pen`` and ``Letter``: exact 2-off-curve
   ``sqrt(2) - 1`` circular pill caps & spine corners, 14-point squircle/stadium
   ovals, non-collapsing ``curved_bowl`` and ``double_bowl`` builders,
   ``curved_arch`` / ``curved_leg`` contours, and curvature-clamped self-
   intersection-free ``stroke`` contours — all preserving strict gvar point and
   flag invariance across every 6-axis master.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from math import hypot, radians, sqrt, tan
from typing import Iterable, Mapping, Sequence

from fontTools.pens.recordingPen import DecomposingRecordingPointPen

# Quadratic 2-off-curve circular 90-degree arc handle factor: tan(22.5 deg) = sqrt(2) - 1.
# Placing handles at p0 + ARC_K * (V - p0) and p3 + ARC_K * (V - p3) puts the
# quadratic spline midpoint directly on the circle and maintains G1 tangency.
ARC_K = sqrt(2.0) - 1.0  # ~0.4142135623730951

REF_GLYPHS = ("H", "O", "B", "M", "T", "Y", "X", "n", "o", "m", "k", "s", "a")

_CHAR_NAME_FALLBACK = {
    " ": "space",
    ".": "period",
    ",": "comma",
    "-": "hyphen",
    "0": "zero",
    "1": "one",
    "2": "two",
    "3": "three",
    "4": "four",
    "5": "five",
    "6": "six",
    "7": "seven",
    "8": "eight",
    "9": "nine",
}

# Memoized MasterMetrics keyed by (id(font), sorted location tuple).
_METRICS_CACHE: dict[tuple, "MasterMetrics"] = {}


@dataclass
class Outline:
    advance: float
    contours: list = field(default_factory=list)

    def bounds(self):
        pts = [(x, y) for contour in self.contours for x, y, _ in contour]
        if not pts:
            return 0.0, 0.0, 0.0, 0.0
        xs, ys = zip(*pts)
        return min(xs), min(ys), max(xs), max(ys)

    def copy(self):
        return Outline(self.advance, [list(c) for c in self.contours])

    def transform(self, sx=1.0, sy=1.0, dx=0.0, dy=0.0, shear=0.0):
        return Outline(
            abs(sx) * self.advance,
            [
                [
                    (sx * x + shear * y + dx, sy * y + dy, on)
                    for x, y, on in (contour if sx * sy > 0 else reversed(contour))
                ]
                for contour in self.contours
            ],
        )

    def add(self, other):
        self.contours.extend(other.contours)


def source_outline(glyphs, name):
    """Extract decomposed TrueType point contours from a fontTools glyphset."""
    pen = DecomposingRecordingPointPen(glyphs)
    glyphs[name].drawPoints(pen)
    outline = Outline(glyphs[name].width)
    contour = []
    for operation, args, _ in pen.value:
        if operation == "beginPath":
            contour = []
        elif operation == "addPoint":
            (x, y), segment = args[:2]
            contour.append((x, y, segment is not None))
        elif operation == "endPath":
            outline.contours.append(contour)
    return outline


def native_outline(glyphset, char_or_name, scale_x=1.0, scale_y=1.0, cmap=None):
    """Extract a native glyph outline by glyph name or Unicode character."""
    name = None
    if char_or_name in glyphset:
        name = char_or_name
    elif len(char_or_name) == 1:
        active_cmap = cmap
        if active_cmap is None and hasattr(glyphset, "font") and glyphset.font is not None:
            active_cmap = glyphset.font.getBestCmap()
        if active_cmap is not None:
            name = active_cmap.get(ord(char_or_name))
        if name is None:
            name = _CHAR_NAME_FALLBACK.get(char_or_name)
    if not name or name not in glyphset:
        raise KeyError(f"Glyph {char_or_name!r} not found in glyphset")
    outline = source_outline(glyphset, name)
    if scale_x != 1.0 or scale_y != 1.0:
        outline = outline.transform(sx=scale_x, sy=scale_y)
    return outline


def shear_slnt(outline, slant, pivot_y):
    """Apply italic slant around Google Sans Flex's x_h/2 pivot height."""
    if not slant:
        return outline.copy()
    return outline.transform(shear=slant, dx=-slant * pivot_y)


@dataclass(frozen=True)
class MasterMetrics:
    # Vertical & overshoot geometry
    cap_h: float
    x_h: float
    desc_y: float
    cap_ov: float
    low_ov: float
    rond_ov_cap: float
    rond_ov_low: float
    h_overhang_cap: float
    h_overhang_low: float
    roundness: float
    slant: float
    slant_pivot: float

    # Native stem and horizontal contrast measurements (at ROND=0, slnt=0)
    H_stem: float
    n_stem: float
    H_bar: float
    E_mid: float
    F_mid: float
    B_top: float
    B_mid: float
    B_bot: float
    P_bar: float
    R_bar: float
    O_side: float
    O_top: float
    o_side: float
    o_top: float
    e_bar: float
    f_bar: float

    # Native advance widths and LSBs
    H_adv: float
    O_adv: float
    B_adv: float
    M_adv: float
    T_adv: float
    Y_adv: float
    X_adv: float
    n_adv: float
    o_adv: float
    m_adv: float
    k_adv: float
    s_adv: float
    a_adv: float
    H_lsb: float
    O_lsb: float
    n_lsb: float
    o_lsb: float

    # Per-glyph advance width ratio vs (wdth=100, GRAD=0) at same wght/opsz
    wdth_ratios: dict

    # Squircle / stadium curve parameters extracted from native O (and o below)
    kx: float
    ky: float
    ikx: float
    iky: float

    # Extended native geometry details
    flat_out: float = 0.0
    flat_in: float = 0.0
    o_kx: float = 0.431
    o_ky: float = 0.429
    o_ikx: float = 0.424
    o_iky: float = 0.440
    o_flat_out: float = 0.0
    o_flat_in: float = 0.0
    n_left_stem: float = 162.0
    B_lsb: float = 160.0
    T_lsb: float = 52.0
    M_lsb: float = 160.0
    B_rwall_top: float = 168.0
    B_rwall_bot: float = 172.0
    E_adv: float = 1102.0
    F_adv: float = 1062.0
    P_adv: float = 1152.0
    R_adv: float = 1172.0
    e_adv: float = 1126.0
    f_adv: float = 758.0
    rond_ov_cap_max: float = 9.0
    rond_ov_low_max: float = 9.0
    h_overhang_cap_max: float = 9.0
    h_overhang_low_max: float = 8.0
    opsz: float = 18.0
    wdth: float = 100.0
    wght: float = 400.0
    GRAD: float = 0.0
    ROND: float = 0.0
    slnt: float = 0.0

    def ref_adv(self, ref="H", cap=True):
        val = getattr(self, f"{ref}_adv", None)
        if val is not None:
            return val
        return self.H_adv if cap else self.n_adv

    def stem(self, cap=True):
        return self.H_stem if cap else self.n_stem

    def bar(self, cap=True):
        return self.E_mid if cap else self.o_top

    def ref_lsb(self, cap=True):
        return self.H_lsb if cap else self.n_lsb

    def overshoot(self, cap=True):
        return self.cap_ov if cap else self.low_ov

    def rond_ov(self, cap=True):
        return self.rond_ov_cap if cap else self.rond_ov_low

    def h_overhang(self, cap=True):
        return self.h_overhang_cap if cap else self.h_overhang_low

    def spine_r(self, stem_w=None):
        w = self.H_stem if stem_w is None else stem_w
        return w * 0.55 * self.roundness

    def squircle_params(self, cap=True):
        if cap:
            return {
                "kx": self.kx,
                "ky": self.ky,
                "flat_out": self.flat_out,
                "ikx": self.ikx,
                "iky": self.iky,
                "flat_in": self.flat_in,
            }
        return {
            "kx": self.o_kx,
            "ky": self.o_ky,
            "flat_out": self.o_flat_out,
            "ikx": self.o_ikx,
            "iky": self.o_iky,
            "flat_in": self.o_flat_in,
        }


def _extract_squircle_params(o_contours):
    out, inn = o_contours[0], o_contours[1]
    bx, by = out[0][:2]
    lx, ly_b = out[3][:2]
    _, ly_t = out[4][:2]
    _, ty = out[7][:2]
    flat_out = max(0.0, (ly_t - ly_b) / max(1e-6, ty - by))
    arc_h_out = max(1e-6, ly_b - by)
    kx = max(0.25, min(0.58, (bx - out[1][0]) / max(1e-6, bx - lx)))
    ky = max(0.25, min(0.58, (ly_b - out[2][1]) / arc_h_out))

    ibx, iby = inn[0][:2]
    irx, iry_b = inn[3][:2]
    _, iry_t = inn[4][:2]
    _, ity = inn[7][:2]
    flat_in = max(0.0, (iry_t - iry_b) / max(1e-6, ity - iby))
    arc_h_in = max(1e-6, iry_b - iby)
    ikx = max(0.25, min(0.58, (inn[1][0] - ibx) / max(1e-6, irx - ibx)))
    iky = max(0.25, min(0.58, (iry_b - inn[2][1]) / arc_h_in))
    return kx, ky, flat_out, ikx, iky, flat_in


def extract_master_metrics(font, location: Mapping[str, float]) -> MasterMetrics:
    """Measure native Google Sans Flex anatomy in final space at ``location``."""
    loc = dict(location)
    cache_key = (id(font), tuple(sorted((k, float(v)) for k, v in loc.items())))
    cached = _METRICS_CACHE.get(cache_key)
    if cached is not None:
        return cached

    rond_val = float(loc.get("ROND", 0.0))
    slnt_val = float(loc.get("slnt", 0.0))
    roundness = rond_val / 100.0
    slant = -tan(radians(slnt_val))

    gs_flat = font.getGlyphSet(location=loc | {"slnt": 0, "ROND": 0})
    gs_rond = font.getGlyphSet(location=loc | {"slnt": 0, "ROND": 100})
    gs_norm = font.getGlyphSet(location=loc | {"slnt": 0, "ROND": 0, "wdth": 100, "GRAD": 0})

    cH = source_outline(gs_flat, "H").contours
    cE = source_outline(gs_flat, "E").contours
    cF = source_outline(gs_flat, "F").contours
    cB = source_outline(gs_flat, "B").contours
    cP = source_outline(gs_flat, "P").contours
    cR = source_outline(gs_flat, "R").contours
    cO = source_outline(gs_flat, "O").contours
    cn = source_outline(gs_flat, "n").contours
    co = source_outline(gs_flat, "o").contours
    ce = source_outline(gs_flat, "e").contours
    cf = source_outline(gs_flat, "f").contours
    cp = source_outline(gs_flat, "p").contours
    cT = source_outline(gs_flat, "T").contours
    cM = source_outline(gs_flat, "M").contours

    cap_h = cH[1][4][1]
    H_stem = cH[1][8][0] - cH[1][4][0]
    H_bar = cH[2][1][1] - cH[2][0][1]
    H_lsb = cH[1][0][0]

    E_mid = cE[1][2][1] - cE[1][0][1]
    F_mid = cF[1][2][1] - cF[1][0][1]
    B_top = cB[0][4][1] - cB[1][19][1]
    B_mid = cB[1][10][1] - cB[1][9][1]
    B_bot = cB[1][0][1] - cB[0][0][1]
    B_lsb = cB[0][0][0]
    B_rwall_top = cB[0][11][0] - cB[1][14][0]
    B_rwall_bot = cB[0][19][0] - cB[1][4][0]
    P_bar = cP[0][17][1] - cP[0][16][1]
    R_bar = cR[0][17][1] - cR[0][16][1]

    x_h = cn[0][1][1]
    n_stem = cn[0][18][0] - cn[0][22][0]
    n_left_stem = cn[0][5][0] - cn[0][1][0]
    n_lsb = cn[0][0][0]
    desc_y = min(y for _, y, _ in cp[0])

    O_side = ((cO[1][10][0] - cO[0][3][0]) + (cO[0][10][0] - cO[1][3][0])) * 0.5
    O_top = ((cO[0][7][1] - cO[1][7][1]) + (cO[1][0][1] - cO[0][0][1])) * 0.5
    cap_ov = ((cO[0][7][1] - cap_h) + (-cO[0][0][1])) * 0.5
    O_lsb = cO[0][3][0]

    o_side = ((co[1][10][0] - co[0][3][0]) + (co[0][10][0] - co[1][3][0])) * 0.5
    o_top = ((co[0][7][1] - co[1][7][1]) + (co[1][0][1] - co[0][0][1])) * 0.5
    low_ov = ((co[0][7][1] - x_h) + (-co[0][0][1])) * 0.5
    o_lsb = co[0][3][0]

    e_bar = ce[0][16][1] - ce[0][15][1]
    f_bar = cf[0][4][1] - cf[0][0][1]
    T_lsb = min(x for x, _, _ in cT[1])
    M_lsb = min(x for x, _, _ in cM[0])

    cHr = source_outline(gs_rond, "H").contours
    cEr = source_outline(gs_rond, "E").contours
    cnr = source_outline(gs_rond, "n").contours
    cfr = source_outline(gs_rond, "f").contours

    rond_ov_cap_max = max(0.0, max(y for _, y, _ in cHr[1]) - cap_h)
    rond_ov_low_max = max(0.0, -min(y for _, y, _ in cnr[0]))
    h_ov_cap_max = max(0.0, max(x for x, _, _ in cEr[0]) - max(x for x, _, _ in cE[0]))
    h_ov_low_max = max(0.0, max(x for x, _, _ in cfr[0]) - max(x for x, _, _ in cf[0]))

    kx, ky, flat_out, ikx, iky, flat_in = _extract_squircle_params(cO)
    o_kx, o_ky, o_flat_out, o_ikx, o_iky, o_flat_in = _extract_squircle_params(co)

    advs = {g: float(gs_flat[g].width) for g in REF_GLYPHS}
    wdth_ratios = {
        g: advs[g] / max(1.0, float(gs_norm[g].width))
        for g in REF_GLYPHS
    }

    metrics = MasterMetrics(
        cap_h=cap_h,
        x_h=x_h,
        desc_y=desc_y,
        cap_ov=cap_ov,
        low_ov=low_ov,
        rond_ov_cap=rond_ov_cap_max * roundness,
        rond_ov_low=rond_ov_low_max * roundness,
        h_overhang_cap=h_ov_cap_max * roundness,
        h_overhang_low=h_ov_low_max * roundness,
        roundness=roundness,
        slant=slant,
        slant_pivot=x_h * 0.5,
        H_stem=H_stem,
        n_stem=n_stem,
        H_bar=H_bar,
        E_mid=E_mid,
        F_mid=F_mid,
        B_top=B_top,
        B_mid=B_mid,
        B_bot=B_bot,
        P_bar=P_bar,
        R_bar=R_bar,
        O_side=O_side,
        O_top=O_top,
        o_side=o_side,
        o_top=o_top,
        e_bar=e_bar,
        f_bar=f_bar,
        H_adv=advs["H"],
        O_adv=advs["O"],
        B_adv=advs["B"],
        M_adv=advs["M"],
        T_adv=advs["T"],
        Y_adv=advs["Y"],
        X_adv=advs["X"],
        n_adv=advs["n"],
        o_adv=advs["o"],
        m_adv=advs["m"],
        k_adv=advs["k"],
        s_adv=advs["s"],
        a_adv=advs["a"],
        H_lsb=H_lsb,
        O_lsb=O_lsb,
        n_lsb=n_lsb,
        o_lsb=o_lsb,
        wdth_ratios=wdth_ratios,
        kx=kx,
        ky=ky,
        ikx=ikx,
        iky=iky,
        flat_out=flat_out,
        flat_in=flat_in,
        o_kx=o_kx,
        o_ky=o_ky,
        o_ikx=o_ikx,
        o_iky=o_iky,
        o_flat_out=o_flat_out,
        o_flat_in=o_flat_in,
        n_left_stem=n_left_stem,
        B_lsb=B_lsb,
        T_lsb=T_lsb,
        M_lsb=M_lsb,
        B_rwall_top=B_rwall_top,
        B_rwall_bot=B_rwall_bot,
        E_adv=float(gs_flat["E"].width),
        F_adv=float(gs_flat["F"].width),
        P_adv=float(gs_flat["P"].width),
        R_adv=float(gs_flat["R"].width),
        e_adv=float(gs_flat["e"].width),
        f_adv=float(gs_flat["f"].width),
        rond_ov_cap_max=rond_ov_cap_max,
        rond_ov_low_max=rond_ov_low_max,
        h_overhang_cap_max=h_ov_cap_max,
        h_overhang_low_max=h_ov_low_max,
        opsz=float(loc.get("opsz", 18.0)),
        wdth=float(loc.get("wdth", 100.0)),
        wght=float(loc.get("wght", 400.0)),
        GRAD=float(loc.get("GRAD", 0.0)),
        ROND=rond_val,
        slnt=slnt_val,
    )
    if len(_METRICS_CACHE) > 256:
        _METRICS_CACHE.clear()
    _METRICS_CACHE[cache_key] = metrics
    return metrics


def offset(a, b, distance):
    dx, dy = b[0] - a[0], b[1] - a[1]
    fraction = distance / max(hypot(dx, dy), 1e-6)
    return a[0] + dx * fraction, a[1] + dy * fraction


def normal(dx, dy):
    length = hypot(dx, dy) or 1.0
    return -dy / length, dx / length


def _unit(dx, dy):
    length = hypot(dx, dy) or 1.0
    return dx / length, dy / length, length


def _signed_area(vertices):
    return sum(
        x * yy - xx * y
        for (x, y), (xx, yy) in zip(vertices, vertices[1:] + vertices[:1])
    )


def _squircle_loop(x0, y0, x1, y1, kx, ky, flat_frac, cw=True):
    """Build a 14-pt Google Sans Flex 2-off-curve squircle/stadium loop."""
    cx = (x0 + x1) * 0.5
    cy = (y0 + y1) * 0.5
    rx = max(0.5, (x1 - x0) * 0.5)
    ry = max(0.5, (y1 - y0) * 0.5)
    h_flat = ry * max(0.0, min(0.95, flat_frac))
    yb = cy - h_flat
    yt = cy + h_flat
    arc_h = max(1e-3, yb - y0)
    hx = rx * kx
    hy = arc_h * ky
    if cw:
        return [
            (cx, y0, True),
            (cx - hx, y0, False),
            (x0, yb - hy, False),
            (x0, yb, True),
            (x0, yt, True),
            (x0, yt + hy, False),
            (cx - hx, y1, False),
            (cx, y1, True),
            (cx + hx, y1, False),
            (x1, yt + hy, False),
            (x1, yt, True),
            (x1, yb, True),
            (x1, yb - hy, False),
            (cx + hx, y0, False),
        ]
    return [
        (cx, y0, True),
        (cx + hx, y0, False),
        (x1, yb - hy, False),
        (x1, yb, True),
        (x1, yt, True),
        (x1, yt + hy, False),
        (cx + hx, y1, False),
        (cx, y1, True),
        (cx - hx, y1, False),
        (x0, yt + hy, False),
        (x0, yt, True),
        (x0, yb, True),
        (x0, yb - hy, False),
        (cx - hx, y0, False),
    ]


def _adaptive_flat(rx, ry, base_flat=None):
    """Smooth stadium side fraction when aspect ratio becomes condensed."""
    if base_flat is not None:
        return base_flat
    aspect = rx / max(ry, 1e-6)
    if aspect >= 0.80:
        return 0.0
    return min(0.65, max(0.0, (0.80 - aspect) / 0.44 * 0.55))


def _fit_quads(points):
    """Fit monotone quadratic Bezier triples across an odd-length sample list (9 -> 4 spans)."""
    out = [(*points[0], True)]
    for i in range(0, len(points) - 2, 2):
        x0, y0 = points[i]
        xm, ym = points[i + 1]
        x1, y1 = points[i + 2]
        cx = 2.0 * xm - (x0 + x1) * 0.5
        cy = 2.0 * ym - (y0 + y1) * 0.5
        dx, dy = x1 - x0, y1 - y0
        l2 = dx * dx + dy * dy
        if l2 < 1e-6:
            cx, cy = (x0 + x1) * 0.5, (y0 + y1) * 0.5
        else:
            # Clamp control point projection onto [0, 1] along chord to prevent backward kinks.
            t = ((cx - x0) * dx + (cy - y0) * dy) / l2
            if t < 0.0:
                cx += (-t) * dx
                cy += (-t) * dy
            elif t > 1.0:
                cx -= (t - 1.0) * dx
                cy -= (t - 1.0) * dy
        out.append((cx, cy, False))
        out.append((x1, y1, True))
    return out


def _seg_intersect(a, b, c, d):
    """Return intersection point of segments AB and CD if strictly crossing, else None."""
    abx, aby = b[0] - a[0], b[1] - a[1]
    cdx, cdy = d[0] - c[0], d[1] - c[1]
    denom = abx * cdy - aby * cdx
    if abs(denom) < 1e-9:
        return None
    acx, acy = c[0] - a[0], c[1] - a[1]
    t = (acx * cdy - acy * cdx) / denom
    u = (acx * aby - acy * abx) / denom
    if 0.0 <= t <= 1.0 and 0.0 <= u <= 1.0:
        return a[0] + t * abx, a[1] + t * aby
    return None


def _de_kink_polyline(samples, tangents=None):
    """Ensure offset curve progresses cleanly with zero self-crossing loops."""
    pts = list(samples)
    n = len(pts)
    if n < 3:
        return pts
    if tangents is not None:
        for _ in range(2):
            for i in range(1, n - 1):
                tx, ty, _ = _unit(tangents[i][0], tangents[i][1])
                dx = pts[i + 1][0] - pts[i - 1][0]
                dy = pts[i + 1][1] - pts[i - 1][1]
                dot_prev = (pts[i][0] - pts[i - 1][0]) * tx + (pts[i][1] - pts[i - 1][1]) * ty
                dot_next = (pts[i + 1][0] - pts[i][0]) * tx + (pts[i + 1][1] - pts[i][1]) * ty
                if dot_prev < -1e-4 or dot_next < -1e-4:
                    if (dx * tx + dy * ty) >= 0:
                        pts[i] = ((pts[i - 1][0] + pts[i + 1][0]) * 0.5,
                                  (pts[i - 1][1] + pts[i + 1][1]) * 0.5)
    # Collapse any segment crossing (including line-to-cubic corners) onto its intersection cusp.
    for _ in range(3):
        changed = False
        for i in range(n - 3):
            for j in range(n - 2, i + 1, -1):
                if j == i + 2 and hypot(pts[i + 1][0] - pts[j][0], pts[i + 1][1] - pts[j][1]) < 1e-3:
                    # Coincident segment join vertex; only treat as crossing if a strictly interior overlap exists.
                    continue
                ix = _seg_intersect(pts[i], pts[i + 1], pts[j], pts[j + 1])
                if ix is not None:
                    for k in range(i + 1, j + 1):
                        pts[k] = ix
                    changed = True
                    break
        if not changed:
            break
    return pts


def _pill_cap(left_pt, right_pt, scale):
    """Exact 2-off-curve semicircular cap from left_pt to right_pt (5 interior pts)."""
    lx, ly = left_pt[0], left_pt[1]
    rx, ry = right_pt[0], right_pt[1]
    mx, my = (lx + rx) * 0.5, (ly + ry) * 0.5
    ux, uy = lx - mx, ly - my
    r_half = hypot(ux, uy)
    if r_half < 1e-6:
        return [(mx, my, False), (mx, my, False), (mx, my, True), (mx, my, False), (mx, my, False)]
    # Outward tangent is 90 deg CCW from (left -> right): (ly - ry, rx - lx).
    tx, ty, _ = _unit(ly - ry, rx - lx)
    d = r_half * max(0.0, scale)
    tip_x, tip_y = mx + tx * d, my + ty * d
    v1x, v1y = lx + tx * d, ly + ty * d
    v2x, v2y = rx + tx * d, ry + ty * d
    return [
        (lx + ARC_K * (v1x - lx), ly + ARC_K * (v1y - ly), False),
        (tip_x + ARC_K * (v1x - tip_x), tip_y + ARC_K * (v1y - tip_y), False),
        (tip_x, tip_y, True),
        (tip_x + ARC_K * (v2x - tip_x), tip_y + ARC_K * (v2y - tip_y), False),
        (rx + ARC_K * (v2x - rx), ry + ARC_K * (v2y - ry), False),
    ]


class Pen:
    def __init__(self, outline, vertical, horizontal, roundness, metrics: MasterMetrics | None = None):
        self.outline = outline
        self.v = vertical
        self.h = horizontal
        self.roundness = roundness
        self.metrics = metrics
        self.radius = vertical * roundness * 0.50

    def polygon(self, vertices, radius=None, hole=False, corner_radii=None, sharp_corners=()):
        """Build a polygon using 2-off-curve circular corners and optional sharp joins.

        - Entries in ``corner_radii`` equal to ``None`` or indices in ``sharp_corners``
          emit a single ON-curve point across every master (hidden joins stay sharp).
        - Float radii (including ``0.0`` when ``ROND=0``) emit 4 points per corner
          with ``ARC_K = sqrt(2) - 1`` handles so point topology stays invariant.
        """
        verts = [(float(x), float(y)) for x, y in vertices]
        n = len(verts)
        if n < 3:
            return

        default_r = self.radius if radius is None else radius
        if corner_radii is None:
            radii = [default_r] * n
        else:
            radii = list(corner_radii)
            assert len(radii) == n, "corner_radii length must match vertices"

        sharp_set = set(sharp_corners)
        is_sharp = [idx in sharp_set or radii[idx] is None for idx in range(n)]

        area = _signed_area(verts)
        if (area > 0) != hole:
            verts.reverse()
            radii.reverse()
            is_sharp.reverse()

        u_prev = []
        u_next = []
        len_next = []
        d_raw = [0.0] * n
        cos_a = [0.0] * n

        for i in range(n):
            vx, vy = verts[i]
            bx, by = verts[i - 1]
            ax, ay = verts[(i + 1) % n]
            ux1, uy1, _ = _unit(bx - vx, by - vy)
            ux2, uy2, l2 = _unit(ax - vx, ay - vy)
            u_prev.append((ux1, uy1))
            u_next.append((ux2, uy2))
            len_next.append(l2)
            c = max(-0.9999, min(0.9999, ux1 * ux2 + uy1 * uy2))
            cos_a[i] = c
            if not is_sharp[i] and (radii[i] or 0.0) > 0.0:
                tan_half_phi = sqrt(max(0.0, (1.0 + c) / max(1e-9, 1.0 - c)))
                d_raw[i] = max(0.0, float(radii[i])) * tan_half_phi

        d_in = list(d_raw)
        d_out = list(d_raw)
        for i in range(n):
            j = (i + 1) % n
            edge_l = len_next[i]
            req = d_out[i] + d_in[j]
            if req > 0.992 * edge_l and req > 1e-9:
                scale = (0.992 * edge_l) / req
                d_out[i] = min(d_out[i], d_raw[i] * scale)
                d_in[j] = min(d_in[j], d_raw[j] * scale)

        contour = []
        for i, (vx, vy) in enumerate(verts):
            if is_sharp[i]:
                contour.append((vx, vy, True))
                continue
            di = min(d_in[i], d_out[i]) if radii[i] else 0.0
            # Keep true semi-circle proportions on acute diagonal tips where edge-sum allows:
            if cos_a[i] > 0.15:
                di_in, di_out = d_in[i], d_out[i]
            else:
                di_in = di_out = di
            ux1, uy1 = u_prev[i]
            ux2, uy2 = u_next[i]
            cos_half_phi = sqrt(max(0.0, (1.0 - cos_a[i]) * 0.5))
            s = cos_half_phi / (1.0 + cos_half_phi)
            p0 = (vx + di_in * ux1, vy + di_in * uy1, True)
            p1 = (vx + di_in * (1.0 - s) * ux1, vy + di_in * (1.0 - s) * uy1, False)
            p2 = (vx + di_out * (1.0 - s) * ux2, vy + di_out * (1.0 - s) * uy2, False)
            p3 = (vx + di_out * ux2, vy + di_out * uy2, True)
            contour.extend([p0, p1, p2, p3])
        self.outline.contours.append(contour)

    def box(self, x0, y0, x1, y1, radius=None, corner_radii=None, sharp_corners=()):
        self.polygon(
            [(x0, y0), (x0, y1), (x1, y1), (x1, y0)],
            radius=radius,
            corner_radii=corner_radii,
            sharp_corners=sharp_corners,
        )

    def squircle(
        self,
        x0,
        y0,
        x1,
        y1,
        wall_side=None,
        wall_top=None,
        wall_bot=None,
        ring=True,
        kx=None,
        ky=None,
        ikx=None,
        iky=None,
        flat_out=None,
        flat_in=None,
        cap=None,
    ):
        """Draw a true 14-point Google Sans Flex squircle/stadium outer (+ inner counter)."""
        w = max(2.0, x1 - x0)
        h = max(2.0, y1 - y0)
        if self.metrics is not None:
            is_cap = (h > self.metrics.x_h * 1.12) if cap is None else cap
            mp = self.metrics.squircle_params(cap=is_cap)
            def_side = self.metrics.O_side if is_cap else self.metrics.o_side
            def_top = self.metrics.O_top if is_cap else self.metrics.o_top
        else:
            mp = {"kx": 0.426, "ky": 0.430, "flat_out": None, "ikx": 0.428, "iky": 0.435, "flat_in": None}
            def_side = self.v
            def_top = self.h

        _kx = mp["kx"] if kx is None else kx
        _ky = mp["ky"] if ky is None else ky
        _ikx = mp["ikx"] if ikx is None else ikx
        _iky = mp["iky"] if iky is None else iky
        _fout = _adaptive_flat(w * 0.5, h * 0.5, mp["flat_out"] if flat_out is None else flat_out)
        self.outline.contours.append(_squircle_loop(x0, y0, x1, y1, _kx, _ky, _fout, cw=True))

        if ring:
            ws = def_side if wall_side is None else wall_side
            wt = def_top if wall_top is None else wall_top
            wb = wt if wall_bot is None else wall_bot
            # Protect open counter against extreme weight/condensed collapse monotonically.
            max_ws = max(4.0, (w - max(12.0, w * 0.14)) * 0.5)
            max_wt = max(4.0, (h - max(14.0, h * 0.14)) * 0.5)
            ws = min(ws, max_ws)
            wt = min(wt, max_wt)
            wb = min(wb, max_wt)
            ix0, iy0, ix1, iy1 = x0 + ws, y0 + wb, x1 - ws, y1 - wt
            iw, ih = max(4.0, ix1 - ix0), max(4.0, iy1 - iy0)
            _fin = _adaptive_flat(iw * 0.5, ih * 0.5, mp["flat_in"] if flat_in is None else flat_in)
            self.outline.contours.append(
                _squircle_loop(ix0, iy0, ix1, iy1, _ikx, _iky, _fin, cw=False)
            )

    def oval(self, x0, y0, x1, y1, ring=True, wall=1.0):
        """Legacy wrapper delegating cleanly to 14-point Google Sans squircle."""
        self.squircle(
            x0,
            y0,
            x1,
            y1,
            wall_side=wall * self.v,
            wall_top=wall * self.h,
            ring=ring,
        )

    def dot(self, x, y, diameter=None):
        """Circular Google Sans dot (stays round even at condensed wdth)."""
        d = diameter if diameter is not None else (
            self.metrics.H_stem * 1.18 if self.metrics is not None else self.v * 0.96 + 58.0
        )
        r = max(2.0, d * 0.5)
        self.squircle(
            x - r,
            y - r,
            x + r,
            y + r,
            ring=False,
            kx=0.422,
            ky=0.422,
            flat_out=0.0,
        )

    def stroke(
        self,
        start,
        *segments,
        weight=1.0,
        weight_x=None,
        weight_y=None,
        cap=None,
        cap_start=None,
        cap_end=None,
    ):
        """Draw a straight or cubic stroke with curvature clamping and 2-off-curve pill caps."""
        base_cap = self.roundness if cap is None else cap
        c_start = base_cap if cap_start is None else cap_start
        c_end = base_cap if cap_end is None else cap_end
        hw_x = (self.v if weight_x is None else weight_x) * weight * 0.5
        hw_y = (self.h if weight_y is None else weight_y) * weight * 0.5

        left_poly, right_poly = [], []
        left_tan, right_tan = [], []
        spans = []  # (kind, count)
        a = start
        steps = 8

        for segment in segments:
            if len(segment) == 2:
                b = segment
                dx, dy = b[0] - a[0], b[1] - a[1]
                nx, ny = normal(dx, dy)
                ex, ey = nx * hw_x, ny * hw_y
                l_pts = [(a[0] + ex, a[1] + ey), (b[0] + ex, b[1] + ey)]
                r_pts = [(a[0] - ex, a[1] - ey), (b[0] - ex, b[1] - ey)]
                t_pts = [(dx, dy), (dx, dy)]
                spans.append(("line", len(left_poly), len(left_poly) + 2))
            else:
                c1, c2, b = segment
                l_pts, r_pts, t_pts = [], [], []
                for step in range(steps + 1):
                    t = step / steps
                    u = 1.0 - t
                    x = (
                        u ** 3 * a[0]
                        + 3.0 * u * u * t * c1[0]
                        + 3.0 * u * t * t * c2[0]
                        + t ** 3 * b[0]
                    )
                    y = (
                        u ** 3 * a[1]
                        + 3.0 * u * u * t * c1[1]
                        + 3.0 * u * t * t * c2[1]
                        + t ** 3 * b[1]
                    )
                    dx = 3.0 * (u * u * (c1[0] - a[0]) + 2.0 * u * t * (c2[0] - c1[0]) + t * t * (b[0] - c2[0]))
                    dy = 3.0 * (u * u * (c1[1] - a[1]) + 2.0 * u * t * (c2[1] - c1[1]) + t * t * (b[1] - c2[1]))
                    ddx = 6.0 * (u * (c2[0] - 2.0 * c1[0] + a[0]) + t * (b[0] - 2.0 * c2[0] + c1[0]))
                    ddy = 6.0 * (u * (c2[1] - 2.0 * c1[1] + a[1]) + t * (b[1] - 2.0 * c2[1] + c1[1]))

                    nx, ny = normal(dx, dy)
                    ex, ey = nx * hw_x, ny * hw_y
                    r_off = hypot(ex, ey)
                    speed2 = dx * dx + dy * dy
                    scale_l, scale_r = 1.0, 1.0
                    if speed2 > 1e-6 and r_off > 1e-6:
                        cross = dx * ddy - dy * ddx
                        kappa = cross / (speed2 ** 1.5)
                        if kappa > 1e-7:
                            r_turn = 1.0 / kappa
                            if r_off > 0.88 * r_turn:
                                scale_l = max(0.05, (0.88 * r_turn) / r_off)
                        elif kappa < -1e-7:
                            r_turn = -1.0 / kappa
                            if r_off > 0.88 * r_turn:
                                scale_r = max(0.05, (0.88 * r_turn) / r_off)

                    t_pts.append((dx, dy))
                    l_pts.append((x + ex * scale_l, y + ey * scale_l))
                    r_pts.append((x - ex * scale_r, y - ey * scale_r))

                spans.append(("cubic", len(left_poly), len(left_poly) + steps + 1))

            left_poly.extend(l_pts)
            right_poly.extend(r_pts)
            left_tan.extend(t_pts)
            right_tan.extend(t_pts)
            a = b

        # De-kink each side globally across all segments (prevents line-to-curve corner crossings).
        left_poly = _de_kink_polyline(left_poly, left_tan)
        right_poly = _de_kink_polyline(right_poly, right_tan)

        left, right = [], []
        for kind, s0, s1 in spans:
            if kind == "line":
                left.extend([(left_poly[s0][0], left_poly[s0][1], True),
                             (left_poly[s0 + 1][0], left_poly[s0 + 1][1], True)])
                right.extend([(right_poly[s0][0], right_poly[s0][1], True),
                              (right_poly[s0 + 1][0], right_poly[s0 + 1][1], True)])
            else:
                left.extend(_fit_quads(left_poly[s0:s1]))
                right.extend(_fit_quads(right_poly[s0:s1]))

        contour = (
            left
            + _pill_cap(left[-1], right[-1], c_end)
            + right[::-1]
            + _pill_cap(right[0], left[0], c_start)
        )
        self.outline.contours.append(contour)


class Letter(Pen):
    """Glyph builder supporting both final-space ``MasterMetrics`` and legacy profiles."""

    def __init__(self, profile, width=None, left=None, right=None, growth=0.62, *, cap=True, ref="H"):
        if isinstance(profile, MasterMetrics):
            m = profile
            self.metrics = m
            self.cap = cap
            height = m.cap_h if cap else m.x_h
            vertical = m.H_stem if cap else m.n_stem
            horizontal = m.E_mid if cap else m.o_top
            roundness = m.roundness
            ref_lsb = m.H_lsb if cap else m.n_lsb
            self.left = ref_lsb if left is None else left
            right_sb = ref_lsb if right is None else right
            if width is None:
                ref_adv = m.ref_adv(ref, cap=cap)
                width = max(vertical * 1.2, ref_adv - 2.0 * ref_lsb)
            self.width = width
            self.right = self.left + self.width
            self.mid = self.left + self.width * 0.5
            self.top = height
            self.ov = m.cap_ov if cap else m.low_ov
            self.curve_ov = self.ov
            self.rond_ov = m.rond_ov_cap if cap else m.rond_ov_low
            self.overhang = m.h_overhang_cap if cap else m.h_overhang_low
            self.spine_r = vertical * 0.55 * roundness
            self.desc_y = m.desc_y
            self.tip = height + self.rond_ov
            self.bottom = -self.rond_ov
            super().__init__(Outline(self.right + right_sb), vertical, horizontal, roundness, metrics=m)
            return

        # Legacy 6-tuple compatibility path for existing cyrillic_design.py callers.
        height, vertical, horizontal, spacing, weight, roundness = profile
        self.metrics = None
        self.cap = height > 1200
        l_sb = (160.0 if left is None else left) * spacing
        r_sb = (160.0 if right is None else right) * spacing
        self.left = l_sb
        self.width = (width if width is not None else 700.0) + growth * (weight - 170.0)
        self.right = self.left + self.width
        self.mid = self.left + self.width * 0.5
        self.top = height
        overshoot = vertical * roundness * 0.054
        self.ov = 28.0 + 9.0 * min(max(vertical / 400.0, 0.0), 1.0)
        self.curve_ov = self.ov
        self.rond_ov = overshoot
        self.overhang = vertical * roundness * 0.054
        self.spine_r = vertical * 0.55 * roundness
        self.desc_y = -432.0
        self.tip = height + overshoot
        self.bottom = -overshoot
        super().__init__(Outline(self.right + r_sb), vertical, horizontal, roundness, metrics=None)

    @classmethod
    def from_metrics(cls, metrics: MasterMetrics, cap=True, width=None, left=None, right=None, ref="H"):
        """Construct a final-space Letter builder bound to ``metrics``."""
        return cls(metrics, width=width, left=left, right=right, cap=cap, ref=ref)

    def stem(
        self,
        x,
        bottom=None,
        top=None,
        weight=1.0,
        top_r=None,
        bottom_r=None,
        corner_radii=None,
        sharp_corners=(),
    ):
        w = self.v * weight
        y0 = self.bottom if bottom is None else bottom
        y1 = self.tip if top is None else top
        default_r = w * self.roundness * 0.50
        if corner_radii is not None:
            self.box(x, y0, x + w, y1, corner_radii=corner_radii, sharp_corners=sharp_corners)
            return
        if top_r is None and bottom_r is None and not sharp_corners:
            self.box(x, y0, x + w, y1, radius=default_r)
            return
        rt = default_r if top_r is None else top_r
        rb = default_r if bottom_r is None else bottom_r
        self.box(x, y0, x + w, y1, corner_radii=(rb, rt, rt, rb), sharp_corners=sharp_corners)

    def bar(
        self,
        x0,
        y,
        x1,
        thickness=None,
        radius=None,
        overhang_left=False,
        overhang_right=False,
        corner_radii=None,
        sharp_corners=(),
    ):
        t = self.h if thickness is None else thickness
        half = t * 0.5
        ax0 = x0 - (self.overhang if overhang_left else 0.0)
        ax1 = x1 + (self.overhang if overhang_right else 0.0)
        r = half * self.roundness if radius is None else radius
        self.box(
            ax0,
            y - half,
            ax1,
            y + half,
            radius=r,
            corner_radii=corner_radii,
            sharp_corners=sharp_corners,
        )

    def diagonal(self, x0, y0, x1, y1, weight=1.0, cap=None, cap_start=None, cap_end=None):
        self.stroke((x0, y0), (x1, y1), weight=weight, cap=cap, cap_start=cap_start, cap_end=cap_end)

    def curved_bowl(
        self,
        left,
        bottom,
        right,
        top,
        *,
        stem_w=None,
        wall=None,
        top_bar=None,
        bot_bar=None,
        flat_base=True,
        top_ov=0.0,
        bot_ov=0.0,
        spine_top_r=None,
        spine_bot_r=None,
        inktrap_top=False,
        inktrap_bot=False,
        kx=None,
        ky=None,
        ikx=None,
        iky=None,
        flat_frac=None,
        draw_spine=True,
    ):
        """Build a 2-handle Google Sans Flex bowl (B/P/R or b/p style) with monotonic contrast."""
        m = self.metrics
        sw = (self.v if stem_w is None else stem_w)
        width = max(sw + 16.0, right - left)
        height = max(24.0, top - bottom)

        if wall is None:
            raw_wall = m.O_side * 0.94 if m is not None else self.v * 0.94
        else:
            raw_wall = wall
        if top_bar is None:
            raw_top_bar = (m.P_bar if self.cap else m.o_top) if m is not None else self.h * 0.94
        else:
            raw_top_bar = top_bar
        if bot_bar is None:
            raw_bot_bar = (m.B_bot if self.cap else m.o_top) if m is not None else self.h * 0.94
        else:
            raw_bot_bar = bot_bar

        # Monotonic open-counter guards: prevent shrinkage or counter pinch at wght=1000 / wdth=25.
        min_cw = max(14.0, width * 0.14)
        min_ch = max(16.0, height * 0.16)
        eff_wall = min(raw_wall, max(8.0, (width - sw - min_cw) * 0.85))
        eff_wall = max(8.0, min(eff_wall, width - sw - min_cw))
        total_v_bar = raw_top_bar + raw_bot_bar
        max_v_bar = max(12.0, height - min_ch)
        if total_v_bar > max_v_bar and total_v_bar > 1e-6:
            s_bar = max_v_bar / total_v_bar
            raw_top_bar *= s_bar
            raw_bot_bar *= s_bar

        _kx = (m.kx if m is not None else 0.425) if kx is None else kx
        _ky = (m.ky if m is not None else 0.430) if ky is None else ky
        _ikx = (m.ikx if m is not None else 0.428) if ikx is None else ikx
        _iky = (m.iky if m is not None else 0.435) if iky is None else iky

        out_b = bottom if flat_base else (bottom - bot_ov)
        out_t = top + top_ov
        mid = (out_b + out_t) * 0.5
        out_h_half = (out_t - out_b) * 0.5

        f_out = _adaptive_flat(width * 0.55, out_h_half, m.flat_out if (m is not None and flat_frac is None) else flat_frac)
        h_flat = out_h_half * f_out
        y_side_b = mid - h_flat
        y_side_t = mid + h_flat
        arc_ht = max(1e-3, out_t - y_side_t)
        arc_hb = max(1e-3, y_side_b - out_b)

        spine_r_def = sw * 0.55 * self.roundness
        rb = spine_r_def if spine_bot_r is None else spine_bot_r
        rt = spine_r_def if spine_top_r is None else spine_top_r
        rb = min(rb, height * 0.45, width * 0.35)
        rt = min(rt, height * 0.45, width * 0.35)

        stem_r = left + sw
        # Keep non-spine attach_x safely inside rounded 0.55*sw stem tangents (fixes ROND=100 fin on Ь/ь/Ъ/ъ/Ы/ы)
        # while staying strictly left of stem_r - trap_x (<= 0.75*sw) if inktraps are enabled.
        spine_tangent = max(spine_r_def, rb, rt)
        attach_x = left + min(sw * 0.72, max(sw * 0.62, spine_tangent + 6.0))
        trap_x = min(sw * 0.25, 16.0)
        trap_y = min(raw_top_bar * 0.25, 20.0)
        min_sh_x = min(right - 6.0, stem_r + (trap_x * 0.5 if (not draw_spine and inktrap_top) else 0.0) + 4.0)
        min_sh_xb = min(right - 6.0, stem_r + (trap_x * 0.5 if (not draw_spine and inktrap_bot) else 0.0) + 4.0)
        if draw_spine:
            shoulder_t = max(min_sh_x, min(right - 8.0, left + width * 0.46))
            shoulder_b = max(min_sh_xb, min(right - 8.0, left + width * 0.46))
        else:
            shoulder_t = max(min_sh_x, min(right - 8.0, stem_r + (right - stem_r) * 0.36))
            shoulder_b = max(min_sh_xb, min(right - 8.0, stem_r + (right - stem_r) * 0.36))

        if draw_spine:
            outer = [
                (left, bottom + rb, True),
                (left, top - rt, True),
                (left, top - (1.0 - ARC_K) * rt, False),
                (left + (1.0 - ARC_K) * rt, top, False),
                (left + rt, top, True),
            ]
        else:
            outer = [
                (attach_x, bottom, True),
                (attach_x, top, True),
            ]
            if inktrap_top:
                outer.append((stem_r, top, True))
                outer.append((stem_r - trap_x, top - trap_y, True))
                outer.append((stem_r + trap_x * 0.5, top, True))
            else:
                outer.append((stem_r, top, True))

        outer.extend([
            (shoulder_t, out_t, True),
            (shoulder_t + (right - shoulder_t) * _kx, out_t, False),
            (right, y_side_t + arc_ht * _ky, False),
            (right, y_side_t, True),
            (right, y_side_b, True),
            (right, y_side_b - arc_hb * _ky, False),
            (shoulder_b + (right - shoulder_b) * _kx, out_b, False),
            (shoulder_b, out_b, True),
        ])

        if draw_spine:
            outer.extend([
                (left + rb, bottom, True),
                (left + (1.0 - ARC_K) * rb, bottom, False),
                (left, bottom + (1.0 - ARC_K) * rb, False),
            ])
        else:
            if inktrap_bot:
                trap_x = min(sw * 0.25, 16.0)
                trap_y = min(raw_bot_bar * 0.25, 20.0)
                outer.append((stem_r + trap_x * 0.5, bottom, True))
                outer.append((stem_r - trap_x, bottom + trap_y, True))
            outer.append((stem_r, bottom, True))

        self.outline.contours.append(outer)

        # Inner counter contour (CCW), safely tucked inside stem_r.
        in_x = left + min(sw * 0.72, sw - 4.0) if draw_spine else stem_r
        in_l = stem_r
        in_r = max(in_l + min_cw, right - eff_wall)
        in_b = out_b + raw_bot_bar
        in_t = out_t - raw_top_bar
        in_mid = (in_b + in_t) * 0.5
        in_h_half = max(2.0, (in_t - in_b) * 0.5)
        f_in = _adaptive_flat((in_r - in_l) * 0.55, in_h_half, m.flat_in if (m is not None and flat_frac is None) else flat_frac)
        ih_flat = in_h_half * f_in
        iy_b = in_mid - ih_flat
        iy_t = in_mid + ih_flat
        i_arc_b = max(1e-3, iy_b - in_b)
        i_arc_t = max(1e-3, in_t - iy_t)
        sh_in = min(shoulder_t, in_l + (in_r - in_l) * 0.45)

        self.outline.contours.append([
            (in_x, in_b, True),
            (sh_in, in_b, True),
            (sh_in + (in_r - sh_in) * _ikx, in_b, False),
            (in_r, iy_b - i_arc_b * _iky, False),
            (in_r, iy_b, True),
            (in_r, iy_t, True),
            (in_r, iy_t + i_arc_t * _iky, False),
            (sh_in + (in_r - sh_in) * _ikx, in_t, False),
            (sh_in, in_t, True),
            (in_x, in_t, True),
        ])

    def double_bowl(
        self,
        left,
        bottom,
        right_top,
        right_bot,
        top,
        *,
        mid_y=None,
        stem_w=None,
        wall_top=None,
        wall_bot=None,
        top_bar=None,
        mid_bar=None,
        bot_bar=None,
        waist_x=None,
        spine_r=None,
        kx=None,
        ky=None,
        ikx=None,
        iky=None,
        flat_frac=None,
    ):
        """Native Google Sans Flex B-style double bowl (24-pt outer + 20-pt figure-8 counter)."""
        m = self.metrics
        sw = self.v if stem_w is None else stem_w
        height = max(48.0, top - bottom)
        mid = bottom + height * 0.517 if mid_y is None else mid_y
        w_top = max(sw + 16.0, right_top - left)
        w_bot = max(sw + 16.0, right_bot - left)

        wt = (m.B_rwall_top * (w_top / max(m.B_adv - 2 * m.B_lsb, 1.0)) if m is not None else sw * 0.94) if wall_top is None else wall_top
        wb = (m.B_rwall_bot * (w_bot / max(m.B_adv - 2 * m.B_lsb, 1.0)) if m is not None else sw * 0.96) if wall_bot is None else wall_bot

        tb = (m.B_top * (height / m.cap_h) if m is not None else self.h) if top_bar is None else top_bar
        mb = (m.B_mid * (height / m.cap_h) if m is not None else self.h * 0.85) if mid_bar is None else mid_bar
        bb = (m.B_bot * (height / m.cap_h) if m is not None else self.h) if bot_bar is None else bot_bar

        # Keep both upper and lower counters open across wght=1..1000 & wdth=25.
        min_cw = max(12.0, w_bot * 0.13)
        wt = max(6.0, min(wt, w_top - sw - min_cw))
        wb = max(6.0, min(wb, w_bot - sw - min_cw))

        up_h = top - (mid + mb * 0.5)
        lo_h = (mid - mb * 0.5) - bottom
        min_ch_u = max(12.0, (top - mid) * 0.18)
        min_ch_l = max(12.0, (mid - bottom) * 0.18)
        if tb > up_h - min_ch_u or bb > lo_h - min_ch_l:
            avail = max(18.0, height - min_ch_u - min_ch_l)
            total = tb + mb + bb
            if total > avail and total > 1e-6:
                s = avail / total
                tb *= s
                mb *= s
                bb *= s

        _kx = (m.kx if m is not None else 0.425) if kx is None else kx
        _ky = (m.ky if m is not None else 0.435) if ky is None else ky
        _ikx = (m.ikx if m is not None else 0.428) if ikx is None else ikx
        _iky = (m.iky if m is not None else 0.435) if iky is None else iky

        sp_r = sw * 0.55 * self.roundness if spine_r is None else spine_r
        sp_r = min(sp_r, height * 0.35, w_top * 0.35)

        if waist_x is None:
            pinch = min(w_bot * 0.28, max(24.0, w_bot * 0.24))
            wx = max(left + sw + min_cw * 0.6, right_bot - pinch)
        else:
            wx = waist_x

        wy_t = mid + 4.0
        wy_b = mid - 4.0

        # Upper outer lobe
        sh_t = min(right_top - 6.0, left + w_top * 0.52)
        up_mid = (top + wy_t) * 0.5
        up_half = max(2.0, (top - wy_t) * 0.5)
        fu = _adaptive_flat(w_top * 0.55, up_half, m.flat_out * 0.7 if (m is not None and flat_frac is None) else flat_frac)
        ut_side = up_mid + up_half * fu
        ub_side = up_mid - up_half * fu

        # Lower outer lobe
        sh_b = min(right_bot - 6.0, left + w_bot * 0.55)
        lo_mid = (wy_b + bottom) * 0.5
        lo_half = max(2.0, (wy_b - bottom) * 0.5)
        fl = _adaptive_flat(w_bot * 0.55, lo_half, m.flat_out * 0.7 if (m is not None and flat_frac is None) else flat_frac)
        lt_side = lo_mid + lo_half * fl
        lb_side = lo_mid - lo_half * fl

        self.outline.contours.append([
            (left + sp_r, bottom, True),
            (left + sp_r * (1.0 - ARC_K), bottom, False),
            (left, bottom + sp_r * (1.0 - ARC_K), False),
            (left, bottom + sp_r, True),
            (left, top - sp_r, True),
            (left, top - sp_r * (1.0 - ARC_K), False),
            (left + sp_r * (1.0 - ARC_K), top, False),
            (left + sp_r, top, True),
            (sh_t, top, True),
            (sh_t + (right_top - sh_t) * _kx, top, False),
            (right_top, ut_side + max(1.0, top - ut_side) * _ky, False),
            (right_top, ut_side, True),
            (right_top, ub_side, True),
            (right_top, ub_side - max(1.0, ub_side - wy_t) * _ky * 0.85, False),
            (wx + (right_top - wx) * 0.38, wy_t + max(1.0, ub_side - wy_t) * 0.20, False),
            (wx, wy_t, True),
            (wx, wy_b, True),
            (wx + (right_bot - wx) * 0.42, wy_b - max(1.0, wy_b - lt_side) * 0.20, False),
            (right_bot, lt_side + max(1.0, wy_b - lt_side) * _ky * 0.85, False),
            (right_bot, lt_side, True),
            (right_bot, lb_side, True),
            (right_bot, lb_side - max(1.0, lb_side - bottom) * _ky, False),
            (sh_b + (right_bot - sh_b) * _kx, bottom, False),
            (sh_b, bottom, True),
        ])

        # Single figure-8 CCW inner contour matching B.C1[0..19]
        stem_r = left + sw
        mid_stem_x = left + sw * 0.53
        in_bot_r = max(stem_r + min_cw, right_bot - wb)
        in_top_r = max(stem_r + min_cw * 0.9, right_top - wt)

        in_bb = bottom + bb
        in_mb = mid - mb * 0.5
        in_mt = mid + mb * 0.5
        in_tt = top - tb

        ilo_mid = (in_bb + in_mb) * 0.5
        ilo_half = max(1.5, (in_mb - in_bb) * 0.5)
        ilo_f = min(fl, 0.5)
        ilo_b_s = ilo_mid - ilo_half * ilo_f
        ilo_t_s = ilo_mid + ilo_half * ilo_f

        iup_mid = (in_mt + in_tt) * 0.5
        iup_half = max(1.5, (in_tt - in_mt) * 0.5)
        iup_f = min(fu, 0.5)
        iup_b_s = iup_mid - iup_half * iup_f
        iup_t_s = iup_mid + iup_half * iup_f

        sh_in_b = min(in_bot_r - 4.0, stem_r + (in_bot_r - stem_r) * 0.50)
        sh_in_t = min(in_top_r - 4.0, stem_r + (in_top_r - stem_r) * 0.48)

        self.outline.contours.append([
            (stem_r, in_bb, True),
            (sh_in_b, in_bb, True),
            (sh_in_b + (in_bot_r - sh_in_b) * _ikx, in_bb, False),
            (in_bot_r, ilo_b_s - max(1.0, ilo_b_s - in_bb) * _iky, False),
            (in_bot_r, ilo_b_s, True),
            (in_bot_r, ilo_t_s, True),
            (in_bot_r, ilo_t_s + max(1.0, in_mb - ilo_t_s) * _iky, False),
            (sh_in_b + (in_bot_r - sh_in_b) * _ikx, in_mb, False),
            (sh_in_b, in_mb, True),
            (mid_stem_x, in_mb, True),
            (mid_stem_x, in_mt, True),
            (sh_in_t, in_mt, True),
            (sh_in_t + (in_top_r - sh_in_t) * _ikx, in_mt, False),
            (in_top_r, iup_b_s - max(1.0, iup_b_s - in_mt) * _iky, False),
            (in_top_r, iup_b_s, True),
            (in_top_r, iup_t_s, True),
            (in_top_r, iup_t_s + max(1.0, in_tt - iup_t_s) * _iky, False),
            (sh_in_t + (in_top_r - sh_in_t) * _ikx, in_tt, False),
            (sh_in_t, in_tt, True),
            (stem_r, in_tt, True),
        ])

    def curved_arch(
        self,
        join_x,
        base_y,
        right,
        top_y,
        *,
        leg_w=None,
        top_bar=None,
        join_y=None,
        join_frac=None,
        arch_ov=None,
        bottom_y=None,
        pill_r=None,
        inktrap=True,
        invert=False,
        mirror=False,
    ):
        """Native Google Sans Flex n/h/u-style arch with 2-handle curve, inktrap, and pill end."""
        m = self.metrics
        lw = self.v if leg_w is None else leg_w
        tb = (m.o_top if (m is not None and not self.cap) else self.h) if top_bar is None else top_bar
        ov = (m.low_ov if (m is not None and not self.cap) else self.ov) if arch_ov is None else arch_ov
        end_y = (self.bottom if not invert else self.tip) if bottom_y is None else bottom_y
        r_pill = (lw * 0.5 * self.roundness) if pill_r is None else pill_r
        r_pill = min(r_pill, lw * 0.5, max(2.0, abs(top_y - end_y) * 0.4))

        dir_x = -1.0 if mirror else 1.0
        sign_y = -1.0 if invert else 1.0
        span_w = max(lw + 12.0, abs(right - join_x))
        span_h = max(24.0, abs(top_y - base_y))
        peak_y_rel = span_h + ov
        end_rel = (base_y - end_y) if not invert else (end_y - base_y)

        peak_x = max(8.0, (span_w - lw * 0.08) * 0.54)
        in_x = max(8.0, span_w - lw)
        in_peak_x = min(in_x - 2.0, max(4.0, peak_x - lw * 0.04))

        custom_join = (join_y is not None) or (join_frac is not None)
        if not custom_join:
            # Native n/h arch join & inner crotch heights (preserves default callers identically).
            def_join = span_h * (0.85 if span_w >= lw * 2.2 else 0.90)
            if m is not None and m.wght < 200:
                # Lighter weights drop outer join slightly lower just like native h at wght=1 (0.72*x_h)
                t_w = max(0.0, (200.0 - m.wght) / 199.0)
                def_join = span_h * (0.85 - 0.13 * t_w)
            join_rel = min(peak_y_rel - max(tb * 0.45, 18.0), max(tb * 1.1, def_join))
            arch_h = max(12.0, peak_y_rel - join_rel)
            in_peak_y = max(24.0, min(peak_y_rel - max(6.0, tb * 0.55), peak_y_rel - tb))
            in_join_rel = span_h * (0.56 if span_w >= lw * 2.2 else 0.74)
            in_join_y = max(4.0, min(in_peak_y - 12.0, join_rel - max(18.0, tb * 0.75), in_join_rel))
            in_sh_y = max(-end_rel + r_pill, min(in_peak_y - 6.0, in_peak_y - (in_peak_y - in_join_y) * 0.55))
            shoulder_y_rel = max(in_sh_y + max(4.0, tb * 0.18), -end_rel + r_pill, peak_y_rel - arch_h * 1.20)
            shoulder_y_rel = min(peak_y_rel - 4.0, shoulder_y_rel)
            trap_out = min(lw * 0.05, 8.0) if inktrap else 0.0
            pts = [
                (0.0, join_rel, True),
                (trap_out * 0.5, join_rel, True),
                (trap_out, join_rel, True),
                (max(4.0, trap_out + (peak_x - trap_out) * 0.14), join_rel + arch_h * 0.45, False),
                (max(8.0, peak_x * 0.62), peak_y_rel, False),
                (peak_x, peak_y_rel, True),
                (peak_x + (span_w - peak_x) * 0.46, peak_y_rel, False),
                (span_w, shoulder_y_rel + (peak_y_rel - shoulder_y_rel) * 0.52, False),
                (span_w, shoulder_y_rel, True),
                (span_w, -end_rel + r_pill, True),
                (span_w, -end_rel + r_pill * (1.0 - ARC_K), False),
                (span_w - r_pill * (1.0 - ARC_K), -end_rel, False),
                (span_w - r_pill, -end_rel, True),
                (in_x + r_pill, -end_rel, True),
                (in_x + r_pill * (1.0 - ARC_K), -end_rel, False),
                (in_x, -end_rel + r_pill * (1.0 - ARC_K), False),
                (in_x, -end_rel + r_pill, True),
                (in_x, in_sh_y, True),
                (in_x, in_sh_y + (in_peak_y - in_sh_y) * 0.48, False),
                (in_peak_x + (in_x - in_peak_x) * 0.45, in_peak_y, False),
                (in_peak_x, in_peak_y, True),
                (in_peak_x * 0.50, in_peak_y, False),
                (0.0, in_join_y + (in_peak_y - in_join_y) * 0.36, False),
                (0.0, in_join_y, True),
            ]
        else:
            # Deep continuous U/u-cup curve (e.g. Ч/ч) when join_y or join_frac is specified.
            raw_join = abs(join_y - base_y) if join_y is not None else (span_h * float(join_frac))
            join_rel = min(peak_y_rel - max(tb * 0.45, 18.0), max(max(16.0, tb * 0.25), raw_join))
            arch_h = max(12.0, peak_y_rel - join_rel)
            in_peak_y = max(24.0, min(peak_y_rel - max(tb * 0.55, 2.0), peak_y_rel - tb))
            in_join_y = max(4.0, min(in_peak_y - max(tb * 0.40, 2.0), join_rel - max(tb * 0.65, 2.0)))
            shoulder_y_rel = max(-end_rel + r_pill, min(peak_y_rel - 8.0, peak_y_rel - arch_h * 1.02))
            in_sh_y = max(-end_rel + r_pill, min(in_peak_y - 4.0, shoulder_y_rel))
            trap_in = min(lw * 0.08, 10.0) if inktrap else 0.0
            trap_h = min(arch_h * 0.04, 10.0) if inktrap else 0.0
            j0 = join_rel + trap_h
            pts = [
                (0.0, join_rel, True),
                (-trap_in, j0, True),
                (0.0, j0, True),
                (0.0, j0 + (peak_y_rel - j0) * 0.50, False),
                (max(8.0, min(in_peak_x * 0.54, peak_x * 0.50)), peak_y_rel, False),
                (peak_x, peak_y_rel, True),
                (peak_x + (span_w - peak_x) * 0.46, peak_y_rel, False),
                (span_w, shoulder_y_rel + (peak_y_rel - shoulder_y_rel) * 0.52, False),
                (span_w, shoulder_y_rel, True),
                (span_w, -end_rel + r_pill, True),
                (span_w, -end_rel + r_pill * (1.0 - ARC_K), False),
                (span_w - r_pill * (1.0 - ARC_K), -end_rel, False),
                (span_w - r_pill, -end_rel, True),
                (in_x + r_pill, -end_rel, True),
                (in_x + r_pill * (1.0 - ARC_K), -end_rel, False),
                (in_x, -end_rel + r_pill * (1.0 - ARC_K), False),
                (in_x, -end_rel + r_pill, True),
                (in_x, in_sh_y, True),
                (in_x, in_sh_y + (in_peak_y - in_sh_y) * 0.48, False),
                (in_peak_x + (in_x - in_peak_x) * 0.45, in_peak_y, False),
                (in_peak_x, in_peak_y, True),
                (in_peak_x * 0.55, in_peak_y, False),
                (0.0, in_join_y + (in_peak_y - in_join_y) * 0.46, False),
                (0.0, in_join_y, True),
            ]

        mapped = [
            (join_x + dir_x * px, base_y + sign_y * py, on)
            for px, py, on in pts
        ]
        # Maintain clockwise orientation for outer positive contour.
        if (dir_x * sign_y) < 0:
            mapped.reverse()
        self.outline.contours.append(mapped)

    def curved_leg(
        self,
        start,
        c1,
        c2,
        end,
        *,
        start_width=None,
        end_width=None,
        flat_end_y=None,
        cap_start=0.0,
        cap_end=None,
        foot_radius=None,
    ):
        """Draw a smooth curvature-safe curved leg/tail (e.g. Л/л/Д/д leg, У tail)."""
        w0 = (self.v if start_width is None else start_width) * 0.5
        w1 = (self.v if end_width is None else end_width) * 0.5
        c_end = self.roundness if cap_end is None else cap_end

        steps = 8
        centers, tangents, pts_l, pts_r = [], [], [], []
        for step in range(steps + 1):
            t = step / steps
            u = 1.0 - t
            x = u ** 3 * start[0] + 3.0 * u * u * t * c1[0] + 3.0 * u * t * t * c2[0] + t ** 3 * end[0]
            y = u ** 3 * start[1] + 3.0 * u * u * t * c1[1] + 3.0 * u * t * t * c2[1] + t ** 3 * end[1]
            dx = 3.0 * (u * u * (c1[0] - start[0]) + 2.0 * u * t * (c2[0] - c1[0]) + t * t * (end[0] - c2[0]))
            dy = 3.0 * (u * u * (c1[1] - start[1]) + 2.0 * u * t * (c2[1] - c1[1]) + t * t * (end[1] - c2[1]))
            hw = w0 + (w1 - w0) * t
            nx, ny = normal(dx, dy)
            centers.append((x, y))
            tangents.append((dx, dy))
            pts_l.append((x + nx * hw, y + ny * hw))
            pts_r.append((x - nx * hw, y - ny * hw))

        pts_l = _de_kink_polyline(pts_l, tangents)
        pts_r = _de_kink_polyline(pts_r, tangents)

        if flat_end_y is not None:
            # Anchor horizontal foot cleanly onto baseline flat_end_y with no stub self-loop.
            lx, _ = pts_l[-1]
            rx, _ = pts_r[-1]
            pts_l[-1] = (lx, flat_end_y)
            pts_r[-1] = (rx, flat_end_y)
            f_rad = (min(w1 * 0.85, self.h * 0.42) * self.roundness) if foot_radius is None else foot_radius
            left_c = _fit_quads(pts_l)
            right_c = _fit_quads(pts_r)

            def _foot_corner(end_pt, prev_pt, opp_pt, rad):
                vx, vy = end_pt
                ux, uy, l_back = _unit(prev_pt[0] - vx, prev_pt[1] - vy)
                fx, fy, l_span = _unit(opp_pt[0] - vx, 0.0)
                r = min(rad, l_span * 0.45, max(1.0, l_back * 0.45))
                cos_a = max(-0.9999, min(0.9999, ux * fx))
                ch = sqrt(max(0.0, (1.0 - cos_a) * 0.5))
                s = ch / (1.0 + ch)
                return [
                    (vx + r * ux, vy + r * uy, True),
                    (vx + r * (1.0 - s) * ux, vy + r * (1.0 - s) * uy, False),
                    (vx + r * (1.0 - s) * fx, vy, False),
                    (vx + r * fx, vy, True),
                ]

            end_l = _foot_corner(pts_l[-1], pts_l[-2], pts_r[-1], f_rad)
            end_r = _foot_corner(pts_r[-1], pts_r[-2], pts_l[-1], f_rad * 0.35)
            contour = left_c[:-1] + end_l + end_r[::-1] + right_c[:-1][::-1] + _pill_cap(right_c[0], left_c[0], cap_start)
        else:
            left_c = _fit_quads(pts_l)
            right_c = _fit_quads(pts_r)
            contour = (
                left_c
                + _pill_cap(left_c[-1], right_c[-1], c_end)
                + right_c[::-1]
                + _pill_cap(right_c[0], left_c[0], cap_start)
            )
        # Construction traverses left_c -> end_cap -> right_c[::-1] -> start_cap, which
        # is inherently clockwise for every stroke direction with invariant flag order.
        self.outline.contours.append(contour)

    def bowl(self, left, bottom, right, top, bar_scale=1.0, wall_scale=1.0, flat_base=False):
        """Improved bowl with monotonic wall growth (legacy-compatible signature)."""
        width = max(16.0, right - left)
        height = max(24.0, top - bottom)
        corner_r = min(self.v * self.roundness * 0.34, width * 0.18, height * 0.26)
        middle = (top + bottom) * 0.5
        edge = left + min(self.v * 0.84 * wall_scale, width * 0.38)
        # Fixed monotonic wall: never shrink wall by (width * 0.72 - self.v).
        min_counter_w = max(14.0, width * 0.15)
        wall = min(self.v * 0.96 * wall_scale, max(width * 0.16, width - edge - min_counter_w))
        horizontal = min(self.h * bar_scale, height * 0.30)
        shoulder = left + width * 0.38
        if flat_base:
            b_out = bottom
            t_out = top
        else:
            bowl_ov = self.v * 0.035 * self.roundness + 8.0
            b_out = bottom - bowl_ov * 0.45
            t_out = top + bowl_ov * 0.45

        kx = self.metrics.kx if self.metrics is not None else 0.425
        ky = self.metrics.ky if self.metrics is not None else 0.440
        ikx = self.metrics.ikx if self.metrics is not None else 0.435
        iky = self.metrics.iky if self.metrics is not None else 0.440

        self.outline.contours.append([
            (left, bottom + corner_r, True),
            (left, top - corner_r, True),
            (left, top - corner_r * (1.0 - ARC_K), False),
            (left + corner_r * (1.0 - ARC_K), top, False),
            (left + corner_r, top, True),
            (shoulder, t_out, True),
            (shoulder + (right - shoulder) * kx, t_out, False),
            (right, middle + (t_out - middle) * ky, False),
            (right, middle, True),
            (right, middle - (middle - b_out) * ky, False),
            (shoulder + (right - shoulder) * kx, b_out, False),
            (shoulder, b_out, True),
            (left + corner_r, bottom, True),
            (left + corner_r * (1.0 - ARC_K), bottom, False),
            (left, bottom + corner_r * (1.0 - ARC_K), False),
        ])
        in_r = max(edge + min_counter_w, right - wall)
        in_b = bottom + horizontal
        in_t = top - horizontal
        sh_in = min(shoulder, edge + max(0.0, (in_r - edge) * 0.42))
        self.outline.contours.append([
            (edge, in_b, True),
            (sh_in, in_b, True),
            (sh_in + (in_r - sh_in) * ikx, in_b, False),
            (in_r, middle - (middle - in_b) * iky, False),
            (in_r, middle, True),
            (in_r, middle + (in_t - middle) * iky, False),
            (sh_in + (in_r - sh_in) * ikx, in_t, False),
            (sh_in, in_t, True),
            (edge, in_t, True),
        ])
