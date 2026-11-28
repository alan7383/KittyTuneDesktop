import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.*;
import javax.imageio.metadata.*;
import javax.imageio.stream.ImageOutputStream;

public class GenerateStartupSplash {
    private static IIOMetadataNode node(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) {
            if (root.item(i).getNodeName().equals(name)) return (IIOMetadataNode) root.item(i);
        }
        var child = new IIOMetadataNode(name);
        root.appendChild(child);
        return child;
    }

    private static final int WIDTH = 360;
    private static final int HEIGHT = 252;
    private static final Color BACKGROUND = Color.BLACK;
    private static Font appFont;
    private static final int[] PERCENTS = {0, 14, 28, 42, 57, 71, 85, 100};
    private static final String[] LANGUAGES = {"en", "de", "fr", "hu", "pt-BR", "ru", "tr", "vi"};
    private static final String[] STAGES = {"starting", "settings", "database", "library", "player", "window", "layout", "ready"};

    private static BufferedImage logoMask(BufferedImage source) {
        var result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int rgb = source.getRGB(x, y);
                int light = Math.max(rgb & 255, Math.max((rgb >> 8) & 255, (rgb >> 16) & 255));
                light = Math.max(0, light - 32) * 255 / 223;
                light = light * (rgb >>> 24) / 255;
                result.setRGB(x, y, (light << 24) | 0xffffff);
            }
        }
        return result;
    }

    private static void textRow(Graphics2D g, String label, int percent, int left, int baseline) {
        g.setFont(appFont.deriveFont(Font.PLAIN, 12f));
        g.setColor(new Color(0xb5b5b5));
        g.drawString(label, left, baseline);
        g.setFont(appFont.deriveFont(Font.BOLD, 19f));
        g.setColor(new Color(0xf5f5f5));
        String value = percent + "%";
        g.drawString(value, left + 312 - g.getFontMetrics().stringWidth(value), baseline);
    }

    private static BufferedImage render(BufferedImage logo, int frame, boolean demo) {
        var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(BACKGROUND);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(logo, 104, 6, 152, 152, null);
        double phase = frame * Math.PI * 2 / 48;
        for (int i = 0; i < 13; i++) {
            double wave = (Math.sin(phase - i * .5) + 1) / 2;
            double envelope = .65 + .35 * Math.cos((i - 6) * Math.PI / 12);
            double height = 4 + 15 * wave * envelope;
            g.setColor(new Color(0xe8e8e8));
            g.fill(new RoundRectangle2D.Double(142.5 + i * 6, 145 - height / 2, 3, height, 3, 3));
        }
        g.setFont(appFont.deriveFont(Font.BOLD, 26f));
        g.setColor(new Color(0xf5f5f5));
        g.drawString("KittyTune", (WIDTH - g.getFontMetrics().stringWidth("KittyTune")) / 2, 185);
        int stage = demo ? Math.min(7, frame / 18) : 0;
        String[] labels = {"Starting KittyTune", "Settings", "Music library", "Services", "Player", "Interface", "Layout", "Ready"};
        textRow(g, labels[stage], PERCENTS[stage], 24, 214);
        g.setColor(new Color(0x242424));
        g.fillRoundRect(24, 229, 312, 3, 3, 3);
        int completed = 312 * PERCENTS[stage] / 100;
        if (completed > 0) {
            g.setColor(new Color(0xf5f5f5));
            g.fillRoundRect(24, 229, completed, 3, 3, 3);
        }
        g.dispose();
        return image;
    }

    private static void writeTextAtlas(Path fontPath, Path output) throws Exception {
        var atlas = new BufferedImage(312, 26 * LANGUAGES.length * STAGES.length, BufferedImage.TYPE_INT_ARGB);
        var g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        for (int language = 0; language < LANGUAGES.length; language++) {
            Path xml = fontPath.getParent().getParent().resolve("i18n/strings-" + LANGUAGES[language] + ".xml");
            var document = factory.newDocumentBuilder().parse(xml.toFile());
            var strings = document.getElementsByTagName("string");
            for (int stage = 0; stage < STAGES.length; stage++) {
                String key = "startup_stage_" + STAGES[stage];
                String label = null;
                for (int i = 0; i < strings.getLength(); i++) {
                    var element = (org.w3c.dom.Element) strings.item(i);
                    if (key.equals(element.getAttribute("name"))) { label = element.getTextContent(); break; }
                }
                if (label == null) throw new IllegalStateException("Missing " + key + " in " + xml);
                textRow(g, label, PERCENTS[stage], 0, (language * STAGES.length + stage) * 26 + 20);
            }
        }
        g.dispose();
        Files.createDirectories(output.getParent());
        ImageIO.write(atlas, "png", output.toFile());
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 5) throw new IllegalArgumentException("logo.png output.gif preview.png mode font.ttf [text-atlas.png]");
        Path fontPath = Path.of(args[4]);
        appFont = Font.createFont(Font.TRUETYPE_FONT, fontPath.toFile());
        System.out.println("Splash font: " + appFont.getFontName());
        if (args.length > 5) writeTextAtlas(fontPath, Path.of(args[5]));
        var logo = logoMask(ImageIO.read(Path.of(args[0]).toFile()));
        Path output = Path.of(args[1]);
        boolean demo = args.length > 3 && args[3].equals("demo");
        Files.createDirectories(output.getParent());
        Files.deleteIfExists(output);
        var writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output.toFile())) {
            writer.setOutput(stream);
            writer.prepareWriteSequence(null);
            int frames = demo ? 144 : 48;
            for (int frame = 0; frame < frames; frame++) {
                var image = render(logo, frame, demo);
                if (frame == (demo ? 96 : 0) && args.length > 2) ImageIO.write(image, "png", Path.of(args[2]).toFile());
                int left = frame == 0 ? 0 : (demo ? 24 : 138);
                int top = frame == 0 ? 0 : 132;
                int width = frame == 0 ? WIDTH : (demo ? 312 : 84);
                int height = frame == 0 ? HEIGHT : (demo ? 104 : 26);
                var encoded = image.getSubimage(left, top, width, height);
                var metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(encoded), null);
                String format = metadata.getNativeMetadataFormatName();
                var tree = (IIOMetadataNode) metadata.getAsTree(format);
                var descriptor = node(tree, "ImageDescriptor");
                descriptor.setAttribute("imageLeftPosition", Integer.toString(left));
                descriptor.setAttribute("imageTopPosition", Integer.toString(top));
                var control = node(tree, "GraphicControlExtension");
                control.setAttribute("disposalMethod", "doNotDispose");
                control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentColorFlag", "FALSE");
                control.setAttribute("delayTime", "5");
                control.setAttribute("transparentColorIndex", "0");
                if (frame == 0) {
                    var loop = new IIOMetadataNode("ApplicationExtension");
                    loop.setAttribute("applicationID", "NETSCAPE");
                    loop.setAttribute("authenticationCode", "2.0");
                    loop.setUserObject(new byte[]{1, 0, 0});
                    node(tree, "ApplicationExtensions").appendChild(loop);
                }
                metadata.setFromTree(format, tree);
                writer.writeToSequence(new IIOImage(encoded, null, metadata), null);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
    }
}
