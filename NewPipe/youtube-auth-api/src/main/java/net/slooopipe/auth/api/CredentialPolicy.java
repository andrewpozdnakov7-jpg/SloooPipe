// SPDX-License-Identifier: GPL-3.0-or-later
package net.slooopipe.auth.api;

import java.net.URI;
import java.util.Locale;

/** Exact endpoint allowlist, rather than a substring check or all googlevideo hosts. */
public final class CredentialPolicy {
    private CredentialPolicy() { }

    /**
     * Checks browser navigation independently of authenticated backend requests.
     *
     * @param uri destination of the login WebView
     * @return whether the HTTPS origin is an explicitly allowed login service
     */
    public static boolean permitsLoginNavigation(final URI uri) {
        if (uri == null
                || !"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getRawUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            return false;
        }
        switch (uri.getHost().toLowerCase(Locale.ROOT)) {
            case "www.youtube.com":
            case "m.youtube.com":
            case "music.youtube.com":
            case "accounts.youtube.com":
            case "accounts.google.com":
            case "myaccount.google.com":
            case "gds.google.com":
            case "consent.google.com":
            case "consent.youtube.com":
                return true;
            default:
                return GoogleAccountDomains.containsAccountHost(
                        uri.getHost().toLowerCase(Locale.ROOT));
        }
    }

    public static boolean permits(final URI uri) {
        return uri != null
                && "https".equalsIgnoreCase(uri.getScheme())
                && "www.youtube.com".equalsIgnoreCase(uri.getHost())
                && uri.getRawUserInfo() == null
                && uri.getFragment() == null
                && (uri.getPort() == -1 || uri.getPort() == 443)
                && "/youtubei/v1/player".equals(uri.getRawPath());
    }
}
