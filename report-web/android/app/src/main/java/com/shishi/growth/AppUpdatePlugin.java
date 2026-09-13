package com.shishi.growth;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * App 在线升级：下载安装包（带 Authorization 头访问受保护的 /api/app/{id}/apk），
 * 进度经 downloadProgress 事件推给 H5 渲染进度条，完成后唤起系统安装器。
 * Android 8+ 首次安装需用户授予「安装未知应用」权限：插件已跳转系统设置页，
 * H5 提示用户开启后重新点击更新。
 */
@CapacitorPlugin(name = "AppUpdate")
public class AppUpdatePlugin extends Plugin {

    @PluginMethod
    public void downloadAndInstall(PluginCall call) {
        String url = call.getString("url");
        String token = call.getString("token");
        if (url == null || url.isBlank()) {
            call.reject("缺少下载地址");
            return;
        }
        getBridge().execute(() -> {
            try {
                File apk = download(url, token);
                if (Build.VERSION.SDK_INT >= 26
                        && !getContext().getPackageManager().canRequestPackageInstalls()) {
                    // 未授予「安装未知应用」：跳设置页，H5 引导用户开启后重试
                    getContext().startActivity(new Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + getContext().getPackageName()))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    JSObject need = new JSObject();
                    need.put("needPermission", true);
                    call.resolve(need);
                    return;
                }
                install(apk);
                call.resolve(new JSObject());
            } catch (Exception e) {
                call.reject("下载安装失败：" + e.getMessage());
            }
        });
    }

    private File download(String url, String token) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        if (token != null && !token.isBlank()) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        int code = conn.getResponseCode();
        if (code != 200) {
            throw new IllegalStateException("HTTP " + code);
        }
        long total = conn.getContentLength();
        File out = new File(getContext().getCacheDir(), "update.apk");
        try (InputStream in = conn.getInputStream(); FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[16384];
            long done = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                fos.write(buf, 0, n);
                done += n;
                if (total > 0 && done * 100 / total != (done - n) * 100 / total) {
                    JSObject p = new JSObject();
                    p.put("progress", (int) (done * 100 / total));
                    p.put("received", done);
                    p.put("total", total);
                    notifyListeners("downloadProgress", p);
                }
            }
        } finally {
            conn.disconnect();
        }
        return out;
    }

    private void install(File apk) {
        Uri uri = FileProvider.getUriForFile(getContext(),
                getContext().getPackageName() + ".fileprovider", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
    }
}
