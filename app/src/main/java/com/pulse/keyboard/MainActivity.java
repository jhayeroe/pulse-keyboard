package com.pulse.keyboard;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private int dp(int amount) {
        return Math.round(amount * getResources().getDisplayMetrics().density);
    }

    private TextView label(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setPadding(0, dp(9), 0, dp(13));
        return view;
    }

    private Button button(String value, Runnable action) {
        Button view = new Button(this);
        view.setText(value);
        view.setAllCaps(false);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(22), dp(24), dp(22), dp(22));

        content.addView(label("PULSE KEYBOARD", 26));
        content.addView(label(
                "Offline Android 3×4 keypad • T9 + Multi-tap\n\n" +
                "Step 1: Enable Pulse Keyboard in Android's keyboard settings.\n" +
                "Step 2: Choose Pulse Keyboard as your active input method.\n" +
                "Step 3: Adjust the one-handed keypad layout in Keyboard Settings.", 16));

        content.addView(button("1. Enable Keyboard",
                () -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))));
        content.addView(button("2. Choose Keyboard", () -> {
            InputMethodManager manager =
                    (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (manager != null) manager.showInputMethodPicker();
        }));
        content.addView(button("3. Keyboard Settings",
                () -> startActivity(new Intent(this, SettingsActivity.class))));
        content.addView(button("🌐 Switch to another keyboard", () -> {
            InputMethodManager manager =
                    (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (manager != null) manager.showInputMethodPicker();
        }));
        content.addView(label(
                "Alpha/testing build. Personal words stay on this phone; no internet permission is requested.",
                13));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);
    }
}
