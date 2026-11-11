package com.featurestic.loader.sdk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import com.featurestic.loader.AppConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Locale;

/**
 * OBB Copy Task — Reference: Dynamic VIP (loader000) FileCopyTask
 * Copies host OBB from /storage/emulated/0/Android/obb/<pkg>/
 * to virtual /storage/emulated/0/SdCard/Android/obb/<pkg>/
 * using FileChannel.transferTo() on a background thread.
 */
public class FileCopyTask {

    private final Activity activity;
    private final Handler handler;
    private static boolean isCopying = false;

    public interface CopyCallback {
        void onCopyCompleted(boolean success, String message);
    }

    public FileCopyTask(Activity activity) {
        this.activity = activity;
        this.handler = new Handler(Looper.getMainLooper());
    }

    public static File getExternalStorageRoot() {
        return Environment.getExternalStorageDirectory();
    }

    public static File getExternalObbDir(String packageName) {
        return new File(getExternalStorageRoot(), "SdCard/Android/obb/" + packageName);
    }

    public static File getHostObbDir(String packageName) {
        return new File(Environment.getExternalStorageDirectory(), "Android/obb/" + packageName);
    }

    public boolean isObbCopied(String packageName) {
        File destDir = getExternalObbDir(packageName);
        return destDir.exists() && destDir.isDirectory() && destDir.list() != null && destDir.list().length > 0;
    }

    public boolean hasHostObb(String packageName) {
        File srcDir = getHostObbDir(packageName);
        if (!srcDir.exists() || !srcDir.isDirectory()) return false;
        File[] files = srcDir.listFiles((dir, name) -> name.endsWith(".obb"));
        return files != null && files.length > 0;
    }

    public void requestAllFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(intent);
            }
        }
    }

    public boolean hasAllFilesPermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                return Environment.isExternalStorageManager();
            } catch (Throwable ignored) {
                return true;
            }
        }
        return true;
    }

    public void copyObbFolderAsync(final String packageName, final CopyCallback callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasAllFilesPermission()) {
            requestAllFilesPermission();
            if (callback != null) {
                callback.onCopyCompleted(false, "All Files Access permission required!");
            }
            return;
        }

        if (isObbCopied(packageName)) {
            if (callback != null) callback.onCopyCompleted(true, "OBB already copied");
            return;
        }

        if (!hasHostObb(packageName)) {
            if (callback != null) {
                callback.onCopyCompleted(false, "Source OBB not found at /storage/emulated/0/Android/obb/" + packageName);
            }
            return;
        }

        isCopying = true;

        new AsyncTask<Void, Integer, Boolean>() {
            private String errorMsg = "";

            @Override
            protected void onPreExecute() {
                // Could show progress dialog here
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                File sourceDir = getHostObbDir(packageName);
                File destDir = getExternalObbDir(packageName);

                if (!sourceDir.exists() || !sourceDir.canRead()) {
                    errorMsg = "Source OBB not found or unreadable!";
                    return false;
                }

                if (!destDir.exists() && !destDir.mkdirs()) {
                    errorMsg = "Destination folder creation failed!";
                    return false;
                }

                File[] files = sourceDir.listFiles((dir, name) -> name.endsWith(".obb"));
                if (files == null || files.length == 0) {
                    errorMsg = "No OBB files to copy!";
                    return false;
                }

                try {
                    for (File file : files) {
                        File destFile = new File(destDir, file.getName());

                        // Skip if already exists with same size
                        if (destFile.exists() && destFile.length() == file.length()) {
                            continue;
                        }

                        try (FileChannel sourceChannel = new FileInputStream(file).getChannel();
                             FileChannel destChannel = new FileOutputStream(destFile).getChannel()) {

                            long size = sourceChannel.size();
                            long transferred = 0;
                            while (transferred < size) {
                                long bytes = sourceChannel.transferTo(transferred, size - transferred, destChannel);
                                if (bytes <= 0) break;
                                transferred += bytes;
                            }
                        }
                    }
                } catch (IOException e) {
                    errorMsg = "Error: " + e.getMessage();
                    return false;
                }
                return true;
            }

            @Override
            protected void onPostExecute(Boolean success) {
                isCopying = false;
                if (success) {
                    if (callback != null) {
                        callback.onCopyCompleted(true, "OBB copied successfully to /SdCard/Android/obb/" + packageName);
                    }
                } else {
                    if (callback != null) {
                        callback.onCopyCompleted(false, errorMsg != null ? errorMsg : "Copy failed");
                    }
                }
            }
        }.execute();
    }
}
