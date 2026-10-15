package app.ui;

import app.config.Settings;
import app.core.Event;
import app.core.Journal;
import app.core.Ledger;
import app.core.Row;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.time.Clock;
import java.util.List;

// the real window under Xvfb, driven like the scanner drives it: text + ENTER,
// text + TAB. after EVERY step the test reads two things — the table the
// operator sees, and the journal on disk — because the promise is that they
// can never disagree: what is in the table is on the disk.
//
//   xvfb-run java -cp out app.ui.ScanFlowTest
public final class ScanFlowTest {

    static final String P = "209531.001_01-01_26120003";
    static final String FREE =
        "CA HVCO 50 T 048 3-2; M-CA HVCO 50 T 048 3-2  Rev.04; Serial No.: 000066076 ; 2023/12/11;A";

    private static int passed = 0;
    private static int failed = 0;
    private static MainWindow win;
    private static Ledger ledger;

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("  skip  no display: run under xvfb-run");
            return;
        }
        System.setProperty("user.home", Files.createTempDirectory("rqr-flow").toString());
        Settings cfg = Settings.getInstance();
        cfg.set(Settings.STATION, "L2");
        cfg.set(Settings.SOUND_ON, false);
        cfg.set(Settings.QR1_PATTERN, "[0-9]{6}\\.[0-9]{3}_.*");
        cfg.set(Settings.QR2_PATTERN, "[^_]+");
        cfg.save();
        ScanTabSupport.install();
        ledger = new Ledger(cfg, Clock.systemDefaultZone());
        ledger.open();
        SwingUtilities.invokeAndWait(() -> win = new MainWindow(ledger));
        Thread.sleep(300);

        on(ScanFlowTest::pairWithEnter_isOnDiskAndOnTop);
        on(ScanFlowTest::pairWithTab_isAcceptedLikeEnter);
        on(ScanFlowTest::freeTextLot_arrivesIntact);
        on(ScanFlowTest::duplicate_isRefused_fieldsStayFull);
        on(ScanFlowTest::inversion_isHeld_thenF2_swapsAndRecords);
        on(ScanFlowTest::mismatch_recordsNothing);
        on(ScanFlowTest::range_overlapAndOverflow_areRefused);
        on(ScanFlowTest::range_isOneEvent_andCtrlZ_removesItWhole);
        on(ScanFlowTest::editDialog_changesLabelAndLot);
        on(ScanFlowTest::exportedRow_editsOnlyTheLot);
        on(ScanFlowTest::goToRow_selectsTheExistingRow);
        on(ScanFlowTest::tornEdit_isNotReportedAsAPairToScanAgain);
        on(ScanFlowTest::exportPreview_showsEveryLineWhole);

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed > 0 ? 1 : 0);
    }

    // the PC went off while writing an edit, not a pair: "scan it again" would
    // only give a duplicate. the banner names what was lost and the row
    private static void tornEdit_isNotReportedAsAPairToScanAgain() throws Exception {
        java.io.File f = Files.createTempFile("rqr-torn", ".giornale").toFile();
        String head = Journal.encodeForTest(new Event(1, "2026-10-13T07:00:00.000", Event.Type.APRI,
                java.util.Arrays.asList("L2", "2026-10-13", "0", "0")))
            + Journal.encodeForTest(new Event(2, "2026-10-13T07:00:01.000", Event.Type.AGGIUNGI,
                java.util.Arrays.asList("1", P + "90", "LOT-A", "SCAN")));
        String torn = "3\t2026-10-13T07:00:02.000\tMODIFICA\t1\t" + P + "90\tLOT-";
        Files.write(f.toPath(), (head + torn).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Journal.Replay r = Journal.recover(f, Clock.systemDefaultZone());
        String[] b = MainWindow.discardBanner(r);
        check("a torn edit is named as an edit of row 1",
              b[0].contains("modifica") && b[0].contains("#1") && !b[1].toLowerCase(java.util.Locale.ROOT).contains("sparala"));
        check("a torn pair still says to scan it again",
              MainWindow.discardBanner(tornPair())[1].toLowerCase(java.util.Locale.ROOT).contains("sparala"));
    }

    // the preview is the file as it will be written: its last line (#fine,
    // with the fingerprint) must be on screen, not wrapped out of the box
    private static void exportPreview_showsEveryLineWhole() throws Exception {
        scanEnter(P + "95", "900612202601310095");
        ExportDialog d = new ExportDialog(win, ledger, false);
        JTextArea pv = null;
        for (Component c : allComponents(d.getContentPane())) if (c instanceof JTextArea) pv = (JTextArea) c;
        int lines = pv == null ? 0 : pv.getText().split("\n").length;
        check("the export preview does not wrap lines", pv != null && !pv.getLineWrap());
        check("the export preview has a row for every line, #fine included",
              pv != null && pv.getRows() >= lines && pv.getText().contains("#fine"));
        d.dispose();
    }

    private static java.util.List<Component> allComponents(Container root) {
        java.util.List<Component> out = new java.util.ArrayList<>();
        for (Component c : root.getComponents()) {
            out.add(c);
            if (c instanceof Container) out.addAll(allComponents((Container) c));
        }
        return out;
    }

    private static Journal.Replay tornPair() throws Exception {
        java.io.File f = Files.createTempFile("rqr-torn", ".giornale").toFile();
        String head = Journal.encodeForTest(new Event(1, "2026-10-13T07:00:00.000", Event.Type.APRI,
                java.util.Arrays.asList("L2", "2026-10-13", "0", "0")));
        String torn = "2\t2026-10-13T07:00:01.000\tAGGIUNGI\t1\t" + P + "91\t9006";
        Files.write(f.toPath(), (head + torn).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Journal.recover(f, Clock.systemDefaultZone());
    }

    interface Step { void run() throws Exception; }

    private static void on(Step s) throws Exception {
        final Exception[] err = { null };
        SwingUtilities.invokeAndWait(() -> {
            try { s.run(); } catch (Exception e) { err[0] = e; }
        });
        if (err[0] != null) {
            failed++;
            System.out.println("  FAIL exception: " + err[0]);
            err[0].printStackTrace(System.out);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static List<Event> journal() throws Exception {
        return Journal.read(ledger.day().file()).events;
    }

    private static Event lastEvent() throws Exception {
        List<Event> e = journal();
        return e.get(e.size() - 1);
    }

    private static Row top() {
        return win.tablePanel.model().rowAt(0);
    }

    private static void scanEnter(String q1, String q2) {
        ScanPanel s = win.scanPanel;
        s.tfQr1.setText(q1);
        s.tfQr1.postActionEvent();        // the scanner's ENTER after QR 1
        s.tfQr2.setText(q2);
        s.tfQr2.postActionEvent();        // and after QR 2
    }

    private static void scanTab(String q1, String q2) {
        ScanPanel s = win.scanPanel;
        s.tfQr1.setText(q1);
        s.tfQr1.postActionEvent();
        s.tfQr2.setText(q2);
        KeyEvent tab = new KeyEvent(s.tfQr2, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
                                    0, KeyEvent.VK_TAB, '\t');
        ScanTabSupport.handle(tab, s.tfQr2);   // what the dispatcher does with the scanner's TAB
    }

    // ── steps ────────────────────────────────────────────────────────────

    private static void pairWithEnter_isOnDiskAndOnTop() throws Exception {
        scanEnter(P + "01", "900612202601310007");
        check("a scanned pair is on top of the table",
              top() != null && (P + "01").equals(top().label()) && "900612202601310007".equals(top().lot()));
        Event e = lastEvent();
        check("the same pair is in the journal on disk",
              e.type == Event.Type.AGGIUNGI && (P + "01").equals(e.field(1))
              && "900612202601310007".equals(e.field(2)));
        check("the fields are emptied for the next piece",
              win.scanPanel.tfQr1.getText().isEmpty() && win.scanPanel.tfQr2.getText().isEmpty());
        check("the box confirms the pair with its row number",
              win.scanPanel.box.titleText().contains("COPPIA REGISTRATA") && win.scanPanel.box.titleText().contains("#1"));
    }

    private static void pairWithTab_isAcceptedLikeEnter() throws Exception {
        scanTab(P + "02", "900612202601310007");
        check("a TAB-suffix scanner records the pair too", (P + "02").equals(top().label()));
        check("and the journal has it", (P + "02").equals(lastEvent().field(1)));
    }

    private static void freeTextLot_arrivesIntact() throws Exception {
        scanEnter(P + "03", "  " + FREE + " ");
        check("the free-text lot reaches the table intact, only the edges trimmed", FREE.equals(top().lot()));
        check("the free-text lot reaches the journal intact", FREE.equals(lastEvent().field(2)));
    }

    private static void duplicate_isRefused_fieldsStayFull() throws Exception {
        int before = journal().size();
        scanEnter(P + "02", "900612202601310009");
        check("a label already recorded today is refused",
              win.scanPanel.box.titleText().contains("GIÀ REGISTRATA OGGI"));
        check("the refusal names the existing row", win.scanPanel.box.detailText().contains("#2"));
        check("the fields stay full so the operator sees what came in",
              (P + "02").equals(win.scanPanel.tfQr1.getText())
              && "900612202601310009".equals(win.scanPanel.tfQr2.getText()));
        check("nothing reaches the journal", journal().size() == before);
        check("the table is unchanged", win.tablePanel.model().getRowCount() == 3);
        // the next piece overwrites QR 1 and must not glue onto the old QR 2
        win.scanPanel.tfQr1.setText(P + "04");
        check("a new scan after a refusal starts QR 2 empty", win.scanPanel.tfQr2.getText().isEmpty());
        win.scanPanel.clearFields();
    }

    private static void inversion_isHeld_thenF2_swapsAndRecords() throws Exception {
        int before = journal().size();
        scanEnter("900612202601310011", P + "05");
        check("an inverted pair is held, yellow", win.scanPanel.box.titleText().contains("INVERTITI")
              && win.scanPanel.box.accent().equals(AppTheme.YELLOW));
        check("an inverted pair writes nothing", journal().size() == before);
        Action f2 = win.scanPanel.getActionMap().get("swapQr");
        f2.actionPerformed(new ActionEvent(win.scanPanel, ActionEvent.ACTION_PERFORMED, "F2"));
        check("F2 swaps and records the pair the right way round",
              (P + "05").equals(top().label()) && "900612202601310011".equals(top().lot()));
        check("the swapped pair is on disk the right way round",
              (P + "05").equals(lastEvent().field(1)) && "900612202601310011".equals(lastEvent().field(2)));
    }

    private static void mismatch_recordsNothing() throws Exception {
        int before = journal().size();
        scanEnter("BOH", "PURE_BOH");
        check("a pair that fits neither field is refused red",
              win.scanPanel.box.titleText().contains("FORMATO NON RICONOSCIUTO")
              && win.scanPanel.box.accent().equals(AppTheme.RED));
        check("a mismatch writes nothing", journal().size() == before);
        win.scanPanel.clearFields();
    }

    private static void range_overlapAndOverflow_areRefused() throws Exception {
        win.modeSelector.select(1);
        RangePanel r = win.rangePanel;
        r.tfFirst.setText(P + "00");
        r.tfLot.setText("900612202601310020");
        r.spCount.setValue(4);
        r.update();
        check("a range over existing labels is refused before anything happens",
              r.box.titleText().contains("SOVRAPPOSIZIONE") && !r.btnAdd.isEnabled());
        int before = journal().size();
        r.add();
        check("a refused range writes nothing", journal().size() == before);

        r.tfFirst.setText("209531.001_01-01_2612000998");
        r.spCount.setValue(5);
        r.update();
        check("a range past 999 is refused as an overflow",
              r.box.titleText().contains("SFORA") && !r.btnAdd.isEnabled());
    }

    private static void range_isOneEvent_andCtrlZ_removesItWhole() throws Exception {
        RangePanel r = win.rangePanel;
        r.tfFirst.setText(P + "20");
        r.tfLot.setText("900612202601310020");
        ((JSpinner.DefaultEditor) r.spCount.getEditor()).getTextField().setText("3");
        int before = journal().size();
        r.add();
        check("the typed quantity is the one used (commitEdit)", win.tablePanel.model().getRowCount() == 7);
        check("a range is ONE journal event", journal().size() == before + 1
              && lastEvent().type == Event.Type.INTERVALLO);
        check("the range rows are on top", (P + "22").equals(top().label()));

        Action undo = ((JComponent) win.getContentPane()).getActionMap().get("undoLast");
        undo.actionPerformed(new ActionEvent(win, ActionEvent.ACTION_PERFORMED, "z"));
        check("Ctrl+Z removes the whole range in one event",
              lastEvent().type == Event.Type.ELIMINA_INTERVALLO);
        check("the undone rows stay visible as ELIMINATA",
              RowTableModel.ELIMINATA.equals(win.tablePanel.model().status(top())));
        win.modeSelector.select(0);
    }

    private static void editDialog_changesLabelAndLot() throws Exception {
        Row row = ledger.day().row(1);
        EditDialog d = new EditDialog(win, ledger, row);
        check("an unexported row offers its label for editing", d.tfLabel.isEnabled());
        d.tfLabel.setText(P + "02");
        check("the live check refuses a duplicate label", d.validateLive() != null && !d.btnSave.isEnabled());
        d.tfLabel.setText(P + "09");
        d.tfLot.setText("900612202601310030");
        d.tfReason.setText("cartellino sbagliato");
        check("the live check accepts a clean edit", d.validateLive() == null);
        d.save();
        check("the edit is saved", d.wasSaved());
        Event e = lastEvent();
        check("the edit is a MODIFICA on disk, reason included",
              e.type == Event.Type.MODIFICA && "1".equals(e.field(0)) && (P + "09").equals(e.field(1))
              && "cartellino sbagliato".equals(e.field(3)));
        int i = win.tablePanel.model().indexOf(1);
        check("the table shows the edited row as MODIFICATA",
              RowTableModel.MODIFICATA.equals(win.tablePanel.model().status(win.tablePanel.model().rowAt(i))));
    }

    private static void exportedRow_editsOnlyTheLot() throws Exception {
        ledger.day().recordExport(1, "2026-10-13T12:00:41", "h", "f.csv", false, ledger.day().pendingExport());
        ledger.fireChanged();
        Row row = ledger.day().row(2);
        EditDialog d = new EditDialog(win, ledger, row);
        check("an exported row's label is locked in the dialog", !d.tfLabel.isEnabled());
        d.tfLot.setText("900612202601310044");
        d.save();
        check("the new lot is saved as a correction",
              d.wasSaved() && ledger.day().row(2).state() == Row.State.CORRECTED);
        check("the correction is on disk", lastEvent().type == Event.Type.MODIFICA
              && "900612202601310044".equals(lastEvent().field(2)));
        check("the table reads CORRETTA",
              RowTableModel.CORRETTA.equals(RowTableModelTest.statusOf(win.tablePanel.model(), 2)));
    }

    private static void goToRow_selectsTheExistingRow() throws Exception {
        scanEnter(P + "05", "900612202601310011");
        check("the duplicate offers its row", win.scanPanel.box.titleText().contains("GIÀ REGISTRATA"));
        win.tablePanel.model().setFilter(RowTableModel.Filter.DELETED);
        win.showRow(4);
        Row sel = win.tablePanel.selected();
        check("Vai alla riga selects the row, clearing a filter that hid it", sel != null && sel.n == 4);
        win.scanPanel.clearFields();
    }

    private static void check(String name, boolean cond) {
        if (cond) { passed++; System.out.println("  ok   " + name); }
        else      { failed++; System.out.println("  FAIL " + name); }
    }
}
