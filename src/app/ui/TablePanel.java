package app.ui;

import app.core.Day;
import app.core.Ledger;
import app.core.Row;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The right half: the day's table, newest on top. Enter and Canc act only
 * while the TABLE has the focus — never while the operator is scanning — and
 * every action hands the focus back to QR 1.
 */
public final class TablePanel extends JPanel {

    /** What the table asks the window to do with a row. */
    public interface Actions {
        void edit(Row r);
        void delete(Row r);
        void restore(Row r);
        void focusScan();
    }

    private static final int SPINE = 4;
    private static final DateTimeFormatter DAY_FMT =
        DateTimeFormatter.ofPattern("d MMMM", Locale.ITALIAN);

    private final Ledger ledger;
    private final RowTableModel model;
    private final Actions actions;
    private final JTable table;
    private final JLabel title = new JLabel();
    private final Pill[] pills = new Pill[RowTableModel.Filter.values().length];
    private final JButton btnEdit, btnDelete, btnRestore;
    private final JLabel hint = AppTheme.hint(" ");

    public TablePanel(Ledger ledger, Actions actions) {
        this.ledger = ledger;
        this.actions = actions;
        this.model = new RowTableModel(ledger);
        setLayout(new BorderLayout());
        setOpaque(false);

        JPanel card = AppTheme.card();
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        // head: title, filter pills, search
        JPanel head = new JPanel(new BorderLayout(0, 6));
        head.setOpaque(false);
        JPanel titleRow = new JPanel(new BorderLayout(10, 0));
        titleRow.setOpaque(false);
        title.setFont(AppTheme.F_TITLE.deriveFont(16f));
        title.setForeground(AppTheme.TEXT);
        titleRow.add(title, BorderLayout.WEST);
        JPanel pillRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        pillRow.setOpaque(false);
        String[] names = { "Tutte", "Modificate", "Eliminate", "Esportate", "Corrette" };
        for (RowTableModel.Filter f : RowTableModel.Filter.values()) {
            Pill p = new Pill(names[f.ordinal()]);
            p.addActionListener(e -> {
                model.setFilter(f);
                refreshHead();
            });
            pills[f.ordinal()] = p;
            pillRow.add(p);
        }
        titleRow.add(pillRow, BorderLayout.CENTER);
        head.add(titleRow, BorderLayout.NORTH);
        head.add(buildSearch(), BorderLayout.SOUTH);
        card.add(head, BorderLayout.NORTH);

        table = new JTable(model);
        card.add(buildTable(), BorderLayout.CENTER);

        // foot: row actions
        JPanel foot = new JPanel(new BorderLayout(10, 0));
        foot.setOpaque(false);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        btns.setOpaque(false);
        btnEdit = AppTheme.secondary("Modifica", Icons.pencil(AppTheme.ICON, AppTheme.SUBTEXT));
        btnDelete = AppTheme.secondary("Elimina", Icons.cross(AppTheme.ICON, AppTheme.RED));
        btnRestore = AppTheme.secondary("Ripristina", Icons.restore(AppTheme.ICON, AppTheme.SUBTEXT));
        btnEdit.addActionListener(e -> act(0));
        btnDelete.addActionListener(e -> act(1));
        btnRestore.addActionListener(e -> act(2));
        btns.add(btnEdit);
        btns.add(btnDelete);
        btns.add(btnRestore);
        foot.add(btns, BorderLayout.WEST);
        foot.add(hint, BorderLayout.CENTER);
        card.add(foot, BorderLayout.SOUTH);

        add(card, BorderLayout.CENTER);
        ledger.addListener(this::refresh);
        refresh();
    }

    public RowTableModel model() {
        return model;
    }

    public JTable table() {
        return table;
    }

    /** Re-reads the day, keeping the selected row selected. */
    public void refresh() {
        Row sel = selected();
        model.refresh();
        if (sel != null) select(sel.n);
        refreshHead();
        refreshButtons();
    }

    /** Selects row n (clearing a filter or search that hides it) and shows it. */
    public void showRow(int n) {
        int i = model.indexOf(n);
        if (i < 0) {
            model.setFilter(RowTableModel.Filter.ALL);
            searchField.setText("");
            model.setSearch("");
            refreshHead();
            i = model.indexOf(n);
        }
        if (i >= 0) {
            table.getSelectionModel().setSelectionInterval(i, i);
            table.scrollRectToVisible(table.getCellRect(i, 0, true));
        }
        refreshButtons();
    }

    private void select(int n) {
        int i = model.indexOf(n);
        if (i >= 0) table.getSelectionModel().setSelectionInterval(i, i);
    }

    public Row selected() {
        int v = table.getSelectedRow();
        return v < 0 ? null : model.rowAt(v);
    }

    private void refreshHead() {
        Day d = ledger.day();
        LocalDate today = LocalDate.now(ledger.clock());
        String date = d == null ? today.toString() : d.date();
        String when;
        try {
            LocalDate ld = LocalDate.parse(date);
            when = (ld.equals(today) ? "Oggi, " : "Giornata del ") + ld.format(DAY_FMT);
        } catch (RuntimeException e) {
            when = "Giornata " + date;
        }
        if (d != null && d.isCorrupted()) when += " (sola lettura)";
        title.setText(when);
        for (RowTableModel.Filter f : RowTableModel.Filter.values()) {
            Pill p = pills[f.ordinal()];
            p.setText(p.base + " " + model.count(f));
            p.setOn(model.filter() == f);
        }
    }

    /**
     * A refusal, in the line under the table: never a modal window, because a
     * window would take the next scan — its ENTER closes it, QR 1 is lost and
     * QR 2 lands in QR 1. Stays until the selection changes.
     */
    void problem(String text) {
        hint.setText(text);
        hint.setToolTipText(text);
        hint.setForeground(AppTheme.RED);
    }

    private void refreshButtons() {
        Row r = selected();
        hint.setForeground(AppTheme.OVERLAY);
        boolean writable = ledger.day() != null && !ledger.day().isReadOnly();
        btnEdit.setEnabled(writable && r != null && !r.deleted());
        btnDelete.setEnabled(writable && r != null && !r.deleted() && !r.everExported());
        btnRestore.setEnabled(writable && r != null && r.deleted());
        if (r == null) {
            hint.setText("seleziona una riga  -  doppio click = modifica");
            btnDelete.setToolTipText("Elimina la riga selezionata");
        } else if (r.everExported()) {
            hint.setText("riga " + r.n + " " + Day.ON_PORTAL.substring(0, Day.ON_PORTAL.indexOf(':'))
                         + ": si corregge solo il lotto");
            btnDelete.setToolTipText("Non si elimina: " + Day.ON_PORTAL);
        } else {
            hint.setText("riga " + r.n + " selezionata  -  doppio click = modifica");
            btnDelete.setToolTipText("Elimina la riga selezionata");
        }
        hint.setToolTipText(hint.getText());
    }

    private void act(int which) {
        Row r = selected();
        if (r == null) { actions.focusScan(); return; }
        if (which == 0 && btnEdit.isEnabled()) actions.edit(r);
        else if (which == 1 && btnDelete.isEnabled()) actions.delete(r);
        else if (which == 2 && btnRestore.isEnabled()) actions.restore(r);
        else Sounds.error();
        actions.focusScan();
    }

    // ── search ───────────────────────────────────────────────────────────

    private JTextField searchField;

    private JComponent buildSearch() {
        searchField = new HintField("cerca etichetta o lotto");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                model.setSearch(searchField.getText());
                refreshHead();
                refreshButtons();
            }
            @Override public void insertUpdate(DocumentEvent e)  { changed(); }
            @Override public void removeUpdate(DocumentEvent e)  { changed(); }
            @Override public void changedUpdate(DocumentEvent e) { changed(); }
        });
        // Esc in the search goes back to scanning
        searchField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "back");
        searchField.getActionMap().put("back", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { actions.focusScan(); }
        });
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        JLabel icon = new JLabel(Icons.search(AppTheme.ICON + 1, AppTheme.OVERLAY));
        row.add(icon, BorderLayout.WEST);
        row.add(searchField, BorderLayout.CENTER);
        return row;
    }

    /** A field that shows a grey hint while empty. Painted, no state changes in paint. */
    private static final class HintField extends JTextField {
        private final String hintText;

        HintField(String hint) {
            this.hintText = hint;
            Widgets.styleField(this, AppTheme.F_UI);
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (!getText().isEmpty() || isFocusOwner()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(AppTheme.OVERLAY);
            g2.setFont(AppTheme.F_UI);
            Insets in = getInsets();
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(hintText, in.left, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    // ── table ────────────────────────────────────────────────────────────

    private JScrollPane buildTable() {
        table.setRowHeight(26);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setBackground(AppTheme.BASE);
        table.setForeground(AppTheme.TEXT);
        table.setSelectionBackground(AppTheme.SURFACE1);
        table.setSelectionForeground(AppTheme.TEXT);
        table.setFont(AppTheme.F_UI);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JTableHeader h = table.getTableHeader();
        h.setFont(AppTheme.F_SMALL.deriveFont(Font.BOLD));
        h.setBackground(AppTheme.MANTLE);
        h.setForeground(AppTheme.OVERLAY);
        h.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.SURFACE1));
        h.setReorderingAllowed(false);

        // measured: the fixed columns get exactly what their longest value needs
        FontMetrics mono = table.getFontMetrics(AppTheme.F_MONO);
        FontMetrics ui = table.getFontMetrics(AppTheme.F_UI);
        FontMetrics bold = table.getFontMetrics(AppTheme.F_BOLD);
        fixWidth(0, ui.stringWidth("99999") + 22);
        fixWidth(3, mono.stringWidth("00:00:00") + 18);
        fixWidth(4, ui.stringWidth("int. 999") + 16);
        fixWidth(5, bold.stringWidth("MODIFICATA") + AppTheme.ICON + 24);
        table.getColumnModel().getColumn(1).setPreferredWidth(250);
        table.getColumnModel().getColumn(2).setPreferredWidth(190);
        h.setDefaultRenderer(new HeaderRenderer());
        table.setDefaultRenderer(Object.class, new RowRenderer());

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) refreshButtons();
        });
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.rowAtPoint(e.getPoint()) >= 0) act(0);
            }
        });

        // the table never takes the focus: a row clicked to look at it would
        // otherwise receive the next scan, and the scanner's ENTER opened the
        // edit dialog on that row while the codes rewrote it. Selection is the
        // mouse's; the caret stays where the next piece will be scanned.
        table.setFocusable(false);

        JScrollPane sc = new JScrollPane(table);
        sc.setPreferredSize(new Dimension(560, 200));
        sc.setBorder(BorderFactory.createEmptyBorder());
        sc.getViewport().setBackground(AppTheme.BASE);
        AppTheme.styleScroll(sc);
        return sc;
    }

    private void fixWidth(int col, int w) {
        javax.swing.table.TableColumn c = table.getColumnModel().getColumn(col);
        c.setMinWidth(w);
        c.setMaxWidth(w);
        c.setPreferredWidth(w);
    }

    /** Flat header: uppercase captions on MANTLE, no system bevels. */
    private static final class HeaderRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable t, Object v,
                boolean sel, boolean foc, int row, int col) {
            super.getTableCellRendererComponent(t, v == null ? "" : v.toString().toUpperCase(Locale.ROOT),
                                                false, false, row, col);
            setFont(AppTheme.F_SMALL.deriveFont(Font.BOLD));
            setForeground(AppTheme.OVERLAY);
            setBackground(AppTheme.MANTLE);
            setOpaque(true);
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.SURFACE1),
                BorderFactory.createEmptyBorder(5, col == 0 ? 10 : 6, 5, 6)));
            return this;
        }
    }

    static Color spineOf(String status) {
        switch (status) {
            case RowTableModel.NUOVA:      return AppTheme.GREEN;
            case RowTableModel.MODIFICATA: return AppTheme.PEACH;
            case RowTableModel.CORRETTA:   return AppTheme.PEACH;
            case RowTableModel.ESPORTATA:  return AppTheme.BLUE;
            case RowTableModel.ELIMINATA:  return AppTheme.SURFACE1;
            default:                       return null;
        }
    }

    static Color tintOf(String status) {
        switch (status) {
            case RowTableModel.NUOVA:      return AppTheme.OK_TINT;
            case RowTableModel.MODIFICATA: return AppTheme.blend(AppTheme.PEACH, AppTheme.BASE, 0.12f);
            case RowTableModel.CORRETTA:   return AppTheme.blend(AppTheme.PEACH, AppTheme.BASE, 0.20f);
            default:                       return AppTheme.BASE;
        }
    }

    static Color statusColor(String status) {
        switch (status) {
            case RowTableModel.NUOVA:      return AppTheme.GREEN;
            case RowTableModel.OK:         return AppTheme.GREEN;
            case RowTableModel.MODIFICATA: return AppTheme.PEACH;
            case RowTableModel.CORRETTA:   return AppTheme.PEACH;
            case RowTableModel.ESPORTATA:  return AppTheme.BLUE;
            default:                       return AppTheme.OVERLAY;
        }
    }

    /** Spine + tint tell the state at a glance; long codes keep their tail. */
    private final class RowRenderer extends DefaultTableCellRenderer {
        private boolean strike;

        @Override public Component getTableCellRendererComponent(JTable t, Object v,
                boolean sel, boolean foc, int row, int col) {
            super.getTableCellRendererComponent(t, v, sel, false, row, col);
            Row r = model.rowAt(row);
            String status = r == null ? "" : model.status(r);
            boolean deleted = RowTableModel.ELIMINATA.equals(status);
            strike = deleted && col < 5;
            setIcon(null);
            setHorizontalTextPosition(SwingConstants.RIGHT);
            if (!sel) setBackground(tintOf(status));
            setForeground(deleted ? AppTheme.OVERLAY : col == 0 ? AppTheme.OVERLAY : AppTheme.TEXT);
            setFont(col == 1 || col == 2 || col == 3 ? AppTheme.F_MONO : AppTheme.F_UI);

            String text = v == null ? "" : String.valueOf(v);
            setToolTipText(col == 1 || col == 2 ? text : null);
            if (col == 1 || col == 2) {
                int w = t.getColumnModel().getColumn(col).getWidth() - 14;
                setText(AppTheme.fitTail(text, getFontMetrics(getFont()), Math.max(20, w)));
            }

            Color spine = spineOf(status);
            if (col == 0 && spine != null) {
                setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, SPINE, 0, 0, spine),
                    BorderFactory.createEmptyBorder(0, 6, 0, 6)));
            } else {
                setBorder(BorderFactory.createEmptyBorder(0, col == 0 ? 6 + SPINE : 6, 0, 6));
            }
            if (col == 5) {
                setFont(AppTheme.F_BOLD);
                setForeground(statusColor(status));
                // touched by hand after an export: the pencil, drawn, never typed
                if (r != null && r.modified() && !RowTableModel.MODIFICATA.equals(status) && !deleted) {
                    setIcon(Icons.pencil(AppTheme.ICON, AppTheme.PEACH));
                    setHorizontalTextPosition(SwingConstants.LEFT);
                    setToolTipText("modificata a mano");
                }
            }
            return this;
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (!strike) return;
            String s = getText();
            if (s == null || s.isEmpty()) return;
            FontMetrics fm = g.getFontMetrics(getFont());
            Insets in = getInsets();
            int y = getHeight() / 2;
            g.setColor(AppTheme.OVERLAY);
            g.drawLine(in.left, y, in.left + fm.stringWidth(s), y);
        }
    }

    /** Filter pill: filled when selected. Hover lives in the listener. */
    private static final class Pill extends JButton {
        final String base;
        private boolean on;
        private boolean over;

        Pill(String base) {
            super(base);
            this.base = base;
            setFocusable(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setFont(AppTheme.F_SMALL.deriveFont(Font.BOLD));
            setForeground(AppTheme.SUBTEXT);
            setBorder(BorderFactory.createEmptyBorder(3, 9, 3, 9));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { over = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { over = false; repaint(); }
            });
        }

        void setOn(boolean b) {
            on = b;
            setForeground(b ? AppTheme.TEXT : AppTheme.SUBTEXT);
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(on ? AppTheme.SURFACE2 : over ? AppTheme.SURFACE1 : AppTheme.MANTLE);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
