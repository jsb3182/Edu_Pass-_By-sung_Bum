package kr.go.edu.boundary.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ApplicationNumbersTest {
    @Test
    void normalizesWhitespaceWidthCaseAndSeparators() {
        assertEquals("AB1234", ApplicationNumbers.clean("  Ａb-12 / 34 "));
        assertEquals(ApplicationNumbers.clean("A B-1"), ApplicationNumbers.clean("a_b/1"));
    }

    @Test
    void refusesEmptyNumberAfterNormalization() {
        assertThrows(IllegalArgumentException.class, () -> ApplicationNumbers.clean(" - / _ "));
    }
}

