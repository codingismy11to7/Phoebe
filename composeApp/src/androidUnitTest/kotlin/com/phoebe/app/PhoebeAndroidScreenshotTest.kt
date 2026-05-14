package com.phoebe.app

import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.size
import com.phoebe.app.ui.PhoebeScreenshotApp
import com.phoebe.app.ui.PhoebeScreenshotScenario
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = ScreenshotTestApplication::class)
class PhoebeAndroidPhoneScreenshotTest {
    @Test fun phoneHomeDark() = capturePhone("home", PhoebeScreenshotScenario.Home)
    @Test fun phoneLibraryDark() = capturePhone("library", PhoebeScreenshotScenario.Library)
    @Test fun phonePlaylistDark() = capturePhone("playlist", PhoebeScreenshotScenario.Playlist)
    @Test fun phoneArtistDark() = capturePhone("artist", PhoebeScreenshotScenario.Artist)
    @Test fun phoneAlbumDark() = capturePhone("album", PhoebeScreenshotScenario.Album)
    @Test fun phoneSearchDark() = capturePhone("search", PhoebeScreenshotScenario.Search)
    @Test fun phonePlayerDark() = capturePhone("player", PhoebeScreenshotScenario.Player)
    @Test fun phonePlayerUpNextExpandedDark() = capturePhone("player-upnext-expanded", PhoebeScreenshotScenario.PlayerUpNextExpanded)
    @Test fun phoneSettingsDark() = capturePhone("settings", PhoebeScreenshotScenario.Settings)
    @Test fun phoneSignInDark() = capturePhone("signin", PhoebeScreenshotScenario.SignIn)

    @Test fun phoneLibraryLight() = capturePhone("library", PhoebeScreenshotScenario.Library, useLightAppearance = true)
    @Test fun phoneSearchLight() = capturePhone("search", PhoebeScreenshotScenario.Search, useLightAppearance = true)
    @Test fun phonePlayerLight() = capturePhone("player", PhoebeScreenshotScenario.Player, useLightAppearance = true)
    @Test fun phonePlayerUpNextExpandedLight() = capturePhone("player-upnext-expanded", PhoebeScreenshotScenario.PlayerUpNextExpanded, useLightAppearance = true)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = ScreenshotTestApplication::class)
class PhoebeAndroidTabletScreenshotTest {
    @Test fun tabletHomeDark() = captureTablet("home", PhoebeScreenshotScenario.Home)
    @Test fun tabletLibraryDark() = captureTablet("library", PhoebeScreenshotScenario.Library)
    @Test fun tabletPlaylistDark() = captureTablet("playlist", PhoebeScreenshotScenario.Playlist)
    @Test fun tabletArtistDark() = captureTablet("artist", PhoebeScreenshotScenario.Artist)
    @Test fun tabletSearchDark() = captureTablet("search", PhoebeScreenshotScenario.Search)
    @Test fun tabletPlayerDark() = captureTablet("player", PhoebeScreenshotScenario.Player)
}

private fun capturePhone(
    slug: String,
    scenario: PhoebeScreenshotScenario,
    useLightAppearance: Boolean = false,
) = capture(
    name = "android-phone-$slug-${if (useLightAppearance) "light" else "dark"}",
    scenario = scenario,
    widthDp = 430,
    heightDp = 932,
    useLightAppearance = useLightAppearance,
)

private fun captureTablet(
    slug: String,
    scenario: PhoebeScreenshotScenario,
) = capture(
    name = "android-tablet-$slug-dark",
    scenario = scenario,
    widthDp = 1180,
    heightDp = 820,
)

@OptIn(ExperimentalRoborazziApi::class)
private fun capture(
    name: String,
    scenario: PhoebeScreenshotScenario,
    widthDp: Int,
    heightDp: Int,
    useLightAppearance: Boolean = false,
) {
    captureRoboImage(
        filePath = "src/screenshotTest/roborazzi/$name.png",
        roborazziComposeOptions = RoborazziComposeOptions {
            size(widthDp = widthDp, heightDp = heightDp)
        },
    ) {
        PhoebeScreenshotApp(
            scenario = scenario,
            useLightAppearance = useLightAppearance,
            modifier = Modifier,
        )
    }
}
