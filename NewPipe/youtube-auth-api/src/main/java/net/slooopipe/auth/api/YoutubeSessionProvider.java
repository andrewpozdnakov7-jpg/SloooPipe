// SPDX-License-Identifier: GPL-3.0-or-later
package net.slooopipe.auth.api;

/** UI observes State only. Request credentials are for the restricted backend adapter. */
public interface YoutubeSessionProvider {
    enum State { UNAVAILABLE, ANONYMOUS, CAPTURED, VERIFIED, REAUTH_REQUIRED }
    State state();
    long generation();
    AuthorizedPlayerRequest preparePlayerRequest(String videoId);
    boolean markVerified(long expectedGeneration);
    void requireReauthentication(long expectedGeneration);
    void logout();
    void addListener(Runnable listener);
}
