package app.ui;

import app.config.Settings;
import app.core.Day;
import app.core.Ledger;
import app.core.Ranges;
import app.core.ScanGuard;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * INTERVALLO: the first label, the lot, the quantity — and N rows go into the
 * table in ONE journal event, all or none. Nothing moves until "Aggiungi":
 * the preview and the overlap check are live, the action is explicit.
 */
public final class RangePanel extends JPanel {

    private final Settings cfg = Settings.getInstance();
    private final Ledger ledger;

    final JTextField tfFirst = Widgets.scanField();
    final JTextField tfLot = Widgets.scanField();
    final JSpinner spCount = AppTheme.spinnerInt(10, 1, 9999);
    private final Preview preview = new Preview();
    private final JLabel digitsHint = AppTheme.hint(" ");
    final Widgets.ResultBox box = new Widgets.ResultBox();
    final JButton btnAdd;
    final JButton btnUndo;
    private boolean inverted;

    public RangePanel(Ledger ledger) {
        this.ledger = ledger;
        setOpaque(false);
        setLayout(new GridBagLayout());
        GridBagConstraints g = AppTheme.gbc();
        g.insets = new Insets(4, 2, 4, 2);
        int y = 0;

        g.gridx = 0; g.gridy = y; g.weightx = 0;
        add(Widgets.caption("Primo", "etichetta"), g);
        g.gridx = 1; g.weightx = 1;
        add(tfFirst, g);

        y++; g.gridx = 0; g.gridy = y; g.weightx = 0;
        add(Widgets.caption("Lotto", null), g);
        g.gridx = 1; g.weightx = 1;
        add(tfLot, g);

        y++; g.gridx = 0; g.gridy = y; g.weightx = 0;
        add(Widgets.caption("Quantità", null), g);
        g.gridx = 1; g.weightx = 1;
        JFormattedTextField ed = ((JSpinner.DefaultEditor) spCount.getEditor()).getTextField();
        ed.setFont(AppTheme.F_SCAN);
        add(spCount, g);

        g.gridx = 0; g.gridwidth = 2;
        y++; g.gridy = y;
        g.insets = new Insets(8, 2, 0, 2);
        add(preview, g);
        y++; g.gridy = y;
        g.insets = new Insets(0, 2, 4, 2);
        digitsHint.setHorizontalAlignment(SwingConstants.CENTER);
        add(digitsHint, g);
        g.insets = new Insets(4, 2, 4, 2);

        y++; g.gridy = y;
        add(box, g);

        y++; g.gridy = y;
        btnAdd = AppTheme.primary("Aggiungi righe", AppTheme.GREEN, Icons.check(AppTheme.ICON, AppTheme.ON_ACCENT));
        add(btnAdd, g);

        y++; g.gridy = y;
        btnUndo = AppTheme.secondary("Annulla ultima (Ctrl+Z)", Icons.undo(AppTheme.ICON, AppTheme.SUBTEXT));
        btnUndo.setToolTipText("Annulla l'ultima coppia o l'ultimo intervallo  (Ctrl+Z)");
        add(btnUndo, g);

        y++; g.gridy = y; g.weighty = 1;
        JPanel fill = new JPanel();
        fill.setOpaque(false);
        add(fill, g);

        wire();
        update();
    }

    private void wire() {
        DocumentListener live = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { update(); }
            @Override public void removeUpdate(DocumentEvent e)  { update(); }
            @Override public void changedUpdate(DocumentEvent e) { update(); }
        };
        tfFirst.getDocument().addDocumentListener(live);
        tfLot.getDocument().addDocumentListener(live);
        spCount.addChangeListener(e -> update());
        // the lot stays on screen for the next range: a lot scanned again must
        // replace it, not be glued onto it (32 rows went out with the lot twice)
        selectAllOnFocus(tfLot);
        selectAllOnFocus(tfFirst);
        // the scanner sends ENTER after the first label: on to the lot
        tfFirst.addActionListener(e -> {
            if (!tfFirst.getText().trim().isEmpty()) tfLot.requestFocusInWindow();
        });
        tfLot.addActionListener(e -> {
            if (!tfLot.getText().trim().isEmpty()) {
                ((JSpinner.DefaultEditor) spCount.getEditor()).getTextField().requestFocusInWindow();
            }
        });
        btnAdd.addActionListener(e -> { add(); tfFirst.requestFocusInWindow(); });
        btnUndo.addActionListener(e -> { undo(); tfFirst.requestFocusInWindow(); });
        // ENTER in the quantity confirms the range. A key binding, not an action
        // listener: the formatted field's own ENTER commits the number and never
        // reaches the listener with a real key press.
        JFormattedTextField ed = ((JSpinner.DefaultEditor) spCount.getEditor()).getTextField();
        // and the number typed there replaces the one shown ("2" after "10" was 102)
        selectAllOnFocus(ed);
        ed.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "addRange");
        ed.getActionMap().put("addRange", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { add(); }
        });

        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "swapRange");
        getActionMap().put("swapRange", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                String a = tfFirst.getText();
                tfFirst.setText(tfLot.getText());
                tfLot.setText(a);
            }
        });
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearRange");
        getActionMap().put("clearRange", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { clearFields(); }
        });
    }

    /** A field reached by the scanner's ENTER is overwritten by what comes next, never appended to. */
    static void selectAllOnFocus(final JTextField f) {
        f.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                // NOW for a scan field: the scanner's keystrokes are already queued
                // and are handed over right after this event. A formatted field
                // (the quantity, typed by hand) resets its text after its
                // listeners, so it selects once more, later.
                f.selectAll();
                if (f instanceof JFormattedTextField) SwingUtilities.invokeLater(f::selectAll);
            }
        });
    }

    int digits() {
        int d = cfg.getInt(Settings.TAIL_DIGITS, 3);
        return d < 1 || d > 9 ? 3 : d;
    }

    /** The value on screen IS the value used: the edit is committed first. */
    int count() {
        try { spCount.commitEdit(); } catch (java.text.ParseException ignored) {}
        return ((Number) spCount.getValue()).intValue();
    }

    /** Live: preview, overlap, button label. Changes nothing on disk. */
    void update() {
        int n = ((Number) spCount.getValue()).intValue();
        Ranges r = Ranges.of(tfFirst.getText(), n, digits());
        digitsHint.setText("cifre mobili: " + digits() + " (da Impostazioni)");
        btnAdd.setText("Aggiungi " + n + (n == 1 ? " riga" : " righe"));
        inverted = false;
        if (!r.ok()) {
            preview.set(null, null, 0);
            if (r.problem == Ranges.Problem.EMPTY) box.neutral("INTERVALLO", r.message(n));
            else box.show(AppTheme.RED,
                r.problem == Ranges.Problem.OVERFLOW ? "L'INTERVALLO SFORA" : "PRIMO CODICE NON VALIDO",
                r.message(n));
            btnAdd.setEnabled(false);
            return;
        }
        List<String> labels = r.labels();
        preview.set(labels.get(0), labels.get(labels.size() - 1), r.digits);
        String lot = tfLot.getText().trim();
        if (!lot.isEmpty()) {
            int advice = ScanGuard.inspect(labels.get(0), lot,
                cfg.get(Settings.QR1_PATTERN, ""), cfg.get(Settings.QR2_PATTERN, ""));
            if (advice == ScanGuard.INVERTED) {
                inverted = true;
                box.show(AppTheme.YELLOW, "CODICI INVERTITI",
                         "il lotto è nel campo Primo: premi F2 per scambiarli");
                btnAdd.setEnabled(false);
                return;
            }
            if (advice == ScanGuard.MISMATCH) {
                box.show(AppTheme.RED, "FORMATO NON RICONOSCIUTO",
                         "etichetta o lotto non hanno la forma attesa");
                btnAdd.setEnabled(false);
                return;
            }
        }
        List<String> dups = overlaps(labels);
        if (!dups.isEmpty()) {
            box.show(AppTheme.RED, "SOVRAPPOSIZIONE",
                     dups.size() + " già in tabella: " + String.join(", ", tails(dups)));
            btnAdd.setEnabled(false);
            return;
        }
        box.show(AppTheme.GREEN, "NESSUNA SOVRAPPOSIZIONE",
                 "nessuno dei " + labels.size() + " codici è già in tabella");
        btnAdd.setEnabled(!lot.isEmpty() && ledger.canScan());
    }

    private List<String> overlaps(List<String> labels) {
        List<String> out = new ArrayList<>();
        Day d = ledger.day();
        if (d == null || d.isClosed()) return out;
        for (String l : labels) {
            if (d.duplicateOf(l) != 0) out.add(l);
        }
        return out;
    }

    private static List<String> tails(List<String> labels) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < labels.size() && i < 6; i++) {
            String l = labels.get(i);
            out.add("..." + l.substring(Math.max(0, l.length() - 6)));
        }
        if (labels.size() > 6) out.add("...");
        return out;
    }

    /** "Aggiungi N righe": one INTERVALLO event, all rows or none. */
    void add() {
        int n = count();
        update();
        if (!btnAdd.isEnabled()) {
            Sounds.error();
            return;
        }
        Ranges r = Ranges.of(tfFirst.getText(), n, digits());
        Day.Result res = ledger.addRange(r.labels(), tfLot.getText());
        if (res.ok()) {
            int n0 = Integer.parseInt(res.event.field(0));
            tfFirst.setText("");
            update();
            box.show(AppTheme.GREEN, n + (n == 1 ? " RIGA AGGIUNTA" : " RIGHE AGGIUNTE"),
                     "#" + n0 + " - #" + (n0 + n - 1) + "  -  lotto " + tfLot.getText().trim());
            Sounds.ok();
            tfFirst.requestFocusInWindow();
        } else {
            box.show(AppTheme.RED, res.kind == Day.Result.Kind.IO_ERROR ? "DISCO NON SCRIVE"
                                   : "INTERVALLO RIFIUTATO", res.message);
            Sounds.error();
        }
    }

    void undo() {
        Day d = ledger.day();
        int[] last = d == null ? null : d.lastAddition();
        Day.Result r = ledger.undoLast();
        if (r.ok() && last != null) {
            box.show(AppTheme.PEACH, last[1] > 1 ? "INTERVALLO ANNULLATO" : "COPPIA ANNULLATA",
                     last[1] + (last[1] > 1 ? " righe" : " riga") + " da #" + last[0] + " eliminate");
            Sounds.ok();
        } else {
            box.show(AppTheme.SURFACE2, "NIENTE DA ANNULLARE", r.message);
            Sounds.error();
        }
    }

    void clearFields() {
        tfFirst.setText("");
        tfLot.setText("");
        update();
        tfFirst.requestFocusInWindow();
    }

    boolean isInverted() {
        return inverted;
    }

    /**
     * Typographic preview: the shared prefix dimmed, the moving digits lit,
     * then the last label collapsed to its tail. Fitted, never cut at the tail.
     */
    static final class Preview extends JComponent {
        private String first, last;
        private int digits;

        Preview() {
            setFont(AppTheme.F_MONO.deriveFont(13f));
            setOpaque(false);
        }

        void set(String first, String last, int digits) {
            this.first = first;
            this.last = last;
            this.digits = digits;
            setToolTipText(first == null ? null : first + " -> " + last);
            repaint();
        }

        String text() {
            if (first == null) return "";
            String tail = last.substring(Math.max(0, last.length() - digits - 4));
            return first + " -> ..." + tail;
        }

        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(200, fm.getHeight() + 14);
        }

        @Override public Dimension getMinimumSize() {
            return new Dimension(80, getPreferredSize().height);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(AppTheme.MANTLE);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            if (first != null) {
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int w = getWidth() - 16;
                String full = text();
                // the whole line must fit: if not, the head of the FIRST code goes
                String shown = AppTheme.fitTail(full, fm, w);
                boolean cutHead = !shown.equals(full);
                int x = Math.max(8, (getWidth() - fm.stringWidth(shown)) / 2);
                int yb = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                // colour by position in the full string
                int lit1From = first.length() - digits;
                int lit2From = full.length() - digits;
                for (int i = 0; i < shown.length(); i++) {
                    int pos = !cutHead ? i : i < 3 ? -1 : i + full.length() - shown.length();
                    char c = shown.charAt(i);
                    boolean lit = pos >= 0 && ((pos >= lit1From && pos < first.length()) || pos >= lit2From);
                    g2.setColor(lit ? AppTheme.MAUVE : AppTheme.OVERLAY);
                    if (lit) g2.setFont(getFont().deriveFont(Font.BOLD));
                    else g2.setFont(getFont());
                    String ch = String.valueOf(c);
                    g2.drawString(ch, x, yb);
                    x += g2.getFontMetrics().stringWidth(ch);
                }
            }
            g2.dispose();
        }
    }
}
