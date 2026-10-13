package app.core;

/**
 * The station name travels in the export file name and in the CSV header
 * ({@code postazione=L2}): it may hold neither ';' nor '=' nor characters a
 * Windows file name refuses. Letters, digits, '-' and '_', up to 20.
 */
public final class StationRule {

    public static final String HINT = "solo lettere, cifre, '-' e '_' (massimo 20)";

    private StationRule() {}

    public static boolean valid(String name) {
        if (name == null) return false;
        String s = name.trim();
        if (s.isEmpty() || s.length() > 20) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                      || (c >= '0' && c <= '9') || c == '-' || c == '_';
            if (!ok) return false;
        }
        return true;
    }
}
