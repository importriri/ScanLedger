package app.ui;

// Adapted from AutoFillSuite src/app/ui/AppTheme.java — keep behaviour in sync.

import app.config.Settings;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.text.DefaultFormatter;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Design system v2 — "line panel" on the Catppuccin palette.
 * Two flavors (Mocha dark / Latte light), chosen in the settings, applied
 * at the next startup (read once, static final).
 * Hand-rolled, zero dependencies: rounded components, hover states, flat
 * tabs, a custom progress bar. Window frames stay native.
 */
public final class AppTheme {

    private AppTheme() {}

    // ── flavor, read once at startup ─────────────────────────────────────
    public static final boolean LATTE = "latte".equalsIgnoreCase(
        Settings.getInstance().get(Settings.UI_FLAVOR, "mocha"));

    private static Color pick(int mocha, int latte) {
        return new Color(LATTE ? latte : mocha);
    }

    // ── Catppuccin palette ───────────────────────────────────────────────
    public static final Color CRUST    = pick(0x11111b, 0xdce0e8);
    public static final Color MANTLE   = pick(0x181825, 0xe6e9ef);
    public static final Color BASE     = pick(0x1e1e2e, 0xeff1f5);
    public static final Color SURFACE0 = pick(0x313244, 0xccd0da);
    public static final Color SURFACE1 = pick(0x45475a, 0xbcc0cc);
    public static final Color SURFACE2 = pick(0x585b70, 0xacb0be);
    public static final Color TEXT     = pick(0xcdd6f4, 0x4c4f69);
    public static final Color SUBTEXT  = pick(0xa6adc8, 0x6c6f85);
    public static final Color OVERLAY  = pick(0x6c7086, 0x9ca0b0);
    public static final Color GREEN    = pick(0xa6e3a1, 0x40a02b);
    public static final Color RED      = pick(0xf38ba8, 0xd20f39);
    public static final Color PEACH    = pick(0xfab387, 0xfe640b);
    public static final Color BLUE     = pick(0x89b4fa, 0x1e66f5);
    public static final Color MAUVE    = pick(0xcba6f7, 0x8839ef);
    public static final Color YELLOW   = pick(0xf9e2af, 0xdf8e1d);
    /** Text color that reads on top of the accent colors above. */
    public static final Color ON_ACCENT = pick(0x11111b, 0xeff1f5);

    // row tints: accent blended over the base, readable in both flavors
    public static final Color OK_TINT = blend(GREEN, BASE, 0.16f);
    public static final Color KO_TINT = blend(RED,   BASE, 0.14f);

    public static Color blend(Color fg, Color bg, float a) {
        return new Color(
            Math.round(fg.getRed()   * a + bg.getRed()   * (1 - a)),
            Math.round(fg.getGreen() * a + bg.getGreen() * (1 - a)),
            Math.round(fg.getBlue()  * a + bg.getBlue()  * (1 - a)));
    }

    private static Color hover(Color c) {
        return LATTE ? blend(Color.BLACK, c, 0.10f) : blend(Color.WHITE, c, 0.14f);
    }

    // ── typography — a missing family maps silently to proportional Dialog ──
    private static final String SANS = firstInstalled("Segoe UI", Font.SANS_SERIF);
    private static final String MONO = firstInstalled("Consolas", Font.MONOSPACED);

    private static String firstInstalled(String preferred, String logical) {
        for (String f : GraphicsEnvironment.getLocalGraphicsEnvironment()
                                           .getAvailableFontFamilyNames()) {
            if (f.equalsIgnoreCase(preferred)) return preferred;
        }
        return logical;
    }

    public static final Font F_UI       = new Font(SANS, Font.PLAIN, 12);
    public static final Font F_BOLD     = new Font(SANS, Font.BOLD, 12);
    public static final Font F_TITLE    = new Font(SANS, Font.BOLD, 14);
    public static final Font F_BANNER   = new Font(SANS, Font.BOLD, 13);
    public static final Font F_SMALL    = new Font(SANS, Font.PLAIN, 11);
    public static final Font F_MONO     = new Font(MONO, Font.PLAIN, 12);
    public static final Font F_MONO_BIG = new Font(MONO, Font.BOLD, 20);

    // the line station is read from a metre away: scan fields and the counter
    public static final Font F_SCAN     = new Font(MONO, Font.BOLD, 17);
    public static final Font F_COUNTER  = new Font(SANS, Font.BOLD, 44);

    public static final int ICON = 13;

    // ── rounded borders ───────────────────────────────────────────────────
    public static final class RoundBorder extends AbstractBorder {
        private final Color color;
        private final int arc;
        private final Insets pad;

        public RoundBorder(Color color, int arc, Insets pad) {
            this.color = color;
            this.arc = arc;
            this.pad = pad;
        }

        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, w - 1, h - 1, arc, arc);
            g2.dispose();
        }

        @Override public Insets getBorderInsets(Component c) { return pad; }
        @Override public Insets getBorderInsets(Component c, Insets i) { return pad; }
    }

    public static Border fieldBorder(Color line) {
        return new RoundBorder(line, 10, new Insets(3, 8, 3, 8));
    }

    /** A panel painted as a rounded card on SURFACE0 — the content plate. */
    public static JPanel card() {
        JPanel p = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(SURFACE0);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        p.setOpaque(false);
        return p;
    }

    // ── buttons ───────────────────────────────────────────────────────────
    private static final class RoundButton extends JButton {
        private final Color bg;
        private final boolean ghost;
        private boolean over = false;

        RoundButton(String text, Color bg, boolean ghost) {
            super(text);
            this.bg = bg;
            this.ghost = ghost;
            setFocusable(false);   // never in the scanner's TAB chain
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setForeground(ghost ? TEXT : ON_ACCENT);
            setFont(F_BOLD);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
            // hover color set here, never inside paintComponent
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) {
                    over = true;
                    if (RoundButton.this.ghost) setForeground(MAUVE);
                    repaint();
                }
                @Override public void mouseExited(MouseEvent e) {
                    over = false;
                    if (RoundButton.this.ghost) setForeground(TEXT);
                    repaint();
                }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            if (ghost) {
                Color line = !isEnabled() ? blend(SURFACE2, BASE, 0.45f)
                           : over         ? MAUVE
                           :                SURFACE2;
                g2.setColor(line);
                g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);
            } else {
                Color fill = !isEnabled() ? blend(bg, BASE, 0.40f)
                           : over        ? hover(bg)
                           :               bg;
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, w, h, 12, 12);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static JButton button(String text, Color bg) {
        return new RoundButton(text, bg, false);
    }

    public static JButton ghost(String text) {
        return new RoundButton(text, BASE, true);
    }

    // ── fields & spinners ─────────────────────────────────────────────────
    public static JTextField field() {
        final JTextField t = new JTextField();
        t.setFont(F_MONO);
        t.setBackground(MANTLE);
        t.setForeground(TEXT);
        t.setCaretColor(MAUVE);
        t.setSelectionColor(blend(MAUVE, BASE, 0.35f));
        t.setSelectedTextColor(TEXT);
        t.setBorder(fieldBorder(SURFACE1));
        t.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { t.setBorder(fieldBorder(MAUVE)); }
            @Override public void focusLost(FocusEvent e)   { t.setBorder(fieldBorder(SURFACE1)); }
        });
        return t;
    }

    public static JTextField fieldQr() {
        return field();
    }

    // clamped: SpinnerNumberModel throws on out-of-range, corrupt settings must not kill startup
    public static JSpinner spinnerInt(int value, int min, int max) {
        int v = Math.max(min, Math.min(max, value));
        JSpinner sp = new JSpinner(new SpinnerNumberModel(v, min, max, 1));
        styleSpinner(sp);
        return sp;
    }

    public static JSpinner spinnerDouble(double value, double min, double max, double step) {
        double v = Math.max(min, Math.min(max, value));
        JSpinner sp = new JSpinner(new SpinnerNumberModel(v, min, max, step));
        styleSpinner(sp);
        return sp;
    }

    private static void styleSpinner(JSpinner sp) {
        sp.setFont(F_MONO);
        sp.setBorder(BorderFactory.createLineBorder(SURFACE1));
        sp.setBackground(MANTLE);
        JComponent ed = sp.getEditor();
        if (ed instanceof JSpinner.DefaultEditor) {
            JFormattedTextField tf = ((JSpinner.DefaultEditor) ed).getTextField();
            tf.setFont(F_MONO);
            tf.setBackground(MANTLE);
            tf.setForeground(TEXT);
            tf.setCaretColor(MAUVE);
            tf.setColumns(4);
            tf.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 4));
            // buttons are focusable(false): without this a typed value never reaches the model
            if (tf.getFormatter() instanceof DefaultFormatter) {
                ((DefaultFormatter) tf.getFormatter()).setCommitsOnValidEdit(true);
            }
        }
        // the system LAF paints the little arrows light: repaint them dark
        for (Component c : sp.getComponents()) {
            if (c instanceof JButton) {
                JButton b = (JButton) c;
                b.setBackground(SURFACE0);
                b.setForeground(SUBTEXT);
                b.setBorder(BorderFactory.createEmptyBorder());
                b.setFocusable(false);
            }
        }
    }

    /** Dark combo box: the system LAF would paint it white. */
    public static JComboBox<String> combo(String[] items) {
        JComboBox<String> cb = new JComboBox<>(items);
        cb.setFont(F_UI);
        cb.setFocusable(false);
        cb.setBackground(MANTLE);
        cb.setForeground(TEXT);
        cb.setBorder(BorderFactory.createLineBorder(SURFACE1));
        cb.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object v,
                    int idx, boolean sel, boolean foc) {
                Component c = super.getListCellRendererComponent(list, v, idx, sel, foc);
                c.setBackground(sel ? SURFACE1 : MANTLE);
                c.setForeground(TEXT);
                setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                setFont(F_UI);
                return c;
            }
        });
        return cb;
    }

    /** Slim dark scrollbars — the default ones scream "1998" on a dark panel. */
    public static void styleScroll(JScrollPane sp) {
        sp.getVerticalScrollBar().setUI(new DarkScrollBarUI());
        sp.getHorizontalScrollBar().setUI(new DarkScrollBarUI());
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(9, 0));
        sp.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 9));
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.setBackground(BASE);
    }

    private static final class DarkScrollBarUI extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors() {
            thumbColor = SURFACE2;
            trackColor = MANTLE;
        }
        @Override protected JButton createDecreaseButton(int o) { return zeroButton(); }
        @Override protected JButton createIncreaseButton(int o) { return zeroButton(); }
        private JButton zeroButton() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(MANTLE);
            g.fillRect(r.x, r.y, r.width, r.height);
        }
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isThumbRollover() ? MAUVE : SURFACE2);
            g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 6, 6);
            g2.dispose();
        }
    }

    // ── labels & structure ────────────────────────────────────────────────
    public static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(F_BOLD);
        l.setForeground(SUBTEXT);
        return l;
    }

    public static JLabel hint(String text) {
        JLabel l = new JLabel(text);
        l.setFont(F_SMALL);
        l.setForeground(OVERLAY);
        return l;
    }

    /** Uppercase eyebrow + hairline: encodes a real group, not decoration. */
    public static JComponent section(String title) {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setOpaque(false);
        JLabel l = new JLabel(title.toUpperCase());
        l.setFont(F_SMALL.deriveFont(Font.BOLD));
        l.setForeground(OVERLAY);
        JComponent line = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(SURFACE1);
                g.fillRect(0, getHeight() / 2, getWidth(), 1);
            }
        };
        p.add(l, BorderLayout.WEST);
        p.add(line, BorderLayout.CENTER);
        p.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
        return p;
    }

    public static GridBagConstraints gbc() {
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(2, 3, 2, 3);
        g.weightx = 1.0;
        return g;
    }

    /** Full-color state banner: the app's "line light". */
    public static JLabel banner() {
        JLabel b = new JLabel("", SwingConstants.CENTER);
        b.setOpaque(true);
        b.setForeground(ON_ACCENT);
        b.setFont(F_BANNER);
        b.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        return b;
    }

    /** Full-width primary action: the machine's lever, impossible to miss. */
    public static JButton primary(String text, Color bg, Icon icon) {
        JButton b = button(text, bg);
        b.setFont(F_BANNER);
        b.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        if (icon != null) {
            b.setIcon(icon);
            b.setIconTextGap(8);
        }
        return b;
    }

    /** Small secondary action: present, but clearly not the lever. */
    public static JButton secondary(String text, Icon icon) {
        JButton b = ghost(text);
        b.setFont(F_SMALL.deriveFont(Font.BOLD));
        b.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        if (icon != null) {
            b.setIcon(icon);
            b.setIconTextGap(text.isEmpty() ? 0 : 6);
        }
        return b;
    }

    /** Icon-only square button. */
    public static JButton iconButton(Icon icon, String tip) {
        JButton b = ghost("");
        b.setIcon(icon);
        b.setToolTipText(tip);
        b.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        return b;
    }

    /** Uppercase zone caption inside a card. */
    public static JLabel zone(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(F_SMALL.deriveFont(Font.BOLD));
        l.setForeground(OVERLAY);
        return l;
    }

    // ── progress / status bar ─────────────────────────────────────────────

    /** Thin flat line — the Windows LAF ignores setForeground on stock bars. */
    public static JProgressBar thinLine(int height) {
        JProgressBar p = new JProgressBar(0, 100);
        p.setBorderPainted(false);
        p.setPreferredSize(new Dimension(10, height));
        p.setForeground(GREEN);
        p.setBackground(MANTLE);
        p.setUI(new BasicProgressBarUI() {
            @Override protected void paintDeterminate(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                int w = c.getWidth(), h = c.getHeight();
                g2.setColor(c.getBackground());
                g2.fillRect(0, 0, w, h);
                int fw = (int) (w * progressBar.getPercentComplete());
                if (fw > 0) {
                    g2.setColor(c.getForeground());
                    g2.fillRect(0, 0, fw, h);
                }
                g2.dispose();
            }
            @Override protected void paintIndeterminate(Graphics g, JComponent c) {
                paintDeterminate(g, c);
            }
        });
        return p;
    }

    /** Fit a string into the given pixel budget keeping its TAIL: the last
     *  characters are the ones that discriminate (serial endings, lot endings),
     *  so an overflowing text becomes "...<tail>", never "<head>...". */
    public static String fitTail(String s, FontMetrics fm, int maxW) {
        if (s == null || fm.stringWidth(s) <= maxW) return s;
        for (int i = 1; i < s.length(); i++) {
            String cut = "..." + s.substring(i);
            if (fm.stringWidth(cut) <= maxW) return cut;
        }
        return "..." + s.substring(s.length() - 1);
    }

    public static JProgressBar progressStatus() {
        JProgressBar p = new JProgressBar(0, 100) {
            // every message is hoverable in full, whatever the paint had to cut
            @Override public void setString(String s) {
                super.setString(s);
                setToolTipText(s == null || s.isEmpty() ? null : s);
            }
        };
        p.setStringPainted(true);
        p.setBorderPainted(false);
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(10, 22));
        p.setUI(new BasicProgressBarUI() {
            @Override protected void paintDeterminate(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                int w = c.getWidth(), h = c.getHeight();
                g2.setColor(MANTLE);
                g2.fillRoundRect(0, 0, w, h, 10, 10);
                int fw = (int) (w * progressBar.getPercentComplete());
                if (fw > 0) {
                    g2.setColor(blend(GREEN, MANTLE, 0.38f));
                    g2.fillRoundRect(0, 0, fw, h, 10, 10);
                }
                g2.setColor(SURFACE1);
                g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);
                String s = progressBar.getString();
                if (s != null) {
                    g2.setFont(F_MONO);
                    g2.setColor(TEXT);
                    FontMetrics fm = g2.getFontMetrics();
                    // centering a too-long string pushes x NEGATIVE and cuts
                    // BOTH ends — fit it first, tail wins (the digits that count)
                    String fit = fitTail(s, fm, w - 12);
                    g2.drawString(fit, Math.max(6, (w - fm.stringWidth(fit)) / 2),
                                  (h + fm.getAscent() - fm.getDescent()) / 2);
                }
                g2.dispose();
            }
            @Override protected void paintIndeterminate(Graphics g, JComponent c) {
                paintDeterminate(g, c);
            }
        });
        return p;
    }

    // ── flat tabs ─────────────────────────────────────────────────────────
    public static void flatTabs(final JTabbedPane tabs) {
        tabs.setFont(F_BOLD);
        tabs.setOpaque(false);
        tabs.setBorder(BorderFactory.createEmptyBorder());
        tabs.setUI(new BasicTabbedPaneUI() {
            @Override protected void installDefaults() {
                super.installDefaults();
                tabAreaInsets = new Insets(6, 8, 0, 8);
                contentBorderInsets = new Insets(4, 0, 0, 0);
                tabInsets = new Insets(6, 12, 7, 12);
                selectedTabPadInsets = new Insets(0, 0, 0, 0);
            }
            @Override protected void paintTabBackground(Graphics g, int placement,
                    int idx, int x, int y, int w, int h, boolean selected) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                if (selected) {
                    g2.setColor(SURFACE0);
                    g2.fillRoundRect(x, y + 1, w, h + 8, 12, 12);
                    g2.setColor(MAUVE);
                    g2.fillRect(x + 10, y + h - 2, w - 20, 2);
                }
                g2.dispose();
            }
            @Override protected void paintTabBorder(Graphics g, int p, int i,
                    int x, int y, int w, int h, boolean s) {}
            @Override protected void paintContentBorder(Graphics g, int p, int i) {}
            @Override protected void paintFocusIndicator(Graphics g, int p,
                    Rectangle[] r, int i, Rectangle ic, Rectangle tr, boolean s) {}
            @Override protected int calculateTabHeight(int p, int i, int fh) {
                return fh + 12;
            }
        });
        tabs.addChangeListener(e -> retintTabs(tabs));
        retintTabs(tabs);
    }

    private static void retintTabs(JTabbedPane tabs) {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            tabs.setForegroundAt(i, i == tabs.getSelectedIndex() ? TEXT : SUBTEXT);
        }
    }
}
