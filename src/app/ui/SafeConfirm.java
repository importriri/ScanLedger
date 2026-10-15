package app.ui;

// Adapted from AutoFillSuite src/app/ui/SafeConfirm.java — keep behaviour in sync.

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import java.awt.Component;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * A yes/no question that a scanner cannot answer.
 *
 * A scanner types the code and ends it with ENTER (or TAB). A plain Sì/No
 * window opens with Sì as the default button, so the next scan confirmed a
 * correction nobody had read. Here No is the default and the only button the
 * keyboard can reach: ENTER, SPACE and ESC all answer No, TAB cannot move to Sì
 * (on Windows the default button follows the focus). Sì takes the mouse.
 */
final class SafeConfirm {

    static final String YES = "Sì";
    static final String NO = "No";

    private SafeConfirm() {}

    /** Shows the question and waits; true only when Sì was clicked. */
    static boolean ask(Component parent, Object message, String title) {
        JOptionPane pane = pane(message);
        JDialog dialog = pane.createDialog(parent, title);
        dialog.setVisible(true);   // modal
        dialog.dispose();
        return pane.getValue() == button(pane, YES);
    }

    /** The question, built apart so the tests can open it and press keys on it. */
    static JOptionPane pane(Object message) {
        final JButton yes = new JButton(YES);
        final JButton no = new JButton(NO);
        yes.setFocusable(false);
        yes.setToolTipText("Conferma con il mouse");
        final JOptionPane pane = new JOptionPane(message, JOptionPane.WARNING_MESSAGE,
            JOptionPane.YES_NO_OPTION, null, new Object[] { yes, no }, no) {
            @Override
            public JDialog createDialog(Component parentComponent, String title) {
                JDialog d = super.createDialog(parentComponent, title);
                d.getRootPane().setDefaultButton(no);
                d.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowOpened(WindowEvent e) {
                        no.requestFocusInWindow();
                    }
                });
                return d;
            }
        };
        yes.addActionListener(e -> pane.setValue(yes));
        no.addActionListener(e -> pane.setValue(no));
        return pane;
    }

    static JButton button(JOptionPane pane, String text) {
        for (Object o : pane.getOptions()) {
            if (o instanceof JButton && text.equals(((JButton) o).getText())) return (JButton) o;
        }
        return null;
    }
}
