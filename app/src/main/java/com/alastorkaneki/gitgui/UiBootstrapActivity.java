package com.alastorkaneki.gitgui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;

public final class UiBootstrapActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        String uri = getIntent().getStringExtra("diagnostic_uri");
        DiagnosticLogger.attach(this, uri);
        DiagnosticLogger.installCrashHandler(this, "ui-process");
        DiagnosticLogger.append(this, "UiBootstrapActivity.onCreate entered");
        try {
            Intent intent = new Intent();
            intent.setClassName(getPackageName(), getPackageName() + ".MainActivity");
            DiagnosticLogger.append(this, "Starting MainActivity");
            startActivity(intent);
            DiagnosticLogger.append(this, "MainActivity startActivity returned");
            finish();
        } catch (Throwable error) {
            DiagnosticLogger.appendThrowable(this, "MAIN ACTIVITY START FAILED", error);
            TextView text = new TextView(this);
            text.setText("The full interface failed before it could open. Return to the safe launcher and share the diagnostic report.");
            text.setTextColor(Color.WHITE);
            text.setTextSize(16f);
            text.setPadding(40, 60, 40, 60);
            text.setBackgroundColor(Color.BLACK);
            setContentView(text);
        }
    }
}
