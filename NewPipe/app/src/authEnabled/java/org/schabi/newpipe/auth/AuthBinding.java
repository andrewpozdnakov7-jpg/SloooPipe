// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.auth;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import net.slooopipe.auth.api.AuthDiagnostics;
import net.slooopipe.auth.api.YoutubeSessionProvider;
import net.slooopipe.auth.pipepipe.PipePipeLoginActivity;
import net.slooopipe.auth.pipepipe.PipePipeSessionProvider;

import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor;

final class AuthBinding {
    private AuthBinding() { }

    static YoutubeSessionProvider create(final Context context) {
        return new PipePipeSessionProvider(context);
    }

    static void installBackend(final Context context, final YoutubeSessionProvider provider) {
        YoutubeStreamExtractor.setAuthenticatedPlayerProvider(new PipePipeBackend(provider));
    }

    static void login(final Activity activity, final int request) {
        final okhttp3.OkHttpClient network =
                org.schabi.newpipe.DownloaderImpl.getInstance().getClient();
        final boolean importOnly =
                network.proxy() != null
                        || network.proxySelector() != java.net.ProxySelector.getDefault();
        AuthDiagnostics.event(
                "binding.login",
                "import_only",
                importOnly,
                "explicit_proxy",
                network.proxy() != null,
                "custom_selector",
                network.proxySelector() != java.net.ProxySelector.getDefault());
        activity.startActivityForResult(
                new Intent(activity, PipePipeLoginActivity.class)
                        .putExtra("importOnly", importOnly),
                request);
    }

    static void capture(final YoutubeSessionProvider provider, final Intent result)
            throws Exception {
        AuthDiagnostics.event("binding.capture_begin", "generation", provider.generation());
        ((PipePipeSessionProvider) provider)
                .capture(
                        result.getStringExtra("cookies"),
                        result.getStringExtra("version"),
                        result.getStringExtra("visitor"),
                        result.getStringExtra("pot"),
                        result.getStringExtra("potVideoId"));
        result.replaceExtras((android.os.Bundle) null);
        AuthDiagnostics.event(
                "binding.capture_end",
                "generation",
                provider.generation(),
                "state",
                provider.state());
    }

    static void clearBrowser(final Activity activity) {
        activity.startActivity(
                new Intent(activity, PipePipeLoginActivity.class)
                        .setAction(PipePipeLoginActivity.CLEAR));
    }
}
