package app.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * Components only Registro QR needs, built from AppTheme parts. Kept apart so
 * AppTheme itself stays in sync with AutoFillSuite.
 */
final class Widgets {

    private Widgets() {}

    /** A field like AppTheme.field(), with a caller-chosen font. */
    static void styleField(final JTextField t, Font font) {
        t.setFont(font);
        t.setBackground(AppTheme.MANTLE);
        t.setForeground(AppTheme.TEXT);
        t.setCaretColor(AppTheme.MAUVE);
        t.setSelectionColor(AppTheme.blend(AppTheme.MAUVE, AppTheme.BASE, 0.35f));
        t.setSelectedTextColor(AppTheme.TEXT);
        t.setBorder(AppTheme.fieldBorder(AppTheme.SURFACE1));
        t.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { t.setBorder(AppTheme.fieldBorder(AppTheme.MAUVE)); }
            @Override public void focusLost(FocusEvent e)   { t.setBorder(AppTheme.fieldBorder(AppTheme.SURFACE1)); }
        });
    }

    /** The big scan field: monospaced, readable from a metre away. */
    static JTextField scanField() {
        JTextField t = new JTextField();
        // a fixed column count: a field sized by its TEXT grows past the column
        // with a long code, and GridBag then drops every row to its minimum
        t.setColumns(10);
        styleField(t, AppTheme.F_SCAN);
        t.setBorder(BorderFactory.createCompoundBorder(
            AppTheme.fieldBorder(AppTheme.SURFACE1), BorderFactory.createEmptyBorder(4, 2, 4, 2)));
        t.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                t.setBorder(BorderFactory.createCompoundBorder(
                    AppTheme.fieldBorder(AppTheme.MAUVE), BorderFactory.createEmptyBorder(4, 2, 4, 2)));
            }
            @Override public void focusLost(FocusEvent e) {
                t.setBorder(BorderFactory.createCompoundBorder(
                    AppTheme.fieldBorder(AppTheme.SURFACE1), BorderFactory.createEmptyBorder(4, 2, 4, 2)));
            }
        });
        return t;
    }

    /** Label with a small caption under it ("QR 1" / "etichetta"). */
    static JComponent caption(String title, String sub) {
        JPanel p = new JPanel(new GridLayout(0, 1, 0, 0));
        p.setOpaque(false);
        JLabel t = AppTheme.label(title);
        t.setForeground(AppTheme.TEXT);
        t.setFont(AppTheme.F_TITLE);
        p.add(t);
        if (sub != null) p.add(AppTheme.hint(sub));
        return p;
    }

    /**
     * The outcome box: a bordered, tinted plate with a big title and a mono
     * detail line. Text is set through {@link #show}; the detail keeps its tail.
     */
    static class ResultBox extends JPanel {
        private final JLabel title = new JLabel(" ", SwingConstants.CENTER);
        private final JLabel detail = new JLabel(" ", SwingConstants.CENTER);
        private Color line = AppTheme.SURFACE1;
        private Color fill = AppTheme.MANTLE;
        private String fullDetail = "";

        ResultBox() {
            setOpaque(false);
            setLayout(new GridLayout(0, 1, 0, 2));
            setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            title.setFont(AppTheme.F_TITLE.deriveFont(17f));
            detail.setFont(AppTheme.F_MONO);
            add(title);
            add(detail);
        }

        void show(Color accent, String head, String body) {
            line = accent;
            fill = AppTheme.blend(accent, AppTheme.SURFACE0, 0.16f);
            title.setForeground(accent);
            title.setText(head);
            fullDetail = body == null ? "" : body;
            detail.setForeground(AppTheme.TEXT);
            detail.setToolTipText(fullDetail.isEmpty() ? null : fullDetail);
            fitDetail();
            repaint();
        }

        void neutral(String head, String body) {
            line = AppTheme.SURFACE1;
            fill = AppTheme.MANTLE;
            title.setForeground(AppTheme.SUBTEXT);
            title.setText(head);
            fullDetail = body == null ? "" : body;
            detail.setForeground(AppTheme.OVERLAY);
            fitDetail();
            repaint();
        }

        String titleText()  { return title.getText(); }
        String detailText() { return fullDetail; }
        Color accent()      { return line; }

        @Override public void doLayout() {
            super.doLayout();
            fitDetail();
        }

        private void fitDetail() {
            int w = getWidth() - 24;
            String s = fullDetail.isEmpty() ? " " : fullDetail;
            detail.setText(w > 40 ? AppTheme.fitTail(s, detail.getFontMetrics(detail.getFont()), w) : s);
        }

        @Override public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(Math.min(d.width, 260), d.height);
        }

        @Override public Dimension getMinimumSize() {
            return new Dimension(120, super.getPreferredSize().height);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g2.setColor(line);
            g2.setStroke(new BasicStroke(1.6f));
            g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 12, 12);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
