package app.core;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;

/**
 * One instance per data folder. Two processes appending to the same journal
 * is the one way left to corrupt it, so the second one is refused.
 */
public final class DataLock implements Closeable {

    /**
     * Lock files held by THIS process. Checked before opening a second channel:
     * on POSIX systems closing any channel on the file drops every lock the
     * process holds on it, so a refused second attempt would silently release
     * the first instance's lock.
     */
    private static final Set<String> HELD = new HashSet<>();

    private final FileChannel channel;
    private final FileLock lock;
    private final String key;

    private DataLock(FileChannel channel, FileLock lock, String key) {
        this.channel = channel;
        this.lock = lock;
        this.key = key;
    }

    /** The lock, or null when another instance holds it. */
    public static DataLock acquire(File dataDir) throws IOException {
        Files.createDirectories(dataDir.toPath());
        File f = new File(dataDir, ".lock");
        String key = f.getCanonicalPath();
        synchronized (HELD) {
            if (HELD.contains(key)) return null;
            FileChannel ch = FileChannel.open(f.toPath(),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try {
                FileLock l = ch.tryLock();
                if (l == null) {
                    ch.close();
                    return null;
                }
                HELD.add(key);
                return new DataLock(ch, l, key);
            } catch (OverlappingFileLockException e) {
                ch.close();
                return null;
            }
        }
    }

    @Override public void close() {
        synchronized (HELD) {
            try { lock.release(); } catch (IOException ignored) {}
            try { channel.close(); } catch (IOException ignored) {}
            HELD.remove(key);
        }
    }
}
