package app.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One line of the day journal: an operator action that already reached the
 * disk. Immutable. The type words are data (Italian, like the verdicts in the
 * AutoFillSuite report), the fields are plain strings in a fixed order per type.
 */
public final class Event {

    public enum Type {
        /** postazione, giornata, first n, first export number */
        APRI,
        /** n, etichetta, lotto, modo */
        AGGIUNGI,
        /** n0, lotto, etichette joined by '|' — all rows or none */
        INTERVALLO,
        /** n, etichetta, lotto, motivo */
        MODIFICA,
        /** n */
        ELIMINA,
        /** n */
        RIPRISTINA,
        /** n0, quantita */
        ELIMINA_INTERVALLO,
        /** numero, creato, sha256, nome file, riesportazione (0/1), n joined by ',' */
        ESPORTA,
        /** numero, cartella, esito (OK/FALLITA), motivo */
        COPIA,
        /** no fields: the day is archived, read only */
        CHIUDI;

        static Type parse(String s) {
            for (Type t : values()) if (t.name().equals(s)) return t;
            return null;
        }
    }

    public final long seq;
    /** ISO local date-time, millisecond precision. */
    public final String ts;
    public final Type type;
    private final List<String> fields;

    public Event(long seq, String ts, Type type, List<String> fields) {
        this.seq = seq;
        this.ts = ts;
        this.type = type;
        this.fields = Collections.unmodifiableList(new ArrayList<>(fields));
    }

    public List<String> fields() {
        return fields;
    }

    public String field(int i) {
        return i < fields.size() ? fields.get(i) : "";
    }

    public int fieldCount() {
        return fields.size();
    }

    /** The timestamp cut to the second: what the CSV calls "ora". */
    public String tsSeconds() {
        return ts.length() >= 19 ? ts.substring(0, 19) : ts;
    }

    @Override public String toString() {
        return seq + " " + ts + " " + type + " " + fields;
    }
}
