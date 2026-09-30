package com.meshchat.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String PAGE = "file:///android_asset/index.html";
    private WebView web;
    private FrameLayout root;
    private ValueCallback<Uri[]> fileCb;
    private Button reloadBtn;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = new FrameLayout(this);
        buildWebView();
        setContentView(root);
        web.loadUrl(PAGE);
    }

    private void buildWebView() {
        if (web != null) {
            try { web.destroy(); } catch (Exception ignored) {}
        }
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        web.setBackgroundColor(Color.parseColor("#f3efe6"));
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) {
                if (url == null) return true;
                if (url.startsWith("file://") || url.startsWith("http://") || url.startsWith("https://")) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {}
                return true;
            }
            @Override public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
                root.removeView(web);
                try { web.destroy(); } catch (Exception ignored) {}
                buildWebView();
                web.loadUrl(PAGE);
                Toast.makeText(MainActivity.this, "已恢复页面", Toast.LENGTH_SHORT).show();
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                fileCb = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(Intent.createChooser(i, "选择文件"), 100);
                return true;
            }
        });
        web.setDownloadListener((url, ua, cd, mime, len) -> {
            try {
                DownloadManager.Request rq = new DownloadManager.Request(Uri.parse(url));
                rq.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                String fn = url.substring(url.lastIndexOf('/') + 1);
                rq.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fn);
                ((DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(rq);
                Toast.makeText(this, "下载: " + fn, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "下载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        reloadBtn = new Button(this);
        reloadBtn.setText("⟳");
        reloadBtn.setTextSize(18);
        reloadBtn.setTextColor(Color.WHITE);
        reloadBtn.setBackgroundColor(0xCC2f6f5e);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                (int) (56 * getResources().getDisplayMetrics().density),
                (int) (56 * getResources().getDisplayMetrics().density),
                Gravity.BOTTOM | Gravity.END);
        lp.rightMargin = (int) (16 * getResources().getDisplayMetrics().density);
        lp.bottomMargin = (int) (90 * getResources().getDisplayMetrics().density);
        root.addView(reloadBtn, lp);
        reloadBtn.setOnClickListener(v -> {
            Toast.makeText(this, "重新加载…", Toast.LENGTH_SHORT).show();
            web.loadUrl(PAGE);
        });
    }

    @Override protected void onResume() {
        super.onResume();
        web.onResume();
        if (web.getUrl() == null || "about:blank".equals(web.getUrl())) web.loadUrl(PAGE);
    }

    @Override protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override protected void onActivityResult(int rq, int rc, Intent data) {
        if (rq == 100 && fileCb != null) {
            Uri[] uris = null;
            if (data != null && data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                uris = new Uri[n];
                for (int i = 0; i < n; i++) uris[i] = data.getClipData().getItemAt(i).getUri();
            } else if (data != null && data.getData() != null) {
                uris = new Uri[]{data.getData()};
            }
            fileCb.onReceiveValue(uris);
            fileCb = null;
        } else super.onActivityResult(rq, rc, data);
    }

    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
