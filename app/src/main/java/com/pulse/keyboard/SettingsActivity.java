package com.pulse.keyboard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Self-contained native Settings UI, without third-party SDKs or network.
 * All changes are stored locally and apply when Pulse Keyboard next opens.
 */
public final class SettingsActivity extends Activity {
    private SharedPreferences prefs;
    private int pageColor;
    private int cardColor;
    private int toolColor;
    private int fg;
    private int muted;
    private int border;
    private int accent;
    private int white = Color.WHITE;

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable bg(int fill, int radius, int outline) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        if (outline != 0) shape.setStroke(dp(1), outline);
        return shape;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private void gap(LinearLayout parent, int height) {
        View space = new View(this);
        parent.addView(space, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private LinearLayout card(LinearLayout root, String name, String subtitle) {
        LinearLayout panel = column();
        panel.setPadding(dp(16), dp(15), dp(16), dp(16));
        panel.setBackground(bg(cardColor, 16, border));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(14);
        root.addView(panel, params);
        panel.addView(text(name, 17, fg, true));
        if (subtitle != null && !subtitle.isEmpty()) {
            gap(panel, 4);
            panel.addView(text(subtitle, 13, muted, false));
        }
        gap(panel, 12);
        return panel;
    }

    private Button button(String label, boolean primary) {
        Button view = new Button(this);
        view.setAllCaps(false);
        view.setText(label);
        view.setTextSize(14);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(primary ? white : fg);
        view.setBackground(bg(primary ? accent : toolColor, 12,
                primary ? 0 : border));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        params.topMargin = dp(5);
        view.setLayoutParams(params);
        return view;
    }

    private Button sideButton(String label) {
        Button result = button(label, false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, dp(43), 1f);
        params.leftMargin = dp(3);
        params.rightMargin = dp(3);
        result.setLayoutParams(params);
        return result;
    }

    private void updateSideButtons(Button left, Button right) {
        boolean alignRight = prefs.getBoolean("align_right", true);
        left.setTextColor(alignRight ? fg : white);
        right.setTextColor(alignRight ? white : fg);
        left.setBackground(bg(alignRight ? toolColor : accent, 11,
                alignRight ? border : 0));
        right.setBackground(bg(alignRight ? accent : toolColor, 11,
                alignRight ? 0 : border));
    }

    private SeekBar slider(int min, int max, int value, TextView display,
                           String label, String suffix, String prefKey) {
        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(Math.max(0, Math.min(max - min, value - min)));
        bar.setProgressTintList(ColorStateList.valueOf(accent));
        bar.setThumbTintList(ColorStateList.valueOf(accent));
        display.setText(label + ": " + value + suffix);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seek, int progress, boolean user) {
                int selected = min + progress;
                display.setText(label + ": " + selected + suffix);
                if (user) prefs.edit().putInt(prefKey, selected).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seek) {}
            @Override public void onStopTrackingTouch(SeekBar seek) {}
        });
        return bar;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(WordRepository.PREFS, MODE_PRIVATE);
        boolean dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        pageColor = dark ? Color.rgb(14, 19, 29) : Color.rgb(244, 247, 251);
        cardColor = dark ? Color.rgb(27, 36, 51) : white;
        toolColor = dark ? Color.rgb(42, 53, 71) : Color.rgb(237, 242, 249);
        fg = dark ? Color.rgb(245, 248, 255) : Color.rgb(25, 37, 56);
        muted = dark ? Color.rgb(158, 174, 197) : Color.rgb(94, 111, 133);
        border = dark ? Color.rgb(57, 71, 91) : Color.rgb(221, 229, 239);
        accent = dark ? Color.rgb(59, 125, 241) : Color.rgb(37, 99, 235);
        getWindow().setStatusBarColor(pageColor);
        getWindow().setNavigationBarColor(pageColor);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setFitsSystemWindows(true);
        scroll.setBackgroundColor(pageColor);
        LinearLayout root = column();
        root.setPadding(dp(18), dp(24), dp(18), dp(26));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        root.addView(text("PULSE / SETTINGS", 12, accent, true));
        gap(root, 6);
        root.addView(text("Your keyboard, your way.", 25, fg, true));
        gap(root, 5);
        root.addView(text("Compact one-handed keypad • English + Filipino", 14, muted, false));
        gap(root, 21);

        LinearLayout system = card(root, "Keyboard switch",
                "Return to your previous keyboard anytime.");
        Button picker = button("🌐  Switch Keyboard", true);
        picker.setOnClickListener(v -> {
            InputMethodManager manager =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) manager.showInputMethodPicker();
        });
        system.addView(picker);
        Button manage = button("Manage enabled keyboards", false);
        manage.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        system.addView(manage);
        gap(system, 7);
        system.addView(text("Tip: The 🌐 button on Pulse Keyboard opens this same system picker.",
                12, muted, false));

        LinearLayout typing = card(root, "Typing", "Choose how the 3×4 keypad enters words.");
        Switch t9 = new Switch(this);
        t9.setText("Predictive T9 typing (optional)");
        t9.setTextColor(fg);
        t9.setTextSize(15);
        t9.setChecked(prefs.getBoolean("t9_mode", false));
        t9.setShowText(false);
        t9.setThumbTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{white, muted}));
        t9.setTrackTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{accent, toolColor}));
        t9.setOnCheckedChangeListener((view, checked) ->
                prefs.edit().putBoolean("t9_mode", checked).apply());
        typing.addView(t9);
        gap(typing, 6);
        typing.addView(text("ABC is the default: tap 6 once = M, twice = N, three times = O. T9 uses one key press for each letter.",
                13, muted, false));
        gap(typing, 9);
        TextView example = text("TEST PHRASE   Ano na pare koy\n" +
                "T9 keys: 266  /  62  /  7273  /  569\n" +
                "Press SPACE after each word.", 13, fg, false);
        example.setPadding(dp(12), dp(12), dp(12), dp(12));
        example.setBackground(bg(toolColor, 12, 0));
        typing.addView(example);
        gap(typing, 8);
        typing.addView(text(
                "ABC: press the same number again within 1.8 seconds to cycle its letters. Use → to confirm one letter before typing another on the same key.",
                12, muted, false));

        LinearLayout layout = card(root, "Full-width split layout",
                "Default: 25% utility controls + 75% main keypad. Changes apply when you reopen the keyboard.");
        layout.addView(text("Alignment", 14, fg, true));
        gap(layout, 8);
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.HORIZONTAL);
        Button left = sideButton("← Left");
        Button right = sideButton("Right →");
        side.addView(left);
        side.addView(right);
        left.setOnClickListener(v -> {
            prefs.edit().putBoolean("align_right", false).apply();
            updateSideButtons(left, right);
        });
        right.setOnClickListener(v -> {
            prefs.edit().putBoolean("align_right", true).apply();
            updateSideButtons(left, right);
        });
        updateSideButtons(left, right);
        layout.addView(side);
        gap(layout, 16);
        TextView widthText = text("", 14, fg, true);
        layout.addView(widthText);
        SeekBar width = slider(65, 85,
                Math.max(65, Math.min(85, prefs.getInt("width_percent", 75))),
                widthText, "Main keypad share", "% of full keyboard", "width_percent");
        layout.addView(width);
        gap(layout, 13);
        TextView heightText = text("", 14, fg, true);
        layout.addView(heightText);
        SeekBar height = slider(38, 66,
                Math.max(38, Math.min(66, prefs.getInt("key_height_dp", 44))),
                heightText, "Key height", " dp", "key_height_dp");
        layout.addView(height);
        gap(layout, 4);
        Button reset = button("Restore 25/75 default layout", false);
        reset.setOnClickListener(v -> {
            prefs.edit().putBoolean("align_right", true)
                    .putInt("width_percent", 75).putInt("key_height_dp", 44).apply();
            width.setProgress(10);
            height.setProgress(6);
            updateSideButtons(left, right);
            Toast.makeText(this, "Layout restored", Toast.LENGTH_SHORT).show();
        });
        layout.addView(reset);

        LinearLayout dictionary = card(root, "Personal dictionary",
                "Save a word only when you choose to. Everything stays on this phone.");
        EditText input = new EditText(this);
        input.setTextColor(fg);
        input.setHintTextColor(muted);
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setHint("Add a word, e.g. pareng");
        input.setBackground(bg(toolColor, 11, border));
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        dictionary.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(45)));
        Button add = button("Add personal word", false);
        add.setOnClickListener(v -> {
            boolean success = WordRepository.addPersonal(this, input.getText().toString());
            Toast.makeText(this, success ? "Saved to local dictionary"
                    : "Use 2–32 English letters", Toast.LENGTH_SHORT).show();
            if (success) input.setText("");
        });
        dictionary.addView(add);

        LinearLayout test = card(root, "Test the keyboard", "Tap the field below and type:");
        EditText sample = new EditText(this);
        sample.setHint("Ano na pare koy");
        sample.setHintTextColor(muted);
        sample.setTextColor(fg);
        sample.setTextSize(16);
        sample.setSingleLine(false);
        sample.setMinLines(2);
        sample.setGravity(Gravity.TOP);
        sample.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        sample.setPadding(dp(12), dp(14), dp(12), dp(14));
        sample.setBackground(bg(toolColor, 12, border));
        test.addView(sample, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(92)));

        gap(root, 10);
        root.addView(text(
                "Pulse Keyboard alpha • Offline • No internet permission\n" +
                "Your typed text is not automatically stored.",
                12, muted, false));
    }
}
