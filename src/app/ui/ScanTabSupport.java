package app.ui;

// Adapted from AutoFillSuite src/app/ui/ScanTabSupport.java — keep behaviour in sync.

import javax.swing.JComponent;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;

/**
 * Makes scanner suffix TAB behave like ENTER on the second QR field.
 *
 * Swing normally consumes TAB as a focus-traversal key before the field can
 * handle it. This dispatcher converts an unmodified TAB pressed in a field
 * that is explicitly marked with {@link #SUBMIT_ON_TAB} into that field's
 * normal ActionEvent. Everything else keeps Swing's default focus traversal.
 *
 * Why a marker and not "the second of exactly two fields" (the AutoFillSuite
 * rule): counting fields breaks silently the day a panel gains a third one —
 * the scanner's TAB stops submitting and no test notices. A marker survives
 * any layout change.
 */
public final class ScanTabSupport {

    /** Client property: TRUE on the one field whose TAB submits the pair. */
    public static final String SUBMIT_ON_TAB = "scan.submitOnTab";

    private static boolean installed;

    private ScanTabSupport() {}

    public static synchronized void install() {
        if (installed) return;
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .addKeyEventDispatcher(ScanTabSupport::dispatch);
        installed = true;
    }

    /** Marks a field so that TAB submits it like ENTER. */
    public static void markSubmitOnTab(JTextField field) {
        field.putClientProperty(SUBMIT_ON_TAB, Boolean.TRUE);
    }

    private static boolean dispatch(KeyEvent event) {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .getFocusOwner();
        return handle(event, focusOwner);
    }

    static boolean handle(KeyEvent event, Component focusOwner) {
        if (event.getID() != KeyEvent.KEY_PRESSED
                || event.getKeyCode() != KeyEvent.VK_TAB
                || event.getModifiersEx() != 0
                || !(focusOwner instanceof JTextField)
                || !isMarked((JTextField) focusOwner)) {
            return false;
        }
        ((JTextField) focusOwner).postActionEvent();
        event.consume();
        return true;
    }

    private static boolean isMarked(JComponent field) {
        return Boolean.TRUE.equals(field.getClientProperty(SUBMIT_ON_TAB));
    }
}
