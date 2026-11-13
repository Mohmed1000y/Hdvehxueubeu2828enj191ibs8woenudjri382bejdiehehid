package com.aligh.browser;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.webkit.*;
import android.widget.*;
import android.text.TextUtils;
import java.util.*;

public class MainActivity extends Activity {

    LinearLayout root, toolbar, webArea;
    EditText address;
    ArrayList<WebView> tabs = new ArrayList<>();
    int current = 0;
    ArrayList<String> history = new ArrayList<>();
    ArrayList<String> bookmarks = new ArrayList<>();
    android.content.SharedPreferences prefs;

    int dark = Color.rgb(18, 25, 34);
    int green = Color.rgb(0, 199, 165);

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(dark);
        getWindow().setNavigationBarColor(dark);
        prefs = getSharedPreferences("browser", MODE_PRIVATE);
        loadSaved();
        buildUI();
        newTab("https://www.google.com");
    }

    void loadSaved() {
        Set<String> h = prefs.getStringSet("history", new HashSet<>());
        Set<String> m = prefs.getStringSet("bookmarks", new HashSet<>());
        history.addAll(h);
        bookmarks.addAll(m);
    }

    void saveLists() {
        prefs.edit()
            .putStringSet("history", new HashSet<>(history))
            .putStringSet("bookmarks", new HashSet<>(bookmarks))
            .apply();
    }

    TextView button(String title, Runnable action) {
        TextView b = new TextView(this);
        b.setText(title);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setGravity(Gravity.CENTER);
        b.setPadding(12, 12, 12, 12);
        b.setBackgroundColor(dark);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    void buildUI() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.VERTICAL);
        toolbar.setBackgroundColor(dark);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(7, 7, 7, 5);

        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextSize(14);
        address.setHint("ابحث أو اكتب عنوان الموقع");
        address.setHintTextColor(Color.GRAY);
        address.setTextColor(Color.WHITE);
        address.setPadding(12, 4, 12, 4);
        address.setBackgroundColor(Color.rgb(43, 53, 65));
        address.setImeOptions(EditorInfo.IME_ACTION_GO);

        row.addView(address, new LinearLayout.LayoutParams(0, 48, 1));
        row.addView(button("اذهب", () -> openAddress()), new LinearLayout.LayoutParams(-2, 48));
        toolbar.addView(row);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        LinearLayout actions = new LinearLayout(this);
        actions.addView(button("رجوع", () -> {
            if (tabs.size() > 0 && tabs.get(current).canGoBack())
                tabs.get(current).goBack();
        }));
        actions.addView(button("تقدم", () -> {
            if (tabs.size() > 0 && tabs.get(current).canGoForward())
                tabs.get(current).goForward();
        }));
        actions.addView(button("+ تبويب", () -> newTab("https://www.google.com")));
        actions.addView(button("التبويبات", () -> showTabs()));
        actions.addView(button("حفظ", () -> saveBookmark()));
        actions.addView(button("المحفوظات", () -> showBookmarks()));
        actions.addView(button("السجل", () -> showHistory()));
        scroll.addView(actions);
        toolbar.addView(scroll);

        root.addView(toolbar);

        webArea = new LinearLayout(this);
        webArea.setOrientation(LinearLayout.VERTICAL);
        root.addView(webArea, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);

        address.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_GO ||
                (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                openAddress();
                return true;
            }
            return false;
        });
    }

    void newTab(String url) {
        WebView web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        if (Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                if (tabs.get(current) == view) address.setText(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (tabs.get(current) == view) address.setText(url);
                if (url != null && !url.isEmpty() && !url.equals("about:blank")) {
                    if (history.isEmpty() || !history.get(history.size() - 1).equals(url)) {
                        history.add(url);
                        if (history.size() > 300) history.remove(0);
                        saveLists();
                    }
                }
            }
        });

        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, length) -> {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
                request.setTitle(filename);
                request.setDescription("Ali_gh Browser");
                request.setDestinationInExternalPublicDir(
                    android.os.Environment.DIRECTORY_DOWNLOADS, filename);
                DownloadManager manager =
                    (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                manager.enqueue(request);
                Toast.makeText(this, "بدأ التحميل: " + filename, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "تعذر بدء التحميل", Toast.LENGTH_SHORT).show();
            }
        });

        tabs.add(web);
        current = tabs.size() - 1;
        showCurrent();
        web.loadUrl(url);
    }

    void showCurrent() {
        webArea.removeAllViews();
        if (tabs.isEmpty()) return;
        WebView web = tabs.get(current);
        if (web.getParent() != null) ((android.view.ViewGroup) web.getParent()).removeView(web);
        webArea.addView(web, new LinearLayout.LayoutParams(-1, -1));
        address.setText(web.getUrl() == null ? "" : web.getUrl());
    }

    void openAddress() {
        String value = address.getText().toString().trim();
        if (value.isEmpty()) return;

        String url;
        if (value.matches("(?i)^https?://.*")) {
            url = value;
        } else if (value.contains(".") && !value.contains(" ")) {
            url = "https://" + value;
        } else {
            url = "https://www.google.com/search?q=" + Uri.encode(value);
        }

        ((android.view.inputmethod.InputMethodManager)
            getSystemService(INPUT_METHOD_SERVICE))
            .hideSoftInputFromWindow(address.getWindowToken(), 0);

        tabs.get(current).loadUrl(url);
    }

    void showTabs() {
        if (tabs.isEmpty()) return;
        String[] names = new String[tabs.size()];
        for (int i = 0; i < tabs.size(); i++) {
            String u = tabs.get(i).getUrl();
            names[i] = (i + 1) + " - " + (u == null ? "تبويب جديد" : u);
        }
        new AlertDialog.Builder(this)
            .setTitle("التبويبات المفتوحة")
            .setItems(names, (d, which) -> {
                current = which;
                showCurrent();
            })
            .setPositiveButton("تبويب جديد", (d, w) ->
                newTab("https://www.google.com"))
            .setNegativeButton("إغلاق الحالي", (d, w) -> closeCurrent())
            .show();
    }

    void closeCurrent() {
        if (tabs.isEmpty()) return;
        WebView web = tabs.remove(current);
        web.stopLoading();
        web.destroy();
        if (tabs.isEmpty()) {
            newTab("https://www.google.com");
        } else {
            current = Math.min(current, tabs.size() - 1);
            showCurrent();
        }
    }

    void saveBookmark() {
        String url = tabs.get(current).getUrl();
        if (url == null || url.equals("about:blank")) return;
        if (!bookmarks.contains(url)) bookmarks.add(url);
        saveLists();
        Toast.makeText(this, "تم حفظ الموقع", Toast.LENGTH_SHORT).show();
    }

    void showList(String title, ArrayList<String> items) {
        if (items.isEmpty()) {
            Toast.makeText(this, "القائمة فارغة", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] values = items.toArray(new String[0]);
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(values, (d, which) -> tabs.get(current).loadUrl(values[which]))
            .setNegativeButton("إغلاق", null)
            .show();
    }

    void showBookmarks() { showList("المواقع المحفوظة", bookmarks); }
    void showHistory() { showList("سجل التصفح", history); }

    @Override
    public void onBackPressed() {
        if (!tabs.isEmpty() && tabs.get(current).canGoBack()) {
            tabs.get(current).goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        for (WebView web : tabs) web.destroy();
        super.onDestroy();
    }
}
