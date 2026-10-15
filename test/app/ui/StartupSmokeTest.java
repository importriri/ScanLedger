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
        // first start: the station name is asked before anything can be recorded
        if (!saved) {
            JDialog station = awaitDialog("Postazione", 4000);
            check("the first start asks for the station name", station != null);
            if (station instanceof StationDialog) {
                StationDialog sd = (StationDialog) station;
                SwingUtilities.invokeAndWait(() -> { sd.field.setText("L 2;"); sd.confirm(); });
                check("a station name that cannot go in a file name is refused", sd.isShowing());
                SwingUtilities.invokeAndWait(() -> { sd.field.setText("L2"); sd.confirm(); });
                Thread.sleep(200);
                check("a valid station name closes the question", !sd.isShowing());
            }
        }
        check("the header offers Esporta CSV and Chiudi giornata",
              findButton(main, "Esporta CSV") != null && findButton(main, "Chiudi giornata") != null);
        if (saved) {
            check("a restart after a crash says the day was rebuilt, nothing lost",
                  findLabelContaining(main, "Ripresa giornata") != null
                  && findLabelContaining(main, "nessuna persa") != null);
            check("the torn last pair is named so it can be scanned again",
                  findLabelContaining(main, "Ultima coppia scartata") != null
                  && findLabelContaining(main, "2612000299") != null);
        }
        check("the station name is in the title bar strip",
              findLabelContaining(main, "L2") != null);

        MainWindow mw = (MainWindow) main;
        check("the window opens at least at 1100x680 (or the screen)",
              main.getWidth() >= Math.min(1100, screen().width) && main.getHeight() >= Math.min(680, screen().height));
        String theme = saved ? "Latte" : "Mocha";
        sizeAndMeasure(main, mw, main.getSize(), theme + " at the default size");
        sizeAndMeasure(main, mw, main.getMinimumSize(), theme + " at the minimum size");
        SwingUtilities.invokeAndWait(() -> main.setSize(1100, 680));
        Thread.sleep(200);
        check("no button is left blank (no text and no icon)", allButtonsReadable(main));
        check("the disk pill is green and speaks", mw.pill().tone().equals(AppTheme.GREEN)
              && mw.pill().getText() != null && !mw.pill().getText().isEmpty());
        check("the scan field has the focus", waitFor(() -> mw.scanPanel.tfQr1.isFocusOwner(), 3000));

        // a pair through the real startup path: the day is created at the first scan
        SwingUtilities.invokeAndWait(() -> {
            mw.scanPanel.tfQr1.setText("209531.001_01-01_2612000234");
            mw.scanPanel.tfQr2.setText("900612202601310007");
            mw.scanPanel.tfQr2.postActionEvent();
        });
        int expectRows = saved ? 3 : 1;
        check("the first scan lands in the day and records the pair",
              mw.tablePanel.model().getRowCount() == expectRows);
        java.io.File journals = new java.io.File(home.toFile(), "RegistroQR/giornate");
        String[] names = journals.list();
        check("the day journal is on disk in the data folder", names != null && names.length == 1);

        // the settings: every tab measured, the dialog fits a 768 px screen
        JButton gear = findByTooltip(main, "Impostazioni");
        check("the gear button is in the header", gear != null);
        if (gear != null) {
            SwingUtilities.invokeAndWait(gear::doClick);
            JDialog settings = awaitDialog("Impostazioni", 3000);
            check("the gear opens the settings window", settings != null);
            if (settings != null) {
                check("the settings dialog fits a small screen", settings.getHeight() <= 760);
                Thread.sleep(300);
                Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                // a scan fired while the settings are open must not land in a field:
                // its ENTER would save it as the station name or a QR pattern
                check("the settings open with no text field holding the caret",
                      !(owner instanceof javax.swing.text.JTextComponent));
                JTabbedPane tabs = findTabs(settings);
                boolean allFit = tabs != null;
                String[] want = { "Postazione", "Codici", "Suoni", "Cartelle", "Aspetto", "Storico", "Manuale" };
                for (String w : want) allFit &= hasTab(tabs, w);
                check("the settings hold postazione, codici, suoni, cartelle, aspetto, storico, manuale", allFit);
                boolean noClip = tabs != null;
                for (int i = 0; tabs != null && i < tabs.getTabCount(); i++) {
                    final int idx = i;
                    SwingUtilities.invokeAndWait(() -> tabs.setSelectedIndex(idx));
                    Thread.sleep(120);
                    if (!nothingClipped(settings)) {
                        System.out.println("       clipped tab: " + tabs.getTitleAt(i));
                        noClip = false;
                    }
                }
                check("no settings tab is clipped", noClip);
                check("no settings button is blank", allButtonsReadable(settings));
                SwingUtilities.invokeAndWait(() -> settings.setVisible(false));
            }
        }

        report();
    }

    /** Both cards, at one window size: nothing may be clipped. */
    private static void sizeAndMeasure(JFrame main, MainWindow mw, Dimension size, String what)
            throws Exception {
        SwingUtilities.invokeAndWait(() -> main.setSize(size));
        Thread.sleep(250);
        for (int card = 0; card < 2; card++) {
            final int c = card;
            SwingUtilities.invokeAndWait(() -> mw.modeSelector.select(c));
            Thread.sleep(150);
            check("no panel clipped: " + what + ", " + (c == 0 ? "Scansione" : "Intervallo"),
                  nothingClipped(main));
        }
        SwingUtilities.invokeAndWait(() -> mw.modeSelector.select(0));
    }

    private static Dimension screen() {
        Rectangle r = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        return r.getSize();
    }

    private static boolean waitFor(java.util.function.BooleanSupplier done, int timeoutMs)
            throws Exception {
        long until = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < until) {
            if (done.getAsBoolean()) return true;
            Thread.sleep(80);
        }
        return done.getAsBoolean();
    }

    private static JDialog awaitDialog(String title, int timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog && w.isShowing() && title.equals(((JDialog) w).getTitle())) {
                    return (JDialog) w;
                }
            }
            Thread.sleep(100);
        }
        return null;
    }

    // isShowing: CardLayout keeps hidden cards in the tree, and a test once
    // clicked a button the operator could not even see
    private static JButton findByTooltip(Container root, String tip) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton && c.isShowing() && tip.equals(((JButton) c).getToolTipText())) {
                return (JButton) c;
            }
            if (c instanceof Container) {
                JButton b = findByTooltip((Container) c, tip);
                if (b != null) return b;
            }
        }
        return null;
    }

    private static JButton findButton(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton && c.isShowing() && text.equals(((JButton) c).getText())) {
                return (JButton) c;
            }
            if (c instanceof Container) {
                JButton b = findButton((Container) c, text);
                if (b != null) return b;
            }
        }
        return null;
    }

    private static JLabel findLabelContaining(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof JLabel && c.isShowing() && ((JLabel) c).getText() != null
                && ((JLabel) c).getText().contains(text)) return (JLabel) c;
            if (c instanceof Container) {
                JLabel l = findLabelContaining((Container) c, text);
                if (l != null) return l;
            }
        }
        return null;
    }

    private static JTabbedPane findTabs(Container root) {
        for (Component c : root.getComponents()) {
            if (c instanceof JTabbedPane) return (JTabbedPane) c;
            if (c instanceof Container) {
                JTabbedPane t = findTabs((Container) c);
                if (t != null) return t;
            }
        }
        return null;
    }

    private static boolean hasTab(JTabbedPane t, String title) {
        if (t == null) return false;
        for (int i = 0; i < t.getTabCount(); i++) if (title.equals(t.getTitleAt(i))) return true;
        return false;
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
        // the last run did not end through the window: a crash, or the Task Manager
        p.setProperty("app.running", "true");
        try (FileOutputStream out = new FileOutputStream(
                new File(home.toFile(), ".registro_qr.properties"))) {
            p.store(out, "saved by StartupSmokeTest");
        }
        // and it left a journal: two pairs, and a third cut mid-write
        File dir = new File(home.toFile(), "RegistroQR/giornate");
        dir.mkdirs();
        String today = java.time.LocalDate.now().toString();
        File journal = new File(dir, today + ".giornale");
        java.time.Clock clock = java.time.Clock.systemDefaultZone();
        app.core.Day d = app.core.Day.open(journal, new app.core.Day.Init("L2", today, 0, 0), clock);
        d.add("209531.001_01-01_2612000297", "900612202601310007");
        d.add("209531.001_01-01_2612000298", "900612202601310007");
        d.add("209531.001_01-01_2612000299", "900612202601310008");
        d.journal().close();
        byte[] all = Files.readAllBytes(journal.toPath());
        Files.write(journal.toPath(), java.util.Arrays.copyOf(all, all.length - 5));
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
