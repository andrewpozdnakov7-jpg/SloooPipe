// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.auth;

import net.slooopipe.auth.api.AuthDiagnostics;

import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.Handshake;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;

/** Auth call timings only: no hostnames, addresses, certificates, headers or bodies. */
final class AuthNetworkTrace extends EventListener {
    private final long op;
    private final long started = AuthDiagnostics.now();

    AuthNetworkTrace(final long op) {
        this.op = op;
    }

    private void event(final String name) {
        AuthDiagnostics.event(name, "op", op, "elapsed_ms", AuthDiagnostics.now() - started);
    }

    @Override
    public void callStart(final Call call) {
        event("http.call_start");
    }

    @Override
    public void dnsStart(final Call call, final String domainName) {
        event("http.dns_start");
    }

    @Override
    public void dnsEnd(
            final Call call, final String domainName, final List<InetAddress> addresses) {
        AuthDiagnostics.event(
                "http.dns_end",
                "op",
                op,
                "address_count",
                addresses.size(),
                "elapsed_ms",
                AuthDiagnostics.now() - started);
    }

    @Override
    public void connectStart(final Call call, final InetSocketAddress address, final Proxy proxy) {
        AuthDiagnostics.event(
                "http.connect_start",
                "op",
                op,
                "port",
                address.getPort(),
                "proxy_type",
                proxy.type(),
                "elapsed_ms",
                AuthDiagnostics.now() - started);
    }

    @Override
    public void secureConnectStart(final Call call) {
        event("http.tls_start");
    }

    @Override
    public void secureConnectEnd(final Call call, final Handshake handshake) {
        event("http.tls_end");
        AuthDiagnostics.event(
                "http.tls_details",
                "op",
                op,
                "version",
                handshake == null ? null : handshake.tlsVersion());
    }

    @Override
    public void connectEnd(
            final Call call,
            final InetSocketAddress address,
            final Proxy proxy,
            final Protocol protocol) {
        event("http.connect_end");
    }

    @Override
    public void connectFailed(
            final Call call,
            final InetSocketAddress address,
            final Proxy proxy,
            final Protocol protocol,
            final IOException error) {
        AuthDiagnostics.failure(
                "http.connect_failed",
                error,
                "op",
                op,
                "elapsed_ms",
                AuthDiagnostics.now() - started);
    }

    @Override
    public void requestHeadersStart(final Call call) {
        event("http.request_headers");
    }

    @Override
    public void requestHeadersEnd(final Call call, final Request request) {
        AuthDiagnostics.event(
                "http.request_details",
                "op",
                op,
                "method",
                AuthDiagnostics.text(request.method()),
                "url",
                AuthDiagnostics.url(request.url().toString()),
                "headers",
                AuthDiagnostics.payload(request.headers().toMultimap()));
    }

    @Override
    public void requestBodyEnd(final Call call, final long byteCount) {
        AuthDiagnostics.event(
                "http.request_sent",
                "op",
                op,
                "bytes",
                byteCount,
                "elapsed_ms",
                AuthDiagnostics.now() - started);
    }

    @Override
    public void responseHeadersStart(final Call call) {
        event("http.response_headers");
    }

    @Override
    public void responseHeadersEnd(final Call call, final Response response) {
        AuthDiagnostics.event(
                "http.response_details",
                "op",
                op,
                "status",
                response.code(),
                "protocol",
                response.protocol(),
                "message",
                AuthDiagnostics.text(response.message()),
                "url",
                AuthDiagnostics.url(response.request().url().toString()),
                "headers",
                AuthDiagnostics.payload(response.headers().toMultimap()));
    }

    @Override
    public void responseBodyEnd(final Call call, final long byteCount) {
        AuthDiagnostics.event(
                "http.response_read",
                "op",
                op,
                "bytes",
                byteCount,
                "elapsed_ms",
                AuthDiagnostics.now() - started);
    }

    @Override
    public void callEnd(final Call call) {
        event("http.call_end");
    }

    @Override
    public void callFailed(final Call call, final IOException error) {
        AuthDiagnostics.failure(
                "http.call_failed", error, "op", op, "elapsed_ms", AuthDiagnostics.now() - started);
    }
}
