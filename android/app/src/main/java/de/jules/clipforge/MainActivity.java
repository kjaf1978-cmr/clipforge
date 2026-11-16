package de.jules.clipforge;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * ClipForge Android shell: hosts the offline web app (assets/www) in a WebView.
 * The page is served from https://appassets.androidplatform.net so that Web Workers and
 * WebAssembly (the FFmpeg engine) run exactly as in Chrome. Nothing is loaded from the internet.
 */
public class MainActivity extends Activity {
    private static final String START_URL = "https://appassets.androidplatform.net/assets/www/index.html";
    private static final int PICK_FILES = 41;
    private static final int REQ_STORAGE = 42;

    private WebView web;
    private ValueCallback<Uri[]> pendingChooser;

    /** One output file being written: chunks arrive from JavaScript as Base64 strings. */
    private static class SaveJob {
        String name, mime, display;
        Uri uri;            // content:// uri used for sharing / opening
        OutputStream out;
        File legacyFile;    // Android 9 and older
    }
    private final Map<Integer, SaveJob> jobs = new HashMap<>();
    private int nextId = 1;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#0C0F14"));
        getWindow().setNavigationBarColor(Color.parseColor("#0F1218"));

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0F1218"));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                WebResourceResponse r = loader.shouldInterceptRequest(request.getUrl());
                if (r != null) {
                    String path = request.getUrl().getPath();
                    if (path != null && path.endsWith(".wasm")) r.setMimeType("application/wasm");
                    else if (path != null && path.endsWith(".js")) r.setMimeType("text/javascript");
                }
                return r;
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // keep everything inside the app; external links open in the browser
                Uri u = request.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (pendingChooser != null) pendingChooser.onReceiveValue(null);
                pendingChooser = callback;
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"video/*", "audio/*", "application/octet-stream", "application/vnd.ms-asf", "application/x-matroska"});
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                try {
                    startActivityForResult(i, PICK_FILES);
                } catch (ActivityNotFoundException e) {
                    pendingChooser = null;
                    return false;
                }
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "ClipForgeAndroid");

        if (Build.VERSION.SDK_INT <= 28 && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
        }

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(START_URL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != PICK_FILES || pendingChooser == null) { super.onActivityResult(requestCode, resultCode, data); return; }
        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            ClipData clip = data.getClipData();
            if (clip != null && clip.getItemCount() > 0) {
                result = new Uri[clip.getItemCount()];
                for (int k = 0; k < clip.getItemCount(); k++) result[k] = clip.getItemAt(k).getUri();
            } else if (data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
        }
        pendingChooser.onReceiveValue(result);
        pendingChooser = null;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        // Leaving would lose converted results; ask the page first if work exists.
        web.evaluateJavascript("(window.__clip && (window.__clip.busy || window.__clip.queue.some(i=>i.done))) ? 1 : 0", v -> {
            if ("1".equals(v)) {
                new android.app.AlertDialog.Builder(this)
                        .setMessage("Leave ClipForge? A running conversion stops and unsaved results are lost.")
                        .setPositiveButton("Leave", (d, w) -> finish())
                        .setNegativeButton("Stay", null).show();
            } else finish();
        });
    }

    @Override
    protected void onDestroy() {
        for (SaveJob j : jobs.values()) try { if (j.out != null) j.out.close(); } catch (Exception ignored) {}
        if (web != null) web.destroy();
        super.onDestroy();
    }

    private void toast(final String t) { runOnUiThread(() -> Toast.makeText(this, t, Toast.LENGTH_LONG).show()); }

    /** Methods callable from JavaScript as window.ClipForgeAndroid.xxx() */
    public class Bridge {
        @JavascriptInterface
        public String appVersion() { return BuildConfig.VERSION_NAME; }

        @JavascriptInterface
        public void setBusy(final boolean busy) {
            runOnUiThread(() -> {
                if (busy) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        /** Creates Download/ClipForge/<name>; returns a job id or -1. */
        @JavascriptInterface
        public synchronized int saveStart(String name, String mime) {
            try {
                SaveJob j = new SaveJob();
                j.name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
                j.mime = mime;
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, j.name);
                    v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ClipForge");
                    v.put(MediaStore.MediaColumns.IS_PENDING, 1);
                    j.uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (j.uri == null) return -1;
                    j.out = getContentResolver().openOutputStream(j.uri);
                } else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ClipForge");
                    if (!dir.exists() && !dir.mkdirs()) return -1;
                    File f = new File(dir, j.name);
                    int n = 1;
                    String base = j.name.replaceAll("\\.[^.]+$", ""), ext = j.name.substring(base.length());
                    while (f.exists()) f = new File(dir, base + " (" + (n++) + ")" + ext);
                    j.legacyFile = f;
                    j.name = f.getName();
                    j.out = new FileOutputStream(f);
                }
                int id = nextId++;
                jobs.put(id, j);
                return id;
            } catch (Exception e) {
                return -1;
            }
        }

        @JavascriptInterface
        public boolean saveChunk(int id, String b64) {
            SaveJob j = jobs.get(id);
            if (j == null || j.out == null) return false;
            try {
                j.out.write(Base64.decode(b64, Base64.DEFAULT));
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        /** Finishes the file; returns where it was saved (shown to the user) or "" on error. */
        @JavascriptInterface
        public String saveEnd(int id) {
            SaveJob j = jobs.get(id);
            if (j == null) return "";
            try {
                j.out.close();
                j.out = null;
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    getContentResolver().update(j.uri, v, null, null);
                    String real = j.name;
                    try (android.database.Cursor c = getContentResolver().query(j.uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null)) {
                        if (c != null && c.moveToFirst()) real = c.getString(0);
                    } catch (Exception ignored) {}
                    j.display = "Download/ClipForge/" + real;
                } else {
                    MediaScannerConnection.scanFile(MainActivity.this, new String[]{j.legacyFile.getAbsolutePath()}, new String[]{j.mime}, null);
                    j.uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".files", j.legacyFile);
                    j.display = "Download/ClipForge/" + j.legacyFile.getName();
                }
                toast("Saved: " + j.display);
                return j.display;
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void saveAbort(int id) {
            SaveJob j = jobs.remove(id);
            if (j == null) return;
            try { if (j.out != null) j.out.close(); } catch (Exception ignored) {}
            try {
                if (j.uri != null && Build.VERSION.SDK_INT >= 29) getContentResolver().delete(j.uri, null, null);
                if (j.legacyFile != null) //noinspection ResultOfMethodCallIgnored
                    j.legacyFile.delete();
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void share(int id) {
            final SaveJob j = jobs.get(id);
            if (j == null || j.uri == null) return;
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType(j.mime);
                i.putExtra(Intent.EXTRA_STREAM, j.uri);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(i, "Share " + j.name));
            });
        }

        /** Opens the file in another app (video player). Returns false if no app can open it. */
        @JavascriptInterface
        public boolean open(int id) {
            final SaveJob j = jobs.get(id);
            if (j == null || j.uri == null) return false;
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(j.uri, j.mime);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (i.resolveActivity(getPackageManager()) == null) {
                // fall back to a generic video type so any player is offered
                i.setDataAndType(j.uri, j.mime.startsWith("audio") ? "audio/*" : "video/*");
                if (i.resolveActivity(getPackageManager()) == null) return false;
            }
            final Intent fi = i;
            runOnUiThread(() -> {
                try { startActivity(fi); } catch (ActivityNotFoundException e) { toast("No app can open this file."); }
            });
            return true;
        }
    }
}
