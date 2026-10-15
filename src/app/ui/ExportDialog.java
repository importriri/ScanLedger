package app.ui;

import app.config.Settings;
import app.core.Day;
import app.core.ExportRecord;
import app.core.Exporter;
import app.core.Ledger;
import app.core.ScanGuard;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

/**
 * Esporta (screen 06): what the file will carry, where it goes, the first and
 * last lines — then Esporta, and what happened (the copy included).
 */
final class ExportDialog extends JDialog {

    static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("dd/MM");

    private final Settings cfg = Settings.getInstance();
    private final Ledger ledger;
    private final boolean reexport;
    private final JPanel body = new JPanel(new BorderLayout(0, 10));
    final JButton btnExport;
    final JButton btnCancel;
    Exporter.Result result;

    ExportDialog(Window owner, Ledger ledger, boolean reexport) {
        super(owner, reexport ? "Riesporta giornata" : "Esporta CSV", ModalityType.APPLICATION_MODAL);
        this.ledger = ledger;
        this.reexport = reexport;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        Day day = ledger.day();
        Exporter x = ledger.exporter();
        Exporter.Plan plan = day == null ? null : x.plan(day, reexport || day.isCorrupted());

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(AppTheme.SURFACE0);
        root.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(AppTheme.MAUVE),
            BorderFactory.createEmptyBorder(14, 18, 14, 18)));

        JPanel head = new JPanel(new GridLayout(0, 1, 0, 2));
        head.setOpaque(false);
        JLabel t = new JLabel((reexport ? "Riesporta la giornata del " : "Esporta la giornata del ")
                              + (day == null ? "-" : dm(day.date())));
        t.setFont(AppTheme.F_TITLE.deriveFont(17f));
        t.setForeground(AppTheme.TEXT);
        head.add(t);
        if (day != null && plan != null) {
            ExportRecord first = day.exports().isEmpty() ? null : day.exports().get(0);
            head.add(AppTheme.hint("postazione " + day.station() + "  -  export "
                + String.format("%02d", plan.number)
                + (first == null ? "  -  primo export della giornata"
                                 : "  -  il primo è uscito alle " + first.created.substring(11, 16))
                + (plan.reexport ? "  -  RIESPORTAZIONE (tutte le righe vive, come nuove)" : "")));
        }
        root.add(head, BorderLayout.NORTH);

        body.setOpaque(false);
        root.add(body, BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btns.setOpaque(false);
        btnCancel = AppTheme.ghost("Annulla");
        btnExport = AppTheme.button(reexport ? "Riesporta" : "Esporta", AppTheme.MAUVE);
        btns.add(btnCancel);
        btns.add(btnExport);
        root.add(btns, BorderLayout.SOUTH);
        btnCancel.addActionListener(e -> dispose());
        btnExport.addActionListener(e -> doExport());

        if (plan == null || plan.isEmpty()) {
            body.add(message(AppTheme.SURFACE2, "NIENTE DA ESPORTARE",
                     "nessuna riga nuova e nessuna correzione dall'ultimo export"), BorderLayout.NORTH);
            btnExport.setEnabled(false);
            btnCancel.setText("Chiudi");
        } else {
            body.add(summary(plan, day), BorderLayout.NORTH);
            JTextArea pv = new JTextArea(plan.preview);
            pv.setEditable(false);
            pv.setFocusable(false);
            pv.setFont(AppTheme.F_MONO.deriveFont(11f));
            pv.setBackground(AppTheme.MANTLE);
            pv.setForeground(AppTheme.SUBTEXT);
            // the file as it will be written: one screen row per file line, no
            // wrapping (it pushed #fine and its fingerprint out of the box);
            // a long line scrolls sideways instead
            pv.setLineWrap(false);
            pv.setRows(Math.max(1, plan.preview.split("\n").length));
            pv.setColumns(70);
            pv.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            JScrollPane sp = new JScrollPane(pv, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                                             ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            sp.setBorder(BorderFactory.createEmptyBorder());
            sp.getViewport().setBackground(AppTheme.MANTLE);
            AppTheme.styleScroll(sp);
            body.add(sp, BorderLayout.CENTER);
        }

        setContentPane(root);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    static String dm(String isoDate) {
        try { return LocalDate.parse(isoDate).format(DM); }
        catch (RuntimeException e) { return isoDate; }
    }

    private JPanel summary(Exporter.Plan plan, Day day) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        GridBagConstraints g = AppTheme.gbc();
        g.insets = new Insets(2, 0, 2, 0);
        int y = 0;
        int dups = 0, offShape = 0;
        Set<String> seen = new HashSet<>();
        String p1 = cfg.get(Settings.QR1_PATTERN, ""), p2 = cfg.get(Settings.QR2_PATTERN, "");
        for (ExportRecord.Line l : plan.lines) {
            if (!seen.add(l.label)) dups++;
            if (ScanGuard.inspect(l.label, l.lot, p1, p2) != ScanGuard.OK) offShape++;
        }
        y = stat(p, g, y, "Coppie nuove", String.valueOf(plan.newRows), AppTheme.TEXT);
        y = stat(p, g, y, "Correzioni di righe già esportate", String.valueOf(plan.corrections),
                 plan.corrections > 0 ? AppTheme.PEACH : AppTheme.OVERLAY);
        y = stat(p, g, y, "Modificate a mano (fra le nuove)", String.valueOf(plan.modifiedNew),
                 plan.modifiedNew > 0 ? AppTheme.PEACH : AppTheme.OVERLAY);
        y = stat(p, g, y, "Eliminate (escluse)", String.valueOf(plan.deleted), AppTheme.OVERLAY);
        y = stat(p, g, y, "Doppioni  -  codici fuori forma", dups + "  -  " + offShape,
                 dups + offShape > 0 ? AppTheme.RED : AppTheme.GREEN);

        File exp = ledger.exporter().exportDir();
        y = pathRow(p, g, y, "File", new File(exp, plan.fileName).getPath(), null);
        File copy = cfg.copyDir();
        String note = copy == null ? "(nessuna cartella di copia)"
                    : copy.isDirectory() ? "(presente)" : "(assente: la copia resterà in sospeso)";
        pathRow(p, g, y, "Copia", copy == null ? "-" : copy.getPath(), note);
        return p;
    }

    private static int stat(JPanel p, GridBagConstraints g, int y, String label, String value, Color c) {
        g.gridy = y; g.gridx = 0; g.weightx = 1;
        JLabel l = new JLabel(label);
        l.setFont(AppTheme.F_UI.deriveFont(13f));
        l.setForeground(AppTheme.TEXT);
        p.add(l, g);
        g.gridx = 1; g.weightx = 0;
        JLabel v = new JLabel(value, SwingConstants.RIGHT);
        v.setFont(AppTheme.F_MONO.deriveFont(Font.BOLD, 13f));
        v.setForeground(c);
        p.add(v, g);
        return y + 1;
    }

    private static int pathRow(JPanel p, GridBagConstraints g, int y, String label, String path, String note) {
        g.gridy = y; g.gridx = 0; g.gridwidth = 2; g.weightx = 1;
        g.insets = new Insets(y == 5 ? 10 : 2, 0, 2, 0);
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        JLabel l = AppTheme.label(label);
        l.setPreferredSize(new Dimension(52, 20));
        row.add(l, BorderLayout.WEST);
        JLabel v = new JLabel(path + (note == null ? "" : "  " + note));
        v.setFont(AppTheme.F_MONO);
        v.setForeground(AppTheme.TEXT);
        v.setToolTipText(v.getText());
        row.add(v, BorderLayout.CENTER);
        p.add(row, g);
        g.gridwidth = 1;
        g.insets = new Insets(2, 0, 2, 0);
        return y + 1;
    }

    private static Widgets.ResultBox message(Color tone, String head, String text) {
        Widgets.ResultBox b = new Widgets.ResultBox() {
            @Override public Dimension getPreferredSize() {
                return new Dimension(560, super.getPreferredSize().height);
            }
        };
        b.show(tone, head, text);
        return b;
    }

    void doExport() {
        btnExport.setEnabled(false);
        result = ledger.export(reexport || (ledger.day() != null && ledger.day().isCorrupted()));
        body.removeAll();
        if (result.ok) {
            Sounds.ok();
            body.add(message(AppTheme.GREEN, reexport ? "RIESPORTATO" : "ESPORTATO",
                             result.file.getPath()), BorderLayout.NORTH);
            String copy = "OK".equals(result.copy) ? "copia fatta in " + cfg.copyDir()
                        : "FALLITA".equals(result.copy) ? "COPIA IN SOSPESO: " + result.copyReason
                          + "  -  riprovo a ogni avvio e a ogni export"
                        : "nessuna cartella di copia configurata";
            JLabel c = new JLabel(copy);
            c.setFont(AppTheme.F_BOLD);
            c.setForeground("FALLITA".equals(result.copy) ? AppTheme.PEACH : AppTheme.SUBTEXT);
            body.add(c, BorderLayout.CENTER);
            JButton open = AppTheme.secondary("Apri cartella", Icons.folder(AppTheme.ICON, AppTheme.SUBTEXT));
            open.addActionListener(e -> openFolder(result.file.getParentFile()));
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            row.setOpaque(false);
            row.add(open);
            body.add(row, BorderLayout.SOUTH);
        } else {
            Sounds.error();
            body.add(message(AppTheme.RED, "EXPORT NON RIUSCITO", result.message), BorderLayout.NORTH);
        }
        btnCancel.setText("Chiudi");
        body.revalidate();
        body.repaint();
        pack();
    }

    /** Desktop.open with a silent fallback: no desktop, no folder window, no exception. */
    static void openFolder(File dir) {
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(dir);
        } catch (Exception | LinkageError ignored) {
            // nothing to open with here
        }
    }
}
