package app.ui;

import app.config.Settings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * The station window. Not always-on-top: there is no portal to sit next to.
 */
public class MainWindow extends JFrame {

    static final int DEFAULT_W = 1100;
    static final int DEFAULT_H = 680;

    private final Settings cfg = Settings.getInstance();

    public MainWindow() {
        super("Registro QR");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(AppTheme.BASE);
        root.add(buildHeader(), BorderLayout.NORTH);
        setContentPane(root);

        pack();
        setSize(Math.max(getWidth(), DEFAULT_W), Math.max(getHeight(), DEFAULT_H));
        setLocationByPlatform(true);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                cfg.save();
                dispose();
                System.exit(0);
            }
        });

        setVisible(true);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AppTheme.MANTLE);
        header.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 8));

        JLabel name = new JLabel("Registro QR");
        name.setFont(AppTheme.F_TITLE);
        name.setForeground(AppTheme.TEXT);
        header.add(name, BorderLayout.WEST);
        return header;
    }
}
