import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.ui.theme.SoundTuneTheme
import com.alananasss.kittytune.ui.theme.getDynamicTypography
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class BannerRenderTest {

    @Test
    fun renderRealMaterial3Banner() {
        val logoFile = File("images/logo.png")
        val rawLogo = ImageIO.read(logoFile)

        val cleanLogo = java.awt.image.BufferedImage(rawLogo.width, rawLogo.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        for (x in 0 until rawLogo.width) {
            for (y in 0 until rawLogo.height) {
                val rgb = rawLogo.getRGB(x, y)
                val r = (rgb shr 16) and 0xFF
                if (r > 100) {
                    cleanLogo.setRGB(x, y, 0xFFFFFFFF.toInt())
                } else {
                    cleanLogo.setRGB(x, y, 0x00000000)
                }
            }
        }
        val composeLogo = cleanLogo.toComposeImageBitmap()

        val width = 1920
        val height = 650

        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            // Default KittyTune typography: wght=400, rond=0f (customFamilyRounded has ROND=100f for display & titles)
            val typography = getDynamicTypography(
                useCustomFont = true,
                wght = 400,
                wdth = 100f,
                slnt = 0f,
                rond = 0f,
                grad = 0f,
                opsz = 14f
            )

            SoundTuneTheme(
                themeMode = AppThemeMode.DARK,
                dynamicColor = false,
                pureBlack = true,
                keyColor = 0xFF000000.toInt(),
                colorStyle = "System",
                typography = typography
            ) {
                // displayLarge in typography uses customFamilyRounded (the real rounded Google Sans Flex of KittyTune)
                val kittyTuneRoundedFamily = MaterialTheme.typography.displayLarge.fontFamily

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    // Full monochrome M3 Capsule Card
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 40.dp, vertical = 24.dp),
                        shape = RoundedCornerShape(110.dp),
                        color = Color.Black,
                        shadowElevation = 0.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 40.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // Cat Logo (zoomed in, tight next to text)
                            Image(
                                bitmap = composeLogo,
                                contentDescription = "Logo",
                                modifier = Modifier.size(420.dp)
                            )

                            Spacer(Modifier.width(28.dp))

                            // Text Column
                            Column(
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "KittyTune",
                                        fontFamily = kittyTuneRoundedFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 175.sp,
                                        color = Color.White
                                    )

                                    Spacer(Modifier.width(24.dp))

                                    // Monochrome Material 3 Chip (no border, soft neutral dark surface)
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(0xFF262626),
                                    ) {
                                        Text(
                                            text = "Desktop",
                                            fontFamily = kittyTuneRoundedFamily,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 32.sp,
                                            color = Color(0xFFE2E2E2),
                                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(14.dp))

                                Text(
                                    text = "continuous soundcloud vibes for your desktop",
                                    fontFamily = kittyTuneRoundedFamily,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 36.sp,
                                    color = Color(0xFFA5A5A5)
                                )
                            }
                        }
                    }
                }
            }
        }

        val image = scene.render()
        val data = image.encodeToData(EncodedImageFormat.PNG)
        checkNotNull(data) { "Failed to encode image to PNG" }
        File("scratch/banner_real_kittytune_font.png").writeBytes(data.bytes)
        File("images/banner.png").writeBytes(data.bytes)
        println("Generated real KittyTune rounded font banner: images/banner.png")
    }
}
