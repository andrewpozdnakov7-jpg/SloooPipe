// SPDX-License-Identifier: GPL-3.0-or-later
// Adapted from PipePipe YouTubeLoginWebViewActivity / BaseLoginWebViewActivity.
package net.slooopipe.auth.pipepipe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import net.slooopipe.auth.api.AuthDiagnostics;
import net.slooopipe.auth.api.CredentialPolicy;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.net.URI;

/** Internal activity in its own process, with its own WebView cookie database. */
public final class PipePipeLoginActivity extends Activity {
    public static final String CLEAR = "net.slooopipe.auth.CLEAR";
    private WebView webView;
    private TextView status;
    private String pot;
    private boolean returning;
    private final long screenOp = AuthDiagnostics.nextOperation();
    private long navigationStarted;
    private int lastLeft = -1;
    private int lastTop = -1;
    private int lastRight = -1;
    private int lastBottom = -1;
    private String lastSnapshot;
    private boolean snapshotsActive;
    private final Runnable snapshotPoll =
            new Runnable() {
                @Override
                public void run() {
                    if (snapshotsActive && webView != null && !isFinishing()) {
                        recordPage(webView);
                        webView.postDelayed(this, 2000);
                    }
                }
            };

    @Override
    public void onCreate(final Bundle state) {
        super.onCreate(state);
        final boolean browserAvailable =
                Build.VERSION.SDK_INT >= 28 && !getIntent().getBooleanExtra("importOnly", false);
        AuthDiagnostics.event(
                "login.create",
                "screen",
                screenOp,
                "sdk",
                Build.VERSION.SDK_INT,
                "webview_enabled",
                browserAvailable,
                "clear",
                CLEAR.equals(getIntent().getAction()),
                "restored",
                state != null,
                "orientation",
                getResources().getConfiguration().orientation,
                "density_dpi",
                getResources().getDisplayMetrics().densityDpi,
                "font_pct",
                Math.round(getResources().getConfiguration().fontScale * 100));
        if (CLEAR.equals(getIntent().getAction())) {
            if (Build.VERSION.SDK_INT >= 28) {
                clearWebSession(this::finish);
            } else {
                finish();
            }
            return;
        }
        setResult(RESULT_CANCELED);
        setTitle(R.string.youtube_auth_login);
        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        applyInsets(root);
        final ScrollView controls = new ControlsScrollView(this);
        final LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        final int padding = Math.round(12 * getResources().getDisplayMetrics().density);
        actions.setPadding(padding, padding, padding, padding);
        status = new TextView(this);
        status.setText(R.string.youtube_auth_browser_help);
        actions.addView(status);
        if (browserAvailable) {
            button(actions, R.string.youtube_auth_capture, this::capture);
        }
        button(actions, R.string.youtube_auth_import, this::importCookies);
        button(actions, R.string.youtube_auth_external_browser, this::openBrowser);
        controls.addView(actions, new ScrollView.LayoutParams(-1, -2));
        controls.addOnLayoutChangeListener(
                (view, left, top, right, bottom, ol, ot, or, ob) ->
                        AuthDiagnostics.event(
                                "login.controls_geometry",
                                "screen",
                                screenOp,
                                "width",
                                right - left,
                                "viewport_height",
                                bottom - top,
                                "content_height",
                                actions.getHeight(),
                                "scrollable",
                                actions.getHeight() > bottom - top));
        root.addView(controls, new LinearLayout.LayoutParams(-1, -2));
        if (browserAvailable) {
            webView = new WebView(this);
            root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        setContentView(root);
        root.post(root::requestApplyInsets);
        if (webView != null) {
            configureWebView();
            clearWebSession(
                    () -> {
                        if (webView != null) {
                            webView.loadUrl("https://m.youtube.com/signin");
                        }
                    });
        }
    }

    private void button(final LinearLayout layout, final int label, final Runnable action) {
        final Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setSingleLine(false);
        button.setOnClickListener(
                view -> {
                    AuthDiagnostics.event("login.button", "screen", screenOp, "control", label);
                    action.run();
                });
        layout.addView(button, new LinearLayout.LayoutParams(-1, -2));
    }

    private void applyInsets(final View root) {
        root.setOnApplyWindowInsetsListener(
                (view, insets) -> {
                    if (Build.VERSION.SDK_INT >= 30) {
                        final Insets bars =
                                insets.getInsets(
                                        WindowInsets.Type.systemBars()
                                                | WindowInsets.Type.displayCutout()
                                                | WindowInsets.Type.ime());
                        view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    } else {
                        view.setPadding(
                                insets.getSystemWindowInsetLeft(),
                                insets.getSystemWindowInsetTop(),
                                insets.getSystemWindowInsetRight(),
                                insets.getSystemWindowInsetBottom());
                    }
                    if (view.getPaddingLeft() != lastLeft
                            || view.getPaddingTop() != lastTop
                            || view.getPaddingRight() != lastRight
                            || view.getPaddingBottom() != lastBottom) {
                        lastLeft = view.getPaddingLeft();
                        lastTop = view.getPaddingTop();
                        lastRight = view.getPaddingRight();
                        lastBottom = view.getPaddingBottom();
                        AuthDiagnostics.event(
                                "login.insets",
                                "screen",
                                screenOp,
                                "left",
                                lastLeft,
                                "top",
                                lastTop,
                                "right",
                                lastRight,
                                "bottom",
                                lastBottom,
                                "width",
                                view.getWidth(),
                                "height",
                                view.getHeight());
                    }
                    return insets;
                });
    }

    private void configureWebView() {
        if (Build.VERSION.SDK_INT >= 28) {
            final android.content.pm.PackageInfo engine = WebView.getCurrentWebViewPackage();
            AuthDiagnostics.event(
                    "webview.engine",
                    "screen",
                    screenOp,
                    "present",
                    engine != null,
                    "version",
                    engine == null ? 0L : engine.getLongVersionCode());
        }
        WebView.setWebContentsDebuggingEnabled(false);
        final WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
        settings.setUserAgentString(
                "Mozilla/5.0 (iPad; CPU OS 16_7_10 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like"
                        + " Gecko) Version/16.6 Mobile/15E148 Safari/604.1");
        AuthDiagnostics.event(
                "webview.settings",
                "screen",
                screenOp,
                "javascript",
                true,
                "dom_storage",
                true,
                "third_party_cookies",
                false,
                "file_access",
                false);
        AuthDiagnostics.event(
                "webview.identity",
                "screen",
                screenOp,
                "user_agent",
                AuthDiagnostics.text(settings.getUserAgentString()),
                "wide_viewport",
                settings.getUseWideViewPort(),
                "overview",
                settings.getLoadWithOverviewMode());
        webView.setWebChromeClient(
                new WebChromeClient() {
                    @Override
                    public boolean onConsoleMessage(final ConsoleMessage message) {
                        AuthDiagnostics.event(
                                "webview.console",
                                "screen",
                                screenOp,
                                "level",
                                message.messageLevel(),
                                "line",
                                message.lineNumber(),
                                "message",
                                AuthDiagnostics.text(message.message()),
                                "source",
                                AuthDiagnostics.text(message.sourceId()));
                        return true; // Do not forward page messages, which may contain credentials.
                    }
                });
        webView.setWebViewClient(
                new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(
                            final WebView view, final WebResourceRequest request) {
                        final boolean permitted = permittedNavigation(request.getUrl());
                        AuthDiagnostics.event(
                                "webview.navigation",
                                "screen",
                                screenOp,
                                "destination",
                                AuthDiagnostics.destination(request.getUrl().toString()),
                                "url",
                                AuthDiagnostics.url(request.getUrl().toString()),
                                "allowed",
                                permitted,
                                "main_frame",
                                request.isForMainFrame());
                        if (!permitted && request.isForMainFrame()) {
                            status.setText(
                                    getString(
                                            R.string.youtube_auth_navigation_blocked,
                                            request.getUrl().getHost() == null
                                                    ? "[NON_WEB_LINK]"
                                                    : request.getUrl().getHost()));
                        }
                        return !permitted;
                    }

                    @Override
                    public void onPageStarted(
                            final WebView view, final String url, final Bitmap icon) {
                        navigationStarted = AuthDiagnostics.now();
                        AuthDiagnostics.event(
                                "webview.page_started",
                                "screen",
                                screenOp,
                                "destination",
                                AuthDiagnostics.destination(url),
                                "url",
                                AuthDiagnostics.url(url));
                    }

                    @Override
                    public void onPageFinished(final WebView view, final String url) {
                        final String path = url == null ? null : Uri.parse(url).getPath();
                        final String cookies =
                                CookieManager.getInstance().getCookie("https://www.youtube.com");
                        AuthDiagnostics.event(
                                "webview.page_finished",
                                "screen",
                                screenOp,
                                "destination",
                                AuthDiagnostics.destination(url),
                                "url",
                                AuthDiagnostics.url(url),
                                "elapsed_ms",
                                AuthDiagnostics.now() - navigationStarted,
                                "rejected_route",
                                path != null && path.contains("rejected"),
                                "cookies_present",
                                cookies != null && !cookies.isEmpty(),
                                "session_cookie_present",
                                cookies != null
                                        && (cookies.contains("SAPISID=")
                                                || cookies.contains("__Secure-3PAPISID=")));
                        final AuthDiagnostics.Destination destination =
                                AuthDiagnostics.destination(url);
                        if ((destination == AuthDiagnostics.Destination.GOOGLE_ACCOUNT
                                        || destination
                                                == AuthDiagnostics.Destination.YOUTUBE_ACCOUNT)
                                && validCookies(cookies)) {
                            status.setText(R.string.youtube_auth_session_continue);
                        } else if (destination == AuthDiagnostics.Destination.GOOGLE_ACCOUNT) {
                            status.setText(R.string.youtube_auth_google_step);
                        } else if (destination == AuthDiagnostics.Destination.YOUTUBE_ACCOUNT) {
                            status.setText(R.string.youtube_auth_youtube_transfer);
                        } else if ((destination == AuthDiagnostics.Destination.YOUTUBE
                                        || destination
                                                == AuthDiagnostics.Destination.YOUTUBE_MOBILE)
                                && validCookies(cookies)) {
                            status.setText(R.string.youtube_auth_session_ready);
                        }
                        recordPage(view);
                    }

                    @Override
                    public void doUpdateVisitedHistory(
                            final WebView view, final String url, final boolean reload) {
                        AuthDiagnostics.event(
                                "webview.history",
                                "screen",
                                screenOp,
                                "url",
                                AuthDiagnostics.url(url),
                                "reload",
                                reload);
                        recordPage(view);
                    }

                    @Override
                    public void onReceivedError(
                            final WebView view,
                            final WebResourceRequest request,
                            final WebResourceError error) {
                        AuthDiagnostics.event(
                                "webview.load_error",
                                "screen",
                                screenOp,
                                "code",
                                error.getErrorCode(),
                                "main_frame",
                                request.isForMainFrame(),
                                "destination",
                                AuthDiagnostics.destination(request.getUrl().toString()),
                                "url",
                                AuthDiagnostics.url(request.getUrl().toString()),
                                "description",
                                AuthDiagnostics.text(error.getDescription().toString()));
                        super.onReceivedError(view, request, error);
                    }

                    @Override
                    public void onReceivedHttpError(
                            final WebView view,
                            final WebResourceRequest request,
                            final WebResourceResponse response) {
                        AuthDiagnostics.event(
                                "webview.http_error",
                                "screen",
                                screenOp,
                                "status",
                                response.getStatusCode(),
                                "main_frame",
                                request.isForMainFrame(),
                                "destination",
                                AuthDiagnostics.destination(request.getUrl().toString()),
                                "url",
                                AuthDiagnostics.url(request.getUrl().toString()),
                                "reason",
                                AuthDiagnostics.text(response.getReasonPhrase()),
                                "headers",
                                AuthDiagnostics.payload(response.getResponseHeaders()));
                        super.onReceivedHttpError(view, request, response);
                    }

                    @Override
                    public void onReceivedSslError(
                            final WebView view,
                            final SslErrorHandler handler,
                            final SslError error) {
                        AuthDiagnostics.event(
                                "webview.ssl_error",
                                "screen",
                                screenOp,
                                "code",
                                error.getPrimaryError(),
                                "destination",
                                AuthDiagnostics.destination(error.getUrl()),
                                "url",
                                AuthDiagnostics.url(error.getUrl()),
                                "description",
                                AuthDiagnostics.text(error.toString()));
                        super.onReceivedSslError(view, handler, error);
                    }

                    @Override
                    public boolean onRenderProcessGone(
                            final WebView view, final RenderProcessGoneDetail detail) {
                        AuthDiagnostics.event(
                                "webview.renderer_gone",
                                "screen",
                                screenOp,
                                "crashed",
                                detail.didCrash(),
                                "priority",
                                detail.rendererPriorityAtExit());
                        return false;
                    }

                    @Override
                    public WebResourceResponse shouldInterceptRequest(
                            final WebView view, final WebResourceRequest request) {
                        final Uri uri = request.getUrl();
                        AuthDiagnostics.event(
                                "webview.request",
                                "screen",
                                screenOp,
                                "url",
                                AuthDiagnostics.url(uri.toString()),
                                "method",
                                AuthDiagnostics.text(request.getMethod()),
                                "main_frame",
                                request.isForMainFrame(),
                                "headers",
                                AuthDiagnostics.payload(request.getRequestHeaders()));
                        final String host = uri.getHost();
                        if ("https".equals(uri.getScheme())
                                && host != null
                                && host.endsWith(".googlevideo.com")
                                && "/videoplayback".equals(uri.getPath())) {
                            final String token = uri.getQueryParameter("pot");
                            if (token != null) {
                                runOnUiThread(
                                        () -> {
                                            pot = token;
                                            AuthDiagnostics.event(
                                                    "webview.stream_token_seen",
                                                    "screen",
                                                    screenOp);
                                        });
                            }
                        }
                        return null;
                    }
                });
    }

    private static boolean permittedNavigation(final Uri uri) {
        try {
            return CredentialPolicy.permitsLoginNavigation(URI.create(uri.toString()));
        } catch (final RuntimeException ignored) {
            return false;
        }
    }

    private void recordPage(final WebView view) {
        if (!AuthDiagnostics.enabled() || isFinishing() || view != webView) {
            return;
        }
        final String page = view.getUrl();
        // Read visible text and form metadata, never input values or the page HTML.
        view.evaluateJavascript(
                "JSON.stringify({title:document.title,ready:document.readyState,"
                        + "visibleText:(document.body?.innerText||'').slice(0,12000),"
                        + "forms:Array.from(document.forms).slice(0,20)"
                        + ".map(f=>({action:f.action,method:f.method,"
                        + "fields:Array.from(f.elements).slice(0,80)"
                        + ".map(e=>({tag:e.tagName,type:e.type,"
                        + "name:e.name,required:e.required,disabled:e.disabled}))})),"
                        + "buttons:Array.from(document.querySelectorAll('button,[role=button]'))"
                        + ".slice(0,80).map(b=>({text:b.innerText,disabled:!!b.disabled}))})",
                value -> {
                    if (view != webView
                            || isFinishing()
                            || page == null
                            || !page.equals(view.getUrl())) {
                        AuthDiagnostics.event("webview.page_snapshot_stale", "screen", screenOp);
                        return;
                    }
                    try {
                        final Object decoded = new JSONTokener(value).nextValue();
                        final AuthDiagnostics.SafeText details =
                                AuthDiagnostics.text(
                                        decoded instanceof String
                                                ? (String) decoded
                                                : "[NO_PAGE_DATA]");
                        final String snapshot =
                                AuthDiagnostics.url(page).display() + details.display();
                        if (!snapshot.equals(lastSnapshot)) {
                            lastSnapshot = snapshot;
                            AuthDiagnostics.event(
                                    "webview.page_snapshot",
                                    "screen",
                                    screenOp,
                                    "url",
                                    AuthDiagnostics.url(page),
                                    "page",
                                    details);
                        }
                    } catch (final RuntimeException error) {
                        AuthDiagnostics.failure(
                                "webview.page_snapshot_failed", error, "screen", screenOp);
                    } catch (final org.json.JSONException error) {
                        AuthDiagnostics.failure(
                                "webview.page_snapshot_failed", error, "screen", screenOp);
                    }
                });
    }

    private void openBrowser() {
        AuthDiagnostics.event("login.external_browser_begin", "screen", screenOp);
        try {
            final Intent browser =
                    new Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com/signin"))
                            .addCategory(Intent.CATEGORY_BROWSABLE);
            browser.setSelector(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER));
            startActivity(browser);
            AuthDiagnostics.event("login.external_browser_launched", "screen", screenOp);
            status.setText(R.string.youtube_auth_external_help);
        } catch (final ActivityNotFoundException ignored) {
            AuthDiagnostics.failure("login.external_browser_failed", ignored, "screen", screenOp);
            status.setText(R.string.youtube_auth_no_browser);
        }
    }

    private void importCookies() {
        AuthDiagnostics.event("login.import_open", "screen", screenOp);
        final LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        final int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        fields.setPadding(padding, padding, padding, padding);
        final EditText cookies = new EditText(this);
        cookies.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        cookies.setMinLines(3);
        cookies.setMaxLines(6);
        cookies.setHint(R.string.youtube_auth_import_hint);
        final EditText version = new EditText(this);
        version.setSingleLine(true);
        version.setHint(R.string.youtube_auth_version_hint);
        final EditText visitor = new EditText(this);
        visitor.setSingleLine(true);
        visitor.setHint(R.string.youtube_auth_visitor_hint);
        fields.addView(cookies);
        fields.addView(version);
        fields.addView(visitor);
        final ScrollView scroll = new ScrollView(this);
        scroll.addView(fields);
        final AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(R.string.youtube_auth_import)
                        .setView(scroll)
                        .setNegativeButton(R.string.youtube_auth_cancel, null)
                        .setPositiveButton(android.R.string.ok, null)
                        .create();
        dialog.setOnShowListener(
                ignored ->
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                .setOnClickListener(
                                        view -> importSession(dialog, cookies, version, visitor)));
        dialog.setOnDismissListener(
                ignored -> {
                    AuthDiagnostics.event("login.import_dismiss", "screen", screenOp);
                    cookies.setText("");
                    version.setText("");
                    visitor.setText("");
                });
        dialog.show();
    }

    private void importSession(
            final AlertDialog dialog,
            final EditText cookies,
            final EditText version,
            final EditText visitor) {
        AuthDiagnostics.event(
                "login.import_submit",
                "screen",
                screenOp,
                "cookies_present",
                cookies.length() > 0,
                "version_present",
                version.length() > 0,
                "visitor_present",
                visitor.length() > 0);
        String value = cookies.getText().toString().trim();
        if (value.regionMatches(true, 0, "Cookie:", 0, 7)) {
            value = value.substring(7).trim();
        }
        if (!validCookies(value)) {
            AuthDiagnostics.event("login.import_cookie_invalid", "screen", screenOp);
            cookies.setError(getString(R.string.youtube_auth_cookie_missing));
            return;
        }
        final String clientVersion = version.getText().toString().trim();
        if (!validVersion(clientVersion)) {
            AuthDiagnostics.event("login.import_version_invalid", "screen", screenOp);
            version.setError(getString(R.string.youtube_auth_version_missing));
            return;
        }
        final String visitorData = visitor.getText().toString().trim();
        if (visitorData.contains("\r")
                || visitorData.contains("\n")
                || visitorData.length() > 8192) {
            visitor.setError(getString(R.string.youtube_auth_invalid));
            AuthDiagnostics.event("login.import_visitor_invalid", "screen", screenOp);
            return;
        }
        dialog.dismiss();
        finishCapture(value, clientVersion, visitorData, null, "");
    }

    private static boolean validCookies(final String cookies) {
        if (cookies == null
                || cookies.length() > 65536
                || cookies.contains("\r")
                || cookies.contains("\n")) {
            return false;
        }
        try {
            PipePipeSessionProvider.authorization(cookies, System.currentTimeMillis() / 1000);
            return true;
        } catch (final Exception ignored) {
            return false;
        }
    }

    private static boolean validVersion(final String version) {
        return version != null && version.matches("[A-Za-z0-9._-]{1,128}");
    }

    private void capture() {
        AuthDiagnostics.event(
                "login.capture_begin",
                "screen",
                screenOp,
                "returning",
                returning,
                "webview_present",
                webView != null);
        if (returning || webView == null) {
            return;
        }
        final String page = webView.getUrl();
        if (page == null
                || !permittedNavigation(Uri.parse(page))
                || !"m.youtube.com".equals(Uri.parse(page).getHost())) {
            status.setText(R.string.youtube_auth_open_mobile);
            AuthDiagnostics.event(
                    "login.capture_move_mobile",
                    "screen",
                    screenOp,
                    "destination",
                    AuthDiagnostics.destination(page));
            webView.loadUrl("https://m.youtube.com/");
            return;
        }
        final String cookies = CookieManager.getInstance().getCookie("https://www.youtube.com");
        if (!validCookies(cookies)) {
            AuthDiagnostics.event("login.capture_cookie_missing", "screen", screenOp);
            status.setText(R.string.youtube_auth_cookie_missing);
            return;
        }
        final WebView currentView = webView;
        final long started = AuthDiagnostics.now();
        AuthDiagnostics.event("login.javascript_begin", "screen", screenOp);
        currentView.evaluateJavascript(
                "JSON.stringify({version:window.ytcfg?.get('INNERTUBE_CONTEXT')"
                        + "?.client?.clientVersion||'',"
                        + "visitor:window.ytcfg?.get('VISITOR_DATA')||''})",
                value -> {
                    if (returning
                            || isFinishing()
                            || webView != currentView
                            || !page.equals(currentView.getUrl())) {
                        AuthDiagnostics.event("login.javascript_stale", "screen", screenOp);
                        return;
                    }
                    try {
                        final Object decoded = new JSONTokener(value).nextValue();
                        if (!(decoded instanceof String)) {
                            throw new IllegalStateException();
                        }
                        final JSONObject config = new JSONObject((String) decoded);
                        final String version = config.optString("version");
                        AuthDiagnostics.event(
                                "login.javascript_result",
                                "screen",
                                screenOp,
                                "elapsed_ms",
                                AuthDiagnostics.now() - started,
                                "version_valid",
                                validVersion(version),
                                "visitor_present",
                                !config.optString("visitor").isEmpty());
                        if (!validVersion(version)) {
                            throw new IllegalStateException();
                        }
                        final String videoId = Uri.parse(page).getQueryParameter("v");
                        finishCapture(
                                cookies,
                                version,
                                config.optString("visitor"),
                                videoId == null ? null : pot,
                                videoId == null ? "" : videoId);
                    } catch (final Exception ignored) {
                        AuthDiagnostics.failure(
                                "login.javascript_failed",
                                ignored,
                                "screen",
                                screenOp,
                                "elapsed_ms",
                                AuthDiagnostics.now() - started);
                        status.setText(R.string.youtube_auth_version_missing);
                    }
                });
    }

    private void finishCapture(
            final String cookies,
            final String version,
            final String visitor,
            final String token,
            final String videoId) {
        if (returning || !validCookies(cookies) || !validVersion(version)) {
            AuthDiagnostics.event(
                    "login.result_rejected", "screen", screenOp, "returning", returning);
            status.setText(R.string.youtube_auth_invalid);
            return;
        }
        returning = true;
        final Intent result = new Intent();
        result.putExtra("cookies", cookies);
        result.putExtra("version", version);
        result.putExtra("visitor", visitor);
        result.putExtra("pot", token);
        result.putExtra("potVideoId", videoId);
        setResult(RESULT_OK, result);
        AuthDiagnostics.event(
                "login.result_ready",
                "screen",
                screenOp,
                "visitor_present",
                visitor != null && !visitor.isEmpty(),
                "stream_token_present",
                token != null && !token.isEmpty());
        finish();
    }

    private void clearWebSession(final Runnable done) {
        AuthDiagnostics.event("login.clear_browser_begin", "screen", screenOp);
        CookieManager.getInstance()
                .removeAllCookies(
                        removed -> {
                            CookieManager.getInstance().flush();
                            WebStorage.getInstance().deleteAllData();
                            AuthDiagnostics.event(
                                    "login.clear_browser_end",
                                    "screen",
                                    screenOp,
                                    "removed",
                                    removed);
                            if (webView != null) {
                                webView.clearCache(true);
                                webView.clearHistory();
                            }
                            done.run();
                        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null && AuthDiagnostics.enabled()) {
            snapshotsActive = true;
            webView.removeCallbacks(snapshotPoll);
            webView.post(snapshotPoll);
        }
        AuthDiagnostics.event(
                "login.resume",
                "screen",
                screenOp,
                "window_flags",
                getWindow().getAttributes().flags,
                "multi_window",
                Build.VERSION.SDK_INT >= 24 && isInMultiWindowMode());
    }

    @Override
    protected void onPause() {
        snapshotsActive = false;
        if (webView != null) {
            webView.removeCallbacks(snapshotPoll);
        }
        AuthDiagnostics.event("login.pause", "screen", screenOp, "returning", returning);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        AuthDiagnostics.event(
                "login.destroy",
                "screen",
                screenOp,
                "finishing",
                isFinishing(),
                "returning",
                returning);
        snapshotsActive = false;
        lastSnapshot = null;
        if (webView != null) {
            webView.removeCallbacks(snapshotPoll);
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    /** Keep the web page usable; all controls remain reachable by scrolling. */
    private static final class ControlsScrollView extends ScrollView {
        ControlsScrollView(final Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(final int widthSpec, final int heightSpec) {
            final int available = View.MeasureSpec.getSize(heightSpec);
            final int capped = Math.max(1, available / 2);
            super.onMeasure(
                    widthSpec, View.MeasureSpec.makeMeasureSpec(capped, View.MeasureSpec.AT_MOST));
        }
    }
}
