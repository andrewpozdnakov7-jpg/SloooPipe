// SPDX-License-Identifier: GPL-3.0-or-later
package net.slooopipe.auth.api;

import java.net.URI;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Structured diagnostics with explicit sanitized text, URL and payload factories. */
public final class AuthDiagnostics {
    public interface Sink {
        void write(boolean failure, String line);
    }

    public enum Destination {
        YOUTUBE_MOBILE,
        YOUTUBE,
        GOOGLE_ACCOUNT,
        YOUTUBE_ACCOUNT,
        CONSENT,
        GOOGLE_VIDEO,
        OTHER,
        INVALID
    }

    /** Only factory-sanitized text can be logged; arbitrary String values stay omitted. */
    public static final class SafeText {
        private final String value;

        private SafeText(final String value) {
            this.value = value;
        }

        public String display() {
            return value;
        }
    }

    public static boolean enabled() {
        return sink != null;
    }

    public static SafeText text(final String value) {
        try {
            return boundedText(DiagnosticRedactor.redact(value == null ? "[null]" : value));
        } catch (final RuntimeException ignored) {
            return new SafeText("[TEXT_UNAVAILABLE]");
        }
    }

    private static SafeText boundedText(final String value) {
        final String safe = value.replace("\r", "\\r").replace("\n", "\\n");
        return new SafeText(
                safe.length() > 12000 ? safe.substring(0, 12000) + "[TRUNCATED]" : safe);
    }

    public static SafeText url(final String value) {
        return boundedText(DiagnosticRedactor.redactUrl(value));
    }

    public static SafeText payload(final Object value) {
        if (!enabled()) {
            return new SafeText("[DISABLED]");
        }
        try {
            final StringBuilder result = new StringBuilder();
            appendPayload(result, value, 0);
            return boundedText(result.toString());
        } catch (final RuntimeException ignored) {
            return new SafeText("[PAYLOAD_UNAVAILABLE]");
        }
    }

    private static boolean sensitive(final String key) {
        return key.toLowerCase(Locale.ROOT)
                .matches(
                        ".*(cookie|authorization|token|visitor|sapisid|password|passwd|credential"
                            + "|csrf|nonce|signature|cipher|tracking|email|otp|cpn|session|params"
                            + "|videoid|api.?key).*|^(sid|hsid|ssid|apisid|sidcc|code|key)$");
    }

    private static void appendPayload(
            final StringBuilder out, final Object value, final int depth) {
        if (depth >= 12 || out.length() >= 48000) {
            out.append("[LIMIT]");
            return;
        }
        if (value instanceof Map<?, ?>) {
            final Map<?, ?> map = (Map<?, ?>) value;
            final Object pairedKey = map.get("key");
            final boolean secretPair = pairedKey instanceof String && sensitive((String) pairedKey);
            int count = 0;
            out.append('{');
            for (final Map.Entry<?, ?> entry : map.entrySet()) {
                if (count++ >= 160 || out.length() >= 48000) {
                    out.append("[LIMIT]");
                    break;
                }
                if (!(entry.getKey() instanceof String)) {
                    continue;
                }
                final String key = (String) entry.getKey();
                out.append(key.matches("[A-Za-z0-9_.-]{1,100}") ? key : "FIELD").append('=');
                if (sensitive(key) || (secretPair && "value".equals(key))) {
                    out.append("[REDACTED]");
                } else {
                    appendPayload(out, entry.getValue(), depth + 1);
                }
                out.append(';');
            }
            out.append('}');
        } else if (value instanceof Iterable<?>) {
            int count = 0;
            out.append('[');
            for (final Object item : (Iterable<?>) value) {
                if (count++ >= 100 || out.length() >= 48000) {
                    out.append("[LIMIT]");
                    break;
                }
                appendPayload(out, item, depth + 1);
                out.append(';');
            }
            out.append(']');
        } else if (value instanceof String) {
            out.append(DiagnosticRedactor.redact((String) value));
        } else if (value == null
                || value instanceof Boolean
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Short
                || value instanceof Byte
                || value instanceof Double
                || value instanceof Float) {
            out.append(value);
        } else {
            out.append("[OMITTED]");
        }
    }

    public enum Playability {
        OK,
        LOGIN_REQUIRED,
        UNPLAYABLE,
        LIVE_STREAM_OFFLINE,
        AGE_CHECK_REQUIRED,
        ERROR,
        OTHER,
        MISSING
    }

    public enum FailureCode {
        AUTH_REAUTH_REQUIRED,
        AUTH_SESSION_CHANGED,
        AUTH_RATE_LIMITED,
        AUTH_SERVER_DENIED,
        AUTH_LOGIN_REQUIRED_OR_CONTENT_RESTRICTED,
        AUTH_NETWORK_FAILURE,
        AUTH_DECODER_NOT_READY,
        AUTH_SESSION_NOT_READY,
        AUTH_INVALID_RESPONSE,
        AUTH_SABR_BACKEND_REQUIRED,
        OTHER
    }

    public static FailureCode failureCode(final Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 10; depth++) {
            for (final FailureCode code : FailureCode.values()) {
                if (code.name().equals(current.getMessage())) {
                    return code;
                }
            }
            current = current.getCause();
        }
        return FailureCode.OTHER;
    }

    public static Playability playability(final String value) {
        if (value == null) {
            return Playability.MISSING;
        }
        try {
            return Playability.valueOf(value);
        } catch (final IllegalArgumentException ignored) {
            return Playability.OTHER;
        }
    }

    private static final AtomicLong IDS = new AtomicLong();
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static volatile Sink sink;

    private AuthDiagnostics() { }

    public static void install(final Sink target) {
        sink = target;
    }

    public static long nextOperation() {
        return IDS.incrementAndGet();
    }

    public static long now() {
        return System.nanoTime() / 1_000_000;
    }

    public static void event(final String event, final Object... fields) {
        write(event, null, fields);
    }

    public static void failure(final String event, final Throwable error, final Object... fields) {
        write(event, error, fields);
    }

    private static void write(final String event, final Throwable error, final Object[] fields) {
        final Sink target = sink;
        if (target == null) {
            return;
        }
        try {
            final String name =
                    event != null && event.matches("[a-z][a-z0-9_.]{0,79}")
                            ? event
                            : "diagnostic.invalid_event";
            final long sequence = SEQUENCE.incrementAndGet();
            final StringBuilder line =
                    new StringBuilder(name)
                            .append(" seq=")
                            .append(sequence)
                            .append(" mono_ms=")
                            .append(now())
                            .append(" thread=")
                            .append(Thread.currentThread().getId());
            for (int i = 0; fields != null && i + 1 < fields.length && i < 48; i += 2) {
                if (!(fields[i] instanceof String)
                        || !((String) fields[i]).matches("[a-z][a-z0-9_]{0,39}")) {
                    continue;
                }
                line.append(' ').append((String) fields[i]).append('=');
                final Object value = fields[i + 1];
                if (value instanceof Boolean
                        || value instanceof Integer
                        || value instanceof Long
                        || value instanceof Short
                        || value instanceof Byte) {
                    line.append(value);
                } else if (value instanceof Enum<?>) {
                    line.append(((Enum<?>) value).name());
                } else if (value instanceof SafeText) {
                    line.append(((SafeText) value).value);
                } else {
                    // Includes String, URI, Request, Intent, headers and credential objects.
                    line.append("OMITTED");
                }
            }
            if (error != null) {
                appendFailure(line, error);
            }
            final String message = line.toString();
            if (message.length() <= 900) {
                target.write(error != null, message);
            } else {
                final int parts = (message.length() + 899) / 900;
                for (int part = 0; part < parts; part++) {
                    target.write(
                            error != null,
                            name
                                    + " seq="
                                    + sequence
                                    + " part="
                                    + (part + 1)
                                    + "/"
                                    + parts
                                    + " "
                                    + message.substring(
                                            part * 900,
                                            Math.min(message.length(), (part + 1) * 900)));
                }
            }
        } catch (final RuntimeException ignored) {
            // Diagnostics must not replace the original app failure or alter its outcome.
        }
    }

    private static void appendFailure(final StringBuilder line, final Throwable error) {
        line.append(" code=").append(failureCode(error));
        final Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = error;
        for (int depth = 0; current != null && depth < 10 && seen.add(current); depth++) {
            line.append(" cause").append(depth).append('=').append(current.getClass().getName());
            line.append(" message=").append(text(current.getMessage()).value);
            final StackTraceElement[] frames = current.getStackTrace();
            for (int i = 0; i < Math.min(frames.length, 50); i++) {
                line.append(" at=")
                        .append(frames[i].getClassName())
                        .append('.')
                        .append(frames[i].getMethodName())
                        .append(':')
                        .append(frames[i].getLineNumber());
            }
            for (final Throwable suppressed : current.getSuppressed()) {
                line.append(" suppressed=")
                        .append(suppressed.getClass().getName())
                        .append(" message=")
                        .append(text(suppressed.getMessage()).value);
                if (line.length() > 48000) {
                    break;
                }
            }
            current = current.getCause();
        }
    }

    public static Destination destination(final String address) {
        try {
            final String host = URI.create(address).getHost();
            if (host == null) {
                return Destination.INVALID;
            }
            switch (host.toLowerCase(Locale.ROOT)) {
                case "m.youtube.com":
                    return Destination.YOUTUBE_MOBILE;
                case "www.youtube.com":
                case "youtube.com":
                case "youtu.be":
                case "music.youtube.com":
                    return Destination.YOUTUBE;
                case "accounts.google.com":
                case "myaccount.google.com":
                case "gds.google.com":
                    return Destination.GOOGLE_ACCOUNT;
                case "accounts.youtube.com":
                    return Destination.YOUTUBE_ACCOUNT;
                case "consent.google.com":
                case "consent.youtube.com":
                    return Destination.CONSENT;
                default:
                    if (GoogleAccountDomains.containsAccountHost(host.toLowerCase(Locale.ROOT))) {
                        return Destination.GOOGLE_ACCOUNT;
                    }
                    return host.toLowerCase(Locale.ROOT).endsWith(".googlevideo.com")
                            ? Destination.GOOGLE_VIDEO
                            : Destination.OTHER;
            }
        } catch (final RuntimeException ignored) {
            return Destination.INVALID;
        }
    }
}
