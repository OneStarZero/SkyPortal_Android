package com.sky.portal;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MainActivity extends Activity {
    private WebView webView;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new NativeBridge(), "AndroidNative");
        webView.loadUrl("file:///android_asset/index.html");
    }

    public class NativeBridge {
        // 1. 获取已安装应用列表
        @JavascriptInterface
        public void getInstalledApps() {
            new Thread(() -> {
                try {
                    PackageManager pm = getPackageManager();
                    List<PackageInfo> packages = pm.getInstalledPackages(0);
                    JSONArray jsonArray = new JSONArray();

                    for (PackageInfo pkg : packages) {
                        ApplicationInfo appInfo = pkg.applicationInfo;
                        if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;

                        JSONObject appObj = new JSONObject();
                        appObj.put("appName", pm.getApplicationLabel(appInfo).toString());
                        appObj.put("packageName", pkg.packageName);
                        appObj.put("apkPath", appInfo.sourceDir);

                        try {
                            Drawable icon = pm.getApplicationIcon(pkg.packageName);
                            Bitmap bitmap = drawableToBitmap(icon);
                            ByteArrayOutputStream stream = new ByteArrayOutputStream();
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                            appObj.put("icon", "data:image/png;base64," + Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP));
                        } catch (Exception e) {
                            appObj.put("icon", "");
                        }
                        jsonArray.put(appObj);
                    }

                    mainHandler.post(() -> webView.evaluateJavascript("javascript:showNativeApps(" + jsonArray.toString() + ")", null));
                } catch (Exception e) { e.printStackTrace(); }
            }).start();
        }

        // 2. 获取特定 APK 里的所有关卡路径
        @JavascriptInterface
        public void getApkLevels(String apkPath) {
            new Thread(() -> {
                try {
                    JSONArray levelsArray = new JSONArray();
                    ZipFile zipFile = new ZipFile(apkPath);
                    Enumeration<? extends ZipEntry> entries = zipFile.entries();
                    
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (name.startsWith("Data/Levels/") && name.endsWith("/Objects.level.bin")) {
                            String[] parts = name.split("/");
                            JSONObject levelObj = new JSONObject();
                            levelObj.put("levelName", parts[parts.length - 2]);
                            levelObj.put("entryName", name);
                            levelsArray.put(levelObj);
                        }
                    }
                    zipFile.close();

                    mainHandler.post(() -> webView.evaluateJavascript("javascript:showApkLevels(" + levelsArray.toString() + ")", null));
                } catch (Exception e) { e.printStackTrace(); }
            }).start();
        }

        // 3. 提取具体的 bin 文件并转 Base64 传给网页
        @JavascriptInterface
        public void extractLevelData(String apkPath, String entryName) {
            new Thread(() -> {
                try {
                    ZipFile zipFile = new ZipFile(apkPath);
                    ZipEntry entry = zipFile.getEntry(entryName);
                    if (entry == null) return;
                    
                    java.io.InputStream is = zipFile.getInputStream(entry);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, read);
                    }
                    is.close();
                    zipFile.close();

                    String base64Data = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                    String levelName = entryName.split("/")[2];

                    mainHandler.post(() -> webView.evaluateJavascript("javascript:loadLevelData('" + base64Data + "', '" + levelName + "')", null));
                } catch (Exception e) { e.printStackTrace(); }
            }).start();
        }

        private Bitmap drawableToBitmap(Drawable drawable) {
            if (drawable instanceof BitmapDrawable) return ((BitmapDrawable) drawable).getBitmap();
            Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            return bitmap;
        }
    }
          }
