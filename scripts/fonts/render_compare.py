# SPDX-License-Identifier: OFL-1.1
"""Render side-by-side Cyrillic vs native Latin Google Sans Flex comparison sheets.

Uses Skiko (Skia + HarfBuzz, identical to KittyTune Desktop) when available via
the local Gradle cache and JDK 17+, with a Pillow FreeType fallback. Verifies
that variable font axes actually produce distinct glyph shapes and advances.
"""

from __future__ import annotations

import argparse
import io
import os
import subprocess
import sys
import tempfile
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
from fontTools.ttLib import TTFont
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_FONT = ROOT / "src/main/resources/fonts/kittytune_flex.ttf"

UPPER_TOKENS = (
    "HАH HБH OВO HГH HҐH HДH HЕH HЁH HЖH HЗH HИH HЙH HКH HЛH HМH HНH "
    "OОO HПH HРH OСO HТH YУY OФO HХH HЦH HЧH HШH HЩH HЪH HЫH HЬH "
    "OЭO HЮH HЯH OЄO HІH HЇH YЎY HЂH HЉH HЊH HЋH HЏH OӨO YҮY H₽H"
).split()

LOWER_TOKENS = (
    "nаn nбn oвo nгn nґn nдn nеn nёn nжn oзo nиn nйn nкn nлn nмn nнn "
    "oоo nпn nрn oсo nтn yуy oфo nхn nцn nчn nшn nщn nъn nыn nьn "
    "oэo nюn nяn oєo nіn nїn yўy nђn nљn nњn nћn nџn oөo yүy"
).split()

STRESS_LINES = [
    "Привет Google Sans",
    "Музыка KittyTune",
    "Жизнь Бязь Щука Эхо",
    "Дёготь Фёдор Юлия",
]

EXTENDED_MODERN = "Ғғ Ҙҙ Ққ Ңң Үү Ұұ Һһ Әә Өө Җҗ Ӄӄ Ӈӈ Ӌӌ Ӏӏ Ѓѓ Ќќ Ѐѐ Ѝѝ Ӑӑ Ӓӓ Ӗӗ Ӯӯ Ӱӱ Ӳӳ Ӵӵ Ӹӹ"
HISTORICAL_SAMPLE = "Ѣѣ Ѳѳ Ѵѵ Ѧѧ Ѫѫ Ѯѯ Ѱѱ Ҁҁ Ҩҩ Ҿҿ Ԁԁ Ԑԑ Ԛԛ Ԝԝ Ꙁꙁ Ꙋꙋ Ꙑꙑ Ꙗꙗ Ꙩꙩ Ꙭꙭ ꙮ Ꚁꚁ Ꚉꚉ Ꚛꚛ ᲀᲁᲂᲃᲄᲅᲆᲇᲈ"
COMBINING_SAMPLE = "А\u030F А\u0311 А\u0483 д\u2DE3 а\u2DF6 е\u2DF7 о\u0488 с\u0489 о\uA670 с\uA671"

EXTENDED_GROUPS = [
    ("Cyrillic Extended 0400-04FF", "ЀЃЌЍѐѓќѝ ҊҋҌҍҎҏ ҒғҔҕҖҗҘҙ ҚқҜҝҞҟҠҡ ҢңҤҥҦҧҨҩ ҪҫҬҭҮүҰұ ҲҳҴҵҶҷҸҹ ҺһҼҽҾҿӀӏ ӐӑӒӓӔӕӖӗ ӘәӚӛӜӝӞӟ ӠӡӢӣӤӥӦӧ ӨөӪӫӬӭӮӯ ӰӱӲӳӴӵӶӷ ӸӹӺӻӼӽӾӿ"),
    ("Supplement & Historic", "ԀԁԂԃԄԅԆԇ ԈԉԊԋԌԍԎԏ ԐԑԒԓԔԕԖԗ ԘԙԚԛԜԝԞԟ ԠԡԢԣԤԥԦԧ ԨԩԪԫԬԭԮԯ ѢѣѲѳѴѵѶѷ ѸѹѺѻѼѽѾѿ Ҁҁ҂"),
    ("Extended-A/B/C", "ᲀᲁᲂᲃᲄᲅᲆᲇᲈᲉᲊ ᴫᵸ ꙀꙁꙂꙃꙄꙅꙆꙇ ꙈꙉꙊꙋꙌꙍꙎꙏ ꙐꙑꙒꙓꙔꙕꙖꙗ ꙘꙙꙚꙛꙜꙝꙞꙟ ꙠꙡꙢꙣꙤꙥꙦꙧ ꙨꙩꙪꙫꙬꙭꙮ꙳"),
    ("Extended-B Abkhaz & Modifiers", "ꚀꚁꚂꚃꚄꚅꚆꚇ ꚈꚉꚊꚋꚌꚍꚎꚏ ꚐꚑꚒꚓꚔꚕꚖꚗ ꚘꚙꚚꚛꚜꚝ 𞀰𞀱𞀲𞀳𞀴𞀵𞀶𞀷 𞀸𞀹𞀺𞀻𞀼𞀽𞀾𞀿"),
    ("Combining & Titlo Marks", "А\u030F А\u0311 А\u0483 А\u0484 А\u0485 А\u0486 А\u0487 о\u0488 с\u0489 б\u2DE0 в\u2DE1 г\u2DE2 д\u2DE3 ж\u2DE4 з\u2DE5 к\u2DE6 л\u2DE7 м\u2DE8 н\u2DE9 о\u2DEA п\u2DEB р\u2DEC с\u2DED т\u2DEE х\u2DEF"),
    ("Combining Extra & Enclosing", "ц\u2DF0 ч\u2DF1 ш\u2DF2 щ\u2DF3 ѳ\u2DF4 т\u2DF5 а\u2DF6 е\u2DF7 ꙋ\u2DF9 ѣ\u2DFA ю\u2DFB ѧ\u2DFD о\uA66F о\uA670 о\uA671 о\uA672 а\uA674 о\uA67C а\uFE2E б\uFE2F а\U0001E08F"),
]

# Design metrics in 2000 UPM from Google Sans Flex
UPM = 2000.0
CAP_H = 1432.0
X_H = 1020.0
OVERSHOOT = 32.0
DESC_Y = -432.0

SKIA_RUNNER_JAVA = r'''
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.jetbrains.skia.*;
import org.jetbrains.skia.shaper.Shaper;

public class SkiaBatchRenderer {
    private static final HexFormat HEX = HexFormat.of();

    private static String decodeHex(String s) {
        return s.isEmpty() ? "" : new String(HEX.parseHex(s), StandardCharsets.UTF_8);
    }

    private static Typeface cloneForAxes(Typeface base, String axesSpec, Map<String, Typeface> cache) {
        Typeface cached = cache.get(axesSpec);
        if (cached != null) return cached;
        String[] pairs = axesSpec.split(",");
        FontVariation[] vars = new FontVariation[pairs.length];
        for (int i = 0; i < pairs.length; i++) {
            String[] kv = pairs[i].split("=");
            vars[i] = new FontVariation(kv[0], Float.parseFloat(kv[1]));
        }
        Typeface cloned = base.makeClone(vars, 0);
        cache.put(axesSpec, cloned);
        return cloned;
    }

    public static void main(String[] args) throws Exception {
        Path fontPath = Path.of(args[0]);
        Path manifestPath = Path.of(args[1]);
        byte[] fontBytes = Files.readAllBytes(fontPath);
        try (Data data = Data.Companion.makeFromBytes(fontBytes, 0, fontBytes.length);
             Typeface base = FontMgr.Companion.getDefault().makeFromData(data, 0);
             Shaper shaper = Shaper.Companion.make();
             Paint paint = new Paint();
             BufferedReader reader = Files.newBufferedReader(manifestPath, StandardCharsets.UTF_8)) {
            paint.setAntiAlias(true);
            Map<String, Typeface> typefaceCache = new HashMap<>();
            Surface surface = null;
            Canvas canvas = null;
            Path outPng = null;

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                String[] parts = line.split("\t", -1);
                String cmd = parts[0];
                switch (cmd) {
                    case "CANVAS" -> {
                        int w = Integer.parseInt(parts[1]);
                        int h = Integer.parseInt(parts[2]);
                        int bg = (int) Long.parseLong(parts[3], 16);
                        outPng = Path.of(decodeHex(parts[4]));
                        if (surface != null) surface.close();
                        surface = Surface.Companion.makeRasterN32Premul(w, h);
                        canvas = surface.getCanvas();
                        canvas.clear(bg);
                    }
                    case "RECT" -> {
                        float x = Float.parseFloat(parts[1]);
                        float y = Float.parseFloat(parts[2]);
                        float w = Float.parseFloat(parts[3]);
                        float h = Float.parseFloat(parts[4]);
                        int fill = (int) Long.parseLong(parts[5], 16);
                        int stroke = (int) Long.parseLong(parts[6], 16);
                        float strokeW = Float.parseFloat(parts[7]);
                        if ((fill >>> 24) != 0) {
                            paint.setMode(PaintMode.FILL);
                            paint.setColor(fill);
                            canvas.drawRect(Rect.makeXYWH(x, y, w, h), paint);
                        }
                        if ((stroke >>> 24) != 0 && strokeW > 0f) {
                            paint.setMode(PaintMode.STROKE);
                            paint.setStrokeWidth(strokeW);
                            paint.setColor(stroke);
                            canvas.drawRect(Rect.makeXYWH(x, y, w, h), paint);
                        }
                    }
                    case "LINE" -> {
                        float x0 = Float.parseFloat(parts[1]);
                        float y0 = Float.parseFloat(parts[2]);
                        float x1 = Float.parseFloat(parts[3]);
                        float y1 = Float.parseFloat(parts[4]);
                        int color = (int) Long.parseLong(parts[5], 16);
                        float strokeW = Float.parseFloat(parts[6]);
                        paint.setMode(PaintMode.STROKE);
                        paint.setStrokeWidth(strokeW);
                        paint.setColor(color);
                        canvas.drawLine(x0, y0, x1, y1, paint);
                    }
                    case "TEXT" -> {
                        String text = decodeHex(parts[1]);
                        float x = Float.parseFloat(parts[2]);
                        float y = Float.parseFloat(parts[3]);
                        float size = Float.parseFloat(parts[4]);
                        int color = (int) Long.parseLong(parts[5], 16);
                        String align = parts[6];
                        String axesSpec = parts[7];
                        Typeface tf = cloneForAxes(base, axesSpec, typefaceCache);
                        try (Font font = new Font(tf, size)) {
                            font.setEdging(FontEdging.ANTI_ALIAS);
                            font.setSubpixel(true);
                            try (TextLine tl = shaper.shapeLine(text, font)) {
                                paint.setMode(PaintMode.FILL);
                                paint.setColor(color);
                                float drawX = x;
                                if ("center".equals(align)) {
                                    drawX = x - tl.getWidth() / 2f;
                                } else if ("right".equals(align)) {
                                    drawX = x - tl.getWidth();
                                }
                                canvas.drawTextLine(tl, drawX, y, paint);
                            }
                        }
                    }
                    case "SAVE" -> {
                        savePng(surface, outPng);
                        surface.close();
                        surface = null;
                        canvas = null;
                    }
                    case "PROBE" -> {
                        String key = parts[1];
                        String text = decodeHex(parts[2]);
                        float size = Float.parseFloat(parts[3]);
                        String axesSpec = parts[4];
                        int pw = Integer.parseInt(parts[5]);
                        int ph = Integer.parseInt(parts[6]);
                        Path probeOut = Path.of(decodeHex(parts[7]));
                        Typeface tf = cloneForAxes(base, axesSpec, typefaceCache);
                        try (Surface ps = Surface.Companion.makeRasterN32Premul(pw, ph);
                             Font font = new Font(tf, size)) {
                            font.setEdging(FontEdging.ANTI_ALIAS);
                            font.setSubpixel(true);
                            try (TextLine tl = shaper.shapeLine(text, font)) {
                                Canvas pc = ps.getCanvas();
                                pc.clear(0xFFFFFFFF);
                                paint.setMode(PaintMode.FILL);
                                paint.setColor(0xFF000000);
                                pc.drawTextLine(tl, 12f, ph * 0.72f, paint);
                                savePng(ps, probeOut);
                                System.out.printf(Locale.ROOT, "PROBE\t%s\t%.3f%n", key, tl.getWidth());
                            }
                        }
                    }
                    default -> throw new IllegalArgumentException("Unknown cmd: " + cmd);
                }
            }
            if (surface != null) surface.close();
            for (Typeface tf : typefaceCache.values()) {
                tf.close();
            }
        }
    }

    private static void savePng(Surface surface, Path outPng) throws Exception {
        try (Image img = surface.makeImageSnapshot();
             Data png = img.encodeToData(EncodedImageFormat.PNG, 100, 6)) {
            if (outPng.getParent() != null) {
                Files.createDirectories(outPng.getParent());
            }
            Files.write(outPng, png.getBytes());
        }
    }
}
'''


def find_skia_toolchain():
    """Locate JDK 17+ and Skiko runtime jars in the local Gradle cache."""
    home = Path.home()
    java_exe = None
    for pattern in ("*/bin/java.exe", "*/*/bin/java.exe", "*/bin/java", "*/*/bin/java"):
        candidates = sorted((home / ".gradle" / "jdks").glob(pattern))
        # Prefer JDK 25 or 17
        for c in candidates:
            if "25" in str(c) or "17" in str(c) or "21" in str(c):
                java_exe = c
                break
        if java_exe:
            break
    if not java_exe:
        return None

    cache = home / ".gradle" / "caches" / "modules-2" / "files-2.1"
    if not cache.exists():
        return None

    def latest_jar(group: str, artifact: str) -> Path | None:
        base = cache / group / artifact
        if not base.exists():
            return None
        jars = [
            p for p in base.rglob(f"{artifact}-*.jar")
            if not p.name.endswith(("-sources.jar", "-javadoc.jar", "-tests.jar"))
        ]
        return sorted(jars)[-1] if jars else None

    skiko = latest_jar("org.jetbrains.skiko", "skiko-awt")
    runtime = latest_jar("org.jetbrains.skiko", "skiko-awt-runtime-all")
    if runtime is None:
        plat = "windows-x64" if os.name == "nt" else "linux-x64"
        runtime = latest_jar("org.jetbrains.skiko", f"skiko-awt-runtime-{plat}")
    stdlib = latest_jar("org.jetbrains.kotlin", "kotlin-stdlib")
    coro = latest_jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm")
    if not all((skiko, runtime, stdlib, coro)):
        return None
    return java_exe, os.pathsep.join(str(p) for p in (skiko, runtime, stdlib, coro))


def argb_to_rgba(argb: int) -> tuple[int, int, int, int]:
    a = (argb >> 24) & 0xFF
    r = (argb >> 16) & 0xFF
    g = (argb >> 8) & 0xFF
    b = argb & 0xFF
    return r, g, b, a


def axes_to_spec(axes: dict[str, float]) -> str:
    return ",".join(f"{k}={v:.2f}" for k, v in axes.items())


@dataclass
class SheetCanvas:
    width: int
    height: int
    out_path: Path
    bg: int = 0xFFF8FAFC
    commands: list[str] = field(default_factory=list)

    def start(self) -> None:
        self.commands.append(
            f"CANVAS\t{self.width}\t{self.height}\t{self.bg:08X}\t{str(self.out_path).encode('utf-8').hex()}"
        )

    def rect(self, x: float, y: float, w: float, h: float, fill: int = 0, stroke: int = 0, stroke_w: float = 1.0) -> None:
        self.commands.append(
            f"RECT\t{x:.2f}\t{y:.2f}\t{w:.2f}\t{h:.2f}\t{fill:08X}\t{stroke:08X}\t{stroke_w:.2f}"
        )

    def line(self, x0: float, y0: float, x1: float, y1: float, color: int, stroke_w: float = 1.0) -> None:
        self.commands.append(
            f"LINE\t{x0:.2f}\t{y0:.2f}\t{x1:.2f}\t{y1:.2f}\t{color:08X}\t{stroke_w:.2f}"
        )

    def text(
        self,
        text: str,
        x: float,
        y: float,
        size: float,
        color: int = 0xFF0F172A,
        align: str = "left",
        opsz: float = 18.0,
        wdth: float = 100.0,
        wght: float = 400.0,
        grad: float = 0.0,
        rond: float = 0.0,
        slnt: float = 0.0,
    ) -> None:
        axes = axes_to_spec({
            "opsz": opsz, "wdth": wdth, "wght": wght,
            "GRAD": grad, "ROND": rond, "slnt": slnt,
        })
        self.commands.append(
            f"TEXT\t{text.encode('utf-8').hex()}\t{x:.2f}\t{y:.2f}\t{size:.2f}\t{color:08X}\t{align}\t{axes}"
        )

    def finish(self) -> None:
        self.commands.append("SAVE")


def build_contact_sheet(
    out_path: Path,
    title: str,
    subtitle: str,
    tokens: list[str],
    cmap: dict[int, str],
    rev_latin: dict[str, str],
    font_size: float,
    is_lower: bool,
) -> SheetCanvas:
    cols = 6
    rows = (len(tokens) + cols - 1) // cols
    margin = 28
    header_h = 84
    gap_x = 12
    gap_y = 12
    cell_w = int(round(font_size * 4.85))
    row_h = int(round(font_size * 1.38))
    cell_head_h = 30
    cell_h = cell_head_h + 2 * row_h + 12

    width = margin * 2 + cols * cell_w + (cols - 1) * gap_x
    height = margin * 2 + header_h + rows * cell_h + (rows - 1) * gap_y

    sheet = SheetCanvas(width=width, height=height, out_path=out_path)
    sheet.start()

    # Header bar
    sheet.rect(margin, margin, width - 2 * margin, header_h - 12, fill=0xFFFFFFFF, stroke=0xFFCBD5E1, stroke_w=1.5)
    sheet.text(title, margin + 18, margin + 32, 22, color=0xFF0F172A, wght=700, opsz=18, rond=100)
    sheet.text(subtitle, margin + 18, margin + 58, 14, color=0xFF475569, wght=400, opsz=14)

    # Legend for metric lines
    legend = "Guides: solid=baseline  blue=cap(1432)  green=x(1020)  dashed-tint=overshoot(±32) / desc(-432)"
    sheet.text(legend, width - margin - 18, margin + 58, 12.5, color=0xFF64748B, align="right", wght=400, opsz=14)

    scale = font_size / UPM
    for idx, token in enumerate(tokens):
        r = idx // cols
        c = idx % cols
        cx = margin + c * (cell_w + gap_x)
        cy = margin + header_h + r * (cell_h + gap_y)

        mid_char = token[1] if len(token) >= 2 else token[0]
        cp = ord(mid_char)
        gname = cmap.get(cp, "?")
        latin_ref = rev_latin.get(gname)
        status = f"={latin_ref}" if latin_ref else "drawn"
        badge_color = 0xFF64748B if latin_ref else 0xFF2563EB

        sheet.rect(cx, cy, cell_w, cell_h, fill=0xFFFFFFFF, stroke=0xFFE2E8F0, stroke_w=1.2)
        sheet.rect(cx, cy, cell_w, cell_head_h, fill=0xFFF1F5F9, stroke=0xFFE2E8F0, stroke_w=1.0)
        sheet.text(f"{mid_char}  U+{cp:04X}", cx + 10, cy + 20, 14, color=0xFF0F172A, wght=600, opsz=14)
        sheet.text(status, cx + cell_w - 10, cy + 20, 12.5, color=badge_color, align="right", wght=600, opsz=14)

        for w_idx, wght in enumerate((400.0, 700.0)):
            ry = cy + cell_head_h + w_idx * row_h
            if w_idx == 1:
                sheet.line(cx + 8, ry, cx + cell_w - 8, ry, 0xFFF1F5F9, 1.0)
            y_base = ry + (row_h * 0.72 if is_lower else row_h * 0.78)
            y_cap = y_base - CAP_H * scale
            y_x = y_base - X_H * scale
            y_ov_top = y_base - ((X_H if is_lower else CAP_H) + OVERSHOOT) * scale
            y_ov_bot = y_base + OVERSHOOT * scale
            y_desc = y_base - DESC_Y * scale

            # Reference metric hairlines
            sheet.line(cx + 46, y_base, cx + cell_w - 10, y_base, 0xFFCBD5E1, 1.0)
            sheet.line(cx + 46, y_cap, cx + cell_w - 10, y_cap, 0xFFDBEAFE, 1.0)
            if is_lower:
                sheet.line(cx + 46, y_x, cx + cell_w - 10, y_x, 0xFFD1FAE5, 1.0)
                sheet.line(cx + 46, y_desc, cx + cell_w - 10, y_desc, 0xFFFEE2E2, 1.0)
            sheet.line(cx + 46, y_ov_top, cx + cell_w - 10, y_ov_top, 0xFFEFF6FF, 0.8)
            sheet.line(cx + 46, y_ov_bot, cx + cell_w - 10, y_ov_bot, 0xFFEFF6FF, 0.8)

            sheet.text(f"{int(wght)}", cx + 10, y_base - 6, 12, color=0xFF94A3B8, wght=500, opsz=14)
            center_x = cx + 44 + (cell_w - 52) / 2.0
            sheet.text(
                token,
                center_x,
                y_base,
                font_size,
                color=0xFF090D16,
                align="center",
                opsz=18.0,
                wdth=100.0,
                wght=wght,
                rond=0.0,
            )

    sheet.finish()
    return sheet


def build_multiaxis_sheet(out_path: Path, font_name: str, label: str) -> SheetCanvas:
    width = 2240
    margin = 32
    header_h = 84

    sections: list[tuple[str, list[dict]]] = []

    # 1. Weight sweep
    w_rows = []
    for w in (250, 400, 700, 900, 1000):
        w_rows.append({
            "tag": f"wght={w}",
            "left": f"{STRESS_LINES[0]}  •  {STRESS_LINES[1]}",
            "right": f"{STRESS_LINES[2]}  •  {STRESS_LINES[3]}",
            "right_x": 1160,
            "axes": dict(opsz=14, wdth=100, wght=w, grad=0, rond=0, slnt=0),
            "size": 31.0,
        })
    sections.append(("1. Weight Sweep (wght = 250 / 400 / 700 / 900 / 1000 | opsz=14, wdth=100, ROND=0)", w_rows))

    # 2. Width sweep (single line per row so wdth=151 never overflows or collides)
    wd_rows = []
    for wd in (50, 100, 151):
        for w in (400, 700):
            wd_rows.append({
                "tag": f"wdth={wd} w={w}",
                "left": f"{STRESS_LINES[0]}  •  {STRESS_LINES[1]}  •  {STRESS_LINES[2]}",
                "right": "",
                "axes": dict(opsz=14, wdth=wd, wght=w, grad=0, rond=0, slnt=0),
                "size": 30.0,
            })
    sections.append(("2. Width Sweep (wdth = 50 Condensed / 100 Normal / 151 Expanded | wght=400 & 700)", wd_rows))

    # 3. Roundness ROND=0 vs ROND=100 (plus full heavy ROND=100 alphabet showcase)
    r_rows = [
        {
            "tag": "w=400 ROND=0",
            "left": f"{STRESS_LINES[0]}  •  {STRESS_LINES[3]}  •  {STRESS_LINES[1]}",
            "right": "",
            "axes": dict(opsz=14, wdth=100, wght=400, grad=0, rond=0, slnt=0),
            "size": 32.0,
        },
        {
            "tag": "w=400 ROND=100",
            "left": f"{STRESS_LINES[0]}  •  {STRESS_LINES[3]}  •  {STRESS_LINES[1]}",
            "right": "",
            "axes": dict(opsz=14, wdth=100, wght=400, grad=0, rond=100, slnt=0),
            "size": 32.0,
        },
        {
            "tag": "w=900 ROND=0",
            "left": f"{STRESS_LINES[2]}  •  HАБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯO",
            "right": "",
            "axes": dict(opsz=14, wdth=100, wght=900, grad=0, rond=0, slnt=0),
            "size": 31.0,
        },
        {
            "tag": "w=900 ROND=100",
            "left": f"{STRESS_LINES[2]}  •  HАБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯO",
            "right": "",
            "axes": dict(opsz=14, wdth=100, wght=900, grad=0, rond=100, slnt=0),
            "size": 31.0,
        },
        {
            "tag": "lc w=900 R=0",
            "left": f"{STRESS_LINES[3]}  •  nабвгдежзийклмнопрстуфхцчшщъыьэюяo",
            "right": "",
            "axes": dict(opsz=14, wdth=100, wght=900, grad=0, rond=0, slnt=0),
            "size": 31.0,
        },
        {
            "tag": "lc w=900 R=100",
            "left": f"{STRESS_LINES[3]}  •  nабвгдежзийклмнопрстуфхцчшщъыьэюяo",
            "right": "",
            "axes": dict(opsz=14, wdth=100, wght=900, grad=0, rond=100, slnt=0),
            "size": 31.0,
        },
    ]
    sections.append(("3. Roundness (ROND = 0 vs 100 | wght=400 & 900, opsz=14 — KittyTune headline style)", r_rows))

    # 4. Slant, Optical Size, Grade
    o_rows = [
        {
            "tag": "slnt=0 w=400",
            "left": f"{STRESS_LINES[0]}  •  {STRESS_LINES[3]}  •  Музыка KittyTune — ₽199",
            "right": "",
            "axes": dict(opsz=14, wdth=100, wght=400, grad=0, rond=0, slnt=0),
            "size": 32.0,
        },
        {
            "tag": "slnt=-10 w=400",
            "left": f"{STRESS_LINES[0]}  •  {STRESS_LINES[3]}  •  Музыка KittyTune — ₽199",
            "right": "",
            "axes": dict(opsz=14, wdth=100, wght=400, grad=0, rond=0, slnt=-10),
            "size": 32.0,
        },
        {
            "tag": "slnt=-10 w=700",
            "left": f"{STRESS_LINES[0]}  •  {STRESS_LINES[2]}  •  Дёготь Фёдор Юлия",
            "right": "",
            "axes": dict(opsz=14, wdth=100, wght=700, grad=0, rond=0, slnt=-10),
            "size": 32.0,
        },
        {
            "tag": "opsz=6 w=400",
            "left": "Micro opsz=6: Привет Google Sans — Дёготь Фёдор Юлия — Жизнь Бязь — ₽199",
            "right": "",
            "axes": dict(opsz=6, wdth=100, wght=400, grad=0, rond=0, slnt=0),
            "size": 31.0,
        },
        {
            "tag": "opsz=14 w=400",
            "left": "Text opsz=14: Привет Google Sans — Дёготь Фёдор Юлия — Жизнь Бязь — ₽199",
            "right": "",
            "axes": dict(opsz=14, wdth=100, wght=400, grad=0, rond=0, slnt=0),
            "size": 31.0,
        },
        {
            "tag": "opsz=72 w=400",
            "left": "Display opsz=72: Привет Google Sans — Дёготь Фёдор Юлия — Жизнь Бязь — ₽199",
            "right": "",
            "axes": dict(opsz=72, wdth=100, wght=400, grad=0, rond=0, slnt=0),
            "size": 31.0,
        },
        {
            "tag": "GRAD=100 w=400",
            "left": "GRAD=100: Привет Google Sans — Дёготь Фёдор Юлия — Жизнь Бязь Щука Эхо — ₽199",
            "right": "",
            "axes": dict(opsz=14, wdth=100, wght=400, grad=100, rond=0, slnt=0),
            "size": 31.0,
        },
    ]
    sections.append(("4. Slant (slnt=-10), Optical Size (opsz=6/14/72), Grade (GRAD=100)", o_rows))

    # 5. Kerning + Extended & Historical check
    e_rows = [
        {
            "tag": "Kern & Marks",
            "left": "Latin AV To Va  |  ГА ГД ГО ГЕ ГУ ТА ТД ТЯ УА УД ФД РД ЛТ ЛУ  |  " + COMBINING_SAMPLE,
            "right": "",
            "axes": dict(opsz=14, wdth=100, wght=400, grad=0, rond=0, slnt=0),
            "size": 31.0,
        },
        {
            "tag": "Ext w=400",
            "left": EXTENDED_MODERN,
            "right": "",
            "axes": dict(opsz=14, wdth=100, wght=400, grad=0, rond=0, slnt=0),
            "size": 31.0,
        },
        {
            "tag": "Ext w=700",
            "left": EXTENDED_MODERN,
            "right": "",
            "axes": dict(opsz=14, wdth=100, wght=700, grad=0, rond=0, slnt=0),
            "size": 31.0,
        },
        {
            "tag": "Hist w=400",
            "left": HISTORICAL_SAMPLE,
            "right": "",
            "axes": dict(opsz=14, wdth=100, wght=400, grad=0, rond=0, slnt=0),
            "size": 30.0,
        },
        {
            "tag": "Hist w=700",
            "left": HISTORICAL_SAMPLE,
            "right": "",
            "axes": dict(opsz=14, wdth=100, wght=700, grad=0, rond=0, slnt=0),
            "size": 30.0,
        },
    ]
    sections.append(("5. Kerning, Combining Marks, Extended & Historical Cyrillic Check", e_rows))

    row_h = 52
    sec_pad_top = 38
    sec_gap = 16
    total_h = margin * 2 + header_h + 12 + sum(sec_pad_top + len(rows) * row_h + 14 + sec_gap for _, rows in sections)

    sheet = SheetCanvas(width=width, height=total_h, out_path=out_path)
    sheet.start()

    tag_text = f" [{label}]" if label else ""
    sheet.rect(margin, margin, width - 2 * margin, header_h - 12, fill=0xFFFFFFFF, stroke=0xFFCBD5E1, stroke_w=1.5)
    sheet.text(f"Sheet 3: Multi-Axis Stress & Extended Cyrillic{tag_text}", margin + 18, margin + 32, 22, color=0xFF0F172A, wght=700, opsz=18, rond=100)
    sheet.text(f"Font: {font_name}  |  Mixed Cyrillic+Latin across wght, wdth, ROND, slnt, opsz, GRAD + kerning + extended/historical", margin + 18, margin + 58, 14, color=0xFF475569, wght=400, opsz=14)

    y = margin + header_h + 12
    for sec_title, rows in sections:
        box_h = sec_pad_top + len(rows) * row_h + 14
        sheet.rect(margin, y, width - 2 * margin, box_h, fill=0xFFFFFFFF, stroke=0xFFE2E8F0, stroke_w=1.2)
        sheet.rect(margin, y, width - 2 * margin, sec_pad_top - 4, fill=0xFFF1F5F9, stroke=0xFFE2E8F0, stroke_w=1.0)
        sheet.text(sec_title, margin + 14, y + 23, 15, color=0xFF0F172A, wght=700, opsz=14)

        ry = y + sec_pad_top + 6
        for i, row in enumerate(rows):
            if i > 0:
                sheet.line(margin + 12, ry, width - margin - 12, ry, 0xFFF1F5F9, 1.0)
            y_base = ry + 36
            sheet.text(row["tag"], margin + 14, y_base - 4, 13.5, color=0xFF64748B, wght=600, opsz=14)
            sheet.line(margin + 178, y_base, width - margin - 18, y_base, 0xFFE2E8F0, 0.8)
            axes = row["axes"]
            sheet.text(row["left"], margin + 184, y_base, row["size"], color=0xFF090D16, **axes)
            if row["right"]:
                sheet.text(row["right"], row.get("right_x", margin + 1160), y_base, row["size"], color=0xFF090D16, **axes)
            ry += row_h
        y += box_h + sec_gap

    sheet.finish()
    return sheet


def wrap_tokens(text: str, max_chars: int = 58) -> list[str]:
    """Wrap space-separated clusters cleanly without splitting base+combining mark pairs."""
    tokens = text.split(" ")
    lines: list[str] = []
    cur = ""
    for tok in tokens:
        # Also break very long unspaced runs at ~42 code points
        if len(tok) > max_chars and all(ord(c) < 0x300 or ord(c) > 0x36F for c in tok):
            for i in range(0, len(tok), max_chars):
                chunk = tok[i : i + max_chars]
                if cur:
                    lines.append(cur)
                cur = chunk
        elif not cur:
            cur = tok
        elif len(cur) + 1 + len(tok) <= max_chars:
            cur += " " + tok
        else:
            lines.append(cur)
            cur = tok
    if cur:
        lines.append(cur)
    return lines


def build_extended_sheet(out_path: Path, font_name: str, label: str) -> SheetCanvas:
    width = 2240
    margin = 32
    header_h = 84
    row_h = 62
    sec_pad = 38
    sec_gap = 16

    wrapped_groups = [(title, wrap_tokens(chars, max_chars=44)) for title, chars in EXTENDED_GROUPS]
    total_h = margin * 2 + header_h + 12 + sum(
        sec_pad + len(lines) * 2 * row_h + 12 + sec_gap for _, lines in wrapped_groups
    )

    sheet = SheetCanvas(width=width, height=total_h, out_path=out_path)
    sheet.start()
    tag_text = f" [{label}]" if label else ""
    sheet.rect(margin, margin, width - 2 * margin, header_h - 12, fill=0xFFFFFFFF, stroke=0xFFCBD5E1, stroke_w=1.5)
    sheet.text(f"Sheet 4: Extended, Historic & Combining Cyrillic Inventory{tag_text}", margin + 18, margin + 32, 22, color=0xFF0F172A, wght=700, opsz=18, rond=100)
    sheet.text(f"Font: {font_name}  |  Regular wght=400 & Bold wght=700 with Latin H/O/n/o reference framing", margin + 18, margin + 58, 14, color=0xFF475569, wght=400, opsz=14)

    y = margin + header_h + 12
    for title, lines in wrapped_groups:
        box_h = sec_pad + len(lines) * 2 * row_h + 12
        sheet.rect(margin, y, width - 2 * margin, box_h, fill=0xFFFFFFFF, stroke=0xFFE2E8F0, stroke_w=1.2)
        sheet.rect(margin, y, width - 2 * margin, sec_pad - 4, fill=0xFFF1F5F9, stroke=0xFFE2E8F0, stroke_w=1.0)
        sheet.text(title, margin + 14, y + 23, 15, color=0xFF0F172A, wght=700, opsz=14)
        ry = y + sec_pad + 4
        for l_idx, chunk in enumerate(lines):
            sample = f"H O {chunk} n o"
            for w_idx, wght in enumerate((400.0, 700.0)):
                if l_idx > 0 or w_idx > 0:
                    sheet.line(margin + 12, ry, width - margin - 12, ry, 0xFFF1F5F9, 1.0)
                y_base = ry + 42
                sheet.line(margin + 90, y_base, width - margin - 18, y_base, 0xFFE2E8F0, 0.8)
                sheet.text(f"w={int(wght)}", margin + 14, y_base - 4, 14, color=0xFF64748B, wght=600, opsz=14)
                sheet.text(sample, margin + 96, y_base, 36.0, color=0xFF090D16, opsz=18, wght=wght)
                ry += row_h
        y += box_h + sec_gap

    sheet.finish()
    return sheet


def run_skia_backend(font_path: Path, sheets: list[SheetCanvas], verify: bool, toolchain: tuple[Path, str]) -> None:
    java_exe, classpath = toolchain
    with tempfile.TemporaryDirectory() as tmp:
        tmp_dir = Path(tmp)
        java_file = tmp_dir / "SkiaBatchRenderer.java"
        manifest = tmp_dir / "commands.tsv"
        java_file.write_text(SKIA_RUNNER_JAVA, encoding="utf-8")

        lines: list[str] = []
        for s in sheets:
            lines.extend(s.commands)

        probe_cases = [
            ("reg", "HАH Дёготь", 72, "opsz=14.0,wdth=100.0,wght=400.0,GRAD=0.0,ROND=0.0,slnt=0.0"),
            ("bold", "HАH Дёготь", 72, "opsz=14.0,wdth=100.0,wght=900.0,GRAD=0.0,ROND=0.0,slnt=0.0"),
            ("cond", "HАH Дёготь", 72, "opsz=14.0,wdth=50.0,wght=400.0,GRAD=0.0,ROND=0.0,slnt=0.0"),
            ("exp", "HАH Дёготь", 72, "opsz=14.0,wdth=151.0,wght=400.0,GRAD=0.0,ROND=0.0,slnt=0.0"),
            ("rond", "HАH Дёготь", 72, "opsz=14.0,wdth=100.0,wght=900.0,GRAD=0.0,ROND=100.0,slnt=0.0"),
            ("slnt", "HАH Дёготь", 72, "opsz=14.0,wdth=100.0,wght=400.0,GRAD=0.0,ROND=0.0,slnt=-10.0"),
            ("opsz6", "HАH Дёготь", 72, "opsz=6.0,wdth=100.0,wght=400.0,GRAD=0.0,ROND=0.0,slnt=0.0"),
            ("opsz72", "HАH Дёготь", 72, "opsz=72.0,wdth=100.0,wght=400.0,GRAD=0.0,ROND=0.0,slnt=0.0"),
            ("grad100", "HАH Дёготь", 72, "opsz=14.0,wdth=100.0,wght=400.0,GRAD=100.0,ROND=0.0,slnt=0.0"),
            ("kern_td", "ТД", 120, "opsz=14.0,wdth=100.0,wght=400.0,GRAD=0.0,ROND=0.0,slnt=0.0"),
            ("kern_t", "Т", 120, "opsz=14.0,wdth=100.0,wght=400.0,GRAD=0.0,ROND=0.0,slnt=0.0"),
            ("kern_d", "Д", 120, "opsz=14.0,wdth=100.0,wght=400.0,GRAD=0.0,ROND=0.0,slnt=0.0"),
        ]
        probe_files: dict[str, Path] = {}
        if verify:
            for key, txt, sz, axes_spec in probe_cases:
                pfile = tmp_dir / f"probe_{key}.png"
                probe_files[key] = pfile
                lines.append(
                    f"PROBE\t{key}\t{txt.encode('utf-8').hex()}\t{sz}\t{axes_spec}\t560\t110\t{str(pfile).encode('utf-8').hex()}"
                )

        manifest.write_text("\n".join(lines) + "\n", encoding="utf-8")
        res = subprocess.run(
            [str(java_exe), "--enable-native-access=ALL-UNNAMED", "-cp", classpath, str(java_file), str(font_path), str(manifest)],
            capture_output=True,
            text=True,
        )
        if res.returncode != 0:
            raise RuntimeError(f"Skia renderer failed ({res.returncode}):\n{res.stderr}\n{res.stdout}")

        if verify:
            widths = {}
            for out_line in res.stdout.splitlines():
                if out_line.startswith("PROBE\t"):
                    _, key, w_str = out_line.split("\t")
                    widths[key] = float(w_str)
            arrays = {k: np.asarray(Image.open(p).convert("L"), dtype=np.int16) for k, p in probe_files.items()}
            checks = [
                ("wght 400 vs 900", "reg", "bold"),
                ("wdth 100 vs 50", "reg", "cond"),
                ("wdth 100 vs 151", "reg", "exp"),
                ("ROND 0 vs 100 (wght=900)", "bold", "rond"),
                ("slnt 0 vs -10", "reg", "slnt"),
                ("opsz 6 vs 72", "opsz6", "opsz72"),
                ("GRAD 0 vs 100", "reg", "grad100"),
            ]
            print("Axis verification (Skia + HarfBuzz):")
            for label, a_key, b_key in checks:
                diff = int(np.sum(np.abs(arrays[a_key] - arrays[b_key]) > 20))
                print(f"  {label:28s}: diff_px={diff:5d}, adv={widths[a_key]:.2f} vs {widths[b_key]:.2f}")
                if diff < 50:
                    raise RuntimeError(f"Axis check failed for {label}: only {diff} differing pixels!")
            kern_diff = (widths["kern_t"] + widths["kern_d"]) - widths["kern_td"]
            print(f"  GPOS kern T+D vs TD         : {widths['kern_t'] + widths['kern_d']:.2f} vs {widths['kern_td']:.2f} (delta={kern_diff:+.2f})")


def run_pil_backend(font_path: Path, sheets: list[SheetCanvas], verify: bool) -> None:
    font_bytes = font_path.read_bytes()
    axis_order = [a.axisTag for a in TTFont(font_path)["fvar"].axes]

    def load_font(size: float, axes_spec: str) -> ImageFont.FreeTypeFont:
        f = ImageFont.truetype(io.BytesIO(font_bytes), size=int(round(size)))
        vals = dict(kv.split("=") for kv in axes_spec.split(","))
        f.set_variation_by_axes([float(vals[tag]) for tag in axis_order])
        return f

    if verify:
        def probe(txt: str, axes_spec: str) -> tuple[np.ndarray, float]:
            f = load_font(72, axes_spec)
            im = Image.new("L", (560, 110), 255)
            ImageDraw.Draw(im).text((12, 16), txt, font=f, fill=0)
            return np.asarray(im, dtype=np.int16), float(f.getlength(txt))

        p_reg, w_reg = probe("HАH Дёготь", "opsz=14,wdth=100,wght=400,GRAD=0,ROND=0,slnt=0")
        p_bold, w_bold = probe("HАH Дёготь", "opsz=14,wdth=100,wght=900,GRAD=0,ROND=0,slnt=0")
        p_cond, w_cond = probe("HАH Дёготь", "opsz=14,wdth=50,wght=400,GRAD=0,ROND=0,slnt=0")
        p_rond, w_rond = probe("HАH Дёготь", "opsz=14,wdth=100,wght=900,GRAD=0,ROND=100,slnt=0")
        p_slnt, w_slnt = probe("HАH Дёготь", "opsz=14,wdth=100,wght=400,GRAD=0,ROND=0,slnt=-10")
        print("Axis verification (PIL FreeType):")
        for lbl, a, b, wa, wb in [
            ("wght 400 vs 900", p_reg, p_bold, w_reg, w_bold),
            ("wdth 100 vs 50", p_reg, p_cond, w_reg, w_cond),
            ("ROND 0 vs 100 (wght=900)", p_bold, p_rond, w_bold, w_rond),
            ("slnt 0 vs -10", p_reg, p_slnt, w_reg, w_slnt),
        ]:
            diff = int(np.sum(np.abs(a - b) > 20))
            print(f"  {lbl:28s}: diff_px={diff:5d}, adv={wa:.2f} vs {wb:.2f}")
            if diff < 50:
                raise RuntimeError(f"PIL axis check failed for {lbl}: {diff} pixels")

    for sheet in sheets:
        img = None
        draw = None
        for cmd in sheet.commands:
            parts = cmd.split("\t")
            op = parts[0]
            if op == "CANVAS":
                w, h = int(parts[1]), int(parts[2])
                bg = argb_to_rgba(int(parts[3], 16))[:3]
                img = Image.new("RGB", (w, h), bg)
                draw = ImageDraw.Draw(img)
            elif op == "RECT":
                x, y, w, h = map(float, parts[1:5])
                fill_c = int(parts[5], 16)
                stroke_c = int(parts[6], 16)
                sw = int(round(float(parts[7])))
                fill = argb_to_rgba(fill_c)[:3] if (fill_c >> 24) else None
                outline = argb_to_rgba(stroke_c)[:3] if (stroke_c >> 24) else None
                draw.rectangle([x, y, x + w, y + h], fill=fill, outline=outline, width=max(1, sw) if outline else 0)
            elif op == "LINE":
                x0, y0, x1, y1 = map(float, parts[1:5])
                col = argb_to_rgba(int(parts[5], 16))[:3]
                sw = max(1, int(round(float(parts[6]))))
                draw.line([x0, y0, x1, y1], fill=col, width=sw)
            elif op == "TEXT":
                txt = bytes.fromhex(parts[1]).decode("utf-8")
                x, y, sz = map(float, parts[2:5])
                col = argb_to_rgba(int(parts[5], 16))[:3]
                align = parts[6]
                axes_spec = parts[7]
                f = load_font(sz, axes_spec)
                anchor = "ls" if align == "left" else ("ms" if align == "center" else "rs")
                draw.text((x, y), txt, font=f, fill=col, anchor=anchor)
            elif op == "SAVE":
                sheet.out_path.parent.mkdir(parents=True, exist_ok=True)
                img.save(sheet.out_path)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--font", type=Path, default=DEFAULT_FONT, help="Path to TTF variable font")
    parser.add_argument("--out-dir", type=Path, default=Path("font_compare_out"), help="Output directory for PNG sheets")
    parser.add_argument("--label", type=str, default="", help="Optional label shown in sheet headers (e.g. Before / After)")
    parser.add_argument("--backend", choices=("auto", "skia", "pil"), default="auto", help="Rendering backend")
    parser.add_argument("--font-size", type=float, default=64.0, help="Glyph size (px) for Alphabet contact sheets")
    parser.add_argument("--skip-verify", action="store_true", help="Skip variable-axis pixel verification")
    args = parser.parse_args()

    font_path = args.font.resolve()
    out_dir = args.out_dir.resolve()
    out_dir.mkdir(parents=True, exist_ok=True)

    tt = TTFont(font_path)
    cmap = tt.getBestCmap()
    rev_latin = {cmap[ord(ch)]: ch for ch in "ABEKMHOPCTXaeopcyxSIJsijYhIlGQqWw" if ord(ch) in cmap}
    font_name = tt["name"].getDebugName(4) or font_path.name
    tag_text = f" [{args.label}]" if args.label else ""

    sheets = [
        build_contact_sheet(
            out_dir / "sheet1_uppercase_modern.png",
            f"Sheet 1: Uppercase Modern Cyrillic vs Native Latin{tag_text}",
            f"Font: {font_name}  |  Size: {args.font_size:.0f}px  |  Regular (400) & Bold (700)  |  opsz=18, wdth=100, ROND=0",
            UPPER_TOKENS,
            cmap,
            rev_latin,
            args.font_size,
            is_lower=False,
        ),
        build_contact_sheet(
            out_dir / "sheet2_lowercase_modern.png",
            f"Sheet 2: Lowercase Modern Cyrillic vs Native Latin{tag_text}",
            f"Font: {font_name}  |  Size: {args.font_size:.0f}px  |  Regular (400) & Bold (700)  |  opsz=18, wdth=100, ROND=0",
            LOWER_TOKENS,
            cmap,
            rev_latin,
            args.font_size,
            is_lower=True,
        ),
        build_multiaxis_sheet(out_dir / "sheet3_multiaxis_stress.png", font_name, args.label),
        build_extended_sheet(out_dir / "sheet4_extended_historical.png", font_name, args.label),
    ]

    toolchain = find_skia_toolchain() if args.backend in ("auto", "skia") else None
    if args.backend == "skia" and toolchain is None:
        raise RuntimeError("Skia backend requested, but JDK 17+ / Skiko jars were not found in ~/.gradle")

    if toolchain is not None and args.backend != "pil":
        print(f"Using backend: skia ({toolchain[0].name})")
        run_skia_backend(font_path, sheets, not args.skip_verify, toolchain)
    else:
        print("Using backend: pil (FreeType fallback)")
        run_pil_backend(font_path, sheets, not args.skip_verify)

    for s in sheets:
        print(f"Wrote: {s.out_path}")


if __name__ == "__main__":
    main()
