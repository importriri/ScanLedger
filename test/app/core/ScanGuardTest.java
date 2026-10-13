package app.core;

// Adapted from AutoFillSuite test/app/core/ScanGuardTest.java — keep behaviour in sync.

// the scan guard, pure on purpose so it can be tested without a scanner and
// without a screen: the lot scanned into QR 1. the interesting part is not
// "does it match", it is telling an INVERSION (swappable, the operator just has
// to press one key) from NONSENSE (nobody can guess what the operator meant, so
// nothing may be recorded).
//
// the guard cases are the AutoFillSuite ones, unchanged: both apps must answer
// the same way for the same pair. the mouse fail-safe half of the original
// suite stays there, with the robot it protects.
//
//   java -cp build app.core.ScanGuardTest
public final class ScanGuardTest {

    private static final String LABEL = "[0-9]{6}\\.[0-9]{3}_.*";
    private static final String LOT   = "[0-9]{18}";

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        noPatterns_holdsNoOpinion();
        rightOrder_passes();
        invertedPair_isRecognisedAsSwappable();
        nonsense_isNotAnInversion();
        onePatternOnly_stillCatchesTheInversion();
        invalidPattern_isIgnored_neverBlocksTheLine();
        nullCode_neverMatchesAPattern();
        whitespace_isTrimmedLikeTheFieldDoes();

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // ── ScanGuard ─────────────────────────────────────────────────────────

    private static void noPatterns_holdsNoOpinion() {
        check("no patterns: anything goes through",
              ScanGuard.inspect("whatever", "else", "", "") == ScanGuard.OK);
        check("null patterns read as no patterns",
              ScanGuard.inspect("whatever", "else", null, null) == ScanGuard.OK);
    }

    private static void rightOrder_passes() {
        check("label in QR 1 and lot in QR 2 is what we want",
              ScanGuard.inspect("209531.001_01-01_2612000234",
                                "900612202601310007", LABEL, LOT) == ScanGuard.OK);
    }

    private static void invertedPair_isRecognisedAsSwappable() {
        check("the lot scanned into QR 1 comes back as INVERTED",
              ScanGuard.inspect("900612202601310007",
                                "209531.001_01-01_2612000234", LABEL, LOT) == ScanGuard.INVERTED);
    }

    private static void nonsense_isNotAnInversion() {
        check("a code that fits neither field is a MISMATCH, not a swap",
              ScanGuard.inspect("BOH", "900612202601310007", LABEL, LOT) == ScanGuard.MISMATCH);
        check("two codes that fit nothing are a MISMATCH",
              ScanGuard.inspect("BOH", "PURE-BOH", LABEL, LOT) == ScanGuard.MISMATCH);
    }

    private static void onePatternOnly_stillCatchesTheInversion() {
        check("describing QR 1 alone is enough to spot the swap",
              ScanGuard.inspect("900612202601310007",
                                "209531.001_01-01_2612000234", LABEL, "") == ScanGuard.INVERTED);
        check("describing QR 2 alone is enough as well",
              ScanGuard.inspect("900612202601310007",
                                "209531.001_01-01_2612000234", "", LOT) == ScanGuard.INVERTED);
    }

    private static void invalidPattern_isIgnored_neverBlocksTheLine() {
        check("a broken regex is reported as invalid", !ScanGuard.validPattern("[0-9"));
        check("an empty pattern is valid (it means: no check)", ScanGuard.validPattern("  "));
        check("a broken regex never stops a pair",
              ScanGuard.inspect("anything", "at all", "[0-9", "[0-9") == ScanGuard.OK);
        // half broken: the good half still does its job
        check("the half that compiles keeps working",
              ScanGuard.inspect("BOH", "900612202601310007", "[0-9", LOT) == ScanGuard.OK);
    }

    private static void nullCode_neverMatchesAPattern() {
        check("a null code cannot satisfy a pattern",
              ScanGuard.inspect(null, "900612202601310007", LABEL, LOT) == ScanGuard.MISMATCH);
    }

    private static void whitespace_isTrimmedLikeTheFieldDoes() {
        check("a trailing space from the scanner is not a mismatch",
              ScanGuard.inspect(" 209531.001_01-01_2612000234 ",
                                " 900612202601310007 ", LABEL, LOT) == ScanGuard.OK);
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
