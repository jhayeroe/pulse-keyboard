package com.pulse.keyboard;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public final class SettingsActivity extends Activity {
    private SharedPreferences prefs;
    private int dp(int amount) {
        return Math.round(amount * getResources().getDisplayMetrics().density);
    }
    private TextView label(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setPadding(0, dp(10), 0, dp(8));
        return view;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(WordRepository.PREFS, MODE_PRIVATE);
        LinearLayout content = new LinearLayout(this);
        content.setPadding(dp(22), dp(22), dp(22), dp(22));
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(label("Pulse Keyboard Settings", 23));

        Button side = new Button(this);
        side.setAllCaps(false);
        side.setText(prefs.getBoolean("align_right", true)
                ? "One-hand side: RIGHT (tap for LEFT)"
                : "One-hand side: LEFT (tap for RIGHT)");
        side.setOnClickListener(v -> {
            boolean right = !prefs.getBoolean("align_right", true);
            prefs.edit().putBoolean("align_right", right).apply();
            side.setText(right ? "One-hand side: RIGHT (tap for LEFT)"
                    : "One-hand side: LEFT (tap for RIGHT)");
        });
        content.addView(side);

        TextView widthText = label("", 16);
        content.addView(widthText);
        SeekBar widthBar = new SeekBar(this);
        widthBar.setMax(40);
        int widthValue = Math.max(60, Math.min(100, prefs.getInt("width_percent", 75)));
        widthBar.setProgress(widthValue - 60);
        widthText.setText("Keypad width: " + widthValue + "%");
        widthBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                int value = progress + 60;
                widthText.setText("Keypad width: " + value + "%");
                if (user) prefs.edit().putInt("width_percent", value).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        content.addView(widthBar);

        TextView heightText = label("", 16);
        content.addView(heightText);
        SeekBar heightBar = new SeekBar(this);
        heightBar.setMax(30);
        int heightValue = Math.max(40, Math.min(70, prefs.getInt("key_height_dp", 51)));
        heightBar.setProgress(heightValue - 40);
        heightText.setText("Number key height: " + heightValue + " dp");
        heightBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                int value = progress + 40;
                heightText.setText("Number key height: " + value + " dp");
                if (user) prefs.edit().putInt("key_height_dp", value).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        content.addView(heightBar);

        content.addView(label("Add a personal dictionary word (English letters only):", 15));
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setHint("e.g. salamat");
        content.addView(input);

        Button add = new Button(this);
        add.setText("Add word");
        add.setAllCaps(false);
        add.setOnClickListener(v -> {
            boolean success = WordRepository.addPersonal(this, input.getText().toString());
            Toast.makeText(this, success ? "Word saved locally" :
                    "Use 2–32 alphabetic characters", Toast.LENGTH_SHORT).show();
            if (success) input.setText("");
        });
        content.addView(add);
        content.addView(label(
                "Changes apply the next time the keyboard opens. No typed message is saved automatically.",
                13));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);
    }
}
