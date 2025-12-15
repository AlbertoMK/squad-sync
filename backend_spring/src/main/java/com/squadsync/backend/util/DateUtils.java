package com.squadsync.backend.util;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class DateUtils {
    public static final ZoneId MADRID_ZONE = ZoneId.of("Europe/Madrid");
    private static Clock clock = Clock.system(MADRID_ZONE);

    public static LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    // For testing purposes
    public static void setClock(Clock newClock) {
        clock = newClock;
    }

    public static void resetClock() {
        clock = Clock.system(MADRID_ZONE);
    }
}
