package app.core;

import app.config.Settings;

import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * The station's book: the current day, created on the first scan after a
 * close, and the disk state the screen shows. Every command goes through here
 * so the screen hears about every change and about every failed write.
 */
public final class Ledger {

    private final Settings cfg;
    private final Clock clock;
    private final Days days;
    private Day day;
    private boolean diskOk = true;
    /** the open day exists and could not be read: retried by {@link #probe} */
    private boolean openFailed;
    private String diskError = "";
    private long lastSavedMillis;
    private final List<Runnable> listeners = new ArrayList<>();

    public Ledger(Settings cfg, Clock clock) {
        this.cfg = cfg;
        this.clock = clock;
        this.days = new Days(cfg.dataDir(), clock);
    }

    /** What the start found, for the banners. */
    private final List<String> recovered = new ArrayList<>();

    /**
     * Opens the open day, if any; records an export the journal missed (the
     * PC went off between the rename and the event); retries failed copies of
     * every day. A failure leaves no day and says why.
     */
    public String open() {
        try {
            day = days.openCurrent(station());
            if (day != null && day.file() != null) lastSavedMillis = day.file().lastModified();
            openFailed = false;
        } catch (IOException e) {
            // the open day is there and cannot be read (an antivirus, a backup
            // holding it): wait for it. Starting another day beside it would
            // leave its rows open, unexported, and on no screen.
            openFailed = true;
            diskOk = false;
            diskError = "giornata aperta illeggibile: " + e.getMessage();
            return diskError;
        }
        if (day != null) {
            recovered.addAll(exporter().recoverMissing(day));
            exporter().copyNeverTried(day);
        }
        retryAllCopies();
        return null;
    }

    /** Export files recorded at the start because the journal had missed them. */
    public List<String> recovered() {
        return recovered;
    }

    /** A fresh exporter: the folders may have changed in the settings. */
    public Exporter exporter() {
        return new Exporter(cfg.exportDir(), cfg.copyDir(), clock);
    }

    /** Incremental export (or the whole day again), then everyone hears about it. */
    public Exporter.Result export(boolean reexport) {
        Exporter x = exporter();
        Exporter.Result r = reexport ? x.reexport(day) : x.export(day);
        Journal j = day == null ? null : day.journal();
        if (j != null && j.isBroken()) {
            // the event did not reach the disk: the pill turns red, the probe takes over
            diskOk = false;
            diskError = r.message;
        }
        retryAllCopies();
        fire();
        return r;
    }

    /** Closes the day. On success there is no current day until the next scan. */
    public Day.Result closeDay() {
        if (day == null) return Day.Result.refused("Nessuna giornata aperta");
        Day.Result r = day.close();
        if (r.ok()) {
            if (day.journal() != null) day.journal().close();
            day = null;
        }
        return after(r);
    }

    /** A pending copy, for the banner and the history. */
    public static final class PendingCopy {
        public final String date;
        public final int number;
        public final String reason;

        PendingCopy(String date, int number, String reason) {
            this.date = date;
            this.number = number;
            this.reason = reason;
        }
    }

    private final List<PendingCopy> pendingCopies = new ArrayList<>();

    public List<PendingCopy> pendingCopies() {
        return pendingCopies;
    }

    /** Retries every failed copy, of every day; closed days included. */
    public int retryAllCopies() {
        pendingCopies.clear();
        Exporter x = exporter();
        for (Days.Info i : days.list()) {
            boolean current = day != null && day.file() != null
                && day.file().getAbsoluteFile().equals(i.file.getAbsoluteFile());
            Day d = current ? day : null;
            try {
                if (d == null) {
                    Journal.Replay rr = Journal.read(i.file);
                    if (rr.corrupted) continue;   // never "repair" an old day just to copy
                    Day peek = Day.replayOnly(rr.events);
                    if (Exporter.pendingCopies(peek) == 0) continue;
                    if (x.copyDir() == null) { collect(peek); continue; }
                    d = Day.open(i.file, new Day.Init(station(), i.date, 0, 0), clock);
                }
                x.retryCopies(d);
                collect(d);
            } catch (IOException e) {
                // an unreadable old day is not a reason to stop the station
            } finally {
                if (d != null && d != day && d.journal() != null) d.journal().close();
            }
        }
        return pendingCopies.size();
    }

    private void collect(Day d) {
        for (ExportRecord r : d.exports()) {
            if (r.copyPending()) pendingCopies.add(new PendingCopy(d.date(), r.number, r.copyReason()));
        }
    }

    public Days days()              { return days; }
    public Day day()                { return day; }
    public Clock clock()            { return clock; }
    public boolean diskOk()         { return diskOk; }
    public String diskError()       { return diskError; }
    public long lastSavedMillis()   { return lastSavedMillis; }
    public File dataDir()           { return cfg.dataDir(); }

    public String station() {
        return cfg.get(Settings.STATION, "").trim();
    }

    public void addListener(Runnable r) {
        listeners.add(r);
    }

    /** The screen may scan: a station name, a writable disk, a writable day. */
    public boolean canScan() {
        return diskOk && StationRule.valid(station()) && (day == null || !day.isCorrupted());
    }

    // ── commands ─────────────────────────────────────────────────────────

    public Day.Result add(String label, String lot) {
        Day.Result pre = ensureDay();
        if (pre != null) return pre;
        return after(day.add(label, lot));
    }

    public Day.Result addRange(List<String> labels, String lot) {
        Day.Result pre = ensureDay();
        if (pre != null) return pre;
        return after(day.addRange(labels, lot));
    }

    public Day.Result modify(int n, String label, String lot, String reason) {
        if (day == null) return Day.Result.refused("Nessuna giornata aperta");
        return after(day.modify(n, label, lot, reason));
    }

    public Day.Result delete(int n) {
        if (day == null) return Day.Result.refused("Nessuna giornata aperta");
        return after(day.delete(n));
    }

    public Day.Result restore(int n) {
        if (day == null) return Day.Result.refused("Nessuna giornata aperta");
        return after(day.restore(n));
    }

    public Day.Result undoLast() {
        if (day == null) return Day.Result.unchanged("Niente da annullare");
        return after(day.undoLast());
    }

    /** After a failed write: tries again. true when writing may resume. */
    public boolean probe() {
        if (diskOk) return true;
        boolean ok;
        if (openFailed) {
            ok = open() == null;
            if (ok) {
                diskOk = true;
                diskError = "";
                fire();
            }
            return ok;
        }
        if (day != null && day.journal() != null) {
            ok = day.journal().probe();
        } else {
            ok = probeFolder();
        }
        if (ok) {
            diskOk = true;
            diskError = "";
            lastSavedMillis = System.currentTimeMillis();
            fire();
        }
        return ok;
    }

    /** Re-reads the folder after the day was closed or changed outside. */
    public void fireChanged() {
        fire();
    }

    /** Swaps in a day opened elsewhere (closing, reopening). */
    public void setDay(Day d) {
        if (day != null && day != d && day.journal() != null) day.journal().close();
        day = d;
        fire();
    }

    private boolean probeFolder() {
        File dir = days.dir();
        try {
            java.nio.file.Files.createDirectories(dir.toPath());
            File t = File.createTempFile("probe", ".tmp", dir);
            if (!t.delete()) t.deleteOnExit();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private Day.Result ensureDay() {
        if (!diskOk) return Day.Result.io(new IOException(diskError));
        if (!StationRule.valid(station())) return Day.Result.refused("Manca il nome della postazione");
        if (day != null && !day.isClosed()) return null;
        try {
            Day d = days.createNew(station());
            if (day != null && day.journal() != null) day.journal().close();
            day = d;
            return null;
        } catch (IOException e) {
            return after(Day.Result.io(e));
        }
    }

    private Day.Result after(Day.Result r) {
        if (r.kind == Day.Result.Kind.IO_ERROR) {
            diskOk = false;
            diskError = r.message;
        } else if (r.ok()) {
            Journal j = day == null ? null : day.journal();
            lastSavedMillis = j != null && j.lastForceMillis() > 0
                ? j.lastForceMillis() : System.currentTimeMillis();
        }
        if (r.ok() || r.kind == Day.Result.Kind.IO_ERROR) fire();
        return r;
    }

    private void fire() {
        for (Runnable r : new ArrayList<>(listeners)) r.run();
    }
}
