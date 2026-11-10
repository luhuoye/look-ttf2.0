package com.example.ttfviewer;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class MainActivity extends Activity {
    WebView wv;
    ValueCallback<Uri[]> cb;
    final ArrayList<Uri> shared = new ArrayList<>();
    String sharedType = "*/*";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        wv = new WebView(this);
        setContentView(wv);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        wv.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }
        });
        wv.addJavascriptInterface(new Bridge(), "Android");
        wv.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cbk, FileChooserParams p) {
                if (cb != null) cb.onReceiveValue(null);
                cb = cbk;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try { startActivityForResult(Intent.createChooser(i, "选择字体文件"), 1); }
                catch (Exception e) { cb = null; return false; }
                return true;
            }
        });
        wv.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req == 1 && cb != null) {
            cb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            cb = null;
        }
    }

    static byte[] decode(String dataUrl) {
        return Base64.decode(dataUrl.substring(dataUrl.indexOf(',') + 1), Base64.DEFAULT);
    }

    class Bridge {
        @JavascriptInterface public String savePng(String dataUrl, String name) {
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TTFViewer");
                Uri u = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                try (OutputStream o = getContentResolver().openOutputStream(u)) { o.write(decode(dataUrl)); }
                return "已保存到相册：Pictures/TTFViewer/" + name;
            } catch (Exception e) {
                return "保存失败：" + e.getMessage();
            }
        }

        @JavascriptInterface public void shareReset() { shared.clear(); }

        @JavascriptInterface public String shareAdd(String dataUrl, String name, String mime) {
            try {
                File d = new File(getCacheDir(), "share");
                d.mkdirs();
                File f = new File(d, name.replaceAll("[\\\\/:*?\"<>|]", "_"));
                try (FileOutputStream o = new FileOutputStream(f)) { o.write(decode(dataUrl)); }
                shared.add(FileProvider.getUriForFile(MainActivity.this, "com.example.ttfviewer.fp", f));
                sharedType = mime;
                return "ok";
            } catch (Exception e) { return "err:" + e.getMessage(); }
        }

        @JavascriptInterface public void shareEnd() {
            runOnUiThread(new Runnable() { public void run() {
                if (shared.isEmpty()) return;
                Intent i;
                if (shared.size() == 1) {
                    i = new Intent(Intent.ACTION_SEND);
                    i.putExtra(Intent.EXTRA_STREAM, shared.get(0));
                    i.setType(sharedType);
                } else {
                    i = new Intent(Intent.ACTION_SEND_MULTIPLE);
                    i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(shared));
                    i.setType(sharedType);
                }
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(i, "分享到"));
            }});
        }
    }
}
