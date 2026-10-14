package app.ui;

import app.config.Settings;
import app.core.Day;
import app.core.Ledger;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Properties;

// headless pictures of the screens, to LOOK at before calling a change done:
// green tests do not see a panel squashed to 1 px. one flavor per run, because
// the theme is read once at startup (static final), exactly like the app.
//
//   xvfb-run java -cp out app.ui.Snapshot mocha     -> out/snapshot/mocha-*.png
//   xvfb-run java -cp out app.ui.Snapshot latte
public final class Snapshot {

    static final String P = "209531.001_01-01_26120018";
    static final String FREE =
        "CA HVCO 50 T 048 3-2; M-CA HVCO 50 T 048 3-2  Rev.04; Serial No.: 000066076 ; 2023/12/11;A";

    public static void main(String[] args) throws Exception {
        String flavor = args.length > 0 ? args[0] : "mocha";
        File outDir = new File(args.length > 1 ? args[1] : "out/snapshot");
        Files.createDirectories(outDir.toPath());
        Path home = Files.createTempDirectory("rqr-snap");
        System.setProperty("user.home", home.toString());
        Properties p = new Properties();
        p.setProperty("station.name", "L2");
        p.setProperty("ui.flavor", flavor);
        p.setProperty("sound.on", "false");
        p.setProperty("scan.qr1Pattern", "[0-9]{6}\\.[0-9]{3}_.*");
        p.setProperty("scan.qr2Pattern", "[^_]+");
        try (FileOutputStream out = new FileOutputStream(new File(home.toFile(), ".registro_qr.properties"))) {
            p.store(out, "snapshot");
        }

        StepClock clock = new StepClock("2026-10-13T12:00:41");
        Ledger ledger = new Ledger(Settings.getInstance(), clock);
        ledger.open();
        fill(ledger, clock);

        final MainWindow[] w = new MainWindow[1];
        SwingUtilities.invokeAndWait(() -> {
            w[0] = new MainWindow(ledger);
            w[0].setSize(MainWindow.DEFAULT_W, MainWindow.DEFAULT_H);
            w[0].validate();
        });
        Thread.sleep(400);
        MainWindow win = w[0];

        // 01: the scan card after a good pair
        SwingUtilities.invokeAndWait(() -> {
            win.scanPanel.tfQr1.setText(P + "42");
            win.scanPanel.tfQr2.setText("900612202601310118");
            win.scanPanel.accept();
            win.tablePanel.showRow(10);
        });
        shoot(win, new File(outDir, flavor + "-01-scansione.png"));

        // 02a: a label already recorded today
        SwingUtilities.invokeAndWait(() -> {
            win.scanPanel.tfQr1.setText(P + "41");
            win.scanPanel.tfQr2.setText("900612202601310118");
            win.scanPanel.accept();
        });
        shoot(win, new File(outDir, flavor + "-02-doppione.png"));

        // 02b: the lot fired into QR 1
        SwingUtilities.invokeAndWait(() -> {
            win.scanPanel.tfQr1.setText("900612202601310118");
            win.scanPanel.tfQr2.setText(P + "43");
            win.scanPanel.accept();
        });
        shoot(win, new File(outDir, flavor + "-02-invertiti.png"));

        // 03: a range of 30, previewed, not yet added
        SwingUtilities.invokeAndWait(() -> {
            win.scanPanel.clearFields();
            win.modeSelector.select(1);
            win.rangePanel.tfFirst.setText(P + "50");
            win.rangePanel.tfLot.setText("900612202601310119");
            win.rangePanel.spCount.setValue(30);
        });
        shoot(win, new File(outDir, flavor + "-03-intervallo.png"));
        SwingUtilities.invokeAndWait(() -> win.rangePanel.add());
        shoot(win, new File(outDir, flavor + "-03b-intervallo-aggiunto.png"));

        // 04: edit a row never exported, then one already exported
        shootDialog(() -> {
            EditDialog d = new EditDialog(win, ledger, ledger.day().row(9));
            d.tfLot.setText("900612202601310118");
            d.tfReason.setText("lotto sparato dal cartellino sbagliato");
            return d;
        }, new File(outDir, flavor + "-04-modifica.png"));
        shootDialog(() -> {
            EditDialog d = new EditDialog(win, ledger, ledger.day().row(4));
            d.tfLot.setText("900612202601310119");
            return d;
        }, new File(outDir, flavor + "-04b-modifica-esportata.png"));

        // 06: the export, before and after (the copy folder is missing: pending)
        Settings.getInstance().set(Settings.DIR_COPY, new File(home.toFile(), "chiavetta").getPath());
        final ExportDialog[] ex = new ExportDialog[1];
        SwingUtilities.invokeAndWait(() -> {
            ex[0] = new ExportDialog(win, ledger, false);
            try {
                paint(ex[0].getContentPane(), new File(outDir, flavor + "-06-esporta.png"));
                ex[0].doExport();
                ex[0].pack();
                paint(ex[0].getContentPane(), new File(outDir, flavor + "-06b-esportato.png"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            ex[0].dispose();
        });
        shoot(win, new File(outDir, flavor + "-06c-copia-in-sospeso.png"));

        // 10: history, after a regeneration
        SwingUtilities.invokeAndWait(() -> win.openSettings("Storico"));
        Thread.sleep(300);
        SwingUtilities.invokeAndWait(() -> {
            win.settingsWindow().showTab("Storico");
            win.history.regenerateInto = new File(home.toFile(), "rigenerati");
            win.history.regenerate();
            try {
                paint(win.settingsWindow().getContentPane(), new File(outDir, flavor + "-10-storico.png"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // 05: the restart after a crash, the next morning, yesterday not exported
        clock.set("2026-10-14T06:30:00");
        SwingUtilities.invokeAndWait(() -> {
            win.settingsWindow().setVisible(false);
            ledger.add(P + "95", "900612202601310120");
        });
        final MainWindow[] w2 = new MainWindow[1];
        SwingUtilities.invokeAndWait(() -> {
            w2[0] = new MainWindow(ledger, true);
            w2[0].setSize(MainWindow.DEFAULT_W, MainWindow.DEFAULT_H);
            w2[0].validate();
        });
        shoot(w2[0], new File(outDir, flavor + "-05-ripresa.png"));

        // 09: settings
        SwingUtilities.invokeAndWait(() -> win.openSettings("Codici"));
        Thread.sleep(300);
        SwingUtilities.invokeAndWait(() -> {
            try {
                paint(win.settingsWindow().getContentPane(), new File(outDir, flavor + "-09-impostazioni.png"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        System.out.println("snapshots in " + outDir.getPath());
        System.exit(0);
    }

    /** A day that looks like the mockup's: exported rows, a correction, a deletion. */
    static void fill(Ledger l, StepClock c) {
        c.set("2026-10-13T07:02:44");
        l.add(P + "35", FREE);
        l.addRange(Arrays.asList(P + "36", P + "37"), "900612202601310118");
        c.set("2026-10-13T08:30:41");
        l.add(P + "38", "900612202601310118");
        Day d = l.day();
        c.set("2026-10-13T12:00:41");
        d.recordExport(1, "2026-10-13T12:00:41", "d570b815243882baae5e2a86912bb220f2440fb74c91e472f001b696f8a788db",
                       "registro_L2_2026-10-13_01.csv", false, d.pendingExport());
        c.set("2026-10-13T14:30:41");
        l.modify(4, P + "38", "900612202601310119", "cartellino sbagliato");
        c.set("2026-10-13T14:30:58");
        l.add(P + "39", "900612202601310118");
        c.set("2026-10-13T14:31:20");
        l.add(P + "90", "900612202601310118");
        l.delete(6);
        c.set("2026-10-13T14:31:22");
        l.add(P + "91", "900612202601310118");
        l.modify(7, P + "91", "900612202601310117", "");
        c.set("2026-10-13T14:31:40");
        l.add(P + "40", "900612202601310117");
        c.set("2026-10-13T14:31:55");
        l.add(P + "41", "900612202601310118");
        c.set("2026-10-13T14:32:07");
    }

    interface DialogMaker {
        JDialog make();
    }

    private static void shootDialog(DialogMaker m, File f) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JDialog d = m.make();
            d.pack();
            try {
                paint(d.getContentPane(), f);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            d.dispose();
        });
    }

    static void shoot(JFrame w, File f) throws Exception {
        Thread.sleep(150);
        SwingUtilities.invokeAndWait(() -> {
            try {
                paint(w.getRootPane(), f);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    static void paint(Container c, File f) throws Exception {
        c.validate();
        BufferedImage img = new BufferedImage(Math.max(1, c.getWidth()), Math.max(1, c.getHeight()),
                                              BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        c.printAll(g);
        g.dispose();
        ImageIO.write(img, "png", f);
    }

    /** A clock the snapshot moves by hand, a second per read so events differ. */
    static final class StepClock extends Clock {
        private Instant now;

        StepClock(String iso) { set(iso); }

        void set(String iso) { now = LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public synchronized Instant instant() {
            Instant t = now;
            now = now.plusMillis(1000);
            return t;
        }
    }
}
