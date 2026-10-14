package app.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

// the test that matters: a second JVM records pairs in a loop and announces
// on stdout every seq it has seen forced to disk; this JVM then kills it with
// destroyForcibly() — the Task Manager's "End task" — at a random moment, and
// reads the journal back. every announced seq must be there; at most ONE more
// event may exist (written, killed before the announcement); never one less.
// 20 rounds on the same day, so each round also reopens a killed journal.
//
//   java -cp out app.core.CrashTest           (prints the seed of any failure)
public final class CrashTest {

    private static final String P = "209531.001_01-01_";
    private static final String FREE =
        "CA HVCO 50 T 048 3-2; M-CA HVCO 50 T 048 3-2  Rev.04; Serial No.: 000066076 ; 2023/12/11;A";

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        if (args.length >= 2 && "--child".equals(args[0])) {
            child(new File(args[1]));
            return;
        }
        if (args.length >= 2 && "--child-export".equals(args[0])) {
            childExport(new File(args[1]));
            return;
        }
        long base = args.length > 0 ? Long.parseLong(args[0]) : System.nanoTime();
        File dir = Files.createTempDirectory("rqr-crash").toFile();

        int rounds = 20;
        int good = 0;
        long previousLast = 0;
        for (int i = 0; i < rounds; i++) {
            long seed = base + i;
            String why = round(dir, seed, previousLast);
            if (why == null) {
                good++;
            } else {
                System.out.println("  FAIL round " + (i + 1) + " seed " + seed + ": " + why);
            }
            Journal.Replay r = Journal.read(new Days(dir, Clock.systemDefaultZone()).openDay().file);
            previousLast = r.events.isEmpty() ? 0 : r.events.get(r.events.size() - 1).seq;
        }
        check("every confirmed pair survives " + rounds + " forced kills (seed base " + base + ")",
              good == rounds);

        Day d = new Days(dir, Clock.systemDefaultZone()).openCurrent("L2");
        check("the killed day reopens writable", d != null && !d.isReadOnly());
        check("the reopened day still takes a pair", d != null && d.add(P + "X999999", FREE).ok());
        System.out.println("       " + (d == null ? 0 : d.rowCount()) + " pairs recorded across the kills");

        // the same, killed while EXPORTING: file and event must agree afterwards
        File xdir = Files.createTempDirectory("rqr-crash-export").toFile();
        int xgood = 0, xrounds = 10;
        for (int i = 0; i < xrounds; i++) {
            long seed = base + 100 + i;
            String why = exportRound(xdir, seed);
            if (why == null) xgood++;
            else System.out.println("  FAIL export round " + (i + 1) + " seed " + seed + ": " + why);
        }
        check("killed mid-export " + xrounds + " times: every file has its event, every event its file",
              xgood == xrounds);

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    /** null when the round holds; otherwise what went wrong. */
    private static String round(File dir, long seed, long previousLast) throws Exception {
        Random rnd = new Random(seed);
        int delay = 200 + rnd.nextInt(1301);
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        Process p = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                                       "app.core.CrashTest", "--child", dir.getPath())
            .redirectErrorStream(true).start();
        AtomicLong announced = new AtomicLong(previousLast);
        StringBuilder noise = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("ok ") || line.startsWith("open ")) {
                        long s = Long.parseLong(line.substring(line.indexOf(' ') + 1).trim());
                        announced.accumulateAndGet(s, Math::max);
                    } else if (noise.length() < 2000) {
                        noise.append(line).append('\n');
                    }
                }
            } catch (IOException ignored) {
                // the pipe dies with the process
            }
        }, "crash-reader");
        reader.start();
        Thread.sleep(delay);
        p.destroyForcibly();
        p.waitFor();
        reader.join(3000);

        Days days = new Days(dir, Clock.systemDefaultZone());
        Days.Info open = days.openDay();
        if (open == null) return "no open day after the kill" + (noise.length() > 0 ? ": " + noise : "");
        Journal.Replay r = Journal.recover(open.file, Clock.systemDefaultZone());
        if (r.corrupted) return "journal corrupted: " + r.error;
        long last = r.events.isEmpty() ? 0 : r.events.get(r.events.size() - 1).seq;
        long confirmed = announced.get();
        if (last < confirmed) return "lost events: confirmed " + confirmed + ", on disk " + last;
        if (last > confirmed + 1) return "too many events: confirmed " + confirmed + ", on disk " + last;
        System.out.println("  ..   round seed " + seed + ": killed at " + delay + " ms, "
                           + confirmed + " confirmed, " + last + " on disk"
                           + (r.hasDiscardedTail() ? ", torn tail cut" : ""));
        return null;
    }

    /** null when, after recovery, the archive and the journal agree exactly. */
    private static String exportRound(File dir, long seed) throws Exception {
        Random rnd = new Random(seed);
        int delay = 200 + rnd.nextInt(1301);
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        Process p = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                                       "app.core.CrashTest", "--child-export", dir.getPath())
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(new File(dir, "child.log"))).start();
        Thread.sleep(delay);
        p.destroyForcibly();
        p.waitFor();

        Days days = new Days(dir, Clock.systemDefaultZone());
        Day d = days.openCurrent("L2");
        if (d == null) return null;   // killed before the day even opened
        if (d.isCorrupted()) return "journal corrupted: " + d.replay().error;
        File exp = new File(dir, "export");
        List<String> recovered = new Exporter(exp, null, Clock.systemDefaultZone()).recoverMissing(d);
        String[] names = exp.list();
        int files = 0;
        if (names != null) {
            for (String n : names) {
                if (n.endsWith(".tmp")) { d.journal().close(); return "a .tmp survived the start: " + n; }
                CsvReader.Result r = CsvReader.read(Files.readAllBytes(new File(exp, n).toPath()));
                if (!r.ok()) { d.journal().close(); return n + " is not valid: " + r.status; }
                ExportRecord x = d.export(r.exportNumber());
                if (x == null) { d.journal().close(); return n + " has no ESPORTA event"; }
                if (!x.sha256.equals(r.computedSha)) { d.journal().close(); return n + " differs from its event"; }
                files++;
            }
        }
        for (ExportRecord x : d.exports()) {
            if (!new File(exp, x.fileName).exists()) {
                d.journal().close();
                return "event " + x.number + " has no file";
            }
        }
        System.out.println("  ..   export round seed " + seed + ": killed at " + delay + " ms, "
                           + files + " files, " + d.exports().size() + " events"
                           + (recovered.isEmpty() ? "" : ", recovered " + recovered));
        d.journal().close();
        return null;
    }

    /** The victim of the export rounds: three pairs, one export, again. */
    private static void childExport(File dir) throws IOException {
        Days days = new Days(dir, Clock.systemDefaultZone());
        Day d = days.openCurrent("L2");
        if (d == null) d = days.createNew("L2");
        Exporter x = new Exporter(new File(dir, "export"), null, Clock.systemDefaultZone());
        x.recoverMissing(d);
        for (;;) {
            for (int k = 0; k < 3; k++) {
                d.add(P + String.format("%010d", d.maxN() + 1), FREE);
            }
            x.export(d);
        }
    }

    /** The victim: records pairs as fast as the disk allows, announcing each one. */
    private static void child(File dir) throws IOException {
        Days days = new Days(dir, Clock.systemDefaultZone());
        Day d = days.openCurrent("L2");
        if (d == null) d = days.createNew("L2");
        System.out.println("open " + d.journal().lastSeq());
        System.out.flush();
        for (int i = 0; ; i++) {
            String label = P + String.format("%010d", d.maxN() + 1);
            Day.Result r = d.add(label, i % 2 == 0 ? FREE : "900612202601310007");
            if (!r.ok()) {
                System.out.println("refused " + r.message);
                System.out.flush();
                return;
            }
            System.out.println("ok " + r.event.seq);
            System.out.flush();
        }
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
