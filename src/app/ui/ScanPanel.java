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
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;

/**
 * SCANSIONE: two QR per piece, the scanner behaves exactly as in AutoFillSuite.
 * ENTER on QR 1 moves to QR 2; ENTER or TAB on QR 2 accepts the pair. A pair
 * reaches the table only after the journal forced it to disk. On a refusal the
 * fields stay full (so the operator sees what came in), the error sounds, and
 * QR 1 is selected so the next scan overwrites it.
 */
public final class ScanPanel extends JPanel {

    /** What the panel asks of the window. */
    public interface Host {
        void showRow(int n);
    }

    private enum Pending { NONE, INVERTED, DUPLICATE, OTHER }

    private final Settings cfg = Settings.getInstance();
    private final Ledger ledger;
    private final Host host;

    final JTextField tfQr1 = Widgets.scanField();
    final JTextField tfQr2 = Widgets.scanField();
    final Widgets.ResultBox box = new Widgets.ResultBox();
    private final JLabel counter = new JLabel("0", SwingConstants.CENTER);
    private final JButton btnCtxA = AppTheme.secondary("Svuota campi (Esc)", null);
    private final JButton btnCtxB = AppTheme.secondary("Vai alla riga", null);
    private final JPanel ctxRow = new JPanel(new GridLayout(1, 2, 6, 0));
    final JButton btnUndo;
    final JButton btnSwap;

    private Pending pending = Pending.NONE;
    private int pendingRow;
    /** after a refusal the next scan overwrites QR 1: QR 2 must start empty too */
    private boolean overwriteNext;
    private boolean accepting;

    public ScanPanel(Ledger ledger, Host host) {
        this.ledger = ledger;
        this.host = host;
        setOpaque(false);
        setLayout(new GridBagLayout());
        GridBagConstraints g = AppTheme.gbc();
        g.insets = new Insets(4, 2, 4, 2);
        int y = 0;

        ScanTabSupport.markSubmitOnTab(tfQr2);

        g.gridx = 0; g.gridy = y; g.weightx = 0;
        add(Widgets.caption("QR 1", "etichetta"), g);
        g.gridx = 1; g.weightx = 1;
        add(tfQr1, g);

        y++; g.gridx = 0; g.gridy = y; g.weightx = 0;
        add(Widgets.caption("QR 2", "lotto"), g);
        g.gridx = 1; g.weightx = 1;
        add(tfQr2, g);

        g.gridx = 0; g.gridwidth = 2;
        y++; g.gridy = y;
        g.insets = new Insets(8, 2, 4, 2);
        add(box, g);
        g.insets = new Insets(4, 2, 4, 2);

        y++; g.gridy = y;
        ctxRow.setOpaque(false);
        ctxRow.add(btnCtxA);
        ctxRow.add(btnCtxB);
        ctxRow.setVisible(false);
        add(ctxRow, g);

        y++; g.gridy = y;
        JPanel count = new JPanel(new BorderLayout());
        count.setOpaque(false);
        counter.setFont(AppTheme.F_COUNTER);
        counter.setForeground(AppTheme.TEXT);
        JLabel cap = new JLabel("COPPIE OGGI", SwingConstants.CENTER);
        cap.setFont(AppTheme.F_SMALL.deriveFont(Font.BOLD));
        cap.setForeground(AppTheme.OVERLAY);
        count.add(counter, BorderLayout.CENTER);
        count.add(cap, BorderLayout.SOUTH);
        add(count, g);

        y++; g.gridy = y;
        JPanel sec = new JPanel(new GridLayout(1, 2, 6, 0));
        sec.setOpaque(false);
        btnUndo = AppTheme.secondary("Annulla ultima (Ctrl+Z)", Icons.undo(AppTheme.ICON, AppTheme.SUBTEXT));
        btnSwap = AppTheme.secondary("Scambia (F2)", Icons.swap(AppTheme.ICON, AppTheme.SUBTEXT));
        btnSwap.setToolTipText("Scambia QR 1 e QR 2  (F2)");
        btnUndo.setToolTipText("Annulla l'ultima coppia o l'ultimo intervallo  (Ctrl+Z)");
        sec.add(btnUndo);
        sec.add(btnSwap);
        add(sec, g);

        y++; g.gridy = y;
        JLabel help = AppTheme.hint("spara QR 1  -  suono acuto = ok  -  due suoni bassi = errore");
        help.setHorizontalAlignment(SwingConstants.CENTER);
        add(help, g);

        // filler: everything sits at the top
        y++; g.gridy = y; g.weighty = 1;
        JPanel fill = new JPanel();
        fill.setOpaque(false);
        add(fill, g);

        wire();
        idle();
    }

    private void wire() {
        tfQr1.addActionListener(e -> {
            if (!tfQr1.getText().trim().isEmpty()) tfQr2.requestFocusInWindow();
        });
        tfQr2.addActionListener(e -> accept());
        tfQr2.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) {
                // fallback for a TAB-suffix scanner whose TAB the dispatcher missed:
                // only a move INSIDE this card counts (not the table, not another window)
                if (e.isTemporary()) return;
                Component next = e.getOppositeComponent();
                if (next == null || !SwingUtilities.isDescendingFrom(next, ScanPanel.this)) return;
                if (!tfQr1.getText().trim().isEmpty() && !tfQr2.getText().trim().isEmpty()) accept();
            }
        });
        tfQr1.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) {
                if (!overwriteNext) return;
                overwriteNext = false;
                // a new piece after a refusal: the old QR 2 must not glue onto it.
                // NOW, not later: a fast scanner has the QR 2 keystrokes already
                // queued, and a deferred clear would wipe the new QR 2 instead
                tfQr2.setText("");
            }
            @Override public void removeUpdate(DocumentEvent e)  {}
            @Override public void changedUpdate(DocumentEvent e) {}
        });

        btnSwap.addActionListener(e -> { swap(); focusQr1(); });
        btnUndo.addActionListener(e -> { undo(); focusQr1(); });
        btnCtxA.addActionListener(e -> ctx(true));
        btnCtxB.addActionListener(e -> ctx(false));

        // F2 from anywhere in the card — the scanner never sends it
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "swapQr");
        getActionMap().put("swapQr", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { swap(); }
        });
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearQr");
        getActionMap().put("clearQr", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { clearFields(); }
        });
    }

    // ── the scan ─────────────────────────────────────────────────────────

    /** Accepts the pair in the fields: guards first, journal second, table last. */
    void accept() {
        if (accepting) return;
        String q1 = tfQr1.getText().trim();
        String q2 = tfQr2.getText().trim();
        if (q1.isEmpty() || q2.isEmpty()) return;
        accepting = true;
        try {
            if (Day.hasControlChars(q1) || Day.hasControlChars(q2)) {
                refuse(Pending.OTHER, AppTheme.RED, "CODICE NON VALIDO", "TAB o a capo dentro un codice");
                return;
            }
            if (!ledger.canScan()) {
                refuse(Pending.OTHER, AppTheme.RED, blockedTitle(), blockedDetail());
                return;
            }
            int advice = ScanGuard.inspect(q1, q2,
                cfg.get(Settings.QR1_PATTERN, ""), cfg.get(Settings.QR2_PATTERN, ""));
            if (advice == ScanGuard.INVERTED) {
                if (!cfg.getBool(Settings.AUTO_SWAP, false)) {
                    refuse(Pending.INVERTED, AppTheme.YELLOW, "CODICI INVERTITI",
                           "premi F2: scambia e registra");
                    return;
                }
                String t = q1; q1 = q2; q2 = t;
            } else if (advice == ScanGuard.MISMATCH) {
                refuse(Pending.OTHER, AppTheme.RED, "FORMATO NON RICONOSCIUTO",
                       "controlla i due QR: nessuno dei due ha la forma attesa");
                return;
            }
            Day d = ledger.day();
            int dup = d == null || d.isClosed() ? 0 : d.duplicateOf(q1);
            if (dup != 0) {
                duplicate(q1, dup);
                return;
            }
            Day.Result r = ledger.add(q1, q2);
            switch (r.kind) {
                case OK: {
                    int n = Integer.parseInt(r.event.field(0));
                    pending = Pending.NONE;
                    ctxRow.setVisible(false);
                    box.show(AppTheme.GREEN, "COPPIA REGISTRATA  -  #" + n, q1 + "  -  " + q2);
                    tfQr1.setText("");
                    tfQr2.setText("");
                    overwriteNext = false;
                    refreshCounter();
                    Sounds.ok();
                    focusQr1();
                    break;
                }
                case DUPLICATE:
                    duplicate(q1, r.existingN);
                    break;
                case IO_ERROR:
                    refuse(Pending.OTHER, AppTheme.RED, "DISCO NON SCRIVE", r.message);
                    break;
                default:
                    refuse(Pending.OTHER, AppTheme.RED, "COPPIA RIFIUTATA", r.message);
            }
        } finally {
            accepting = false;
        }
    }

    private void duplicate(String label, int n) {
        Day d = ledger.day();
        Row dup = d == null ? null : d.row(n);
        pendingRow = n;
        if (dup == null) {
            // a row of an earlier day of the same date: not in this table
            refuse(Pending.OTHER, AppTheme.RED, "GIÀ REGISTRATA OGGI",
                   label + " è la riga #" + n + " della giornata chiusa prima");
            return;
        }
        refuse(Pending.DUPLICATE, AppTheme.RED, "GIÀ REGISTRATA OGGI",
               label + " è la riga #" + n + " (" + RowTableModel.time(dup.created) + ")");
    }

    private String blockedTitle() {
        if (!app.core.StationRule.valid(ledger.station())) return "MANCA LA POSTAZIONE";
        if (!ledger.diskOk()) return "DISCO NON SCRIVE";
        return "GIORNATA IN SOLA LETTURA";
    }

    private String blockedDetail() {
        if (!app.core.StationRule.valid(ledger.station())) return "imposta il nome della postazione nelle Impostazioni";
        if (!ledger.diskOk()) return "riprovo da solo ogni 2 secondi: " + ledger.diskError();
        return "il giornale è danneggiato: esporta le righe leggibili";
    }

    private void refuse(Pending kind, Color tone, String head, String detail) {
        pending = kind;
        box.show(tone, head, detail);
        switch (kind) {
            case INVERTED:
                context("Scambia e registra (F2)", Icons.swap(AppTheme.ICON, AppTheme.SUBTEXT), "Svuota (Esc)");
                break;
            case DUPLICATE:
                context("Svuota campi (Esc)", Icons.cross(AppTheme.ICON, AppTheme.SUBTEXT),
                        "Vai alla riga " + pendingRow);
                break;
            default:
                ctxRow.setVisible(false);
        }
        Sounds.error();
        overwriteNext = true;
        tfQr1.requestFocusInWindow();
        tfQr1.selectAll();
    }

    private void context(String a, Icon icon, String b) {
        btnCtxA.setText(a);
        btnCtxA.setIcon(icon);
        btnCtxB.setText(b);
        ctxRow.setVisible(true);
        revalidate();
    }

    private void ctx(boolean first) {
        if (pending == Pending.INVERTED) {
            if (first) swap();
            else clearFields();
        } else if (pending == Pending.DUPLICATE) {
            if (first) clearFields();
            else {
                host.showRow(pendingRow);
                focusQr1();
            }
        }
    }

    /** F2: trades the two fields; after an inversion it also records the pair. */
    void swap() {
        String a = tfQr1.getText();
        tfQr1.setText(tfQr2.getText());
        tfQr2.setText(a);
        overwriteNext = false;
        if (pending == Pending.INVERTED && !tfQr1.getText().trim().isEmpty()
                && !tfQr2.getText().trim().isEmpty()) {
            accept();
            return;
        }
        if (tfQr1.getText().trim().isEmpty()) tfQr1.requestFocusInWindow();
        else {
            tfQr2.requestFocusInWindow();
            tfQr2.setCaretPosition(tfQr2.getText().length());
        }
    }

    void clearFields() {
        tfQr1.setText("");
        tfQr2.setText("");
        overwriteNext = false;
        pending = Pending.NONE;
        ctxRow.setVisible(false);
        idle();
        focusQr1();
    }

    /** Ctrl+Z: the last pair, or the whole last range. */
    void undo() {
        Day d = ledger.day();
        int[] last = d == null ? null : d.lastAddition();
        Day.Result r = ledger.undoLast();
        if (r.ok()) {
            int n0 = last == null ? 0 : last[0];
            int q = last == null ? 0 : last[1];
            box.show(AppTheme.PEACH, q > 1 ? "INTERVALLO ANNULLATO" : "COPPIA ANNULLATA  -  #" + n0,
                     q > 1 ? q + " righe da #" + n0 + " eliminate (si ripristinano)"
                           : "riga eliminata: si ripristina dalla tabella");
            pending = Pending.NONE;
            ctxRow.setVisible(false);
            Sounds.ok();
        } else {
            box.show(r.kind == Day.Result.Kind.UNCHANGED ? AppTheme.SURFACE2 : AppTheme.RED,
                     "NIENTE DA ANNULLARE", r.message);
            Sounds.error();
        }
        refreshCounter();
    }

    void focusQr1() {
        tfQr1.requestFocusInWindow();
    }

    void refreshCounter() {
        Day d = ledger.day();
        counter.setText(String.valueOf(d == null || d.isClosed() ? 0 : d.liveCount()));
    }

    /** The resting state of the box. */
    void idle() {
        refreshCounter();
        if (!ledger.canScan()) {
            box.show(AppTheme.RED, blockedTitle(), blockedDetail());
            return;
        }
        Day d = ledger.day();
        int last = d == null ? 0 : d.lastAddedN();
        if (last > 0) {
            Row r = d.row(last);
            box.neutral("ULTIMA  -  #" + last, r.label() + "  -  " + r.lot());
        } else {
            box.neutral("SPARA QR 1", "poi QR 2: la coppia va su disco e in tabella");
        }
    }

    /** Called when the ledger changes from outside (disk back, day closed...). */
    void ledgerChanged() {
        refreshCounter();
        if (pending == Pending.NONE && !accepting) idle();
    }
}
