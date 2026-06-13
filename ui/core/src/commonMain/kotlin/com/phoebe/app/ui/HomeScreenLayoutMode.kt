package com.phoebe.app.ui

enum class HomeScreenLayoutMode(
    val label: String,
    val storageValue: String,
) {
    Compact("Compact", "compact"),
    Expanded("Expanded", "expanded");

    companion object {
        val Default = Compact

        fun fromStorage(value: String?): HomeScreenLayoutMode =
            entries.firstOrNull { mode ->
                mode.storageValue.equals(value, ignoreCase = true) ||
                    mode.name.equals(value, ignoreCase = true)
            } ?: Default
    }
}
