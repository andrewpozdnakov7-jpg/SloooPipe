// SPDX-License-Identifier: GPL-3.0-or-later
package net.slooopipe.auth.api;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DiagnosticRedactor {
    private static final Pattern URL =
            Pattern.compile("https?://[^\\s<>\\\"']+", Pattern.CASE_INSENSITIVE);

    private DiagnosticRedactor() { }

    public static String redact(final String input) {
        if (input == null) {
            return null;
        }
        String safe =
                input.replaceAll("(?im)(Cookie|Authorization)\\s*[:=][^\\r\\n]*", "$1: [REDACTED]");
        safe =
                safe.replaceAll(
                        "(?i)(SAPISID|__Secure-3PAPISID|poToken|visitorData|pot)"
                                + "\\s*[=:]\\s*[^\\s;,}]+",
                        "$1=[REDACTED]");
        final Matcher matcher = URL.matcher(safe);
        final StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(output, Matcher.quoteReplacement(redactUrl(matcher.group())));
        }
        matcher.appendTail(output);
        safe =
                output.toString()
                        .replaceAll(
                                "(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", "[EMAIL_REDACTED]");
        safe =
                safe.replaceAll(
                        "(?i)([\\\"']?(?:Cookie|cookies|Authorization|password|passwd|otp|totp"
                            + "|code|email|videoId|cpn|token|access_token"
                            + "|refresh_token|visitor_data"
                            + "|visitorData|SAPISID|__Secure-[A-Za-z0-9_-]+"
                            + "|SID|HSID|SSID|APISID|SIDCC)"
                            + "[\\\"']?\\s*[:=]\\s*)"
                            + "(?:\\\"(?:\\\\.|[^\\\"\\\\])*\\\"|[^\\s;,}]+)",
                        "$1[REDACTED]");
        return redactOpaque(safe).replaceAll("\\b[0-9]{6,}\\b", "[NUMBER_REDACTED]");
    }

    private static String redactOpaque(final String input) {
        final Matcher matches = Pattern.compile("[A-Za-z0-9_-]{24,}").matcher(input);
        final StringBuffer output = new StringBuffer();
        while (matches.find()) {
            final String value = matches.group();
            final String replacement =
                    value.startsWith("AUTH_") && value.matches("[A-Z_]+")
                            ? value
                            : "[OPAQUE_REDACTED]";
            matches.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matches.appendTail(output);
        return output.toString();
    }

    public static String redactUrl(final String input) {
        try {
            final URI uri = URI.create(input);
            final String scheme = uri.getScheme();
            if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
                return scheme != null && scheme.matches("[A-Za-z][A-Za-z0-9+.-]{0,31}")
                        ? scheme + ":[DETAILS_REDACTED]"
                        : "[INVALID_URL]";
            }
            final String host = uri.getHost();
            if (host == null) {
                return "[INVALID_URL]";
            }
            final StringBuilder result =
                    new StringBuilder(scheme.toLowerCase(Locale.ROOT))
                            .append("://")
                            .append(host.toLowerCase(Locale.ROOT));
            if (uri.getPort() != -1) {
                result.append(':').append(uri.getPort());
            }
            final String path = uri.getRawPath();
            if (path != null) {
                result.append(
                        redactOpaque(path.replaceAll("[^/]*(?:%40|@)[^/]*", "[ADDRESS_REDACTED]"))
                                .replaceAll("[0-9]{6,}", "[NUMBER_REDACTED]"));
            }
            if (uri.getRawQuery() != null) {
                int count = 0;
                for (final String field : uri.getRawQuery().split("&")) {
                    if (count >= 80) {
                        break;
                    }
                    final String key = field.split("=", 2)[0];
                    result.append(count++ == 0 ? '?' : '&')
                            .append(key.matches("[A-Za-z0-9_.-]{1,64}") ? key : "PARAM")
                            .append("=[REDACTED]");
                }
            }
            if (uri.getRawFragment() != null) {
                result.append("#[REDACTED]");
            }
            return result.toString();
        } catch (final RuntimeException ignored) {
            return "[INVALID_URL]";
        }
    }
}
