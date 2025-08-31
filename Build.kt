import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.awt.image.BufferedImageOp
import java.awt.image.ColorModel
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.stream.Stream
import javax.imageio.ImageIO

const val NAME = "rEFInd-Minecraft"

val ROOT = Path.of("").toAbsolutePath()
val SRC = Files.createDirectories(ROOT.resolve("icons"))
val TEMPLATES = Files.createDirectories(ROOT.resolve("templates"))
val BUILD = Files.createDirectories(ROOT.resolve("build").resolve(NAME))
val BUILD_ICONS = Files.createDirectories(BUILD.resolve("icons"))

// Magic Constants
val SELECTION_BIG_SIZE = 256 + 32
val SELECTION_SMALL_SIZE = 64 + 22

typealias ImageStream = Stream<Pair<String, BufferedImage>>

fun Path.toBI(): BufferedImage = ImageIO.read(Files.newInputStream(this))

fun Stream<Path>.toBI(): ImageStream = this.map { it.fileName.toString() to it.toBI() }.filter(Objects::nonNull)

fun osIcons(): ImageStream = Files.list(SRC).filter { it.fileName.toString().matches("^os_.*\\.png$".toRegex()) }.toBI()

fun otherIcons(): ImageStream =
    Files.list(SRC).filter { !it.fileName.toString().matches("^os_.*\\.png$".toRegex()) }.toBI()

fun backgroundImage(): ImageStream = Stream.of(TEMPLATES.resolve("bg_1080.png")).toBI()

fun selectionBigImage(mc: Boolean): ImageStream = Stream.of(
    if (mc) TEMPLATES.resolve("button_down_big_alpha.png")
    else ROOT.resolve("selection_big.png")
).toBI()

fun selectionSmlImage(mc: Boolean): ImageStream = Stream.of(
    if (mc) TEMPLATES.resolve("button_down_small_alpha.png")
    else ROOT.resolve("selection_small.png")
).toBI()

//fun createCompatibleImage(w: Int, h: Int): BufferedImage = GraphicsEnvironment
//    .getLocalGraphicsEnvironment()
//    .defaultScreenDevice
//    .defaultConfiguration
//    .createCompatibleImage(w, h)

fun createCompatibleImage(w: Int, h: Int): BufferedImage = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)

fun createCompatibleImage(image: BufferedImage): BufferedImage = createCompatibleImage(image.width, image.height)

fun BufferedImage.graphicsTransform(
    preTransform: BufferedImage? = null, transform: (BufferedImage, Graphics2D) -> Unit
): BufferedImage {
    val result = preTransform ?: createCompatibleImage(this)
    val graphics = result.createGraphics()
    transform(this, graphics)
    graphics.dispose()
    return result
}

fun ImageStream.graphicsTransform(transform: (BufferedImage, Graphics2D) -> Unit): ImageStream =
    this.map { return@map it.first to it.second.graphicsTransform(transform = transform) }

fun BufferedImage.scaleTo(width: Int, height: Int = width): BufferedImage =
    this.graphicsTransform(createCompatibleImage(width, height)) { image, g2d ->
        g2d.drawImage(
            image,
            0,
            0,
            width,
            height,
            null
        )
    }

fun BufferedImage.centerFitTo(width: Int, height: Int = width): BufferedImage =
    this.graphicsTransform(createCompatibleImage(width, height)) { image, g2d ->
        g2d.drawImage(
            image,
            (width - this.width) / 2,
            (height - this.height) / 2,
            null
        )
    }

fun ImageStream.scaleTo(width: Int, height: Int = width): ImageStream =
    this.map { return@map it.first to it.second.scaleTo(width, height) }

fun ImageStream.centerFitTo(width: Int, height: Int = width): ImageStream =
    this.map { return@map it.first to it.second.centerFitTo(width, height) }

fun ImageStream.tint(color: Color, alpha: Float): ImageStream {
    val tintOp = TintOp(color, alpha)
    return this.graphicsTransform { image, g2d -> g2d.drawImage(image, tintOp, 0, 0) }
}

fun ImageStream.addBg(bg: BufferedImage): ImageStream = this.graphicsTransform { image, g2D ->
    g2D.drawImage(bg, 0, 0, image.width, image.height, null)
    g2D.drawImage(image, 0, 0, null)
}

fun ImageStream.bakeIcons(
    icon: BufferedImage,
    count: Int,
    xPadding: Int = (SELECTION_BIG_SIZE - 256) + 8,
    yPadding: Int = 256 / 2 + 64 / 2 + xPadding + 3,
    row: Boolean = true,
): ImageStream = this.graphicsTransform { image, g2D ->
    g2D.drawImage(image, 0, 0, null)

    val W = image.width
    val H = image.height
    val w = icon.width
    val h = icon.height

    val startX = (W + xPadding - (w + xPadding) * count) / 2
    val startY = (H - h) / 2 + if (row) 0 else yPadding
    val stepX = w + xPadding

    (0..<count).forEach { g2D.drawImage(icon, startX + stepX * it, startY, null) }
}

fun ImageStream.writeTo(path: Path): Unit =
    this.forEach { ImageIO.write(it.second, "png", Files.newOutputStream(path.resolve(it.first))) }

typealias Pipeline = MutableList<(ImageStream) -> ImageStream>

fun createPipeline(): Pipeline = mutableListOf()

fun Pipeline.add(
    really: Boolean, element: (ImageStream) -> ImageStream
): Pipeline {
    if (really) add(element)
    return this
}

fun Pipeline.execute(stream: ImageStream, path: Path) {
    var stream = stream
    forEach { stream = it(stream) }
    stream.writeTo(path)
}


fun Iterator<String>.toMap(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    while (hasNext()) {
        val field = next().split("=", limit = 2)
        if (field.size == 2) map[field[0]] = field[1]
        else map[field[0]] = "true"
    }
    return map
}

fun main(args: Array<String>) {
    app(args.map { it.trim() }.map { it.lowercase() }.iterator())
}

fun app(args: Iterator<String>) {
    if (!args.hasNext()) return help()
    when (args.next()) {
        "help" -> help(args)
        "build" -> build(args)
        "clean" -> clean(args)
    }
}

fun job(title: String, code: () -> Unit) {
    println(" *** $title JOB *** ")
    code()
    println(" *** END *** ")
}

fun help(args: Iterator<String> = emptySet<String>().iterator()) {
    val help = """
                `help`         - Show the entire help message.
                `help word`    - Show related to the word.
            """.trimIndent()
    val build = """
                `build`        - Build the basic static rEFInd Theme. Very Boring.
                `build [args]` - Configure the build process.
                List of extra args:
                    `bakeBg`              - Bakes the provided number of icons to the wallpaper,
                                            to give the illusion of button presses. 
                    `bakeBg.osIcons=N`    - Specify how many OS Icons to bake.
                    `bakeBg.otherIcons=N` - Specify how many Other Icons to bake.                
            """.trimIndent()
    val clean = "`clean`        - Delete the build folder"
    if (args.hasNext()) println(
        when (args.next()) {
            "help" -> help
            "build" -> build
            "clean" -> clean
            else -> "What's that?"
        }
    ) else println("$help\n\n$build\n\n$clean")
}

fun clean(args: Iterator<String>) = job("CLEAN") {
    assert(!args.hasNext())
    clean(BUILD)
}

fun clean(path: Path) {
    if (Files.isRegularFile(path)) Files.deleteIfExists(path)
    else if (Files.exists(path)) Files.list(path).use { files ->
        files.forEach { path -> clean(path) }
        Files.deleteIfExists(path)
    }
}

fun build(proc: Iterator<String>) = job("BUILD") {
    val args = proc.toMap()

    val bakeBg = "bakebg" in args
    val osIconsCount = args["bakebg.osicons"]?.toInt() ?: 0
    val otherIconsCount = args["bakebg.othericons"]?.toInt() ?: 0

    createPipeline().add(true) { it.scaleTo(256) }
        .add(true) { it.tint(Color(0x29272A), 1.0f) } // Lighter shade 0x403B3C
        .add(!bakeBg) { it.addBg(TEMPLATES.resolve("button_big_alpha.png").toBI()) }.execute(osIcons(), BUILD_ICONS)

    createPipeline().add(true) { it.scaleTo(64) }.add(true) { it.tint(Color(0x29272A), 1.0f) }
        .add(!bakeBg) { it.addBg(TEMPLATES.resolve("button_small_alpha.png").toBI()) }
        .execute(otherIcons(), BUILD_ICONS)

    createPipeline().add(true) { it.map { "background.png" to it.second } }
        .add(bakeBg) { it.bakeIcons(TEMPLATES.resolve("button_big_alpha.png").toBI().scaleTo(256), osIconsCount) }
        .add(bakeBg) {
            it.bakeIcons(
                TEMPLATES.resolve("button_small_alpha.png").toBI().scaleTo(64),
                otherIconsCount,
                row = false
            )
        }.execute(backgroundImage(), BUILD)

    createPipeline().add(!bakeBg) { it.scaleTo(SELECTION_BIG_SIZE) }
        .add(bakeBg) { it.scaleTo(256).centerFitTo(SELECTION_BIG_SIZE) }
        .add(true) { it.map { "selection_big.png" to it.second } }.execute(selectionBigImage(bakeBg), BUILD)

    createPipeline().add(!bakeBg) { it.scaleTo(SELECTION_SMALL_SIZE) }
        .add(bakeBg) { it.scaleTo(64).centerFitTo(SELECTION_SMALL_SIZE) }
        .add(true) { it.map { "selection_small.png" to it.second } }.execute(selectionSmlImage(bakeBg), BUILD)

    migrateConfiguration()
}

fun migrateConfiguration() {
    Files.lines(ROOT.resolve("theme.conf")).use { lines ->
        Files.newBufferedWriter(BUILD.resolve("theme.conf")).use { writer ->
            lines.map { it.replace("rEFInd-Minimalist", NAME) }.forEach { writer.write(it);writer.newLine() }
        }
    }
}

class TintOp(val tintColor: Color, val alpha: Float) : BufferedImageOp {
    override fun filter(src: BufferedImage, dest: BufferedImage?): BufferedImage {
        var dest = dest
        if (dest == null) dest = BufferedImage(src.getWidth(), src.getHeight(), src.getType())

        for (y in 0..<src.getHeight()) for (x in 0..<src.getWidth()) {
            val rgb = src.getRGB(x, y)

            val original = Color(rgb, true)

            val r = (original.getRed() * (1 - alpha) + tintColor.getRed() * alpha).toInt()
            val g = (original.getGreen() * (1 - alpha) + tintColor.getGreen() * alpha).toInt()
            val b = (original.getBlue() * (1 - alpha) + tintColor.getBlue() * alpha).toInt()
            val a = original.getAlpha()

            val tinted = Color(r, g, b, a)
            dest.setRGB(x, y, tinted.getRGB())
        }

        return dest
    }

    override fun getBounds2D(src: BufferedImage): Rectangle2D? {
        return src.getRaster().getBounds()
    }

    override fun createCompatibleDestImage(src: BufferedImage, destCM: ColorModel?): BufferedImage {
        var destCM = destCM
        if (destCM == null) destCM = src.getColorModel()
        return BufferedImage(
            destCM,
            destCM.createCompatibleWritableRaster(src.getWidth(), src.getHeight()),
            destCM.isAlphaPremultiplied(),
            null
        )
    }

    override fun getPoint2D(srcPt: Point2D?, dstPt: Point2D?): Point2D {
        var dstPt = dstPt
        if (dstPt == null) dstPt = Point2D.Float()
        dstPt.setLocation(srcPt)
        return dstPt
    }

    override fun getRenderingHints(): RenderingHints? {
        return null
    }
}