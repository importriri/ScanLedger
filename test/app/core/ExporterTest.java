package app.core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

// the export is where rows leave the station, so every step of it has a test:
// incremental files that never overlap, the read-back, the archive that never
// overwrites, the backup copy that waits for its folder, and the crash in the
// one gap that matters — the file renamed, the event not yet written.
//
//   java -cp out app.core.ExporterTest
public final class ExporterTest {

    static final String P = "209531.001_01-01_26120004";
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        threeExports_areIncremental_withCorrections();
        nothingToExport_writesNoFile();
        readBackMismatch_deletesTheFile_recordsNothing();
        nameAlreadyInTheArchive_isAnError();
        failedCopy_staysPending_untilTheFolderIsBack();
        crashBetweenRenameAndEvent_isRecoveredAtStart();
        reexport_carriesEveryLiveRow_changesNoState();
        readOnlyDay_exportsReadableRows_withoutRecording();

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    static File tmp() throws Exception {
        return Files.createTempDirectory("rqr-export").toFile();
    }

    static CsvReader.Result read(File f) throws Exception {
        return CsvReader.read(Files.readAllBytes(f.toPath()));
    }

    private static void threeExports_areIncremental_withCorrections() throws Exception {
        File dir = tmp();
        TestClock c = new TestClock("2026-10-13T07:00:00", 1000);
        Day d = new Days(dir, c).createNew("L2");
        Exporter x = new Exporter(new File(dir, "export"), null, c);
        d.add(P + "01", "LOT-A");
        d.add(P + "02", "LOT-A");
        d.add(P + "03", "LOT-A");
        Exporter.Result r1 = x.export(d);
        d.modify(2, P + "02", "LOT-B", "");
        d.add(P + "04", "LOT-C");
        Exporter.Result r2 = x.export(d);
        d.modify(2, P + "02", "LOT-D", "");
        d.modify(4, P + "04", "LOT-E", "");
        d.add(P + "05", "LOT-F");
        Exporter.Result r3 = x.export(d);
        check("three exports succeed", r1.ok && r2.ok && r3.ok);
        CsvReader.Result f1 = read(r1.file), f2 = read(r2.file), f3 = read(r3.file);
        check("all three files are valid", f1.ok() && f2.ok() && f3.ok());
        check("export numbers follow 1, 2, 3",
              f1.exportNumber() == 1 && f2.exportNumber() == 2 && f3.exportNumber() == 3);
        check("export 1 carries the three new rows", f1.rows.size() == 3);
        check("export 2 carries the correction of 2 and the new 4",
              f2.rows.size() == 2 && f2.rows.get(0).n == 2 && f2.rows.get(0).isCorrection()
              && "LOT-A".equals(f2.rows.get(0).lotBefore) && f2.rows.get(1).n == 4
              && !f2.rows.get(1).isCorrection());
        check("export 3 corrects 2 against what export 2 sent, corrects 4, adds 5",
              f3.rows.size() == 3 && "LOT-B".equals(f3.rows.get(0).lotBefore)
              && "LOT-D".equals(f3.rows.get(0).lot) && f3.rows.get(1).isCorrection()
              && "LOT-C".equals(f3.rows.get(1).lotBefore) && f3.rows.get(2).n == 5);
        check("no tmp file is left in the archive",
              Arrays.stream(new File(dir, "export").list()).noneMatch(n -> n.endsWith(".tmp")));
        check("nothing is pending after the third export", d.pendingExport().isEmpty());
    }

    private static void nothingToExport_writesNoFile() throws Exception {
        File dir = tmp();
        TestClock c = new TestClock("2026-10-13T07:00:00", 1000);
        Day d = new Days(dir, c).createNew("L2");
        Exporter.Result r = new Exporter(new File(dir, "export"), null, c).export(d);
        check("an empty day exports nothing, and says so", !r.ok && r.message.contains("Niente da esportare"));
        String[] files = new File(dir, "export").list();
        check("no file is written", files == null || files.length == 0);
    }

    private static void readBackMismatch_deletesTheFile_recordsNothing() throws Exception {
        File dir = tmp();
        TestClock c = new TestClock("2026-10-13T07:00:00", 1000);
        Day d = new Days(dir, c).createNew("L2");
        d.add(P + "01", "LOT-A");
        Exporter x = new Exporter(new File(dir, "export"), null, c);
        File target = new File(dir, "export/" + CsvWriter.fileName("L2", "2026-10-13", 1));
        // something rewrites the file between our write and our read-back
        x.afterRename = () -> {
            try {
                Files.write(target.toPath(), "garbage".getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        int events = d.eventCount();
        Exporter.Result r = x.export(d);
        check("a file that does not read back right is an error", !r.ok);
        check("the bad file is deleted", !target.exists());
        check("no ESPORTA is recorded", d.eventCount() == events && d.exports().isEmpty());
        check("the row is still waiting to be exported", d.pendingExport().equals(Arrays.asList(1)));
    }

    private static void nameAlreadyInTheArchive_isAnError() throws Exception {
        File dir = tmp();
        TestClock c = new TestClock("2026-10-13T07:00:00", 1000);
        Day d = new Days(dir, c).createNew("L2");
        d.add(P + "01", "LOT-A");
        File exp = new File(dir, "export");
        exp.mkdirs();
        File squatter = new File(exp, CsvWriter.fileName("L2", "2026-10-13", 1));
        Files.write(squatter.toPath(), "qualcosa".getBytes(StandardCharsets.UTF_8));
        Exporter.Result r = new Exporter(exp, null, c).export(d);
        check("a name already in the archive is an error, not an overwrite", !r.ok && r.message.contains("Esiste già"));
        check("the file already there is untouched",
              "qualcosa".equals(new String(Files.readAllBytes(squatter.toPath()), StandardCharsets.UTF_8)));
        check("nothing is recorded", d.exports().isEmpty());
    }

    private static void failedCopy_staysPending_untilTheFolderIsBack() throws Exception {
        File dir = tmp();
        TestClock c = new TestClock("2026-10-13T07:00:00", 1000);
        Day d = new Days(dir, c).createNew("L2");
        d.add(P + "01", "LOT-A");
        File stick = new File(dir, "chiavetta");   // not plugged in: the folder is missing
        Exporter x = new Exporter(new File(dir, "export"), stick, c);
        Exporter.Result r = x.export(d);
        check("the export is valid even when the copy fails", r.ok && r.file.exists());
        check("the copy is recorded as failed, with the reason",
              "FALLITA".equals(r.copy) && d.export(1).copyPending() && !r.copyReason.isEmpty());
        d.journal().close();

        // next start: the stick is back
        stick.mkdirs();
        Day again = Day.open(d.file(), new Day.Init("L2", "2026-10-13", 0, 0), c);
        check("the failed copy is still pending after a restart", Exporter.pendingCopies(again) == 1);
        int left = new Exporter(new File(dir, "export"), stick, c).retryCopies(again);
        check("the retry at start succeeds", left == 0 && "OK".equals(again.export(1).copyOutcome()));
        check("the export file is in the copy folder", new File(stick, r.file.getName()).exists());
        check("the day journal travels with it", new File(stick, again.file().getName()).exists());
    }

    private static void crashBetweenRenameAndEvent_isRecoveredAtStart() throws Exception {
        File dir = tmp();
        TestClock c = new TestClock("2026-10-13T07:00:00", 1000);
        Day d = new Days(dir, c).createNew("L2");
        d.add(P + "01", "LOT-A");
        d.add(P + "02", "LOT-B");
        Exporter x = new Exporter(new File(dir, "export"), null, c);
        x.afterRename = () -> { throw new IllegalStateException("corrente staccata"); };
        try {
            x.export(d);
        } catch (IllegalStateException expected) {
            // the program died right here
        }
        File f = new File(dir, "export/" + CsvWriter.fileName("L2", "2026-10-13", 1));
        check("after the crash the file is in the archive", f.exists());
        check("after the crash the journal does not know it", d.exports().isEmpty());
        d.journal().close();

        Day again = Day.open(d.file(), new Day.Init("L2", "2026-10-13", 0, 0), c);
        List<String> found = new Exporter(new File(dir, "export"), null, c).recoverMissing(again);
        check("the start finds the file and records the missing event",
              found.equals(Arrays.asList(f.getName())) && again.export(1) != null);
        check("the recovered event carries the file's fingerprint",
              read(f).computedSha.equals(again.export(1).sha256));
        check("the rows are exported: the next export will not repeat them", again.pendingExport().isEmpty());
        check("a second look finds nothing more",
              new Exporter(new File(dir, "export"), null, c).recoverMissing(again).isEmpty());
        again.add(P + "03", "LOT-C");
        Exporter.Result next = new Exporter(new File(dir, "export"), null, c).export(again);
        check("the next export is number 2 with only the new row",
              next.ok && read(next.file).exportNumber() == 2 && read(next.file).rows.size() == 1);
    }

    private static void reexport_carriesEveryLiveRow_changesNoState() throws Exception {
        File dir = tmp();
        TestClock c = new TestClock("2026-10-13T07:00:00", 1000);
        Day d = new Days(dir, c).createNew("L2");
        d.add(P + "01", "LOT-A");
        d.add(P + "02", "LOT-A");
        d.add(P + "03", "LOT-A");
        d.delete(3);
        Exporter x = new Exporter(new File(dir, "export"), null, c);
        x.export(d);
        d.modify(1, P + "01", "LOT-Z", "");
        Exporter.Result r = x.reexport(d);
        CsvReader.Result f = read(r.file);
        check("a re-export is marked riesportazione=1", r.ok && f.reexport());
        check("a re-export carries every live row, all NUOVA, current values",
              f.rows.size() == 2 && f.rows.stream().allMatch(row -> "NUOVA".equals(row.tipo))
              && "LOT-Z".equals(f.rows.get(0).lot));
        check("a re-export has its own new number", f.exportNumber() == 2);
        check("a re-export changes no state: the correction is still pending",
              d.pendingExport().equals(Arrays.asList(1)));
    }

    private static void readOnlyDay_exportsReadableRows_withoutRecording() throws Exception {
        File dir = tmp();
        TestClock c = new TestClock("2026-10-13T07:00:00", 1000);
        Day d = new Days(dir, c).createNew("L2");
        for (int i = 1; i <= 4; i++) d.add(P + "0" + i, "LOT-A");
        d.journal().close();
        byte[] all = Files.readAllBytes(d.file().toPath());
        String t = new String(all, StandardCharsets.UTF_8);
        int third = t.indexOf('\n', t.indexOf('\n', t.indexOf('\n') + 1) + 1) + 1;
        all[third + 1] ^= 1;   // break line 4, keep the rest
        Files.write(d.file().toPath(), all);
        Day ro = Day.open(d.file(), new Day.Init("L2", "2026-10-13", 0, 0), c);
        check("the damaged day is read only", ro.isCorrupted());
        Exporter.Result r = new Exporter(new File(dir, "export"), null, c).export(ro);
        check("its readable rows can still be exported", r.ok && read(r.file).ok());
        check("as a re-export, so AutoFillSuite always pre-flights it", read(r.file).reexport());
    }

    static void check(String name, boolean cond) {
        if (cond) { passed++; System.out.println("  ok   " + name); }
        else      { failed++; System.out.println("  FAIL " + name); }
    }
}
