// SPDX-License-Identifier: GPL-3.0-or-later
package net.slooopipe.auth.api;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.URI;

public class CredentialPolicyTest {
    @Test
    public void permitsOnlyPlayerEndpoint() {
        assertTrue(CredentialPolicy.permits(URI.create(AuthorizedPlayerRequest.ENDPOINT)));
        for (final String url
                : new String[] {
                    "http://www.youtube.com/youtubei/v1/player",
                    "https://www.youtube.com.evil.test/youtubei/v1/player",
                    "https://evil.test/www.youtube.com/youtubei/v1/player",
                    "https://www.youtube.com:444/youtubei/v1/player",
                    "https://user@www.youtube.com/youtubei/v1/player",
                    "https://www.youtube.com/youtubei/v1/next",
                    "https://www.youtube.com/youtubei/v1/%70layer",
                    "https://r1.googlevideo.com/videoplayback",
                    "https://soundcloud.com/youtubei/v1/player"
                }) {
            assertFalse(url, CredentialPolicy.permits(URI.create(url)));
        }
    }

    @Test
    public void loginAllowsYoutubeSessionTransferWithoutExpandingPlayerEndpoint() {
        final URI transfer =
                URI.create("https://accounts.youtube.com/accounts/SetSID?sidt=example");
        assertTrue(CredentialPolicy.permitsLoginNavigation(transfer));
        assertFalse(CredentialPolicy.permits(transfer));
        final URI accountStep = URI.create("https://gds.google.com/web/landing?continue=example");
        assertTrue(CredentialPolicy.permitsLoginNavigation(accountStep));
        assertFalse(CredentialPolicy.permits(accountStep));
        assertTrue(
                CredentialPolicy.permitsLoginNavigation(
                        URI.create("https://accounts.google.com/v3/signin/challenge/totp")));
        assertTrue(CredentialPolicy.permitsLoginNavigation(URI.create("https://m.youtube.com/")));
    }

    @Test
    public void loginAllowsRegionalGoogleAccountTransfersOnlyForBrowser() {
        final String[] hosts = {
            "accounts.google.ru",
            "accounts.google.co.uk",
            "accounts.google.com.au",
            "accounts.google.co.jp",
            "ACCOUNTS.GOOGLE.DE"
        };
        for (final String host : hosts) {
            final URI transfer = URI.create("https://" + host + "/accounts/SetSID?sidt=example");
            assertTrue(host, CredentialPolicy.permitsLoginNavigation(transfer));
            assertFalse(host, CredentialPolicy.permits(transfer));
        }
    }

    @Test
    public void loginStillRejectsLookalikesAndUnexpectedDestinations() {
        for (final String url
                : new String[] {
                    "http://accounts.youtube.com/accounts/SetSID",
                    "https://accounts.youtube.com.evil.test/accounts/SetSID",
                    "https://evil.test/accounts.youtube.com/accounts/SetSID",
                    "https://accounts.youtube.com:444/accounts/SetSID",
                    "https://password@accounts.youtube.com/accounts/SetSID",
                    "http://gds.google.com/web/landing",
                    "https://gds.google.com.evil.test/web/landing",
                    "https://evil.test/gds.google.com/web/landing",
                    "https://gds.google.com:444/web/landing",
                    "https://user@gds.google.com/web/landing",
                    "http://accounts.google.ru/accounts/SetSID",
                    "https://accounts.google.ru.evil.test/accounts/SetSID",
                    "https://accounts.google.evil.ru/accounts/SetSID",
                    "https://accounts.google.invalid/accounts/SetSID",
                    "https://accounts.google.ru:444/accounts/SetSID",
                    "https://user@accounts.google.ru/accounts/SetSID",
                    "https://evil.accounts.google.ru/accounts/SetSID",
                    "https://www.google.ru/",
                    "https://unknown.youtube.com/",
                    "https://r1.googlevideo.com/videoplayback",
                    "intent://accounts.youtube.com/accounts/SetSID"
                }) {
            assertFalse(url, CredentialPolicy.permitsLoginNavigation(URI.create(url)));
        }
    }
}
