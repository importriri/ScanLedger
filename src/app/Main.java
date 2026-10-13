package app;

import app.ui.AppTheme;
import app.ui.MainWindow;
import app.ui.ScanTabSupport;

import javax.swing.*;

public class Main {
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

        ScanTabSupport.install();
        SwingUtilities.invokeLater(MainWindow::new);
    }
}
