import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.imageio.ImageIO;

public class SeedIcon {
    public static void main(String[] args) throws Exception {
        var type = Class.forName("com.alananasss.kittytune.core.IconEncoders");
        var instance = type.getField("INSTANCE").get(null);
        BufferedImage source;
        try (var input = type.getClassLoader().getResourceAsStream("icons/kittytune.png")) {
            source = ImageIO.read(input);
        }
        byte[] ico = (byte[]) type.getMethod("ico", BufferedImage.class).invoke(instance, source);
        Path directory = Path.of(args[0]);
        Files.createDirectories(directory);
        Files.write(directory.resolve("app_icon_default.ico"), ico);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(ico));
        Files.writeString(directory.resolve("windows_applied.txt"), "default|" + hash);
        System.out.println("Prepared returning-guest icon stamp: " + hash);
    }
}
