// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.auth;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import net.slooopipe.auth.api.NoAuthSessionProvider;
import net.slooopipe.auth.api.YoutubeSessionProvider;

final class AuthBinding {
    private AuthBinding() { }

    static YoutubeSessionProvider create(final Context context) {
        return new NoAuthSessionProvider();
    }

    static void installBackend(final Context context, final YoutubeSessionProvider provider) { }

    static void login(final Activity activity, final int request) { }

    static void capture(final YoutubeSessionProvider provider, final Intent result) { }

    static void clearBrowser(final Activity activity) { }
}
