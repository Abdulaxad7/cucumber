package com.example.authservice.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A UTC test clock (phase-4 D7, AC-4.2, AC-4.3): it reads like the service clock (system UTC, microsecond ticks)
 * until a test {@link #set sets} it to a fixed instant, and {@link #reset} returns it to the system time.
 */
public final class AdjustableClock extends Clock {

    private static final Clock SYSTEM = Clock.tick(Clock.systemUTC(), Duration.ofNanos(1000));

    private volatile Instant fixed;

    public void set(Instant instant) {
        this.fixed = instant;
    }

    public void reset() {
        this.fixed = null;
    }

    @Override
    public Instant instant() {
        Instant current = fixed;
        return current != null ? current : SYSTEM.instant();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("The service clock is always UTC");
    }
}
