package app.ui;

import javax.swing.*;
import java.awt.*;

/**
 * A full-width notice under the header: what the start found, what is still
 * pending. Tinted plate, bold title, one line of detail, actions on the right.
 * It says things once and goes away when the operator answers.
 */
final class Banner extends JPanel {

    private final Color tone;
    final JLabel title = new JLabel();
    final JLabel detail = new JLabel();
    private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));

    Banner(Color tone, String head, String body) {
        this.tone = tone;
        setOpaque(false);
        setLayout(new BorderLayout(12, 0));
        setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 10));
        JPanel text = new JPanel(new GridLayout(0, 1, 0, 1));
        text.setOpaque(false);
        title.setFont(AppTheme.F_BANNER);
        title.setForeground(AppTheme.TEXT);
        title.setText(head);
        detail.setFont(AppTheme.F_UI);
        detail.setForeground(AppTheme.SUBTEXT);
        detail.setText(body == null ? "" : body);
        detail.setToolTipText(body);
        text.add(title);
        if (body != null && !body.isEmpty()) text.add(detail);
        add(text, BorderLayout.CENTER);
        actions.setOpaque(false);
        add(actions, BorderLayout.EAST);
    }

    /** An action; {@code closes} removes the banner after it ran. */
    Banner action(String label, boolean primary, boolean closes, Runnable r) {
        JButton b = primary ? AppTheme.button(label, AppTheme.MAUVE) : AppTheme.ghost(label);
        b.addActionListener(e -> {
            if (r != null) r.run();
            if (closes) dismiss();
        });
        actions.add(b);
        return this;
    }

    void dismiss() {
        Container p = getParent();
        if (p == null) return;
        p.remove(this);
        p.revalidate();
        p.repaint();
    }

    String titleText() {
        return title.getText();
    }

    @Override public Dimension getMinimumSize() {
        return new Dimension(200, getPreferredSize().height);
    }

    @Override public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(AppTheme.blend(tone, AppTheme.SURFACE0, 0.16f));
        g2.fillRoundRect(0, 2, getWidth() - 1, getHeight() - 5, 12, 12);
        g2.setColor(tone);
        g2.drawRoundRect(0, 2, getWidth() - 1, getHeight() - 5, 12, 12);
        g2.dispose();
        super.paintComponent(g);
    }
}
