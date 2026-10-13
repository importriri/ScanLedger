package app.ui;

// Adapted from AutoFillSuite test/app/ui/ScanTabSupportTest.java — keep behaviour in sync.

import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicInteger;

// scanners configured with TAB as the suffix on QR 2. the AutoFillSuite cases,
// unchanged, plus the one that the marker exists for: a third field in the
// panel must not switch the scanner's TAB off.
//
//   java -cp out app.ui.ScanTabSupportTest
public final class ScanTabSupportTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(ScanTabSupportTest::run);
        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed > 0 ? 1 : 0);
    }

    private static void run() {
        JPanel panel = new JPanel();
        JTextField qr1 = new JTextField();
        JTextField qr2 = new JTextField();
        ScanTabSupport.markSubmitOnTab(qr2);
        panel.add(qr1);
        panel.add(qr2);

        AtomicInteger actions = new AtomicInteger();
        qr2.addActionListener(e -> actions.incrementAndGet());

        KeyEvent tab = key(qr2, 0, KeyEvent.VK_TAB, '\t');
        check("plain TAB on QR2 is handled", ScanTabSupport.handle(tab, qr2));
        check("handled QR2 TAB is consumed", tab.isConsumed());
        check("QR2 TAB fires the same ActionEvent path as ENTER", actions.get() == 1);

        KeyEvent qr1Tab = key(qr1, 0, KeyEvent.VK_TAB, '\t');
        check("TAB on QR1 keeps normal focus traversal",
              !ScanTabSupport.handle(qr1Tab, qr1));
        check("QR1 TAB does not submit QR2", actions.get() == 1);

        KeyEvent shiftTab = key(qr2, InputEvent.SHIFT_DOWN_MASK, KeyEvent.VK_TAB, '\t');
        check("Shift+TAB on QR2 keeps backward focus traversal",
              !ScanTabSupport.handle(shiftTab, qr2));
        check("Shift+TAB does not submit QR2", actions.get() == 1);

        KeyEvent enter = key(qr2, 0, KeyEvent.VK_ENTER, '\n');
        check("ENTER remains owned by the field's existing listener",
              !ScanTabSupport.handle(enter, qr2));
        check("shim does not duplicate ENTER", actions.get() == 1);

        JTextField outsider = new JTextField();
        KeyEvent outsideTab = key(outsider, 0, KeyEvent.VK_TAB, '\t');
        check("TAB outside scan mode is untouched",
              !ScanTabSupport.handle(outsideTab, outsider));

        // the reason for the marker: the AutoFillSuite rule ("the second of
        // exactly two fields") goes silent the day the panel gains a third
        JTextField third = new JTextField();
        panel.add(third);
        KeyEvent tabWithThird = key(qr2, 0, KeyEvent.VK_TAB, '\t');
        check("a third field in the panel does not switch QR2 TAB off",
              ScanTabSupport.handle(tabWithThird, qr2) && actions.get() == 2);
        KeyEvent thirdTab = key(third, 0, KeyEvent.VK_TAB, '\t');
        check("TAB on the third field keeps normal traversal",
              !ScanTabSupport.handle(thirdTab, third) && actions.get() == 2);
        check("QR1 TAB is still traversal with three fields",
              !ScanTabSupport.handle(key(qr1, 0, KeyEvent.VK_TAB, '\t'), qr1));
    }

    private static KeyEvent key(Component source, int modifiers, int code, char ch) {
        return new KeyEvent(source, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
                            modifiers, code, ch);
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  ok   " + name);
        } else {
            failed++;
            System.out.println("  FAIL " + name);
        }
    }
}
