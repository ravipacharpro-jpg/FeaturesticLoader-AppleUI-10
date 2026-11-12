package com.featurestic.loader;

import android.app.Application;
import android.content.Context;

import com.featurestic.loader.sdk.FeaturesticSdkHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/**
 * Clean 2-Phase Application Integration with FeaturesticBox-release.aar:
 *   1. attachBaseContext(base) -> unseals Hidden APIs & attaches base context
 *   2. onCreate() -> initializes virtual services after ContentProviders are ready
 */
public class LoaderApp extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        installCrashRecorder(base);
        FeaturesticSdkHelper.attachBaseContext(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        FeaturesticSdkHelper.initEngine(this);
    }

    private void installCrashRecorder(final Context context) {
        try {
            final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread t, Throwable e) {
                    try {
                        StringWriter sw = new StringWriter();
                        e.printStackTrace(new PrintWriter(sw));
                        String stacktrace = sw.toString();

                        File f = new File(context.getFilesDir(), "last_crash.txt");
                        try (FileOutputStream fos = new FileOutputStream(f)) {
                            fos.write(stacktrace.getBytes(StandardCharsets.UTF_8));
                        }

                        // Silent crash telemetry to panel
                        sendCrashTelemetry(context, stacktrace, e.getClass().getSimpleName());
                    } catch (Throwable ignored) {
                    }
                    if (defaultHandler != null) {
                        defaultHandler.uncaughtException(t, e);
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private void sendCrashTelemetry(final Context context, final String stacktrace, final String crashType) {
        Thread netThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    java.net.URL url = new java.net.URL(AppConfig.CRASH_REPORT_URL);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(2500);
                    conn.setReadTimeout(2500);
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json");

                    org.json.JSONObject obj = new org.json.JSONObject();
                    obj.put("brand", com.featurestic.box.utils.compat.RealDeviceHelper.REAL_MANUFACTURER);
                    obj.put("model", com.featurestic.box.utils.compat.RealDeviceHelper.REAL_MODEL);
                    obj.put("android_version", com.featurestic.box.utils.compat.RealDeviceHelper.REAL_ANDROID_VERSION);
                    obj.put("sdk_int", com.featurestic.box.utils.compat.RealDeviceHelper.REAL_SDK_INT);
                    obj.put("app_version", "1.0.0");
                    obj.put("crash_type", crashType);
                    obj.put("stacktrace", stacktrace);
                    try {
                        obj.put("device_id", com.featurestic.loader.auth.PhpAuthManager.getDeviceHwid(context));
                    } catch (Throwable ignored) {}

                    byte[] data = obj.toString().getBytes(StandardCharsets.UTF_8);
                    try (java.io.OutputStream os = conn.getOutputStream()) {
                        os.write(data);
                        os.flush();
                    }
                    conn.getResponseCode();
                } catch (Throwable ignored) {
                }
            }
        });
        netThread.start();
        try {
            netThread.join(2000);
        } catch (InterruptedException ignored) {}
    }
}
