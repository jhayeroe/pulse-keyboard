package com.pulse.keyboard;

/**
 * Predictable 3x4 classic phone multi-tap state machine.
 *
 * The Android IME displays each Tap through setComposingText, so cycling
 * replaces the composing letter rather than deleting surrounding text.
 * Calling confirm() commits the active letter and allows another letter
 * from the same numeric key without waiting.
 */
public final class MultiTapEngine {
    public static final long CYCLE_WINDOW_MS = 1200L;
    private static final String[] LETTERS = {
        "", ".,!?':;@", "abc", "def", "ghi", "jkl",
        "mno", "pqrs", "tuv", "wxyz"
    };

    public static final class Tap {
        public final String letter;
        public final boolean cyclesExisting;
        Tap(String letter, boolean cyclesExisting) {
            this.letter = letter;
            this.cyclesExisting = cyclesExisting;
        }
    }

    private int pendingDigit = -1;
    private int index = 0;
    private long lastTapTime = Long.MIN_VALUE;
    private boolean uppercase;

    public Tap onDigit(int digit, long nowMillis, boolean shift) {
        if (digit < 1 || digit > 9) {
            throw new IllegalArgumentException("Numeric key must be 1 to 9");
        }
        boolean cycle = pendingDigit == digit
                && nowMillis >= lastTapTime
                && nowMillis - lastTapTime <= CYCLE_WINDOW_MS;
        if (cycle) {
            index = (index + 1) % LETTERS[digit].length();
        } else {
            index = 0;
            uppercase = shift;
        }
        pendingDigit = digit;
        lastTapTime = nowMillis;
        char ch = LETTERS[digit].charAt(index);
        return new Tap(String.valueOf(uppercase ? Character.toUpperCase(ch) : ch),
                cycle);
    }

    public boolean hasPending() {
        return pendingDigit >= 1;
    }

    public void confirm() {
        pendingDigit = -1;
        index = 0;
        lastTapTime = Long.MIN_VALUE;
    }
}
