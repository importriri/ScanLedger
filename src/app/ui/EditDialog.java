package app.ui;

import app.config.Settings;
import app.core.Day;
import app.core.Ledger;
import app.core.Row;
import app.core.ScanGuard;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * Edits one row with the same rules as the Day. Never exported: label and lot.
 * Exported: the lot only — the label is on the portal already, and the dialog
 * says so instead of pretending. The new values go through the same guards as
 * a scan, live; the reason is optional and lands in the journal.
 */
final class EditDialog extends JDialog {

    private final Settings cfg = Settings.getInstance();
    private final Ledger ledger;
    private final Row row;
    private final boolean exported;

    final JTextField tfLabel = Widgets.scanField();
    final JTextField tfLot = Widgets.scanField();
    final JTextField tfReason = new JTextField();
    private final Strike wasLabel = new Strike();
    private final Strike wasLot = new Strike();
    private final JLabel check = new JLabel(" ");
    final JButton btnSave;
    private boolean saved;

    EditDialog(Window owner, Ledger ledger, Row row) {
        super(owner, "Modifica riga #" + row.n, ModalityType.APPLICATION_MODAL);
        this.ledger = ledger;
        this.row = row;
        this.exported = row.everExported();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(AppTheme.SURFACE0);
        root.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(AppTheme.MAUVE),
            BorderFactory.createEmptyBorder(14, 18, 14, 18)));
        GridBagConstraints g = AppTheme.gbc();
        g.insets = new Insets(3, 3, 3, 3);
        int y = 0;

        JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        head.setOpaque(false);
        JLabel title = new JLabel("Modifica riga #" + row.n);
        title.setFont(AppTheme.F_TITLE.deriveFont(17f));
        title.setForeground(AppTheme.TEXT);
        head.add(title);
        if (exported) {
            JLabel badge = new JLabel("ESPORTATA in " + String.format("%02d", row.lastExport()));
            badge.setOpaque(true);
            badge.setBackground(AppTheme.SURFACE2);
            badge.setForeground(AppTheme.TEXT);
            badge.setFont(AppTheme.F_SMALL.deriveFont(Font.BOLD));
            badge.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            head.add(badge);
        }
        g.gridx = 0; g.gridy = y++; g.gridwidth = 2;
        add(root, head, g);
        g.gridy = y++;
        add(root, AppTheme.hint(exported
            ? "già sul portale  -  si corregge solo il lotto"
            : "scansionata alle " + RowTableModel.time(row.created) + "  -  modo "
              + (row.mode == Row.Mode.SCAN ? "scan" : "intervallo")), g);

        g.gridwidth = 1;
        g.gridy = y; g.gridx = 0; g.weightx = 0;
        add(root, AppTheme.label("Etichetta"), g);
        g.gridx = 1; g.weightx = 1;
        tfLabel.setText(row.label());
        tfLabel.setColumns(30);
        tfLabel.setEnabled(!exported);
        if (exported) tfLabel.setDisabledTextColor(AppTheme.OVERLAY);
        add(root, tfLabel, g);
        y++; g.gridy = y;
        add(root, exported ? AppTheme.hint("l'etichetta non si cambia: è già registrata") : wasLabel, g);

        y++; g.gridy = y; g.gridx = 0; g.weightx = 0;
        add(root, AppTheme.label("Lotto"), g);
        g.gridx = 1; g.weightx = 1;
        tfLot.setText(row.lot());
        add(root, tfLot, g);
        y++; g.gridy = y;
        add(root, wasLot, g);
        y++; g.gridy = y;
        check.setFont(AppTheme.F_BOLD);
        add(root, check, g);

        y++; g.gridy = y; g.gridx = 0; g.weightx = 0;
        add(root, Widgets.caption("Motivo", "facoltativo"), g);
        g.gridx = 1; g.weightx = 1;
        Widgets.styleField(tfReason, AppTheme.F_UI);
        add(root, tfReason, g);

        y++; g.gridy = y; g.gridx = 0; g.gridwidth = 2;
        g.insets = new Insets(10, 3, 0, 3);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btns.setOpaque(false);
        JButton cancel = AppTheme.ghost("Annulla (Esc)");
        btnSave = AppTheme.button(exported ? "Salva correzione (Invio)" : "Salva modifica (Invio)", AppTheme.MAUVE);
        btns.add(cancel);
        btns.add(btnSave);
        add(root, btns, g);

        cancel.addActionListener(e -> dispose());
        btnSave.addActionListener(e -> save());
        // ENTER: from the label to the lot (a scanner sends it), from the lot or the reason: save
        tfLabel.addActionListener(e -> tfLot.requestFocusInWindow());
        tfLot.addActionListener(e -> save());
        tfReason.addActionListener(e -> save());
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        root.getActionMap().put("cancel", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { dispose(); }
        });
        DocumentListener live = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { validateLive(); }
            @Override public void removeUpdate(DocumentEvent e)  { validateLive(); }
            @Override public void changedUpdate(DocumentEvent e) { validateLive(); }
        };
        tfLabel.getDocument().addDocumentListener(live);
        tfLot.getDocument().addDocumentListener(live);

        setContentPane(root);
        validateLive();
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(() -> {
            JTextField first = exported ? tfLot : tfLabel;
            first.requestFocusInWindow();
            first.selectAll();
        });
    }

    private static void add(JPanel p, Component c, GridBagConstraints g) {
        p.add(c, g);
    }

    boolean wasSaved() {
        return saved;
    }

    /** Same guards as the scan, live: form, duplicate, and what the save will mean. */
    String validateLive() {
        String label = tfLabel.getText().trim();
        String lot = tfLot.getText().trim();
        wasLabel.set(label.equals(row.label()) ? null : "era: ", row.label());
        if (exported) {
            wasLot.set(lot.equals(row.exportedLot()) ? null : "esportato con: ", row.exportedLot());
        } else {
            wasLot.set(lot.equals(row.lot()) ? null : "era: ", row.lot());
        }
        String problem = null;
        if (label.isEmpty() || lot.isEmpty()) problem = "etichetta e lotto sono obbligatori";
        else if (Day.hasControlChars(label) || Day.hasControlChars(lot)) problem = "TAB o a capo dentro un codice";
        else {
            int advice = ScanGuard.inspect(label, lot,
                cfg.get(Settings.QR1_PATTERN, ""), cfg.get(Settings.QR2_PATTERN, ""));
            if (advice == ScanGuard.INVERTED) problem = "etichetta e lotto sembrano invertiti";
            else if (advice == ScanGuard.MISMATCH) problem = "forma non riconosciuta";
            else {
                Day d = ledger.day();
                Row dup = d == null ? null : d.findLive(label);
                if (dup != null && dup.n != row.n) problem = "doppione: è già la riga #" + dup.n;
            }
        }
        String text;
        if (problem != null) {
            text = problem;
            check.setForeground(AppTheme.RED);
            check.setIcon(Icons.cross(AppTheme.ICON, AppTheme.RED));
        } else {
            if (exported) {
                text = lot.equals(row.exportedLot())
                    ? "torna al lotto esportato: nessuna correzione in uscita"
                    : "uscirà nel prossimo export come CORREZIONE";
            } else {
                text = "forma valida  -  nessun doppione";
            }
            check.setForeground(AppTheme.GREEN);
            check.setIcon(Icons.check(AppTheme.ICON, AppTheme.GREEN));
        }
        check.setText(text);
        btnSave.setEnabled(problem == null);
        return problem;
    }

    void save() {
        if (validateLive() != null) {
            Sounds.error();
            return;
        }
        Day.Result r = ledger.modify(row.n, tfLabel.getText(), tfLot.getText(), tfReason.getText());
        if (r.ok() || r.kind == Day.Result.Kind.UNCHANGED) {
            saved = r.ok();
            if (saved) Sounds.ok();
            dispose();
            return;
        }
        check.setForeground(AppTheme.RED);
        check.setIcon(Icons.cross(AppTheme.ICON, AppTheme.RED));
        check.setText(r.message);
        Sounds.error();
    }

    /** "era: <old value struck through>" — the old value, drawn with a line. */
    static final class Strike extends JComponent {
        private String prefix;
        private String value;

        Strike() {
            setFont(AppTheme.F_MONO);
        }

        @Override public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        void set(String prefix, String value) {
            this.prefix = prefix;
            this.value = value;
            setToolTipText(prefix == null ? null : value);
            repaint();
        }

        @Override public Dimension getPreferredSize() {
            return new Dimension(120, getFontMetrics(getFont()).getHeight() + 2);
        }

        @Override protected void paintComponent(Graphics g) {
            if (prefix == null || value == null) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int yb = fm.getAscent();
            g2.setColor(AppTheme.OVERLAY);
            g2.drawString(prefix, 2, yb);
            int x = 2 + fm.stringWidth(prefix);
            String v = AppTheme.fitTail(value, fm, Math.max(20, getWidth() - x - 4));
            g2.setColor(AppTheme.RED);
            g2.drawString(v, x, yb);
            int mid = yb - fm.getAscent() / 3;
            g2.drawLine(x, mid, x + fm.stringWidth(v), mid);
            g2.dispose();
        }
    }
}
