package app.core;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads an export file and says what it is, in the order the contract checks
 * it: format, completeness, columns, fingerprint, fields. Never throws on bad
 * content and never answers with a magic string: a {@link Status}, a detail
 * for the operator, the header and the rows.
 *
 * AutoFillSuite carries a copy of this class (CsvImport): the two apps read
 * the format with the same code, and both test it on the same five samples.
 */
public final class CsvReader {

    public enum Status {
        /** valid, fingerprint matches */
        OK,
        /** formato is not 1: written by a newer program */
        UNKNOWN_FORMAT,
        /** first line is not a Registro QR header, or a required key is missing */
        BAD_HEADER,
        /** no #fine line, or righe does not match: the file was cut */
        TRUNCATED,
        /** the column line is not the contract's */
        BAD_COLUMNS,
        /** the content changed after Registro QR wrote it (Excel, a hand edit) */
        MODIFIED,
        /** a data line breaks a field rule */
        BAD_FIELD,
        /** the same n twice in one file */
        DUPLICATE_N,
        /** empty file */
        EMPTY
    }

    /** One data line. */
    public static final class CsvRow {
        public final int n;
        public final String label;
        public final String lot;
        public final String ora;
        public final String mode;
        public final String tipo;
        public final boolean modified;
        public final String lotBefore;

        CsvRow(int n, String label, String lot, String ora, String mode, String tipo,
               boolean modified, String lotBefore) {
            this.n = n;
            this.label = label;
            this.lot = lot;
            this.ora = ora;
            this.mode = mode;
            this.tipo = tipo;
            this.modified = modified;
            this.lotBefore = lotBefore;
        }

        public boolean isCorrection() {
            return "CORREZIONE".equals(tipo);
        }
    }

    /** What the file is. */
    public static final class Result {
        public final Status status;
        public final String detail;
        public final Map<String, String> header;
        public final List<CsvRow> rows;
        /** fingerprint computed from the content (null when it could not be) */
        public final String computedSha;
        /** fingerprint written in #fine (null when absent) */
        public final String declaredSha;

        Result(Status status, String detail, Map<String, String> header, List<CsvRow> rows,
               String computedSha, String declaredSha) {
            this.status = status;
            this.detail = detail;
            this.header = Collections.unmodifiableMap(header);
            this.rows = Collections.unmodifiableList(rows);
            this.computedSha = computedSha;
            this.declaredSha = declaredSha;
        }

        public boolean ok() {
            return status == Status.OK;
        }

        public String station()    { return header.getOrDefault("postazione", ""); }
        public String date()       { return header.getOrDefault("giornata", ""); }
        public String created()    { return header.getOrDefault("creato", ""); }
        public boolean reexport()  { return "1".equals(header.get("riesportazione")); }

        public int exportNumber() {
            try { return Integer.parseInt(header.getOrDefault("export", "0").trim()); }
            catch (NumberFormatException e) { return 0; }
        }
    }

    private static final String[] REQUIRED = { "formato", "postazione", "giornata", "export", "creato" };

    private CsvReader() {}

    public static Result read(byte[] bytes) {
        String text = decode(bytes);
        if (!text.isEmpty() && text.charAt(0) == '﻿') text = text.substring(1);   // Excel's BOM
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines.add(stripCr(text.substring(start, i)));
                start = i + 1;
            }
        }
        if (start < text.length()) lines.add(stripCr(text.substring(start)));
        Map<String, String> header = new LinkedHashMap<>();
        List<CsvRow> rows = new ArrayList<>();
        if (lines.isEmpty() || (lines.size() == 1 && lines.get(0).isEmpty())) {
            return new Result(Status.EMPTY, "file vuoto", header, rows, null, null);
        }

        // 1. header and format
        String first = lines.get(0);
        List<String> h = split(first);
        if (h == null || h.isEmpty() || !CsvWriter.MAGIC.equals(h.get(0))) {
            return new Result(Status.BAD_HEADER, "la prima riga non è un'intestazione di Registro QR",
                              header, rows, null, null);
        }
        for (int i = 1; i < h.size(); i++) {
            String kv = h.get(i);
            int eq = kv.indexOf('=');
            if (eq > 0) header.put(kv.substring(0, eq), kv.substring(eq + 1));
        }
        String format = header.get("formato");
        if (format != null && !String.valueOf(CsvWriter.FORMAT).equals(format.trim())) {
            return new Result(Status.UNKNOWN_FORMAT, "formato " + format + ": questo programma legge il formato "
                              + CsvWriter.FORMAT, header, rows, null, null);
        }
        for (String k : REQUIRED) {
            if (!header.containsKey(k) || header.get(k).trim().isEmpty()) {
                return new Result(Status.BAD_HEADER, "manca '" + k + "' nell'intestazione", header, rows, null, null);
            }
        }

        // 2. complete?
        int fine = -1;
        for (int i = lines.size() - 1; i >= 1; i--) {
            if (lines.get(i).isEmpty() && i == lines.size() - 1) continue;
            if (lines.get(i).startsWith("#fine")) fine = i;
            break;
        }
        if (fine < 0) {
            return new Result(Status.TRUNCATED, "manca la riga #fine: il file è stato tagliato",
                              header, rows, null, null);
        }
        Map<String, String> end = new LinkedHashMap<>();
        for (String kv : lines.get(fine).split(";")) {
            int eq = kv.indexOf('=');
            if (eq > 0) end.put(kv.substring(0, eq), kv.substring(eq + 1));
        }
        String declared = end.get("sha256");
        int dataLines = fine - 2;
        int declaredRows;
        try {
            declaredRows = Integer.parseInt(end.getOrDefault("righe", "-1").trim());
        } catch (NumberFormatException e) {
            declaredRows = -1;
        }
        if (dataLines < 0 || declaredRows != dataLines) {
            return new Result(Status.TRUNCATED, "#fine dichiara " + declaredRows + " righe, il file ne ha "
                              + Math.max(0, dataLines), header, rows, null, declared);
        }

        // 3. columns
        if (!CsvWriter.COLUMNS.equals(lines.get(1))) {
            return new Result(Status.BAD_COLUMNS, "riga delle colonne diversa dal contratto",
                              header, rows, null, declared);
        }

        // 4. fingerprint: every line before #fine, as written, each + LF
        String computed = CsvWriter.fingerprint(lines.subList(0, fine));
        boolean modified = declared == null || !declared.equals(computed);

        // 5. fields (read even when modified: the operator may confirm and go on)
        Set<Integer> seen = new HashSet<>();
        for (int i = 2; i < fine; i++) {
            List<String> f = split(lines.get(i));
            String where = "riga " + (i + 1) + ": ";
            if (f == null) return bad(Status.BAD_FIELD, where + "virgolette non chiuse", header, rows, computed, declared, modified);
            if (f.size() != 8) return bad(Status.BAD_FIELD, where + f.size() + " campi invece di 8", header, rows, computed, declared, modified);
            int n;
            try { n = Integer.parseInt(f.get(0)); } catch (NumberFormatException e) { n = -1; }
            if (n < 1) return bad(Status.BAD_FIELD, where + "n non valido", header, rows, computed, declared, modified);
            String label = f.get(1), lot = f.get(2);
            if (label.trim().isEmpty() || lot.trim().isEmpty()) {
                return bad(Status.BAD_FIELD, where + "etichetta o lotto vuoti", header, rows, computed, declared, modified);
            }
            if (Day.hasControlChars(label) || Day.hasControlChars(lot)) {
                return bad(Status.BAD_FIELD, where + "TAB dentro un campo", header, rows, computed, declared, modified);
            }
            String mode = f.get(4), tipo = f.get(5), mod = f.get(6), before = f.get(7);
            if (!"SCAN".equals(mode) && !"INTERVALLO".equals(mode)) {
                return bad(Status.BAD_FIELD, where + "modo '" + mode + "'", header, rows, computed, declared, modified);
            }
            if (!"NUOVA".equals(tipo) && !"CORREZIONE".equals(tipo)) {
                return bad(Status.BAD_FIELD, where + "tipo '" + tipo + "'", header, rows, computed, declared, modified);
            }
            if (!"0".equals(mod) && !"1".equals(mod)) {
                return bad(Status.BAD_FIELD, where + "modificata '" + mod + "'", header, rows, computed, declared, modified);
            }
            if ("CORREZIONE".equals(tipo) == before.isEmpty()) {
                return bad(Status.BAD_FIELD, where + "lotto_prima "
                           + (before.isEmpty() ? "mancante in una CORREZIONE" : "presente in una NUOVA"),
                           header, rows, computed, declared, modified);
            }
            if (!seen.add(n)) return bad(Status.DUPLICATE_N, where + "n " + n + " ripetuto", header, rows, computed, declared, modified);
            rows.add(new CsvRow(n, label, lot, f.get(3), mode, tipo, "1".equals(mod), before));
        }
        if (modified) {
            return new Result(Status.MODIFIED, "l'impronta non coincide: il file è stato modificato fuori da Registro QR",
                              header, rows, computed, declared);
        }
        return new Result(Status.OK, "", header, rows, computed, declared);
    }

    /** A broken field in a file that was ALSO modified is reported as modified first. */
    private static Result bad(Status s, String detail, Map<String, String> header, List<CsvRow> rows,
                              String computed, String declared, boolean modified) {
        if (modified) {
            return new Result(Status.MODIFIED, "l'impronta non coincide (" + detail + ")",
                              header, rows, computed, declared);
        }
        return new Result(s, detail, header, rows, computed, declared);
    }

    /** UTF-8, with the latin-1 fallback of AutoFillSuite's DownloadWatcher.readLines. */
    static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException notUtf8) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    private static String stripCr(String s) {
        return s.endsWith("\r") ? s.substring(0, s.length() - 1) : s;
    }

    /**
     * Splits one line on ';' honouring quotes ("" inside quotes is one quote).
     * null when a quote is left open.
     */
    static List<String> split(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        boolean wasQuoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"' && cur.length() == 0 && !wasQuoted) {
                quoted = true;
                wasQuoted = true;
            } else if (c == ';') {
                out.add(cur.toString());
                cur.setLength(0);
                wasQuoted = false;
            } else {
                cur.append(c);
            }
        }
        if (quoted) return null;
        out.add(cur.toString());
        return out;
    }
}
