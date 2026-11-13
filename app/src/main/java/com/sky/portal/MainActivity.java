package com.sky.portal;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MainActivity extends Activity {
    private WebView webView;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private ValueCallback<Uri[]> mFilePathCallback;
    private static final int FILE_CHOOSER_RESULT_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        // 允许通过 file:// 访问其他文件
        settings.setAllowContentAccess(true);
        
        webView.setWebViewClient(new WebViewClient());
        
        // 🌟 核心修复 1：重写 WebChromeClient 以支持网页里的 <input type="file">
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (mFilePathCallback != null) {
                    mFilePathCallback.onReceiveValue(null);
                }
                mFilePathCallback = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER_RESULT_CODE);
                } catch (Exception e) {
                    mFilePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        webView.addJavascriptInterface(new NativeBridge(), "AndroidNative");
        webView.loadUrl("file:///android_asset/index.html");
    }

    // 接收文件选择结果
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            if (mFilePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null) {
                String dataString = data.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                }
            }
            mFilePathCallback.onReceiveValue(results);
            mFilePathCallback = null;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
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

        // 2. 扫描 APK 关卡 (支持 Split APK 分包和忽略大小写)
        @JavascriptInterface
        public void getApkLevels(String packageName) {
            new Thread(() -> {
                try {
                    JSONArray levelsArray = new JSONArray();
                    PackageManager pm = getPackageManager();
                    ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                    
                    // 收集主包和所有分包路径
                    java.util.List<String> apkPaths = new java.util.ArrayList<>();
                    apkPaths.add(appInfo.sourceDir); // 主包
                    if (appInfo.splitSourceDirs != null) {
                        for (String split : appInfo.splitSourceDirs) {
                            apkPaths.add(split); // 分包
                        }
                    }

                    for (String apkPath : apkPaths) {
                        ZipFile zipFile = new ZipFile(apkPath);
                        Enumeration<? extends ZipEntry> entries = zipFile.entries();
                        while (entries.hasMoreElements()) {
                            ZipEntry entry = entries.nextElement();
                            String name = entry.getName();
                            String lowerName = name.toLowerCase();
                            
                            // 放宽匹配规则：只要路径包含 levels 且以 objects.level.bin 结尾
                            if (lowerName.contains("/levels/") && lowerName.endsWith("/objects.level.bin")) {
                                String[] parts = name.split("/");
                                String levelName = "";
                                for (int i = 0; i < parts.length; i++) {
                                    if (parts[i].equalsIgnoreCase("levels") && i + 1 < parts.length) {
                                        levelName = parts[i + 1];
                                        break;
                                    }
                                }
                                if (!levelName.isEmpty()) {
                                    JSONObject levelObj = new JSONObject();
                                    levelObj.put("levelName", levelName);
                                    levelObj.put("entryName", name);
                                    levelObj.put("apkPath", apkPath); // 记录这个文件在哪个 APK 里
                                    levelsArray.put(levelObj);
                                }
                            }
                        }
                        zipFile.close();
                    }

                    mainHandler.post(() -> webView.evaluateJavascript("javascript:showApkLevels(" + levelsArray.toString() + ")", null));
                } catch (Exception e) { 
                    e.printStackTrace(); 
                    mainHandler.post(() -> webView.evaluateJavascript("alert('扫描 APK 时发生错误: " + e.getMessage() + "');", null));
                }
            }).start();
        }

        // 3. 提取具体的 bin 文件
        @JavascriptInterface
        public void extractLevelData(String apkPath, String entryName, String levelName) {
            new Thread(() -> {
                try {
                    ZipFile zipFile = new ZipFile(apkPath);
                    ZipEntry entry = zipFile.getEntry(entryName);
                    if (entry == null) return;
                    
                    InputStream is = zipFile.getInputStream(entry);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, read);
                    }
                    is.close();
                    zipFile.close();

                    String base64Data = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

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
