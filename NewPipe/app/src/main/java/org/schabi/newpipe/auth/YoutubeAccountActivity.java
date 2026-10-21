// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.auth;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import net.slooopipe.auth.api.AuthDiagnostics;
import net.slooopipe.auth.api.LatestOperation;
import net.slooopipe.auth.api.YoutubeSessionProvider;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Receives only login results; UI never displays credentials or upstream response bodies. */
public final class YoutubeAccountActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final LatestOperation operation = new LatestOperation();
    private TextView status;
    private EditText videoUrl;
    private boolean retrySelected;
    private static final int LOGIN = 8401;

    @Override
    protected void onCreate(final Bundle saved) {
        super.onCreate(saved);
        AuthDiagnostics.event("account.create", "restored", saved != null);
        setTitle(R.string.slooopipe_youtube_account);
        final LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        final int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        status = new TextView(this);
        layout.addView(status);
        videoUrl = new EditText(this);
        videoUrl.setSingleLine(true);
        videoUrl.setHint(R.string.slooopipe_auth_video_url);
        final String requested = getIntent().getStringExtra("videoUrl");
        if (requested != null) {
            videoUrl.setText(requested);
            retrySelected = true;
        }
        layout.addView(videoUrl);
        button(layout, R.string.slooopipe_auth_login, () -> AuthBinding.login(this, LOGIN));
        button(layout, R.string.slooopipe_auth_check, this::verify);
        button(
                layout,
                R.string.slooopipe_auth_logout,
                () -> {
                    operation.next();
                    AuthRuntime.sessions().logout();
                    AuthBinding.clearBrowser(this);
                    showState();
                });
        final ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(layout, new ScrollView.LayoutParams(-1, -2));
        scroll.setOnApplyWindowInsetsListener(
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
                    return insets;
                });
        setContentView(scroll);
        scroll.post(scroll::requestApplyInsets);
        showState();
    }

    private void button(final LinearLayout layout, final int label, final Runnable action) {
        final Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setSingleLine(false);
        button.setOnClickListener(view -> action.run());
        layout.addView(button, new LinearLayout.LayoutParams(-1, -2));
    }

    private void showState() {
        if (!AuthRuntime.enabled()) {
            status.setText(R.string.slooopipe_auth_unavailable);
            return;
        }
        switch (AuthRuntime.sessions().state()) {
            case ANONYMOUS:
                status.setText(R.string.slooopipe_auth_anonymous);
                break;
            case CAPTURED:
                status.setText(R.string.slooopipe_auth_captured);
                break;
            case VERIFIED:
                status.setText(R.string.slooopipe_auth_verified);
                break;
            case REAUTH_REQUIRED:
                status.setText(R.string.slooopipe_auth_reauth);
                break;
            default:
                status.setText(R.string.slooopipe_auth_unavailable);
        }
    }

    @Override
    protected void onActivityResult(final int request, final int result, final Intent data) {
        super.onActivityResult(request, result, data);
        AuthDiagnostics.event(
                "account.login_result",
                "expected_request",
                request == LOGIN,
                "ok",
                result == RESULT_OK,
                "data_present",
                data != null);
        if (request != LOGIN || result != RESULT_OK || data == null) {
            return;
        }
        try {
            AuthBinding.capture(AuthRuntime.sessions(), data);
            AuthDiagnostics.event(
                    "account.session_captured",
                    "generation",
                    AuthRuntime.generation(),
                    "state",
                    AuthRuntime.sessions().state());
            showState();
            if (retrySelected) {
                verify();
            }
        } catch (final Exception ignored) {
            AuthDiagnostics.failure("account.capture_failed", ignored);
            data.replaceExtras((Bundle) null);
            status.setText(R.string.slooopipe_auth_capture_failed);
        }
    }

    private void verify() {
        AuthDiagnostics.event("account.verify_clicked", "enabled", AuthRuntime.enabled());
        if (!AuthRuntime.enabled()) {
            return;
        }
        if (AuthRuntime.sessions().state() == YoutubeSessionProvider.State.ANONYMOUS
                || AuthRuntime.sessions().state() == YoutubeSessionProvider.State.REAUTH_REQUIRED) {
            showState();
            return;
        }
        final String url = videoUrl.getText().toString().trim();
        final long ticket = operation.next();
        final long generation = AuthRuntime.sessions().generation();
        final long op = AuthDiagnostics.nextOperation();
        final long started = AuthDiagnostics.now();
        AuthDiagnostics.event(
                "account.verify_begin",
                "op",
                op,
                "ticket",
                ticket,
                "generation",
                generation,
                "state",
                AuthRuntime.sessions().state(),
                "destination",
                AuthDiagnostics.destination(url));
        try {
            final Uri parsed = Uri.parse(url);
            if (!"https".equals(parsed.getScheme())
                    || parsed.getHost() == null
                    || !(parsed.getHost().equals("www.youtube.com")
                            || parsed.getHost().equals("youtu.be")
                            || parsed.getHost().equals("m.youtube.com"))
                    || parsed.getUserInfo() != null
                    || (parsed.getPort() != -1 && parsed.getPort() != 443)) {
                throw new IllegalArgumentException();
            }
        } catch (final Exception ignored) {
            AuthDiagnostics.failure("account.url_rejected", ignored, "op", op);
            status.setText(R.string.slooopipe_auth_video_url);
            return;
        }
        status.setText(R.string.slooopipe_auth_checking);
        executor.execute(
                () -> {
                    String failure = null;
                    try {
                        final StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, url);
                        AuthDiagnostics.event(
                                "account.sources",
                                "op",
                                op,
                                "elapsed_ms",
                                AuthDiagnostics.now() - started,
                                "audio",
                                info.getAudioStreams().size(),
                                "video",
                                info.getVideoStreams().size(),
                                "video_only",
                                info.getVideoOnlyStreams().size(),
                                "hls",
                                !info.getHlsUrl().isEmpty(),
                                "dash",
                                !info.getDashMpdUrl().isEmpty());
                        if (info.getAudioStreams().isEmpty()
                                && info.getVideoStreams().isEmpty()
                                && info.getVideoOnlyStreams().isEmpty()
                                && info.getHlsUrl().isEmpty()) {
                            throw new IllegalStateException();
                        }
                        if (!AuthRuntime.sessions().markVerified(generation)) {
                            throw new IllegalStateException();
                        }
                    } catch (final Exception error) {
                        AuthDiagnostics.failure(
                                "account.verify_failed",
                                error,
                                "op",
                                op,
                                "elapsed_ms",
                                AuthDiagnostics.now() - started);
                        // Only recognize our fixed diagnostic codes; never display upstream
                        // messages.
                        failure =
                                containsCode(error, "AUTH_SABR_BACKEND_REQUIRED")
                                        ? getString(R.string.slooopipe_auth_sabr_required)
                                        : getString(R.string.slooopipe_auth_check_failed)
                                                + "\n"
                                                + diagnosticCode(error);
                    }
                    final String message = failure;
                    runOnUiThread(
                            () -> {
                                if (isFinishing()
                                        || isDestroyed()
                                        || !operation.isCurrent(ticket)) {
                                    AuthDiagnostics.event(
                                            "account.verify_ui_stale",
                                            "op",
                                            op,
                                            "destroyed",
                                            isDestroyed(),
                                            "finishing",
                                            isFinishing());
                                    return;
                                }
                                if (generation != AuthRuntime.sessions().generation()) {
                                    AuthDiagnostics.event(
                                            "account.verify_epoch_changed",
                                            "op",
                                            op,
                                            "expected",
                                            generation,
                                            "actual",
                                            AuthRuntime.generation());
                                    showState();
                                    return;
                                }
                                if (message != null) {
                                    status.setText(message);
                                    return;
                                }
                                showState();
                                AuthDiagnostics.event(
                                        "account.verify_success",
                                        "op",
                                        op,
                                        "elapsed_ms",
                                        AuthDiagnostics.now() - started,
                                        "generation",
                                        generation,
                                        "retry",
                                        retrySelected);
                                if (retrySelected) {
                                    retrySelected = false;
                                    startActivity(
                                            new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                    .setPackage(getPackageName()));
                                    finish();
                                }
                            });
                });
    }

    private static String diagnosticCode(final Throwable error) {
        final String[] codes = {
            "AUTH_REAUTH_REQUIRED",
            "AUTH_SESSION_CHANGED",
            "AUTH_RATE_LIMITED",
            "AUTH_SERVER_DENIED",
            "AUTH_LOGIN_REQUIRED_OR_CONTENT_RESTRICTED",
            "AUTH_NETWORK_FAILURE",
            "AUTH_DECODER_NOT_READY",
            "AUTH_SESSION_NOT_READY",
            "AUTH_INVALID_RESPONSE"
        };
        for (final String code : codes) {
            if (containsCode(error, code)) {
                return code;
            }
        }
        return "AUTH_EXTRACTION_FAILED";
    }

    private static boolean containsCode(final Throwable error, final String code) {
        Throwable current = error;
        for (int i = 0; current != null && i < 10; i++, current = current.getCause()) {
            if (code.equals(current.getMessage())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        AuthDiagnostics.event("account.destroy", "finishing", isFinishing());
        operation.next();
        executor.shutdownNow();
        super.onDestroy();
    }
}
