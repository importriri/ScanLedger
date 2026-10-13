package app.ui;

// Adapted from AutoFillSuite test/app/ui/IconRenderTest.java — keep behaviour in sync.

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

// icon rendering — born from a real bug, like GlyphSafetyTest: the gear's hub
// was "erased" with AlphaComposite.Clear, which sets pixels to transparent
// black. on swing's OPAQUE backbuffer that paints a BLACK disc — glaring on
// the Latte theme. icons are painted here onto an opaque light image, exactly
// what the real pipeline does, and the pixels are read back.
//
//   java -cp build app.ui.IconRenderTest
public final class IconRenderTest {

    private static final int SIZE = 32;                    // big enough to sample
    private static final Color BG   = new Color(0xeff1f5); // latte BASE
    private static final Color INK  = new Color(0x4c4f69); // latte TEXT

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        gear_hubIsAHole_notABlackDisc();
        everyIcon_actuallyPaintsSomething();

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // the exact failure: on an opaque buffer the hub must show the BACKGROUND.
    // anything near-black there means someone brought Clear back.
    private static void gear_hubIsAHole_notABlackDisc() {
        BufferedImage img = render(Icons.gear(SIZE, INK));
        int center = img.getRGB(SIZE / 2, SIZE / 2);
        check("the gear hub shows the background through the hole",
              sameColor(center, BG.getRGB()));
        check("the gear hub is not a black disc", !nearBlack(center));
        // and the ring around the hub is still the gear, not more background
        int ring = img.getRGB(SIZE / 2 + Math.round(SIZE * 0.24f), SIZE / 2);
        check("the gear body is painted in the icon color",
              sameColor(ring, INK.getRGB()));
    }

    // a blank icon passes the smoke test's "has an icon" check and still shows
    // the operator nothing: every icon must change a sensible number of pixels
    private static void everyIcon_actuallyPaintsSomething() {
        Object[][] all = {
            { "stop",    Icons.stop(SIZE, INK) },
            { "search",  Icons.search(SIZE, INK) },
            { "gear",    Icons.gear(SIZE, INK) },
            { "chevron", Icons.chevron(SIZE, INK, true) },
            { "retry",   Icons.retry(SIZE, INK) },
            { "check",   Icons.check(SIZE, INK) },
            { "cross",   Icons.cross(SIZE, INK) },
            { "swap",    Icons.swap(SIZE, INK) },
            { "restore", Icons.restore(SIZE, INK) },
            { "undo",    Icons.undo(SIZE, INK) },
            { "pencil",  Icons.pencil(SIZE, INK) },
            { "export",  Icons.export(SIZE, INK) },
            { "folder",  Icons.folder(SIZE, INK) },
            { "dot",     Icons.dot(SIZE, INK) },
        };
        for (Object[] entry : all) {
            BufferedImage img = render((Icon) entry[1]);
            int inked = 0;
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    if (!sameColor(img.getRGB(x, y), BG.getRGB())) inked++;
                }
            }
            check(entry[0] + " paints a visible glyph (" + inked + "px)",
                  inked > SIZE);   // at least a thin stroke's worth
        }
    }

    // opaque TYPE_INT_RGB, like swing's double buffer — the surface the bug needs
    private static BufferedImage render(Icon icon) {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(BG);
        g.fillRect(0, 0, SIZE, SIZE);
        icon.paintIcon(null, g, 0, 0);
        g.dispose();
        return img;
    }

    // antialiasing softens edges: compare with a tolerance, not with equals
    private static boolean sameColor(int rgb, int expected) {
        int dr = Math.abs(((rgb >> 16) & 0xFF) - ((expected >> 16) & 0xFF));
        int dg = Math.abs(((rgb >> 8) & 0xFF)  - ((expected >> 8) & 0xFF));
        int db = Math.abs((rgb & 0xFF)         - (expected & 0xFF));
        return dr + dg + db < 30;
    }

    private static boolean nearBlack(int rgb) {
        return ((rgb >> 16) & 0xFF) < 40 && ((rgb >> 8) & 0xFF) < 40 && (rgb & 0xFF) < 40;
    }

    private static void check(String name, boolean cond) {
        if (cond) {
            passed++;
            System.out.println("  ok   " + name);
        } else {
            failed++;
            System.out.println("  FAIL " + name);
        }
    }
}
