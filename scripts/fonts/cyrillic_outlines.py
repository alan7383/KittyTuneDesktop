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
        self.radius = vertical * roundness * .50

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
            r = min(radius, .495 * hypot(x - before[0], y - before[1]),
                    .495 * hypot(x - after[0], y - after[1]))
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
            ox, oy = dy * .5 * distance, -dx * .5 * distance
            return [
                (lx + ox, ly + oy, False),
                ((lx + rx) / 2 + ox, (ly + ry) / 2 + oy, True),
                (rx + ox, ry + oy, False),
            ]

        self.outline.contours.append(left + end_cap(left[-1], right[-1], cap) +
                                     right[::-1] + end_cap(right[0], left[0], cap))


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
        overshoot = vertical * roundness * .054
        self.curve_ov = 28 + 9 * min(max(vertical / 400, 0), 1)
        self.tip = height + overshoot
        self.bottom = -overshoot
        super().__init__(Outline(self.right + right), vertical, horizontal, roundness)

    def stem(self, x, bottom=None, top=None, weight=1, top_r=None, bottom_r=None):
        w = self.v * weight
        y0 = self.bottom if bottom is None else bottom
        y1 = self.tip if top is None else top
        default_r = w * self.roundness * .50
        if top_r is None and bottom_r is None:
            self.box(x, y0, x + w, y1, radius=default_r)
            return
        rt = default_r if top_r is None else top_r
        rb = default_r if bottom_r is None else bottom_r
        radii = (rb, rt, rt, rb)
        verts = [(x, y0), (x, y1), (x + w, y1), (x + w, y0)]
        contour = []
        for i, (vx, vy) in enumerate(verts):
            before = verts[i - 1]
            after = verts[(i + 1) % 4]
            r = min(radii[i], .495 * hypot(vx - before[0], vy - before[1]),
                    .495 * hypot(vx - after[0], vy - after[1]))
            def step(pt):
                dx, dy = pt[0] - vx, pt[1] - vy
                d = hypot(dx, dy)
                return (vx + dx * r / d, vy + dy * r / d) if d else (vx, vy)
            contour.extend([(*step(before), True), (vx, vy, False), (*step(after), True)])
        self.outline.contours.append(contour)

    def bar(self, x0, y, x1, thickness=None, radius=None):
        half = (self.h if thickness is None else thickness) / 2
        r = half * self.roundness if radius is None else radius
        self.box(x0, y - half, x1, y + half, radius=r)

    def diagonal(self, x0, y0, x1, y1, weight=1, cap=None):
        self.stroke((x0, y0), (x1, y1), weight=weight, cap=cap)

    def bowl(self, left, bottom, right, top, bar_scale=1.0, wall_scale=1.0):
        """A straight spine with a circular 2-handle Google Sans bowl to its right."""
        width = right - left
        height = top - bottom
        corner_r = min(self.v * self.roundness * .34, width * .18, height * .26)
        middle = (top + bottom) / 2
        edge = left + min(self.v * .84 * wall_scale, width * .38)
        wall = min(self.v * .96 * wall_scale, width * .30, max(width * .15, width * .72 - self.v * wall_scale))
        horizontal = min(self.h * bar_scale, height * .26)
        shoulder = left + width * .38
        bowl_ov = self.v * .035 * self.roundness + 8
        b_out = bottom - bowl_ov * .45
        t_out = top + bowl_ov * .45
        self.outline.contours.append([
            (left, bottom + corner_r, True), (left, top - corner_r, True),
            (left, top, False), (left + corner_r, top, True),
            (shoulder, t_out, True),
            (shoulder + (right - shoulder) * .42, t_out, False),
            (right, middle + (t_out - middle) * .54, False),
            (right, middle, True),
            (right, middle - (middle - b_out) * .54, False),
            (shoulder + (right - shoulder) * .42, b_out, False),
            (shoulder, b_out, True),
            (left + corner_r, bottom, True), (left, bottom, False),
        ])
        in_r = right - wall
        in_b = bottom + horizontal
        in_t = top - horizontal
        sh_in = min(shoulder, edge + max(0.0, (in_r - edge) * .42))
        self.outline.contours.append([
            (edge, in_b, True),
            (sh_in, in_b, True),
            (sh_in + (in_r - sh_in) * .44, in_b, False),
            (in_r, middle - (middle - in_b) * .54, False),
            (in_r, middle, True),
            (in_r, middle + (in_t - middle) * .54, False),
            (sh_in + (in_r - sh_in) * .44, in_t, False),
            (sh_in, in_t, True),
            (edge, in_t, True),
        ])
