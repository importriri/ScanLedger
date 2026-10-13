package app.ui;

import app.core.Day;
import app.core.Ledger;
import app.core.Row;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The day's rows as the operator reads them: newest first, filtered and
 * searched. It owns no data — every refresh reads the Day again, so the table
 * can never show something the journal does not hold.
 */
public final class RowTableModel extends AbstractTableModel {

    public enum Filter { ALL, MODIFIED, DELETED, EXPORTED, CORRECTED }

    static final String[] COLS = { "#", "Etichetta", "Lotto", "Ora", "Modo", "Stato" };

    public static final String NUOVA = "NUOVA";
    public static final String OK = "OK";
    public static final String MODIFICATA = "MODIFICATA";
    public static final String ELIMINATA = "ELIMINATA";
    public static final String ESPORTATA = "ESPORTATA";
    public static final String CORRETTA = "CORRETTA";

    private final Ledger ledger;
    private final List<Row> view = new ArrayList<>();
    private Filter filter = Filter.ALL;
    private String search = "";
    private int[] last;           // the last addition: its live rows read NUOVA
    private final int[] counts = new int[Filter.values().length];

    public RowTableModel(Ledger ledger) {
        this.ledger = ledger;
        refresh();
    }

    public void setFilter(Filter f) {
        filter = f;
        refresh();
    }

    public Filter filter() {
        return filter;
    }

    public void setSearch(String s) {
        search = s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
        refresh();
    }

    public int count(Filter f) {
        return counts[f.ordinal()];
    }

    /** Re-reads the day. */
    public void refresh() {
        view.clear();
        java.util.Arrays.fill(counts, 0);
        Day d = ledger.day();
        last = d == null ? null : d.lastAddition();
        if (d != null) {
            List<Row> rows = d.rows();
            for (int i = rows.size() - 1; i >= 0; i--) {   // newest on top
                Row r = rows.get(i);
                for (Filter f : Filter.values()) if (matches(r, f)) counts[f.ordinal()]++;
                if (!matches(r, filter)) continue;
                if (!search.isEmpty()
                    && !r.label().toLowerCase(Locale.ROOT).contains(search)
                    && !r.lot().toLowerCase(Locale.ROOT).contains(search)) continue;
                view.add(r);
            }
        }
        fireTableDataChanged();
    }

    static boolean matches(Row r, Filter f) {
        switch (f) {
            case MODIFIED:  return r.modified() && !r.deleted();
            case DELETED:   return r.deleted();
            case EXPORTED:  return r.everExported() && !r.deleted();
            case CORRECTED: return r.state() == Row.State.CORRECTED;
            default:        return true;
        }
    }

    public Row rowAt(int viewRow) {
        return viewRow >= 0 && viewRow < view.size() ? view.get(viewRow) : null;
    }

    /** View index of row n, or -1 when filtered out. */
    public int indexOf(int n) {
        for (int i = 0; i < view.size(); i++) if (view.get(i).n == n) return i;
        return -1;
    }

    /** The word in the Stato column. */
    public String status(Row r) {
        switch (r.state()) {
            case DELETED:   return ELIMINATA;
            case CORRECTED: return CORRETTA;
            case EXPORTED:  return ESPORTATA;
            default:
                if (r.modified()) return MODIFICATA;
                if (last != null && r.n >= last[0] && r.n < last[0] + last[1]) return NUOVA;
                return OK;
        }
    }

    String mode(Row r) {
        if (r.mode == Row.Mode.SCAN) return "scan";
        Day d = ledger.day();
        int size = d == null ? 0 : d.additionSize(r.rangeN0);
        return "int. " + size;
    }

    static String time(String iso) {
        return iso != null && iso.length() >= 19 ? iso.substring(11, 19) : "";
    }

    @Override public int getRowCount()           { return view.size(); }
    @Override public int getColumnCount()        { return COLS.length; }
    @Override public String getColumnName(int c) { return COLS[c]; }

    @Override public Object getValueAt(int row, int col) {
        Row r = view.get(row);
        switch (col) {
            case 0:  return r.n;
            case 1:  return r.label();
            case 2:  return r.lot();
            case 3:  return time(r.created);
            case 4:  return mode(r);
            default: return status(r);
        }
    }
}
