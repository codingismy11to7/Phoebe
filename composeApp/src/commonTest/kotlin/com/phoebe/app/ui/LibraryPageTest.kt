package com.phoebe.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryPageTest {
    @Test
    fun disabledPaginationReturnsEveryItem() {
        val items = (1..250).toList()

        val page = libraryPage(items, enabled = false, pageIndex = 3)

        assertEquals(items, page.items)
        assertEquals(1, page.pageCount)
        assertEquals(250, page.totalCount)
    }

    @Test
    fun enabledPaginationReturnsRequestedWindow() {
        val items = (1..250).toList()

        val page = libraryPage(items, enabled = true, pageIndex = 1)

        assertEquals((101..200).toList(), page.items)
        assertEquals(3, page.pageCount)
        assertEquals(250, page.totalCount)
    }
}
