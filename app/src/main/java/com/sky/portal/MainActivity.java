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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MainActivity extends Activity {
    private WebView webView;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private ValueCallback mFilePathCallback;
    private static final int FILE_CHOOSER_RESULT_CODE = 1001;
    
    private String pendingJsonContent = null;
    private static final int REQUEST_CODE_CREATE_DOCUMENT = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        
        webView.setWebViewClient(new WebViewClient());
        
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (mFilePathCallback != null) mFilePathCallback.onReceiveValue(null);
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            if (mFilePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null) {
                String dataString = data.getDataString();
                if (dataString != null) results = new Uri[]{Uri.parse(dataString)};
            }
            mFilePathCallback.onReceiveValue(results);
            mFilePathCallback = null;
        } else if (requestCode == REQUEST_CODE_CREATE_DOCUMENT) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                try {
                    OutputStream os = getContentResolver().openOutputStream(uri);
                    if (os != null && pendingJsonContent != null) {
                        os.write(pendingJsonContent.getBytes("UTF-8"));
                        os.close();
                        mainHandler.post(() -> webView.evaluateJavascript("javascript:alert('JSON 导出成功！');", null));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    mainHandler.post(() -> webView.evaluateJavascript("javascript:alert('保存失败: " + e.getMessage() + "');", null));
                }
            }
            pendingJsonContent = null;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    public class NativeBridge {
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
                        jsonArray.put(appObj);
                    }
                    mainHandler.post(() -> webView.evaluateJavascript("javascript:showNativeApps(" + jsonArray.toString() + ")", null));
                } catch (Exception e) { e.printStackTrace(); }
            }).start();
        }

        @JavascriptInterface
        public void getAppIcon(String packageName, String callbackId) {
            new Thread(() -> {
                try {
                    PackageManager pm = getPackageManager();
                    Drawable icon = pm.getApplicationIcon(packageName);
                    Bitmap bitmap = drawableToBitmap(icon);
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                    String iconBase64 = "data:image/png;base64," + Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP);
                    mainHandler.post(() -> webView.evaluateJavascript("javascript:receiveAppIcon('" + callbackId + "', '" + iconBase64 + "')", null));
                } catch (Exception e) { e.printStackTrace(); }
            }).start();
        }

        @JavascriptInterface
        public void getApkLevels(String packageName) {
            new Thread(() -> {
                try {
                    JSONArray levelsArray = new JSONArray();
                    PackageManager pm = getPackageManager();
                    ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                    
                    ArrayList<String> apkPaths = new ArrayList<>();
                    if (appInfo.sourceDir != null) apkPaths.add(appInfo.sourceDir);
                    if (appInfo.splitSourceDirs != null) {
                        for (String split : appInfo.splitSourceDirs) apkPaths.add(split);
                    }

                    for (String apkPath : apkPaths) {
                        ZipFile zipFile = new ZipFile(apkPath);
                        Enumeration<? extends ZipEntry> entries = zipFile.entries();
                        while (entries.hasMoreElements()) {
                            ZipEntry entry = entries.nextElement();
                            String name = entry.getName();
                            String lowerName = name.toLowerCase();
                            
                            if (lowerName.contains("/levels/") && lowerName.endsWith("/objects.level.bin")) {
                                String[] parts = name.split("/");
                                String levelName = parts[parts.length - 2];
                                
                                JSONObject levelObj = new JSONObject();
                                levelObj.put("levelName", levelName);
                                levelObj.put("entryName", name);
                                levelObj.put("apkPath", apkPath);
                                levelsArray.put(levelObj);
                            }
                        }
                        zipFile.close();
                    }
                    
                    final String jsCode = "javascript:showApkLevels(" + levelsArray.toString() + ")";
                    mainHandler.post(() -> webView.evaluateJavascript(jsCode, null));
                    
                } catch (Exception e) {
                    e.printStackTrace();
                    final String errMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
                    mainHandler.post(() -> webView.evaluateJavascript("javascript:alert('扫描 APK 失败: " + errMsg + "');", null));
                }
            }).start();
        }

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
                    while ((read = is.read(buffer)) != -1) baos.write(buffer, 0, read);
                    is.close();
                    zipFile.close();

                    String base64Data = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                    mainHandler.post(() -> webView.evaluateJavascript("javascript:loadLevelData('" + base64Data + "', '" + levelName + "')", null));
                } catch (Exception e) { e.printStackTrace(); }
            }).start();
        }

        @JavascriptInterface
        public void exportJson(String fileName, String jsonContent) {
            pendingJsonContent = jsonContent;
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, fileName);
            startActivityForResult(intent, REQUEST_CODE_CREATE_DOCUMENT);
        }

        // 保存词典
        @JavascriptInterface
        public void saveDictLocally(String jsonStr) {
            try {
                File file = new File(getFilesDir(), "online_dict.json");
                FileWriter writer = new FileWriter(file);
                writer.write(jsonStr);
                writer.close();
            } catch (Exception e) { e.printStackTrace(); }
        }

        // 读取词典
        @JavascriptInterface
        public String getLocalDict() {
            try {
                File file = new File(getFilesDir(), "online_dict.json");
                if (!file.exists()) return "";
                
                FileInputStream fis = new FileInputStream(file);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = fis.read(buffer)) != -1) baos.write(buffer, 0, read);
                fis.close();
                return baos.toString("UTF-8");
            } catch (Exception e) {
                e.printStackTrace();
                return "";
            }
        }

        // 🌟 新增：打开系统浏览器用于下载更新
        @JavascriptInterface
        public void openBrowser(String url) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getApplicationContext().startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
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
