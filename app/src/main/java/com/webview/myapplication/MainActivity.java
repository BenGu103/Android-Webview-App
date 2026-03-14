package com.webview.myapplication;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static class BrowserTab {
        public WebView webView;
        public String title = "New Tab";
        public String url = "";
        public boolean isDesktopMode = false;
    }

    private FrameLayout webViewContainer;
    private SwipeRefreshLayout swipeRefreshLayout;
    private EditText urlInput;
    private View backButton, forwardButton, homeButton, goButton, tabsButtonContainer, newTabButton;
    private TextView tabsCountText;
    private ProgressBar progressBar;
    private FrameLayout fullscreenContainer;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private NetworkCallback networkCallback;
    private final String homeUrl = "https://www.google.com";
    
    // Desktop Use Agent
    private final String DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private final String MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; SM-S926B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    // Basic Ad Blocker List
    private static final String[] AD_HOSTS = {
        "googleads", "doubleclick.net", "adservice.google.com", 
        "googleadservices.com", "googlesyndication.com", "adsystem.com",
        "adnexus", "adnxs", "smartadserver.com", "criteo", "amazon-adsystem",
        "taboola", "outbrain", "mgid", "zergnet", "/ads/", "advert"
    };

    private List<BrowserTab> tabsList = new ArrayList<>();
    private int currentTabIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        webViewContainer = findViewById(R.id.webview_container);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        urlInput = findViewById(R.id.url_input);
        goButton = findViewById(R.id.go_button);
        progressBar = findViewById(R.id.progress_bar);
        fullscreenContainer = findViewById(R.id.fullscreen_container);

        backButton = findViewById(R.id.back_button);
        forwardButton = findViewById(R.id.forward_button);
        homeButton = findViewById(R.id.home_button);
        tabsButtonContainer = findViewById(R.id.tabs_button_container);
        tabsCountText = findViewById(R.id.tabs_count_text);
        newTabButton = findViewById(R.id.new_tab_button);

        // Pull to refresh
        swipeRefreshLayout.setOnRefreshListener(() -> {
            BrowserTab currentTab = getCurrentTab();
            if (currentTab != null && currentTab.webView != null) {
                currentTab.webView.reload();
            } else {
                swipeRefreshLayout.setRefreshing(false);
            }
        });

        // Navigation Actions
        goButton.setOnClickListener(v -> loadUrlFromInput());
        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || 
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                loadUrlFromInput();
                return true;
            }
            return false;
        });

        backButton.setOnClickListener(v -> {
            BrowserTab tab = getCurrentTab();
            if (tab != null && tab.webView.canGoBack()) tab.webView.goBack();
        });

        forwardButton.setOnClickListener(v -> {
            BrowserTab tab = getCurrentTab();
            if (tab != null && tab.webView.canGoForward()) tab.webView.goForward();
        });

        homeButton.setOnClickListener(v -> {
            BrowserTab tab = getCurrentTab();
            if (tab != null) tab.webView.loadUrl(homeUrl);
        });

        newTabButton.setOnClickListener(v -> createNewTab(homeUrl));

        tabsButtonContainer.setOnClickListener(v -> showTabsBottomSheet());

        // Setup Network Monitor
        setupNetworkMonitor();

        // Create initial tab
        createNewTab(homeUrl);
    }

    private void loadUrlFromInput() {
        String url = urlInput.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(this, "Enter URL", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            if (url.contains(".") && !url.contains(" ")) {
                url = "https://" + url;
            } else {
                url = "https://www.google.com/search?q=" + Uri.encode(url);
            }
        }
        
        BrowserTab currentTab = getCurrentTab();
        if (currentTab != null) {
            currentTab.webView.loadUrl(url);
            urlInput.clearFocus();
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void createNewTab(String urlToLoad) {
        BrowserTab tab = new BrowserTab();
        tab.webView = new WebView(this);
        tab.webView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        
        setupWebViewSettings(tab.webView);
        
        tab.webView.setWebViewClient(new BrowserWebViewClient(tab));
        tab.webView.setWebChromeClient(new BrowserWebChromeClient(tab));
        tab.webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> 
            handleDownload(url, userAgent, contentDisposition, mimetype)
        );

        tabsList.add(tab);
        switchToTab(tabsList.size() - 1);
        
        if (!isNetworkAvailable()) {
            tab.webView.loadUrl("file:///android_asset/offline.html");
        } else {
            tab.webView.loadUrl(urlToLoad);
        }
        
        updateTabsUI();
    }

    private void switchToTab(int index) {
        if (index < 0 || index >= tabsList.size()) return;
        
        currentTabIndex = index;
        BrowserTab activeTab = tabsList.get(index);
        
        webViewContainer.removeAllViews();
        webViewContainer.addView(activeTab.webView);
        
        urlInput.setText(activeTab.url);
        updateNavigationButtons();
    }

    private void closeTab(int index) {
        if (index < 0 || index >= tabsList.size()) return;
        
        BrowserTab tab = tabsList.get(index);
        webViewContainer.removeView(tab.webView);
        tab.webView.destroy();
        tabsList.remove(index);
        
        if (tabsList.isEmpty()) {
            createNewTab(homeUrl);
        } else {
            if (currentTabIndex >= tabsList.size()) {
                currentTabIndex = tabsList.size() - 1;
            } else if (currentTabIndex > index) {
                currentTabIndex--;
            }
            switchToTab(currentTabIndex);
        }
        updateTabsUI();
    }

    private BrowserTab getCurrentTab() {
        if (currentTabIndex >= 0 && currentTabIndex < tabsList.size()) {
            return tabsList.get(currentTabIndex);
        }
        return null;
    }

    private void updateTabsUI() {
        tabsCountText.setText(String.valueOf(tabsList.size()));
    }

    private void updateNavigationButtons() {
        BrowserTab tab = getCurrentTab();
        if (tab == null) return;
        backButton.setEnabled(tab.webView.canGoBack());
        forwardButton.setEnabled(tab.webView.canGoForward());
        backButton.setAlpha(tab.webView.canGoBack() ? 1.0f : 0.4f);
        forwardButton.setAlpha(tab.webView.canGoForward() ? 1.0f : 0.4f);
    }

    private void setupWebViewSettings(WebView webView) {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setUserAgentString(MOBILE_USER_AGENT);

        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
    }

    private void showTabsBottomSheet() {
        View view = getLayoutInflater().inflate(R.layout.layout_tabs_bottom_sheet, null);
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(view);
        
        RecyclerView recyclerView = view.findViewById(R.id.tabs_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        TabsAdapter adapter = new TabsAdapter(tabsList, new TabsAdapter.OnTabClickListener() {
            @Override
            public void onTabSelected(int position) {
                switchToTab(position);
                dialog.dismiss();
            }

            @Override
            public void onTabClosed(int position) {
                closeTab(position);
                if (recyclerView.getAdapter() != null) {
                    recyclerView.getAdapter().notifyDataSetChanged();
                }
                if (tabsList.isEmpty()) {
                    dialog.dismiss();
                }
            }
        });
        recyclerView.setAdapter(adapter);
        
        view.findViewById(R.id.btn_close_all_tabs).setOnClickListener(v -> {
            for (BrowserTab tab : new ArrayList<>(tabsList)) {
                tab.webView.destroy();
            }
            tabsList.clear();
            createNewTab(homeUrl);
            dialog.dismiss();
        });
        
        dialog.show();
    }

    private class BrowserWebViewClient extends WebViewClient {
        private final BrowserTab tab;
        
        BrowserWebViewClient(BrowserTab tab) {
            this.tab = tab;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString().toLowerCase();
            for (String adHost : AD_HOSTS) {
                if (url.contains(adHost)) {
                    // Return empty response for ad
                    return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes()));
                }
            }
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return false;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            tab.url = url;
            if (currentTabIndex >= 0 && tabsList.get(currentTabIndex) == tab) {
                urlInput.setText(url);
                updateNavigationButtons();
                swipeRefreshLayout.setRefreshing(false);
            }
        }
    }

    private class BrowserWebChromeClient extends WebChromeClient {
        private final BrowserTab tab;

        BrowserWebChromeClient(BrowserTab tab) {
            this.tab = tab;
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            super.onReceivedTitle(view, title);
            tab.title = title;
        }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            if (currentTabIndex >= 0 && tabsList.get(currentTabIndex) == tab) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
                updateNavigationButtons();
            }
        }

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
            swipeRefreshLayout.setVisibility(View.GONE);
        }

        @Override
        public void onHideCustomView() {
            if (customView == null) return;

            customView.setVisibility(View.GONE);
            fullscreenContainer.removeView(customView);
            fullscreenContainer.setVisibility(View.GONE);
            swipeRefreshLayout.setVisibility(View.VISIBLE);
            
            customView = null;
            if (customViewCallback != null) {
                customViewCallback.onCustomViewHidden();
            }
        }
    }

    private void handleDownload(String url, String userAgent, String contentDisposition, String mimetype) {
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
            if (dm != null) dm.enqueue(request);
            Toast.makeText(this, "Downloading File", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Download Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupNetworkMonitor() {
        networkCallback = new NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                runOnUiThread(() -> {
                    for (BrowserTab tab : tabsList) {
                        if (tab.webView.getUrl() != null && tab.webView.getUrl().startsWith("file:///android_asset")) {
                            if (!tab.url.isEmpty() && !tab.url.startsWith("file:///")) {
                                tab.webView.loadUrl(tab.url);
                            } else {
                                tab.webView.loadUrl(homeUrl);
                            }
                        }
                    }
                });
            }
            @Override
            public void onLost(Network network) {
                runOnUiThread(() -> {
                    BrowserTab cur = getCurrentTab();
                    if (cur != null) cur.webView.loadUrl("file:///android_asset/offline.html");
                });
            }
        };
        
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network nw = cm.getActiveNetwork();
        if (nw == null) return false;
        NetworkCapabilities actNw = cm.getNetworkCapabilities(nw);
        return actNw != null && (actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || 
                                 actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || 
                                 actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) || 
                                 actNw.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.browser_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        BrowserTab cur = getCurrentTab();
        if (cur != null) {
            MenuItem desktopItem = menu.findItem(R.id.action_desktop_mode);
            if (desktopItem != null) {
                desktopItem.setChecked(cur.isDesktopMode);
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        BrowserTab cur = getCurrentTab();
        
        if (id == R.id.action_share) {
            if (cur != null && cur.url != null && !cur.url.isEmpty() && !cur.url.startsWith("file:///")) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, cur.title);
                shareIntent.putExtra(Intent.EXTRA_TEXT, cur.url);
                startActivity(Intent.createChooser(shareIntent, "Share Link"));
            } else {
                Toast.makeText(this, "Nothing to share", Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (id == R.id.action_desktop_mode) {
            if (cur != null) {
                cur.isDesktopMode = !cur.isDesktopMode;
                item.setChecked(cur.isDesktopMode);
                cur.webView.getSettings().setUserAgentString(cur.isDesktopMode ? DESKTOP_USER_AGENT : MOBILE_USER_AGENT);
                cur.webView.reload();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            BrowserTab cur = getCurrentTab();
            if (cur != null && cur.webView.getWebChromeClient() != null) {
                cur.webView.getWebChromeClient().onHideCustomView();
            }
            return;
        }
        
        BrowserTab cur = getCurrentTab();
        if (cur != null && cur.webView.canGoBack()) {
            cur.webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkCallback != null) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) cm.unregisterNetworkCallback(networkCallback);
        }
    }
}