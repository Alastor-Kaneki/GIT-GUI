package com.alastorkaneki.gitgui;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Process;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DiagnosticLogger {
    private static final Object LOCK = new Object();
    private static final AtomicBoolean HANDLING_CRASH = new AtomicBoolean(false);
    private static final String INTERNAL_NAME = "git-gui-diagnostic-current.txt";
    private static volatile Uri publicUri;
    private static volatile String publicName = "";

    private DiagnosticLogger() {
    }

    public static String beginSession(Context context) {
        synchronized (LOCK) {
            File internal = internalFile(context);
            if (internal.exists()) {
                internal.delete();
            }
            publicName = "GIT-GUI-diagnostic-" + fileTimestamp() + ".txt";
            publicUri = createPublicFile(context, publicName);
            appendLocked(context, "========== GIT GUI DIAGNOSTIC SESSION ==========");
            appendLocked(context, "Device: " + Build.MANUFACTURER + " " + Build.MODEL);
            appendLocked(context, "Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
            appendLocked(context, "Build: " + Build.DISPLAY);
            appendLocked(context, "Package: " + context.getPackageName());
            appendLocked(context, "Version: " + versionName(context));
            appendLocked(context, "PID: " + Process.myPid());
            appendLocked(context, "Process: " + processName());
            appendLocked(context, "Public file: " + publicName);
            appendLocked(context, "================================================");
            syncPublicLocked(context);
            return publicUri == null ? "" : publicUri.toString();
        }
    }

    public static void attach(Context context, String uriText) {
        synchronized (LOCK) {
            if (uriText != null && !uriText.isEmpty()) {
                try {
                    publicUri = Uri.parse(uriText);
                } catch (Throwable ignored) {
                    publicUri = null;
                }
            }
            appendLocked(context, "Logger attached in process " + processName());
            syncPublicLocked(context);
        }
    }

    public static void installCrashHandler(Context context, String label) {
        Context appContext = context.getApplicationContext();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            if (HANDLING_CRASH.compareAndSet(false, true)) {
                appendThrowable(appContext, "UNCAUGHT EXCEPTION " + label + " thread=" + thread.getName(), error);
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            } else {
                Process.killProcess(Process.myPid());
            }
        });
        append(appContext, "Crash handler installed: " + label);
    }

    public static void append(Context context, String message) {
        synchronized (LOCK) {
            appendLocked(context, message);
            syncPublicLocked(context);
        }
    }

    public static void appendThrowable(Context context, String title, Throwable error) {
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        synchronized (LOCK) {
            appendLocked(context, "========== " + title + " ==========");
            appendLocked(context, writer.toString());
            appendLocked(context, "========== END " + title + " ==========");
            syncPublicLocked(context);
        }
    }

    public static void appendHistoricalExits(Context context) {
        if (Build.VERSION.SDK_INT < 30) {
            append(context, "Historical process exits require Android 11 or newer");
            return;
        }
        try {
            ActivityManager manager = context.getSystemService(ActivityManager.class);
            List<ApplicationExitInfo> exits = manager.getHistoricalProcessExitReasons(context.getPackageName(), 0, 10);
            if (exits.isEmpty()) {
                append(context, "Android reported no historical process exits");
                return;
            }
            int count = Math.min(exits.size(), 5);
            for (int index = 0; index < count; index++) {
                ApplicationExitInfo exit = exits.get(index);
                StringBuilder summary = new StringBuilder();
                summary.append("Exit record ").append(index + 1).append('\n');
                summary.append("Time: ").append(displayTimestamp(exit.getTimestamp())).append('\n');
                summary.append("Process: ").append(exit.getProcessName()).append('\n');
                summary.append("Reason: ").append(reasonName(exit.getReason())).append(" (").append(exit.getReason()).append(")\n");
                summary.append("Status: ").append(exit.getStatus()).append('\n');
                summary.append("Importance: ").append(exit.getImportance()).append('\n');
                summary.append("PSS: ").append(exit.getPss()).append('\n');
                summary.append("RSS: ").append(exit.getRss()).append('\n');
                summary.append("Description: ").append(exit.getDescription() == null ? "" : exit.getDescription()).append('\n');
                append(context, summary.toString());
                try {
                    InputStream trace = exit.getTraceInputStream();
                    if (trace != null) {
                        append(context, "ANDROID EXIT TRACE\n" + readLimited(trace, 262144));
                        trace.close();
                    }
                } catch (Throwable traceError) {
                    append(context, "Unable to read Android exit trace: " + traceError.getClass().getName() + ": " + safeMessage(traceError));
                }
            }
        } catch (Throwable error) {
            appendThrowable(context, "HISTORICAL EXIT QUERY FAILED", error);
        }
    }

    public static String read(Context context) {
        synchronized (LOCK) {
            File file = internalFile(context);
            if (!file.exists()) {
                return "No diagnostic data has been written yet.";
            }
            try {
                return readAll(new FileInputStream(file), 1048576);
            } catch (Throwable error) {
                return "Unable to read diagnostic data: " + error.getClass().getName() + ": " + safeMessage(error);
            }
        }
    }

    public static String syncToDownloads(Context context) {
        synchronized (LOCK) {
            if (publicUri == null) {
                if (publicName.isEmpty()) {
                    publicName = "GIT-GUI-diagnostic-" + fileTimestamp() + ".txt";
                }
                publicUri = createPublicFile(context, publicName);
            }
            boolean success = syncPublicLocked(context);
            return success ? publicName : "FAILED";
        }
    }

    private static void appendLocked(Context context, String message) {
        try {
            File file = internalFile(context);
            FileOutputStream output = new FileOutputStream(file, true);
            String line = displayTimestamp(System.currentTimeMillis()) + " | " + processName() + " | " + Thread.currentThread().getName() + " | " + message + "\n";
            output.write(line.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.flush();
            output.close();
        } catch (Throwable ignored) {
        }
    }

    private static boolean syncPublicLocked(Context context) {
        if (publicUri == null) {
            return false;
        }
        try {
            File internal = internalFile(context);
            InputStream input = new FileInputStream(internal);
            OutputStream output;
            if ("file".equals(publicUri.getScheme())) {
                output = new FileOutputStream(new File(publicUri.getPath()), false);
            } else {
                output = context.getContentResolver().openOutputStream(publicUri, "wt");
            }
            if (output == null) {
                input.close();
                return false;
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) > 0) {
                output.write(buffer, 0, read);
            }
            output.flush();
            output.close();
            input.close();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Uri createPublicFile(Context context, String name) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, name);
                values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                return context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            }
            File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!directory.exists()) {
                directory.mkdirs();
            }
            return Uri.fromFile(new File(directory, name));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static File internalFile(Context context) {
        return new File(context.getFilesDir(), INTERNAL_NAME);
    }

    private static String versionName(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private static String processName() {
        if (Build.VERSION.SDK_INT >= 28) {
            return android.app.Application.getProcessName();
        }
        return "pid-" + Process.myPid();
    }

    private static String fileTimestamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
    }

    private static String displayTimestamp(long time) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(new Date(time));
    }

    private static String reasonName(int reason) {
        switch (reason) {
            case 1: return "EXIT_SELF";
            case 2: return "SIGNALED";
            case 3: return "LOW_MEMORY";
            case 4: return "CRASH";
            case 5: return "CRASH_NATIVE";
            case 6: return "ANR";
            case 7: return "INITIALIZATION_FAILURE";
            case 8: return "PERMISSION_CHANGE";
            case 9: return "EXCESSIVE_RESOURCE_USAGE";
            case 10: return "USER_REQUESTED";
            case 11: return "USER_STOPPED";
            case 12: return "DEPENDENCY_DIED";
            case 13: return "OTHER";
            case 14: return "FREEZER";
            case 15: return "PACKAGE_STATE_CHANGE";
            case 16: return "PACKAGE_UPDATED";
            default: return "UNKNOWN";
        }
    }

    private static String readLimited(InputStream input, int limit) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int remaining = limit;
        while (remaining > 0) {
            int read = input.read(buffer, 0, Math.min(buffer.length, remaining));
            if (read <= 0) {
                break;
            }
            output.write(buffer, 0, read);
            remaining -= read;
        }
        return output.toString("UTF-8");
    }

    private static String readAll(InputStream input, int limit) throws Exception {
        try {
            return readLimited(input, limit);
        } finally {
            input.close();
        }
    }

    private static String safeMessage(Throwable error) {
        return error.getMessage() == null ? "" : error.getMessage();
    }
}
