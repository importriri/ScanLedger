package app.core;

// Adapted from AutoFillSuite src/app/core/WindowGuard.java — keep behaviour in sync.

import java.awt.Rectangle;
import java.util.List;

/**
 * Pure geometry, no Swing: where a remembered window may be put back. Only
 * the reachability check is needed here — this window is not always-on-top and
 * never covers a robot's target.
 */
public final class WindowGuard {

    private WindowGuard() {}

    /**
     * True if enough of the window sits on ANY screen for the operator to
     * grab it: a title-bar-sized sliver of the TOP edge (at least 100x20 px)
     * must be visible somewhere. A window remembered on an unplugged monitor
     * fails this and gets re-placed instead of opening off-screen.
     */
    public static boolean reachableOnAny(Rectangle win, List<Rectangle> screens) {
        Rectangle grab = new Rectangle(win.x, win.y, win.width, 30);
        for (Rectangle s : screens) {
            Rectangle i = grab.intersection(s);
            if (i.width >= 100 && i.height >= 20) return true;
        }
        return false;
    }
}
