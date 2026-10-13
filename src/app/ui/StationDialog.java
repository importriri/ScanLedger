package app.ui;

import app.core.StationRule;

import javax.swing.*;
import java.awt.*;

/** First start: the station name, required — it names every export file. */
final class StationDialog extends JDialog {

    final JTextField field = AppTheme.field();
    private final JLabel problem = AppTheme.hint(" ");
    private String name;

    StationDialog(Window owner) {
        super(owner, "Postazione", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(AppTheme.SURFACE0);
        root.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));
        GridBagConstraints g = AppTheme.gbc();
        g.insets = new Insets(3, 3, 3, 3);
        g.gridx = 0; g.gridy = 0;
        JLabel t = new JLabel("Nome della postazione");
        t.setFont(AppTheme.F_TITLE);
        t.setForeground(AppTheme.TEXT);
        root.add(t, g);
        g.gridy++;
        root.add(AppTheme.hint("Esempio: L2. Finisce nel nome dei file e nel CSV."), g);
        g.gridy++;
        field.setColumns(18);
        field.setFont(AppTheme.F_SCAN);
        root.add(field, g);
        g.gridy++;
        problem.setForeground(AppTheme.RED);
        root.add(problem, g);
        g.gridy++;
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btns.setOpaque(false);
        JButton later = AppTheme.ghost("Più tardi");
        JButton ok = AppTheme.button("Salva", AppTheme.MAUVE);
        btns.add(later);
        btns.add(ok);
        root.add(btns, g);

        ok.addActionListener(e -> confirm());
        field.addActionListener(e -> confirm());
        later.addActionListener(e -> dispose());
        setContentPane(root);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    void confirm() {
        String v = field.getText().trim();
        if (!StationRule.valid(v)) {
            problem.setText(StationRule.HINT);
            Sounds.error();
            return;
        }
        name = v;
        dispose();
    }

    /** The chosen name, or null when the operator postponed. */
    String name() {
        return name;
    }
}
