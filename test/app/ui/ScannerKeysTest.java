package app.ui;

import app.config.Settings;
import app.core.Day;
import app.core.Ledger;
import app.core.Row;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.time.Clock;

// the scanner is a keyboard: it types the code and ends it with ENTER, wherever
// the caret happens to be. these steps press REAL keys through the X server,
// because the bugs they pin down live in focus and key routing, not in the
// actions themselves:
//   - a row clicked in the table, then the next piece scanned: the ENTER opened
//     the edit dialog and the scan rewrote that row
//   - the range: a lot scanned again glued onto the lot kept from the last
//     range, and ENTER in the quantity added nothing
//   - "Chiudi giornata?" answered OK by the ENTER of the next scan
//   - a refusal pop-up that swallowed QR 1 and put QR 2 into QR 1
//
//   xvfb-run java -cp out app.ui.ScannerKeysTest
public final class ScannerKeysTest {

    static final String L = "20953100101012612";   // digits only: the robot types them unshifted
    static final String LOT = "900612202601310007";

    private static int passed = 0;
    private static int failed = 0;
    private static Robot robot;
    private static MainWindow win;
    private static Ledger ledger;

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("  skip  no display: run under xvfb-run");
            return;
        }
        // a dialog left open by a failing step must not hang the build
        Thread watchdog = new Thread(() -> {
            try { Thread.sleep(90_000); } catch (InterruptedException e) { return; }
            System.out.println("  FAIL timed out: a modal window is still waiting");
            System.exit(1);
        });
        watchdog.setDaemon(true);
        watchdog.start();

        System.setProperty("user.home", Files.createTempDirectory("rqr-keys").toString());
        Settings cfg = Settings.getInstance();
        cfg.set(Settings.STATION, "L2");
        cfg.set(Settings.SOUND_ON, false);
        cfg.save();
        ScanTabSupport.install();
        ledger = new Ledger(cfg, Clock.systemDefaultZone());
        ledger.open();
        SwingUtilities.invokeAndWait(() -> win = new MainWindow(ledger));
        robot = new Robot();
        robot.setAutoDelay(4);
        settle(600);
        SwingUtilities.invokeAndWait(() -> { win.toFront(); win.focusScan(); });
        settle(400);

        scanAfterClickingARow_recordsAPair_andLeavesTheRowAlone();
        range_enterInQuantity_adds_andARescannedLotReplacesTheOld();
        closeDayQuestion_isNotAnsweredByTheNextScan();
        refusedDelete_doesNotSwallowTheNextScan();

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed > 0 ? 1 : 0);
    }

    private static void scanAfterClickingARow_recordsAPair_andLeavesTheRowAlone() throws Exception {
        scan(L + "00001");
        scan(LOT);
        check("a pair scanned the normal way is row 1", liveCount() == 1);

        clickTableRow(0);
        scan(L + "00002");
        scan(LOT);
        settle(300);
        check("no edit dialog was opened by the scan", !editDialogShowing());
        closeStrayDialogs();
        Row first = row(1);
        check("row 1 is untouched", first != null && (L + "00001").equals(first.label()) && LOT.equals(first.lot()));
        Row second = row(2);
        check("the scan after the click is a new pair, row 2",
              second != null && (L + "00002").equals(second.label()) && LOT.equals(second.lot()));
    }

    private static void range_enterInQuantity_adds_andARescannedLotReplacesTheOld() throws Exception {
        SwingUtilities.invokeAndWait(() -> win.modeSelector.select(1));
        settle(300);
        SwingUtilities.invokeAndWait(() -> win.rangePanel.tfFirst.requestFocusInWindow());
        settle(200);
        int before = liveCount();
        scan(L + "00100");
        scan("900612202601310011");
        scan("2");                    // quantity, ENTER confirms
        settle(300);
        check("ENTER in the quantity adds the range", liveCount() == before + 2);

        scan(L + "00200");
        scan("900612202601310011");   // the same lot, scanned again
        scan("2");
        settle(300);
        Day d = ledger.day();
        Row r = d == null ? null : d.findLive(L + "00201");
        check("the second range adds its two rows", liveCount() == before + 4);
        check("a lot scanned again replaces the lot on screen, it does not glue onto it",
              r != null && "900612202601310011".equals(r.lot()));
        SwingUtilities.invokeAndWait(() -> win.modeSelector.select(0));
        settle(300);
    }

    private static void closeDayQuestion_isNotAnsweredByTheNextScan() throws Exception {
        SwingUtilities.invokeAndWait(() -> ledger.export(false));
        check("everything is exported before closing", ledger.day().pendingExport().isEmpty());
        SwingUtilities.invokeLater(() -> win.btnCloseDay.doClick());
        JDialog q = awaitDialog("Chiudi giornata");
        check("closing the day asks first", q != null);
        scan(L + "00300");            // the next piece arrives while the question is open
        settle(400);
        boolean closedByScan = ledger.day() == null || ledger.day().isClosed();
        check("the ENTER of the next scan does not close the day", !closedByScan);
        closeStrayDialogs();
        SwingUtilities.invokeAndWait(() -> win.scanPanel.clearFields());
        settle(200);
    }

    private static void refusedDelete_doesNotSwallowTheNextScan() throws Exception {
        // with no window manager (Xvfb) the frame does not get the focus back
        // by itself after a dialog: the operator's click on QR 1 does it
        click(win.scanPanel.tfQr1);
        int before = liveCount();
        // row 1 is exported: deleting it is refused
        SwingUtilities.invokeAndWait(() -> win.tablePanel.showRow(1));
        SwingUtilities.invokeLater(() -> win.delete(ledger.day().row(1)));
        settle(400);
        scan(L + "00400");
        scan(LOT);
        settle(400);
        boolean modal = false;
        for (Window w : Window.getWindows()) {
            if (w instanceof JDialog && w.isShowing() && ((JDialog) w).isModal()) modal = true;
        }
        closeStrayDialogs();
        Row r = ledger.day() == null ? null : ledger.day().findLive(L + "00400");
        check("the refusal leaves no window in the scanner's way", !modal);
        check("the next pair is recorded whole", liveCount() == before + 1
              && r != null && LOT.equals(r.lot()));
    }

    // ── keys and windows ─────────────────────────────────────────────────

    private static void scan(String code) {
        for (char c : code.toCharArray()) {
            int k = KeyEvent.getExtendedKeyCodeForChar(c);
            robot.keyPress(k);
            robot.keyRelease(k);
        }
        robot.keyPress(KeyEvent.VK_ENTER);
        robot.keyRelease(KeyEvent.VK_ENTER);
        settle(150);
    }

    private static void click(JComponent c) throws Exception {
        final Point[] at = new Point[1];
        SwingUtilities.invokeAndWait(() -> {
            Point p = c.getLocationOnScreen();
            at[0] = new Point(p.x + c.getWidth() / 2, p.y + c.getHeight() / 2);
        });
        robot.mouseMove(at[0].x, at[0].y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        settle(300);
    }

    private static void clickTableRow(int viewRow) throws Exception {
        final Point[] at = new Point[1];
        SwingUtilities.invokeAndWait(() -> {
            JTable t = win.tablePanel.table();
            Rectangle cell = t.getCellRect(viewRow, 1, true);
            Point p = t.getLocationOnScreen();
            at[0] = new Point(p.x + cell.x + cell.width / 2, p.y + cell.y + cell.height / 2);
        });
        robot.mouseMove(at[0].x, at[0].y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        settle(300);
    }

    private static boolean editDialogShowing() {
        for (Window w : Window.getWindows()) if (w instanceof EditDialog && w.isShowing()) return true;
        return false;
    }

    private static JDialog awaitDialog(String title) {
        long end = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < end) {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog && w.isShowing() && title.equals(((JDialog) w).getTitle())) {
                    settle(300);
                    return (JDialog) w;
                }
            }
            settle(50);
        }
        return null;
    }

    private static void closeStrayDialogs() throws Exception {
        for (Window w : Window.getWindows()) {
            if (w instanceof JDialog && w.isShowing()) SwingUtilities.invokeAndWait(w::dispose);
        }
        settle(200);
        SwingUtilities.invokeAndWait(() -> { win.toFront(); win.focusScan(); });
        settle(200);
    }

    private static int liveCount() throws Exception {
        final int[] n = { 0 };
        SwingUtilities.invokeAndWait(() -> n[0] = ledger.day() == null ? 0 : ledger.day().liveCount());
        return n[0];
    }

    private static Row row(int n) throws Exception {
        final Row[] r = { null };
        SwingUtilities.invokeAndWait(() -> r[0] = ledger.day() == null ? null : ledger.day().row(n));
        return r[0];
    }

    private static void settle(int ms) {
        robot.waitForIdle();
        robot.delay(ms);
    }

    static void check(String name, boolean cond) {
        if (cond) { passed++; System.out.println("  ok   " + name); }
        else      { failed++; System.out.println("  FAIL " + name); }
    }
}
