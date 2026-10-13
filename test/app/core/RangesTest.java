package app.core;

// a range is its first label plus a count: the last digits move, the rest is
// fixed. the bug this suite exists for comes from AutoFillSuite: 998 + 5 gave
// 1000 and 1001, labels with one digit more, that do not exist.
//
//   java -cp out app.core.RangesTest
public final class RangesTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        cleanRange_buildsEveryLabel();
        rangeTouchingTheLastValue_isAccepted();
        rangePastTheLastValue_isRefused();
        shapeProblems_areNamed();

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static void cleanRange_buildsEveryLabel() {
        Ranges r = Ranges.of("209531.001_01-01_2612001850", 30, 3);
        check("a clean range is ok", r.ok());
        check("it has every label", r.labels().size() == 30);
        check("the first label is the scanned one", r.labels().get(0).equals("209531.001_01-01_2612001850"));
        check("the last label ends at 879", r.labels().get(29).equals("209531.001_01-01_2612001879"));
        check("the prefix is everything but the moving digits", r.prefix.equals("209531.001_01-01_2612001"));
    }

    private static void rangeTouchingTheLastValue_isAccepted() {
        Ranges r = Ranges.of("LBL995", 5, 3);
        check("995 + 5 ends at 999 and is accepted", r.ok() && r.labels().get(4).equals("LBL999"));
    }

    private static void rangePastTheLastValue_isRefused() {
        Ranges r = Ranges.of("LBL998", 5, 3);
        check("998 + 5 is refused as an overflow", r.problem == Ranges.Problem.OVERFLOW);
        check("an overflow builds no label", r.labels().isEmpty());
        check("the message says what would happen", r.message(5).contains("supera 999"));
        check("more moving digits make room", Ranges.of("LBL0998", 5, 4).ok());
    }

    private static void shapeProblems_areNamed() {
        check("an empty first label asks for a scan", Ranges.of("  ", 5, 3).problem == Ranges.Problem.EMPTY);
        check("a code shorter than the digits is too short", Ranges.of("12", 5, 3).problem == Ranges.Problem.TOO_SHORT);
        check("letters in the moving digits are refused",
              Ranges.of("LBL12A", 5, 3).problem == Ranges.Problem.NOT_DIGITS);
        check("a zero count is refused", Ranges.of("LBL001", 0, 3).problem == Ranges.Problem.BAD_COUNT);
    }

    private static void check(String name, boolean cond) {
        if (cond) { passed++; System.out.println("  ok   " + name); }
        else      { failed++; System.out.println("  FAIL " + name); }
    }
}
