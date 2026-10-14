package app.ui;

// Adapted from AutoFillSuite test/app/ui/ManualRenderTest.java — keep behaviour in sync.

import app.config.Manuals;

// the manual inside the app: a loader that must never throw and a markdown
// reader that must never let the document's own text become markup. no screen
// needed — both are text in, text out, which is exactly why the rendering was
// split from the pane that shows it.
//
//   java -cp out app.ui.ManualRenderTest
public final class ManualRenderTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        headings_becomeHeadings();
        paragraphs_joinTheirLines();
        boldAndCode_areMarkedUp();
        oddMarker_isLeftAlone();
        lists_closeThemselves();
        listContinuation_staysInTheItem();
        table_readsItsHeader();
        rule_becomesAnHr();
        angleBrackets_cannotBecomeTags();
        emptyInput_isEmptyOutput();

        manuals_bothLanguagesLoad();
        manuals_unknownLanguage_fallsBackToItalian();
        manuals_neverReturnNull();
        manuals_renderWithoutBlowingUp();

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // ── markdown ──────────────────────────────────────────────────────────

    private static void headings_becomeHeadings() {
        check("a level-1 heading", Markdown.toHtml("# Titolo").contains("<h1>Titolo</h1>"));
        check("a level-3 heading", Markdown.toHtml("### Piccolo").contains("<h3>Piccolo</h3>"));
        check("a hash with no space is not a heading",
              !Markdown.toHtml("#nothashtag").contains("<h1>"));
    }

    private static void paragraphs_joinTheirLines() {
        String html = Markdown.toHtml("una riga\ne la sua continuazione\n\naltro paragrafo");
        check("wrapped lines become one paragraph",
              html.contains("<p>una riga e la sua continuazione</p>"));
        check("a blank line opens a new paragraph",
              html.contains("<p>altro paragrafo</p>"));
    }

    private static void boldAndCode_areMarkedUp() {
        check("bold", Markdown.toHtml("premi **AVVIA** ora").contains("premi <b>AVVIA</b> ora"));
        check("inline code", Markdown.toHtml("il file `report.csv`").contains("<tt>report.csv</tt>"));
    }

    private static void oddMarker_isLeftAlone() {
        // a single ** in the middle of a sentence must not eat the rest of the file
        String html = Markdown.toHtml("questo ** resta cosi");
        check("an unpaired marker stays literal text", html.contains("questo ** resta cosi"));
        check("an unpaired marker opens no tag", !html.contains("<b>"));
    }

    private static void lists_closeThemselves() {
        String html = Markdown.toHtml("- uno\n- due\n\ntesto dopo");
        check("bullets become a ul", html.contains("<ul>") && html.contains("<li>uno</li>"));
        check("the list closes before the next block",
              html.indexOf("</ul>") < html.indexOf("testo dopo"));
        String ol = Markdown.toHtml("1. primo\n2. secondo");
        check("numbers become an ol", ol.contains("<ol>") && ol.contains("<li>secondo</li>"));
        check("every opened list is closed",
              count(ol, "<ol>") == count(ol, "</ol>"));
    }

    private static void listContinuation_staysInTheItem() {
        // the manuals wrap long steps: the second line belongs to the same item,
        // not to a paragraph escaping the list
        String html = Markdown.toHtml("1. una istruzione\n   che continua sotto\n2. la prossima");
        check("an indented line joins the item above",
              html.contains("<li>una istruzione che continua sotto</li>"));
        check("no stray paragraph inside the list", !html.contains("<p>che continua"));
    }

    private static void table_readsItsHeader() {
        String html = Markdown.toHtml("| Sintomo | Rimedio |\n|---|---|\n| Fermo | Riparti |");
        check("a table is a table", html.contains("<table"));
        check("the first row is the header", html.contains("<th>Sintomo</th>"));
        check("the body row is data", html.contains("<td>Riparti</td>"));
        check("the separator row is not rendered", !html.contains("<td>---</td>"));
    }

    private static void rule_becomesAnHr() {
        check("a rule", Markdown.toHtml("prima\n\n---\n\ndopo").contains("<hr>"));
    }

    private static void angleBrackets_cannotBecomeTags() {
        String html = Markdown.toHtml("scrivi <b>a mano</b> & aspetta");
        check("angle brackets are escaped", html.contains("&lt;b&gt;a mano&lt;/b&gt;"));
        check("ampersands are escaped", html.contains("&amp; aspetta"));
    }

    private static void emptyInput_isEmptyOutput() {
        check("null renders to nothing", Markdown.toHtml(null).isEmpty());
        check("blank renders to nothing", Markdown.toHtml("\n\n").trim().isEmpty());
    }

    // ── loader ────────────────────────────────────────────────────────────

    private static void manuals_bothLanguagesLoad() {
        String it = Manuals.text(Manuals.IT);
        String en = Manuals.text(Manuals.EN);
        check("the italian manual is bundled and readable",
              it.contains("Manuale d'uso") && it.length() > 2000);
        check("the english manual is bundled and readable",
              en.contains("User Manual") && en.length() > 2000);
        check("the two are not the same file", !it.equals(en));
        // the parts an operator looks up: they must be in both
        check("the italian manual says what a power cut does", it.contains("si spegne"));
        check("the english manual says what a power cut does", en.contains("power cut"));
        check("the italian manual warns against resaving in Excel", it.contains("Excel"));
        check("the english manual warns against resaving in Excel", en.contains("Excel"));
        check("the italian manual documents the swap", it.contains("Scambia"));
        check("the english manual documents the swap", en.contains("Scambia"));
    }

    private static void manuals_unknownLanguage_fallsBackToItalian() {
        check("an unknown language reads as the shop floor one",
              Manuals.text("de").equals(Manuals.text(Manuals.IT)));
        check("a null language reads as the shop floor one",
              Manuals.text(null).equals(Manuals.text(Manuals.IT)));
    }

    private static void manuals_neverReturnNull() {
        check("the loader never hands back null", Manuals.text("it") != null);
    }

    private static void manuals_renderWithoutBlowingUp() {
        String html = Markdown.toHtml(Manuals.text(Manuals.IT));
        check("the whole italian manual renders", html.contains("<h1>") && html.contains("<table"));
        check("every paragraph it opened, it closed", count(html, "<p>") == count(html, "</p>"));
        check("every list it opened, it closed",
              count(html, "<ul>") == count(html, "</ul>")
           && count(html, "<ol>") == count(html, "</ol>"));
        String en = Markdown.toHtml(Manuals.text(Manuals.EN));
        check("the whole english manual renders too",
              en.contains("<h1>") && count(en, "<p>") == count(en, "</p>"));
    }

    private static int count(String haystack, String needle) {
        int n = 0, at = 0;
        for (;;) {
            at = haystack.indexOf(needle, at);
            if (at < 0) return n;
            n++;
            at += needle.length();
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
