package app.core;

import app.config.Settings;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

// the life of a day through the Ledger the screen uses: it opens at the first
// scan, refuses to close with something still to export, closes, and the next
// scan starts a new one — "_2" on the same date, numbering continued. a day
// left open from yesterday is reported; a closed day keeps its rows frozen but
// still records a late copy or a full re-export.
//
//   java -cp out app.core.DayLifecycleTest
public final class DayLifecycleTest {

    static final String P = "209531.001_01-01_26120006";
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        File home = Files.createTempDirectory("rqr-life").toFile();
        System.setProperty("user.home", home.getPath());
        Settings cfg = Settings.getInstance();
        cfg.set(Settings.STATION, "L2");
        TestClock c = new TestClock("2026-10-12T22:00:00", 1000);
        Ledger l = new Ledger(cfg, c);
        l.open();

        check("no day exists before the first scan", l.day() == null);
        check("the first scan opens the day", l.add(P + "01", "LOT-A").ok() && l.day() != null);
        check("the day is named after the date it opened", "2026-10-12".equals(l.day().date()));

        c.set("2026-10-13T01:30:00");   // past midnight: the shift goes on
        check("past midnight the scan still lands in the same day",
              l.add(P + "02", "LOT-A").ok() && "2026-10-12".equals(l.day().date()));
        check("an open day from yesterday is stale", l.days().staleOpenDays().size() == 1);

        Day.Result refused = l.closeDay();
        check("a day with rows to export cannot be closed",
              refused.kind == Day.Result.Kind.REFUSED && refused.message.contains("2 righe nuove"));
        check("the export empties what is pending", l.export(false).ok);
        File first = l.day().file();
        check("an exported day closes", l.closeDay().ok());
        check("after closing there is no current day", l.day() == null);
        check("the folder knows the day is closed", l.days().openDay() == null);

        c.set("2026-10-13T06:00:00");
        check("the next scan opens a new day, dated today",
              l.add(P + "03", "LOT-B").ok() && "2026-10-13".equals(l.day().date()));
        check("the new day's rows continue from 1 (a new date)", l.day().row(1) != null);
        l.export(false);
        l.closeDay();
        check("a second day on the same date is _2", l.add(P + "04", "LOT-C").ok()
              && l.day().file().getName().equals("2026-10-13_2.giornale"));
        check("_2 continues the row numbers of the first", l.day().row(2) != null && l.day().row(1) == null);
        check("_2 continues the export numbers", l.day().nextExportNumber() == 2);

        // a closed day: rows frozen, copies and re-exports still recorded
        Day closed = Day.open(first, new Day.Init("L2", "2026-10-12", 0, 0), c);
        check("a closed day refuses new rows", closed.add(P + "99", "X").kind == Day.Result.Kind.READ_ONLY);
        check("a closed day refuses edits", closed.modify(1, P + "01", "X", "").kind == Day.Result.Kind.READ_ONLY);
        check("a closed day records a late copy", closed.recordCopy(1, "E:\\\\", true, "").ok());
        Exporter.Result re = new Exporter(cfg.exportDir(), null, c).reexport(closed);
        check("a closed day can be re-exported whole", re.ok && closed.export(2) != null);
        closed.journal().close();
        check("a closed day with events after CHIUDI is still closed", Days.endsClosed(first));

        // a restart finds the open day again
        l.day().journal().close();
        Ledger again = new Ledger(cfg, c);
        again.open();
        check("a restart reopens the open day with its rows",
              again.day() != null && again.day().file().getName().equals("2026-10-13_2.giornale")
              && again.day().liveCount() == 1);
        check("the same label is still a duplicate after the restart",
              again.add(P + "04", "LOT-C").kind == Day.Result.Kind.DUPLICATE);
        check("rows of a closed day do not block today's labels",
              again.add(P + "01", "LOT-A").ok());
        check("the archive kept every file", Arrays.asList(cfg.exportDir().list()).size() == 3);

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    static void check(String name, boolean cond) {
        if (cond) { passed++; System.out.println("  ok   " + name); }
        else      { failed++; System.out.println("  FAIL " + name); }
    }
}
