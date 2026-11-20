package com.corex.falcon;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private int dp(float value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createInterface();
    }

    private void createInterface() {

        ScrollView scroll = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(18));
        root.setBackgroundColor(Color.rgb(246, 247, 249));

        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Falcon");
        title.setTextSize(32);
        title.setTextColor(Color.rgb(25, 30, 40));
        title.setGravity(Gravity.CENTER);
        root.addView(title, params());

        TextView subtitle = new TextView(this);
        subtitle.setText("SECURE COMMUNICATION");
        subtitle.setTextSize(12);
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(5), 0, dp(30));
        root.addView(subtitle, params());

        addHeading(root, "Communication");

        addButton(root, "Internet Messaging & File Transfer",
                () -> openActivity(MessagingActivity.class));

        addHeading(root, "Private Storage");

        addButton(root, "Open Falcon Vault",
                () -> openActivity(VaultActivity.class));

        addHeading(root, "Security Information");

        TextView security = new TextView(this);
        security.setText(
                "• AES-256-GCM file encryption\n" +
                "• RSA recipient-key encryption\n" +
                "• Android Keystore integration\n" +
                "• Encrypted Internet file transfer\n" +
                "• Message and file inbox\n" +
                "• Offline transfer: Not yet implemented"
        );
        security.setTextSize(14);
        security.setTextColor(Color.rgb(65, 70, 80));
        security.setPadding(dp(14), dp(14), dp(14), dp(14));
        root.addView(security, params());

        TextView footer = new TextView(this);
        footer.setText(
                "Falcon | CoreX OS\n\n" +
                "Developed by Deepak Kumar Sharma"
        );
        footer.setGravity(Gravity.CENTER);
        footer.setTextSize(12);
        footer.setTextColor(Color.DKGRAY);
        footer.setPadding(0, dp(35), 0, dp(15));
        root.addView(footer, params());

        setContentView(scroll);
    }

    private void addHeading(LinearLayout root, String text) {
        TextView heading = new TextView(this);
        heading.setText(text);
        heading.setTextSize(18);
        heading.setTextColor(Color.rgb(30, 35, 45));
        heading.setPadding(0, dp(22), 0, dp(10));
        root.addView(heading, params());
    }

    private void addButton(
            LinearLayout root,
            String text,
            Runnable action
    ) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setOnClickListener(v -> action.run());

        root.addView(button, params());
    }

    private LinearLayout.LayoutParams params() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private void openActivity(Class<?> activity) {
        startActivity(new Intent(this, activity));
    }
}