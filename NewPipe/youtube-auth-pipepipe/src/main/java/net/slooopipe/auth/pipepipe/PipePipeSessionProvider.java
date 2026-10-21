// SPDX-License-Identifier: GPL-3.0-or-later
// Adapted from PipePipe YoutubeParsingHelper.getAuthorizationHeader and MWEB client.
// See THIRD_PARTY.md for pinned donor revisions and upstream authors/license.
package net.slooopipe.auth.pipepipe;

import android.content.Context;

import net.slooopipe.auth.api.AuthDiagnostics;
import net.slooopipe.auth.api.AuthorizedPlayerRequest;
import net.slooopipe.auth.api.YoutubeSessionProvider;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PipePipeSessionProvider implements YoutubeSessionProvider {
    private final EncryptedSessionStore store;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private JSONObject session;
    private State state = State.ANONYMOUS;
    private long generation;

    public PipePipeSessionProvider(Context context) {
        AuthDiagnostics.event("session.restore_begin");
        store = new EncryptedSessionStore(context);
        try {
            String data = store.read();
            if (data != null) {
                JSONObject loaded = new JSONObject(data);
                authorization(loaded.getString("cookies"), System.currentTimeMillis() / 1000);
                session = loaded;
                state = State.CAPTURED; // server verification is never inferred from disk.
            }
        } catch (Exception ignored) {
            AuthDiagnostics.failure("session.restore_failed", ignored);
            store.clear();
        }
        AuthDiagnostics.event("session.restore_end", "state", state, "generation", generation);
    }

    @Override
    public synchronized State state() {
        return state;
    }

    @Override
    public synchronized long generation() {
        return generation;
    }

    public void capture(
            String cookies,
            String clientVersion,
            String visitorData,
            String streamingPoToken,
            String tokenVideoId)
            throws Exception {
        AuthDiagnostics.event(
                "session.capture_begin",
                "generation",
                generation(),
                "version_present",
                clientVersion != null && !clientVersion.isEmpty(),
                "visitor_present",
                visitorData != null && !visitorData.isEmpty(),
                "stream_token_present",
                streamingPoToken != null && !streamingPoToken.isEmpty());
        if (cookies == null
                || cookies.length() > 65536
                || cookies.contains("\r")
                || cookies.contains("\n")) {
            throw new IllegalArgumentException("Invalid session");
        }
        authorization(cookies, System.currentTimeMillis() / 1000);
        JSONObject captured =
                new JSONObject()
                        .put("cookies", cookies)
                        .put("clientVersion", clientVersion == null ? "" : clientVersion)
                        .put("visitorData", visitorData == null ? "" : visitorData)
                        .put("streamingPoToken", streamingPoToken == null ? "" : streamingPoToken)
                        .put("tokenVideoId", tokenVideoId == null ? "" : tokenVideoId);
        synchronized (this) {
            store.write(captured.toString());
            generation++;
            session = captured;
            state = State.CAPTURED;
        }
        changed();
        AuthDiagnostics.event("session.capture_end", "generation", generation(), "state", state());
    }

    @Override
    public AuthorizedPlayerRequest preparePlayerRequest(String videoId) {
        if (!videoId.matches("[A-Za-z0-9_-]{11}")) {
            throw new IllegalArgumentException("Invalid video ID");
        }
        synchronized (this) {
            if (session == null || state == State.REAUTH_REQUIRED) {
                throw new IllegalStateException("YouTube sign-in required");
            }
            try {
                String cookies = session.getString("cookies");
                String version = session.optString("clientVersion");
                if (version.isEmpty()) {
                    throw new IllegalStateException("Missing web client version");
                }
                String cpn = randomCpn();
                String ua =
                        "Mozilla/5.0 (iPad; CPU OS 16_7_10 like Mac OS X) AppleWebKit/605.1.15"
                            + " (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1";
                JSONObject client =
                        new JSONObject()
                                .put("clientName", "MWEB")
                                .put("clientVersion", version)
                                .put("hl", "en")
                                .put("gl", "US")
                                .put("userAgent", ua);
                String visitor = session.optString("visitorData");
                if (!visitor.isEmpty()) {
                    client.put("visitorData", visitor);
                }
                byte[] body =
                        new JSONObject()
                                .put("context", new JSONObject().put("client", client))
                                .put("videoId", videoId)
                                .put("cpn", cpn)
                                .put("contentCheckOk", true)
                                .put("racyCheckOk", true)
                                .toString()
                                .getBytes(StandardCharsets.UTF_8);
                Map<String, String> headers = new LinkedHashMap<>();
                headers.put("Cookie", cookies);
                headers.put(
                        "Authorization", authorization(cookies, System.currentTimeMillis() / 1000));
                headers.put("X-Origin", "https://www.youtube.com");
                headers.put("Origin", "https://www.youtube.com");
                headers.put("X-YouTube-Client-Name", "2");
                headers.put("X-YouTube-Client-Version", version);
                headers.put("User-Agent", ua);
                if (!visitor.isEmpty()) {
                    headers.put("X-Goog-Visitor-Id", visitor);
                }
                // Captured videoplayback pot is stream/video-bound, not a player-attestation token.
                String pot =
                        videoId.equals(session.optString("tokenVideoId"))
                                ? session.optString("streamingPoToken", null)
                                : null;
                if (pot != null && pot.isEmpty()) {
                    pot = null;
                }
                return new AuthorizedPlayerRequest(generation, body, headers, cpn, pot);
            } catch (IllegalStateException error) {
                throw error;
            } catch (Exception error) {
                throw new IllegalStateException("Cannot prepare YouTube session");
            }
        }
    }

    @Override
    public boolean markVerified(long expected) {
        synchronized (this) {
            if (generation != expected || session == null || state == State.REAUTH_REQUIRED) {
                AuthDiagnostics.event(
                        "session.verify_stale",
                        "expected",
                        expected,
                        "actual",
                        generation,
                        "state",
                        state);
                return false;
            }
            state = State.VERIFIED;
        }
        changed();
        AuthDiagnostics.event("session.verified", "generation", generation(), "state", state());
        return true;
    }

    @Override
    public void requireReauthentication(long expected) {
        AuthDiagnostics.event(
                "session.reauth_requested", "expected", expected, "actual", generation());
        synchronized (this) {
            if (generation != expected || session == null) {
                return;
            }
            generation++;
            state = State.REAUTH_REQUIRED;
        }
        changed();
    }

    @Override
    public void logout() {
        AuthDiagnostics.event("session.logout_begin", "generation", generation());
        synchronized (this) {
            generation++;
            session = null;
            state = State.ANONYMOUS;
            store.clear();
        }
        changed();
        AuthDiagnostics.event("session.logout_end", "generation", generation(), "state", state());
    }

    @Override
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void changed() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    static String authorization(String cookies, long timestamp) throws Exception {
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String part : cookies.split(";")) {
            int split = part.indexOf('=');
            if (split > 0) {
                parsed.put(part.substring(0, split).trim(), part.substring(split + 1).trim());
            }
        }
        String sapisid = parsed.get("SAPISID");
        if (sapisid == null || sapisid.isEmpty()) {
            sapisid = parsed.get("__Secure-3PAPISID");
        }
        if (sapisid == null || sapisid.isEmpty()) {
            throw new IllegalArgumentException("Missing session cookie");
        }
        byte[] hash =
                MessageDigest.getInstance("SHA-1")
                        .digest(
                                (timestamp + " " + sapisid + " https://www.youtube.com")
                                        .getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
        }
        return "SAPISIDHASH " + timestamp + "_" + hex;
    }

    private static String randomCpn() {
        final String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        SecureRandom random = new SecureRandom();
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            value.append(alphabet.charAt(random.nextInt(64)));
        }
        return value.toString();
    }

    @Override
    public String toString() {
        return "PipePipeSessionProvider[REDACTED]";
    }
}
