package app.ui;

import app.config.Settings;
import app.core.ScanGuard;
import app.core.StationRule;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;

/**
 * Modeless settings window. Every change is written the moment it happens.
 * One page per area, each in its own scroll pane, and the tab area capped so
 * the dialog fits a 768 px screen (the AutoFillSuite rule: measured, then
 * capped — a tall page costs a scrollbar, never the Chiudi button).
 */
public class SettingsWindow extends JDialog {

    /** Tallest the tab area may ask for: the dialog must fit a 768px screen. */
    private static final int TAB_AREA_MAX_H = 600;
    private static final int TAB_AREA_W = 580;

    private final Settings cfg = Settings.getInstance();
    private final Runnable onChange;
    final JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);

    public SettingsWindow(Window owner, Runnable onChange) {
        super(owner, "Impostazioni", ModalityType.MODELESS);
        this.onChange = onChange;
        setDefaultCloseOperation(HIDE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBackground(AppTheme.BASE);
        root.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        AppTheme.flatTabs(tabs);
        tabs.addTab("Postazione", page(buildStation()));
        tabs.addTab("Codici",     page(buildCodes()));
        tabs.addTab("Suoni",      page(buildSounds()));
        tabs.addTab("Cartelle",   page(buildFolders()));
        tabs.addTab("Aspetto",    page(buildLook()));
        root.add(tabs, BorderLayout.CENTER);

        JButton close = AppTheme.ghost("Chiudi");
        close.addActionListener(e -> setVisible(false));
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        south.setOpaque(false);
        south.add(close);
        root.add(south, BorderLayout.SOUTH);

        setContentPane(root);
        fitTabs();
        setLocationRelativeTo(owner);
    }

    /** Adds a page after construction: Storico and Manuale need the ledger and the main window. */
    public void addPage(String title, JComponent content, boolean scroll) {
        tabs.addTab(title, scroll ? page(content) : content);
        fitTabs();
    }

    /**
     * Tab area = tallest page + the tab strip, capped. The strip is MEASURED
     * after a layout, not assumed: with wider fonts seven tabs wrap onto two
     * rows at this width, and a guessed strip height cut the Storico page.
     */
    private void fitTabs() {
        int tallest = 0;
        for (int i = 0; i < tabs.getTabCount(); i++) {
            tallest = Math.max(tallest, tabs.getComponentAt(i).getPreferredSize().height);
        }
        tabs.setPreferredSize(new Dimension(TAB_AREA_W, Math.min(tallest + 40, TAB_AREA_MAX_H)));
        pack();
        Component shown = tabs.getSelectedComponent();
        if (shown == null || shown.getHeight() <= 0) return;
        int strip = tabs.getHeight() - shown.getHeight();
        int want = Math.min(tallest + strip, TAB_AREA_MAX_H);
        if (want != tabs.getHeight()) {
            tabs.setPreferredSize(new Dimension(TAB_AREA_W, want));
            pack();
        }
    }

    public void showTab(String title) {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (title.equals(tabs.getTitleAt(i))) tabs.setSelectedIndex(i);
        }
    }

    // ── pages ────────────────────────────────────────────────────────────

    private JPanel buildStation() {
        Page p = new Page();
        p.section("Postazione");
        JTextField name = AppTheme.field();
        name.setText(cfg.get(Settings.STATION, ""));
        Runnable commit = () -> {
            String v = name.getText().trim();
            boolean ok = StationRule.valid(v);
            name.setForeground(ok ? AppTheme.TEXT : AppTheme.RED);
            name.setToolTipText(ok ? null : StationRule.HINT);
            if (ok && !v.equals(cfg.get(Settings.STATION, ""))) save(Settings.STATION, v);
        };
        commitOn(name, commit);
        p.fieldRow("Nome", name, "finisce nel nome del file e nell'intestazione del CSV: "
                               + "AutoFillSuite sa da che linea arriva. Obbligatorio.");
        p.hint("Una giornata già aperta tiene il nome con cui è nata; il nome nuovo "
             + "vale dalla prossima giornata.");
        return p.panel;
    }

    private JPanel buildCodes() {
        Page p = new Page();
        p.section("Controllo formato (facoltativo)");
        p.hint("Se descrivi i due QR, il programma riconosce le coppie invertite "
             + "(il lotto sparato in QR 1). Vuoto = nessun controllo. "
             + "Sono le stesse espressioni di AutoFillSuite: si possono copiare.");
        JTextField pat1 = AppTheme.field();
        pat1.setText(cfg.get(Settings.QR1_PATTERN, ""));
        patternField(pat1, Settings.QR1_PATTERN);
        p.fieldRow("Modello QR 1", pat1, "espressione regolare dell'etichetta, es. [0-9]{6}\\..*");
        JTextField pat2 = AppTheme.field();
        pat2.setText(cfg.get(Settings.QR2_PATTERN, ""));
        patternField(pat2, Settings.QR2_PATTERN);
        p.fieldRow("Modello QR 2", pat2, "espressione regolare del lotto, es. [0-9]{18}");

        JCheckBox autoSwap = check("Raddrizza da solo le coppie invertite",
            cfg.getBool(Settings.AUTO_SWAP, false));
        autoSwap.addActionListener(e -> save(Settings.AUTO_SWAP, autoSwap.isSelected()));
        p.row(autoSwap, "spento: il programma avvisa e aspetta che premi Scambia (F2)");

        p.section("Intervallo");
        JSpinner digits = AppTheme.spinnerInt(cfg.getInt(Settings.TAIL_DIGITS, 3), 1, 9);
        digits.addChangeListener(e -> save(Settings.TAIL_DIGITS, digits.getValue()));
        p.spinRow("Cifre mobili", digits, "", "le ultime cifre dell'etichetta che crescono "
                + "nell'intervallo. Un intervallo che ne richiederebbe una in più viene rifiutato.");

        p.section("Doppioni");
        p.hint("Un'etichetta già presente nella giornata viene sempre rifiutata: una "
             + "seconda riga per la stessa etichetta sarebbe una correzione silenziosa "
             + "sul portale. Per cambiare il lotto si modifica la riga.");
        return p.panel;
    }

    private JPanel buildSounds() {
        Page p = new Page();
        p.section("Suoni");
        JCheckBox on = check("Suoni attivi", cfg.getBool(Settings.SOUND_ON, true));
        on.addActionListener(e -> save(Settings.SOUND_ON, on.isSelected()));
        p.row(on, "ok = un suono acuto corto  -  errore = due suoni bassi");
        JSpinner vol = AppTheme.spinnerInt(cfg.getInt(Settings.SOUND_VOLUME, 80), 0, 100);
        vol.addChangeListener(e -> save(Settings.SOUND_VOLUME, vol.getValue()));
        p.spinRow("Volume", vol, "%", "");
        JPanel test = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        test.setOpaque(false);
        JButton tOk = AppTheme.secondary("Prova ok", null);
        JButton tErr = AppTheme.secondary("Prova errore", null);
        tOk.addActionListener(e -> Sounds.ok());
        tErr.addActionListener(e -> Sounds.error());
        test.add(tOk);
        test.add(tErr);
        p.row(test, "senza scheda audio il programma resta in silenzio");
        return p.panel;
    }

    private JPanel buildFolders() {
        Page p = new Page();
        p.section("Cartelle");
        folderRow(p, "Dati", Settings.DIR_DATA, Settings.defaultDataDir().getPath(),
                  "giornale delle giornate e archivio. Vale dal prossimo avvio; le giornate "
                  + "già registrate restano nella cartella di prima.");
        folderRow(p, "Export", Settings.DIR_EXPORT, "<dati>" + File.separator + "export",
                  "archivio dei CSV: il programma non cancella mai un file");
        folderRow(p, "Copia", Settings.DIR_COPY, "(nessuna)",
                  "facoltativa: chiavetta o cartella di rete. Se manca, la copia resta in sospeso.");
        return p.panel;
    }

    private JPanel buildLook() {
        Page p = new Page();
        p.section("Aspetto");
        JComboBox<String> flavor = AppTheme.combo(new String[] { "Mocha (scuro)", "Latte (chiaro)" });
        flavor.setSelectedIndex("latte".equalsIgnoreCase(cfg.get(Settings.UI_FLAVOR, "mocha")) ? 1 : 0);
        flavor.addActionListener(e -> save(Settings.UI_FLAVOR, flavor.getSelectedIndex() == 1 ? "latte" : "mocha"));
        p.fieldRowAny("Tema", flavor, "si applica al prossimo avvio");
        return p.panel;
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private void folderRow(Page p, String label, String key, String placeholder, String hint) {
        JTextField tf = AppTheme.field();
        tf.setText(cfg.get(key, ""));
        tf.setToolTipText("vuoto = " + placeholder);
        commitOn(tf, () -> save(key, tf.getText().trim()));
        JButton browse = AppTheme.secondary("Sfoglia...", Icons.folder(AppTheme.ICON, AppTheme.SUBTEXT));
        browse.addActionListener(e -> {
            JFileChooser ch = new JFileChooser(tf.getText().trim().isEmpty() ? null : tf.getText().trim());
            ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (ch.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                tf.setText(ch.getSelectedFile().getAbsolutePath());
                save(key, tf.getText().trim());
            }
        });
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.add(tf, BorderLayout.CENTER);
        row.add(browse, BorderLayout.EAST);
        p.fieldRowAny(label, row, hint + "  (vuoto = " + placeholder + ")");
    }

    private static void commitOn(JTextField tf, Runnable commit) {
        tf.addActionListener(e -> commit.run());
        tf.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { commit.run(); }
        });
    }

    /** A pattern that does not compile is worse than none: say so, then ignore it. */
    private void patternField(final JTextField tf, final String key) {
        paintPattern(tf);
        commitOn(tf, () -> {
            save(key, tf.getText().trim());
            paintPattern(tf);
        });
        tf.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e)  { paintPattern(tf); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e)  { paintPattern(tf); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { paintPattern(tf); }
        });
    }

    static void paintPattern(JTextField tf) {
        boolean ok = ScanGuard.validPattern(tf.getText());
        tf.setForeground(ok ? AppTheme.TEXT : AppTheme.RED);
        tf.setToolTipText(ok ? null : "Espressione non valida: verrà ignorata");
    }

    private void save(String key, Object value) {
        cfg.set(key, value);
        cfg.save();
        if (onChange != null) onChange.run();
    }

    private static JCheckBox check(String text, boolean value) {
        JCheckBox c = new JCheckBox(text, value);
        c.setOpaque(false);
        c.setFocusable(false);
        c.setFont(AppTheme.F_UI);
        c.setForeground(AppTheme.TEXT);
        return c;
    }

    // Adapted from AutoFillSuite src/app/ui/SettingsWindow.java (Page) — keep behaviour in sync.
    // ── page: label | control | unit, with a hint line under each row ─────
    private static final class Page {
        final JPanel panel = new JPanel(new GridBagLayout());
        private final GridBagConstraints g = new GridBagConstraints();
        private int y = 0;

        Page() {
            panel.setOpaque(false);
            panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
            g.fill = GridBagConstraints.HORIZONTAL;
            g.insets = new Insets(2, 0, 2, 6);
        }

        void section(String title) {
            g.gridx = 0; g.gridy = y++; g.gridwidth = 3; g.weightx = 1;
            g.insets = new Insets(10, 0, 4, 0);
            panel.add(AppTheme.section(title), g);
            g.insets = new Insets(2, 0, 2, 6);
        }

        void spinRow(String label, JSpinner sp, String unit, String hint) {
            g.gridx = 0; g.gridy = y; g.gridwidth = 1; g.weightx = 0;
            JLabel l = AppTheme.label(label);
            l.setPreferredSize(new Dimension(130, 22));
            panel.add(l, g);
            g.gridx = 1; g.weightx = 0;
            panel.add(sp, g);
            g.gridx = 2; g.weightx = 1;
            panel.add(AppTheme.hint(unit.isEmpty() ? "" : unit), g);
            y++;
            if (!hint.isEmpty()) hintUnder(hint);
        }

        void fieldRow(String label, JTextField tf, String hint) {
            fieldRowAny(label, tf, hint);
        }

        void fieldRowAny(String label, Component c, String hint) {
            g.gridx = 0; g.gridy = y; g.gridwidth = 1; g.weightx = 0;
            JLabel l = AppTheme.label(label);
            l.setPreferredSize(new Dimension(130, 22));
            panel.add(l, g);
            g.gridx = 1; g.gridwidth = 2; g.weightx = 1;
            panel.add(c, g);
            y++;
            if (!hint.isEmpty()) hintUnder(hint);
        }

        void row(Component c, String hint) {
            g.gridx = 0; g.gridy = y++; g.gridwidth = 3; g.weightx = 1;
            panel.add(c, g);
            if (!hint.isEmpty()) hintUnder(hint);
        }

        void hint(String text) {
            g.gridx = 0; g.gridy = y++; g.gridwidth = 3; g.weightx = 1;
            g.insets = new Insets(6, 0, 2, 0);
            panel.add(wrap(text), g);
            g.insets = new Insets(2, 0, 2, 6);
        }

        private void hintUnder(String text) {
            g.gridx = 0; g.gridy = y++; g.gridwidth = 3; g.weightx = 1;
            g.insets = new Insets(0, 2, 6, 0);
            panel.add(wrap(text), g);
            g.insets = new Insets(2, 0, 2, 6);
        }

        /** Hints wrap: a long Italian sentence must not widen the dialog. */
        private static JComponent wrap(String text) {
            JTextArea a = new JTextArea(text);
            a.setLineWrap(true);
            a.setWrapStyleWord(true);
            a.setEditable(false);
            a.setFocusable(false);
            a.setOpaque(false);
            a.setFont(AppTheme.F_SMALL);
            a.setForeground(AppTheme.OVERLAY);
            a.setColumns(40);
            a.setBorder(BorderFactory.createEmptyBorder());
            return a;
        }
    }

    /** Keeps a page top-aligned and scrollable if the window shrinks. */
    private static JScrollPane page(JComponent content) {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(AppTheme.SURFACE0);
        wrap.add(content, BorderLayout.NORTH);
        JScrollPane sc = new JScrollPane(wrap);
        sc.setBorder(BorderFactory.createEmptyBorder());
        sc.getViewport().setBackground(AppTheme.SURFACE0);
        sc.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        AppTheme.styleScroll(sc);
        return sc;
    }
}
