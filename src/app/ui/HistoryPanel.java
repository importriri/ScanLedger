package app.ui;

import app.core.Day;
import app.core.Days;
import app.core.ExportRecord;
import app.core.Exporter;
import app.core.Journal;
import app.core.Ledger;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Storico (Impostazioni): the days, and for one day its exports. Three ways
 * nothing gets lost, reachable from here: the archive folder, the copy state,
 * and Rigenera — any export rewritten from the journal, its fingerprint proving
 * it identical. Riesporta writes the whole day again, as a new file.
 */
final class HistoryPanel extends JPanel {

    private final Ledger ledger;
    private final List<Day> days = new ArrayList<>();
    private final List<Days.Info> infos = new ArrayList<>();
    final JTable dayTable;
    final JTable exportTable;
    private final DayModel dayModel = new DayModel();
    private final ExportModel exportModel = new ExportModel();
    private final JLabel exportTitle = new JLabel("Export");
    final JButton btnRegen, btnOpen, btnReexport;
    final Widgets.ResultBox box = new Widgets.ResultBox();
    /** Test hook: where Rigenera writes, instead of asking. */
    File regenerateInto;

    HistoryPanel(Ledger ledger) {
        this.ledger = ledger;
        setOpaque(false);
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        dayTable = table(dayModel);
        exportTable = table(exportModel);
        int[] w = { 44, 70, 52, 80, 90, 180 };
        for (int i = 0; i < w.length; i++) exportTable.getColumnModel().getColumn(i).setPreferredWidth(w[i]);
        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.setOpaque(false);
        top.add(AppTheme.section("Giornate"), BorderLayout.NORTH);
        top.add(scroll(dayTable, 120), BorderLayout.CENTER);

        JPanel mid = new JPanel(new BorderLayout(0, 6));
        mid.setOpaque(false);
        exportTitle.setFont(AppTheme.F_TITLE);
        exportTitle.setForeground(AppTheme.TEXT);
        mid.add(exportTitle, BorderLayout.NORTH);
        mid.add(scroll(exportTable, 100), BorderLayout.CENTER);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        btns.setOpaque(false);
        btnRegen = AppTheme.button("Rigenera", AppTheme.MAUVE);
        btnOpen = AppTheme.secondary("Apri cartella archivio", Icons.folder(AppTheme.ICON, AppTheme.SUBTEXT));
        btnReexport = AppTheme.secondary("Riesporta giornata intera", Icons.export(AppTheme.ICON, AppTheme.SUBTEXT));
        btns.add(btnRegen);
        btns.add(btnOpen);
        btns.add(btnReexport);
        mid.add(btns, BorderLayout.SOUTH);

        JPanel center = new JPanel(new GridLayout(2, 1, 0, 8));
        center.setOpaque(false);
        center.add(top);
        center.add(mid);
        add(center, BorderLayout.CENTER);
        box.neutral("STORICO", "scegli una giornata e un export");
        add(box, BorderLayout.SOUTH);

        dayTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showDay();
        });
        exportTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) refreshButtons();
        });
        btnRegen.addActionListener(e -> regenerate());
        btnOpen.addActionListener(e -> ExportDialog.openFolder(ledger.exporter().exportDir()));
        btnReexport.addActionListener(e -> reexport());
        addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) { reload(); }
        });
        reload();
    }

    private static JTable table(AbstractTableModel m) {
        JTable t = new JTable(m);
        t.setRowHeight(24);
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.setBackground(AppTheme.BASE);
        t.setForeground(AppTheme.TEXT);
        t.setSelectionBackground(AppTheme.SURFACE1);
        t.setSelectionForeground(AppTheme.TEXT);
        t.setFont(AppTheme.F_MONO);
        t.setFillsViewportHeight(true);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.getTableHeader().setReorderingAllowed(false);
        t.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object v, boolean s,
                    boolean f, int r, int c) {
                super.getTableCellRendererComponent(tb, String.valueOf(v).toUpperCase(), false, false, r, c);
                setFont(AppTheme.F_SMALL.deriveFont(Font.BOLD));
                setForeground(AppTheme.OVERLAY);
                setBackground(AppTheme.MANTLE);
                setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                return this;
            }
        });
        t.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object v, boolean s,
                    boolean f, int r, int c) {
                super.getTableCellRendererComponent(tb, v, s, false, r, c);
                setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                String text = String.valueOf(v);
                setForeground(text.startsWith("IN SOSPESO") ? AppTheme.YELLOW
                            : text.equals("OK") ? AppTheme.GREEN
                            : text.equals("danneggiata") ? AppTheme.RED : AppTheme.TEXT);
                setFont(text.equals("OK") || text.startsWith("IN SOSPESO") ? AppTheme.F_BOLD : AppTheme.F_MONO);
                return this;
            }
        });
        return t;
    }

    private static JScrollPane scroll(JTable t, int h) {
        JScrollPane sc = new JScrollPane(t);
        sc.setPreferredSize(new Dimension(520, h));
        sc.setBorder(BorderFactory.createEmptyBorder());
        sc.getViewport().setBackground(AppTheme.BASE);
        AppTheme.styleScroll(sc);
        return sc;
    }

    /** Reads every day again (the current one from the ledger, the others from disk). */
    void reload() {
        days.clear();
        infos.clear();
        List<Days.Info> all = ledger.days().list();
        for (int i = all.size() - 1; i >= 0; i--) {
            Days.Info info = all.get(i);
            Day d = current(info);
            if (d == null) {
                try {
                    Journal.Replay r = Journal.read(info.file);
                    d = Day.replayOnly(r.events);
                    if (r.corrupted) d = null;
                } catch (IOException e) {
                    d = null;
                }
            }
            infos.add(info);
            days.add(d);
        }
        dayModel.fireTableDataChanged();
        if (!days.isEmpty() && dayTable.getSelectedRow() < 0) dayTable.setRowSelectionInterval(0, 0);
        showDay();
    }

    private Day current(Days.Info info) {
        Day d = ledger.day();
        return d != null && d.file() != null
            && d.file().getAbsoluteFile().equals(info.file.getAbsoluteFile()) ? d : null;
    }

    private Day selectedDay() {
        int r = dayTable.getSelectedRow();
        return r < 0 || r >= days.size() ? null : days.get(r);
    }

    private ExportRecord selectedExport() {
        Day d = selectedDay();
        int r = exportTable.getSelectedRow();
        return d == null || r < 0 || r >= d.exports().size() ? null : d.exports().get(r);
    }

    private void showDay() {
        Day d = selectedDay();
        exportTitle.setText(d == null ? "Export" : "Export del " + ExportDialog.dm(d.date()));
        exportModel.fireTableDataChanged();
        if (d != null && !d.exports().isEmpty()) {
            int last = d.exports().size() - 1;
            exportTable.setRowSelectionInterval(last, last);
        }
        refreshButtons();
    }

    private void refreshButtons() {
        ExportRecord x = selectedExport();
        btnRegen.setEnabled(x != null);
        btnRegen.setText(x == null ? "Rigenera" : "Rigenera il " + String.format("%02d", x.number));
        Day d = selectedDay();
        btnReexport.setEnabled(d != null && d.liveCount() > 0);
    }

    void regenerate() {
        int r = dayTable.getSelectedRow();
        ExportRecord x = selectedExport();
        if (r < 0 || x == null) return;
        File dir = regenerateInto;
        if (dir == null) {
            JFileChooser ch = new JFileChooser(ledger.exporter().exportDir());
            ch.setDialogTitle("Dove scrivere il file rigenerato");
            ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (ch.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            dir = ch.getSelectedFile();
        }
        Exporter.Regenerated g = Exporter.regenerate(infos.get(r).file, x.number, dir);
        if (g.identical) {
            box.show(AppTheme.GREEN, "RIGENERATO IDENTICO", g.message);
            Sounds.ok();
        } else {
            box.show(AppTheme.RED, "RIGENERAZIONE NON RIUSCITA", g.message);
            Sounds.error();
        }
    }

    void reexport() {
        int r = dayTable.getSelectedRow();
        if (r < 0) return;
        Days.Info info = infos.get(r);
        Day d = current(info);
        Exporter.Result res;
        if (d != null) {
            res = ledger.export(true);
        } else {
            Day other = null;
            try {
                other = Day.open(info.file, new Day.Init(ledger.station(), info.date, 0, 0), ledger.clock());
                res = ledger.exporter().reexport(other);
            } catch (IOException e) {
                box.show(AppTheme.RED, "RIESPORTAZIONE NON RIUSCITA", e.getMessage());
                return;
            } finally {
                if (other != null && other.journal() != null) other.journal().close();
            }
            ledger.retryAllCopies();
            ledger.fireChanged();
        }
        if (res.ok) {
            box.show(AppTheme.GREEN, "RIESPORTATA", res.file.getName() + "  -  " + res.lines.length
                     + " righe, tutte come nuove (riesportazione=1)");
            Sounds.ok();
        } else {
            box.show(AppTheme.RED, "RIESPORTAZIONE NON RIUSCITA", res.message);
            Sounds.error();
        }
        reload();
        dayTable.setRowSelectionInterval(r, r);
    }

    private final class DayModel extends AbstractTableModel {
        private final String[] cols = { "Giornata", "Righe", "Export", "Copia", "Stato" };
        @Override public int getRowCount()           { return days.size(); }
        @Override public int getColumnCount()        { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public Object getValueAt(int r, int c) {
            Days.Info i = infos.get(r);
            Day d = days.get(r);
            switch (c) {
                case 0: return i.file.getName().replace(Days.EXT, "");
                case 1: return d == null ? "-" : String.valueOf(d.liveCount());
                case 2: {
                    if (d == null) return "-";
                    StringBuilder sb = new StringBuilder();
                    for (ExportRecord x : d.exports()) {
                        if (sb.length() > 0) sb.append(" - ");
                        sb.append(String.format("%02d", x.number));
                    }
                    return sb.length() == 0 ? "nessuno" : sb.toString();
                }
                case 3: {
                    if (d == null) return "-";
                    int pending = Exporter.pendingCopies(d);
                    if (pending > 0) return "IN SOSPESO";
                    boolean any = false;
                    for (ExportRecord x : d.exports()) any |= "OK".equals(x.copyOutcome());
                    return any ? "OK" : "-";
                }
                default: return d == null ? "danneggiata" : i.closed ? "chiusa" : "aperta";
            }
        }
    }

    private final class ExportModel extends AbstractTableModel {
        private final String[] cols = { "N.", "Ora", "Righe", "Correzioni", "Impronta", "Copia" };
        @Override public int getRowCount() {
            Day d = selectedDay();
            return d == null ? 0 : d.exports().size();
        }
        @Override public int getColumnCount()        { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public Object getValueAt(int r, int c) {
            ExportRecord x = selectedDay().exports().get(r);
            switch (c) {
                case 0: return String.format("%02d", x.number) + (x.reexport ? " R" : "");
                case 1: return x.created.length() >= 19 ? x.created.substring(11, 19) : x.created;
                case 2: return String.valueOf(x.lines().size());
                case 3: return String.valueOf(x.corrections());
                case 4: return x.sha256.length() >= 8 ? x.sha256.substring(0, 8) + "..." : x.sha256;
                default:
                    return x.copyPending() ? "IN SOSPESO: " + x.copyReason()
                         : "OK".equals(x.copyOutcome()) ? "OK" : "-";
            }
        }
    }
}
