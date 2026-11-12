package com.featurestic.loader.update;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.featurestic.loader.AppConfig;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * In-App 1-Click Auto-Update Controller for Featurestic Loader.
 * Checks for updates delivered via the Panel /connect endpoint,
 * prompts the user with an Apple-style update dialog, downloads the APK,
 * and launches the Android package installer.
 */
public final class InAppUpdateManager {

    private static final String PANEL_CONNECT_URL = "https://featurestic-box-panel.onrender.com/connect";
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    private InAppUpdateManager() {}

    public static void checkAndPromptUpdate(final Activity activity, final String sdkKey) {
        if (activity == null || activity.isFinishing()) return;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String pkg = activity.getPackageName();
                    String key = (sdkKey != null && !sdkKey.trim().isEmpty()) ? sdkKey.trim() : AppConfig.SDK_LICENSE_KEY;
                    String hwid = com.featurestic.loader.auth.PhpAuthManager.getDeviceHwid(activity);

                    JSONObject postJson = new JSONObject();
                    postJson.put("user_key", key);
                    postJson.put("serial", hwid);
                    postJson.put("pkg", pkg);

                    URL url = new URL(PANEL_CONNECT_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Accept", "application/json");

                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(postJson.toString().getBytes(StandardCharsets.UTF_8));
                        os.flush();
                    }

                    int code = conn.getResponseCode();
                    InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
                    StringBuilder sb = new StringBuilder();
                    if (is != null) {
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = br.readLine()) != null) sb.append(line);
                        }
                    }

                    String resStr = sb.toString().trim();
                    if (!resStr.startsWith("{")) return;

                    JSONObject resp = new JSONObject(resStr);

                    // 1. Emergency Safe Mode or Device Ban verification
                    if (!resp.optBoolean("status", true)) {
                        final String reason = resp.optString("reason", "");
                        final String message = resp.optString("message", "");
                        if ("SAFE_MODE_ACTIVE".equalsIgnoreCase(reason) || "DEVICE_BANNED".equalsIgnoreCase(reason)) {
                            sMainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (activity.isFinishing()) return;
                                    showSecurityAlert(activity, reason, message);
                                }
                            });
                            return;
                        }
                    }

                    JSONObject data = resp.optJSONObject("data");
                    if (data == null) return;

                    // Save .so SHA-256 Anti-Tamper Checksum delivered from /connect
                    String serverSoSha256 = data.optString("so_sha256", "").trim();
                    String serverPluginUrl = data.optString("plugin_url", "").trim();
                    activity.getSharedPreferences("featurestic_so_security", Context.MODE_PRIVATE)
                            .edit()
                            .putString("server_so_sha256", serverSoSha256)
                            .putString("server_plugin_url", serverPluginUrl)
                            .apply();

                    // 2. Remote Broadcast In-App Notice Popup
                    JSONObject noticeObj = data.optJSONObject("notice");
                    if (noticeObj != null) {
                        final int noticeId = noticeObj.optInt("id", 0);
                        final String noticeMsg = noticeObj.optString("message", "").trim();
                        if (noticeId > 0 && !noticeMsg.isEmpty()) {
                            checkAndShowNoticeDialog(activity, noticeId, noticeMsg);
                        }
                    }

                    // 3. 1-Click APK Auto-Update Delivery
                    JSONObject updateObj = data.optJSONObject("update");
                    if (updateObj == null) return;

                    final boolean hasUpdate = updateObj.optBoolean("has_update", false);
                    final String latestVersion = updateObj.optString("latest_version", "").trim();
                    final String apkUrl = updateObj.optString("apk_url", "").trim();
                    final String changelog = updateObj.optString("changelog", "Bug fixes and performance enhancements.").trim();
                    final boolean forceUpdate = updateObj.optBoolean("force_update", false);

                    if (!hasUpdate || apkUrl.isEmpty() || latestVersion.isEmpty()) {
                        return;
                    }

                    PackageInfo pInfo = activity.getPackageManager().getPackageInfo(pkg, 0);
                    final String curVer = pInfo.versionName;

                    if (latestVersion.equalsIgnoreCase(curVer)) {
                        return; // Already up to date
                    }

                    sMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (activity.isFinishing()) return;
                            showUpdateDialog(activity, latestVersion, curVer, apkUrl, changelog, forceUpdate);
                        }
                    });

                } catch (Throwable ignored) {
                }
            }
        }).start();
    }

    private static void showUpdateDialog(final Activity activity,
                                         final String latestVer,
                                         final String curVer,
                                         final String apkUrl,
                                         final String changelog,
                                         final boolean forceUpdate) {
        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        b.setTitle("New Loader Update Available");
        b.setMessage("A new update has been released by your administrator.\n\n" +
                "• Current Version: v" + curVer + "\n" +
                "• New Version: v" + latestVer + "\n\n" +
                "What's New:\n" + changelog + "\n\n" +
                "Would you like to update now?");
        b.setCancelable(!forceUpdate);

        b.setPositiveButton("Update Now (1-Click)", (dialog, which) -> {
            dialog.dismiss();
            downloadAndInstallApk(activity, apkUrl);
        });

        if (!forceUpdate) {
            b.setNegativeButton("Later", (dialog, which) -> dialog.dismiss());
        }

        try {
            AlertDialog d = b.create();
            d.show();
        } catch (Throwable ignored) {}
    }

    private static void downloadAndInstallApk(final Activity activity, final String apkUrl) {
        final ProgressDialog pd = new ProgressDialog(activity);
        pd.setTitle("Downloading Loader Update");
        pd.setMessage("Please wait while the new APK is downloaded...");
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setMax(100);
        pd.setCancelable(false);
        pd.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                File apkFile = null;
                try {
                    File dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (dir == null) dir = activity.getFilesDir();
                    apkFile = new File(dir, "FeaturesticLoader-Update.apk");
                    if (apkFile.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        apkFile.delete();
                    }

                    URL url = new URL(apkUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    conn.connect();

                    int fileLength = conn.getContentLength();
                    InputStream input = new BufferedInputStream(conn.getInputStream());
                    OutputStream output = new FileOutputStream(apkFile);

                    byte[] data = new byte[8192];
                    long total = 0;
                    int count;
                    while ((count = input.read(data)) != -1) {
                        total += count;
                        if (fileLength > 0) {
                            final int progress = (int) (total * 100 / fileLength);
                            sMainHandler.post(() -> pd.setProgress(progress));
                        }
                        output.write(data, 0, count);
                    }

                    output.flush();
                    output.close();
                    input.close();

                    final File finalApk = apkFile;
                    sMainHandler.post(() -> {
                        if (pd.isShowing()) pd.dismiss();
                        installApk(activity, finalApk);
                    });

                } catch (final Throwable e) {
                    if (apkFile != null && apkFile.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        apkFile.delete();
                    }
                    sMainHandler.post(() -> {
                        if (pd.isShowing()) pd.dismiss();
                        Toast.makeText(activity, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }
        }).start();
    }

    private static void installApk(Activity activity, File apkFile) {
        if (activity == null || apkFile == null || !apkFile.exists()) return;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.getPackageManager().canRequestPackageInstalls()) {
                    Toast.makeText(activity, "Please allow 'Install unknown apps' to install the update", Toast.LENGTH_LONG).show();
                    Intent permIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivity(permIntent);
                    return;
                }
            }

            Uri apkUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                apkUri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".provider", apkFile);
            } else {
                apkUri = Uri.fromFile(apkFile);
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);

        } catch (Throwable t) {
            Toast.makeText(activity, "Failed to launch installer: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static void checkAndShowNoticeDialog(final Activity activity, final int noticeId, final String noticeMsg) {
        final SharedPreferences sp = activity.getSharedPreferences("featurestic_notices", Context.MODE_PRIVATE);
        int lastSeenId = sp.getInt("last_notice_id", 0);
        if (noticeId > lastSeenId) {
            sMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (activity.isFinishing()) return;
                    AlertDialog.Builder b = new AlertDialog.Builder(activity);
                    b.setTitle("Server Announcement");
                    b.setMessage(noticeMsg);
                    b.setPositiveButton("Understood", (dialog, which) -> {
                        sp.edit().putInt("last_notice_id", noticeId).apply();
                        dialog.dismiss();
                    });
                    b.setCancelable(true);
                    b.show();
                }
            });
        }
    }

    private static void showSecurityAlert(final Activity activity, final String reason, final String message) {
        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        b.setTitle("SAFE_MODE_ACTIVE".equalsIgnoreCase(reason) ? "Game Security Alert" : "Access Denied");
        b.setMessage(message != null && !message.isEmpty() ? message : "Service temporarily suspended.");
        b.setCancelable(false);
        b.setPositiveButton("Exit", (dialog, which) -> {
            dialog.dismiss();
            activity.finishAffinity();
        });
        b.show();
    }
}
