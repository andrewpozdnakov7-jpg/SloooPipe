// SPDX-License-Identifier: GPL-3.0-or-later
// PipePipe MWEB / SAPISIDHASH adaptation; see auth module THIRD_PARTY.md.
package org.schabi.newpipe.auth;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import net.slooopipe.auth.api.AuthDiagnostics;
import net.slooopipe.auth.api.AuthorizedPlayerRequest;
import net.slooopipe.auth.api.CredentialPolicy;
import net.slooopipe.auth.api.YoutubeSessionProvider;

import okhttp3.CookieJar;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.services.youtube.AuthenticatedPlayerProvider;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

/** The only adapter allowed to receive credentials. Other Extractor services stay unchanged. */
final class PipePipeBackend implements AuthenticatedPlayerProvider {
    private final YoutubeSessionProvider sessions;
    private final java.util.Set<okhttp3.Call> active =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    PipePipeBackend(final YoutubeSessionProvider sessions) {
        this.sessions = sessions;
        final java.util.concurrent.atomic.AtomicLong previous =
                new java.util.concurrent.atomic.AtomicLong(sessions.generation());
        sessions.addListener(
                () -> {
                    if (previous.getAndSet(sessions.generation()) != sessions.generation()) {
                        AuthDiagnostics.event(
                                "http.cancel_session_changed",
                                "active",
                                active.size(),
                                "generation",
                                sessions.generation());
                        active.forEach(okhttp3.Call::cancel);
                    }
                });
    }

    @Override
    public Response fetch(final String videoId) throws IOException, ExtractionException {
        final long op = AuthDiagnostics.nextOperation();
        final long started = AuthDiagnostics.now();
        final YoutubeSessionProvider.State state = sessions.state();
        AuthDiagnostics.event(
                "backend.begin", "op", op, "state", state, "generation", sessions.generation());
        if (state == YoutubeSessionProvider.State.ANONYMOUS) {
            AuthDiagnostics.event("backend.anonymous_fallback", "op", op);
            return null;
        }
        if (state == YoutubeSessionProvider.State.REAUTH_REQUIRED) {
            AuthDiagnostics.event("backend.reauth_required", "op", op);
            throw new ExtractionException("AUTH_REAUTH_REQUIRED");
        }
        final AuthorizedPlayerRequest request;
        try {
            request = sessions.preparePlayerRequest(videoId);
        } catch (final Exception ignored) {
            AuthDiagnostics.failure("backend.prepare_failed", ignored, "op", op);
            throw new ExtractionException("AUTH_SESSION_NOT_READY");
        }
        if (!CredentialPolicy.permits(URI.create(AuthorizedPlayerRequest.ENDPOINT))) {
            AuthDiagnostics.event("backend.endpoint_rejected", "op", op);
            throw new ExtractionException("AUTH_ENDPOINT_REJECTED");
        }
        // Inherit user proxy/TLS settings. Do not forward credentials across redirects.
        final OkHttpClient client =
                DownloaderImpl.getInstance()
                        .getClient()
                        .newBuilder()
                        .followRedirects(false)
                        .followSslRedirects(false)
                        .cache(null)
                        .cookieJar(CookieJar.NO_COOKIES)
                        .callTimeout(30, TimeUnit.SECONDS)
                        .eventListener(new AuthNetworkTrace(op))
                        .addNetworkInterceptor(
                                chain -> {
                                    if (sessions.generation() != request.generation()
                                            || !CredentialPolicy.permits(
                                                    chain.request().url().uri())) {
                                        AuthDiagnostics.event(
                                                "http.endpoint_or_epoch_rejected",
                                                "op",
                                                op,
                                                "expected",
                                                request.generation(),
                                                "actual",
                                                sessions.generation());
                                        throw new IOException("AUTH_SESSION_CHANGED");
                                    }
                                    return chain.proceed(chain.request());
                                })
                        .build();
        final byte[] body;
        AuthDiagnostics.event("backend.decoder_begin", "op", op);
        try {
            final org.json.JSONObject payload =
                    new org.json.JSONObject(
                            new String(request.body(), java.nio.charset.StandardCharsets.UTF_8));
            final Integer sts =
                    org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
                            .getSignatureTimestamp(videoId);
            payload.put(
                    "playbackContext",
                    new org.json.JSONObject()
                            .put(
                                    "contentPlaybackContext",
                                    new org.json.JSONObject().put("signatureTimestamp", sts)));
            body = payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            AuthDiagnostics.event(
                    "backend.request_payload",
                    "op",
                    op,
                    "body",
                    AuthDiagnostics.text(payload.toString()));
            AuthDiagnostics.event(
                    "backend.decoder_end",
                    "op",
                    op,
                    "elapsed_ms",
                    AuthDiagnostics.now() - started,
                    "body_bytes",
                    body.length,
                    "stream_token_present",
                    request.streamingPoToken() != null);
        } catch (final Exception ignored) {
            AuthDiagnostics.failure("backend.decoder_failed", ignored, "op", op);
            throw new ExtractionException("AUTH_DECODER_NOT_READY");
        }
        final Request.Builder builder =
                new Request.Builder()
                        .url(AuthorizedPlayerRequest.ENDPOINT)
                        .post(RequestBody.create(body, MediaType.get("application/json")));
        request.headers().forEach(builder::header);
        final okhttp3.Call call = client.newCall(builder.build());
        active.add(call);
        if (sessions.generation() != request.generation()) {
            AuthDiagnostics.event("http.cancel_before_execute", "op", op);
            call.cancel();
        }
        try (okhttp3.Response response = call.execute()) {
            AuthDiagnostics.event(
                    "backend.http_response",
                    "op",
                    op,
                    "status",
                    response.code(),
                    "elapsed_ms",
                    AuthDiagnostics.now() - started,
                    "body_present",
                    response.body() != null);
            if (sessions.generation() != request.generation()) {
                throw new ExtractionException("AUTH_SESSION_CHANGED");
            }
            if (response.code() == 401) {
                sessions.requireReauthentication(request.generation());
                throw new ExtractionException("AUTH_REAUTH_REQUIRED");
            }
            if (response.code() == 429) {
                throw new ExtractionException("AUTH_RATE_LIMITED");
            }
            if (response.code() == 403) {
                throw new ExtractionException("AUTH_SERVER_DENIED");
            }
            if (!response.isSuccessful() || response.body() == null) {
                throw new ExtractionException("AUTH_HTTP_" + response.code());
            }
            final JsonObject player;
            try {
                player = JsonParser.object().from(response.body().string());
            } catch (final Exception ignored) {
                AuthDiagnostics.failure("backend.json_failed", ignored, "op", op);
                throw new ExtractionException("AUTH_INVALID_RESPONSE");
            }
            final JsonObject status = player.getObject("playabilityStatus");
            AuthDiagnostics.event(
                    "backend.playability_payload",
                    "op",
                    op,
                    "payload",
                    AuthDiagnostics.payload(status));
            AuthDiagnostics.event(
                    "backend.player_payload", "op", op, "payload", AuthDiagnostics.payload(player));
            try {
                final JsonObject streams = player.getObject("streamingData", new JsonObject());
                AuthDiagnostics.event(
                        "backend.player_summary",
                        "op",
                        op,
                        "playability",
                        AuthDiagnostics.playability(status.getString("status")),
                        "formats",
                        streams.getArray("formats", new JsonArray()).size(),
                        "adaptive",
                        streams.getArray("adaptiveFormats", new JsonArray()).size(),
                        "hls",
                        !streams.getString("hlsManifestUrl", "").isEmpty(),
                        "dash",
                        !streams.getString("dashManifestUrl", "").isEmpty(),
                        "sabr",
                        !streams.getString("serverAbrStreamingUrl", "").isEmpty(),
                        "video_matches",
                        videoId.equals(
                                player.getObject("videoDetails", new JsonObject())
                                        .getString("videoId")));
            } catch (final RuntimeException diagnosticError) {
                AuthDiagnostics.failure("backend.summary_failed", diagnosticError, "op", op);
            }
            if ("LOGIN_REQUIRED".equals(status.getString("status"))) {
                throw new ExtractionException("AUTH_LOGIN_REQUIRED_OR_CONTENT_RESTRICTED");
            }
            // No raw response, cookie, token or signed URL is attached to exceptions.
            return new Response(
                    player,
                    request.cpn(),
                    request.streamingPoToken(),
                    () -> sessions.generation() == request.generation(),
                    (itag, error) -> AuthDiagnostics.failure(
                            "backend.format_rejected", error, "op", op, "itag", itag));
        } catch (final IOException ignored) {
            AuthDiagnostics.failure(
                    "backend.network_failed",
                    ignored,
                    "op",
                    op,
                    "elapsed_ms",
                    AuthDiagnostics.now() - started,
                    "stale",
                    sessions.generation() != request.generation());
            throw new IOException(
                    sessions.generation() == request.generation()
                            ? "AUTH_NETWORK_FAILURE"
                            : "AUTH_SESSION_CHANGED");
        } finally {
            active.remove(call);
            AuthDiagnostics.event(
                    "backend.end",
                    "op",
                    op,
                    "elapsed_ms",
                    AuthDiagnostics.now() - started,
                    "active",
                    active.size());
        }
    }
}
