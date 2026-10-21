package net.newpipe.newplayer.uiModel

import android.net.Uri
import net.newpipe.newplayer.ui.NewPlayerUI

/**
 * This is used by the ViewModel to signal single events that don't alter the UIState.
 * This is useful for actions like poping up the share menu or showing a toast.
 */
sealed class NewPlayerUIEvent {

    /**
     * Relative value how much the embedded player was dragged down since the last
     * DragDownBy() event was sent.
     */
    data class DragDownBy(val value: Float) : NewPlayerUIEvent()

    /**
     * Signals to the activity that a back was pressed that NewPlayer itself should not handle.
     */
    class OnBackPressed : NewPlayerUIEvent()


    /** @hide
     * Tells the UI that it should open the link in the browser.
     */
    internal data class OpenInBrowser(val link: Uri) : NewPlayerUIEvent()

    /** @hide
     * Tells the UI that this link should be shared by opening the share menu.
     */
    internal data class ShareThisLink(val link: Uri) : NewPlayerUIEvent()
}