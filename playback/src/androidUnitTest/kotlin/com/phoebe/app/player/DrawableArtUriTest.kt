package com.phoebe.app.player

import android.app.Application
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Browse-item artwork URIs must use the resource *name* form.
 *
 * Android Automotive OS resolves `android.resource://` URIs by name only: its
 * `UriUtils.getIconResource` rebuilds a resource name as
 * `authority + path.replaceFirst("/", ":")` and passes it to
 * `Resources.getIdentifier`, which returns 0 for a numeric path. AAOS then
 * calls `getDrawable(0)`, throws `Resources$NotFoundException: Resource ID
 * #0x0`, and the uncaught exception kills the whole `com.android.car.media`
 * process. Android Auto resolves both forms, so this regression is invisible
 * on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DrawableArtUriTest {
    @Test
    fun usesResourceNameForm() {
        assertEquals(
            "android.resource://android/drawable/ic_menu_gallery",
            drawableArtUri(android.R.drawable.ic_menu_gallery).toString(),
        )
    }

    @Test
    fun neverEmitsNumericResourceId() {
        val browseFolderIcons = listOf(
            android.R.drawable.ic_menu_gallery,
            android.R.drawable.ic_menu_myplaces,
            android.R.drawable.ic_menu_agenda,
        )

        browseFolderIcons.forEach { drawableRes ->
            val lastSegment = drawableArtUri(drawableRes).lastPathSegment
            assertNull(
                lastSegment?.toIntOrNull(),
                "artwork URI must not end in a numeric resource id, got $lastSegment",
            )
        }
    }
}
