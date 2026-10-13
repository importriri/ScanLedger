package app;

import app.config.Settings;
import app.core.DataLock;
import app.core.Ledger;
import app.ui.AppTheme;
import app.ui.MainWindow;
import app.ui.ScanTabSupport;

import javax.swing.*;
import java.io.IOException;
import java.time.Clock;

public class Main {

    /** Held for the life of the process: one Registro QR per data folder. */
    private static DataLock lock;

    /** true when the previous run did not end through the window's close. */
    private static boolean uncleanExit;

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        // paint the shared surfaces (tabs, panels, tooltips) in the chosen flavor
        UIManager.put("Panel.background", AppTheme.BASE);
        UIManager.put("TabbedPane.background", AppTheme.BASE);
        UIManager.put("TabbedPane.contentAreaColor", AppTheme.SURFACE0);
        UIManager.put("TabbedPane.foreground", AppTheme.TEXT);
        UIManager.put("ToolTip.background", AppTheme.SURFACE0);
        UIManager.put("ToolTip.foreground", AppTheme.TEXT);
        UIManager.put("OptionPane.background", AppTheme.BASE);
        UIManager.put("OptionPane.messageForeground", AppTheme.TEXT);

        Settings cfg = Settings.getInstance();
        try {
            lock = DataLock.acquire(cfg.dataDir());
        } catch (IOException e) {
            lock = null;
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null,
                    "La cartella dati non si apre: " + cfg.dataDir() + "\n" + e.getMessage(),
                    "Registro QR", JOptionPane.ERROR_MESSAGE);
                System.exit(2);
            });
            return;
        }
        if (lock == null) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, "Registro QR e' gia' aperto su questo PC.",
                    "Registro QR", JOptionPane.WARNING_MESSAGE);
                System.exit(0);
            });
            return;
        }

        uncleanExit = cfg.getBool(Settings.RUNNING, false);
        cfg.set(Settings.RUNNING, true);
        cfg.save();

        Ledger ledger = new Ledger(cfg, Clock.systemDefaultZone());
        ledger.open();

        ScanTabSupport.install();
        SwingUtilities.invokeLater(() -> new MainWindow(ledger));
    }

    public static boolean uncleanExit() {
        return uncleanExit;
    }
}
