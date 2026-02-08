package com.webview.myapplication;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends Activity {
    private WebView mWebView;
    private EditText urlInput;
    private Button refreshButton;
    private Button goButton;
    private NetworkCallback networkCallback;
    
    // שנה את הכתובת הזו למה שאתה רוצה (למשל יוטיוב)
    private final String myUrl = "https://www.youtube.com"; 

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // חיבור לאלמנטים
        mWebView = findViewById(R.id.activity_main_webview);
        urlInput = findViewById(R.id.url_input);
        refreshButton = findViewById(R.id.refresh_button);
        goButton = findViewById(R.id.go_button);

        WebSettings webSettings = mWebView.getSettings();

        // --- התחלת השינויים החשובים ---
        
        // 1. הפעלת JavaScript
        webSettings.setJavaScriptEnabled(true);
        
        // 2. הפעלת זיכרון מקומי (חובה להתחברות לחשבונות!)
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);

        // 3. שינוי הזהות לדפדפן כרום רגיל (מונע חסימה של גוגל)
        String newUA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36";
        webSettings.setUserAgentString(newUA);
        
        // --- סוף השינויים החשובים ---

        mWebView.setWebViewClient(new HelloWebViewClient());
        
        // עדכון שורת הכתובת כשעמוד חדש נטען
        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (newProgress == 100) {
                    urlInput.setText(view.getUrl());
                }
            }
        });

        // כפתור רענון
        refreshButton.setOnClickListener(v -> mWebView.reload());

        // כפתור "סע"
        goButton.setOnClickListener(v -> loadUrl());

        // לחיצה על Enter בשורת הכתובת
        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || 
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                loadUrl();
                return true;
            }
            return false;
        });

        // מנהל ההורדות
        mWebView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimetype);
                String cookies = CookieManager.getInstance().getCookie(url);
                request.addRequestHeader("cookie", cookies);
                request.addRequestHeader("User-Agent", userAgent);
                request.setDescription("Downloading file...");
                request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype));
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimetype));
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(request);
                Toast.makeText(getApplicationContext(), "Downloading File", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), "Download Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        // טעינה ראשונית
        if (isNetworkAvailable()) {
            mWebView.loadUrl(myUrl);
            urlInput.setText(myUrl);
        } else {
            mWebView.loadUrl("file:///android_asset/offline.html");
        }

        // ניהול רשת
        networkCallback = new NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                runOnUiThread(() -> {
                    if (mWebView.getUrl() != null && mWebView.getUrl().startsWith("file:///android_asset")) {
                        mWebView.loadUrl(myUrl);
                        urlInput.setText(myUrl);
                    }
                });
            }
            @Override
            public void onLost(Network network) {
                runOnUiThread(() -> {
                    mWebView.loadUrl("file:///android_asset/offline.html");
                });
            }
        };
        
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        }
    }

    // פונקציה לטעינת URL מהשורת כתובת
    private void loadUrl() {
        String url = urlInput.getText().toString().trim();
        
        if (url.isEmpty()) {
            Toast.makeText(this, "הזן כתובת", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // אם לא התחיל ב-http, נוסיף אוטומטית
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            // אם נראה כמו כתובת אתר, נוסיף https
            if (url.contains(".") && !url.contains(" ")) {
                url = "https://" + url;
            } else {
                // אחרת, חיפוש בגוגל
                url = "https://www.google.com/search?q=" + Uri.encode(url);
            }
        }
        
        mWebView.loadUrl(url);
        urlInput.setText(url);
        
        // הסתרת המקלדת
        urlInput.clearFocus();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return false;
        Network nw = connectivityManager.getActiveNetwork();
        if (nw == null) return false;
        NetworkCapabilities actNw = connectivityManager.getNetworkCapabilities(nw);
        return actNw != null && (actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) || actNw.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
    }

    private class HelloWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return false;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            // עדכון שורת הכתובת כשהעמוד נטען
            urlInput.setText(url);
        }
    }

    @Override
    public void onBackPressed() {
        if (mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (networkCallback != null) {
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager != null) {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            }
        }
    }
}