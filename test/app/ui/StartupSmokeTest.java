package app.ui;

import app.Main;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

// startup smoke test — calls app.Main.main exactly like `java -jar` does and
// then asks the toolkit a blunt question: is there a window on screen? a build
// that compiles and shows nothing is still a broken app.
//
// TWO STARTUPS, because they are different code paths (the AutoFillSuite lesson:
// a restored state that fired a listener on a half-built panel was an NPE only
// the second path could see):
//   (no arg)  first run ever, no settings file
//   --saved   restart with every setting already saved
//
// the anti-clipping walker and the blank-button check are the AutoFillSuite
// ones (test/app/ui/StartupSmokeTest.java) — keep behaviour in sync.
//
//   xvfb-run java -cp out app.ui.StartupSmokeTest
//   xvfb-run java -cp out app.ui.StartupSmokeTest --saved
public final class StartupSmokeTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("  skip  no display: run under xvfb-run to exercise the ui");
            return;
        }
        // never touch the real settings or data: give the app a throwaway home
        Path home = Files.createTempDirectory("rqr-home");
        System.setProperty("user.home", home.toString());

        boolean saved = args.length > 0 && "--saved".equals(args[0]);
        if (saved) {
            writeSavedSettings(home);
            System.out.println("  ..   avvio con impostazioni salvate");
        } else {
            System.out.println("  ..   primo avvio, nessun file impostazioni");
        }

        Main.main(new String[0]);

        JFrame main = awaitFrame("Registro QR", 5000);
        check("the app actually puts a window on screen", main != null);
        if (main == null) { report(); return; }

        check("the window has a size a human could use",
              main.getWidth() > 200 && main.getHeight() > 200);
        check("the window is not always-on-top: there is no portal to sit next to",
              !main.isAlwaysOnTop());
        check("the window is painted in the theme",
              AppTheme.BASE.equals(main.getContentPane().getBackground()));
        check("no panel is clipped by the window", nothingClipped(main));
        check("no button is left blank (no text and no icon)", allButtonsReadable(main));

        report();
    }

    // an operator who used the app before restarts with every setting in place
    private static void writeSavedSettings(Path home) throws Exception {
        Properties p = new Properties();
        p.setProperty("station.name", "L2");
        p.setProperty("ui.flavor", "latte");
        p.setProperty("ui.winX", "40");
        p.setProperty("ui.winY", "40");
        p.setProperty("ui.winW", "1100");
        p.setProperty("ui.winH", "680");
        p.setProperty("ui.mode", "1");
        p.setProperty("scan.qr1Pattern", "[0-9]{6}\\.[0-9]{3}_.*");
        p.setProperty("scan.qr2Pattern", ".+");
        p.setProperty("scan.autoSwap", "false");
        p.setProperty("scan.dupGuard", "true");
        p.setProperty("range.tailDigits", "3");
        p.setProperty("sound.on", "false");
        try (FileOutputStream out = new FileOutputStream(
                new File(home.toFile(), ".registro_qr.properties"))) {
            p.store(out, "saved by StartupSmokeTest");
        }
    }

    /** A container asking for more height than it got is losing content. */
    static boolean nothingClipped(Container root) {
        for (Component c : root.getComponents()) {
            // a CardLayout host asks for its TALLEST card even while showing a
            // small one — that is the layout's contract, not clipping. The
            // showing child underneath is what gets measured.
            boolean cardHost = c instanceof Container
                            && ((Container) c).getLayout() instanceof CardLayout;
            if (c instanceof JPanel && c.isShowing() && !cardHost) {
                Dimension want = c.getPreferredSize();
                Dimension got = c.getSize();
                if (got.height > 0 && want.height > got.height + 2) {
                    System.out.println("       clipped: " + c.getClass().getName()
                        + " wants " + want.height + "px, got " + got.height + "px");
                    return false;
                }
            }
            if (c instanceof Container && !nothingClipped((Container) c)) return false;
        }
        return true;
    }

    /** A button with neither a label nor an icon is invisible to the operator. */
    static boolean allButtonsReadable(Container root) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton) {
                JButton b = (JButton) c;
                // a spinner's arrows and a scrollbar's stubs are drawn by their own
                // UI and live INSIDE those widgets: blank by design, not by mistake
                Container parent = b.getParent();
                boolean internal = parent instanceof JScrollBar || parent instanceof JSpinner
                                || parent instanceof JComboBox;
                boolean hasText = b.getText() != null && !b.getText().trim().isEmpty();
                if (!internal && !hasText && b.getIcon() == null) {
                    System.out.println("       blank button inside " + parent.getClass().getSimpleName());
                    return false;
                }
            }
            if (c instanceof Container && !allButtonsReadable((Container) c)) return false;
        }
        return true;
    }

    private static JFrame awaitFrame(String title, int timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            for (Window w : Window.getWindows()) {
                if (w instanceof JFrame && w.isShowing()
                        && title.equals(((JFrame) w).getTitle())) {
                    return (JFrame) w;
                }
            }
            Thread.sleep(100);
        }
        return null;
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

    private static void report() {
        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed > 0 ? 1 : 0);
    }
}
