package app.core;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock the tests move by hand: timestamps are part of what they compare. */
final class TestClock extends Clock {

    private Instant now;
    private final long stepMillis;

    TestClock(String isoLocal) {
        this(isoLocal, 0);
    }

    /** stepMillis > 0: every read advances the clock, so events get distinct times. */
    TestClock(String isoLocal, long stepMillis) {
        set(isoLocal);
        this.stepMillis = stepMillis;
    }

    void set(String isoLocal) {
        now = LocalDateTime.parse(isoLocal).toInstant(ZoneOffset.UTC);
    }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }

    @Override public synchronized Instant instant() {
        Instant t = now;
        if (stepMillis > 0) now = now.plusMillis(stepMillis);
        return t;
    }
}
