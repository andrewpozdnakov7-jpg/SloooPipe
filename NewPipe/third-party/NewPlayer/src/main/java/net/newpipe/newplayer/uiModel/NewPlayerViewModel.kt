package net.newpipe.newplayer.uiModel

import android.app.Activity
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import net.newpipe.newplayer.NewPlayer
import net.newpipe.newplayer.ui.NewPlayerUI
import net.newpipe.newplayer.ui.ContentScale
import net.newpipe.newplayer.data.PlayMode.IDLE


/**
 * The NewPlayerViewModel must live and be owned by an Activity context.
 */
@OptIn(UnstableApi::class)
interface NewPlayerViewModel {

    /**
     * The current instance of [NewPlayer]. If set to null the [NewPlayerUI] instance
     * which the viewModel talks too will display the same as if [NewPlayer] was in [IDLE] mode.
     *
     * You can always add or remove the NewPlayer instance.
     */
    var newPlayer: NewPlayer?

    /**
     * Depicts weather the picture of the video should be stretched, fit inside or be cropped.
     * You can set it yourself, but [NewPlayerUI] can also change it.
     *
     * You should store this value in the settings database when the ViewModel gets destroyed.
     * You should then reload this value if the ViewModel gets recreated.
     * This way you ensure that whatever the user desires to see it stays persistent upon
     * app restarts.
     */
    var contentFitMode: ContentScale

    /**
     * This represents the state the UI is in. [NewPlayerUI] will basically render out that state.
     * You can make your UI also listen to changes to this state. This is especially helpful
     * for switching to or from fullscreen or to or from PiP mode.
     */
    val uiState: StateFlow<NewPlayerUIState>


    /**
     * This will send single events to the [NewPlayerUI] and the underlying Activity,
     * which will not lead to a UIState change.
     * This is used by the ViewModel if it wants this to happen like:
     * - Show a Snackbar
     * - Show a Toast
     * - Open a Share/Open Menu
     * - Signal a singular event to the activity which is not part of NewPlayer.
     */
    val uiEvent: SharedFlow<NewPlayerUIEvent>

    /**
     * If this is set the audio player will display the playlist instead of the thumbnail of the
     * current stream.
     *
     * You should store this value in the settings database when the ViewModel gets destroyed.
     * You should then reload this value if the ViewModel gets recreated.
     * This way you ensure that whatever the user desires to see it stays persistent upon
     * app restarts.
     */
    var showPlaylistInAudioPlayer: Boolean

    /**
     * This is something you have to call in the Activity that should host the [NewPlayerUI].
     * See the [example app's](https://github.com/TeamNewPipe/NewPlayer/blob/master/test-app/src/main/java/net/newpipe/newplayer/testapp/MainActivity.kt) main activity to find out how to use this.
     *
     * Long story short your activity should handle the `onPictureInPictureModeChanged` event.
     * You can do this by adding this code to your Activity's `onCreate()` function:
     * ```
     *  addOnPictureInPictureModeChangedListener { mode ->
     *      newPlayerViewModel.onPictureInPictureModeChanged(mode.isInPictureInPictureMode)
     *  }
     * ```
     */
    fun onPictureInPictureModeChanged(isPictureInPictureMode: Boolean)
}