package app.ui;

import app.config.Settings;
import app.core.Day;
import app.core.Journal;
import app.core.Ledger;
import app.core.Row;
import app.core.StationRule;
import app.core.WindowGuard;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * The station window: controls on the left (scan or range), the day's table on
 * the right. Not always-on-top: there is no portal to sit next to, so it can
 * be big and readable from a metre away.
 */
public class MainWindow extends JFrame implements TablePanel.Actions, ScanPanel.Host {

    static final int DEFAULT_W = 1100;
    static final int DEFAULT_H = 680;
    static final int CONTROL_W = 360;

    private static final String CARD_SCAN = "scan";
    private static final String CARD_RANGE = "range";

    private final Settings cfg = Settings.getInstance();
    private final Ledger ledger;

    final ScanPanel scanPanel;
    final RangePanel rangePanel;
    final TablePanel tablePanel;
    final Segmented modeSelector;
    private final JPanel cards = new JPanel(new CardLayout());
    final JPanel banners = new JPanel();
    private final DiskPill pill = new DiskPill();
    private final JLabel titleLabel = new JLabel();
    private final JLabel footLeft = new JLabel();
    final JLabel footRight = new JLabel();
    private SettingsWindow settingsWindow;
    HistoryPanel history;

    private final boolean uncleanExit;
    JButton btnExport;
    JButton btnCloseDay;
    private Banner copyBanner;

    public MainWindow(Ledger ledger) {
        this(ledger, false);
    }

    public MainWindow(Ledger ledger, boolean uncleanExit) {
        super("Registro QR");
        this.ledger = ledger;
        this.uncleanExit = uncleanExit;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        scanPanel = new ScanPanel(ledger, this);
        rangePanel = new RangePanel(ledger);
        tablePanel = new TablePanel(ledger, this);

        int mode = cfg.getInt(Settings.UI_MODE, 0) == 1 ? 1 : 0;
        // restored SILENTLY: the listener must not run on a half-built window
        modeSelector = new Segmented(new String[] { "SCANSIONE", "INTERVALLO" }, true, mode, idx -> {
            showCard(idx == 0 ? CARD_SCAN : CARD_RANGE);
            cfg.set(Settings.UI_MODE, idx);
            cfg.save();
            focusScan();
        });

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(AppTheme.BASE);
        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(buildHeader(), BorderLayout.NORTH);
        banners.setLayout(new BoxLayout(banners, BoxLayout.Y_AXIS));
        banners.setOpaque(false);
        banners.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        north.add(banners, BorderLayout.SOUTH);
        root.add(north, BorderLayout.NORTH);
        root.add(buildColumns(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        setContentPane(root);
        showCard(mode == 0 ? CARD_SCAN : CARD_RANGE);

        // Ctrl+Z anywhere in the window: the last pair or range goes
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK), "undoLast");
        root.getActionMap().put("undoLast", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (modeSelector.getSelected() == 0) scanPanel.undo();
                else rangePanel.undo();
                focusScan();
            }
        });

        ledger.addListener(this::ledgerChanged);
        startupBanners();
        refreshChrome();

        // measured, then the default size on top: never smaller than the content asks
        pack();
        Dimension packed = getSize();
        setMinimumSize(new Dimension(Math.min(packed.width, DEFAULT_W), Math.min(packed.height, DEFAULT_H)));
        restoreBounds();

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                cfg.set(Settings.RUNNING, false);
                cfg.save();
                dispose();
                System.exit(0);
            }
            @Override public void windowOpened(WindowEvent e) {
                focusScan();
            }
        });

        // the disk pill: the clock of the last write, and the retry when it failed
        Timer tick = new Timer(1000, e -> refreshPill());
        tick.start();
        Timer retry = new Timer(2000, e -> {
            if (!ledger.diskOk()) ledger.probe();
        });
        retry.start();

        setVisible(true);
        if (!StationRule.valid(ledger.station())) SwingUtilities.invokeLater(this::askStation);
    }

    // ── layout ───────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AppTheme.MANTLE);
        header.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 8));
        titleLabel.setFont(AppTheme.F_MONO.deriveFont(13f));
        titleLabel.setForeground(AppTheme.SUBTEXT);
        header.add(titleLabel, BorderLayout.WEST);

        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        east.setOpaque(false);
        east.add(pill);
        JButton gear = AppTheme.iconButton(Icons.gear(AppTheme.ICON + 3, AppTheme.SUBTEXT), "Impostazioni");
        gear.addActionListener(e -> openSettings(null));
        headerButtons(east);
        east.add(gear);
        header.add(east, BorderLayout.EAST);
        return header;
    }

    /** Export and day closing, next to the pill. */
    void headerButtons(JPanel east) {
        btnExport = AppTheme.button("Esporta CSV", AppTheme.MAUVE);
        btnExport.setIcon(Icons.export(AppTheme.ICON, AppTheme.ON_ACCENT));
        btnExport.setToolTipText("Scrive un CSV con le righe nuove e le correzioni");
        btnExport.addActionListener(e -> openExport(false));
        btnCloseDay = AppTheme.ghost("Chiudi giornata");
        btnCloseDay.setToolTipText("Archivia la giornata: la prossima coppia ne apre una nuova");
        btnCloseDay.addActionListener(e -> closeDay());
        east.add(btnExport);
        east.add(btnCloseDay);
    }

    private JPanel buildColumns() {
        JPanel left = AppTheme.card();
        left.setLayout(new BorderLayout(0, 10));
        left.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        left.add(modeSelector, BorderLayout.NORTH);
        cards.setOpaque(false);
        cards.add(scanPanel, CARD_SCAN);
        cards.add(rangePanel, CARD_RANGE);
        left.add(cards, BorderLayout.CENTER);

        JPanel control = new JPanel(new BorderLayout()) {
            // width pinned, height honest
            @Override public Dimension getPreferredSize() {
                return new Dimension(CONTROL_W, super.getPreferredSize().height);
            }
        };
        control.setOpaque(false);
        control.add(left, BorderLayout.CENTER);

        JPanel columns = new JPanel(new BorderLayout(10, 0));
        columns.setBackground(AppTheme.BASE);
        columns.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        columns.add(control, BorderLayout.WEST);
        columns.add(tablePanel, BorderLayout.CENTER);
        return columns;
    }

    private JPanel buildFooter() {
        JPanel foot = new JPanel(new BorderLayout(12, 0));
        foot.setBackground(AppTheme.MANTLE);
        foot.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        footLeft.setFont(AppTheme.F_MONO.deriveFont(11f));
        footLeft.setForeground(AppTheme.GREEN);
        footLeft.setIcon(Icons.dot(10, AppTheme.GREEN));
        footRight.setFont(AppTheme.F_MONO.deriveFont(11f));
        footRight.setForeground(AppTheme.OVERLAY);
        footRight.setHorizontalAlignment(SwingConstants.RIGHT);
        foot.add(footLeft, BorderLayout.WEST);
        foot.add(footRight, BorderLayout.EAST);
        return foot;
    }

    private void showCard(String card) {
        ((CardLayout) cards.getLayout()).show(cards, card);
    }

    // ── state ────────────────────────────────────────────────────────────

    private void ledgerChanged() {
        refreshCopyBanner();
        refreshChrome();
        scanPanel.ledgerChanged();
        rangePanel.update();
    }

    /** Title, pill and footer from the ledger. */
    void refreshChrome() {
        String st = ledger.station();
        titleLabel.setText("Registro QR  -  " + (st.isEmpty() ? "postazione da impostare" : st));
        refreshPill();
        Day d = ledger.day();
        String journal = d == null || d.isClosed()
            ? "nessuna giornata aperta: si apre al primo sparo"
            : "giornale " + d.file().getName() + "  -  " + d.eventCount() + " eventi"
              + (d.isCorrupted() ? "  -  SOLA LETTURA" : "");
        footLeft.setText(journal + "    cartella " + ledger.dataDir().getPath());
        footLeft.setToolTipText(footLeft.getText());
        boolean bad = !ledger.diskOk() || (d != null && d.isCorrupted());
        footLeft.setForeground(bad ? AppTheme.RED : AppTheme.GREEN);
        footLeft.setIcon(Icons.dot(10, bad ? AppTheme.RED : AppTheme.GREEN));

        String right = "nessun export oggi";
        if (d != null && !d.exports().isEmpty()) {
            app.core.ExportRecord x = d.exports().get(d.exports().size() - 1);
            String copy = x.copyPending() ? "copia IN SOSPESO"
                        : "OK".equals(x.copyOutcome()) ? "copia OK" : "nessuna copia";
            right = "export " + String.format("%02d", x.number) + " alle " + x.created.substring(11, 16)
                  + "  -  " + copy;
        }
        footRight.setText(right);
        footRight.setForeground(right.contains("SOSPESO") ? AppTheme.YELLOW : AppTheme.OVERLAY);
        boolean writable = d != null && !d.isReadOnly();
        if (btnExport != null) {
            btnExport.setEnabled(d != null && (d.isCorrupted() || !d.pendingExport().isEmpty()));
            btnCloseDay.setEnabled(writable);
        }
    }

    // ── export, closing, banners ─────────────────────────────────────────

    void openExport(boolean reexport) {
        ExportDialog d = new ExportDialog(this, ledger, reexport);
        d.setVisible(true);
        focusScan();
    }

    void closeDay() {
        Day d = ledger.day();
        if (d == null || d.isReadOnly()) return;
        int nw = d.pendingNew(), corr = d.pendingCorrections();
        if (nw > 0 || corr > 0) {
            Object[] options = { "Esporta adesso", "Annulla" };
            int c = JOptionPane.showOptionDialog(this,
                "Prima di chiudere bisogna esportare: " + nw + " righe nuove, " + corr + " correzioni.",
                "Chiudi giornata", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                null, options, options[0]);
            if (c == 0) openExport(false);
            return;
        }
        int c = JOptionPane.showConfirmDialog(this,
            "Chiudere la giornata del " + ExportDialog.dm(d.date()) + " (" + d.liveCount() + " coppie)?\n"
            + "Diventa di sola lettura; la prossima coppia apre una giornata nuova.",
            "Chiudi giornata", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (c != JOptionPane.OK_OPTION) return;
        Day.Result r = ledger.closeDay();
        if (r.ok()) {
            Sounds.ok();
            clearDayBanners();
        } else {
            Sounds.error();
            JOptionPane.showMessageDialog(this, r.message, "Chiudi giornata", JOptionPane.WARNING_MESSAGE);
        }
        focusScan();
    }

    /** What the start found: a restart after a crash, a torn line, a recovered export, old days. */
    private void startupBanners() {
        Day d = ledger.day();
        if (d != null && d.isCorrupted()) {
            Journal.Replay r = d.replay();
            addBanner(new Banner(AppTheme.RED, "Giornale danneggiato: giornata in sola lettura",
                "Copia del file in " + (r.corruptCopy == null ? "-" : r.corruptCopy.getName())
                + ". Si leggono " + d.rowCount() + " righe: esportale, poi chiudi.")
                .action("Esporta righe leggibili", true, false, () -> openExport(true)));
        }
        if (uncleanExit && d != null && !d.isCorrupted()) {
            String last = d.lastEventTs().length() >= 19 ? d.lastEventTs().substring(11, 19) : "-";
            addBanner(new Banner(AppTheme.BLUE, "Ripresa giornata del " + ExportDialog.dm(d.date())
                + " — " + d.liveCount() + " coppie, nessuna persa",
                "Il programma non era stato chiuso normalmente (ultimo evento " + last
                + "). Tabella ricostruita dal giornale: puoi continuare a sparare.")
                .action("Ok", false, true, this::focusScan));
        }
        if (d != null && d.replay() != null && d.replay().hasDiscardedTail()) {
            Journal.Replay r = d.replay();
            String what = r.discardedLabel == null ? "una riga incompleta"
                        : r.discardedLabel + (r.discardedLot == null ? "" : "  -  " + r.discardedLot);
            addBanner(new Banner(AppTheme.PEACH, "Ultima coppia scartata: " + what,
                "Il PC si è spento mentre la scriveva: sparala di nuovo.")
                .action("Ok", false, true, this::focusScan));
        }
        for (String name : ledger.recovered()) {
            addBanner(new Banner(AppTheme.BLUE, "Export recuperato: " + name,
                "Il PC si era spento subito dopo averlo scritto: ora è registrato nel giornale.")
                .action("Ok", false, true, this::focusScan));
        }
        if (d != null && !d.isCorrupted()
                && d.date().compareTo(java.time.LocalDate.now(ledger.clock()).toString()) < 0) {
            int pending = d.pendingExport().size();
            if (pending > 0) {
                addBanner(new Banner(AppTheme.PEACH, "Il " + ExportDialog.dm(d.date())
                    + " non è stato esportato: " + pending + " coppie",
                    "Esporta prima di andare avanti, oppure tienilo per dopo: la giornata resta aperta.")
                    .action("Esporta " + ExportDialog.dm(d.date()), true, true, () -> openExport(false))
                    .action("Più tardi", false, true, this::focusScan));
            } else {
                addBanner(new Banner(AppTheme.PEACH, "La giornata del " + ExportDialog.dm(d.date())
                    + " è ancora aperta",
                    "Chiudila per iniziare quella di oggi: finché è aperta le coppie nuove vanno lì.")
                    .action("Chiudi giornata", true, true, this::closeDay)
                    .action("Più tardi", false, true, this::focusScan));
            }
        }
        refreshCopyBanner();
    }

    private void addBanner(Banner b) {
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        banners.add(b);
        banners.revalidate();
    }

    private void clearDayBanners() {
        for (Component c : banners.getComponents()) if (c != copyBanner) banners.remove(c);
        banners.revalidate();
        banners.repaint();
    }

    /** The copy that is waiting for its folder stays on screen until it succeeds. */
    private void refreshCopyBanner() {
        java.util.List<Ledger.PendingCopy> pending = ledger.pendingCopies();
        if (copyBanner != null) {
            banners.remove(copyBanner);
            copyBanner = null;
        }
        if (!pending.isEmpty()) {
            Ledger.PendingCopy first = pending.get(0);
            copyBanner = new Banner(AppTheme.YELLOW, "COPIA IN SOSPESO — " + pending.size()
                + (pending.size() == 1 ? " export" : " export"),
                ExportDialog.dm(first.date) + " export " + String.format("%02d", first.number) + ": "
                + first.reason + ". Riprovo a ogni avvio e a ogni export.")
                .action("Riprova ora", false, false, () -> {
                    ledger.retryAllCopies();
                    ledger.fireChanged();
                });
            copyBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
            banners.add(copyBanner);
        }
        banners.revalidate();
        banners.repaint();
    }

    private void refreshPill() {
        if (ledger.diskOk()) {
            long t = ledger.lastSavedMillis();
            pill.set(AppTheme.GREEN, t > 0
                ? "salvato su disco " + new SimpleDateFormat("HH:mm:ss").format(new Date(t))
                : "nessuna scrittura ancora");
        } else {
            pill.set(AppTheme.RED, "DISCO NON SCRIVE");
        }
        pill.setToolTipText(ledger.diskOk() ? "ora dell'ultimo evento scritto e sincronizzato su disco"
                                            : ledger.diskError() + "  -  riprovo ogni 2 secondi");
    }

    // ── actions ──────────────────────────────────────────────────────────

    @Override public void edit(Row r) {
        EditDialog d = new EditDialog(this, ledger, r);
        d.setVisible(true);
        tablePanel.showRow(r.n);
        focusScan();
    }

    @Override public void delete(Row r) {
        Day.Result res = ledger.delete(r.n);
        if (res.ok()) Sounds.ok();
        else {
            Sounds.error();
            JOptionPane.showMessageDialog(this, res.message, "Elimina", JOptionPane.WARNING_MESSAGE);
        }
        focusScan();
    }

    @Override public void restore(Row r) {
        Day.Result res = ledger.restore(r.n);
        if (res.ok()) Sounds.ok();
        else {
            Sounds.error();
            JOptionPane.showMessageDialog(this, res.message, "Ripristina", JOptionPane.WARNING_MESSAGE);
        }
        focusScan();
    }

    @Override public void showRow(int n) {
        tablePanel.showRow(n);
    }

    /** Every action ends here: the caret goes back where the next scan lands. */
    @Override public void focusScan() {
        SwingUtilities.invokeLater(() -> {
            if (modeSelector.getSelected() == 0) scanPanel.focusQr1();
            else rangePanel.tfFirst.requestFocusInWindow();
        });
    }

    void openSettings(String tab) {
        if (settingsWindow == null) {
            settingsWindow = new SettingsWindow(this, this::settingsChanged);
            history = new HistoryPanel(ledger);
            settingsWindow.addPage("Storico", history, false);
        }
        if ("Storico".equals(tab) || tab == null) history.reload();
        if (tab != null) settingsWindow.showTab(tab);
        settingsWindow.setVisible(true);
        settingsWindow.toFront();
    }

    SettingsWindow settingsWindow() {
        return settingsWindow;
    }

    private void settingsChanged() {
        refreshChrome();
        scanPanel.ledgerChanged();
        rangePanel.update();
    }

    /** First start: the station name is required before the first scan. */
    void askStation() {
        StationDialog d = new StationDialog(this);
        d.setVisible(true);
        if (d.name() != null) {
            cfg.set(Settings.STATION, d.name());
            cfg.save();
        }
        settingsChanged();
        // the dialog had the focus: take the window back, then the caret
        toFront();
        requestFocus();
        focusScan();
    }

    private void restoreBounds() {
        int x = cfg.getInt(Settings.UI_WIN_X, Integer.MIN_VALUE);
        int y = cfg.getInt(Settings.UI_WIN_Y, Integer.MIN_VALUE);
        int w = Math.max(getMinimumSize().width, cfg.getInt(Settings.UI_WIN_W, DEFAULT_W));
        int h = Math.max(getMinimumSize().height, cfg.getInt(Settings.UI_WIN_H, DEFAULT_H));
        setSize(w, h);
        List<Rectangle> screens = new ArrayList<>();
        for (GraphicsDevice dev : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            screens.add(dev.getDefaultConfiguration().getBounds());
        }
        if (x != Integer.MIN_VALUE && WindowGuard.reachableOnAny(new Rectangle(x, y, w, h), screens)) {
            setLocation(x, y);
        } else {
            setLocationByPlatform(true);
        }
        final Timer saver = new Timer(400, e -> {
            cfg.set(Settings.UI_WIN_X, getX());
            cfg.set(Settings.UI_WIN_Y, getY());
            cfg.set(Settings.UI_WIN_W, getWidth());
            cfg.set(Settings.UI_WIN_H, getHeight());
            cfg.save();
        });
        saver.setRepeats(false);
        addComponentListener(new ComponentAdapter() {
            @Override public void componentMoved(ComponentEvent e)   { saver.restart(); }
            @Override public void componentResized(ComponentEvent e) { saver.restart(); }
        });
    }

    /** "salvato su disco hh:mm:ss": the pill is the disk's heartbeat. */
    static final class DiskPill extends JLabel {
        private Color tone = AppTheme.GREEN;

        DiskPill() {
            setFont(AppTheme.F_BOLD);
            setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 12));
            setIconTextGap(6);
        }

        void set(Color tone, String text) {
            this.tone = tone;
            setForeground(tone);
            setIcon(Icons.dot(10, tone));
            setText(text);
            repaint();
        }

        Color tone() {
            return tone;
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(AppTheme.blend(tone, AppTheme.MANTLE, 0.12f));
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            g2.setColor(tone);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    DiskPill pill() {
        return pill;
    }

    Ledger ledger() {
        return ledger;
    }
}
