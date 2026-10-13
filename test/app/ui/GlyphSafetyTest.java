package app.ui;

// Adapted from AutoFillSuite test/app/ui/GlyphSafetyTest.java — keep behaviour in sync.

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

// glyph safety — the test born from a real bug: emoji and box-drawing characters
// baked into UI strings (▶ 🔎 ⚙ ◂ ✔) came out as EMPTY BOXES on windows, because
// an explicit Font kills java's glyph fallback. the app looked broken.
//
// rule: a user-visible string may only contain characters we have SEEN render in
// Segoe UI / Consolas — ascii, latin-1, and the two punctuation marks below.
// anything fancier is an icon (see Icons), drawn with Graphics2D, never typed.
//
// the bundled manuals fall under the same rule since they are rendered INSIDE
// the app (Impostazioni > Manuale): an arrow or a gear glyph in the markdown is
// a box on the operator's screen, exactly like one in a java string.
//
//   java -cp build app.ui.GlyphSafetyTest
public final class GlyphSafetyTest {

    // seen rendering fine on windows: em dash and the horizontal ellipsis stays out
    private static final String ALLOWED_ABOVE_LATIN1 = "\u2014";   // —

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        Path ui = Paths.get("src/app/ui");
        if (!Files.isDirectory(ui)) {
            System.out.println("  skip  run me from the project root");
            return;
        }
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(ui)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                scan(f, offenders);
            }
        }
        check("no unsafe glyph in any ui string literal", offenders.isEmpty());
        for (String o : offenders) System.out.println("       " + o);

        List<String> manualOffenders = new ArrayList<>();
        Path docs = Paths.get("src/app/docs");
        if (Files.isDirectory(docs)) {
            try (Stream<Path> files = Files.walk(docs)) {
                for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".md"))::iterator) {
                    scanText(f, manualOffenders);
                }
            }
        }
        check("no unsafe glyph in the bundled manuals", manualOffenders.isEmpty());
        for (String o : manualOffenders) System.out.println("       " + o);

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // string literals only: comments may say whatever they like
    private static void scan(Path file, List<String> offenders) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("*")) continue;

            boolean inString = false;
            for (int c = 0; c < line.length(); c++) {
                char ch = line.charAt(c);
                if (ch == '"' && (c == 0 || line.charAt(c - 1) != '\\')) {
                    inString = !inString;
                    continue;
                }
                if (!inString) continue;
                if (ch > 0xFF && ALLOWED_ABOVE_LATIN1.indexOf(ch) < 0) {
                    offenders.add(file.getFileName() + ":" + (i + 1)
                                + "  U+" + Integer.toHexString(ch).toUpperCase()
                                + "  in: " + trimmed);
                    break;
                }
            }
        }
    }

    // a manual is user-visible from its first character: no string rule, just text
    private static void scanText(Path file, List<String> offenders) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            for (int c = 0; c < line.length(); c++) {
                char ch = line.charAt(c);
                if (ch > 0xFF && ALLOWED_ABOVE_LATIN1.indexOf(ch) < 0) {
                    offenders.add(file.getFileName() + ":" + (i + 1)
                                + "  U+" + Integer.toHexString(ch).toUpperCase()
                                + "  in: " + line.trim());
                    break;
                }
            }
        }
    }

    private static void check(String name, boolean cond) {
        if (cond) {
            passed++;
            System.out.println("  ok   " + name);
        } else {
            failed++;
            System.out.println("  FAIL " + name);
        }
    }
}
