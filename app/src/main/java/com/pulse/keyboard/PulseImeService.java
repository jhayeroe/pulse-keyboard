package com.pulse.keyboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Offline compact 3x4 Android input method. Multi-tap edits an Android
 * composing span; it never deletes the previous character to cycle a key.
 * The globe button opens Android's system input-method picker.
 */
public final class PulseImeService extends InputMethodService {
    private static final String[] KEYS = {
        "", ".,!?':;@", "abc", "def", "ghi", "jkl",
        "mno", "pqrs", "tuv", "wxyz"
    };
    private static final String[] EMOJI = {
        "😀", "😂", "😊", "😍", "🥰", "😎", "😭", "😢",
        "👍", "🙏", "❤️", "🔥", "🎉", "✅", "🤔", "💯",
        "👋", "😅", "😁", "🙌", "🤝", "💪", "👌", "😴"
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MultiTapEngine multiTapEngine = new MultiTapEngine();
    private Runnable repeatedDelete;
    private Runnable settleTap;
    private FrameLayout frame;
    private LinearLayout body;
    private final TextView[] suggestionViews = new TextView[3];
    private SharedPreferences prefs;
    private EditorInfo editor;
    private boolean t9Mode;
    private boolean numericMode;
    private boolean shift;
    private boolean t9Uppercase;
    private boolean passwordField;
    private boolean darkTheme;
    private String panel = "keys";
    private String t9Digits = "";
    /** Letter already committed to the editor and eligible for cycling. */
    private String pendingLetter = "";
    private int backdrop;
    private int face;
    private int toolFace;
    private int textColor;
    private int subColor;
    private int borderColor;
    private int accent;

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(WordRepository.PREFS, MODE_PRIVATE);
        // v0.3 migration: use the requested old-phone ABC behavior on upgrade.
        // Mode selected after this migration is still remembered.
        if (prefs.getInt("typing_mode_revision", 0) < 3) {
            prefs.edit().putBoolean("t9_mode", false)
                    .putInt("typing_mode_revision", 3).apply();
        }
        t9Mode = prefs.getBoolean("t9_mode", false);
    }

    @Override public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @Override public View onCreateInputView() {
        frame = new FrameLayout(this);
        frame.setClipChildren(false);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        frame.addView(body);
        buildKeyboard();
        return frame;
    }

    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        editor = attribute;
        passwordField = isPassword(attribute);
        int kind = attribute == null ? InputType.TYPE_CLASS_TEXT
                : (attribute.inputType & InputType.TYPE_MASK_CLASS);
        numericMode = kind == InputType.TYPE_CLASS_NUMBER
                || kind == InputType.TYPE_CLASS_PHONE
                || kind == InputType.TYPE_CLASS_DATETIME;
        t9Mode = prefs.getBoolean("t9_mode", false);
        clearState();
        shift = !passwordField && !numericMode;
        panel = "keys";
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        // Only auto-capitalize when starting an empty field/sentence.
        if (!passwordField && !numericMode) {
            InputConnection input = getCurrentInputConnection();
            CharSequence before = input == null ? null : input.getTextBeforeCursor(80, 0);
            if (before != null) {
                String s = before.toString();
                String trimmed = s.trim();
                shift = trimmed.isEmpty() || trimmed.endsWith(".")
                        || trimmed.endsWith("!") || trimmed.endsWith("?")
                        || trimmed.endsWith("\n");
            }
        }
        buildKeyboard();
    }

    @Override public void onFinishInput() {
        stopDeleteRepeat();
        stopTapTimer();
        multiTapEngine.confirm();
        t9Digits = "";
        super.onFinishInput();
    }

    @Override public void onDestroy() {
        stopDeleteRepeat();
        stopTapTimer();
        super.onDestroy();
    }

    private boolean isPassword(EditorInfo info) {
        if (info == null) return false;
        int kind = info.inputType & InputType.TYPE_MASK_CLASS;
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        if (kind == InputType.TYPE_CLASS_NUMBER) {
            return variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        }
        return kind == InputType.TYPE_CLASS_TEXT
                && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
    }

    private int dp(int amount) {
        return Math.round(amount * getResources().getDisplayMetrics().density);
    }

    private void colors() {
        darkTheme = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        backdrop = darkTheme ? Color.rgb(24, 29, 39) : Color.rgb(225, 230, 237);
        face = darkTheme ? Color.rgb(47, 56, 73) : Color.WHITE;
        toolFace = darkTheme ? Color.rgb(38, 45, 59) : Color.rgb(236, 241, 247);
        textColor = darkTheme ? Color.WHITE : Color.rgb(23, 37, 56);
        subColor = darkTheme ? Color.rgb(158, 173, 196) : Color.rgb(105, 116, 135);
        borderColor = darkTheme ? Color.rgb(74, 86, 107) : Color.rgb(208, 217, 228);
        accent = darkTheme ? Color.rgb(165, 126, 250) : Color.rgb(115, 63, 203);
    }

    private GradientDrawable background(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(10));
        shape.setStroke(dp(1), borderColor);
        return shape;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER);
        return layout;
    }

    private TextView key(String label, String description, int size, int height,
                         boolean utility, Runnable action) {
        TextView text = new TextView(this);
        int separator = label.indexOf("\n");
        if (separator > 0 && separator < label.length() - 1) {
            SpannableString styled = new SpannableString(label);
            styled.setSpan(new StyleSpan(Typeface.BOLD), 0, separator,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            styled.setSpan(new RelativeSizeSpan(0.62f), separator + 1,
                    label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            styled.setSpan(new ForegroundColorSpan(subColor), separator + 1,
                    label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setText(styled);
        } else {
            text.setText(label);
        }
        text.setGravity(Gravity.CENTER);
        text.setTextSize(size);
        text.setIncludeFontPadding(false);
        text.setLineSpacing(dp(1), 1f);
        text.setTextColor(textColor);
        text.setBackground(background(utility ? toolFace : face));
        text.setContentDescription(description);
        text.setClickable(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(height), 1f);
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        text.setLayoutParams(params);
        text.setOnClickListener(v -> action.run());
        return text;
    }

    private void add(LinearLayout row, String label, String description,
                     int size, int height, boolean utility, Runnable action) {
        row.addView(key(label, description, size, height, utility, action));
    }

    /**
     * The keyboard is always screen-width. The utility panel uses 25% and the
     * phone keypad uses 75%, regardless of portrait or landscape orientation.
     * The percentage setting adjusts the two panels, NOT the overall width.
     */
    private void buildKeyboard() {
        if (frame == null || body == null) return;
        colors();
        frame.setBackgroundColor(backdrop);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        body.setLayoutParams(params);
        body.setPadding(dp(2), dp(5), dp(2), dp(6));
        body.setBackgroundColor(backdrop);
        body.removeAllViews();

        drawToolbar();
        drawSuggestions();

        int height = Math.max(38, Math.min(66, prefs.getInt("key_height_dp", 44)));
        int keypadPercent = Math.max(65, Math.min(85,
                prefs.getInt("width_percent", 75)));
        LinearLayout split = row();
        split.setGravity(Gravity.BOTTOM);

        LinearLayout utilities = new LinearLayout(this);
        utilities.setOrientation(LinearLayout.VERTICAL);
        utilities.setPadding(0, 0, dp(2), 0);
        LinearLayout keypad = new LinearLayout(this);
        keypad.setOrientation(LinearLayout.VERTICAL);
        keypad.setPadding(dp(2), 0, 0, 0);

        drawLeftPanel(utilities, height);
        if ("emoji".equals(panel)) drawEmoji(keypad);
        else if ("symbols".equals(panel)) drawSymbols(keypad);
        else drawNumberGrid(keypad);

        LinearLayout.LayoutParams utilityParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 100 - keypadPercent);
        LinearLayout.LayoutParams keypadParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, keypadPercent);

        boolean right = prefs.getBoolean("align_right", true);
        if (right) {
            split.addView(utilities, utilityParams);
            split.addView(keypad, keypadParams);
        } else {
            split.addView(keypad, keypadParams);
            split.addView(utilities, utilityParams);
        }
        body.addView(split);
        updateSuggestions();
    }

    /** The top row spans the full width and contains only actionable controls. */
    private void drawToolbar() {
        LinearLayout line = row();
        add(line, "PULSE", "Choose another keyboard", 12, 35, true,
                this::showKeyboardPicker);
        add(line, numericMode ? "ABC" : "123", "Toggle number typing", 13, 35, true,
                () -> {
                    finishT9();
                    finishMultiTap();
                    numericMode = !numericMode;
                    panel = "keys";
                    buildKeyboard();
                });
        add(line, "⇆", "Switch keypad side", 18, 35, true, () -> {
            boolean right = !prefs.getBoolean("align_right", true);
            prefs.edit().putBoolean("align_right", right).apply();
            buildKeyboard();
        });
        add(line, "+", "Save current word to personal dictionary", 19, 35, true,
                this::addCurrentWord);
        add(line, "⌄", "Hide keyboard", 22, 35, true,
                () -> requestHideSelf(0));
        body.addView(line);
    }

    private void drawSuggestions() {
        LinearLayout suggestions = row();
        for (int i = 0; i < suggestionViews.length; i++) {
            final int at = i;
            TextView suggestion = key("·", "Word suggestion " + (i + 1),
                    13, 31, true, () -> acceptSuggestion(at));
            suggestion.setTextColor(accent);
            suggestionViews[i] = suggestion;
            suggestions.addView(suggestion);
        }
        body.addView(suggestions);
    }

    /**
     * Five compact rows fill the same vertical space as four telephone rows.
     * The panel has full-width ABC/T9, then four 2-column utility rows.
     * At 25% width, icons (rather than long captions) stay touchable on phones.
     */
    private void drawLeftPanel(LinearLayout target, int keyHeight) {
        int rightRow = keyHeight + 4;
        int utilityHeight = Math.max(30, (4 * rightRow) / 5 - 4);

        LinearLayout mode = row();
        add(mode, passwordField ? "SECURE" : (t9Mode ? "T9 / ABC" : "ABC / T9"),
                "Switch ABC multi-tap and T9 predictive typing", 12, utilityHeight,
                true, () -> {
                    if (passwordField) return;
                    finishT9();
                    finishMultiTap();
                    t9Mode = !t9Mode;
                    prefs.edit().putBoolean("t9_mode", t9Mode).apply();
                    panel = "keys";
                    buildKeyboard();
                });
        target.addView(mode);

        LinearLayout caseAndSymbols = row();
        add(caseAndSymbols, shift ? "⇧●" : "⇧", "Shift / uppercase",
                16, utilityHeight, true,
                () -> { finishMultiTap(); shift = !shift; buildKeyboard(); });
        add(caseAndSymbols, "!? #", "Symbols panel",
                12, utilityHeight, true, () -> {
                    finishMultiTap();
                    finishT9();
                    panel = "symbols".equals(panel) ? "keys" : "symbols";
                    buildKeyboard();
                });
        target.addView(caseAndSymbols);

        LinearLayout emojiAndClipboard = row();
        add(emojiAndClipboard, "☺", "Emoji panel", 20, utilityHeight, true,
                () -> {
                    finishMultiTap();
                    finishT9();
                    panel = "emoji".equals(panel) ? "keys" : "emoji";
                    buildKeyboard();
                });
        add(emojiAndClipboard, "⎘", "Paste clipboard", 19, utilityHeight, true,
                this::pasteClipboard);
        target.addView(emojiAndClipboard);

        LinearLayout switchAndSettings = row();
        add(switchAndSettings, "🌐", "Switch to another installed keyboard",
                17, utilityHeight, true, this::showKeyboardPicker);
        add(switchAndSettings, "⚙", "Pulse Keyboard settings",
                19, utilityHeight, true, () -> {
                    finishMultiTap();
                    finishT9();
                    Intent intent = new Intent(this, SettingsActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                });
        target.addView(switchAndSettings);

        LinearLayout cursors = row();
        add(cursors, "←", "Move cursor left", 22, utilityHeight, true,
                () -> moveCursor(false));
        add(cursors, "→", "Confirm letter or move cursor right",
                22, utilityHeight, true, () -> moveCursor(true));
        target.addView(cursors);
    }

    private void drawNumberGrid(LinearLayout target) {
        int height = Math.max(38, Math.min(66, prefs.getInt("key_height_dp", 44)));
        for (int line = 0; line < 3; line++) {
            LinearLayout digitRow = row();
            for (int column = 0; column < 3; column++) {
                int digit = line * 3 + column + 1;
                String letters = KEYS[digit].toUpperCase(Locale.ROOT);
                String display = numericMode ? String.valueOf(digit)
                        : digit + "\n" + (digit == 1 ? ".,!?" : letters);
                TextView button = key(display, "Key " + digit + ": " + letters,
                        numericMode ? 21 : 21, height, false, () -> typeDigit(digit));
                button.setOnLongClickListener(v -> {
                    if (digit == 1 && !numericMode) {
                        finishMultiTap();
                        finishT9();
                        panel = "symbols";
                        buildKeyboard();
                    } else {
                        insertLiteral(String.valueOf(digit));
                    }
                    return true;
                });
                digitRow.addView(button);
            }
            target.addView(digitRow);
        }
        LinearLayout last = row();
        TextView back = key("*  ⌫", "Delete; hold to repeat", 16, height,
                false, this::backspace);
        back.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                stopDeleteRepeat();
                view.setPressed(true);
                backspace();
                repeatedDelete = new Runnable() {
                    @Override public void run() {
                        backspace();
                        handler.postDelayed(this, 85L);
                    }
                };
                handler.postDelayed(repeatedDelete, 440L);
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                view.setPressed(false);
                stopDeleteRepeat();
                return true;
            }
            return true;
        });
        last.addView(back);
        add(last, numericMode ? "0" : "0\nSPACE", "Space; zero in numeric mode",
                16, height, false, this::space);
        add(last, "#  ↵", "Enter or next", 16, height, false, this::enter);
        target.addView(last);
    }

    private void drawSymbols(LinearLayout target) {
        String[] symbols = {
            "@", "#", "$", "%", "&", "*", "(", ")", "-",
            "+", "=", "/", ":", ";", "'", "\"", "?", "!",
            ".", ",", "_", "€", "₱", "✓"
        };
        for (int i = 0; i < symbols.length; i += 6) {
            LinearLayout line = row();
            for (int j = i; j < i + 6; j++) {
                String literal = symbols[j];
                add(line, literal, "Insert " + literal, 18, 41, false,
                        () -> insertLiteral(literal));
            }
            target.addView(line);
        }
    }

    private void drawEmoji(LinearLayout target) {
        ArrayList<String> all = new ArrayList<>();
        String recent = prefs.getString("recent_emojis", "");
        if (!recent.isEmpty()) {
            for (String entry : recent.split("\\|")) {
                if (!entry.isEmpty() && !all.contains(entry)) all.add(entry);
            }
        }
        for (String emoji : EMOJI) if (!all.contains(emoji)) all.add(emoji);
        for (int start = 0; start < 24; start += 6) {
            LinearLayout line = row();
            for (int j = start; j < start + 6; j++) {
                final String chosen = all.get(j);
                add(line, chosen, "Insert emoji", 21, 41, false, () -> {
                    insertLiteral(chosen);
                    rememberEmoji(chosen);
                });
            }
            target.addView(line);
        }
    }

    private void rememberEmoji(String emoji) {
        ArrayList<String> recent = new ArrayList<>();
        recent.add(emoji);
        for (String old : prefs.getString("recent_emojis", "").split("\\|")) {
            if (!old.isEmpty() && !recent.contains(old) && recent.size() < 6) {
                recent.add(old);
            }
        }
        StringBuilder saved = new StringBuilder();
        for (String item : recent) {
            if (saved.length() > 0) saved.append('|');
            saved.append(item);
        }
        prefs.edit().putString("recent_emojis", saved.toString()).apply();
    }

    private void showKeyboardPicker() {
        finishT9();
        finishMultiTap();
        InputMethodManager manager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) manager.showInputMethodPicker();
    }

    private void stopDeleteRepeat() {
        if (repeatedDelete != null) handler.removeCallbacks(repeatedDelete);
        repeatedDelete = null;
    }

    private void stopTapTimer() {
        if (settleTap != null) handler.removeCallbacks(settleTap);
        settleTap = null;
    }

    private void clearState() {
        stopTapTimer();
        t9Digits = "";
        multiTapEngine.confirm();
        pendingLetter = "";
        t9Uppercase = false;
    }

    private void finishT9() {
        if (!t9Digits.isEmpty()) {
            InputConnection input = getCurrentInputConnection();
            if (input != null) input.finishComposingText();
            t9Digits = "";
        }
    }

    private void finishMultiTap() {
        stopTapTimer();
        multiTapEngine.confirm();
        pendingLetter = "";
    }

    private void scheduleTapCommit() {
        stopTapTimer();
        settleTap = () -> {
            multiTapEngine.confirm();
            pendingLetter = "";
            updateSuggestions();
            settleTap = null;
        };
        handler.postDelayed(settleTap, MultiTapEngine.CYCLE_WINDOW_MS);
    }

    private void typeDigit(int digit) {
        InputConnection input = getCurrentInputConnection();
        if (input == null) return;
        if (numericMode) {
            insertLiteral(String.valueOf(digit));
            return;
        }
        if (digit == 1) {
            multiTap(digit);
        } else if (t9Mode && !passwordField) {
            finishMultiTap();
            if (t9Digits.isEmpty()) {
                t9Uppercase = shift;
                shift = false;
            }
            t9Digits += digit;
            updateT9Composition();
            updateSuggestions();
        } else {
            multiTap(digit);
        }
    }

    private void multiTap(int digit) {
        InputConnection input = getCurrentInputConnection();
        if (input == null) return;
        finishT9();
        long now = SystemClock.uptimeMillis();
        MultiTapEngine.Tap tap = multiTapEngine.onDigit(digit, now, shift);
        boolean cycle = tap.cyclesExisting && !pendingLetter.isEmpty();

        // When an editor moved the cursor between taps, never erase unrelated text.
        // Some password editors intentionally suppress surrounding-text reads.
        if (cycle && !passwordField) {
            CharSequence before = input.getTextBeforeCursor(1, 0);
            if (before != null && (before.length() != 1
                    || !pendingLetter.contentEquals(before))) {
                multiTapEngine.confirm();
                tap = multiTapEngine.onDigit(digit, now, shift);
                cycle = false;
            }
        }

        stopTapTimer();
        // Commit a visible character immediately. On a second/third press,
        // replace precisely that last character instead of appending. This is
        // more compatible with chat input fields than relying on composing spans.
        input.beginBatchEdit();
        try {
            if (cycle) {
                if (!input.deleteSurroundingTextInCodePoints(1, 0)) {
                    input.deleteSurroundingText(1, 0);
                }
            }
            input.commitText(tap.letter, 1);
        } finally {
            input.endBatchEdit();
        }
        pendingLetter = tap.letter;
        if (!cycle) shift = false;
        scheduleTapCommit();
        updateSuggestions();
    }

    private void updateT9Composition() {
        InputConnection input = getCurrentInputConnection();
        if (input == null) return;
        if (t9Digits.isEmpty()) {
            input.setComposingText("", 1);
            input.finishComposingText();
            return;
        }
        List<String> options = T9Engine.suggest(t9Digits, WordRepository.all(this), 3);
        String candidate = options.isEmpty() ? t9Digits : options.get(0);
        if (t9Uppercase && !candidate.isEmpty()) {
            candidate = Character.toUpperCase(candidate.charAt(0))
                    + candidate.substring(1);
        }
        input.setComposingText(candidate, 1);
    }

    private void insertLiteral(String value) {
        InputConnection input = getCurrentInputConnection();
        if (input == null) return;
        finishT9();
        finishMultiTap();
        clearState();
        input.commitText(value, 1);
        updateSuggestions();
    }

    private void space() {
        if (numericMode) {
            insertLiteral("0");
            return;
        }
        InputConnection input = getCurrentInputConnection();
        if (input == null) return;
        finishT9();
        finishMultiTap();
        CharSequence before = input.getTextBeforeCursor(1, 0);
        boolean afterSentence = before != null && before.length() == 1
                && ".!?".indexOf(before.charAt(0)) >= 0;
        clearState();
        input.commitText(" ", 1);
        shift = afterSentence;
        updateSuggestions();
    }

    private void enter() {
        InputConnection input = getCurrentInputConnection();
        if (input == null) return;
        finishT9();
        finishMultiTap();
        clearState();
        int action = editor == null ? EditorInfo.IME_ACTION_NONE
                : (editor.imeOptions & EditorInfo.IME_MASK_ACTION);
        if (action == EditorInfo.IME_ACTION_DONE
                || action == EditorInfo.IME_ACTION_GO
                || action == EditorInfo.IME_ACTION_SEND
                || action == EditorInfo.IME_ACTION_SEARCH
                || action == EditorInfo.IME_ACTION_NEXT
                || action == EditorInfo.IME_ACTION_PREVIOUS) {
            input.performEditorAction(action);
        } else {
            sendKey(input, KeyEvent.KEYCODE_ENTER);
        }
        shift = !passwordField && !numericMode;
        updateSuggestions();
    }

    private void backspace() {
        InputConnection input = getCurrentInputConnection();
        if (input == null) return;
        if (t9Mode && !passwordField && !t9Digits.isEmpty()) {
            t9Digits = t9Digits.substring(0, t9Digits.length() - 1);
            updateT9Composition();
        } else {
            finishMultiTap();
            clearState();
            input.deleteSurroundingTextInCodePoints(1, 0);
        }
        updateSuggestions();
    }

    private void moveCursor(boolean right) {
        InputConnection input = getCurrentInputConnection();
        if (input == null) return;
        if (right && multiTapEngine.hasPending()) {
            finishMultiTap(); // Confirm active letter without moving.
            updateSuggestions();
            return;
        }
        if (right && !t9Digits.isEmpty()) {
            finishT9(); // Confirm active T9 candidate without moving.
            updateSuggestions();
            return;
        }
        finishT9();
        finishMultiTap();
        clearState();
        sendKey(input, right ? KeyEvent.KEYCODE_DPAD_RIGHT
                : KeyEvent.KEYCODE_DPAD_LEFT);
        updateSuggestions();
    }

    private void sendKey(InputConnection input, int code) {
        input.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        input.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
    }

    private String currentWord() {
        InputConnection input = getCurrentInputConnection();
        if (input == null) return "";
        CharSequence before = input.getTextBeforeCursor(48, 0);
        if (before == null) return "";
        String text = before.toString();
        int start = text.length();
        while (start > 0 && Character.isLetter(text.charAt(start - 1))) {
            start--;
        }
        return text.substring(start);
    }

    private void updateSuggestions() {
        if (suggestionViews[0] == null) return;
        List<String> options = Collections.emptyList();
        if (!passwordField && !numericMode) {
            List<String> words = WordRepository.all(this);
            if (t9Mode && !t9Digits.isEmpty()) {
                options = T9Engine.suggest(t9Digits, words, 3);
            } else if (!t9Mode) {
                options = T9Engine.completePrefix(currentWord(), words, 3);
            }
        }
        for (int i = 0; i < suggestionViews.length; i++) {
            String word = i < options.size() ? options.get(i) : "";
            TextView view = suggestionViews[i];
            view.setText(word.isEmpty() ? (passwordField && i == 0
                    ? "Secure" : "·") : word);
            view.setTag(word);
        }
    }

    private void acceptSuggestion(int index) {
        if (passwordField || index < 0 || index >= suggestionViews.length) return;
        Object tag = suggestionViews[index].getTag();
        if (!(tag instanceof String) || ((String) tag).isEmpty()) return;
        String word = (String) tag;
        InputConnection input = getCurrentInputConnection();
        if (input == null) return;

        if (t9Mode && !t9Digits.isEmpty()) {
            String accepted = t9Uppercase
                    ? Character.toUpperCase(word.charAt(0)) + word.substring(1)
                    : word;
            input.setComposingText(accepted, 1);
            input.finishComposingText();
        } else {
            finishMultiTap();
            String typed = currentWord();
            if (!typed.isEmpty()) input.deleteSurroundingText(typed.length(), 0);
            input.commitText(word, 1);
        }
        clearState();
        shift = false;
        input.commitText(" ", 1);
        updateSuggestions();
    }

    private void pasteClipboard() {
        if (passwordField) return;
        ClipboardManager manager =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null || !manager.hasPrimaryClip()) return;
        ClipData clip = manager.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return;
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        if (text != null && text.length() > 0) insertLiteral(text.toString());
    }

    private void addCurrentWord() {
        if (passwordField) return;
        finishMultiTap();
        if (WordRepository.addPersonal(this, currentWord())) updateSuggestions();
    }
}
