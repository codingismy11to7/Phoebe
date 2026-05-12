package com.phoebe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class SettingsCategory(
    val label: String,
    val subtitle: String,
    val icon: PhoebeIcon,
) {
    Account("Account", "Profile and plans", PhoebeIcon.Music),
    Playback("Playback", "Playback behavior", PhoebeIcon.Play),
    AudioQuality("Audio Quality", "Streaming and downloads", PhoebeIcon.Volume),
    Library("Library", "Organize your library", PhoebeIcon.Library),
    Downloads("Downloads", "Manage downloads", PhoebeIcon.Cast),
    Appearance("Appearance", "Theme and visuals", PhoebeIcon.Grid),
    Notifications("Notifications", "Manage alerts", PhoebeIcon.Bell),
    Advanced("Advanced", "Developer and advanced", PhoebeIcon.More),
}

@Composable
internal fun SettingsDesktopView(
    isLightMode: Boolean,
    onLightModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var category by remember { mutableStateOf(SettingsCategory.AudioQuality) }
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 36.dp, vertical = 28.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column(Modifier.width(232.dp)) {
                Text("Settings", color = PhoebeUi.primaryText, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text(
                    "Customize your listening experience",
                    color = PhoebeUi.secondaryText,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 22.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SettingsCategory.entries.forEach { cat ->
                        SettingsCategoryRow(
                            cat = cat,
                            selected = category == cat,
                            onClick = { category = cat },
                        )
                    }
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (category) {
                    SettingsCategory.Appearance -> AppearanceSettingsCard(isLightMode, onLightModeChange)
                    SettingsCategory.AudioQuality -> AudioQualityPlaceholderCard()
                    SettingsCategory.Account -> AccountPlaceholderCard()
                    SettingsCategory.Playback,
                    SettingsCategory.Library,
                    SettingsCategory.Downloads,
                    SettingsCategory.Notifications,
                    SettingsCategory.Advanced,
                    -> GenericPlaceholderCard(category.label)
                }
            }
        }
    }
}

@Composable
internal fun SettingsMobileView(
    isLightMode: Boolean,
    onLightModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SectionLabel("APPEARANCE", PhoebeUi.accentLight)
        AppearanceSettingsCard(isLightMode, onLightModeChange)
        SectionLabel("AUDIO QUALITY", PhoebeUi.accentLight)
        AudioQualityPlaceholderCard(compact = true)
        SectionLabel("PLAYBACK", PhoebeUi.accentLight)
        GenericPlaceholderCard("Playback", compact = true)
    }
}

@Composable
private fun SettingsCategoryRow(
    cat: SettingsCategory,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(
                if (selected) PhoebeUi.accent.copy(alpha = 0.10f) else androidx.compose.ui.graphics.Color.Transparent,
            )
            .border(
                BorderStroke(1.dp, if (selected) PhoebeUi.accent.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent),
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PhoebeIconView(
            cat.icon,
            tint = if (selected) PhoebeUi.accentLight else PhoebeUi.secondaryText,
            modifier = Modifier.size(18.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(cat.label, color = if (selected) PhoebeUi.accentLight else PhoebeUi.primaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(cat.subtitle, color = PhoebeUi.mutedText, fontSize = 11.sp, maxLines = 2)
        }
    }
}

@Composable
private fun AppearanceSettingsCard(
    isLightMode: Boolean,
    onLightModeChange: (Boolean) -> Unit,
) {
    SettingsCard {
        Text("Appearance", color = PhoebeUi.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("Theme and visuals", color = PhoebeUi.mutedText, fontSize = 12.sp, modifier = Modifier.padding(bottom = 14.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Light mode", color = PhoebeUi.primaryText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("Use the bright theme across the app", color = PhoebeUi.secondaryText, fontSize = 12.sp)
            }
            Switch(
                checked = isLightMode,
                onCheckedChange = onLightModeChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = PhoebeUi.accentLight,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = PhoebeUi.progressTrack,
                ),
            )
        }
    }
}

@Composable
private fun AudioQualityPlaceholderCard(compact: Boolean = false) {
    var crossfade by remember { mutableFloatStateOf(0.35f) }
    var streamingMenu by remember { mutableStateOf(false) }
    var downloadMenu by remember { mutableStateOf(false) }
    var streamingQuality by remember { mutableStateOf("Lossless") }
    var downloadQuality by remember { mutableStateOf("Lossless") }
    SettingsCard {
        Text("Audio Quality", color = PhoebeUi.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("Streaming and downloads", color = PhoebeUi.mutedText, fontSize = 12.sp, modifier = Modifier.padding(bottom = 14.dp))
        Text("Crossfade", color = PhoebeUi.secondaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Slider(
            value = crossfade,
            onValueChange = { crossfade = it },
            modifier = Modifier.padding(vertical = 4.dp),
            colors = SliderDefaults.colors(
                thumbColor = PhoebeUi.accentLight,
                activeTrackColor = PhoebeUi.accentLight,
                inactiveTrackColor = PhoebeUi.progressTrack,
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0s", color = PhoebeUi.mutedText, fontSize = 11.sp)
            Text("${(crossfade * 12).toInt()}s", color = PhoebeUi.accentLight, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text("12s", color = PhoebeUi.mutedText, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        PlaceholderToggleRow("Gapless Playback", true)
        PlaceholderToggleRow("Normalize Audio", true)
        PlaceholderToggleRow("Explicit Content", true)
        if (!compact) {
            PlaceholderToggleRow("Auto-download Favorites", true)
            PlaceholderToggleRow("Scan Library on Launch", true)
        }
        Spacer(Modifier.height(8.dp))
        Text("Streaming Quality", color = PhoebeUi.secondaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        BoxWithQualityMenu(
            value = streamingQuality,
            expanded = streamingMenu,
            onExpand = { streamingMenu = true },
            onDismiss = { streamingMenu = false },
            onSelect = { streamingQuality = it; streamingMenu = false },
        )
        Spacer(Modifier.height(10.dp))
        Text("Download Quality", color = PhoebeUi.secondaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        BoxWithQualityMenu(
            value = downloadQuality,
            expanded = downloadMenu,
            onExpand = { downloadMenu = true },
            onDismiss = { downloadMenu = false },
            onSelect = { downloadQuality = it; downloadMenu = false },
        )
        if (!compact) {
            Spacer(Modifier.height(10.dp))
            Text("Local Library Path", color = PhoebeUi.secondaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PhoebeUi.subtleFill)
                    .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "~/Music/Library",
                    color = PhoebeUi.mutedText,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                Text("…", color = PhoebeUi.accentLight, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BoxWithQualityMenu(
    value: String,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val options = listOf("Low", "Normal", "High", "Lossless")
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PhoebeUi.subtleFill)
                .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(10.dp))
                .clickable(onClick = onExpand)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, color = PhoebeUi.primaryText, fontSize = 13.sp)
            PhoebeIconView(PhoebeIcon.ChevronDown, tint = PhoebeUi.mutedText, modifier = Modifier.size(14.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt, color = PhoebeUi.primaryText) },
                    onClick = { onSelect(opt) },
                )
            }
        }
    }
}

@Composable
private fun PlaceholderToggleRow(label: String, on: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = PhoebeUi.primaryText, fontSize = 14.sp)
        Switch(
            checked = on,
            onCheckedChange = null,
            enabled = false,
            colors = SwitchDefaults.colors(
                disabledCheckedThumbColor = Color.White,
                disabledCheckedTrackColor = PhoebeUi.accentLight.copy(alpha = 0.55f),
            ),
        )
    }
}

@Composable
private fun AccountPlaceholderCard() {
    SettingsCard {
        Text("Account", color = PhoebeUi.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Profile and subscription controls will live here.", color = PhoebeUi.secondaryText, fontSize = 13.sp)
    }
}

@Composable
private fun GenericPlaceholderCard(title: String, compact: Boolean = false) {
    SettingsCard {
        Text(title, color = PhoebeUi.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
        Text("This section is not implemented yet.", color = PhoebeUi.secondaryText, fontSize = 13.sp)
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PhoebeUi.panel)
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(16.dp))
            .padding(20.dp),
        content = content,
    )
}
