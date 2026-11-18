package com.zeekoltd.setat.pro;

import android.app.ActivityManager;
import android.app.Application;
import android.app.ApplicationExitInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Process;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Local crash diagnostics. Reports stay on the device until the user copies or dismisses them. */
public final class CrashDiagnostics {
    private static final String PREFS = "crash_diagnostics";
    private static final String KEY_LAST_START = "last_process_start";
    private static final String KEY_LAST_EVENT = "last_ad_event";
    private static final String KEY_LAST_EVENT_TIME = "last_ad_event_time";
    private static final String KEY_PENDING_EXIT_TIME = "pending_exit_time";
    private static final String KEY_HANDLED_EXIT_TIME = "handled_exit_time";
    private static final String REPORT_FILE = "crash_report.txt";
    private static final int MAX_REPORT_CHARS = 120_000;
    private static volatile Context appContext;
    private static volatile Thread.UncaughtExceptionHandler previousHandler;
    private static volatile boolean installed;

    private CrashDiagnostics() { }

    public static synchronized void install(Application application) {
        if (installed) return;
        appContext = application.getApplicationContext();
        collectPreviousSystemExit(appContext);
        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                writeReport(appContext, buildJavaCrashReport(appContext, thread, error));
            } catch (Throwable ignored) {
                // Crash handling must never prevent Android's original crash handler from running.
            }
            Thread.UncaughtExceptionHandler old = previousHandler;
            if (old != null) {
                old.uncaughtException(thread, error);
            } else {
                Process.killProcess(Process.myPid());
                System.exit(10);
            }
        });
        installed = true;
        recordAdEvent("process_started");
    }

    /** Store the latest ad-flow breadcrumb synchronously so it survives a process crash. */
    public static void recordAdEvent(String event) {
        Context context = appContext;
        if (context == null || event == null) return;
        try {
            String safeEvent = event.length() > 400 ? event.substring(0, 400) : event;
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_LAST_EVENT, safeEvent)
                    .putLong(KEY_LAST_EVENT_TIME, System.currentTimeMillis())
                    .commit();
        } catch (Throwable ignored) { }
    }

    /** Persist a synchronous ad-show exception even when the SDK call was caught by app code. */
    public static void saveHandledException(String operation, Throwable error) {
        Context context = appContext;
        if (context == null || error == null) return;
        try {
            File file = new File(context.getFilesDir(), REPORT_FILE);
            if (file.exists()) return;
            StringWriter stack = new StringWriter();
            error.printStackTrace(new PrintWriter(stack));
            String details = stack.toString();
            String report = "SETAT AD-SHOW ERROR REPORT\n"
                    + "Time: " + timestamp(System.currentTimeMillis()) + "\n"
                    + "Operation: " + operation + "\n"
                    + deviceInfo(context) + "\n"
                    + lastEventInfo(context) + "\n"
                    + "Likely ad SDK from stack: " + detectAdSdk(details) + "\n\n"
                    + "FULL STACK TRACE\n" + details;
            writeReport(context, limit(report));
        } catch (Throwable ignored) { }
    }

    /** Read without deleting; the report remains available until the user chooses an action. */
    public static String getPendingReport(Context context) {
        try {
            File file = new File(context.getFilesDir(), REPORT_FILE);
            if (!file.isFile() || file.length() <= 0 || file.length() > MAX_REPORT_CHARS * 4L) return null;
            byte[] bytes = new byte[(int) file.length()];
            try (InputStream in = context.openFileInput(REPORT_FILE)) {
                int offset = 0;
                while (offset < bytes.length) {
                    int count = in.read(bytes, offset, bytes.length - offset);
                    if (count < 0) break;
                    offset += count;
                }
                return new String(bytes, 0, offset, StandardCharsets.UTF_8);
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean copyToClipboard(Context context, String report) {
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || report == null) return false;
            clipboard.setPrimaryClip(ClipData.newPlainText("App crash report", report));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void clearPendingReport(Context context) {
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            long pendingTime = prefs.getLong(KEY_PENDING_EXIT_TIME, 0L);
            prefs.edit().putLong(KEY_HANDLED_EXIT_TIME, pendingTime).remove(KEY_PENDING_EXIT_TIME).commit();
            context.deleteFile(REPORT_FILE);
        } catch (Throwable ignored) { }
    }

    private static String buildJavaCrashReport(Context context, Thread thread, Throwable error) {
        StringWriter stack = new StringWriter();
        error.printStackTrace(new PrintWriter(stack));
        String stackText = stack.toString();
        StringBuilder report = new StringBuilder(4096);
        report.append("SETAT APP CRASH REPORT\n")
                .append("Time: ").append(timestamp(System.currentTimeMillis())).append('\n')
                .append("Type: Uncaught Java exception\n")
                .append("Process: ").append(context.getPackageName()).append('\n')
                .append("Thread: ").append(thread.getName()).append(" (id=").append(thread.getId()).append(")\n")
                .append(deviceInfo(context)).append('\n')
                .append(lastEventInfo(context)).append('\n')
                .append("Likely ad SDK from stack: ").append(detectAdSdk(stackText)).append("\n\n")
                .append("FULL STACK TRACE\n").append(stackText);
        return limit(report.toString());
    }

    private static String deviceInfo(Context context) {
        String version = "unknown";
        long versionCode = -1;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            version = info.versionName == null ? "unknown" : info.versionName;
            versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? info.getLongVersionCode() : info.versionCode;
        } catch (Throwable ignored) { }
        return "App version: " + version + " (" + versionCode + ")\n"
                + "Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n"
                + "Device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                + "ABIs: " + join(Build.SUPPORTED_ABIS);
    }

    private static String lastEventInfo(Context context) {
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String event = prefs.getString(KEY_LAST_EVENT, "none recorded");
            long eventTime = prefs.getLong(KEY_LAST_EVENT_TIME, 0L);
            return "Last ad/app event: " + event + "\nEvent time: "
                    + (eventTime == 0L ? "unknown" : timestamp(eventTime));
        } catch (Throwable ignored) {
            return "Last ad/app event: unavailable";
        }
    }

    private static String detectAdSdk(String stack) {
        String s = stack.toLowerCase(Locale.ROOT);
        if (s.contains("com.applovin")) return "AppLovin (stack package match)";
        if (s.contains("com.bytedance") || s.contains("com.pangle")) return "Pangle/ByteDance (stack package match)";
        if (s.contains("sg.bigo")) return "BIGO Ads (stack package match)";
        if (s.contains("com.google.android.gms.ads") || s.contains("com.google.android.libraries.ads")) return "Google AdMob (stack package match)";
        if (s.contains("com.inmobi")) return "InMobi (stack package match)";
        if (s.contains("com.unity3d") || s.contains("com.ironsource")) return "Unity Ads/LevelPlay (stack package match)";
        if (s.contains("com.yandex.mobile.ads")) return "Yandex SDK (stack package match)";
        if (s.contains("com.mbridge")) return "Mintegral (stack package match)";
        if (s.contains("com.vungle")) return "Liftoff/Vungle (stack package match)";
        return "not identifiable from Java stack; inspect full trace and last event";
    }

    private static void collectPreviousSystemExit(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        Api30.collectPreviousSystemExit(context);
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.R)
    private static final class Api30 {
        private static void collectPreviousSystemExit(Context context) {
            try {
                android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                if (new File(context.getFilesDir(), REPORT_FILE).exists()) return;
                long previousStart = prefs.getLong(KEY_LAST_START, 0L);
                long handledTime = prefs.getLong(KEY_HANDLED_EXIT_TIME, 0L);
                if (previousStart == 0L) return;
                ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (manager == null) return;
                List<ApplicationExitInfo> exits = manager.getHistoricalProcessExitReasons(context.getPackageName(), 0, 12);
                if (exits == null) return;
                ApplicationExitInfo match = null;
                for (ApplicationExitInfo info : exits) {
                    if (info == null || info.getTimestamp() < previousStart || info.getTimestamp() <= handledTime) continue;
                    String processName = info.getProcessName();
                    if (processName != null && !processName.startsWith(context.getPackageName())) continue;
                    if (!isAbnormalExit(info.getReason())) continue;
                    if (match == null || info.getTimestamp() > match.getTimestamp()) match = info;
                }
                if (match == null) return;
                StringBuilder report = new StringBuilder(2048);
                report.append("SETAT APP EXIT REPORT\n")
                        .append("Time: ").append(timestamp(match.getTimestamp())).append('\n')
                        .append("Type: Android recorded process exit\n")
                        .append("Reason: ").append(reasonName(match.getReason())).append(" (code ").append(match.getReason()).append(")\n")
                        .append("Process: ").append(match.getProcessName()).append('\n')
                        .append("Status/signal: ").append(match.getStatus()).append('\n')
                        .append("System description: ").append(match.getDescription()).append('\n')
                        .append(deviceInfo(context)).append('\n')
                        .append(lastEventInfo(context)).append('\n');
                appendSystemTrace(report, match);
                prefs.edit().putLong(KEY_PENDING_EXIT_TIME, match.getTimestamp()).commit();
                writeReport(context, limit(report.toString()));
            } catch (Throwable ignored) { }
            finally {
                try {
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                            .putLong(KEY_LAST_START, System.currentTimeMillis()).commit();
                } catch (Throwable ignored) { }
            }
        }

        private static boolean isAbnormalExit(int reason) {
            return reason == ApplicationExitInfo.REASON_CRASH
                    || reason == ApplicationExitInfo.REASON_CRASH_NATIVE
                    || reason == ApplicationExitInfo.REASON_ANR
                    || reason == ApplicationExitInfo.REASON_INITIALIZATION_FAILURE
                    || reason == ApplicationExitInfo.REASON_DEPENDENCY_DIED
                    || reason == ApplicationExitInfo.REASON_SIGNALED;
        }

        private static String reasonName(int reason) {
            switch (reason) {
                case ApplicationExitInfo.REASON_CRASH: return "Java crash";
                case ApplicationExitInfo.REASON_CRASH_NATIVE: return "Native crash";
                case ApplicationExitInfo.REASON_ANR: return "Application not responding (ANR)";
                case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE: return "Initialization failure";
                case ApplicationExitInfo.REASON_DEPENDENCY_DIED: return "Dependency process died";
                case ApplicationExitInfo.REASON_SIGNALED: return "Process terminated by signal";
                default: return "Other";
            }
        }

        private static void appendSystemTrace(StringBuilder report, ApplicationExitInfo info) {
            try (InputStream input = info.getTraceInputStream()) {
                if (input == null) {
                    report.append("System trace: not provided by Android.\n");
                    return;
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int total = 0;
                int count;
                while ((count = input.read(buffer)) != -1 && total < 24_000) {
                    int accepted = Math.min(count, 24_000 - total);
                    output.write(buffer, 0, accepted);
                    total += accepted;
                }
                byte[] bytes = output.toByteArray();
                String text = new String(bytes, StandardCharsets.UTF_8);
                if (looksReadable(text)) report.append("SYSTEM TRACE (text)\n").append(text).append('\n');
                else report.append("System trace: Android supplied a binary trace (")
                        .append(bytes.length).append(" bytes); raw binary omitted.\n");
            } catch (Throwable error) {
                report.append("System trace read failed: ").append(error.getClass().getSimpleName()).append('\n');
            }
        }
    }

    private static boolean looksReadable(String text) {
        if (text == null || text.isEmpty()) return false;
        int printable = 0;
        int sample = Math.min(text.length(), 512);
        for (int i = 0; i < sample; i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t' || c >= 32) printable++;
        }
        return printable > sample * 0.85;
    }

    private static void writeReport(Context context, String report) {
        if (context == null || report == null) return;
        try (FileOutputStream output = context.openFileOutput(REPORT_FILE, Context.MODE_PRIVATE)) {
            output.write(limit(report).getBytes(StandardCharsets.UTF_8));
            output.flush();
            output.getFD().sync();
        } catch (Throwable ignored) { }
    }

    private static String limit(String value) {
        if (value == null || value.length() <= MAX_REPORT_CHARS) return value;
        return value.substring(0, MAX_REPORT_CHARS) + "\n[Report truncated]";
    }

    private static String timestamp(long time) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(new Date(time));
    }

    private static String join(String[] values) {
        if (values == null || values.length == 0) return "unknown";
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append(", ");
            result.append(value);
        }
        return result.toString();
    }
}
