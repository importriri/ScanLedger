package app.core;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

/**
 * The write-ahead journal of one day: one event per line, appended and forced
 * to the disk BEFORE the model changes. The model is always rebuilt by reading
 * the file from the start; there are no snapshots and nothing is ever saved at
 * exit, so the way the program ends does not matter.
 *
 * Line format, UTF-8, '\n' terminated:
 * <pre>seq TAB ts TAB TYPE TAB field ... TAB crc32</pre>
 * The CRC covers everything before the last TAB. Inside fields, '\' TAB LF CR
 * are escaped as \\ \t \n \r, so any text (free-text lots included) survives.
 */
public final class Journal implements Closeable {

    static final DateTimeFormatter TS =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private final File file;
    private final Clock clock;
    private FileChannel ch;
    private long goodLength;
    private long lastSeq;
    private boolean broken;
    private long lastForceMillis;

    private Journal(File file, Clock clock, FileChannel ch, long goodLength, long lastSeq) {
        this.file = file;
        this.clock = clock;
        this.ch = ch;
        this.goodLength = goodLength;
        this.lastSeq = lastSeq;
    }

    /**
     * Opens a journal for appending after a recovered prefix. The file must
     * already hold exactly {@code goodLength} good bytes (see {@link #recover}).
     */
    public static Journal openForAppend(File file, Replay replay, Clock clock) throws IOException {
        boolean created = !file.exists();
        File dir = file.getAbsoluteFile().getParentFile();
        if (dir != null) Files.createDirectories(dir.toPath());
        FileChannel ch = FileChannel.open(file.toPath(),
            StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ);
        long good = replay == null ? 0 : replay.goodLength;
        if (ch.size() != good) {
            ch.close();
            throw new IOException("journal length changed under us: " + file);
        }
        if (created) forceDirectory(dir);
        long last = replay == null || replay.events.isEmpty()
            ? 0 : replay.events.get(replay.events.size() - 1).seq;
        return new Journal(file, clock, ch, good, last);
    }

    public File file() {
        return file;
    }

    public synchronized long lastSeq() {
        return lastSeq;
    }

    /** Wall-clock millis of the last successful force(), 0 if none yet. */
    public synchronized long lastForceMillis() {
        return lastForceMillis;
    }

    /** true after a failed write that could not be rolled back: see {@link #probe}. */
    public synchronized boolean isBroken() {
        return broken;
    }

    /** Local date-time of the journal's clock, for callers that need "now". */
    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /**
     * Appends one event and forces it to the disk. Returns only after force():
     * the caller may then, and only then, change the model. On failure the
     * file is cut back to the last good byte and the exception propagates:
     * the action did not happen.
     */
    public synchronized Event append(Event.Type type, List<String> fields) throws IOException {
        if (broken) throw new IOException("il disco non scrive (scrittura precedente fallita)");
        if (ch == null) throw new IOException("journal closed");
        long seq = lastSeq + 1;
        String ts = LocalDateTime.now(clock).format(TS);
        Event e = new Event(seq, ts, type, fields);
        byte[] line = encode(e).getBytes(StandardCharsets.UTF_8);
        try {
            ByteBuffer buf = ByteBuffer.wrap(line);
            long pos = goodLength;
            while (buf.hasRemaining()) pos += ch.write(buf, pos);
            ch.force(true);
        } catch (IOException ex) {
            rollback();
            throw ex;
        }
        goodLength += line.length;
        lastSeq = seq;
        lastForceMillis = System.currentTimeMillis();
        return e;
    }

    /**
     * After a failed write: tries to bring the file back to its last good
     * length and force it. true means writing may resume.
     */
    public synchronized boolean probe() {
        if (ch == null) return false;
        try {
            if (ch.size() != goodLength) ch.truncate(goodLength);
            ch.force(true);
            broken = false;
            lastForceMillis = System.currentTimeMillis();
            return true;
        } catch (IOException e) {
            broken = true;
            return false;
        }
    }

    private void rollback() {
        try {
            if (ch.size() > goodLength) ch.truncate(goodLength);
            ch.force(true);
        } catch (IOException e) {
            broken = true;   // a half line may be on disk: no append until probe()
        }
        // even a clean rollback marks the disk suspect: the UI asks for a probe
        broken = true;
    }

    @Override public synchronized void close() {
        if (ch == null) return;
        try { ch.close(); } catch (IOException ignored) {}
        ch = null;
    }

    // ── encoding ─────────────────────────────────────────────────────────

    static String encode(Event e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.seq).append('\t').append(e.ts).append('\t').append(e.type.name());
        for (String f : e.fields()) sb.append('\t').append(escape(f));
        String body = sb.toString();
        return body + '\t' + crc(body) + '\n';
    }

    static String crc(String body) {
        CRC32 c = new CRC32();
        c.update(body.getBytes(StandardCharsets.UTF_8));
        return String.format("%08x", c.getValue());
    }

    static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\\': sb.append("\\\\"); break;
                case '\t': sb.append("\\t");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                default:   sb.append(ch);
            }
        }
        return sb.toString();
    }

    static String unescape(String s) {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch != '\\' || i + 1 >= s.length()) { sb.append(ch); continue; }
            char n = s.charAt(++i);
            switch (n) {
                case 't': sb.append('\t'); break;
                case 'n': sb.append('\n'); break;
                case 'r': sb.append('\r'); break;
                default:  sb.append(n);
            }
        }
        return sb.toString();
    }

    /** null when the line is not a well-formed event (bad CRC, bad shape). */
    static Event decode(String line) {
        int lastTab = line.lastIndexOf('\t');
        if (lastTab < 0) return null;
        String body = line.substring(0, lastTab);
        if (!crc(body).equals(line.substring(lastTab + 1))) return null;
        String[] parts = body.split("\t", -1);
        if (parts.length < 3) return null;
        long seq;
        try { seq = Long.parseLong(parts[0]); } catch (NumberFormatException e) { return null; }
        Event.Type type = Event.Type.parse(parts[2]);
        if (type == null) return null;
        List<String> fields = new ArrayList<>();
        for (int i = 3; i < parts.length; i++) fields.add(unescape(parts[i]));
        return new Event(seq, parts[1], type, fields);
    }

    // ── reading ──────────────────────────────────────────────────────────

    /** What a read of the journal found. Never an exception for bad content. */
    public static final class Replay {
        /** the good prefix, in order */
        public final List<Event> events;
        /** bytes of the good prefix */
        public final long goodLength;
        /** the torn last line, as text, when a shutdown cut a write; else null */
        public final String discardedTail;
        /** the label and lot of the torn line, when it was a scan (best effort) */
        public final String discardedLabel;
        public final String discardedLot;
        /** a broken line in the middle: corruption, not a shutdown */
        public final boolean corrupted;
        public final String error;
        /** where the corrupted original was copied, when it was */
        public final File corruptCopy;

        Replay(List<Event> events, long goodLength, String discardedTail,
               String discardedLabel, String discardedLot,
               boolean corrupted, String error, File corruptCopy) {
            this.events = events;
            this.goodLength = goodLength;
            this.discardedTail = discardedTail;
            this.discardedLabel = discardedLabel;
            this.discardedLot = discardedLot;
            this.corrupted = corrupted;
            this.error = error;
            this.corruptCopy = corruptCopy;
        }

        public boolean hasDiscardedTail() {
            return discardedTail != null;
        }
    }

    /** Reads without touching the file. */
    public static Replay read(File file) throws IOException {
        byte[] all = file.exists() ? Files.readAllBytes(file.toPath()) : new byte[0];
        return parse(all);
    }

    static Replay parse(byte[] all) {
        List<Event> events = new ArrayList<>();
        CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        long good = 0;
        int start = 0;
        long expected = 1;
        while (start < all.length) {
            int nl = -1;
            for (int i = start; i < all.length; i++) {
                if (all[i] == '\n') { nl = i; break; }
            }
            if (nl < 0) {
                // no terminator: the write was cut by a shutdown
                String tail = lenient(all, start, all.length);
                return torn(events, good, tail);
            }
            boolean last = nl == all.length - 1;
            String line;
            try {
                line = dec.decode(ByteBuffer.wrap(all, start, nl - start)).toString();
            } catch (CharacterCodingException e) {
                line = null;
            }
            Event e = line == null ? null : decode(line);
            if (e == null) {
                String text = lenient(all, start, nl);
                if (last) return torn(events, good, text);
                return new Replay(events, good, null, null, null, true,
                    "riga " + (events.size() + 1) + " del giornale illeggibile", null);
            }
            if (e.seq != expected) {
                return new Replay(events, good, null, null, null, true,
                    "numerazione del giornale interrotta alla riga " + (events.size() + 1)
                    + " (attesa " + expected + ", trovata " + e.seq + ")", null);
            }
            events.add(e);
            expected++;
            good = nl + 1;
            start = nl + 1;
        }
        return new Replay(events, good, null, null, null, false, null, null);
    }

    private static String lenient(byte[] all, int from, int to) {
        CharBuffer cb = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(all, from, to - from));
        return cb.toString();
    }

    private static Replay torn(List<Event> events, long good, String tail) {
        String label = null, lot = null;
        String[] parts = tail.split("\t", -1);
        if (parts.length >= 6 && "AGGIUNGI".equals(parts[2])) {
            label = unescape(parts[4]);
            lot = unescape(parts[5]);
        } else if (parts.length >= 5 && "AGGIUNGI".equals(parts[2])) {
            label = unescape(parts[4]);
        } else if (parts.length >= 5 && "INTERVALLO".equals(parts[2])) {
            lot = unescape(parts[4]);
            if (parts.length >= 6) label = unescape(parts[5]).replace('|', ' ');
        }
        return new Replay(events, good, tail, label, lot, false, null, null);
    }

    /**
     * Reads the journal and repairs what a shutdown can break:
     * <ul>
     * <li>torn last line: the file is cut back to the last good byte and the
     *     discarded content is returned, so the operator can scan it again;</li>
     * <li>broken line in the middle: nothing is cut. The file is copied next to
     *     itself as {@code .corrotto-<timestamp>} and the result is flagged
     *     {@code corrupted}: the caller opens the day read only.</li>
     * </ul>
     */
    public static Replay recover(File file, Clock clock) throws IOException {
        Replay r = read(file);
        if (r.corrupted) {
            String stamp = LocalDateTime.now(clock).format(
                DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            File copy = new File(file.getParentFile(), file.getName() + ".corrotto-" + stamp);
            int k = 2;
            while (copy.exists()) {
                copy = new File(file.getParentFile(),
                                file.getName() + ".corrotto-" + stamp + "_" + (k++));
            }
            Files.copy(file.toPath(), copy.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            return new Replay(r.events, r.goodLength, null, null, null, true, r.error, copy);
        }
        if (r.hasDiscardedTail()) {
            try (FileChannel c = FileChannel.open(file.toPath(), StandardOpenOption.WRITE)) {
                c.truncate(r.goodLength);
                c.force(true);
            }
        }
        return r;
    }

    /** Fields as a list, for callers building events. */
    public static List<String> fields(String... values) {
        return Arrays.asList(values);
    }

    /** Best effort: on Linux a directory can be forced, on Windows it cannot. */
    private static void forceDirectory(File dir) {
        if (dir == null) return;
        try (FileChannel d = FileChannel.open(dir.toPath(), StandardOpenOption.READ)) {
            d.force(true);
        } catch (IOException | RuntimeException ignored) {
            // not supported here: the file's own force() is what counts
        }
    }
}
