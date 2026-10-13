package app.config;

// Adapted from AutoFillSuite src/app/config/SettingsManager.java — keep behaviour in sync.

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Singleton holding the workstation settings in a .properties file in
 * user.home. Write-through: callers save after every change, and the save is
 * atomic (sibling temp file + rename), so a crash mid-write leaves the old
 * file intact.
 */
public final class Settings {

    private static Settings instance;
    private final Properties props = new Properties();
    private final File file;

    private Settings() {
        file = new File(System.getProperty("user.home"), ".registro_qr.properties");
        load();
    }

    public static synchronized Settings getInstance() {
        if (instance == null) instance = new Settings();
        return instance;
    }

    public File file() {
        return file;
    }

    public String get(String key, String def) {
        return props.getProperty(key, def);
    }

    public int getInt(String key, int def) {
        try { return Integer.parseInt(props.getProperty(key, String.valueOf(def)).trim()); }
        catch (NumberFormatException e) { return def; }
    }

    public boolean getBool(String key, boolean def) {
        return Boolean.parseBoolean(props.getProperty(key, String.valueOf(def)).trim());
    }

    public boolean has(String key) {
        return props.containsKey(key);
    }

    public void set(String key, Object value) {
        props.setProperty(key, String.valueOf(value));
    }

    public void remove(String key) {
        props.remove(key);
    }

    /** Atomic: a crash mid-write can never leave a half-written settings file. */
    public synchronized boolean save() {
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                props.store(fos, "Registro QR - settings");
                fos.getFD().sync();
            }
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                // some filesystems cannot do atomic renames: plain replace
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            System.err.println("[Settings] Cannot save: " + e.getMessage());
            if (tmp.exists() && !tmp.delete()) tmp.deleteOnExit();
            return false;
        }
    }

    private void load() {
        if (!file.exists()) return;
        try (FileInputStream fis = new FileInputStream(file)) {
            props.load(fis);
        } catch (IOException e) {
            System.err.println("[Settings] Cannot load: " + e.getMessage());
        }
    }

    // ── folders ──────────────────────────────────────────────────────────
    /** Default data folder: journals, export archive, lock. */
    public static File defaultDataDir() {
        return new File(System.getProperty("user.home"), "RegistroQR");
    }

    public File dataDir() {
        String d = get(DIR_DATA, "").trim();
        return d.isEmpty() ? defaultDataDir() : new File(d);
    }

    public File exportDir() {
        String d = get(DIR_EXPORT, "").trim();
        return d.isEmpty() ? new File(dataDir(), "export") : new File(d);
    }

    /** null when no copy folder is configured. */
    public File copyDir() {
        String d = get(DIR_COPY, "").trim();
        return d.isEmpty() ? null : new File(d);
    }

    // ── keys ─────────────────────────────────────────────────────────────
    public static final String STATION        = "station.name";     // postazione, required

    public static final String UI_FLAVOR      = "ui.flavor";        // mocha | latte
    public static final String UI_WIN_X       = "ui.winX";
    public static final String UI_WIN_Y       = "ui.winY";
    public static final String UI_WIN_W       = "ui.winW";
    public static final String UI_WIN_H       = "ui.winH";
    public static final String UI_MODE        = "ui.mode";          // 0 scan, 1 range

    public static final String QR1_PATTERN    = "scan.qr1Pattern";  // regex, "" = no check
    public static final String QR2_PATTERN    = "scan.qr2Pattern";
    public static final String AUTO_SWAP      = "scan.autoSwap";
    public static final String DUP_GUARD      = "scan.dupGuard";
    public static final String TAIL_DIGITS    = "range.tailDigits"; // 1-9, default 3

    public static final String SOUND_ON       = "sound.on";
    public static final String SOUND_VOLUME   = "sound.volume";     // 0-100

    public static final String DIR_DATA       = "dir.data";
    public static final String DIR_EXPORT     = "dir.export";
    public static final String DIR_COPY       = "dir.copy";

    /** true while the app runs; false only after a clean exit. */
    public static final String RUNNING        = "app.running";
}
