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
import android.view.View;
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
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

public class MainActivity extends Activity {
    private WebView mWebView;
    private EditText urlInput;
    private Button refreshButton;
    private Button goButton;
    private Button backButton;
    private Button forwardButton;
    private ProgressBar progressBar;
    private NetworkCallback networkCallback;

    // מסך מלא לוידאו
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private FrameLayout fullscreenContainer;
    
    private final String myUrl = "https://www.google.com"; 

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
        backButton = findViewById(R.id.back_button);
        forwardButton = findViewById(R.id.forward_button);
        progressBar = findViewById(R.id.progress_bar);
        fullscreenContainer = findViewById(R.id.fullscreen_container);

        WebSettings webSettings = mWebView.getSettings();

        // הגדרות בסיסיות
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        
        String newUA = "Mozilla/5.0 (Linux; Android 14; SM-S926B Build/UP1A.231005.007; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/144.0.7559.109 Mobile Safari/537.36";
        webSettings.setUserAgentString(newUA);

        // הגדרות מדיה משופרות
        webSettings.setMediaPlaybackRequiresUserGesture(false); // וידאו אוטומטי
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        
        // Zoom
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        mWebView.setWebViewClient(new HelloWebViewClient());
        
        // WebChromeClient משודרג - תמיכה במסך מלא
        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                progressBar.setProgress(newProgress);
                
                // הצג/הסתר את פס הטעינה
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                } else {
                    progressBar.setVisibility(View.GONE);
                    urlInput.setText(view.getUrl());
                }
                
                // עדכון כפתורי ניווט
                updateNavigationButtons();
            }

            // תמיכה במסך מלא לוידאו
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }

                customView = view;
                customViewCallback = callback;
                
                fullscreenContainer.addView(customView);
                fullscreenContainer.setVisibility(View.VISIBLE);
                mWebView.setVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) {
                    return;
                }

                customView.setVisibility(View.GONE);
                fullscreenContainer.removeView(customView);
                fullscreenContainer.setVisibility(View.GONE);
                mWebView.setVisibility(View.VISIBLE);
                
                customView = null;
                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                }
            }
        });

        // כפתור רענון
        refreshButton.setOnClickListener(v -> mWebView.reload());

        // כפתור סע
        goButton.setOnClickListener(v -> loadUrl());

        // כפתור אחורה
        backButton.setOnClickListener(v -> {
            if (mWebView.canGoBack()) {
                mWebView.goBack();
            }
        });

        // כפתור קדימה
        forwardButton.setOnClickListener(v -> {
            if (mWebView.canGoForward()) {
                mWebView.goForward();
            }
        });

        // Enter בשורת הכתובת
        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || 
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                loadUrl();
                return true;
            }
            return false;
        });

        // מנהל הורדות
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
        
        // עדכון ראשוני של כפתורי ניווט
        updateNavigationButtons();
    }

    // עדכון מצב כפתורי ניווט
    private void updateNavigationButtons() {
        backButton.setEnabled(mWebView.canGoBack());
        forwardButton.setEnabled(mWebView.canGoForward());
        
        // שינוי צבע לכפתורים לא פעילים (אופציונלי)
        backButton.setAlpha(mWebView.canGoBack() ? 1.0f : 0.5f);
        forwardButton.setAlpha(mWebView.canGoForward() ? 1.0f : 0.5f);
    }

    private void loadUrl() {
        String url = urlInput.getText().toString().trim();

        if (url.isEmpty()) {
            Toast.makeText(this, "הזן כתובת", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            if (url.contains(".") && !url.contains(" ")) {
                url = "https://" + url;
            } else {
                url = "https://www.google.com/search?q=" + Uri.encode(url);
            }
        }

        mWebView.loadUrl(url);
        urlInput.setText(url);
        urlInput.clearFocus();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return false;
        Network nw = connectivityManager.getActiveNetwork();
        if (nw == null) return false;
        NetworkCapabilities actNw = connectivityManager.getNetworkCapabilities(nw);
        return actNw != null && (actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || 
                                 actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || 
                                 actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) || 
                                 actNw.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
    }

    private class HelloWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return false;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            urlInput.setText(url);
            updateNavigationButtons();
        }
    }

    @Override
    public void onBackPressed() {
        // אם יש מסך מלא - צא ממנו
        if (customView != null) {
            mWebView.getWebChromeClient().onHideCustomView();
            return;
        }
        
        // אחרת - ניווט אחורה רגיל
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