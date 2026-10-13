package app.core;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The folder of day journals: {@code <data>/giornate/<yyyy-MM-dd>[_k].giornale}.
 *
 * A day is named after the date it was opened and stays open until someone
 * closes it, past midnight included (shifts). Closing and reopening on the same
 * date gives {@code _2}, {@code _3}... and the new day continues the row and
 * export numbering of the earlier ones, so (station, date, export) stays unique
 * for AutoFillSuite and a row number never comes back as a different row.
 */
public final class Days {

    public static final String EXT = ".giornale";
    private static final Pattern NAME =
        Pattern.compile("(\\d{4}-\\d{2}-\\d{2})(?:_(\\d+))?\\.giornale");

    private final File dir;
    private final Clock clock;

    public Days(File dataDir, Clock clock) {
        this.dir = new File(dataDir, "giornate");
        this.clock = clock;
    }

    public File dir() {
        return dir;
    }

    /** One journal file on disk. */
    public static final class Info implements Comparable<Info> {
        public final File file;
        public final String date;
        public final int k;
        public final boolean closed;

        Info(File file, String date, int k, boolean closed) {
            this.file = file;
            this.date = date;
            this.k = k;
            this.closed = closed;
        }

        @Override public int compareTo(Info o) {
            int c = date.compareTo(o.date);
            return c != 0 ? c : Integer.compare(k, o.k);
        }
    }

    /** Every day journal, oldest first. */
    public List<Info> list() {
        List<Info> out = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files == null) return out;
        for (File f : files) {
            Matcher m = NAME.matcher(f.getName());
            if (!f.isFile() || !m.matches()) continue;
            int k = m.group(2) == null ? 1 : Integer.parseInt(m.group(2));
            out.add(new Info(f, m.group(1), k, endsClosed(f)));
        }
        Collections.sort(out);
        return out;
    }

    /** The open day (no CHIUDI), newest if several; null when none. */
    public Info openDay() {
        List<Info> all = list();
        for (int i = all.size() - 1; i >= 0; i--) {
            if (!all.get(i).closed) return all.get(i);
        }
        return null;
    }

    /** Open days from a date before today: work not closed (and maybe not exported). */
    public List<Info> staleOpenDays() {
        String today = LocalDate.now(clock).toString();
        List<Info> out = new ArrayList<>();
        for (Info i : list()) {
            if (!i.closed && i.date.compareTo(today) < 0) out.add(i);
        }
        return out;
    }

    /** Opens the current open day, or null when there is none. */
    public Day openCurrent(String station) throws IOException {
        Info i = openDay();
        if (i == null) return null;
        return Day.open(i.file, init(station, i.date, i.k), clock);
    }

    /** Starts a new day dated today (suffix _k when today already has one). */
    public Day createNew(String station) throws IOException {
        String today = LocalDate.now(clock).toString();
        int k = 1;
        for (Info i : list()) if (i.date.equals(today)) k = Math.max(k, i.k + 1);
        File f = new File(dir, today + (k == 1 ? "" : "_" + k) + EXT);
        if (f.exists()) throw new IOException("giornata già presente: " + f.getName());
        return Day.open(f, init(station, today, k), clock);
    }

    /** Numbering continues from the earlier days of the same date. */
    private Day.Init init(String station, String date, int k) throws IOException {
        int nBase = 0, exportBase = 0;
        for (Info i : list()) {
            if (!i.date.equals(date) || i.k >= k) continue;
            Day prev = Day.replayOnly(Journal.read(i.file).events);
            nBase = Math.max(nBase, prev.maxN());
            exportBase = Math.max(exportBase, prev.lastExportNumber());
        }
        return new Day.Init(station, date, nBase, exportBase);
    }

    /** Reads only the end of the file: is the last whole event a CHIUDI? */
    static boolean endsClosed(File f) {
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            long len = raf.length();
            int n = (int) Math.min(len, 4096);
            byte[] buf = new byte[n];
            raf.seek(len - n);
            raf.readFully(buf);
            String tail = new String(buf, StandardCharsets.UTF_8);
            int end = tail.lastIndexOf('\n');
            if (end < 0) return false;
            int start = tail.lastIndexOf('\n', end - 1) + 1;
            Event e = Journal.decode(tail.substring(start, end));
            return e != null && e.type == Event.Type.CHIUDI;
        } catch (IOException e) {
            return false;
        }
    }
}
