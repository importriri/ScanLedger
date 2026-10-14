package app.ui;

// Adapted from AutoFillSuite src/app/ui/ManualPane.java — keep behaviour in sync.

import app.config.Manuals;

import javax.swing.*;
import java.awt.*;

/**
 * The manual, inside the app. The shop-floor machine has one browser tab open
 * and it is the portal: an operator who has to leave it to find out what the
 * fail-safe does will simply not find out.
 *
 * Both languages ship in the JAR; the switch is a segment, and the pane keeps
 * a fixed preferred size so the settings dialog cannot grow to the length of
 * the document.
 */
public class ManualPane extends JPanel {

    // a MINIMUM, not a demand: the pane fills whatever the tab area gives it,
    // so the manual can never be the reason the settings dialog outgrows a
    // laptop screen
    private static final Dimension VIEW = new Dimension(520, 240);

    private final JEditorPane view = new JEditorPane();

    public ManualPane() {
        setLayout(new BorderLayout(0, 6));
        setBackground(AppTheme.SURFACE0);
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        view.setEditable(false);
        view.setContentType("text/html");
        view.setBackground(AppTheme.SURFACE0);
        view.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        // the app's font, not the editor kit's: HTML must not undo the theme
        view.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        view.setFont(AppTheme.F_UI);

        JScrollPane scroll = new JScrollPane(view);
        scroll.setPreferredSize(VIEW);
        scroll.setBorder(BorderFactory.createLineBorder(AppTheme.SURFACE1));
        scroll.getViewport().setBackground(AppTheme.SURFACE0);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        AppTheme.styleScroll(scroll);

        Segmented lang = new Segmented(new String[] { "Italiano", "English" },
                                       false, 0, i -> show(i == 1 ? Manuals.EN : Manuals.IT));
        add(lang, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(AppTheme.hint("Lo stesso testo dei file in src/app/docs."), BorderLayout.SOUTH);

        show(Manuals.IT);
    }

    private void show(String lang) {
        view.setText(page(Markdown.toHtml(Manuals.text(lang))));
        view.setCaretPosition(0);
    }

    /** Colors only: the family and the size come from the component's font. */
    private static String page(String body) {
        String text  = hex(AppTheme.TEXT);
        String head  = hex(AppTheme.BLUE);
        String rule  = hex(AppTheme.SURFACE1);
        String code  = hex(AppTheme.PEACH);
        return "<html><head><style>"
             + "body { color: " + text + "; margin: 4px 6px 8px 6px; }"
             + "h1 { color: " + head + "; font-size: 15pt; }"
             + "h2 { color: " + head + "; font-size: 13pt; }"
             + "h3 { color: " + head + "; font-size: 12pt; }"
             + "p, li, td, th { color: " + text + "; }"
             + "tt { color: " + code + "; }"
             + "hr { color: " + rule + "; }"
             + "table { border-color: " + rule + "; }"
             + "</style></head><body>" + body + "</body></html>";
    }

    private static String hex(Color c) {
        return String.format("#%06x", c.getRGB() & 0xFFFFFF);
    }
}
