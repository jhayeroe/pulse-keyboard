package com.pulse.keyboard;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class T9EngineTest {
    @Test public void englishEncoding() {
        assertEquals("43556", T9Engine.encode("Hello"));
    }
    @Test public void tagalogEncoding() {
        assertEquals("5868782", T9Engine.encode("kumusta"));
    }
    @Test public void ignoresPunctuationAndNumbers() {
        assertEquals("43556", T9Engine.encode("(hello!) 10"));
    }
    @Test public void nullIsSafe() {
        assertEquals("", T9Engine.encode(null));
    }
    @Test public void exactSuggestionsFirst() {
        List<String> result = T9Engine.suggest("4663",
                Arrays.asList("goodbye", "home", "good", "gone"), 3);
        assertEquals("home", result.get(0));
        assertTrue(result.contains("good"));
    }
    @Test public void matchesLongerWords() {
        assertEquals(Arrays.asList("hello", "help"),
                T9Engine.suggest("435", Arrays.asList("hello", "help", "world"), 3));
    }
    @Test public void invalidDigitsReturnNoPredictions() {
        assertTrue(T9Engine.suggest("101", Arrays.asList("hello"), 3).isEmpty());
    }
    @Test public void suggestionLimitAndDeduplication() {
        assertEquals(Arrays.asList("hello"), T9Engine.suggest("43556",
                Arrays.asList("hello", "hello", "hellos"), 1));
    }
    @Test public void completionsRequireAtLeastTwoLetters() {
        assertTrue(T9Engine.completePrefix("h", Arrays.asList("hello"), 3).isEmpty());
        assertEquals(Arrays.asList("hello", "help"),
                T9Engine.completePrefix("hel",
                        Arrays.asList("hello", "help", "world"), 2));
    }
}
