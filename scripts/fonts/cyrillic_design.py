# SPDX-License-Identifier: OFL-1.1
"""Cyrillic design host, dispatcher, marks, accents, and modifiers in final master coordinates.

Letters with an identical Latin outline share it via ``HOMOGLYPHS``.
Drawn letters are provided by ``cyrillic_arch``, ``cyrillic_diag``, and
``cyrillic_bowl``; ``marks_and_symbols``, ``accent_letters``, and
``raised_letters`` complete the full Unicode Cyrillic coverage and Ruble sign.
"""

from __future__ import annotations

from math import cos, pi, sin
from unicodedata import decomposition

from cyrillic_outlines import (
    Letter,
    Outline,
    Pen,
    extract_master_metrics,
    native_outline,
    shear_slnt,
)


def pairs(cyrillic, latin):
    """Map matching Cyrillic and Latin character sequences 1-to-1."""
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

ENCLOSING = "҈҉꙰꙱꙲"

_ASCENDER_LOWER = frozenset("бфђћѣꙉꙋᲄᲆᲇ")

_OPTICAL_ABOVE_FRACTION = {
    "Ґ": 0.40, "ґ": 0.40,
    "Г": 0.44, "Ѓ": 0.44, "г": 0.44, "ѓ": 0.44, "Ғ": 0.44, "ғ": 0.44, "Ӷ": 0.44, "ӷ": 0.44,
    "К": 0.45, "Ќ": 0.45, "к": 0.45, "ќ": 0.45, "Р": 0.45, "Ҏ": 0.45, "Б": 0.46, "Ь": 0.44, "ь": 0.44,
    "Е": 0.51, "е": 0.51,
    "Д": 0.54, "д": 0.54, "Ъ": 0.54, "ъ": 0.54,
    "Л": 0.56, "л": 0.56, "Љ": 0.56, "љ": 0.56,
}


def coverage():
    """Return sorted Unicode code points covered by KittyTune Flex Cyrillic."""
    ranges = (
        (0x0400, 0x052F), (0x1C80, 0x1C8A), (0x2DE0, 0x2DFF),
        (0xA640, 0xA69F), (0xFE2E, 0xFE2F), (0x1E030, 0x1E06D),
    )
    return sorted(
        {0x030F, 0x0311, 0x1D2B, 0x1D78, 0x1E08F}
        | {code for start, end in ranges for code in range(start, end + 1)}
    )


def combining(code):
    """Return True if ``code`` is a non-spacing Cyrillic or diacritic combining mark."""
    return (
        code in (0x030F, 0x0311, 0x1E08F)
        or 0x0483 <= code <= 0x0489
        or 0x2DE0 <= code <= 0x2DFF
        or 0xA66F <= code <= 0xA672
        or 0xA674 <= code <= 0xA67D
        or 0xA69E <= code <= 0xA69F
        or 0xFE2E <= code <= 0xFE2F
    )


def is_cap(character):
    """Return True if ``character`` follows uppercase cap height conventions."""
    return character.isupper() or character == "\u1c89"


class Design:
    """Final-coordinate glyph drawing host for a single 6-axis font master."""

    def __init__(self, font, location):
        self.font = font
        self.location = dict(location)
        self.cmap = font.getBestCmap()
        self.metrics = extract_master_metrics(font, self.location)
        self.upright = font.getGlyphSet(location=self.location | {"slnt": 0})
        self.slanted = font.getGlyphSet(location=self.location)
        self.cap_h = self.metrics.cap_h
        self.x_h = self.metrics.x_h
        self.slant = self.metrics.slant
        self.drawings = {}
        self._native_cache = {}
        self._anchors_x = {}

    def adv_letter(self, cap, adv_ratio=1.0, ref="H", left=None, right=None) -> Letter:
        """Create a ``Letter`` matching reference advance width at current master."""
        m = self.metrics
        sb_scale = m.ref_lsb(cap) / (160.0 if cap else 126.0)
        lsb = m.ref_lsb(cap) if left is None else left * sb_scale
        rsb = m.ref_lsb(cap) if right is None else right * sb_scale
        adv = m.ref_adv(ref, cap) * adv_ratio
        width = max(m.stem(cap) * 1.15, adv - lsb - rsb)
        return Letter.from_metrics(m, cap=cap, width=width, left=lsb, right=rsb, ref=ref)

    def letter(self, cap, width=None, left=None, right=None, ref="H") -> Letter:
        """Create a ``Letter`` directly in final master coordinates."""
        return Letter.from_metrics(self.metrics, cap=cap, width=width, left=left, right=right, ref=ref)

    def sb(self, base_sb_400, cap=True) -> float:
        """Scale a baseline-400 sidebearing cleanly to current master."""
        return base_sb_400 * (self.metrics.ref_lsb(cap) / (160.0 if cap else 126.0))

    def native(self, char_or_name, upright=False, scale_x=1.0, scale_y=1.0) -> Outline:
        """Extract a native Google Sans Flex contour cleanly in final master coords."""
        target = (
            HOMOGLYPHS.get(char_or_name, char_or_name)
            if isinstance(char_or_name, str) and len(char_or_name) == 1
            else char_or_name
        )
        key = (target, bool(upright))
        cached = self._native_cache.get(key)
        if cached is None:
            glyphset = self.upright if upright else self.slanted
            cached = native_outline(glyphset, target, cmap=self.cmap)
            self._native_cache[key] = cached
        if scale_x != 1.0 or scale_y != 1.0:
            return cached.transform(sx=scale_x, sy=scale_y)
        return cached.copy()

    def put(self, character, drawing, slanted=False):
        """Store a drawn glyph outline (or Pen) for ``character``."""
        if len(character) != 1:
            raise ValueError(f"put() expects a single character, got {character!r}")
        outline = drawing.outline if isinstance(drawing, Pen) else drawing
        self.drawings[character] = (outline.copy(), bool(slanted))

    def put_case(self, pair, cap, drawing, slanted=False):
        """Store a drawn glyph for ``pair[0]`` (cap) or ``pair[1]`` (lowercase)."""
        self.put(pair[0 if cap else 1], drawing, slanted=slanted)

    def get(self, character, cap=None) -> Outline:
        """Return an upright outline for ``character``, rescaling if ``cap`` mismatches."""
        if character in self.drawings:
            outline, is_slanted = self.drawings[character]
            if is_slanted and self.metrics.slant:
                outline = shear_slnt(outline, -self.metrics.slant, self.metrics.slant_pivot)
            else:
                outline = outline.copy()
        else:
            target = HOMOGLYPHS.get(character, character)
            outline = self.native(target, upright=True)
        if cap is not None and is_cap(character) != cap:
            scale = (self.cap_h / self.x_h) if cap else (self.x_h / self.cap_h)
            outline = outline.transform(sx=scale, sy=scale)
        return outline

    def final(self, character) -> Outline:
        """Return the final master outline, applying native y=x_h/2 slant if needed."""
        if character in self.drawings:
            outline, is_slanted = self.drawings[character]
            if is_slanted:
                return outline.copy()
            pivot = 0.0 if outline.advance == 0 else self.metrics.slant_pivot
            return shear_slnt(outline, self.metrics.slant, pivot)
        target = HOMOGLYPHS.get(character, character)
        return self.native(target, upright=False)

    def set_anchor_x(self, char, x_upright):
        """Record an explicit upright top-anchor X coordinate for ``char``."""
        self._anchors_x[char] = float(x_upright)

    def anchor_x(self, char) -> float | None:
        """Return the final slanted top-anchor X coordinate if registered, else None."""
        if char not in self._anchors_x:
            return None
        x_up = self._anchors_x[char]
        if not self.metrics.slant:
            return x_up
        cap = is_cap(char)
        ref_h = self.cap_h if (cap or char in _ASCENDER_LOWER) else self.x_h
        return x_up + self.metrics.slant * (ref_h - self.metrics.slant_pivot)


def _draw_ruble(d: Design):
    """Draw ₽ directly from native P with full semicircular pill bars and ROND overhang."""
    m = d.metrics
    base = d.native("P", upright=True)
    l_p, _, r_p, _ = base.bounds()
    ext = base.advance * 0.11

    p = d.letter(True, 1)
    p.outline = base.transform(dx=ext)
    p.outline.advance = base.advance + ext

    # Native P lower bowl bar Y span (verified invariant across all 161 masters).
    y_bot = base.contours[0][15][1]
    y_top = base.contours[0][18][1]
    mid_h = y_top - y_bot
    x_in_stem = l_p + ext + m.H_stem * 0.72
    p.bar(
        l_p,
        (y_bot + y_top) * 0.5,
        x_in_stem,
        thickness=mid_h,
        overhang_left=True,
        sharp_corners=(2, 3),
    )

    low_h = min(m.H_bar * 0.88, y_bot * 0.46)
    low_y0 = 0.42 * (y_bot - low_h)
    low_x1 = l_p + ext + m.H_stem + (r_p - l_p) * 0.34
    p.bar(
        l_p,
        low_y0 + low_h * 0.5,
        low_x1,
        thickness=low_h,
        overhang_left=True,
        overhang_right=True,
    )
    d.put("₽", p)


def marks_and_symbols(d: Design):
    """Draw Ruble, thousands sign, Church Slavonic titlos, breathings, and enclosing marks."""
    _draw_ruble(d)

    m = d.metrics
    wd_s = max(0.56, min(1.38, m.n_adv / 1122.0))
    cap_s = m.cap_h / 1432.0
    v_mark = min(m.n_stem, 300.0)
    h_mark = min(m.o_top, 250.0)
    rnd = m.roundness

    p = Pen(Outline(690.0 * wd_s), v_mark, h_mark, rnd, metrics=m)
    p.stroke((150.0 * wd_s, -312.0 * cap_s), (547.0 * wd_s, 809.0 * cap_s), weight=0.83)
    p.stroke((150.0 * wd_s, 125.0 * cap_s), (528.0 * wd_s, -10.0 * cap_s), weight=0.77)
    p.stroke((267.0 * wd_s, 439.0 * cap_s), (647.0 * wd_s, 304.0 * cap_s), weight=0.77)
    d.put("҂", p)

    for char, mode in (
        ("҃", "titlo"), ("҄", "peak"), ("҇", "slope"), ("꙯", "vzmet"),
        ("︮", "left"), ("︯", "right"),
    ):
        p = Pen(Outline(0), v_mark, h_mark, rnd, metrics=m)
        if mode == "titlo":
            p.stroke(
                (-296.0 * wd_s, 200.0),
                ((-210.0 * wd_s, 354.0), (98.0 * wd_s, 163.0), (299.0 * wd_s, 302.0)),
                weight=0.62,
            )
        elif mode == "peak":
            p.stroke(
                (-285.0 * wd_s, 225.0),
                ((-106.0 * wd_s, 153.0), (38.0 * wd_s, 442.0), (171.0 * wd_s, 340.0)),
                ((231.0 * wd_s, 291.0), (246.0 * wd_s, 248.0), (286.0 * wd_s, 214.0)),
                weight=0.58,
            )
        elif mode == "slope":
            p.stroke(
                (-287.0 * wd_s, 235.0),
                ((-167.0 * wd_s, 463.0), (111.0 * wd_s, 196.0), (299.0 * wd_s, 232.0)),
                weight=0.62,
            )
        elif mode == "vzmet":
            p.stroke(
                (-283.0 * wd_s, 192.0),
                ((-249.0 * wd_s, 355.0), (-213.0 * wd_s, 280.0), (0.0, 280.0)),
                ((217.0 * wd_s, 280.0), (244.0 * wd_s, 355.0), (283.0 * wd_s, 192.0)),
                weight=0.61,
            )
        elif mode == "left":
            p.stroke(
                (-279.0 * wd_s, 193.0),
                ((-144.0 * wd_s, 433.0), (120.0 * wd_s, 258.0), (409.0 * wd_s, 297.0)),
                weight=0.62,
            )
        else:
            p.stroke(
                (-409.0 * wd_s, 297.0),
                ((-120.0 * wd_s, 258.0), (144.0 * wd_s, 166.0), (279.0 * wd_s, 410.0)),
                weight=0.62,
            )
        d.put(char, p)

    dot_d = min(m.n_stem * 0.96, 290.0)
    for char, direction in (("҅", 1), ("҆", -1), ("꙽", 1)):
        p = Pen(Outline(0), v_mark, h_mark, rnd, metrics=m)
        p.dot(72.0 * direction * wd_s, 350.0, diameter=dot_d)
        p.stroke(
            (55.0 * direction * wd_s, 350.0),
            ((-98.0 * direction * wd_s, 320.0), (-95.0 * direction * wd_s, 188.0), (98.0 * direction * wd_s, 162.0)),
            weight=0.52,
        )
        if char == "꙽":
            p.outline = p.outline.transform(sx=-1.0, dx=50.0 * wd_s)
        d.put(char, p)

    p = Pen(Outline(0), v_mark, h_mark, rnd, metrics=m)
    p.stroke(
        (-257.0 * wd_s, 350.0),
        ((-235.0 * wd_s, 128.0), (235.0 * wd_s, 128.0), (257.0 * wd_s, 350.0)),
        weight=0.63,
    )
    d.put("꙼", p)

    kavyka = d.get("꙼").transform(dx=365.0 * wd_s, dy=m.x_h * 0.632)
    kavyka.advance = 730.0 * wd_s
    d.put("꙾", kavyka)

    payerok = d.get("꙽").transform(dx=180.0 * wd_s, dy=m.x_h * 0.646)
    payerok.advance = 360.0 * wd_s
    d.put("ꙿ", payerok)

    v_enc = min(m.n_stem, 280.0)
    h_enc = min(m.o_top, 240.0)
    for character in ENCLOSING:
        p = Pen(Outline(0), v_enc, h_enc, rnd, metrics=m)
        if character in "҈҉꙰":
            for step in range(8):
                angle = step * pi / 4.0
                if character == "҈":
                    x, y = 1030.0 * wd_s * cos(angle), 1000.0 * sin(angle)
                    p.stroke(
                        (x - 95.0 * wd_s, y),
                        ((x - 85.0 * wd_s, y + 124.0), (x + 85.0 * wd_s, y + 124.0), (x + 95.0 * wd_s, y)),
                        weight=0.51,
                    )
                elif character == "҉":
                    x, y = cos(angle), sin(angle)
                    p.stroke((x * 854.0 * wd_s, y * 900.0), (x * 1180.0 * wd_s, y * 1160.0), weight=0.53)
                else:
                    x, y = 1040.0 * wd_s * cos(angle), 1000.0 * sin(angle)
                    p.stroke((x - 72.0 * wd_s, y - 93.0), (x + 72.0 * wd_s, y + 93.0), weight=0.48)
                    p.stroke((x + 72.0 * wd_s, y - 93.0), (x - 72.0 * wd_s, y + 93.0), weight=0.48)
        elif character == "꙱":
            for y, direction in ((-994.0, 1.0), (994.0, -1.0)):
                p.stroke(
                    (-997.0 * wd_s, y + 243.0 * direction),
                    (-997.0 * wd_s, y),
                    (997.0 * wd_s, y),
                    (997.0 * wd_s, y + 243.0 * direction),
                    weight=0.55,
                    cap=0.0,
                )
        else:
            for x in (-234.0, 0.0, 234.0):
                p.stroke((x * wd_s, -1124.0), (x * wd_s, -850.0), weight=0.54)
            for x, side in ((-920.0, -1.0), (920.0, 1.0)):
                for y in (-342.0, 0.0, 342.0):
                    p.stroke((x * wd_s, y), ((x + side * 270.0) * wd_s, y), weight=0.54)
            p.stroke((-139.0 * wd_s, 969.0), (139.0 * wd_s, 969.0), weight=0.55)
            p.stroke((0.0, 831.0), (0.0, 1107.0), weight=0.55)
        d.put(character, p)

    # Slavonic asterisk (U+A673) with monotonic wall protection at heavy/condensed masters.
    ast_adv = 780.0 * wd_s
    ast_cx = ast_adv * 0.5
    ast_cy = m.cap_h * 0.72
    rx_in = 215.0 * wd_s
    wall = min(m.o_top * 0.52, rx_in * 0.48, 125.0)
    p = Pen(Outline(ast_adv), m.n_stem, m.o_top, rnd, metrics=m)
    outer, inner = [], []
    for step in range(12):
        angle = step * pi / 6.0
        rad = 320.0 if step % 2 == 0 else 215.0
        outer.append((ast_cx + rad * wd_s * cos(angle), ast_cy + rad * sin(angle)))
        in_x = max(24.0, rad * wd_s - wall)
        in_y = max(24.0, rad - wall)
        inner.append((ast_cx + in_x * cos(angle), ast_cy + in_y * sin(angle)))
    p.polygon(outer, radius=14.0 * rnd)
    p.polygon(inner, radius=10.0 * rnd, hole=True)
    d.put("꙳", p)

    # Native-matched U+030F (double grave) from dual native gravecomb positioned above y=0.
    grave = d.native("gravecomb", upright=True)
    gl, _, gr, _ = grave.bounds()
    g_cx = (gl + gr) * 0.5
    sep = max(85.0, min(200.0, (gr - gl) * 0.45))
    dbl_grave = Outline(0.0)
    dbl_grave.add(grave.transform(dx=-sep - g_cx, dy=-m.x_h))
    dbl_grave.add(grave.transform(dx=sep - g_cx, dy=-m.x_h))
    d.put("\u030f", dbl_grave)

    # Native-matched U+0311 (inverted breve) by reflecting native uni0306 above y=0.
    breve = d.native("uni0306", upright=True)
    bl, bb, br, bt = breve.bounds()
    inv_breve = breve.transform(sy=-1.0, dx=-(bl + br) * 0.5, dy=bb + bt - m.x_h)
    inv_breve.advance = 0.0
    d.put("\u0311", inv_breve)


def accent_letters(d: Design):
    """Build native-matched accented Cyrillic letters (Ё/ё/Ї/ї, Й/й, Ў/ў, Ѓ/ѓ, Ќ/ќ, etc.)."""
    m = d.metrics

    # Exact Google Sans Flex optical dots, slant curves, and ROND=100 for Ё/ё/Ї/ї.
    for cyr, lat in (("Ё", "Ë"), ("ё", "ë"), ("Ї", "Ï"), ("ї", "ï")):
        d.put(cyr, d.native(lat, upright=False), slanted=True)

    for code in coverage():
        character = chr(code)
        if character in "ЁёЇї":
            continue
        parts = decomposition(character).split()
        if len(parts) != 2 or parts[0].startswith("<"):
            continue
        base, accent = (chr(int(part, 16)) for part in parts)
        if base == "і":
            base = "ı"

        cap = is_cap(character)
        glyph = d.get(base, cap)
        l, _, r, top = glyph.bounds()

        if base in d._anchors_x:
            x = d._anchors_x[base]
        else:
            frac = _OPTICAL_ABOVE_FRACTION.get(base, 0.50)
            x = l + frac * (r - l)

        if accent == "\u030f":
            mark = d.get("\u030f")
            ml, mb, mr, mt = mark.bounds()
            ref_h = d.cap_h if cap else d.x_h
            dy = max(ref_h, top + 20.0)
            # Keep tall composites (e.g. Ѷ) safely within font ascender limit without colliding.
            if dy + mt > 2120.0:
                min_clear = (top + max(60.0, min(m.H_bar * 0.25, 110.0))) - mb
                dy = max(min(dy, min_clear), min(dy, 2120.0 - mt))
            dx = x - (ml + mr) * 0.5
        else:
            mark = d.native(accent, upright=True)
            ml, mb, mr, mt = mark.bounds()
            mark_w = mr - ml
            if cap:
                dy = max(d.cap_h - d.x_h, top - d.x_h - max(m.cap_ov, m.rond_ov_cap))
            else:
                dy = max(0.0, top - d.x_h - max(m.low_ov, m.rond_ov_low))
            if dy + mt > 2120.0:
                min_clear = (top + max(60.0, min(m.H_bar * 0.25, 110.0))) - mb
                dy = max(min(dy, min_clear), min(dy, 2120.0 - mt))
            # Match Google Sans Flex optical shift on directional acute / grave marks.
            bias = 0.0
            if accent == "\u0301":
                bias = min(mark_w * 0.18, (r - l) * 0.14)
            elif accent == "\u0300":
                bias = -min(mark_w * 0.18, (r - l) * 0.14)
            dx = x + bias - (ml + mr) * 0.5

        glyph.add(mark.transform(dx=dx, dy=dy))
        d.put(character, glyph)

    # Ҋ / ҋ: Short I with clean right-stem Ц/ц-style Google Sans descender tail.
    for char, base in (("Ҋ", "Й"), ("ҋ", "й")):
        cap = is_cap(char)
        p = d.letter(cap, 1)
        p.outline = d.get(base)
        _, _, r, _ = p.outline.bounds()
        sw = m.stem(cap)
        bh = m.bar(cap)
        ext_r = sw * 0.18
        tail_w = sw * 0.78
        shelf_h = min(bh * 0.80, sw * 0.85)
        depth = (255.0 if cap else 195.0) + m.rond_ov(cap)

        p.box(
            r - sw * 0.65,
            0.0,
            r + ext_r,
            shelf_h,
            corner_radii=(None, None, min(sw * 0.55 * m.roundness, shelf_h * 0.48), None),
        )
        p.box(
            r + ext_r - tail_w,
            -depth,
            r + ext_r,
            shelf_h * 0.65,
            corner_radii=(
                tail_w * 0.5 * m.roundness,
                None,
                None,
                tail_w * 0.5 * m.roundness,
            ),
        )
        p.outline.advance += ext_r
        d.put(char, p)


def scaled_letter(outline, scale, baseline=0.0, top=None, mark=False, clear_desc=False):
    """Scale an outline cleanly for superscript/subscript modifiers or combining marks."""
    l, b, r, high = outline.bounds()
    dx = -(l + r) * scale * 0.5 if mark else 0.0
    if top is not None:
        dy = top - high * scale
    elif clear_desc:
        dy = max(baseline, baseline - 20.0 - min(0.0, b) * scale)
    else:
        dy = baseline
    result = outline.transform(sx=scale, sy=scale, dx=dx, dy=dy)
    if mark:
        result.advance = 0.0
    return result


def raised_letters(d: Design):
    """Draw Cyrillic superscript/subscript modifiers and combining letter marks."""
    m = d.metrics
    sup_top = m.cap_h * 1.006
    mod_top = m.cap_h * 1.020
    sub_base = max(-295.0, m.desc_y * 0.68)

    for target, source in (("ꚜ", "ъ"), ("ꚝ", "ь"), ("ᵸ", "н")):
        d.put(target, scaled_letter(d.get(source), 0.61, top=sup_top))

    for code, source in enumerate(MODIFIERS, 0x1E030):
        subscript = 0x1E051 <= code <= 0x1E06A
        d.put(
            chr(code),
            scaled_letter(
                d.get(source),
                0.58,
                baseline=sub_base if subscript else 0.0,
                top=None if subscript else mod_top,
            ),
        )

    s = d.get("с")
    t = d.get("т")
    es_te = s.copy()
    es_te.add(t.transform(dx=s.advance * 0.97))

    for start, sources in (
        (0x2DE0, COMBINING_A),
        (0xA674, "єиїуъыьѡ"),
        (0xA69E, "фѥ"),
        (0x1E08F, "і"),
    ):
        for code, source in enumerate(sources, start):
            outline = es_te if source == "\0" else d.get(source)
            d.put(chr(code), scaled_letter(outline, 0.49, baseline=190.0, mark=True, clear_desc=True))


def draw_design(font, location):
    """Assemble the complete Cyrillic glyph design for ``font`` at ``location``."""
    from cyrillic_arch import draw_arch_letters
    from cyrillic_bowl import draw_bowl_letters
    from cyrillic_diag import draw_diag_letters

    design = Design(font, location)
    draw_arch_letters(design)
    draw_diag_letters(design)
    draw_bowl_letters(design)
    marks_and_symbols(design)
    accent_letters(design)
    raised_letters(design)
    return design
