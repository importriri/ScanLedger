package app.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the export file exactly as docs/CSV-FORMAT.md says: UTF-8 without
 * BOM, CRLF, ';' separator, quotes only when a field holds ';' or '"', and
 * a closing {@code #fine;righe=N;sha256=H} line. Pure: text in, bytes out.
 */
public final class CsvWriter {

    public static final String MAGIC = "#registro-qr";
    public static final String COLUMNS = "n;etichetta;lotto;ora;modo;tipo;modificata;lotto_prima";
    public static final int FORMAT = 1;

    private CsvWriter() {}

    /** The contract's quoting rule — RunReport.esc of AutoFillSuite, minus newlines. */
    public static String esc(String s) {
        if (s == null) return "";
        boolean needs = s.indexOf(';') >= 0 || s.indexOf('"') >= 0;
        return needs ? '"' + s.replace("\"", "\"\"") + '"' : s;
    }

    public static String header(String station, String date, int number, String created, boolean reexport) {
        return MAGIC + ";formato=" + FORMAT + ";postazione=" + station + ";giornata=" + date
             + ";export=" + number + ";creato=" + created + (reexport ? ";riesportazione=1" : "");
    }

    public static String line(ExportRecord.Line l) {
        return l.n + ";" + esc(l.label) + ";" + esc(l.lot) + ";" + l.ora + ";" + l.mode.name()
             + ";" + l.tipo + ";" + (l.modified ? "1" : "0") + ";" + esc(l.lotBefore);
    }

    /** SHA-256 of every line before #fine, each followed by a single LF. */
    public static String fingerprint(List<String> lines) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String l : lines) {
                md.update(l.getBytes(StandardCharsets.UTF_8));
                md.update((byte) '\n');
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 missing from the JDK", e);
        }
    }

    /** A whole file, ready to be written. */
    public static final class Output {
        public final byte[] bytes;
        public final String sha256;

        Output(byte[] bytes, String sha256) {
            this.bytes = bytes;
            this.sha256 = sha256;
        }
    }

    public static Output write(String header, List<ExportRecord.Line> rows) {
        List<String> lines = new ArrayList<>();
        lines.add(header);
        lines.add(COLUMNS);
        for (ExportRecord.Line l : rows) lines.add(line(l));
        String sha = fingerprint(lines);
        StringBuilder sb = new StringBuilder();
        for (String l : lines) sb.append(l).append("\r\n");
        sb.append("#fine;righe=").append(rows.size()).append(";sha256=").append(sha).append("\r\n");
        return new Output(sb.toString().getBytes(StandardCharsets.UTF_8), sha);
    }

    /** export_<NN>: two digits, more when a day goes past 99. */
    public static String fileName(String station, String date, int number) {
        return "registro_" + station + "_" + date + "_" + String.format("%02d", number) + ".csv";
    }
}
