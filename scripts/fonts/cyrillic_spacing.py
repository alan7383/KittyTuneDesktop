# SPDX-License-Identifier: OFL-1.1
"""Fit Cyrillic text pairs to the space around the native n and o outlines."""

from math import ceil, floor, inf

from fontTools.misc.bezierTools import solveQuadratic
from fontTools.misc.fixedTools import otRound
from fontTools.pens.basePen import BasePen
from fontTools.pens.pointPen import PointToSegmentPen


_LOWERCASE = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя"
_FIRST_ROW, _LAST_ROW = -20, 64
_TEXT_ROWS = slice(4 - _FIRST_ROW, 29 - _FIRST_ROW)


class _EdgePen(BasePen):
    """Record the outer ink edges at evenly spaced heights through a glyph."""

    def __init__(self, step):
        super().__init__(None)
        self.step = step
        count = _LAST_ROW - _FIRST_ROW + 1
        self.left = [inf] * count
        self.right = [-inf] * count

    def _moveTo(self, point):
        self.start = point

    def _rows(self, *heights):
        low = max(_FIRST_ROW, ceil(min(heights) / self.step))
        high = min(_LAST_ROW, floor(max(heights) / self.step))
        return range(low, high + 1)

    def _edge(self, row, x):
        index = row - _FIRST_ROW
        self.left[index] = min(self.left[index], x)
        self.right[index] = max(self.right[index], x)

    def _lineTo(self, point):
        x0, y0 = self._getCurrentPoint()
        x1, y1 = point
        if y0 == y1:
            return
        slope = (x1 - x0) / (y1 - y0)
        for row in self._rows(y0, y1):
            self._edge(row, x0 + (row * self.step - y0) * slope)

    def _qCurveToOne(self, control, end):
        x0, y0 = self._getCurrentPoint()
        x1, y1 = control
        x2, y2 = end
        if y0 == y1 == y2:
            return
        a = y0 - 2.0 * y1 + y2
        b = 2.0 * (y1 - y0)
        for row in self._rows(y0, y1, y2):
            for t in solveQuadratic(a, b, y0 - row * self.step):
                if 0.0 <= t <= 1.0:
                    u = 1.0 - t
                    self._edge(row, u * u * x0 + 2.0 * u * t * x1 + t * t * x2)

    def _closePath(self):
        self._lineTo(self.start)


def _profile(outline, step):
    edges = _EdgePen(step)
    pen = PointToSegmentPen(edges)
    for contour in outline.contours:
        pen.beginPath()
        for x, y, on_curve in contour:
            pen.addPoint((x, y), segmentType="qcurve" if on_curve else None)
        pen.endPath()
    # Store bearing distances so the space between glyphs is the sum of their edges.
    return edges.left, [outline.advance - x for x in edges.right]


def _measure_gap(left, right):
    gaps = [after + before for after, before in zip(left[1], right[0])]
    # Give closer contours more weight than open corners in the visible text band.
    text = sorted(gap for gap in gaps[_TEXT_ROWS] if gap < inf)
    if not text:
        return min(gaps), inf
    last = len(text) - 1
    average = 0.45 * text[round(0.20 * last)] + 0.40 * text[round(0.45 * last)] + 0.15 * text[round(0.70 * last)]
    return min(gaps), average


def fit_cyrillic_text(designs, glyphs, drawn, inherited):
    """Return per-master adjustments to the inherited Russian text spacing."""
    characters = _LOWERCASE + _LOWERCASE.upper()
    names = {character: glyphs[ord(character)] for character in characters}
    # Pairs of shared Latin outlines retain their native metrics and kerning.
    pairs = {}
    for left in characters:
        for right in _LOWERCASE:
            names_pair = names[left], names[right]
            if names_pair[0] in drawn or names_pair[1] in drawn:
                pairs[(left, right)] = (names_pair, inherited(*names_pair), [])

    for index, design in enumerate(designs):
        m = design.metrics
        step = m.x_h / 32.0
        profiles = {character: _profile(design.final(character), step) for character in characters}
        n = _profile(design.native("n"), step)
        o = _profile(design.native("o"), step)
        reference = [_measure_gap(left, right)[1] for left, right in ((n, n), (n, o), (o, n), (o, o))]
        low = min(reference) * 0.99
        high = max(reference) * 1.14
        clearance = max(50.0, m.n_lsb * 0.47)
        display = max(0.0, (m.opsz - 18.0) / 126.0)
        strength = 0.72 * (1.0 - 0.52 * display)
        for (left, right), (_, baseline, values) in pairs.items():
            original = baseline[index]
            nearest, optical = _measure_gap(profiles[left], profiles[right])
            nearest += original
            optical += original
            adjustment = 0.0
            if nearest < clearance and (left in "жд" or right in "жд"):
                adjustment = min(clearance - nearest, m.n_adv * 0.06)
            elif optical > high and nearest < inf:
                # Keep the existing clearance when a pair is already tighter than the floor.
                available = max(0.0, nearest - clearance)
                adjustment = -min((optical - high) * strength, available, m.n_adv * 0.115)
            elif optical < low:
                adjustment = min((low - optical) * strength, m.n_adv * 0.023)
            values.append(otRound(adjustment))

    return {
        names_pair: values
        for names_pair, _, values in pairs.values()
        if any(abs(value) >= 10 for value in values)
    }
