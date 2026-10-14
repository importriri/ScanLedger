package app.config;

// Adapted from AutoFillSuite src/app/config/Manuals.java — keep behaviour in sync.

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;

/**
 * The operator manuals travel INSIDE the JAR: the shop floor has no repository
 * and the one machine that matters has no browser tab open on GitHub. The two
 * files under src/app/docs are the only copy — the README links to them, the
 * settings window renders them.
 *
 * Never throws and never returns null: a build without the resource still opens
 * the manual tab, it just says so.
 */
public final class Manuals {

    public static final String IT = "it";
    public static final String EN = "en";

    private static final String RESOURCE = "/app/docs/MANUAL.";
    private static final String SOURCE   = "src/app/docs/MANUAL.";

    private Manuals() {}

    /** Anything that is not "en" reads as Italian: the shop floor language. */
    public static String text(String lang) {
        String code = EN.equalsIgnoreCase(lang) ? EN : IT;
        String body = fromClasspath(RESOURCE + code + ".md");
        if (body == null) body = fromWorkingDir(SOURCE + code + ".md");
        if (body == null) {
            return "# Manuale non disponibile\n\n"
                 + "Questa build non contiene il file del manuale. "
                 + "Trovi la versione aggiornata nel repository, in `src/app/docs`.";
        }
        return body;
    }

    private static String fromClasspath(String path) {
        InputStream in = Manuals.class.getResourceAsStream(path);
        if (in == null) return null;
        try {
            return read(in);
        } catch (IOException e) {
            return null;
        } finally {
            closeQuietly(in);
        }
    }

    /** Running from a classes directory that never got the resources copied. */
    private static String fromWorkingDir(String path) {
        File f = new File(path);
        if (!f.isFile()) return null;
        try {
            byte[] bytes = Files.readAllBytes(f.toPath());
            return new String(bytes, Charset.forName("UTF-8"));
        } catch (IOException e) {
            return null;
        }
    }

    private static String read(InputStream in) throws IOException {
        BufferedReader r = new BufferedReader(
            new InputStreamReader(in, Charset.forName("UTF-8")));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) out.append(line).append('\n');
        return out.toString();
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // closing a resource we already read is nobody's problem
        }
    }
}
