package com.meshchat.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivity extends Activity {
    private WebView web;
    private FrameLayout root;
    private SharedPreferences sp;
    private ValueCallback<Uri[]> fileCb;
    private String loadedUrl = "";
    private Button reloadBtn;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("meshchat", MODE_PRIVATE);
        root = new FrameLayout(this);
        buildWebView();
        setContentView(root);
        String url = sp.getString("server", "");
        if (url.isEmpty()) promptServer(true); else load(url);
    }

    /** 构建 WebView 及全部客户端(渲染进程崩溃后可整体重建) */
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
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        web.setBackgroundColor(Color.parseColor("#0b0e17"));
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) {
                if (url.startsWith(base())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {}
                return true;
            }
            @Override public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
                // 渲染进程被系统杀死(切后台回收)→ 重建并重载, 避免黑屏卡死
                root.removeView(web);
                try { web.destroy(); } catch (Exception ignored) {}
                buildWebView();
                web.loadUrl(loadedUrl);
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
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
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

        // 常驻 ⟳ 重载按钮(黑屏/白屏时点一下即恢复)
        reloadBtn = new Button(this);
        reloadBtn.setText("⟳");
        reloadBtn.setTextSize(18);
        reloadBtn.setTextColor(Color.WHITE);
        reloadBtn.setBackgroundColor(0xCC1d4ed8);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                (int) (56 * getResources().getDisplayMetrics().density),
                (int) (56 * getResources().getDisplayMetrics().density),
                Gravity.BOTTOM | Gravity.END);
        lp.rightMargin = (int) (16 * getResources().getDisplayMetrics().density);
        lp.bottomMargin = (int) (90 * getResources().getDisplayMetrics().density);
        root.addView(reloadBtn, lp);
        reloadBtn.setOnClickListener(v -> {
            if (loadedUrl.isEmpty()) promptServer(false);
            else { Toast.makeText(this, "重新加载…", Toast.LENGTH_SHORT).show(); web.reload(); }
        });
    }

    private String base() { return sp.getString("server", ""); }

    private void load(String url) {
        if (!url.startsWith("http")) url = "http://" + url;
        if (!url.matches(".*:[0-9]+$")) url += ":8900";
        sp.edit().putString("server", url).apply();
        loadedUrl = url;
        setTitle("MeshChat · " + url);
        web.loadUrl(url);
    }

    @Override protected void onResume() {
        super.onResume();
        web.onResume();
        // 从后台回来若是空白/黑屏(渲染被回收), 自动重载
        if (!loadedUrl.isEmpty() && (web.getUrl() == null || web.getUrl().equals("about:blank"))) {
            web.loadUrl(loadedUrl);
        }
    }

    @Override protected void onPause() {
        web.onPause();
        super.onPause();
    }

    private void promptServer(boolean first) {
        AlertDialog.Builder ab = new AlertDialog.Builder(this);
        ab.setTitle(first ? "连接到 MeshChat 设备" : "切换服务器");
        ab.setMessage("输入对方设备的 MeshChat 地址(Tailscale IP,端口默认 8900)");
        final EditText et = new EditText(this);
        et.setHint("100.x.y.z 或 http://100.x.y.z:8900");
        et.setText(sp.getString("last_input", ""));
        ab.setView(et);
        ab.setPositiveButton("连接", (d, w) -> {
            sp.edit().putString("last_input", et.getText().toString()).apply();
            load(et.getText().toString().trim());
        });
        ab.setNegativeButton("退出", (d, w) -> finish());
        ab.setCancelable(false);
        ab.show();
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
