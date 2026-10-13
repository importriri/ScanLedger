package app.ui;

// Adapted from AutoFillSuite test/app/ui/TextFitTest.java — keep behaviour in sync.

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

// text fitting — born on the shop floor: the status bar centered its string,
// so "Da 01_2612900022 -> 01_2612900031" overflowed BOTH sides and the last
// digits — the only ones that say where the range ENDS — were unreadable.
// the rule lives here: an overflowing text keeps its TAIL. here it also cuts
// the long free-text lots in the table, where the tail is what the operator reads.
//
//   java -cp build app.ui.TextFitTest
public final class TextFitTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        fitTail_keepsTheTail();
        freeTextLot_keepsItsTail();

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static FontMetrics metrics() {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setFont(AppTheme.F_MONO);
        FontMetrics fm = g.getFontMetrics();
        g.dispose();
        return fm;
    }

    private static void fitTail_keepsTheTail() {
        FontMetrics fm = metrics();
        String s = "Da 01_2612900022 -> 01_2612900031";

        check("a string that fits passes through untouched",
              AppTheme.fitTail(s, fm, fm.stringWidth(s) + 5).equals(s));

        String cut = AppTheme.fitTail(s, fm, fm.stringWidth(s) / 2);
        check("an overflowing string keeps its TAIL", cut.endsWith("0031"));
        check("the cut is announced at the head", cut.startsWith("..."));
        check("the fitted string respects the budget",
              fm.stringWidth(cut) <= fm.stringWidth(s) / 2);
        check("even a tiny budget still shows the last character",
              AppTheme.fitTail(s, fm, 8).endsWith("1"));
    }

    // the real free-text lot shape: its end ("2023/12/11;A") is the part that differs
    private static void freeTextLot_keepsItsTail() {
        FontMetrics fm = metrics();
        String lot = "CA HVCO 50 T 048 3-2; M-CA HVCO 50 T 048 3-2  Rev.04; "
                   + "Serial No.: 000066076 ; 2023/12/11;A";
        String cut = AppTheme.fitTail(lot, fm, 160);
        check("a long free-text lot keeps its last characters", cut.endsWith("2023/12/11;A"));
        check("a long free-text lot fits its budget", fm.stringWidth(cut) <= 160);
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
