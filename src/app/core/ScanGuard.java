package app.core;

// Adapted from AutoFillSuite src/app/core/ScanGuard.java — keep behaviour in sync.

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The scanner has no idea which QR it just read: the operator does. Shooting the
 * lot into QR 1 and the label into QR 2 registers a pair the portal will never
 * match, and nobody notices until the verification comes back red.
 *
 * When the operator describes the two codes with a regular expression, this
 * catches the inversion BEFORE the pair is queued — and can tell an inversion
 * (both codes known, in the wrong holes) from plain nonsense (a code that fits
 * neither), because the two need different answers: swap the first, stop on the
 * second.
 *
 * Pure logic — no Swing, no robot, fully testable. An empty or invalid pattern
 * is an opinion not given: never a reason to block the line.
 */
public final class ScanGuard {

    /** The pair looks the way it was described (or nothing was described). */
    public static final int OK = 0;
    /** Each code fits the OTHER field: swapping them makes the pair valid. */
    public static final int INVERTED = 1;
    /** At least one code fits neither field: only the operator can sort it out. */
    public static final int MISMATCH = 2;

    private ScanGuard() {}

    /**
     * @param q1 what was scanned into QR 1 (the label)
     * @param q2 what was scanned into QR 2 (the lot)
     * @param p1 regex describing QR 1, may be null/blank/invalid
     * @param p2 regex describing QR 2, may be null/blank/invalid
     */
    public static int inspect(String q1, String q2, String p1, String p2) {
        Pattern pat1 = compile(p1);
        Pattern pat2 = compile(p2);
        if (pat1 == null && pat2 == null) return OK;   // nothing to check against

        if (matches(q1, pat1) && matches(q2, pat2)) return OK;
        if (matches(q2, pat1) && matches(q1, pat2)) return INVERTED;
        return MISMATCH;
    }

    /** True when the pattern is empty (no check) or compiles. */
    public static boolean validPattern(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) return true;
        try {
            Pattern.compile(pattern.trim());
            return true;
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    /** A typo in the settings must not stop the line: it becomes "no pattern". */
    private static Pattern compile(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) return null;
        try {
            return Pattern.compile(pattern.trim());
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    /** No pattern means everything fits; a null code fits nothing. */
    private static boolean matches(String value, Pattern pattern) {
        if (pattern == null) return true;
        if (value == null) return false;
        return pattern.matcher(value.trim()).matches();
    }
}
