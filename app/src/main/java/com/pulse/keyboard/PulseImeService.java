package com.pulse.keyboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Offline 3x4 one-handed Android IME. No network, no typed-text logging.
 * Multi-tap changes the last committed letter while a digit is active.
 * T9 uses a composing span, then accepts a candidate on Space/Enter/Next.
 */
public final class PulseImeService extends InputMethodService {
    private static final String[] KEYS = {
        "", ".,!?':;@", "abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"
    };
    private static final String[] EMOJI = {
        "😀", "😂", "😊", "😍", "🥰", "😎", "😭", "😢",
        "👍", "🙏", "❤️", "🔥", "🎉", "✅", "🤔", "💯",
        "👋", "😅", "😁", "🙌", "🤝", "💪", "👌", "😴"
    };
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable repeatedDelete;
    private FrameLayout frame;
    private LinearLayout body;
    private final TextView[] suggestionViews = new TextView[3];
    private SharedPreferences prefs;
    private EditorInfo editor;
    private boolean t9Mode;
    private boolean numericMode;
    private boolean shift;
    private boolean passwordField;
    private boolean darkTheme;
    private String panel = "keys";
    private String t9Digits = "";
    private int lastDigit = -1;
    private int lastIndex;
    private long lastDigitTime;
    private boolean lastUppercase;
    private int backdrop;
    private int face;
    private int textColor;
    private int accent;

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(WordRepository.PREFS, MODE_PRIVATE);
        t9Mode = prefs.getBoolean("t9_mode", false);
    }

    @Override public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @Override public View onCreateInputView() {
        frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.TRANSPARENT);
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
        int kind = attribute == null ? InputType.TYPE_CLASS_TEXT :
                (attribute.inputType & InputType.TYPE_MASK_CLASS);
        numericMode = kind == InputType.TYPE_CLASS_NUMBER ||
                kind == InputType.TYPE_CLASS_PHONE || kind == InputType.TYPE_CLASS_DATETIME;
        t9Mode = prefs.getBoolean("t9_mode", false);
        shift = false;
        clearState();
        panel = "keys";
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        buildKeyboard();
    }

    @Override public void onFinishInput() {
        stopDeleteRepeat();
        clearState();
        super.onFinishInput();
    }

    @Override public void onDestroy() {
        stopDeleteRepeat();
        super.onDestroy();
    }

    private boolean isPassword(EditorInfo info) {
        if (info == null) return false;
        int kind = info.inputType & InputType.TYPE_MASK_CLASS;
        int variant = info.inputType & InputType.TYPE_MASK_VARIATION;
        if (kind == InputType.TYPE_CLASS_NUMBER) {
            return variant == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        }
        return kind == InputType.TYPE_CLASS_TEXT &&
                (variant == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                 variant == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                 variant == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private void colors() {
        darkTheme = (getResources().getConfiguration().uiMode &
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        backdrop = darkTheme ? Color.rgb(32, 34, 41) : Color.rgb(225, 229, 236);
        face = darkTheme ? Color.rgb(59, 63, 75) : Color.WHITE;
        textColor = darkTheme ? Color.WHITE : Color.rgb(30, 34, 44);
        accent = darkTheme ? Color.rgb(95, 159, 255) : Color.rgb(36, 98, 197);
    }

    private GradientDrawable background(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(8));
        return shape;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private TextView key(String label, String description, int textSp, int height,
                         Runnable action) {
        TextView text = new TextView(this);
        text.setText(label);
        text.setGravity(Gravity.CENTER);
        text.setTextSize(textSp);
        text.setTextColor(textColor);
        text.setBackground(background(face));
        text.setContentDescription(description);
        text.setClickable(true);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(0, dp(height), 1f);
        layout.setMargins(dp(2), dp(2), dp(2), dp(2));
        text.setLayoutParams(layout);
        text.setOnClickListener(v -> action.run());
        return text;
    }

    private void addKey(LinearLayout row, String display, String description,
                        int textSize, int height, Runnable action) {
        row.addView(key(display, description, textSize, height, action));
    }

    private void buildKeyboard() {
        if (body == null || frame == null) return;
        colors();
        int widthPercent = Math.max(60, Math.min(100, prefs.getInt("width_percent", 75)));
        int displayWidth = getResources().getDisplayMetrics().widthPixels;
        int width = Math.min(displayWidth, Math.max(dp(235),
                Math.round(displayWidth * widthPercent / 100f)));
        int side = prefs.getBoolean("align_right", true) ? Gravity.RIGHT : Gravity.LEFT;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | side);
        body.setLayoutParams(params);
        body.setPadding(dp(3), dp(3), dp(3), dp(5));
        body.setBackgroundColor(backdrop);
        body.removeAllViews();
        drawToolbar();
        drawSuggestions();
        if ("emoji".equals(panel)) drawEmoji();
        else if ("symbols".equals(panel)) drawSymbols();
        else drawNumberGrid();
        drawBottomControls();
        updateSuggestions();
    }

    private void drawToolbar() {
        LinearLayout toolbar = row();
        addKey(toolbar, t9Mode ? "T9" : "ABC", "Toggle T9 or multi-tap", 12, 34,
                () -> {
                    finishT9();
                    clearState();
                    t9Mode = !t9Mode;
                    prefs.edit().putBoolean("t9_mode", t9Mode).apply();
                    panel = "keys";
                    buildKeyboard();
                });
        addKey(toolbar, shift ? "⇧●" : "⇧", "Shift", 15, 34,
                () -> { shift = !shift; buildKeyboard(); });
        addKey(toolbar, "⇆", "Switch left or right", 16, 34,
                () -> {
                    boolean right = !prefs.getBoolean("align_right", true);
                    prefs.edit().putBoolean("align_right", right).apply();
                    buildKeyboard();
                });
        addKey(toolbar, "☺", "Emoji panel", 17, 34,
                () -> { panel = "emoji".equals(panel) ? "keys" : "emoji"; buildKeyboard(); });
        addKey(toolbar, "✳", "Symbols panel", 15, 34,
                () -> { panel = "symbols".equals(panel) ? "keys" : "symbols"; buildKeyboard(); });
        addKey(toolbar, "⎘", "Paste clipboard manually", 16, 34, this::pasteClipboard);
        addKey(toolbar, "+", "Add current word to personal dictionary", 15, 34,
                this::addCurrentWord);
        addKey(toolbar, "⚙", "Keyboard settings", 15, 34, () -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });
        body.addView(toolbar);
    }

    private void drawSuggestions() {
        LinearLayout suggestions = row();
        for (int i = 0; i < suggestionViews.length; i++) {
            final int index = i;
            TextView view = key("·", "Word suggestion", 13, 34,
                    () -> acceptSuggestion(index));
            view.setTextColor(accent);
            suggestionViews[i] = view;
            suggestions.addView(view);
        }
        body.addView(suggestions);
    }

    private void drawNumberGrid() {
        int height = Math.max(40, Math.min(70, prefs.getInt("key_height_dp", 51)));
        for (int line = 0; line < 3; line++) {
            LinearLayout digitRow = row();
            for (int column = 0; column < 3; column++) {
                int digit = line * 3 + column + 1;
                String letters = KEYS[digit].toUpperCase(Locale.ROOT);
                String display = numericMode ? String.valueOf(digit) :
                        digit + "\n" + (digit == 1 ? ".,!?" : letters);
                TextView button = key(display, "Key " + digit + " " + letters,
                        numericMode ? 21 : 16, height, () -> typeDigit(digit));
                button.setOnLongClickListener(v -> {
                    if (digit == 1 && !numericMode) {
                        panel = "symbols";
                        buildKeyboard();
                    } else typeLiteralDigit(digit);
                    return true;
                });
                digitRow.addView(button);
            }
            body.addView(digitRow);
        }
        LinearLayout last = row();
        TextView back = key("*  ⌫", "Backspace; hold to repeat", 16, height,
                this::backspace);
        back.setOnTouchListener((view, event) -> {
            int kind = event.getActionMasked();
            if (kind == MotionEvent.ACTION_DOWN) {
                stopDeleteRepeat();
                view.setPressed(true);
                backspace();
                repeatedDelete = new Runnable() {
                    @Override public void run() {
                        backspace();
                        handler.postDelayed(this, 85);
                    }
                };
                handler.postDelayed(repeatedDelete, 420);
                return true;
            }
            if (kind == MotionEvent.ACTION_UP || kind == MotionEvent.ACTION_CANCEL) {
                view.setPressed(false);
                stopDeleteRepeat();
                return true;
            }
            return true;
        });
        last.addView(back);
        addKey(last, numericMode ? "0" : "0\nSPACE", "Space or zero", 15, height,
                this::space);
        addKey(last, "#  ↵", "Enter or next", 16, height, this::enter);
        body.addView(last);
    }

    private void drawBottomControls() {
        LinearLayout lower = row();
        addKey(lower, numericMode ? "ABC" : "123", "Toggle numeric mode", 13, 36, () -> {
            finishT9();
            clearState();
            numericMode = !numericMode;
            panel = "keys";
            buildKeyboard();
        });
        addKey(lower, "←", "Move cursor left", 21, 36, () -> moveCursor(false));
        addKey(lower, "→", "Confirm letter or move cursor right", 21, 36,
                () -> moveCursor(true));
        body.addView(lower);
    }

    private void drawSymbols() {
        String[] symbols = {"@", "#", "$", "%", "&", "*", "(", ")", "-",
                "+", "=", "/", ":", ";", "'", "\"", "?", "!", ".", ",", "_", "€", "₱", "✓"};
        int rowSize = 6;
        for (int start = 0; start < symbols.length; start += rowSize) {
            LinearLayout line = row();
            for (int i = start; i < Math.min(start + rowSize, symbols.length); i++) {
                final String chosen = symbols[i];
                addKey(line, chosen, "Insert " + chosen, 18, 42,
                        () -> insertLiteral(chosen));
            }
            body.addView(line);
        }
    }

    private void drawEmoji() {
        ArrayList<String> all = new ArrayList<>();
        String history = prefs.getString("recent_emojis", "");
        if (!history.isEmpty()) {
            for (String item : history.split("\\|")) {
                if (!item.isEmpty() && !all.contains(item)) all.add(item);
            }
        }
        for (String item : EMOJI) if (!all.contains(item)) all.add(item);
        int rowSize = 6;
        for (int start = 0; start < all.size() && start < 24; start += rowSize) {
            LinearLayout line = row();
            for (int i = start; i < Math.min(start + rowSize, Math.min(24, all.size())); i++) {
                final String chosen = all.get(i);
                addKey(line, chosen, "Emoji", 21, 43, () -> {
                    insertLiteral(chosen);
                    rememberEmoji(chosen);
                });
            }
            body.addView(line);
        }
    }

    private void rememberEmoji(String item) {
        ArrayList<String> recent = new ArrayList<>();
        recent.add(item);
        String existing = prefs.getString("recent_emojis", "");
        for (String candidate : existing.split("\\|")) {
            if (!candidate.isEmpty() && !recent.contains(candidate) && recent.size() < 6)
                recent.add(candidate);
        }
        prefs.edit().putString("recent_emojis", String.join("|", recent)).apply();
    }

    private void stopDeleteRepeat() {
        if (repeatedDelete != null) handler.removeCallbacks(repeatedDelete);
        repeatedDelete = null;
    }

    private void clearState() {
        t9Digits = "";
        lastDigit = -1;
        lastIndex = 0;
        lastDigitTime = 0L;
    }

    private void finishT9() {
        InputConnection input = getCurrentInputConnection();
        if (input != null && !t9Digits.isEmpty()) input.finishComposingText();
        t9Digits = "";
        lastDigit = -1;
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
            return;
        }
        if (t9Mode && !passwordField) {
            lastDigit = -1;
            t9Digits += digit;
            updateT9Composition();
        } else multiTap(digit);
        updateSuggestions();
    }

    private void typeLiteralDigit(int digit) {
        insertLiteral(String.valueOf(digit));
    }

    private void multiTap(int digit) {
        InputConnection input = getCurrentInputConnection();
        if (input == null) return;
        finishT9();
        String set = KEYS[digit];
        long now = SystemClock.uptimeMillis();
        boolean same = digit == lastDigit && now - lastDigitTime < 900;
        int index = same ? (lastIndex + 1) % set.length() : 0;
        if (same) input.deleteSurroundingText(1, 0);
        boolean upper = same ? lastUppercase : shift;
        char letter = set.charAt(index);
        input.commitText(String.valueOf(upper ? Character.toUpperCase(letter) : letter), 1);
        lastDigit = digit;
        lastIndex = index;
        lastDigitTime = now;
        lastUppercase = upper;
        if (!same) shift = false;
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
        if (shift && !candidate.isEmpty()) {
            candidate = Character.toUpperCase(candidate.charAt(0)) + candidate.substring(1);
        }
        input.setComposingText(candidate, 1);
    }

    private void insertLiteral(String value) {
        InputConnection input = getCurrentInputConnection();
        if (input == null) return;
        finishT9();
        clearState();
        input.commitText(value, 1);
        updateSuggestions();
    }

    private void space() {
        InputConnection input = getCurrentInputConnection();
        if (input == null) return;
        if (numericMode) {
            insertLiteral("0");
            return;
        }
        finishT9();
        clearState();
        input.commitText(" ", 1);
        shift = false;
        updateSuggestions();
    }

    private void enter() {
        InputConnection input = getCurrentInputConnection();
        if (input == null) return;
        finishT9();
        clearState();
        int action = editor == null ? EditorInfo.IME_ACTION_NONE :
                (editor.imeOptions & EditorInfo.IME_MASK_ACTION);
        if (action == EditorInfo.IME_ACTION_DONE || action == EditorInfo.IME_ACTION_GO ||
                action == EditorInfo.IME_ACTION_SEND || action == EditorInfo.IME_ACTION_SEARCH ||
                action == EditorInfo.IME_ACTION_NEXT || action == EditorInfo.IME_ACTION_PREVIOUS) {
            input.performEditorAction(action);
        } else sendKey(input, KeyEvent.KEYCODE_ENTER);
        updateSuggestions();
    }

    private void backspace() {
        InputConnection input = getCurrentInputConnection();
        if (input == null) return;
        if (!t9Digits.isEmpty() && t9Mode && !passwordField) {
            t9Digits = t9Digits.substring(0, t9Digits.length() - 1);
            updateT9Composition();
        } else {
            clearState();
            input.deleteSurroundingTextInCodePoints(1, 0);
        }
        updateSuggestions();
    }

    private void moveCursor(boolean right) {
        InputConnection input = getCurrentInputConnection();
        if (input == null) return;
        if (right && lastDigit != -1) {
            lastDigit = -1; // Confirm the active multi-tap letter.
            updateSuggestions();
            return;
        }
        if (right && !t9Digits.isEmpty()) {
            finishT9(); // Confirm the composing predictive word.
            updateSuggestions();
            return;
        }
        finishT9();
        clearState();
        sendKey(input, right ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT);
        updateSuggestions();
    }

    private void sendKey(InputConnection input, int keyCode) {
        input.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        input.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }

    private String currentWord() {
        InputConnection input = getCurrentInputConnection();
        if (input == null) return "";
        CharSequence before = input.getTextBeforeCursor(40, 0);
        if (before == null) return "";
        String value = before.toString();
        int start = value.length();
        while (start > 0 && Character.isLetter(value.charAt(start - 1))) start--;
        return value.substring(start);
    }

    private void updateSuggestions() {
        if (suggestionViews[0] == null) return;
        List<String> options = Collections.emptyList();
        if (!passwordField && !numericMode) {
            List<String> dictionary = WordRepository.all(this);
            if (t9Mode && !t9Digits.isEmpty()) {
                options = T9Engine.suggest(t9Digits, dictionary, 3);
            } else if (!t9Mode) {
                options = T9Engine.completePrefix(currentWord(), dictionary, 3);
            }
        }
        for (int i = 0; i < suggestionViews.length; i++) {
            TextView view = suggestionViews[i];
            String word = i < options.size() ? options.get(i) : "";
            view.setText(word.isEmpty() ? (passwordField && i == 0 ? "Secure" : "·") : word);
            view.setTag(word);
        }
    }

    private void acceptSuggestion(int index) {
        if (passwordField || index < 0 || index >= suggestionViews.length) return;
        Object tag = suggestionViews[index].getTag();
        if (!(tag instanceof String)) return;
        String word = (String) tag;
        if (word.isEmpty()) return;
        InputConnection input = getCurrentInputConnection();
        if (input == null) return;
        if (t9Mode && !t9Digits.isEmpty()) {
            String accepted = shift && !word.isEmpty() ?
                    Character.toUpperCase(word.charAt(0)) + word.substring(1) : word;
            input.setComposingText(accepted, 1);
            input.finishComposingText();
        } else {
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
        if (passwordField) return; // Never expose clipboard through a password field.
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null || !manager.hasPrimaryClip()) return;
        ClipData clip = manager.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return;
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        if (text != null && text.length() > 0) insertLiteral(text.toString());
    }

    private void addCurrentWord() {
        if (passwordField) return;
        String current = currentWord();
        if (WordRepository.addPersonal(this, current)) updateSuggestions();
    }
}
