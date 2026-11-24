# SPDX-License-Identifier: OFL-1.1
"""TrueType contours for the six-axis KittyTune Flex design."""

from dataclasses import dataclass, field
from math import cos, hypot, pi, sin

from fontTools.pens.recordingPen import DecomposingRecordingPointPen


@dataclass
class Outline:
    advance: float
    contours: list = field(default_factory=list)

    def bounds(self):
        xs, ys = zip(*[(x, y) for contour in self.contours for x, y, _ in contour])
        return min(xs), min(ys), max(xs), max(ys)

    def transform(self, sx=1, sy=1, dx=0, dy=0, shear=0):
        return Outline(abs(sx) * self.advance, [
            [(sx * x + shear * y + dx, sy * y + dy, on) for x, y, on in
             (contour if sx * sy > 0 else reversed(contour))]
            for contour in self.contours
        ])

    def add(self, other):
        self.contours.extend(other.contours)


def source_outline(glyphs, name):
    pen = DecomposingRecordingPointPen(glyphs)
    glyphs[name].drawPoints(pen)
    outline = Outline(glyphs[name].width)
    for operation, args, _ in pen.value:
        if operation == "beginPath":
            contour = []
        elif operation == "addPoint":
            (x, y), segment = args[:2]
            contour.append((x, y, segment is not None))
        elif operation == "endPath":
            outline.contours.append(contour)
    return outline


def offset(a, b, distance):
    dx, dy = b[0] - a[0], b[1] - a[1]
    fraction = distance / max(hypot(dx, dy), 1)
    return a[0] + dx * fraction, a[1] + dy * fraction


def normal(dx, dy):
    length = hypot(dx, dy) or 1
    return -dy / length, dx / length


class Pen:
    def __init__(self, outline, vertical, horizontal, roundness):
        self.outline = outline
        self.v = vertical
        self.h = horizontal
        self.roundness = roundness
        self.radius = vertical * roundness * .46

    def polygon(self, vertices, radius=None, hole=False):
        """The same on/off-curve points describe each corner at every master."""
        vertices = list(vertices)
        radius = self.radius if radius is None else radius
        area = sum(x * yy - xx * y for (x, y), (xx, yy) in
                   zip(vertices, vertices[1:] + vertices[:1]))
        if (area > 0) != hole:
            vertices.reverse()
        contour = []
        for i, (x, y) in enumerate(vertices):
            before = vertices[i - 1]
            after = vertices[(i + 1) % len(vertices)]
            r = min(radius, .45 * hypot(x - before[0], y - before[1]),
                    .45 * hypot(x - after[0], y - after[1]))
            contour.extend([
                (*offset((x, y), before, r), True),
                (x, y, False),
                (*offset((x, y), after, r), True),
            ])
        self.outline.contours.append(contour)

    def box(self, x0, y0, x1, y1, radius=None):
        self.polygon([(x0, y0), (x0, y1), (x1, y1), (x1, y0)], radius)

    def oval(self, x0, y0, x1, y1, ring=True, wall=1):
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        rx, ry = (x1 - x0) / 2, (y1 - y0) / 2

        def ellipse(rx, ry, direction):
            contour = []
            for step in range(8):
                start = direction * step * pi / 4
                control = start + direction * pi / 8
                contour.extend([
                    (cx + rx * cos(start), cy + ry * sin(start), True),
                    (cx + rx * cos(control) / cos(pi / 8),
                     cy + ry * sin(control) / cos(pi / 8), False),
                ])
            self.outline.contours.append(contour)

        ellipse(rx, ry, -1)
        if ring:
            ellipse(max(rx * .22, rx - wall * self.v),
                    max(ry * .22, ry - wall * self.h), 1)

    def dot(self, x, y, diameter=None):
        r = (diameter if diameter is not None else self.v * .96 + 58) / 2
        self.oval(x - r, y - r, x + r, y + r, ring=False)

    def stroke(self, start, *segments, weight=1, cap=None):
        """Draw a line or cubic centerline with elliptical stroke contrast."""
        cap = self.roundness if cap is None else cap
        left, right = [], []
        a = start
        for segment in segments:
            if len(segment) == 2:
                b = segment
                dx, dy = b[0] - a[0], b[1] - a[1]
                nx, ny = normal(dx, dy)
                ex, ey = nx * self.v * weight / 2, ny * self.h * weight / 2
                ends = [(a[0] + ex, a[1] + ey, True), (b[0] + ex, b[1] + ey, True)]
                other = [(a[0] - ex, a[1] - ey, True), (b[0] - ex, b[1] - ey, True)]
            else:
                c1, c2, b = segment
                ends, other = [], []
                samples = [[], []]
                for step in range(9):
                    t = step / 8
                    u = 1 - t
                    x, y = [u ** 3 * aa + 3 * u * u * t * cc1 + 3 * u * t * t * cc2 + t ** 3 * bb
                            for aa, cc1, cc2, bb in zip(a, c1, c2, b)]
                    dx, dy = [u * u * (cc1 - aa) + 2 * u * t * (cc2 - cc1) + t * t * (bb - cc2)
                              for aa, cc1, cc2, bb in zip(a, c1, c2, b)]
                    nx, ny = normal(dx, dy)
                    ex, ey = nx * self.v * weight / 2, ny * self.h * weight / 2
                    samples[0].append((x + ex, y + ey))
                    samples[1].append((x - ex, y - ey))
                for target, points in zip((ends, other), samples):
                    target.append((*points[0], True))
                    for i in range(0, 8, 2):
                        x0, y0 = points[i]
                        xm, ym = points[i + 1]
                        x1, y1 = points[i + 2]
                        target.extend([(2 * xm - (x0 + x1) / 2, 2 * ym - (y0 + y1) / 2, False),
                                       (x1, y1, True)])
            left.extend(ends)
            right.extend(other)
            a = b

        def end_cap(left, right, distance):
            lx, ly, _ = left
            rx, ry, _ = right
            dx, dy = lx - rx, ly - ry
            return ((lx + rx) / 2 + dy * distance, (ly + ry) / 2 - dx * distance, False)

        self.outline.contours.append(left + [end_cap(left[-1], right[-1], cap)] +
                                     right[::-1] + [end_cap(right[0], left[0], cap)])


class Letter(Pen):
    """Global weight and proportions with glyph-specific bearings and widths."""

    def __init__(self, profile, width, left, right, growth):
        height, vertical, horizontal, spacing, weight, roundness = profile
        left *= spacing
        right *= spacing
        self.left = left
        self.width = width + growth * (weight - 170)
        self.right = left + self.width
        self.mid = left + self.width / 2
        self.top = height
        self.bottom = -vertical * roundness * .048
        super().__init__(Outline(self.right + right), vertical, horizontal, roundness)

    def stem(self, x, bottom=None, top=None):
        self.box(x, self.bottom if bottom is None else bottom,
                 x + self.v, self.top if top is None else top)

    def bar(self, x0, y, x1, thickness=None):
        half = (self.h if thickness is None else thickness) / 2
        self.box(x0, y - half, x1, y + half)

    def diagonal(self, x0, y0, x1, y1, weight=1):
        self.stroke((x0, y0), (x1, y1), weight=weight)

    def bowl(self, left, bottom, right, top):
        """A straight spine with a full, closed bowl to its right."""
        width = right - left
        height = top - bottom
        radius = min(self.radius, width * .15, height * .24)
        middle = (top + bottom) / 2
        edge = left + min(self.v, width * .44)
        wall = min(self.v, width * .31, max(width * .14, width * .73 - self.v))
        horizontal = min(self.h, height * .30)
        shoulder = left + width * .55
        self.outline.contours.append([
            (left, bottom + radius, True), (left, top - radius, True),
            (left, top, False), (left + radius, top, True),
            (shoulder, top, True), (right, top, False), (right, middle, True),
            (right, bottom, False), (shoulder, bottom, True),
            (left + radius, bottom, True), (left, bottom, False),
        ])
        self.outline.contours.append([
            (edge, bottom + horizontal, True), (shoulder, bottom + horizontal, True),
            (right - wall, bottom + horizontal, False), (right - wall, middle, True),
            (right - wall, top - horizontal, False), (shoulder, top - horizontal, True),
            (edge, top - horizontal, True),
        ])
