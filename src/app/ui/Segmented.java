package app.ui;

// Adapted from AutoFillSuite src/app/ui/Segmented.java — keep behaviour in sync.

import javax.swing.*;
import java.awt.*;
import java.util.function.IntConsumer;

/**
 * Segmented selector — a machine's mode switch, not a filing-cabinet tab.
 * The selected segment is filled; the others recede. Never focusable, so the
 * scanner's TAB can never land on it.
 */
public class Segmented extends JPanel {

    private final JButton[] segments;
    private final IntConsumer onChange;
    private final boolean strong;   // strong = main selector, soft = sub-selector
    private int selected = 0;

    /**
     * @param initial the segment to start on — set SILENTLY. Restoring a saved
     *        state must never fire onChange: the listener would run while the
     *        rest of the panel is still being built and touch fields that do not
     *        exist yet. select() is the user's path; the constructor is not.
     */
    public Segmented(String[] labels, boolean strong, int initial, IntConsumer onChange) {
        this.segments = new JButton[labels.length];
        this.onChange = onChange;
        this.strong   = strong;
        this.selected = (initial >= 0 && initial < labels.length) ? initial : 0;

        setLayout(new GridLayout(1, labels.length, 3, 0));
        setOpaque(true);
        setBackground(strong ? AppTheme.MANTLE : AppTheme.BASE);
        setBorder(strong
            ? BorderFactory.createEmptyBorder(4, 4, 4, 4)
            : BorderFactory.createCompoundBorder(
                  BorderFactory.createLineBorder(AppTheme.SURFACE1),
                  BorderFactory.createEmptyBorder(3, 3, 3, 3)));

        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            JButton b = new JButton(labels[i]);
            b.setFocusable(false);
            b.setBorderPainted(false);
            // false: the Windows LAF skin ignores setBackground otherwise
            b.setContentAreaFilled(false);
            b.setOpaque(true);
            b.setFont(strong ? AppTheme.F_BOLD : AppTheme.F_SMALL.deriveFont(Font.BOLD));
            b.setBorder(BorderFactory.createEmptyBorder(strong ? 6 : 4, 6, strong ? 6 : 4, 6));
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.addActionListener(e -> select(idx));
            segments[i] = b;
            add(b);
        }
        paintSegments();
    }

    /** The user's path: repaint AND notify. */
    public void select(int idx) {
        if (idx < 0 || idx >= segments.length) return;
        selected = idx;
        paintSegments();
        onChange.accept(idx);
    }

    public int getSelected() {
        return selected;
    }

    private void paintSegments() {
        for (int i = 0; i < segments.length; i++) {
            boolean on = i == selected;
            if (strong) {
                segments[i].setBackground(on ? AppTheme.MAUVE : AppTheme.MANTLE);
                segments[i].setForeground(on ? AppTheme.ON_ACCENT : AppTheme.SUBTEXT);
            } else {
                segments[i].setBackground(on ? AppTheme.SURFACE1 : AppTheme.BASE);
                segments[i].setForeground(on ? AppTheme.TEXT : AppTheme.OVERLAY);
            }
        }
    }
}
