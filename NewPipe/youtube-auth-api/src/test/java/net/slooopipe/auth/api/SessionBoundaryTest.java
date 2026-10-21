// SPDX-License-Identifier: GPL-3.0-or-later
package net.slooopipe.auth.api;
import org.junit.Test;
import static org.junit.Assert.*;

public class SessionBoundaryTest {
    @Test public void lateAIsRejectedAfterBAndAfterCancel() {
        LatestOperation gate = new LatestOperation();
        long a = gate.next();
        long b = gate.next();
        assertFalse(gate.isCurrent(a));
        assertTrue(gate.isCurrent(b));
        gate.next(); // cancellation / activity destruction
        assertFalse(gate.isCurrent(b));
    }
    @Test public void diagnosticsDoNotContainCredentialsOrSignedQuery() {
        String safe = DiagnosticRedactor.redact("Authorization: private-header\nCookie: private-cookie\n"
                + "https://r1.googlevideo.com/videoplayback?sig=private-signature&pot=private-pot\n"
                + "AUTH_SABR_BACKEND_REQUIRED");
        assertFalse(safe.contains("private-"));
        assertTrue(safe.contains("AUTH_SABR_BACKEND_REQUIRED"));
    }
    @Test public void noAuthCannotAccidentallyProduceCredentials() {
        YoutubeSessionProvider provider = new NoAuthSessionProvider();
        assertEquals(YoutubeSessionProvider.State.UNAVAILABLE, provider.state());
        assertFalse(provider.markVerified(0));
        try { provider.preparePlayerRequest("abcdefghijk"); fail();
        } catch (IllegalStateException expected) { /* intentionally unavailable */ }
    }
}
