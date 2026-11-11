package com.featurestic.loader.sdk;

import android.app.Activity;
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
        // Try listFiles first
        File[] files = srcDir.listFiles((dir, name) -> name.endsWith(".obb"));
        if (files != null && files.length > 0) return true;
        // Fallback: probe known OBB filenames directly
        String[] knownNames = new String[]{
                "main.21535." + packageName + ".obb",
                "patch.21535." + packageName + ".obb",
                "main.21536." + packageName + ".obb",
                "main.21534." + packageName + ".obb",
        };
        for (String name : knownNames) {
            File f = new File(srcDir, name);
            if (f.isFile() && f.length() >= 1024L * 1024L) return true;
        }
        return false;
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
                callback.onCopyCompleted(false, "All Files Access permission required! Go to Settings and enable it.");
            }
            return;
        }

        if (isObbCopied(packageName)) {
            if (callback != null) callback.onCopyCompleted(true, "OBB already exists in virtual path");
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
            private File[] foundFiles = null;

            @Override
            protected void onPreExecute() {
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                File sourceDir = getHostObbDir(packageName);
                File destDir = getExternalObbDir(packageName);

                // Check source readability
                if (!sourceDir.exists()) {
                    errorMsg = "Source directory does not exist: " + sourceDir.getAbsolutePath();
                    return false;
                }
                if (!sourceDir.canRead()) {
                    errorMsg = "Source directory not readable (Linux permission blocks non-root access)";
                    return false;
                }

                if (!destDir.exists() && !destDir.mkdirs()) {
                    errorMsg = "Destination folder creation failed!";
                    return false;
                }

                // Collect source OBB files (try listFiles, then direct filename probe)
                java.util.List<File> srcFiles = new java.util.ArrayList<>();
                File[] list = sourceDir.listFiles((dir, name) -> name.endsWith(".obb"));
                if (list != null) {
                    for (File f : list) {
                        if (f != null && f.isFile() && f.length() >= 1024L * 1024L) {
                            srcFiles.add(f);
                        }
                    }
                }
                // Fallback: probe known filenames directly
                if (srcFiles.isEmpty()) {
                    String[] knownNames = new String[]{
                            "main.21535." + packageName + ".obb",
                            "patch.21535." + packageName + ".obb",
                            "main.21536." + packageName + ".obb",
                            "main.21534." + packageName + ".obb",
                    };
                    for (String name : knownNames) {
                        File f = new File(sourceDir, name);
                        if (f.isFile() && f.length() >= 1024L * 1024L && !srcFiles.contains(f)) {
                            srcFiles.add(f);
                        }
                    }
                }
                if (srcFiles.isEmpty()) {
                    errorMsg = "No valid OBB files found in source directory!";
                    return false;
                }

                foundFiles = srcFiles.toArray(new File[0]);

                long totalBytes = 0;
                for (File f : foundFiles) totalBytes += f.length();

                long copiedBytes = 0;
                try {
                    for (File file : foundFiles) {
                        File destFile = new File(destDir, file.getName());

                        // Skip if already exists with same size
                        if (destFile.exists() && destFile.length() == file.length()) {
                            copiedBytes += file.length();
                            publishProgress((int) ((copiedBytes * 100) / Math.max(totalBytes, 1)));
                            continue;
                        }

                        boolean copied = false;

                        // Method 1: FileChannel.transferTo()
                        try {
                            try (FileChannel sourceChannel = new FileInputStream(file).getChannel();
                                 FileChannel destChannel = new FileOutputStream(destFile).getChannel()) {
                                long size = sourceChannel.size();
                                long transferred = 0;
                                while (transferred < size) {
                                    long bytes = sourceChannel.transferTo(transferred, size - transferred, destChannel);
                                    if (bytes <= 0) break;
                                    transferred += bytes;
                                    copiedBytes += bytes;
                                    publishProgress((int) ((copiedBytes * 100) / Math.max(totalBytes, 1)));
                                }
                            }
                            if (destFile.exists() && destFile.length() == file.length()) {
                                copied = true;
                            }
                        } catch (IOException e) {
                            // Fall through to Method 2
                        }

                        // Method 2: FileInputStream + FileOutputStream (8KB buffer like reference loaders)
                        if (!copied) {
                            try (FileInputStream in = new FileInputStream(file);
                                 FileOutputStream out = new FileOutputStream(destFile)) {
                                byte[] buffer = new byte[8192];
                                int bytesRead;
                                while ((bytesRead = in.read(buffer)) != -1) {
                                    out.write(buffer, 0, bytesRead);
                                    copiedBytes += bytesRead;
                                    publishProgress((int) ((copiedBytes * 100) / Math.max(totalBytes, 1)));
                                }
                                out.flush();
                            }
                            if (destFile.exists() && destFile.length() == file.length()) {
                                copied = true;
                            }
                        }

                        if (!copied) {
                            errorMsg = "Failed to copy: " + file.getName();
                            return false;
                        }
                    }
                } catch (IOException e) {
                    errorMsg = "Error: " + e.getMessage();
                    return false;
                }
                return true;
            }

            @Override
            protected void onProgressUpdate(Integer... values) {
                // Progress callback could update UI
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
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}
