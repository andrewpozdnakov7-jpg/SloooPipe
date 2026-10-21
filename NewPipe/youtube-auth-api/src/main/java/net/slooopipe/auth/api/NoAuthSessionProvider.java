// SPDX-License-Identifier: GPL-3.0-or-later
package net.slooopipe.auth.api;

public final class NoAuthSessionProvider implements YoutubeSessionProvider {
    @Override public State state() { return State.UNAVAILABLE; }
    @Override public long generation() { return 0; }
    @Override public AuthorizedPlayerRequest preparePlayerRequest(final String videoId) {
        throw new IllegalStateException("YouTube authorization is not included in this build");
    }
    @Override public boolean markVerified(final long generation) { return false; }
    @Override public void requireReauthentication(final long generation) { }
    @Override public void logout() { }
    @Override public void addListener(final Runnable listener) { }
}
