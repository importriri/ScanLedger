package app.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

// the journal is the promise "however it is closed, nothing is lost". each
// test below pins one half of it: what is written comes back identical, and
// what a shutdown or a broken disk does to the file never invents, drops or
// silently skips an event.
//
//   java -cp out app.core.JournalTest
public final class JournalTest {

    static final String P = "209531.001_01-01_2612000";
    static final String LOT_A = "900612202601310007";
    static final String LOT_B = "900612202601310008";
    static final String LOT_C = "900612202601310009";
    static final String FREE =
        "CA HVCO 50 T 048 3-2; M-CA HVCO 50 T 048 3-2  Rev.04; Serial No.: 000066076 ; 2023/12/11;A";

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && "--lock-child".equals(args[0])) {
            DataLock l = DataLock.acquire(new File(args[1]));
            System.out.println(l == null ? "LOCKED" : "ACQUIRED");
            return;
        }

        writeReread_rebuildsTheSameModel_forEveryEventType();
        duplicate_isRefused_withTheExistingRow();
        range_withOneDuplicate_isRefusedWhole();
        modify_neverExported_changesLabelAndLot_withoutDuplicates();
        exportedRow_labelIsFrozen_lotBecomesACorrection();
        doubleCorrection_keepsTheLotOfTheExport();
        correctionBackToExportedLot_cancelsIt();
        exportedRow_cannotBeDeleted_norUndone();
        undo_removesTheLastPairOrTheWholeRange();
        export_listsNewRowsAndCorrections_withTheirValuesThen();
        freeTextLots_surviveTheRoundTrip();
        truncation_atEveryByte_isAlwaysAnExactPrefix();
        tornLastLine_isCut_andItsPairIsReported();
        corruptionInTheMiddle_opensReadOnly_andKeepsTheOriginal();
        lock_secondAcquisition_fails();
        closedDay_reopensAsNew_withContinuedNumbering();

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    static File tempDir() throws IOException {
        File d = Files.createTempDirectory("rqr-journal").toFile();
        d.deleteOnExit();
        return d;
    }

    static Day newDay(File dir, TestClock clock) throws IOException {
        return new Days(dir, clock).createNew("L2");
    }

    static Day reopen(Day d, TestClock clock) throws IOException {
        if (d.journal() != null) d.journal().close();
        return Day.open(d.file(), new Day.Init("L2", d.date(), 0, 0), clock);
    }

    static boolean sameRows(Day a, Day b) {
        List<Row> ra = a.rows(), rb = b.rows();
        if (ra.size() != rb.size()) return false;
        for (int i = 0; i < ra.size(); i++) {
            Row x = ra.get(i), y = rb.get(i);
            boolean same = x.n == y.n && x.label().equals(y.label()) && x.lot().equals(y.lot())
                && x.mode == y.mode && x.created.equals(y.created) && x.rangeN0 == y.rangeN0
                && x.modified() == y.modified() && x.deleted() == y.deleted()
                && eq(x.exportedLot(), y.exportedLot()) && x.lastExport() == y.lastExport()
                && eq(x.correctedAt(), y.correctedAt()) && x.state() == y.state()
                && x.lastReason().equals(y.lastReason());
            if (!same) {
                System.out.println("       differs: " + x + " vs " + y);
                return false;
            }
        }
        return true;
    }

    static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    static void deleteTree(File d) throws IOException {
        if (!d.exists()) return;
        try (Stream<java.nio.file.Path> s = Files.walk(d.toPath())) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    // ── tests ────────────────────────────────────────────────────────────

    private static void writeReread_rebuildsTheSameModel_forEveryEventType() throws Exception {
        File dir = tempDir();
        TestClock clock = new TestClock("2026-10-13T06:58:02", 1300);
        Day d = newDay(dir, clock);
        d.add(P + "231", LOT_A);
        d.add(P + "232", LOT_A);
        d.addRange(Arrays.asList(P + "233", P + "234"), LOT_B);
        d.add(P + "235", LOT_B);
        d.delete(5);
        d.add(P + "236", FREE);
        d.modify(4, P + "234", LOT_B, "rifatta");
        d.restore(5);
        d.delete(5);
        d.recordExport(1, "2026-10-13T12:00:41", "abc", "f1.csv", false, Arrays.asList(1, 2, 3, 4, 6));
        d.recordCopy(1, "/media/chiavetta", false, "cartella assente");
        d.recordCopy(1, "/media/chiavetta", true, "");
        d.modify(2, P + "232", LOT_C, "lotto sbagliato");
        d.addRange(Arrays.asList(P + "240", P + "241", P + "242"), LOT_C);
        d.undoLast();
        d.add(P + "237", LOT_C);
        d.recordExport(2, "2026-10-13T17:28:41", "def", "f2.csv", false, Arrays.asList(2, 10));
        Day.Result closed = d.close();
        check("a fully exported day can be closed", closed.ok());

        Day back = reopen(d, clock);
        check("every event comes back (" + back.eventCount() + ")",
              back.eventCount() == d.eventCount());
        check("rows are identical field by field after a reread", sameRows(d, back));
        check("station and date come back", "L2".equals(back.station())
              && "2026-10-13".equals(back.date()));
        check("exports come back with their lines",
              back.exports().size() == 2 && back.export(1).lines().size() == 5
              && back.export(2).lines().size() == 2);
        check("the copy status comes back (last attempt wins)",
              "OK".equals(back.export(1).copyOutcome()));
        check("the closed day reopens closed and read only",
              back.isClosed() && back.isReadOnly());
        check("a closed day refuses new pairs",
              back.add(P + "299", LOT_A).kind == Day.Result.Kind.READ_ONLY);
    }

    private static void duplicate_isRefused_withTheExistingRow() throws Exception {
        Day d = newDay(tempDir(), new TestClock("2026-10-13T07:00:00", 1000));
        d.add(P + "231", LOT_A);
        d.add(P + "232", LOT_A);
        int events = d.eventCount();
        Day.Result r = d.add(P + "231", LOT_B);
        check("a label already in the day is a DUPLICATE",
              r.kind == Day.Result.Kind.DUPLICATE);
        check("the duplicate names the existing row", r.existingN == 1);
        check("a refused pair writes nothing", d.eventCount() == events);
        d.delete(1);
        check("a deleted row does not block the same label again",
              d.add(P + "231", LOT_B).ok());
        check("restoring the deleted twin is refused as a duplicate",
              d.restore(1).kind == Day.Result.Kind.DUPLICATE);
        check("a pair with a TAB inside is refused",
              d.add(P + "250", "A\tB").kind == Day.Result.Kind.REFUSED);
        check("spaces at the edges are trimmed, inner ones kept",
              d.add("  " + P + "251 ", " LOT  X ").ok()
              && "LOT  X".equals(d.findLive(P + "251").lot()));
    }

    private static void range_withOneDuplicate_isRefusedWhole() throws Exception {
        Day d = newDay(tempDir(), new TestClock("2026-10-13T07:00:00", 1000));
        d.add(P + "233", LOT_A);
        int events = d.eventCount(), rows = d.rowCount();
        Day.Result r = d.addRange(Arrays.asList(P + "232", P + "233", P + "234"), LOT_B);
        check("a range touching one existing label is refused",
              r.kind == Day.Result.Kind.DUPLICATE);
        check("the refusal lists the overlapping labels",
              r.duplicates.equals(Arrays.asList(P + "233")));
        check("nothing of a refused range is written", d.eventCount() == events);
        check("no row of a refused range appears", d.rowCount() == rows);
        Day.Result ok = d.addRange(Arrays.asList(P + "240", P + "241", P + "242"), LOT_B);
        check("a clean range is one single event", ok.ok() && d.eventCount() == events + 1);
        check("a clean range adds all its rows", d.rowCount() == rows + 3);
        check("range rows share the lot and the mode",
              d.row(2).lot().equals(LOT_B) && d.row(4).mode == Row.Mode.INTERVALLO);
    }

    private static void modify_neverExported_changesLabelAndLot_withoutDuplicates() throws Exception {
        Day d = newDay(tempDir(), new TestClock("2026-10-13T07:00:00", 1000));
        d.add(P + "231", LOT_A);
        d.add(P + "232", LOT_A);
        Day.Result r = d.modify(1, P + "239", LOT_B, "");
        check("an unexported row takes a new label and lot",
              r.ok() && P.concat("239").equals(d.row(1).label()) && LOT_B.equals(d.row(1).lot()));
        check("an edited row is marked modified", d.row(1).modified());
        check("an edited unexported row stays LIVE", d.row(1).state() == Row.State.LIVE);
        check("an edit that would duplicate another label is refused",
              d.modify(1, P + "232", LOT_B, "").kind == Day.Result.Kind.DUPLICATE);
        check("an edit with nothing changed writes nothing",
              d.modify(2, P + "232", LOT_A, "").kind == Day.Result.Kind.UNCHANGED);
    }

    private static void exportedRow_labelIsFrozen_lotBecomesACorrection() throws Exception {
        Day d = newDay(tempDir(), new TestClock("2026-10-13T07:00:00", 1000));
        d.add(P + "232", LOT_A);
        d.recordExport(1, "2026-10-13T12:00:41", "h", "f", false, Arrays.asList(1));
        check("an exported row reads EXPORTED", d.row(1).state() == Row.State.EXPORTED);
        Day.Result r = d.modify(1, P + "299", LOT_A, "");
        check("the label of an exported row cannot change", r.kind == Day.Result.Kind.REFUSED);
        check("the refusal says why: it is on the portal",
              r.message.contains("già sul portale") && r.message.contains("Delete item"));
        Day.Result c = d.modify(1, P + "232", LOT_C, "lotto sbagliato");
        check("the lot of an exported row can be corrected", c.ok());
        check("a corrected exported row reads CORRECTED", d.row(1).state() == Row.State.CORRECTED);
        check("the correction goes into the next export", d.pendingExport().equals(Arrays.asList(1)));
    }

    private static void doubleCorrection_keepsTheLotOfTheExport() throws Exception {
        Day d = newDay(tempDir(), new TestClock("2026-10-13T07:00:00", 1000));
        d.add(P + "232", LOT_A);
        d.recordExport(1, "c", "h", "f", false, Arrays.asList(1));
        d.modify(1, P + "232", LOT_B, "");
        d.modify(1, P + "232", LOT_C, "");
        d.recordExport(2, "c", "h", "f", false, Arrays.asList(1));
        ExportRecord.Line l = d.export(2).lines().get(0);
        check("two corrections before an export go out as ONE correction", d.export(2).lines().size() == 1);
        check("the correction carries the latest lot", LOT_C.equals(l.lot));
        check("lotto_prima is the lot of the last export, not the value in between",
              LOT_A.equals(l.lotBefore));
        check("the line is typed CORREZIONE", l.isCorrection());
        check("after the export the new lot is the reference", LOT_C.equals(d.row(1).exportedLot())
              && d.row(1).state() == Row.State.EXPORTED);
    }

    private static void correctionBackToExportedLot_cancelsIt() throws Exception {
        Day d = newDay(tempDir(), new TestClock("2026-10-13T07:00:00", 1000));
        d.add(P + "232", LOT_A);
        d.recordExport(1, "c", "h", "f", false, Arrays.asList(1));
        d.modify(1, P + "232", LOT_B, "");
        d.modify(1, P + "232", LOT_A, "ripensamento");
        check("a correction back to the exported lot cancels it",
              d.row(1).state() == Row.State.EXPORTED);
        check("nothing is left to export", d.pendingExport().isEmpty());
        check("the row still says it was touched by hand", d.row(1).modified());
    }

    private static void exportedRow_cannotBeDeleted_norUndone() throws Exception {
        Day d = newDay(tempDir(), new TestClock("2026-10-13T07:00:00", 1000));
        d.add(P + "232", LOT_A);
        d.recordExport(1, "c", "h", "f", false, Arrays.asList(1));
        Day.Result r = d.delete(1);
        check("an exported row cannot be deleted", r.kind == Day.Result.Kind.REFUSED);
        check("the delete refusal says why", r.message.contains("già sul portale"));
        check("an exported row cannot be undone either",
              d.undoLast().kind == Day.Result.Kind.REFUSED);
    }

    private static void undo_removesTheLastPairOrTheWholeRange() throws Exception {
        Day d = newDay(tempDir(), new TestClock("2026-10-13T07:00:00", 1000));
        d.add(P + "231", LOT_A);
        d.addRange(Arrays.asList(P + "240", P + "241", P + "242"), LOT_B);
        Day.Result r = d.undoLast();
        check("undo after a range removes the whole range in one event",
              r.ok() && r.event.type == Event.Type.ELIMINA_INTERVALLO);
        check("every row of the range is deleted",
              d.row(2).deleted() && d.row(3).deleted() && d.row(4).deleted());
        check("the pair before the range is untouched", !d.row(1).deleted());
        check("a second undo does not walk back into older rows",
              d.undoLast().kind == Day.Result.Kind.UNCHANGED && !d.row(1).deleted());
        d.add(P + "250", LOT_C);
        Day.Result p = d.undoLast();
        check("undo after a pair deletes just that pair",
              p.ok() && p.event.type == Event.Type.ELIMINA && d.row(5).deleted());
    }

    private static void export_listsNewRowsAndCorrections_withTheirValuesThen() throws Exception {
        TestClock clock = new TestClock("2026-10-13T06:58:02", 0);
        Day d = newDay(tempDir(), clock);
        d.add(P + "231", LOT_A);
        clock.set("2026-10-13T06:58:19");
        d.add(P + "232", LOT_A);
        check("both new rows are pending", d.pendingExport().equals(Arrays.asList(1, 2)));
        d.recordExport(1, "2026-10-13T12:00:41", "h1", "f1", false, d.pendingExport());
        clock.set("2026-10-13T13:10:05");
        d.modify(2, P + "232", LOT_C, "");
        clock.set("2026-10-13T13:12:30");
        d.add(P + "237", LOT_C);
        d.recordExport(2, "2026-10-13T17:28:41", "h2", "f2", false, d.pendingExport());
        List<ExportRecord.Line> lines = d.export(2).lines();
        check("export 2 carries the correction and the new row", lines.size() == 2);
        ExportRecord.Line corr = lines.get(0), nw = lines.get(1);
        check("the correction keeps its row number", corr.n == 2 && corr.isCorrection());
        check("a correction's time is when it was corrected", "2026-10-13T13:10:05".equals(corr.ora));
        check("a new row's time is when it was born", "2026-10-13T13:12:30".equals(nw.ora)
              && "NUOVA".equals(nw.tipo) && nw.lotBefore.isEmpty());
        check("export 1 still remembers the OLD lot of row 2",
              LOT_A.equals(d.export(1).lines().get(1).lot));
        check("the next export number follows the last", d.nextExportNumber() == 3);

        d.recordExport(3, "2026-10-13T18:00:00", "h3", "f3", true, Arrays.asList(1, 2, 3));
        check("a re-export writes every row as NUOVA",
              d.export(3).lines().stream().allMatch(l -> "NUOVA".equals(l.tipo)));
        check("a re-export changes no state", d.pendingExport().isEmpty()
              && d.row(2).lastExport() == 2);
    }

    private static void freeTextLots_surviveTheRoundTrip() throws Exception {
        File dir = tempDir();
        TestClock clock = new TestClock("2026-10-13T07:00:00", 1000);
        Day d = newDay(dir, clock);
        d.add(P + "236", FREE);
        d.add(P + "238", "LOTTO \"B\" 7");
        Day back = reopen(d, clock);
        check("the free-text lot (';', double spaces) comes back intact",
              FREE.equals(back.row(1).lot()));
        check("a lot with quotes comes back intact", "LOTTO \"B\" 7".equals(back.row(2).lot()));

        // the day refuses TAB and newlines at the scan, but the journal must
        // not depend on it: any text survives its escaping
        File f = new File(dir, "raw.giornale");
        Journal j = Journal.openForAppend(f, null, clock);
        String nasty = "a\\b\tc\"d\ne\rf\\\\t;g  h";
        j.append(Event.Type.AGGIUNGI, Journal.fields("1", "x\\t", nasty, "SCAN"));
        j.close();
        Journal.Replay r = Journal.read(f);
        check("backslash, TAB, CR, LF and quotes survive the journal",
              r.events.size() == 1 && nasty.equals(r.events.get(0).field(2))
              && "x\\t".equals(r.events.get(0).field(1)));
        check("an escaped field stays on one line",
              new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)
                  .split("\n", -1).length == 2);
    }

    private static void truncation_atEveryByte_isAlwaysAnExactPrefix() throws Exception {
        File dir = tempDir();
        TestClock clock = new TestClock("2026-10-13T07:00:00", 700);
        Day d = newDay(dir, clock);
        // 50 mixed events, with free-text and multi-byte content
        for (int i = 0; i < 49; i++) {
            switch (i % 7) {
                case 0: d.add(P + String.format("%03d", 100 + i), FREE); break;
                case 1: d.add(P + String.format("%03d", 100 + i), "LOTTO \"è\" " + i); break;
                case 2: d.addRange(Arrays.asList(P + (500 + i), P + (600 + i)), LOT_B); break;
                case 3: d.modify(d.maxN(), d.row(d.maxN()).label(), LOT_C, "motivo à " + i); break;
                case 4: d.delete(d.maxN()); break;
                case 5: d.restore(d.maxN()); break;
                default: d.recordExport(d.nextExportNumber(), "c", "h", "f", false, d.pendingExport());
            }
        }
        d.journal().close();
        byte[] all = Files.readAllBytes(d.file().toPath());
        List<Event> full = Journal.read(d.file()).events;
        check("the source journal holds 50 events", full.size() == 50);

        int bad = 0, cuts = 0;
        File copy = new File(dir, "cut.giornale");
        for (int len = all.length; len >= 0; len--) {
            Files.write(copy.toPath(), Arrays.copyOf(all, len));
            Journal.Replay r;
            try {
                r = Journal.recover(copy, clock);
            } catch (RuntimeException e) {
                bad++;
                System.out.println("       exception at " + len + ": " + e);
                continue;
            }
            boolean prefix = !r.corrupted && r.events.size() <= full.size();
            for (int i = 0; prefix && i < r.events.size(); i++) {
                Event a = r.events.get(i), b = full.get(i);
                prefix = a.seq == b.seq && a.ts.equals(b.ts) && a.type == b.type
                      && a.fields().equals(b.fields());
            }
            boolean boundary = len == 0 || all[len - 1] == '\n';
            if (!prefix || boundary == r.hasDiscardedTail()
                || Files.size(copy.toPath()) != r.goodLength) {
                bad++;
                if (bad < 5) System.out.println("       bad cut at " + len);
            }
            if (!boundary) cuts++;
        }
        check("every cut (" + all.length + " offsets) reads back an exact prefix", bad == 0);
        check("most cuts land inside a line and are reported as torn", cuts > all.length / 2);
    }

    private static void tornLastLine_isCut_andItsPairIsReported() throws Exception {
        File dir = tempDir();
        TestClock clock = new TestClock("2026-10-13T07:00:00", 1000);
        Day d = newDay(dir, clock);
        d.add(P + "231", LOT_A);
        d.add(P + "232", FREE);
        d.journal().close();
        byte[] all = Files.readAllBytes(d.file().toPath());
        // cut inside the CRC of the last line: label and lot are complete
        Files.write(d.file().toPath(), Arrays.copyOf(all, all.length - 4));
        Day back = Day.open(d.file(), new Day.Init("L2", d.date(), 0, 0), clock);
        Journal.Replay r = back.replay();
        check("a torn last line is reported", r.hasDiscardedTail());
        check("the report names the discarded label and lot",
              (P + "232").equals(r.discardedLabel) && FREE.equals(r.discardedLot));
        check("the day reopens writable without the torn pair",
              !back.isReadOnly() && back.rowCount() == 1);
        check("the same pair can be scanned again", back.add(P + "232", FREE).ok());
        check("the file holds no trace of the torn bytes",
              Journal.read(back.file()).events.size() == 3 && !Journal.read(back.file()).hasDiscardedTail());
    }

    private static void corruptionInTheMiddle_opensReadOnly_andKeepsTheOriginal() throws Exception {
        File dir = tempDir();
        TestClock clock = new TestClock("2026-10-13T07:00:00", 1000);
        Day d = newDay(dir, clock);
        for (int i = 0; i < 6; i++) d.add(P + (300 + i), LOT_A);
        d.journal().close();
        byte[] all = Files.readAllBytes(d.file().toPath());
        // flip one digit of the 4th line's lot: its CRC no longer matches
        String text = new String(all, StandardCharsets.UTF_8);
        int lineStart = 0;
        for (int i = 0; i < 3; i++) lineStart = text.indexOf('\n', lineStart) + 1;
        int at = text.indexOf(LOT_A, lineStart);
        byte[] broken = all.clone();
        broken[at] = (byte) (broken[at] == '9' ? '8' : '9');
        Files.write(d.file().toPath(), broken);

        Day back = null;
        String thrown = null;
        try {
            back = Day.open(d.file(), new Day.Init("L2", d.date(), 0, 0), clock);
        } catch (Exception e) {
            thrown = e.toString();
        }
        check("a corrupted journal never throws on open", thrown == null && back != null);
        if (back == null) return;
        check("a corrupted journal opens read only", back.isCorrupted() && back.isReadOnly());
        check("the read-only day holds the rows before the broken line", back.rowCount() == 2);
        check("the replay reports a serious error", back.replay().error != null);
        check("the original file is left exactly as it was",
              Arrays.equals(broken, Files.readAllBytes(d.file().toPath())));
        File copy = back.replay().corruptCopy;
        check("a .corrotto- copy sits next to it",
              copy != null && copy.getName().startsWith(d.file().getName() + ".corrotto-")
              && Arrays.equals(broken, Files.readAllBytes(copy.toPath())));
        check("a read-only day refuses new pairs",
              back.add(P + "399", LOT_A).kind == Day.Result.Kind.READ_ONLY);
        clock.set("2026-10-13T08:00:00");
        Day third = Day.open(d.file(), new Day.Init("L2", d.date(), 0, 0), clock);
        String[] copies = d.file().getParentFile().list((f, n) -> n.contains(".corrotto-"));
        check("opening it again does not make a second copy of the same bytes",
              copies != null && copies.length == 1 && copy.equals(third.replay().corruptCopy));
    }

    private static void lock_secondAcquisition_fails() throws Exception {
        File dir = tempDir();
        DataLock first = DataLock.acquire(dir);
        check("the first instance gets the lock", first != null);
        check("a second acquisition in the same process fails", DataLock.acquire(dir) == null);
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        Process p = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                                       "app.core.JournalTest", "--lock-child", dir.getPath())
            .redirectErrorStream(true).start();
        String out;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            out = br.readLine();
        }
        p.waitFor();
        check("a second process is refused while the first runs", "LOCKED".equals(out));
        if (first != null) first.close();
        check("after release the lock can be taken again", DataLock.acquire(dir) != null);
    }

    private static void closedDay_reopensAsNew_withContinuedNumbering() throws Exception {
        File dir = tempDir();
        TestClock clock = new TestClock("2026-10-13T07:00:00", 1000);
        Days days = new Days(dir, clock);
        Day d = days.createNew("L2");
        d.add(P + "231", LOT_A);
        d.add(P + "232", LOT_A);
        d.recordExport(1, "c", "h", "f", false, d.pendingExport());
        check("a day with pending rows cannot be closed",
              d.add(P + "233", LOT_A).ok()
              && d.close().kind == Day.Result.Kind.REFUSED);
        d.recordExport(2, "c", "h", "f", false, d.pendingExport());
        check("closing an exported day works", d.close().ok());
        check("no day is open after closing", days.openDay() == null);
        Day d2 = days.createNew("L2");
        check("the second day of the same date gets suffix _2",
              d2.file().getName().equals("2026-10-13_2.giornale"));
        check("its rows continue the numbering", d2.add(P + "240", LOT_B).ok() && d2.row(4) != null);
        check("its exports continue the numbering", d2.nextExportNumber() == 3);
        clock.set("2026-10-14T06:00:00");
        check("a day opened yesterday and still open is stale",
              days.staleOpenDays().size() == 1 && "2026-10-13".equals(days.staleOpenDays().get(0).date));
        if (d2.journal() != null) d2.journal().close();
        Day again = days.openCurrent("L2");
        check("the open day reopens past midnight, under its own date",
              again != null && "2026-10-13".equals(again.date()) && again.rowCount() == 1);
    }

    static void check(String name, boolean cond) {
        if (cond) {
            passed++;
            System.out.println("  ok   " + name);
        } else {
            failed++;
            System.out.println("  FAIL " + name);
        }
    }
}
