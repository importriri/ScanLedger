package app.core;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The export, in the order that keeps every crash recoverable:
 * <ol>
 * <li>new rows and pending corrections, by n (nothing to send: no file);</li>
 * <li>{@code .csv.tmp} written and forced, then renamed atomically;</li>
 * <li>the file read back with {@link CsvReader}: valid, and exactly those rows,
 *     or it is deleted and nothing is recorded;</li>
 * <li>the ESPORTA event: the rows become EXPORTED;</li>
 * <li>the copy folder, with a COPIA event either way.</li>
 * </ol>
 * The export folder is an archive: nothing in it is ever overwritten or
 * deleted (a name already there is an error, not a replacement).
 */
public final class Exporter {

    private static final DateTimeFormatter CREATED = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final File exportDir;
    private final File copyDir;
    private final Clock clock;

    /** Test hook: the moment between the rename and the event (a power cut). */
    Runnable afterRename;

    public Exporter(File exportDir, File copyDir, Clock clock) {
        this.exportDir = exportDir;
        this.copyDir = copyDir;
        this.clock = clock;
    }

    public File exportDir() { return exportDir; }
    public File copyDir()   { return copyDir; }

    /** What an export would carry, for the dialog. */
    public static final class Plan {
        public final int number;
        public final String fileName;
        public final List<ExportRecord.Line> lines;
        public final int newRows;
        public final int corrections;
        public final int modifiedNew;
        public final int deleted;
        public final boolean reexport;
        public final String preview;

        Plan(int number, String fileName, List<ExportRecord.Line> lines, int deleted,
             boolean reexport, String preview) {
            this.number = number;
            this.fileName = fileName;
            this.lines = lines;
            int nw = 0, corr = 0, mod = 0;
            for (ExportRecord.Line l : lines) {
                if (l.isCorrection()) corr++;
                else {
                    nw++;
                    if (l.modified) mod++;
                }
            }
            this.newRows = nw;
            this.corrections = corr;
            this.modifiedNew = mod;
            this.deleted = deleted;
            this.reexport = reexport;
            this.preview = preview;
        }

        public boolean isEmpty() {
            return lines.isEmpty();
        }
    }

    public Plan plan(Day day, boolean reexport) {
        int number = nextNumber(day);
        List<Integer> ns = reexport ? day.liveRows() : day.pendingExport();
        List<ExportRecord.Line> lines = day.linesFor(ns, reexport);
        int deleted = 0;
        for (Row r : day.rows()) if (r.deleted() && !r.everExported()) deleted++;
        String created = now();
        CsvWriter.Output out = CsvWriter.write(
            CsvWriter.header(day.station(), day.date(), number, created, reexport), lines);
        return new Plan(number, CsvWriter.fileName(day.station(), day.date(), number), lines,
                        deleted, reexport, preview(new String(out.bytes, java.nio.charset.StandardCharsets.UTF_8)));
    }

    /** The next free number: after the day's own, and after any file already in the archive. */
    int nextNumber(Day day) {
        int n = day.nextExportNumber();
        while (new File(exportDir, CsvWriter.fileName(day.station(), day.date(), n)).exists()
               && day.isCorrupted()) {
            n++;   // a read-only day cannot record its exports: never reuse a name
        }
        return n;
    }

    /** Outcome of an export. */
    public static final class Result {
        public final boolean ok;
        public final String message;
        public final File file;
        public final ExportRecord.Line[] lines;
        /** "", OK, FALLITA */
        public final String copy;
        public final String copyReason;
        public final int number;
        public final String sha256;

        Result(boolean ok, String message, File file, List<ExportRecord.Line> lines,
               String copy, String copyReason, int number, String sha256) {
            this.ok = ok;
            this.message = message;
            this.file = file;
            this.lines = lines == null ? new ExportRecord.Line[0] : lines.toArray(new ExportRecord.Line[0]);
            this.copy = copy;
            this.copyReason = copyReason;
            this.number = number;
            this.sha256 = sha256;
        }

        static Result fail(String why) {
            return new Result(false, why, null, null, "", "", 0, null);
        }
    }

    /** Incremental export: new rows and corrections only. */
    public Result export(Day day) {
        return run(day, false);
    }

    /** Every live row with its current value, all NUOVA, riesportazione=1. Changes no state. */
    public Result reexport(Day day) {
        return run(day, true);
    }

    private Result run(Day day, boolean reexport) {
        if (day == null) return Result.fail("Nessuna giornata da esportare");
        // a damaged journal cannot record anything: what it can do is hand the
        // readable rows over, as a re-export (AutoFillSuite always pre-flights those)
        boolean readOnly = day.isCorrupted();
        if (readOnly) reexport = true;
        List<Integer> ns = reexport ? day.liveRows() : day.pendingExport();
        if (ns.isEmpty()) return Result.fail("Niente da esportare: nessuna riga nuova e nessuna correzione");
        int number = nextNumber(day);
        String created = now();
        List<ExportRecord.Line> lines = day.linesFor(ns, reexport);
        CsvWriter.Output out = CsvWriter.write(
            CsvWriter.header(day.station(), day.date(), number, created, reexport), lines);
        String name = CsvWriter.fileName(day.station(), day.date(), number);
        File target = new File(exportDir, name);

        try {
            Files.createDirectories(exportDir.toPath());
        } catch (IOException e) {
            return Result.fail("La cartella export non si crea: " + e.getMessage());
        }
        if (target.exists()) {
            return Result.fail("Esiste già " + name + " nell'archivio: non si sovrascrive. "
                               + "Controlla la cartella export.");
        }
        try {
            writeAtomic(target, out.bytes);
        } catch (IOException e) {
            return Result.fail("Scrittura del file non riuscita: " + e.getMessage());
        }
        if (afterRename != null) afterRename.run();

        // read back what is REALLY on disk
        String problem;
        try {
            problem = verify(Files.readAllBytes(target.toPath()), lines, number, reexport);
        } catch (IOException e) {
            problem = "rilettura impossibile: " + e.getMessage();
        }
        if (problem != null) {
            if (!target.delete()) target.deleteOnExit();
            return Result.fail("Il file scritto non è valido (" + problem + "): cancellato, niente registrato");
        }

        if (readOnly) {
            return new Result(true, "Esportate le righe leggibili in " + name + " (giornata in sola lettura: "
                              + "il file è una riesportazione e non resta nel giornale)",
                              target, lines, "", "", number, out.sha256);
        }
        Day.Result rec = day.recordExport(number, created, out.sha256, name, reexport, ns);
        if (!rec.ok()) {
            // the journal does not know this file, and nothing has left the
            // station yet (the copy comes after the event): take it back. Left
            // there, it would block every later export of the day as soon as one
            // more pair changed what is pending. Only a power cut leaves such a
            // file behind, and recoverMissing handles that one.
            boolean gone = target.delete();
            return new Result(false, "Il giornale non ha registrato l'export (" + rec.message + "): "
                              + (gone ? "file tolto, le righe restano da esportare"
                                      : "il file " + name + " è rimasto nell'archivio, toglilo a mano"),
                              null, lines, "", "", number, out.sha256);
        }
        String[] copy = copyOne(day, number, target);
        return new Result(true, "Esportato " + name, target, lines, copy[0], copy[1], number, out.sha256);
    }

    /** null when the bytes are exactly the export of these lines. */
    static String verify(byte[] bytes, List<ExportRecord.Line> lines, int number, boolean reexport) {
        CsvReader.Result r = CsvReader.read(bytes);
        if (!r.ok()) return r.status + ": " + r.detail;
        if (r.exportNumber() != number) return "numero di export diverso";
        if (r.reexport() != reexport) return "riesportazione diversa";
        return sameRows(r.rows, lines) ? null : "righe diverse da quelle attese";
    }

    static boolean sameRows(List<CsvReader.CsvRow> read, List<ExportRecord.Line> want) {
        if (read.size() != want.size()) return false;
        for (int i = 0; i < read.size(); i++) {
            CsvReader.CsvRow a = read.get(i);
            ExportRecord.Line b = want.get(i);
            if (a.n != b.n || !a.label.equals(b.label) || !a.lot.equals(b.lot) || !a.ora.equals(b.ora)
                || !a.mode.equals(b.mode.name()) || !a.tipo.equals(b.tipo) || a.modified != b.modified
                || !a.lotBefore.equals(b.lotBefore)) return false;
        }
        return true;
    }

    // ── backup copy ──────────────────────────────────────────────────────

    /** {outcome, reason}; outcome "" when no copy folder is configured. */
    private String[] copyOne(Day day, int number, File source) {
        if (copyDir == null) return new String[] { "", "" };
        String reason = copyFile(source, new File(copyDir, source.getName()));
        if (reason == null && day.file() != null) {
            // the day journal travels too: a safety copy, read by nobody unless needed
            try {
                Files.copy(day.file().toPath(), new File(copyDir, day.file().getName()).toPath(),
                           StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                reason = "giornale non copiato: " + e.getMessage();
            }
        }
        boolean ok = reason == null;
        day.recordCopy(number, copyDir.getPath(), ok, ok ? "" : reason);
        return new String[] { ok ? "OK" : "FALLITA", ok ? "" : reason };
    }

    /** null on success; a reason otherwise. Never overwrites a different file. */
    static String copyFile(File source, File dest) {
        try {
            File dir = dest.getParentFile();
            if (dir == null || !dir.isDirectory()) return "la cartella di copia non c'è (" + dir + ")";
            byte[] want = Files.readAllBytes(source.toPath());
            if (dest.exists()) {
                if (Arrays.equals(want, Files.readAllBytes(dest.toPath()))) return null;
                return "nella cartella di copia c'è già un " + dest.getName() + " diverso";
            }
            File tmp = new File(dir, dest.getName() + ".tmp");
            Files.write(tmp.toPath(), want);
            try {
                Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return null;
        } catch (IOException e) {
            return e.getMessage() == null ? e.toString() : e.getMessage();
        }
    }

    /** Retries every failed copy of a day. Returns how many are still pending. */
    public int retryCopies(Day day) {
        if (copyDir == null || day == null) return pendingCopies(day);
        for (ExportRecord x : day.exports()) {
            if (!x.copyPending()) continue;
            File f = new File(exportDir, x.fileName);
            if (!f.exists()) continue;   // the archive lost it: Rigenera first
            copyOne(day, x.number, f);
        }
        return pendingCopies(day);
    }

    /**
     * Copies the exports of a day that never had a copy attempt: one recorded
     * at the start after a power cut, or one whose event was written just
     * before the PC went off and the copy never ran. Without this, a file the
     * copy folder never saw would not even show as pending, and AutoFillSuite
     * would stop at the next file of the day (out of order). Only for the open
     * day: an old export made with no copy folder configured stays as it was.
     */
    public int copyNeverTried(Day day) {
        if (copyDir == null || day == null || day.isCorrupted()) return 0;
        int copied = 0;
        for (ExportRecord x : day.exports()) {
            if (!x.copyOutcome().isEmpty()) continue;
            File f = new File(exportDir, x.fileName);
            if (!f.exists()) continue;
            copyOne(day, x.number, f);
            copied++;
        }
        return copied;
    }

    public static int pendingCopies(Day day) {
        int n = 0;
        if (day == null) return 0;
        for (ExportRecord x : day.exports()) if (x.copyPending()) n++;
        return n;
    }

    // ── regeneration ─────────────────────────────────────────────────────

    /** Outcome of a regeneration. */
    public static final class Regenerated {
        public final boolean identical;
        public final String message;
        public final File file;
        public final String sha256;

        Regenerated(boolean identical, String message, File file, String sha256) {
            this.identical = identical;
            this.message = message;
            this.file = file;
            this.sha256 = sha256;
        }
    }

    /**
     * Rewrites export {@code number} of a day from its journal alone: the
     * journal is replayed up to (not including) that ESPORTA event, the same
     * lines are built and written into {@code dir}. The fingerprint must equal
     * the one the event recorded — otherwise the journal does not match what
     * went out, and that is a serious error.
     */
    public static Regenerated regenerate(File journal, int number, File dir) {
        List<Event> events;
        try {
            events = Journal.read(journal).events;
        } catch (IOException e) {
            return new Regenerated(false, "giornale illeggibile: " + e.getMessage(), null, null);
        }
        int at = -1;
        for (int i = 0; i < events.size(); i++) {
            Event e = events.get(i);
            if (e.type == Event.Type.ESPORTA && String.valueOf(number).equals(e.field(0))) at = i;
        }
        if (at < 0) return new Regenerated(false, "export " + number + " non presente nel giornale", null, null);
        Event x = events.get(at);
        Day before = Day.replayOnly(events.subList(0, at));
        boolean reexport = "1".equals(x.field(4));
        List<Integer> ns = new ArrayList<>();
        if (!x.field(5).isEmpty()) for (String s : x.field(5).split(",")) ns.add(Integer.parseInt(s.trim()));
        CsvWriter.Output out = CsvWriter.write(
            CsvWriter.header(before.station(), before.date(), number, x.field(1), reexport),
            before.linesFor(ns, reexport));
        if (!out.sha256.equals(x.field(2))) {
            return new Regenerated(false, "il giornale non corrisponde a quello che è uscito: impronta "
                                   + out.sha256.substring(0, 8) + "... invece di "
                                   + shortSha(x.field(2)), null, out.sha256);
        }
        File f = new File(dir, x.field(3));
        try {
            Files.createDirectories(dir.toPath());
            if (f.exists()) {
                if (Arrays.equals(out.bytes, Files.readAllBytes(f.toPath()))) {
                    return new Regenerated(true, "già presente e identico", f, out.sha256);
                }
                return new Regenerated(false, "esiste già un " + f.getName() + " diverso in quella cartella",
                                       null, out.sha256);
            }
            writeAtomic(f, out.bytes);
        } catch (IOException e) {
            return new Regenerated(false, "scrittura non riuscita: " + e.getMessage(), null, out.sha256);
        }
        return new Regenerated(true, f.getName() + "  -  impronta " + out.sha256.substring(0, 8)
                               + "... coincide con quella del giornale", f, out.sha256);
    }

    static String shortSha(String s) {
        return s == null || s.length() < 8 ? String.valueOf(s) : s.substring(0, 8) + "...";
    }

    // ── the gap between rename and event ─────────────────────────────────

    private static final Pattern NAME = Pattern.compile("registro_(.+)_(\\d{4}-\\d{2}-\\d{2})_(\\d+)\\.csv");

    /**
     * After a crash: files of this day in the archive with a number beyond the
     * last recorded export, valid, whose rows are exactly the rows still
     * pending (or, for a re-export, the live rows) — the PC went off between
     * the rename and the event. The missing event is recorded. Returns the
     * names recovered. Also removes leftover .tmp files of this day.
     */
    public List<String> recoverMissing(Day day) {
        List<String> found = new ArrayList<>();
        if (day == null || day.isCorrupted()) return found;
        File[] files = exportDir.listFiles();
        if (files == null) return found;
        List<File> candidates = new ArrayList<>();
        for (File f : files) {
            if (f.getName().endsWith(".csv.tmp") && f.getName().contains("_" + day.date() + "_")
                && f.getName().startsWith("registro_" + day.station() + "_")) {
                if (!f.delete()) f.deleteOnExit();   // half-written, never renamed: not an export
                continue;
            }
            Matcher m = NAME.matcher(f.getName());
            if (!m.matches() || !m.group(1).equals(day.station()) || !m.group(2).equals(day.date())) continue;
            if (Integer.parseInt(m.group(3)) > day.lastExportNumber()) candidates.add(f);
        }
        candidates.sort((a, b) -> a.getName().compareTo(b.getName()));
        for (File f : candidates) {
            CsvReader.Result r;
            try {
                r = CsvReader.read(Files.readAllBytes(f.toPath()));
            } catch (IOException e) {
                continue;
            }
            if (!r.ok() || r.exportNumber() != day.nextExportNumber()) continue;
            List<Integer> ns = r.reexport() ? day.liveRows() : day.pendingExport();
            if (!sameRows(r.rows, day.linesFor(ns, r.reexport()))) continue;
            Day.Result rec = day.recordExport(r.exportNumber(), r.created(), r.computedSha, f.getName(),
                                              r.reexport(), ns);
            if (rec.ok()) {
                found.add(f.getName());
                copyOne(day, r.exportNumber(), f);
            }
        }
        return found;
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /** tmp + force + atomic rename, the SettingsManager way. */
    static void writeAtomic(File target, byte[] bytes) throws IOException {
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (FileChannel ch = FileChannel.open(tmp.toPath(), StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer buf = ByteBuffer.wrap(bytes);
            while (buf.hasRemaining()) ch.write(buf);
            ch.force(true);
        }
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            if (target.exists()) {
                if (!tmp.delete()) tmp.deleteOnExit();
                throw new IOException("esiste già " + target.getName());
            }
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String now() {
        return LocalDateTime.now(clock).format(CREATED);
    }

    private static String preview(String text) {
        String[] lines = text.split("\r\n");
        if (lines.length <= 8) return String.join("\n", lines);
        List<String> out = new ArrayList<>(Arrays.asList(lines).subList(0, 4));
        out.add("...");
        out.addAll(Arrays.asList(lines).subList(lines.length - 3, lines.length));
        return String.join("\n", out);
    }
}
