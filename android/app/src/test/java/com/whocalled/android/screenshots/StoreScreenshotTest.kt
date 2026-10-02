package com.whocalled.android.screenshots

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.whocalled.android.network.StatsResponse
import com.whocalled.android.ui.AppBottomBar
import com.whocalled.android.ui.screen.HomeContent
import com.whocalled.android.ui.screen.HomeUiState
import com.whocalled.android.ui.theme.WhoCalledTheme
import com.whocalled.android.util.CallEvent
import com.whocalled.android.util.CallEventAction
import com.whocalled.android.util.RecentCallPrompt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Store screenshots, generated from the real Compose screens with fixed fixtures.
 *
 *   ./gradlew :app:testDebugUnitTest --tests "*StoreScreenshotTest*"
 *
 * Output: store/screenshots/generated/<locale>/<nn>-<screen>.png (1080×2400,
 * Pixel 6 metrics, dark theme — same as the hand-taken captures). Add a locale
 * by adding a values-<lang>/strings.xml and a test method below.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = ScreenshotApp::class)
class StoreScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    // ---- fr-FR -------------------------------------------------------------

    @Test
    @Config(qualifiers = FR)
    fun home_recentCall_fr() = shoot("fr", "01-home-recent-call") { HomeShot(Fixtures.homeRecentCall()) }

    @Test
    @Config(qualifiers = FR)
    fun home_quiet_fr() = shoot("fr", "02-home-quiet") { HomeShot(Fixtures.homeQuiet()) }

    // ---- en-US -------------------------------------------------------------

    @Test
    @Config(qualifiers = EN)
    fun home_recentCall_en() = shoot("en", "01-home-recent-call") { HomeShot(Fixtures.homeRecentCall()) }

    @Test
    @Config(qualifiers = EN)
    fun home_quiet_en() = shoot("en", "02-home-quiet") { HomeShot(Fixtures.homeQuiet()) }

    // ---- plumbing ------------------------------------------------------------

    private fun shoot(locale: String, name: String, content: @Composable () -> Unit) {
        compose.setContent { WhoCalledTheme(darkTheme = true) { content() } }
        compose.waitForIdle()
        val dir = File(System.getProperty("whocalled.screenshots.dir")!!, locale).apply { mkdirs() }
        compose.onRoot().captureRoboImage(File(dir, "$name.png"))
    }

    companion object {
        // Pixel 6 / whocalled_pixel AVD: 1080×2400 @ 420dpi, dark theme.
        // Qualifier order matters for Robolectric: locale first, night before density.
        private const val DEVICE = "w411dp-h914dp-normal-long-notround-any-night-420dpi-keyshidden-nonav"
        const val FR = "fr-rFR-$DEVICE"
        const val EN = "en-rUS-$DEVICE"
    }
}

@Composable
private fun HomeShot(state: HomeUiState) {
    Scaffold(bottomBar = { AppBottomBar(activeRoute = "home", onSelect = {}) }) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding)) { HomeContent(state) }
    }
}

/** Deterministic fixture data. Phone numbers use the ARCEP range reserved for fiction (06 39 98 xx xx). */
object Fixtures {
    /** Frozen clock: 2026-09-10 10:25 UTC (the day the reference captures were taken). */
    const val NOW = 1_789_035_900_000L
    private const val HOUR = 3_600_000L

    private fun iso(t: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(t))

    val stats = StatsResponse(
        serverTime = iso(NOW),
        coveredNumbers = 28_873_272,
        communityCount = 1_204_331,
        arcepPatternCount = 1_541,
        lastUpdate = iso(NOW - 2 * HOUR),
        country = "33",
        countryNumbers = 598_409,
        countryArcepPatternCount = 1_541,
    )

    fun homeRecentCall() = HomeUiState(
        isScreeningRoleHeld = true,
        stats = stats,
        recentCall = RecentCallPrompt(
            call = CallEvent(
                key = "fixture-1",
                callLogId = 1L,
                phone = "33639980412",
                action = CallEventAction.UNKNOWN,
                spamScore = 0,
                category = "unknown",
                timestamp = NOW - 17 * HOUR,
            ),
            others = 1,
        ),
        showCountryBanner = true,
        countryDial = "33",
        now = NOW,
    )

    fun homeQuiet() = homeRecentCall().copy(recentCall = null)
}
