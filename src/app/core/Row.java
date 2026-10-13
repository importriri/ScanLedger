package app.core;

/**
 * One label + lot pair of the day. Mutable only through {@link Day}, which
 * changes it after the journal has the event on disk.
 */
public final class Row {

    public enum Mode { SCAN, INTERVALLO }

    /** On screen: LIVE = OK / NUOVA / MODIFICATA, then ELIMINATA, ESPORTATA, CORRETTA. */
    public enum State { LIVE, DELETED, EXPORTED, CORRECTED }

    public final int n;
    public final Mode mode;
    /** seconds precision, ISO local: when the row was born */
    public final String created;
    /** n of the first row of the range this row belongs to, or 0 */
    public final int rangeN0;

    String label;
    String lot;
    boolean modified;
    boolean deleted;
    /** the lot that went out with the last export, null if never exported */
    String exportedLot;
    /** export number the row last went out with, 0 if never */
    int lastExport;
    /** when the lot was last changed after an export (CORREZIONE "ora"), or null */
    String correctedAt;
    String lastReason = "";

    Row(int n, String label, String lot, Mode mode, String created, int rangeN0) {
        this.n = n;
        this.label = label;
        this.lot = lot;
        this.mode = mode;
        this.created = created;
        this.rangeN0 = rangeN0;
    }

    public String label()        { return label; }
    public String lot()          { return lot; }
    public boolean modified()    { return modified; }
    public boolean deleted()     { return deleted; }
    public String exportedLot()  { return exportedLot; }
    public int lastExport()      { return lastExport; }
    public String correctedAt()  { return correctedAt; }
    public String lastReason()   { return lastReason; }

    public boolean everExported() {
        return exportedLot != null;
    }

    public State state() {
        if (deleted) return State.DELETED;
        if (exportedLot == null) return State.LIVE;
        return lot.equals(exportedLot) ? State.EXPORTED : State.CORRECTED;
    }

    Row copy() {
        Row r = new Row(n, label, lot, mode, created, rangeN0);
        r.modified = modified;
        r.deleted = deleted;
        r.exportedLot = exportedLot;
        r.lastExport = lastExport;
        r.correctedAt = correctedAt;
        r.lastReason = lastReason;
        return r;
    }

    @Override public String toString() {
        return n + ":" + label + "/" + lot + "/" + state();
    }
}
