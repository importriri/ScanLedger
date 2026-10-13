package app.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A range from its first label: the last {@code digits} characters are the
 * moving sequence, the rest is the fixed prefix. Pure, so the screen and the
 * tests build exactly the same labels.
 *
 * A range that would need one more digit (998 + 5 = 1000, 1001) is refused:
 * those labels do not exist. AutoFillSuite's range did not check this.
 */
public final class Ranges {

    public enum Problem { NONE, EMPTY, TOO_SHORT, NOT_DIGITS, OVERFLOW, BAD_COUNT }

    public final Problem problem;
    public final String prefix;
    public final long seq0;
    public final int digits;
    private final List<String> labels;

    private Ranges(Problem problem, String prefix, long seq0, int digits, List<String> labels) {
        this.problem = problem;
        this.prefix = prefix;
        this.seq0 = seq0;
        this.digits = digits;
        this.labels = Collections.unmodifiableList(labels);
    }

    public static Ranges of(String first, int count, int digits) {
        String f = first == null ? "" : first.trim();
        List<String> none = new ArrayList<>();
        if (f.isEmpty()) return new Ranges(Problem.EMPTY, "", 0, digits, none);
        if (digits < 1 || digits > 9) digits = 3;
        if (f.length() <= digits) return new Ranges(Problem.TOO_SHORT, "", 0, digits, none);
        String tail = f.substring(f.length() - digits);
        for (int i = 0; i < tail.length(); i++) {
            if (!Character.isDigit(tail.charAt(i))) return new Ranges(Problem.NOT_DIGITS, "", 0, digits, none);
        }
        String prefix = f.substring(0, f.length() - digits);
        long seq0 = Long.parseLong(tail);
        if (count < 1) return new Ranges(Problem.BAD_COUNT, prefix, seq0, digits, none);
        long max = 1;
        for (int i = 0; i < digits; i++) max *= 10;
        if (seq0 + count - 1 >= max) return new Ranges(Problem.OVERFLOW, prefix, seq0, digits, none);
        List<String> out = new ArrayList<>(count);
        String fmt = "%0" + digits + "d";
        for (int i = 0; i < count; i++) out.add(prefix + String.format(fmt, seq0 + i));
        return new Ranges(Problem.NONE, prefix, seq0, digits, out);
    }

    public boolean ok() {
        return problem == Problem.NONE;
    }

    public List<String> labels() {
        return labels;
    }

    /** The largest sequence the digits allow, e.g. 999 for three. */
    public long maxSeq() {
        long max = 1;
        for (int i = 0; i < digits; i++) max *= 10;
        return max - 1;
    }

    /** Operator-facing reason, Italian. */
    public String message(int count) {
        switch (problem) {
            case EMPTY:      return "spara il primo codice";
            case TOO_SHORT:  return "codice troppo corto per " + digits + " cifre mobili";
            case NOT_DIGITS: return "le ultime " + digits + " cifre devono essere numeriche";
            case BAD_COUNT:  return "quantità non valida";
            case OVERFLOW:   return seq0 + " + " + count + " supera " + maxSeq()
                                    + ": le etichette avrebbero una cifra in più";
            default:         return "";
        }
    }
}
