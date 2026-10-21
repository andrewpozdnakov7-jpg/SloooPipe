// SPDX-License-Identifier: GPL-3.0-or-later
package net.slooopipe.auth.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable request capability. Never put this object or its fields in diagnostics. */
public final class AuthorizedPlayerRequest {
    public static final String ENDPOINT = "https://www.youtube.com/youtubei/v1/player";
    private final long generation;
    private final byte[] body;
    private final Map<String, String> headers;
    private final String cpn;
    private final String streamingPoToken;

    public AuthorizedPlayerRequest(final long generation, final byte[] body,
            final Map<String, String> headers, final String cpn, final String streamingPoToken) {
        this.generation = generation;
        this.body = body.clone();
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        this.cpn = cpn;
        this.streamingPoToken = streamingPoToken;
    }
    public long generation() { return generation; }
    public byte[] body() { return body.clone(); }
    public Map<String, String> headers() { return headers; }
    public String cpn() { return cpn; }
    public String streamingPoToken() { return streamingPoToken; }
    @Override public String toString() { return "AuthorizedPlayerRequest[REDACTED]"; }
}
