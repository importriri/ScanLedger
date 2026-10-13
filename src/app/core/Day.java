package app.core;

import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * The model of one working day, rebuilt from its journal.
 *
 * Every command validates, writes its event through the journal (forced to
 * disk) and only then changes the rows. A failed write means the action did
 * not happen. Replaying the same events always rebuilds the same model: the
 * row numbers travel inside the events and are never recomputed.
 *
 * The edit rules follow the portal ("the last registration wins"): once a row
 * went out in an export its label is on the portal and cannot change here, but
 * its lot can — the correction goes out in the next export.
 */
public final class Day {

    public static final String ON_PORTAL =
        "è già sul portale: per toglierla serve Delete item sul portale, "
        + "poi spara quella giusta come nuova";

    /** Outcome of a command. */
    public static final class Result {
        public enum Kind { OK, UNCHANGED, DUPLICATE, REFUSED, IO_ERROR, READ_ONLY }

        public final Kind kind;
        public final String message;
        /** for DUPLICATE: the n of the row that already holds the label */
        public final int existingN;
        /** for a refused range: every label already present */
        public final List<String> duplicates;
        public final Event event;

        private Result(Kind kind, String message, int existingN,
                       List<String> duplicates, Event event) {
            this.kind = kind;
            this.message = message;
            this.existingN = existingN;
            this.duplicates = duplicates == null
                ? Collections.<String>emptyList() : Collections.unmodifiableList(duplicates);
            this.event = event;
        }

        static Result ok(Event e)                { return new Result(Kind.OK, "", 0, null, e); }
        public static Result unchanged(String why) { return new Result(Kind.UNCHANGED, why, 0, null, null); }
        public static Result refused(String why) { return new Result(Kind.REFUSED, why, 0, null, null); }
        public static Result io(IOException e)   {
            return new Result(Kind.IO_ERROR, "Il disco non scrive: " + e.getMessage(), 0, null, null);
        }
        static Result readOnly() {
            return new Result(Kind.READ_ONLY, "La giornata è in sola lettura", 0, null, null);
        }
        static Result duplicate(String label, int n) {
            return new Result(Kind.DUPLICATE, "Già registrata oggi: riga " + n, n,
                              Collections.singletonList(label), null);
        }
        static Result duplicates(List<String> labels) {
            return new Result(Kind.DUPLICATE, "Già registrate oggi: " + labels.size(), 0, labels, null);
        }

        public boolean ok() {
            return kind == Kind.OK;
        }
    }

    private final File file;
    private final Journal journal;          // null when read only or closed
    private final Journal.Replay replay;    // what the startup read found, null for in-memory

    private String station = "";
    private String date = "";
    private int nBase;
    private int exportBase;

    private final Map<Integer, Row> rows = new LinkedHashMap<>();
    /** additions in order: {n0, count}; the last one is what "undo" removes */
    private final List<int[]> additions = new ArrayList<>();
    private final List<ExportRecord> exports = new ArrayList<>();
    private int maxN;
    private int eventCount;
    private boolean closed;
    private final boolean corrupted;
    private final List<Consumer<Event>> listeners = new ArrayList<>();

    private Day(File file, Journal journal, Journal.Replay replay, boolean corrupted) {
        this.file = file;
        this.journal = journal;
        this.replay = replay;
        this.corrupted = corrupted;
    }

    // ── opening ──────────────────────────────────────────────────────────

    /**
     * Opens a day journal, repairing a torn last line. A journal broken in the
     * middle opens read only (the original is copied next to it). A journal
     * with no APRI yet gets one, from {@code init}.
     */
    public static Day open(File file, Init init, Clock clock) throws IOException {
        Journal.Replay r = Journal.recover(file, clock);
        if (r.corrupted) {
            Day d = new Day(file, null, r, true);
            for (Event e : r.events) d.apply(e);
            return d;
        }
        boolean closedFile = false;
        for (Event e : r.events) if (e.type == Event.Type.CHIUDI) closedFile = true;
        Journal j = closedFile ? null : Journal.openForAppend(file, r, clock);
        Day d = new Day(file, j, r, false);
        for (Event e : r.events) d.apply(e);
        if (r.events.isEmpty() && j != null) {
            Event e = j.append(Event.Type.APRI, Journal.fields(
                init.station, init.date, String.valueOf(init.nBase), String.valueOf(init.exportBase)));
            d.apply(e);
        }
        return d;
    }

    /** A model rebuilt from events only, with no file behind it (regeneration, tests). */
    public static Day replayOnly(List<Event> events) {
        Day d = new Day(null, null, null, false);
        for (Event e : events) d.apply(e);
        return d;
    }

    /** What a new day starts from. */
    public static final class Init {
        public final String station;
        public final String date;
        public final int nBase;
        public final int exportBase;

        public Init(String station, String date, int nBase, int exportBase) {
            this.station = station;
            this.date = date;
            this.nBase = nBase;
            this.exportBase = exportBase;
        }
    }

    // ── reading ──────────────────────────────────────────────────────────

    public File file()             { return file; }
    public String station()        { return station; }
    public String date()           { return date; }
    public boolean isClosed()      { return closed; }
    public boolean isCorrupted()   { return corrupted; }
    public boolean isReadOnly()    { return corrupted || closed || (journal == null && file != null); }
    public int eventCount()        { return eventCount; }
    public Journal journal()       { return journal; }
    public Journal.Replay replay() { return replay; }

    /** All rows, in n order. */
    public List<Row> rows() {
        return Collections.unmodifiableList(new ArrayList<>(rows.values()));
    }

    public Row row(int n) {
        return rows.get(n);
    }

    public int rowCount() {
        return rows.size();
    }

    /** Rows not deleted: the pairs of the day. */
    public int liveCount() {
        int c = 0;
        for (Row r : rows.values()) if (!r.deleted) c++;
        return c;
    }

    /** n of the most recently added row still alive, or 0. */
    public int lastAddedN() {
        for (int i = additions.size() - 1; i >= 0; i--) {
            int[] a = additions.get(i);
            for (int n = a[0] + a[1] - 1; n >= a[0]; n--) {
                Row r = rows.get(n);
                if (r != null && !r.deleted) return n;
            }
            return 0;
        }
        return 0;
    }

    /** The last addition as {n0, count} (a pair or a whole range), or null. */
    public int[] lastAddition() {
        if (additions.isEmpty()) return null;
        int[] a = additions.get(additions.size() - 1);
        return new int[] { a[0], a[1] };
    }

    /** Rows in the range that starts at n0 (1 for a scanned pair). */
    public int additionSize(int n0) {
        for (int i = additions.size() - 1; i >= 0; i--) {
            if (additions.get(i)[0] == n0) return additions.get(i)[1];
        }
        return 1;
    }

    /** The live row holding this label, or null. */
    public Row findLive(String label) {
        for (Row r : rows.values()) {
            if (!r.deleted && r.label.equals(label)) return r;
        }
        return null;
    }

    /** n of the rows the next export will carry: new rows and pending corrections. */
    public List<Integer> pendingExport() {
        List<Integer> out = new ArrayList<>();
        for (Row r : rows.values()) {
            Row.State s = r.state();
            if (s == Row.State.LIVE || s == Row.State.CORRECTED) out.add(r.n);
        }
        return out;
    }

    public int pendingNew() {
        int c = 0;
        for (Row r : rows.values()) if (r.state() == Row.State.LIVE) c++;
        return c;
    }

    public int pendingCorrections() {
        int c = 0;
        for (Row r : rows.values()) if (r.state() == Row.State.CORRECTED) c++;
        return c;
    }

    public List<ExportRecord> exports() {
        return Collections.unmodifiableList(exports);
    }

    public ExportRecord export(int number) {
        for (ExportRecord x : exports) if (x.number == number) return x;
        return null;
    }

    /** The highest export number this day has used (or inherited), 0 if none. */
    public int lastExportNumber() {
        int max = exportBase;
        for (ExportRecord x : exports) max = Math.max(max, x.number);
        return max;
    }

    public int nextExportNumber() {
        return lastExportNumber() + 1;
    }

    /** The highest row number used (or inherited). */
    public int maxN() {
        return Math.max(maxN, nBase);
    }

    public void addListener(Consumer<Event> l) {
        listeners.add(l);
    }

    // ── commands ─────────────────────────────────────────────────────────

    /** One scanned pair. */
    public Result add(String label, String lot) {
        Result pre = writable();
        if (pre != null) return pre;
        label = clean(label);
        lot = clean(lot);
        String bad = badPair(label, lot);
        if (bad != null) return Result.refused(bad);
        Row dup = findLive(label);
        if (dup != null) return Result.duplicate(label, dup.n);
        int n = maxN() + 1;
        return write(Event.Type.AGGIUNGI,
                     String.valueOf(n), label, lot, Row.Mode.SCAN.name());
    }

    /** A range: N rows with one lot, in ONE event — all or none. */
    public Result addRange(List<String> labels, String lot) {
        Result pre = writable();
        if (pre != null) return pre;
        lot = clean(lot);
        if (labels == null || labels.isEmpty()) return Result.refused("Intervallo vuoto");
        List<String> clean = new ArrayList<>();
        for (String l : labels) {
            String c = clean(l);
            String bad = badPair(c, lot);
            if (bad != null) return Result.refused(bad);
            if (c.indexOf('|') >= 0) return Result.refused("Etichetta con '|' non ammessa in un intervallo");
            clean.add(c);
        }
        List<String> dups = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<String> live = new HashSet<>();
        for (Row r : rows.values()) if (!r.deleted) live.add(r.label);
        for (String c : clean) {
            if (live.contains(c) || !seen.add(c)) dups.add(c);
        }
        if (!dups.isEmpty()) return Result.duplicates(dups);
        int n0 = maxN() + 1;
        return write(Event.Type.INTERVALLO, String.valueOf(n0), lot, String.join("|", clean));
    }

    /**
     * Edits a row. Never exported: label and lot may change (no duplicates).
     * Exported: only the lot — the label is on the portal already.
     */
    public Result modify(int n, String label, String lot, String reason) {
        Result pre = writable();
        if (pre != null) return pre;
        Row r = rows.get(n);
        if (r == null) return Result.refused("Riga " + n + " inesistente");
        if (r.deleted) return Result.refused("Riga eliminata: ripristinala prima di modificarla");
        label = clean(label);
        lot = clean(lot);
        String bad = badPair(label, lot);
        if (bad != null) return Result.refused(bad);
        if (r.everExported() && !label.equals(r.label)) {
            return Result.refused("Etichetta non modificabile: " + ON_PORTAL);
        }
        if (label.equals(r.label) && lot.equals(r.lot)) return Result.unchanged("Nessuna modifica");
        if (!label.equals(r.label)) {
            Row dup = findLive(label);
            if (dup != null && dup.n != n) return Result.duplicate(label, dup.n);
        }
        return write(Event.Type.MODIFICA, String.valueOf(n), label, lot,
                     reason == null ? "" : clean(reason));
    }

    public Result delete(int n) {
        Result pre = writable();
        if (pre != null) return pre;
        Row r = rows.get(n);
        if (r == null) return Result.refused("Riga " + n + " inesistente");
        if (r.everExported()) return Result.refused("Non si può eliminare: " + ON_PORTAL);
        if (r.deleted) return Result.unchanged("Riga già eliminata");
        return write(Event.Type.ELIMINA, String.valueOf(n));
    }

    public Result restore(int n) {
        Result pre = writable();
        if (pre != null) return pre;
        Row r = rows.get(n);
        if (r == null) return Result.refused("Riga " + n + " inesistente");
        if (!r.deleted) return Result.unchanged("La riga non è eliminata");
        Row dup = findLive(r.label);
        if (dup != null) return Result.duplicate(r.label, dup.n);
        return write(Event.Type.RIPRISTINA, String.valueOf(n));
    }

    /** "Annulla ultima": removes the last addition (a pair or a whole range) if not exported. */
    public Result undoLast() {
        Result pre = writable();
        if (pre != null) return pre;
        if (additions.isEmpty()) return Result.unchanged("Niente da annullare");
        int[] a = additions.get(additions.size() - 1);
        boolean anyLive = false;
        for (int n = a[0]; n < a[0] + a[1]; n++) {
            Row r = rows.get(n);
            if (r == null) continue;
            if (r.everExported()) return Result.refused("Non si può annullare: " + ON_PORTAL);
            if (!r.deleted) anyLive = true;
        }
        if (!anyLive) return Result.unchanged("Niente da annullare");
        if (a[1] == 1 && rows.get(a[0]).mode == Row.Mode.SCAN) {
            return write(Event.Type.ELIMINA, String.valueOf(a[0]));
        }
        return write(Event.Type.ELIMINA_INTERVALLO, String.valueOf(a[0]), String.valueOf(a[1]));
    }

    /** The export file is on disk and verified: record it. */
    public Result recordExport(int number, String created, String sha256, String fileName,
                               boolean reexport, List<Integer> ns) {
        Result pre = writable();
        if (pre != null) return pre;
        StringBuilder list = new StringBuilder();
        for (Integer n : ns) {
            if (!rows.containsKey(n)) return Result.refused("Riga " + n + " inesistente");
            if (list.length() > 0) list.append(',');
            list.append(n);
        }
        return write(Event.Type.ESPORTA, String.valueOf(number), created, sha256, fileName,
                     reexport ? "1" : "0", list.toString());
    }

    public Result recordCopy(int number, String folder, boolean ok, String reason) {
        Result pre = writable();
        if (pre != null) return pre;
        return write(Event.Type.COPIA, String.valueOf(number), folder,
                     ok ? "OK" : "FALLITA", reason == null ? "" : clean(reason));
    }

    /** Archives the day. Only with nothing left to export. */
    public Result close() {
        Result pre = writable();
        if (pre != null) return pre;
        int nw = pendingNew(), corr = pendingCorrections();
        if (nw > 0 || corr > 0) {
            return Result.refused("Da esportare prima di chiudere: " + nw + " righe nuove, "
                                  + corr + " correzioni");
        }
        return write(Event.Type.CHIUDI);
    }

    // ── internals ────────────────────────────────────────────────────────

    private Result writable() {
        if (corrupted || closed || journal == null) return Result.readOnly();
        return null;
    }

    private Result write(Event.Type type, String... fields) {
        Event e;
        try {
            e = journal.append(type, Journal.fields(fields));
        } catch (IOException ex) {
            return Result.io(ex);
        }
        apply(e);
        return Result.ok(e);
    }

    static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    /** null when the pair can be recorded; else why not. */
    static String badPair(String label, String lot) {
        if (label.isEmpty()) return "Etichetta vuota";
        if (lot.isEmpty()) return "Lotto vuoto";
        if (hasControlChars(label) || hasControlChars(lot)) return "TAB o a capo dentro un codice";
        return null;
    }

    /** TAB, CR or LF: refused at the scan, so they never reach a file. */
    public static boolean hasControlChars(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\t' || c == '\r' || c == '\n') return true;
        }
        return false;
    }

    /** Applies an event already on disk. Tolerant: a nonsensical event changes nothing. */
    private void apply(Event e) {
        eventCount++;
        try {
            switch (e.type) {
                case APRI:
                    station = e.field(0);
                    date = e.field(1);
                    nBase = parseInt(e.field(2), 0);
                    exportBase = parseInt(e.field(3), 0);
                    break;
                case AGGIUNGI: {
                    int n = Integer.parseInt(e.field(0));
                    if (rows.containsKey(n)) break;
                    Row.Mode mode = "INTERVALLO".equals(e.field(3)) ? Row.Mode.INTERVALLO : Row.Mode.SCAN;
                    rows.put(n, new Row(n, e.field(1), e.field(2), mode, e.tsSeconds(), 0));
                    maxN = Math.max(maxN, n);
                    additions.add(new int[] { n, 1 });
                    break;
                }
                case INTERVALLO: {
                    int n0 = Integer.parseInt(e.field(0));
                    String[] labels = e.field(2).split("\\|", -1);
                    for (int i = 0; i < labels.length; i++) {
                        int n = n0 + i;
                        if (rows.containsKey(n)) continue;
                        rows.put(n, new Row(n, labels[i], e.field(1), Row.Mode.INTERVALLO,
                                            e.tsSeconds(), n0));
                        maxN = Math.max(maxN, n);
                    }
                    additions.add(new int[] { n0, labels.length });
                    break;
                }
                case MODIFICA: {
                    Row r = rows.get(Integer.parseInt(e.field(0)));
                    if (r == null) break;
                    r.label = e.field(1);
                    r.lot = e.field(2);
                    r.lastReason = e.field(3);
                    r.modified = true;
                    if (r.exportedLot != null) {
                        r.correctedAt = r.lot.equals(r.exportedLot) ? null : e.tsSeconds();
                    }
                    break;
                }
                case ELIMINA: {
                    Row r = rows.get(Integer.parseInt(e.field(0)));
                    if (r != null) r.deleted = true;
                    break;
                }
                case RIPRISTINA: {
                    Row r = rows.get(Integer.parseInt(e.field(0)));
                    if (r != null) r.deleted = false;
                    break;
                }
                case ELIMINA_INTERVALLO: {
                    int n0 = Integer.parseInt(e.field(0));
                    int q = Integer.parseInt(e.field(1));
                    for (int n = n0; n < n0 + q; n++) {
                        Row r = rows.get(n);
                        if (r != null) r.deleted = true;
                    }
                    break;
                }
                case ESPORTA:
                    applyExport(e);
                    break;
                case COPIA: {
                    ExportRecord x = export(Integer.parseInt(e.field(0)));
                    if (x != null) {
                        x.copyFolder = e.field(1);
                        x.copyOutcome = e.field(2);
                        x.copyReason = e.field(3);
                    }
                    break;
                }
                case CHIUDI:
                    closed = true;
                    break;
                default:
                    break;
            }
        } catch (RuntimeException ignored) {
            // a malformed event (wrong field shape) changes nothing
        }
        for (Consumer<Event> l : listeners) l.accept(e);
    }

    private void applyExport(Event e) {
        int number = Integer.parseInt(e.field(0));
        boolean reexport = "1".equals(e.field(4));
        Set<Integer> ns = new TreeSet<>();
        String list = e.field(5);
        if (!list.isEmpty()) {
            for (String s : list.split(",")) ns.add(Integer.parseInt(s.trim()));
        }
        List<ExportRecord.Line> lines = new ArrayList<>();
        for (Integer n : ns) {
            Row r = rows.get(n);
            if (r == null) continue;
            boolean correction = !reexport && r.everExported();
            String ora = correction && r.correctedAt != null ? r.correctedAt : r.created;
            lines.add(new ExportRecord.Line(r.n, r.label, r.lot, ora, r.mode,
                correction ? "CORREZIONE" : "NUOVA", r.modified,
                correction ? r.exportedLot : ""));
        }
        exports.add(new ExportRecord(number, e.field(1), e.field(2), e.field(3),
                                     reexport, e.seq, e.ts, lines));
        if (reexport) return;   // a re-export changes no state
        for (Integer n : ns) {
            Row r = rows.get(n);
            if (r == null) continue;
            r.exportedLot = r.lot;
            r.lastExport = number;
            r.correctedAt = null;
        }
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return def; }
    }

    /** For tests and the regeneration: a deep copy of the rows. */
    List<Row> snapshotRows() {
        List<Row> out = new ArrayList<>();
        for (Row r : rows.values()) out.add(r.copy());
        return out;
    }
}
