import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ColorModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

class Build {

    static final String NAME = "rEFInd-Minecraft";
    static final Path ROOT = Path.of("").toAbsolutePath();
    static final Path SRC = ROOT.resolve("icons");
    static final Path BUILD = ROOT.resolve("build").resolve(NAME);
    static final Path TEMPLATES = ROOT.resolve("templates");

    public static void main(String[] args) throws IOException {
        app(Arrays.stream(args).map(String::trim).map(String::toLowerCase).iterator());
    }

    static void prt(Object... objects) {
        for (var object : objects) System.out.print(object + " ");
        System.out.println('\b');
    }

    static Map<String, String> consumeArgs(Iterator<String> proc) {
        var map = new HashMap<String, String>();
        while (proc.hasNext()) {
            String[] args = proc.next().split("=", 2);
            map.put(args[0], args.length == 2 ? args[1] : "true");
        }
        return map;
    }

    static int app(Iterator<String> proc) throws IOException {
        if (!proc.hasNext()) return help();
        return switch (proc.next()) {
            case "help" -> help(proc);
            case "build" -> build(proc);
            case "clean" -> clean(proc);
            default -> help();
        };
    }

    static int help() {
        prt("""
                Sup bro!
                """);
        return 0;
    }

    static int help(Iterator<String> proc) {
        prt("""
                helper: Sup bro!
                """);
        return 0;
    }

    static int build(Iterator<String> proc) throws IOException {
        var src = Files.createDirectories(SRC);
        var build = Files.createDirectories(BUILD);
        var templates = Files.createDirectories(TEMPLATES);
        var buildIcons = Files.createDirectories(build.resolve("icons"));
        var args = consumeArgs(proc);

        prt(" ** BUILD PROCESS ** ");
        prt("    Root :", ROOT);
        prt("    Src  :", src);
        prt("    Build:", build);
        prt("    Args :", args);

        if (args.containsKey("bgbakeicons")) {
            copyOsIcons(src, buildIcons);
            copyOtherIcons(src, buildIcons);
        } else {
            transmutateOsIcons(src, templates, buildIcons);
            transmutateOtherIcons(src, templates, buildIcons);
        }
        migrateConfiguration(ROOT, build);
        manageOtherFiles(args, ROOT, templates, build);

        prt(" ** BUILD FINISHED ** ");
        return 0;
    }

    static void transmutateOsIcons(Path src, Path templates, Path build) throws IOException {
        prt(" * Transmutate Os Icons * ");

        var bg = ImageIO.read(Files.newInputStream(templates.resolve("button_big_alpha.png")));

        var buffer = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .getDefaultConfiguration().createCompatibleImage(256, 256);
        var graphics = buffer.createGraphics();

        var tintOp = new TintOp(new Color(0xFF29272A), 1.0f); // Lighter shade 0xFF403B3C

        try (var filesList = Files.list(src)) {
            var icons = filesList
                    .filter(p -> p.getFileName().toString().matches("^os_.*\\.png$"))
                    .iterator();

            while (icons.hasNext()) {
                var icon_fp = icons.next();
                var dest_fp = build.resolve(icon_fp.getFileName());
                var icon = ImageIO.read(Files.newInputStream(icon_fp));

                transmutate(
                        buffer, graphics,
                        bg, icon, tintOp
                );

                ImageIO.write(buffer, "png", Files.newOutputStream(dest_fp));
                prt("Transmutated", icon_fp, "to", dest_fp);
            }
        } finally {
            graphics.dispose();
        }
    }

    static void transmutate(BufferedImage dest, Graphics2D graphics, BufferedImage bg, BufferedImage clip, BufferedImageOp op) {
        int w = dest.getWidth(), h = dest.getHeight();
        graphics.drawImage(bg, 0, 0, w, h, null);
        if (op == null) graphics.drawImage(clip, 0, 0, w, h, null);
        else graphics.drawImage(clip, op, 0, 0);
    }

    static void copyOsIcons(Path src, Path build) throws IOException {
        prt(" * Copy OS Icons * ");

        try (var filesList = Files.list(src)) {
            var files = filesList
                    .filter(p -> p.getFileName().toString().matches("^os_.*\\.png$"))
                    .iterator();
            while (files.hasNext()) {
                var file = files.next();
                var dest = build.resolve(file.getFileName());
                Files.copy(file, dest, REPLACE_EXISTING);
                prt("Copied", file, "to", dest);
            }
        }
    }

    static void copyOtherIcons(Path src, Path build) throws IOException {
        prt(" * Copy Remaining Icons * ");

        try (var filesList = Files.list(src)) {
            var files = filesList
                    .filter(p -> !p.getFileName().toString().matches("^os_.*\\.png$"))
                    .iterator();
            while (files.hasNext()) {
                var file = files.next();
                var dest = build.resolve(file.getFileName());
                Files.copy(file, dest, REPLACE_EXISTING);
                prt("Copied", file, "to", dest);
            }
        }
    }

    static void transmutateOtherIcons(Path src, Path templates, Path build) throws IOException {
        prt(" * Transmutate Other Icons * ");

        var bg = ImageIO.read(Files.newInputStream(templates.resolve("button_small_alpha.png")));

        var buffer = createCompatibleImage(64, 64);
        var graphics = buffer.createGraphics();

        var tintOp = new TintOp(new Color(0xFF29272A), 1.0f); // Lighter shade 0xFF403B3C

        try (var filesList = Files.list(src)) {
            var icons = filesList
                    .filter(p -> !p.getFileName().toString().matches("^os_.*\\.png$"))
                    .iterator();

            while (icons.hasNext()) {
                var icon_fp = icons.next();
                var dest_fp = build.resolve(icon_fp.getFileName());
                var icon = ImageIO.read(Files.newInputStream(icon_fp));

                transmutate(
                        buffer, graphics,
                        bg, icon, tintOp
                );

                ImageIO.write(buffer, "png", Files.newOutputStream(dest_fp));
                prt("Transmutated", icon_fp, "to", dest_fp);
            }
        } finally {
            graphics.dispose();
        }
    }

    private static BufferedImage createCompatibleImage(int w, int h) {
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .getDefaultConfiguration().createCompatibleImage(w, h);
    }

    static void migrateConfiguration(Path src, Path build) throws IOException {
        prt(" * Migrate Remaining Icons * ");

        try (
                var lines = Files.lines(src.resolve("theme.conf"));
                var writer = Files.newBufferedWriter(build.resolve("theme.conf"));
        ) {
            var iterator = lines.iterator();
            while (iterator.hasNext()) {
                writer.write(iterator.next().replace("rEFInd-Minimalist", NAME));
                writer.newLine();
            }
        }
    }

    static void manageOtherFiles(Map<String, String> args, Path src, Path templates, Path build) throws IOException {
        prt(" * Copy Background * ");
        if (args.containsKey("bgbakeicons")) {
            int icons = Integer.parseInt(args.get("bgbakeicons"));
            // There is a spacing of 8 px?
            var bg = ImageIO.read(Files.newInputStream(templates.resolve("bg_1080.png")));
            var res = createCompatibleImage(bg.getWidth(), bg.getHeight());
            var osIcon = ImageIO.read(Files.newInputStream(templates.resolve("button_big_alpha.png")));
            var otherIcon = ImageIO.read(Files.newInputStream(templates.resolve("button_small_alpha.png")));
            var graphics = res.createGraphics();
            try {
                graphics.drawImage(bg, 0, 0, null);
                for (int i = 0, x = (bg.getWidth() + 8 - (256 + 8) * icons) / 2, y = (bg.getHeight() - 256) / 2, s = 256 + 8; i < icons; i++, x += s)
                    graphics.drawImage(osIcon, x, y, 256, 256, null);
                for (int i = 0, x = (bg.getWidth() + 8 - (64 + 8) * icons) / 2, y = (bg.getHeight() - 64) / 2 + 256 + 16, s = 64 + 8; i < icons; i++, x += s)
                    graphics.drawImage(otherIcon, x, y, 64, 64, null);
            } finally {
                graphics.dispose();
            }
            ImageIO.write(res, "png", Files.newOutputStream(build.resolve("background.png")));

        } else Files.copy(templates.resolve("bg_480.png"), build.resolve("background.png"), REPLACE_EXISTING);

        prt(" * Manage Other Files * ");
        if (args.containsKey("bgbakeicons")) {
            Files.copy(templates.resolve("button_down_big_alpha.png"), build.resolve("selection_big.png"), REPLACE_EXISTING);
            Files.copy(templates.resolve("button_down_small_alpha.png"), build.resolve("selection_small.png"), REPLACE_EXISTING);
        } else {
            Files.copy(src.resolve("selection_big.png"), build.resolve("selection_big.png"), REPLACE_EXISTING);
            Files.copy(src.resolve("selection_small.png"), build.resolve("selection_small.png"), REPLACE_EXISTING);
        }
    }

    static int clean(Iterator<String> proc) throws IOException {
        prt(" ** CLEAN PROCESS ** ");
        clean(BUILD);
        return 0;
    }

    private static void clean(Path dir) throws IOException {
        if (Files.isRegularFile(dir))
            Files.deleteIfExists(dir);
        else try (var files = Files.list(dir)) {
            var iterator = files.iterator();
            while (iterator.hasNext()) clean(iterator.next());
        }
    }

    static class TintOp implements BufferedImageOp {
        final Color tintColor;
        final float alpha;

        public TintOp(Color tintColor, float alpha) {
            this.tintColor = tintColor;
            this.alpha = alpha;
        }

        @Override
        public BufferedImage filter(BufferedImage src, BufferedImage dest) {
            if (dest == null) dest = new BufferedImage(src.getWidth(), src.getHeight(), src.getType());

            for (var y = 0; y < src.getHeight(); y++)
                for (var x = 0; x < src.getWidth(); x++) {
                    var rgb = src.getRGB(x, y);

                    var original = new Color(rgb, true);

                    var r = (int) (original.getRed() * (1 - alpha) + tintColor.getRed() * alpha);
                    var g = (int) (original.getGreen() * (1 - alpha) + tintColor.getGreen() * alpha);
                    var b = (int) (original.getBlue() * (1 - alpha) + tintColor.getBlue() * alpha);
                    var a = original.getAlpha();

                    var tinted = new Color(r, g, b, a);
                    dest.setRGB(x, y, tinted.getRGB());
                }

            return dest;
        }

        @Override
        public Rectangle2D getBounds2D(BufferedImage src) {
            return src.getRaster().getBounds();
        }

        @Override
        public BufferedImage createCompatibleDestImage(BufferedImage src, ColorModel destCM) {
            if (destCM == null) destCM = src.getColorModel();
            return new BufferedImage(destCM,
                    destCM.createCompatibleWritableRaster(src.getWidth(), src.getHeight()),
                    destCM.isAlphaPremultiplied(), null);
        }

        @Override
        public Point2D getPoint2D(Point2D srcPt, Point2D dstPt) {
            if (dstPt == null) dstPt = new Point2D.Float();
            dstPt.setLocation(srcPt);
            return dstPt;
        }

        @Override
        public RenderingHints getRenderingHints() {
            return null;
        }
    }
}
