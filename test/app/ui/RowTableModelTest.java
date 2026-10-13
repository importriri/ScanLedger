package app.ui;

import app.config.Settings;
import app.core.Ledger;
import app.core.Row;

import java.nio.file.Files;
import java.time.Clock;
import java.util.Arrays;

// the table is a VIEW of the day: it owns nothing and must follow every event.
// newest on top, so the operator reads the first row for confirmation; NUOVA
// only on the last addition; every filter counts what it shows.
//
//   java -cp out app.ui.RowTableModelTest
public final class RowTableModelTest {

    static final String P = "209531.001_01-01_26120002";
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("rqr-model").toString());
        Settings.getInstance().set(Settings.STATION, "L2");
        Ledger l = new Ledger(Settings.getInstance(), Clock.systemDefaultZone());
        l.open();
        RowTableModel m = new RowTableModel(l);
        l.addListener(m::refresh);

        check("an empty day shows no rows", m.getRowCount() == 0);

        l.add(P + "31", "900612202601310007");
        l.add(P + "32", "900612202601310007");
        check("the newest row is on top", m.rowAt(0).n == 2 && m.rowAt(1).n == 1);
        check("the last pair reads NUOVA", RowTableModel.NUOVA.equals(m.status(m.rowAt(0))));
        check("an older pair reads OK", RowTableModel.OK.equals(m.status(m.rowAt(1))));

        l.addRange(Arrays.asList(P + "40", P + "41", P + "42"), "900612202601310008");
        check("a range adds all its rows to the view", m.getRowCount() == 5);
        check("every row of the last range reads NUOVA",
              RowTableModel.NUOVA.equals(m.status(m.rowAt(0)))
              && RowTableModel.NUOVA.equals(m.status(m.rowAt(2))));
        check("the pair before the range is no longer NUOVA",
              RowTableModel.OK.equals(m.status(m.rowAt(3))));
        check("range rows say their mode and size", "int. 3".equals(m.getValueAt(0, 4)));
        check("scan rows say scan", "scan".equals(m.getValueAt(4, 4)));

        l.modify(1, P + "39", "900612202601310007", "");
        check("an edited row reads MODIFICATA",
              RowTableModelTest.statusOf(m, 1).equals(RowTableModel.MODIFICATA));
        l.delete(2);
        check("a deleted row stays in view and reads ELIMINATA",
              statusOf(m, 2).equals(RowTableModel.ELIMINATA) && m.getRowCount() == 5);

        l.day().recordExport(1, "c", "h", "f", false, l.day().pendingExport());
        l.fireChanged();
        check("exported rows read ESPORTATA", statusOf(m, 3).equals(RowTableModel.ESPORTATA));
        check("a deleted row is never exported", statusOf(m, 2).equals(RowTableModel.ELIMINATA));
        l.modify(3, P + "40", "900612202601310099", "");
        check("an exported row with a new lot reads CORRETTA", statusOf(m, 3).equals(RowTableModel.CORRETTA));
        check("an exported row refuses a new label",
              !l.modify(4, P + "77", "900612202601310008", "").ok());
        check("an exported row refuses delete", !l.delete(4).ok());

        check("Tutte counts every row", m.count(RowTableModel.Filter.ALL) == 5);
        check("Modificate counts edited live rows", m.count(RowTableModel.Filter.MODIFIED) == 2);
        check("Eliminate counts deleted rows", m.count(RowTableModel.Filter.DELETED) == 1);
        check("Esportate counts exported rows, corrected included", m.count(RowTableModel.Filter.EXPORTED) == 4);
        check("Corrette counts pending corrections", m.count(RowTableModel.Filter.CORRECTED) == 1);

        m.setFilter(RowTableModel.Filter.DELETED);
        check("the Eliminate filter shows only the deleted row",
              m.getRowCount() == 1 && m.rowAt(0).n == 2);
        m.setFilter(RowTableModel.Filter.ALL);
        m.setSearch("0099");
        check("search finds a row by lot", m.getRowCount() == 1 && m.rowAt(0).n == 3);
        m.setSearch(P + "41");
        check("search finds a row by label", m.getRowCount() == 1 && m.rowAt(0).n == 4);
        m.setSearch("");

        l.undoLast();
        check("undo of an exported range is refused and the view is unchanged",
              m.getRowCount() == 5 && !m.rowAt(0).deleted());
        l.add(P + "50", "900612202601310010");
        l.undoLast();
        check("undo deletes the last pair, which stays visible as ELIMINATA",
              statusOf(m, 6).equals(RowTableModel.ELIMINATA));

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed > 0 ? 1 : 0);
    }

    static String statusOf(RowTableModel m, int n) {
        int i = m.indexOf(n);
        Row r = i < 0 ? null : m.rowAt(i);
        return r == null ? "" : m.status(r);
    }

    private static void check(String name, boolean cond) {
        if (cond) { passed++; System.out.println("  ok   " + name); }
        else      { failed++; System.out.println("  FAIL " + name); }
    }
}
