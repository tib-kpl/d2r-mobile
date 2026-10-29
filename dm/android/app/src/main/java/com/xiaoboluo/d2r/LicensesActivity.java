package com.xiaoboluo.d2r;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Optional notices page, opened only by the user's Menu > Licenses action. */
public final class LicensesActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String notices;
        try { notices = readNotice("ffmpeg_notice") + "\n\n" + readNotice("ffmpeg_lgpl"); }
        catch (IOException e) { android.util.Log.e("dm", "FFmpeg license", e); finish(); return; }

        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(padding, padding, padding, padding);
        TextView title = new TextView(this);
        title.setText(R.string.dmAndroidLicenses); title.setTextSize(22);
        page.addView(title);
        TextView text = new TextView(this);
        text.setText(notices); text.setTextIsSelectable(true);
        text.setPadding(0, padding, 0, padding);
        ScrollView scroll = new ScrollView(this); scroll.addView(text);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        Button close = new Button(this); close.setText(android.R.string.ok);
        close.setOnClickListener(v -> finish()); page.addView(close);
        setContentView(page);
    }

    private String readNotice(String name) throws IOException {
        int id = getResources().getIdentifier(name, "raw", getPackageName());
        if (id == 0) throw new IOException("Missing notice: " + name);
        try (InputStream stream = getResources().openRawResource(id);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
