package app.ui;

// Adapted from AutoFillSuite test/app/ui/SpinnerCommitTest.java — keep behaviour in sync.

import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;

// spinners — born from a real bug like its siblings: the operator typed 20
// over the 30, pressed STAMPA, and 30 sheets came out. every button in the
// app is focusable(false) for the scanner's sake, so a click never blurs the
// spinner editor — and an uncommitted edit means the DISPLAYED number and the
// number the model answers with are two different numbers. never again.
//
//   java -cp build app.ui.SpinnerCommitTest
public final class SpinnerCommitTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        typedValue_reachesTheModel_withoutFocusLoss();
        invalidText_neverCorruptsTheModel();
        corruptSettings_areClampedIntoRange();

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static JFormattedTextField editor(JSpinner sp) {
        return ((JSpinner.DefaultEditor) sp.getEditor()).getTextField();
    }

    // the exact failure: 30 in the model, "20" typed on screen, no blur —
    // getValue() must answer 20, because 20 is what the operator SEES
    private static void typedValue_reachesTheModel_withoutFocusLoss() {
        JSpinner sp = AppTheme.spinnerInt(30, 1, 9999);
        editor(sp).setText("20");
        check("the number on screen IS the number in the model",
              ((Number) sp.getValue()).intValue() == 20);

        editor(sp).setText("7");
        check("a second edit follows just the same",
              ((Number) sp.getValue()).intValue() == 7);
    }

    private static void invalidText_neverCorruptsTheModel() {
        JSpinner sp = AppTheme.spinnerInt(30, 1, 9999);
        editor(sp).setText("abc");
        check("garbage in the editor leaves the model on the last valid value",
              ((Number) sp.getValue()).intValue() == 30);
        editor(sp).setText("");
        check("an emptied editor cannot blank the model",
              ((Number) sp.getValue()).intValue() == 30);
        editor(sp).setText("99999");
        check("a typed value above max never reaches the model",
              ((Number) sp.getValue()).intValue() <= 9999);
    }

    private static void corruptSettings_areClampedIntoRange() {
        check("a saved value above max is clamped down",
              ((Number) AppTheme.spinnerInt(50000, 1, 9999).getValue()).intValue() == 9999);
        check("a saved value below min is clamped up",
              ((Number) AppTheme.spinnerInt(-5, 1, 9999).getValue()).intValue() == 1);
        check("a sane saved value passes through untouched",
              ((Number) AppTheme.spinnerInt(20, 1, 9999).getValue()).intValue() == 20);
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
