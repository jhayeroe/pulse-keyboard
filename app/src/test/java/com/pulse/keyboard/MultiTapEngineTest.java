package com.pulse.keyboard;

import org.junit.Test;
import static org.junit.Assert.*;

public class MultiTapEngineTest {
    @Test public void rapidRepeatedKeyCyclesInsteadOfRepeatingText() {
        MultiTapEngine engine = new MultiTapEngine();
        assertEquals("m", engine.onDigit(6, 1000L, false).letter);
        assertEquals("n", engine.onDigit(6, 1100L, false).letter);
        assertEquals("o", engine.onDigit(6, 1200L, false).letter);
        assertEquals("m", engine.onDigit(6, 1300L, false).letter);
    }

    @Test public void distinctKeyStartsNewLetter() {
        MultiTapEngine engine = new MultiTapEngine();
        assertEquals("a", engine.onDigit(2, 1000L, false).letter);
        MultiTapEngine.Tap tap = engine.onDigit(6, 1100L, false);
        assertFalse(tap.cyclesExisting);
        assertEquals("m", tap.letter);
        assertTrue(engine.onDigit(6, 1200L, false).cyclesExisting);
    }

    @Test public void arrowConfirmationAllowsSameDigitAsNewLetter() {
        MultiTapEngine engine = new MultiTapEngine();
        engine.onDigit(6, 1000L, false);
        assertEquals("n", engine.onDigit(6, 1075L, false).letter);
        engine.confirm();
        MultiTapEngine.Tap tap = engine.onDigit(6, 1100L, false);
        assertEquals("m", tap.letter);
        assertFalse(tap.cyclesExisting);
    }

    @Test public void timeoutStartsNewLetter() {
        MultiTapEngine engine = new MultiTapEngine();
        assertEquals("a", engine.onDigit(2, 1000L, false).letter);
        MultiTapEngine.Tap tap = engine.onDigit(2, 2301L, false);
        assertEquals("a", tap.letter);
        assertFalse(tap.cyclesExisting);
    }

    @Test public void capitalizationPersistsAcrossCycle() {
        MultiTapEngine engine = new MultiTapEngine();
        assertEquals("A", engine.onDigit(2, 1000L, true).letter);
        assertEquals("B", engine.onDigit(2, 1100L, false).letter);
        assertEquals("C", engine.onDigit(2, 1200L, false).letter);
    }

    @Test public void exampleAnoWithManualSeparator() {
        MultiTapEngine engine = new MultiTapEngine();
        String a = engine.onDigit(2, 0L, true).letter;
        engine.confirm();
        engine.onDigit(6, 20L, false);
        String n = engine.onDigit(6, 60L, false).letter;
        engine.confirm(); // Right arrow confirms N before another 6-key letter.
        engine.onDigit(6, 100L, false);
        engine.onDigit(6, 130L, false);
        String o = engine.onDigit(6, 160L, false).letter;
        assertEquals("Ano", a + n + o);
    }
}
