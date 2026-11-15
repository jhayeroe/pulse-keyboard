package com.pulse.keyboard;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class T9TagalogTest {
    @Test public void sequenceForTagalogExample() {
        assertEquals("266", T9Engine.encode("ano"));
        assertEquals("62", T9Engine.encode("na"));
        assertEquals("7273", T9Engine.encode("pare"));
        assertEquals("569", T9Engine.encode("koy"));
    }

    @Test public void exactSuggestionsPreferIntendedTagalogWords() {
        assertEquals("ano", T9Engine.suggest("266", Arrays.asList("ano", "another", "and"), 3).get(0));
        assertEquals("pare", T9Engine.suggest("7273", Arrays.asList("pare", "party", "pareng"), 3).get(0));
        assertEquals("koy", T9Engine.suggest("569", Arrays.asList("koy", "joy"), 3).get(0));
    }
}
