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
    private String diskError = "";
    private long lastSavedMillis;
    private final List<Runnable> listeners = new ArrayList<>();

    public Ledger(Settings cfg, Clock clock) {
        this.cfg = cfg;
        this.clock = clock;
        this.days = new Days(cfg.dataDir(), clock);
    }

    /** Opens the open day, if any. A failure leaves no day and says why. */
    public String open() {
        try {
            day = days.openCurrent(station());
            if (day != null && day.file() != null) lastSavedMillis = day.file().lastModified();
            return null;
        } catch (IOException e) {
            diskOk = false;
            diskError = e.getMessage();
            return e.getMessage();
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
