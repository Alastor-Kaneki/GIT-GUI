package com.alastorkaneki.gitgui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class DiagnosticActivity extends Activity {
    private TextView reportView;
    private TextView statusView;
    private String diagnosticUri = "";
    private boolean launchedUi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        try {
            diagnosticUri = DiagnosticLogger.beginSession(this);
            DiagnosticLogger.installCrashHandler(this, "diagnostic-process");
            DiagnosticLogger.append(this, "DiagnosticActivity.onCreate entered");
            DiagnosticLogger.appendHistoricalExits(this);
            buildScreen();
            DiagnosticLogger.append(this, "DiagnosticActivity.onCreate completed");
            refreshReport("Safe diagnostic launcher is running");
        } catch (Throwable error) {
            DiagnosticLogger.appendThrowable(this, "DIAGNOSTIC LAUNCHER FAILURE", error);
            showEmergency(error);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (launchedUi) {
            launchedUi = false;
            DiagnosticLogger.append(this, "Returned to diagnostic launcher after full UI launch");
            DiagnosticLogger.appendHistoricalExits(this);
            refreshReport("Full UI returned or crashed; diagnostic data refreshed");
        }
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(24));
        root.setBackgroundColor(Color.BLACK);

        TextView title = new TextView(this);
        title.setText("GIT GUI Safe Launcher");
        title.setTextSize(25f);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, matchWrap());

        statusView = new TextView(this);
        statusView.setTextSize(15f);
        statusView.setTextColor(Color.rgb(180, 255, 190));
        statusView.setPadding(0, dp(10), 0, dp(12));
        root.addView(statusView, matchWrap());

        TextView instructions = new TextView(this);
        instructions.setText("This screen runs without Kotlin, Compose, JGit, or the GitHub client. Tap Launch full app once. If the main interface crashes, this screen should return and update the log automatically.");
        instructions.setTextSize(14f);
        instructions.setTextColor(Color.LTGRAY);
        instructions.setPadding(0, 0, 0, dp(12));
        root.addView(instructions, matchWrap());

        Button launch = new Button(this);
        launch.setText("Launch full app");
        launch.setOnClickListener(view -> launchFullApp());
        root.addView(launch, buttonLayout());

        Button refresh = new Button(this);
        refresh.setText("Refresh Android crash data");
        refresh.setOnClickListener(view -> {
            DiagnosticLogger.appendHistoricalExits(this);
            refreshReport("Android crash data refreshed");
        });
        root.addView(refresh, buttonLayout());

        Button save = new Button(this);
        save.setText("Save current log to Downloads");
        save.setOnClickListener(view -> {
            String result = DiagnosticLogger.syncToDownloads(this);
            if ("FAILED".equals(result)) {
                refreshReport("Downloads write failed; the report is still visible below");
                Toast.makeText(this, "Downloads write failed", Toast.LENGTH_LONG).show();
            } else {
                refreshReport("Saved to Downloads as " + result);
                Toast.makeText(this, "Saved: " + result, Toast.LENGTH_LONG).show();
            }
        });
        root.addView(save, buttonLayout());

        Button share = new Button(this);
        share.setText("Share diagnostic text");
        share.setOnClickListener(view -> shareReport());
        root.addView(share, buttonLayout());

        reportView = new TextView(this);
        reportView.setTextSize(11f);
        reportView.setTextColor(Color.rgb(220, 255, 220));
        reportView.setTextIsSelectable(true);
        reportView.setPadding(dp(12), dp(12), dp(12), dp(12));
        reportView.setBackgroundColor(Color.rgb(10, 10, 10));

        ScrollView reportScroll = new ScrollView(this);
        reportScroll.addView(reportView, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams reportParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        reportParams.topMargin = dp(14);
        root.addView(reportScroll, reportParams);

        setContentView(root);
    }

    private void launchFullApp() {
        try {
            DiagnosticLogger.append(this, "User requested full UI launch");
            DiagnosticLogger.syncToDownloads(this);
            Intent intent = new Intent();
            intent.setClassName(getPackageName(), getPackageName() + ".UiBootstrapActivity");
            intent.putExtra("diagnostic_uri", diagnosticUri);
            launchedUi = true;
            startActivity(intent);
            statusView.setText("Launching the full app in the isolated UI process");
        } catch (Throwable error) {
            launchedUi = false;
            DiagnosticLogger.appendThrowable(this, "FULL UI LAUNCH FAILED", error);
            refreshReport("The full UI could not be launched");
        }
    }

    private void shareReport() {
        try {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, "GIT GUI diagnostic report");
            share.putExtra(Intent.EXTRA_TEXT, DiagnosticLogger.read(this));
            startActivity(Intent.createChooser(share, "Share GIT GUI diagnostic report"));
        } catch (Throwable error) {
            DiagnosticLogger.appendThrowable(this, "SHARE FAILED", error);
            refreshReport("Sharing failed; copy the selectable report below");
        }
    }

    private void refreshReport(String status) {
        if (statusView != null) {
            statusView.setText(status);
        }
        if (reportView != null) {
            reportView.setText(DiagnosticLogger.read(this));
        }
        DiagnosticLogger.syncToDownloads(this);
    }

    private void showEmergency(Throwable error) {
        TextView emergency = new TextView(this);
        emergency.setText("GIT GUI Java launcher failed\n\n" + error.getClass().getName() + "\n" + (error.getMessage() == null ? "" : error.getMessage()) + "\n\nReopen the app and report this exact text.");
        emergency.setTextColor(Color.WHITE);
        emergency.setTextSize(15f);
        emergency.setTextIsSelectable(true);
        emergency.setPadding(dp(20), dp(30), dp(20), dp(30));
        emergency.setBackgroundColor(Color.BLACK);
        setContentView(emergency);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams buttonLayout() {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(7);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
