package com.phoebe.app

import com.phoebe.app.data.RadioBrowserClickDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class RadioBrowserClientTest {
    @Test
    fun clickResponseAcceptsBooleanOk() {
        val response = Json.decodeFromString<RadioBrowserClickDto>(
            """
            {
              "ok": true,
              "message": "retrieved station url",
              "stationuuid": "360bb528-cea3-4e8e-84c6-3970c55bda71",
              "name": "Funk the Planet",
              "url": "https://streaming.live365.com/a01484"
            }
            """.trimIndent(),
        )

        assertTrue(response.ok)
        assertEquals("https://streaming.live365.com/a01484", response.url)
    }
}
