// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.extractor.services.youtube;

import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/** Optional host-supplied MWEB backend. No account storage or UI in Extractor. */
public interface AuthenticatedPlayerProvider {
    Response fetch(String videoId) throws IOException, ExtractionException;

    final class Response {
        public final JsonObject player;
        public final String cpn;
        public final String streamingPoToken;
        public final BooleanSupplier isCurrent;
        public final BiConsumer<Integer, Throwable> onFormatFailure;

        public Response(final JsonObject player, final String cpn,
                        final String streamingPoToken, final BooleanSupplier isCurrent,
                        final BiConsumer<Integer, Throwable> onFormatFailure) {
            this.player = player;
            this.cpn = cpn;
            this.streamingPoToken = streamingPoToken;
            this.isCurrent = isCurrent;
            this.onFormatFailure = onFormatFailure;
        }

        @Override
        public String toString() {
            return "AuthenticatedPlayerResponse[REDACTED]";
        }
    }
}
