package app.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One export as the journal remembers it: the ESPORTA event plus the exact
 * rows, with the values they had at that moment, rebuilt by replaying the
 * journal up to the event. This is what regeneration writes back.
 */
public final class ExportRecord {

    /** One data line of the CSV, in contract order. */
    public static final class Line {
        public final int n;
        public final String label;
        public final String lot;
        public final String ora;
        public final Row.Mode mode;
        /** NUOVA or CORREZIONE */
        public final String tipo;
        public final boolean modified;
        /** for CORREZIONE: the lot the portal should hold now; "" otherwise */
        public final String lotBefore;

        public Line(int n, String label, String lot, String ora, Row.Mode mode,
                    String tipo, boolean modified, String lotBefore) {
            this.n = n;
            this.label = label;
            this.lot = lot;
            this.ora = ora;
            this.mode = mode;
            this.tipo = tipo;
            this.modified = modified;
            this.lotBefore = lotBefore == null ? "" : lotBefore;
        }

        public boolean isCorrection() {
            return "CORREZIONE".equals(tipo);
        }
    }

    public final int number;
    public final String created;
    public final String sha256;
    public final String fileName;
    public final boolean reexport;
    public final long seq;
    /** event timestamp, ms precision */
    public final String ts;
    private final List<Line> lines;

    String copyOutcome = "";   // "" never tried, OK, FALLITA
    String copyFolder = "";
    String copyReason = "";

    ExportRecord(int number, String created, String sha256, String fileName,
                 boolean reexport, long seq, String ts, List<Line> lines) {
        this.number = number;
        this.created = created;
        this.sha256 = sha256;
        this.fileName = fileName;
        this.reexport = reexport;
        this.seq = seq;
        this.ts = ts;
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
    }

    public List<Line> lines() {
        return lines;
    }

    public int corrections() {
        int c = 0;
        for (Line l : lines) if (l.isCorrection()) c++;
        return c;
    }

    public String copyOutcome() { return copyOutcome; }
    public String copyFolder()  { return copyFolder; }
    public String copyReason()  { return copyReason; }

    /** a copy folder was tried and the last attempt failed */
    public boolean copyPending() {
        return "FALLITA".equals(copyOutcome);
    }
}
