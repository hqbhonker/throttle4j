package com.throttle4j.spring.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WindowParserTest {

    @Test
    void parsesMilliseconds() {
        assertEquals(500L, WindowParser.parseToMillis("500ms"));
        assertEquals(1L, WindowParser.parseToMillis("1ms"));
    }

    @Test
    void parsesSeconds() {
        assertEquals(1000L, WindowParser.parseToMillis("1s"));
        assertEquals(30_000L, WindowParser.parseToMillis("30s"));
    }

    @Test
    void parsesMinutes() {
        assertEquals(60_000L, WindowParser.parseToMillis("1m"));
        assertEquals(120_000L, WindowParser.parseToMillis("2m"));
    }

    @Test
    void parsesHours() {
        assertEquals(3_600_000L, WindowParser.parseToMillis("1h"));
        assertEquals(7_200_000L, WindowParser.parseToMillis("2h"));
    }

    @Test
    void parsesDays() {
        assertEquals(86_400_000L, WindowParser.parseToMillis("1d"));
    }

    @Test
    void bareNumberMeansMillis() {
        assertEquals(1500L, WindowParser.parseToMillis("1500"));
    }

    @Test
    void caseInsensitive() {
        assertEquals(1000L, WindowParser.parseToMillis("1S"));
        assertEquals(60_000L, WindowParser.parseToMillis("1M"));
    }

    @Test
    void rejectsInvalid() {
        assertThrows(IllegalArgumentException.class, () -> WindowParser.parseToMillis("abc"));
        assertThrows(IllegalArgumentException.class, () -> WindowParser.parseToMillis(""));
        assertThrows(IllegalArgumentException.class, () -> WindowParser.parseToMillis("1x"));
        assertThrows(IllegalArgumentException.class, () -> WindowParser.parseToMillis(null));
        assertThrows(IllegalArgumentException.class, () -> WindowParser.parseToMillis("0s"));
    }
}
