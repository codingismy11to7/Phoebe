package com.phoebe.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class MobileBottomTabTest {
    @Test
    fun fallsBackToDefaultWhenFewerThanTwoValidTabsRemain() {
        assertEquals(
            MobileBottomTab.defaultOrder,
            listOf(MobileBottomTab.Radio, MobileBottomTab.Radio).normalizedMobileBottomTabs(),
        )
    }

    @Test
    fun preservesCustomVisibleOrder() {
        assertEquals(
            listOf(
                MobileBottomTab.Radio,
                MobileBottomTab.Home,
            ),
            listOf(MobileBottomTab.Radio, MobileBottomTab.Home).normalizedMobileBottomTabs(),
        )
    }
}
