package com.pulse.keyboard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Stateless, offline English/Filipino predictive typing helpers. */
public final class T9Engine {
    private static final String[] LETTERS = {
        "", "", "abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"
    };
    private T9Engine() {}

    public static String encode(String word) {
        if (word == null) return "";
        StringBuilder result = new StringBuilder();
        for (char ch : word.toLowerCase(Locale.ROOT).toCharArray()) {
            for (int number = 2; number <= 9; number++) {
                if (LETTERS[number].indexOf(ch) >= 0) {
                    result.append(number);
                    break;
                }
            }
        }
        return result.toString();
    }

    /** Exact-length matches first, followed by words extending these digits. */
    public static List<String> suggest(String digits, Collection<String> dictionary, int limit) {
        ArrayList<String> output = new ArrayList<>();
        if (digits == null || !digits.matches("[2-9]+") || dictionary == null || limit <= 0) {
            return output;
        }
        LinkedHashSet<String> exact = new LinkedHashSet<>();
        LinkedHashSet<String> longer = new LinkedHashSet<>();
        for (String value : dictionary) {
            if (value == null) continue;
            String word = value.toLowerCase(Locale.ROOT).trim();
            if (!word.matches("[a-z]+")) continue;
            String coded = encode(word);
            if (coded.equals(digits)) exact.add(word);
            else if (coded.startsWith(digits)) longer.add(word);
        }
        for (String word : exact) {
            if (output.size() >= limit) return output;
            output.add(word);
        }
        for (String word : longer) {
            if (output.size() >= limit) return output;
            output.add(word);
        }
        return output;
    }

    public static List<String> completePrefix(String prefix, Collection<String> dictionary, int limit) {
        ArrayList<String> output = new ArrayList<>();
        if (prefix == null || prefix.length() < 2 || dictionary == null || limit <= 0) {
            return output;
        }
        String query = prefix.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> matches = new LinkedHashSet<>();
        for (String value : dictionary) {
            if (value == null) continue;
            String word = value.toLowerCase(Locale.ROOT).trim();
            if (word.startsWith(query) && !word.equals(query)) matches.add(word);
        }
        for (String word : matches) {
            if (output.size() >= limit) return output;
            output.add(word);
        }
        return output;
    }
}
