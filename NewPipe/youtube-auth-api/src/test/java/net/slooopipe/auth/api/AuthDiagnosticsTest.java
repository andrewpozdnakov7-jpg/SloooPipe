// SPDX-License-Identifier: GPL-3.0-or-later
package net.slooopipe.auth.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AuthDiagnosticsTest {
    @After
    public void resetSink() {
        AuthDiagnostics.install(null);
    }

    @Test
    public void onlyStructuredValuesReachSink() {
        final List<String> lines = new ArrayList<>();
        AuthDiagnostics.install((failure, line) -> lines.add(line));
        final Object mustNotBeStringified =
                new Object() {
                    @Override
                    public String toString() {
                        throw new AssertionError("Unsafe toString");
                    }
                };
        AuthDiagnostics.event(
                "test.fields",
                "status",
                403,
                "present",
                true,
                "state",
                YoutubeSessionProvider.State.CAPTURED,
                "cookie",
                "SECRET-cookie",
                "object",
                mustNotBeStringified,
                "SECRET-invalid-key?",
                "SECRET-value");
        final String line = lines.get(0);
        assertTrue(line.contains("status=403"));
        assertTrue(line.contains("state=CAPTURED"));
        assertTrue(line.contains("cookie=OMITTED"));
        assertFalse(line.contains("SECRET"));
    }

    @Test
    public void failureKeepsCodeAndLocationsWithoutSecretsOrSourcePaths() {
        final List<String> lines = new ArrayList<>();
        AuthDiagnostics.install((failure, line) -> lines.add(line));
        final Throwable inner = new IllegalStateException("AUTH_SABR_BACKEND_REQUIRED");
        final Throwable outer =
                new RuntimeException("Cookie: SECRET; https://host/?pot=SECRET", inner);
        outer.setStackTrace(
                new StackTraceElement[] {
                    new StackTraceElement("example.Owner", "run", "SECRET-source-path", 42)
                });
        AuthDiagnostics.failure("test.failure", outer);
        assertTrue(lines.get(0).contains("code=AUTH_SABR_BACKEND_REQUIRED"));
        assertTrue(lines.get(0).contains("example.Owner.run:42"));
        assertFalse(String.join("\n", lines).contains("SECRET"));
        assertFalse(lines.get(0).contains("https://"));
    }

    @Test
    public void cyclicCausesAndBrokenSinkDoNotChangeAppFlow() {
        final List<String> lines = new ArrayList<>();
        AuthDiagnostics.install((failure, line) -> lines.add(line));
        final Throwable first = new RuntimeException("Cookie: SECRET-one");
        final Throwable second = new RuntimeException("Authorization: SECRET-two", first);
        first.initCause(second);
        AuthDiagnostics.failure("test.cycle", first);
        assertTrue(!lines.isEmpty() && lines.size() < 100);
        assertFalse(String.join("\n", lines).contains("SECRET"));
        AuthDiagnostics.install(
                (failure, line) -> {
                    throw new IllegalStateException("sink failure");
                });
        AuthDiagnostics.event("test.broken_sink", "status", 200);
    }

    @Test
    public void destinationIsCategoryAndNeverUrl() {
        assertEquals(
                AuthDiagnostics.Destination.GOOGLE_ACCOUNT,
                AuthDiagnostics.destination("https://accounts.google.com/signin?email=SECRET"));
        assertEquals(
                AuthDiagnostics.Destination.GOOGLE_VIDEO,
                AuthDiagnostics.destination("https://r1.googlevideo.com/videoplayback?pot=SECRET"));
        assertEquals(
                AuthDiagnostics.Destination.GOOGLE_ACCOUNT,
                AuthDiagnostics.destination("https://gds.google.com/web/landing?rapt=SECRET"));
        assertEquals(
                AuthDiagnostics.Destination.GOOGLE_ACCOUNT,
                AuthDiagnostics.destination(
                        "https://accounts.google.ru/accounts/SetSID?sidt=SECRET"));
        assertEquals(
                AuthDiagnostics.Destination.OTHER,
                AuthDiagnostics.destination("https://accounts.google.com.example/signin"));
        assertEquals(AuthDiagnostics.Destination.INVALID, AuthDiagnostics.destination(null));
    }

    @Test
    public void debugUrlKeepsRouteWithoutUserinfoQueryOrFragmentValues() {
        final String safe =
                AuthDiagnostics.url(
                                "https://user:SECRET@accounts.youtube.com/accounts/SetSID"
                                        + "?sidt=SECRET&continue=SECRET#SECRET")
                        .display();
        assertTrue(safe.contains("accounts.youtube.com/accounts/SetSID"));
        assertTrue(safe.contains("sidt=[REDACTED]"));
        assertFalse(safe.contains("SECRET"));
        assertFalse(safe.contains("user:"));
        assertEquals(
                AuthDiagnostics.Destination.YOUTUBE_ACCOUNT,
                AuthDiagnostics.destination("https://accounts.youtube.com/accounts/SetSID"));
    }

    @Test
    public void payloadRedactsCredentialKeysAndTrackingKeyValuePairs() {
        AuthDiagnostics.install((failure, line) -> { });
        final Map<String, Object> pair = new LinkedHashMap<>();
        pair.put("key", "visitor_data");
        pair.put("value", "SECRET-short");
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("Cookie", "SECRET-cookie");
        body.put("Authorization", "SECRET-header");
        body.put("clientData", pair);
        body.put("reason", "browser rejected");
        body.put("url", "https://accounts.youtube.com/accounts/SetSID?sidt=SECRET");
        final String safe = AuthDiagnostics.payload(body).display();
        assertFalse(safe.contains("SECRET"));
        assertTrue(safe.contains("browser rejected"));
        assertTrue(safe.contains("accounts.youtube.com/accounts/SetSID"));
    }

    @Test
    public void pageAndConsoleTextKeepsErrorWithoutEmailPasswordOrCode() {
        final String safe =
                AuthDiagnostics.text(
                                "This browser or app may not be secure\n"
                                        + "email=person@example.com\npassword=SECRET\ncode=123456")
                        .display();
        assertTrue(safe.contains("This browser or app may not be secure"));
        assertFalse(safe.contains("person@example.com"));
        assertFalse(safe.contains("SECRET"));
        assertFalse(safe.contains("123456"));
    }

    @Test
    public void longUnicodeRecordsAreChunkedWithCorrelation() {
        final List<String> lines = new ArrayList<>();
        AuthDiagnostics.install((failure, line) -> lines.add(line));
        AuthDiagnostics.event(
                "test.long_record", "text", AuthDiagnostics.text("статус ".repeat(1500)));
        assertTrue(lines.size() > 1);
        for (final String line : lines) {
            assertTrue(line.contains("part="));
            assertTrue(line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 4000);
        }
    }
}
