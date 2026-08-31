package com.indirgitsin.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.indirgitsin.app.data.downloader.MediaArtwork
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PlayerInstrumentedTest {
    @get:Rule(order = 0) val timeout = org.junit.rules.Timeout.seconds(60)
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun audioUsesOfflineCoverAndOldFilesUseMusicPlaceholder() {
        val file = File(context.cacheDir, "art-player-${UUID.randomUUID()}.wav")
        writeAudio(file)
        val uri = Uri.fromFile(file)
        val bitmap = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(101, 61, 187))
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        bitmap.recycle()
        try {
            MediaArtwork.save(context, uri, MediaArtwork.Entry("Çevrimdışı kapak", "İndir Gitsin", bytes))
            assertArrayEquals(bytes, MediaArtwork.read(context, uri)?.jpeg)
            launch(file).use {
                waitFor("audio_artwork")
                compose.onNodeWithTag("audio_artwork").assertIsDisplayed()
                compose.onNodeWithTag("fullscreen_toggle").assertDoesNotExist()
                screenshot("audio-cover")
            }
            MediaArtwork.remove(context, uri)
            assertNull(MediaArtwork.read(context, uri))
            launch(file).use {
                waitFor("audio_fallback")
                compose.onNodeWithTag("audio_fallback").assertIsDisplayed()
                screenshot("audio-placeholder")
            }
        } finally { MediaArtwork.remove(context, uri); file.delete() }
    }

    @Test fun videoFullscreenAndRecreationKeepSeekPositionAndBackRestoresBars() {
        val file = File(context.cacheDir, "video-player-${UUID.randomUUID()}.mp4")
        val source = File(context.cacheDir, "${file.name}.source")
        try {
            instrumentation.context.assets.open("live/capture-fmp4.mp4").use { input -> source.outputStream().use { input.copyTo(it) } }
            // Exercise the seekable MP4 actually saved by the app, not the unfinalized HLS input.
            kotlinx.coroutines.runBlocking { com.indirgitsin.app.data.downloader.MediaFileMuxer.remuxCapture(source, file) }
        } finally { source.delete() }
        try {
            launch(file).use { scenario ->
                waitFor("play_pause")
                compose.onNodeWithTag("play_pause").performClick()
                compose.onNodeWithTag("player_seek").performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(.5f)) }
                screenshot("video-seek")
                // A paused midpoint remains unchanged through both rotation and Activity recreation.
                compose.onNodeWithText("00:04").assertIsDisplayed()
                compose.onNodeWithTag("fullscreen_toggle").performClick()
                compose.waitUntil(10_000) {
                    var hidden = false
                    scenario.onActivity {
                        hidden = it.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE &&
                            ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.statusBars()) == false
                    }
                    hidden
                }
                compose.onNodeWithText("00:04").assertIsDisplayed()
                screenshot("video-fullscreen")
                scenario.recreate()
                waitFor("fullscreen_toggle")
                compose.onNodeWithText("00:04").assertIsDisplayed()
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                compose.waitUntil(10_000) {
                    var restored = false
                    scenario.onActivity {
                        restored = it.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED &&
                            ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.statusBars()) == true
                    }
                    restored
                }
                compose.onNodeWithTag("player").assertIsDisplayed()
                compose.onNodeWithText("00:04").assertIsDisplayed()
                screenshot("video-normal")
            }
        } finally { file.delete() }
    }

    private fun launch(file: File): ActivityScenario<PlayerActivity> = ActivityScenario.launch(
        Intent(context, PlayerActivity::class.java).setData(Uri.fromFile(file)).putExtra("title", file.name))

    @Test fun missingFileShowsAnActionablePlaybackError() {
        launch(File(context.cacheDir, "missing-${UUID.randomUUID()}.mp4")).use {
            waitFor("player_error")
            compose.onNodeWithTag("player_error").assertIsDisplayed()
        }
    }

    private fun waitFor(tag: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            val folder = File(context.getExternalFilesDir(null), "player-evidence").apply { mkdirs() }
            File(folder, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { image.recycle() }
    }

    private fun writeAudio(file: File) {
        val pcm = ByteArray(44100 * 2 * 8) // Eight seconds of silent mono PCM; no network fixtures.
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVEfmt ".toByteArray()).putInt(16)
        header.putShort(1).putShort(1).putInt(44100).putInt(88200).putShort(2).putShort(16)
        header.put("data".toByteArray()).putInt(pcm.size)
        file.outputStream().use { it.write(header.array()); it.write(pcm) }
    }
}
