package com.phoebe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.phoebe.app.domain.AppSettings
import com.phoebe.app.domain.HomeSection
import com.phoebe.app.domain.ListenBrainzCredentialStorageStatus
import com.phoebe.app.domain.LibraryUiPreferences
import com.phoebe.app.domain.PersonalMixPreferences
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.domain.providerLabel
import com.phoebe.app.platform.SecureCredentialAvailability
import com.phoebe.app.platform.openExternalUrl
import com.phoebe.app.platform.rememberPickDownloadDirectory
import kotlin.math.roundToInt

internal enum class SettingsCategory(
    val label: String,
    val subtitle: String,
    val icon: PhoebeIcon,
) {
    Account("Account", "Profile and plans", PhoebeIcon.Music),
    Personalization("Personalization", "Mixes and recommendations", PhoebeIcon.Person),
    AudioPlayback("Audio Playback", "Transitions and EQ", PhoebeIcon.Equalizer),
    Library("Library", "Organize your library", PhoebeIcon.Library),
    Downloads("Downloads", "Manage downloads", PhoebeIcon.Download),
    Appearance("Appearance", "Theme and visuals", PhoebeIcon.Grid),
    Notifications("Notifications", "Manage alerts", PhoebeIcon.Bell),
    Advanced("Advanced", "Developer and advanced", PhoebeIcon.More),
}

@Composable
internal fun SettingsDesktopView(
    isLightMode: Boolean,
    onLightModeChange: (Boolean) -> Unit,
    tintId: String,
    onTintChange: (String) -> Unit,
    downloadDirectory: String?,
    downloadCount: Int,
    appSettings: AppSettings,
    libraryUi: LibraryUiPreferences,
    defaultDownloadDirectoryLabel: String,
    onDownloadDirectory: (String?) -> Unit,
    onDeleteAllDownloads: () -> Unit,
    onCrossfadeSeconds: (Int) -> Unit,
    onScanLibraryOnLaunch: (Boolean) -> Unit,
    onNotifyWhenDownloadFinishes: (Boolean) -> Unit,
    onPersistEqualizerSettings: (Boolean) -> Unit = {},
    onHomeSections: (List<HomeSection>) -> Unit,
    onPersonalMix: (PersonalMixPreferences) -> Unit,
    onGridColumns: (Int) -> Unit,
    onExportFavoritePlaylists: () -> Unit,
    onImportFavoritePlaylists: () -> Unit,
    homeScreenLayoutMode: HomeScreenLayoutMode = HomeScreenLayoutMode.Default,
    onHomeScreenLayoutModeChange: (HomeScreenLayoutMode) -> Unit = {},
    session: PlexSession? = null,
    listenBrainzCredentialAvailability: SecureCredentialAvailability = SecureCredentialAvailability.Unavailable,
    onConnectListenBrainz: (String) -> Unit = {},
    onDisconnectListenBrainz: () -> Unit = {},
    onListenBrainzSubmitNowPlaying: (Boolean) -> Unit = {},
    onListenBrainzSubmitListens: (Boolean) -> Unit = {},
    onListenBrainzSubmitCurrentTrackFeedback: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    initialCategory: SettingsCategory = SettingsCategory.AudioPlayback,
) {
    var category by remember(initialCategory) { mutableStateOf(initialCategory) }
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
                    SettingsCategory.Appearance -> AppearanceSettingsCard(
                        isLightMode,
                        onLightModeChange,
                        tintId,
                        onTintChange,
                        homeScreenLayoutMode,
                        onHomeScreenLayoutModeChange,
                    )
                    SettingsCategory.AudioPlayback -> AudioPlaybackSettingsCard(
                        settings = appSettings,
                        onCrossfadeSeconds = onCrossfadeSeconds,
                        onScanLibraryOnLaunch = onScanLibraryOnLaunch,
                        onPersistEqualizerSettings = onPersistEqualizerSettings,
                    )
                    SettingsCategory.Account -> AccountSettingsCard(
                        session = session,
                        appSettings = appSettings,
                        listenBrainzCredentialAvailability = listenBrainzCredentialAvailability,
                        onConnectListenBrainz = onConnectListenBrainz,
                        onDisconnectListenBrainz = onDisconnectListenBrainz,
                        onListenBrainzSubmitNowPlaying = onListenBrainzSubmitNowPlaying,
                        onListenBrainzSubmitListens = onListenBrainzSubmitListens,
                        onListenBrainzSubmitCurrentTrackFeedback = onListenBrainzSubmitCurrentTrackFeedback,
                    )
                    SettingsCategory.Library -> {
                        GridSettingsCard(libraryUi.gridColumns, onGridColumns)
                        HomeSettingsCard(libraryUi.homeSections, onHomeSections)
                        FavoritePlaylistSettingsCard(onExportFavoritePlaylists, onImportFavoritePlaylists)
                    }
                    SettingsCategory.Downloads -> DownloadsSettingsCard(
                        downloadDirectory = downloadDirectory,
                        downloadCount = downloadCount,
                        defaultDownloadDirectoryLabel = defaultDownloadDirectoryLabel,
                        onDownloadDirectory = onDownloadDirectory,
                        onDeleteAllDownloads = onDeleteAllDownloads,
                    )
                    SettingsCategory.Personalization -> PersonalMixSettingsCard(libraryUi.personalMix, onPersonalMix)
                    SettingsCategory.Notifications -> NotificationsSettingsCard(
                        settings = appSettings,
                        onNotifyWhenDownloadFinishes = onNotifyWhenDownloadFinishes,
                    )
                    SettingsCategory.Advanced -> GenericPlaceholderCard(category.label)
                }
            }
        }
    }
}

@Composable
internal fun SettingsMobileView(
    isLightMode: Boolean,
    onLightModeChange: (Boolean) -> Unit,
    tintId: String,
    onTintChange: (String) -> Unit,
    downloadDirectory: String?,
    downloadCount: Int,
    appSettings: AppSettings,
    libraryUi: LibraryUiPreferences,
    defaultDownloadDirectoryLabel: String,
    onDownloadDirectory: (String?) -> Unit,
    onDeleteAllDownloads: () -> Unit,
    onCrossfadeSeconds: (Int) -> Unit,
    onScanLibraryOnLaunch: (Boolean) -> Unit,
    onNotifyWhenDownloadFinishes: (Boolean) -> Unit,
    onPersistEqualizerSettings: (Boolean) -> Unit = {},
    onHomeSections: (List<HomeSection>) -> Unit,
    onPersonalMix: (PersonalMixPreferences) -> Unit,
    onGridColumns: (Int) -> Unit,
    onExportFavoritePlaylists: () -> Unit,
    onImportFavoritePlaylists: () -> Unit,
    homeScreenLayoutMode: HomeScreenLayoutMode = HomeScreenLayoutMode.Default,
    onHomeScreenLayoutModeChange: (HomeScreenLayoutMode) -> Unit = {},
    session: PlexSession? = null,
    listenBrainzCredentialAvailability: SecureCredentialAvailability = SecureCredentialAvailability.Unavailable,
    onConnectListenBrainz: (String) -> Unit = {},
    onDisconnectListenBrainz: () -> Unit = {},
    onListenBrainzSubmitNowPlaying: (Boolean) -> Unit = {},
    onListenBrainzSubmitListens: (Boolean) -> Unit = {},
    onListenBrainzSubmitCurrentTrackFeedback: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SectionLabel("ACCOUNT", PhoebeUi.accentLight)
        AccountSettingsCard(
            session = session,
            appSettings = appSettings,
            listenBrainzCredentialAvailability = listenBrainzCredentialAvailability,
            onConnectListenBrainz = onConnectListenBrainz,
            onDisconnectListenBrainz = onDisconnectListenBrainz,
            onListenBrainzSubmitNowPlaying = onListenBrainzSubmitNowPlaying,
            onListenBrainzSubmitListens = onListenBrainzSubmitListens,
            onListenBrainzSubmitCurrentTrackFeedback = onListenBrainzSubmitCurrentTrackFeedback,
            compact = true,
        )
        SectionLabel("APPEARANCE", PhoebeUi.accentLight)
        AppearanceSettingsCard(
            isLightMode,
            onLightModeChange,
            tintId,
            onTintChange,
            homeScreenLayoutMode,
            onHomeScreenLayoutModeChange,
        )
        SectionLabel("LIBRARY", PhoebeUi.accentLight)
        GridSettingsCard(libraryUi.gridColumns, onGridColumns, compact = true)
        HomeSettingsCard(libraryUi.homeSections, onHomeSections, compact = true)
        FavoritePlaylistSettingsCard(onExportFavoritePlaylists, onImportFavoritePlaylists, compact = true)
        SectionLabel("AUDIO PLAYBACK", PhoebeUi.accentLight)
        AudioPlaybackSettingsCard(
            settings = appSettings,
            onCrossfadeSeconds = onCrossfadeSeconds,
            onScanLibraryOnLaunch = onScanLibraryOnLaunch,
            onPersistEqualizerSettings = onPersistEqualizerSettings,
            compact = true,
        )
        SectionLabel("DOWNLOADS", PhoebeUi.accentLight)
        DownloadsSettingsCard(
            downloadDirectory = downloadDirectory,
            downloadCount = downloadCount,
            defaultDownloadDirectoryLabel = defaultDownloadDirectoryLabel,
            onDownloadDirectory = onDownloadDirectory,
            onDeleteAllDownloads = onDeleteAllDownloads,
            compact = true,
        )
        SectionLabel("PERSONALIZATION", PhoebeUi.accentLight)
        PersonalMixSettingsCard(libraryUi.personalMix, onPersonalMix, compact = true)
        SectionLabel("NOTIFICATIONS", PhoebeUi.accentLight)
        NotificationsSettingsCard(
            settings = appSettings,
            onNotifyWhenDownloadFinishes = onNotifyWhenDownloadFinishes,
        )
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
    tintId: String,
    onTintChange: (String) -> Unit,
    homeScreenLayoutMode: HomeScreenLayoutMode,
    onHomeScreenLayoutModeChange: (HomeScreenLayoutMode) -> Unit,
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
        Spacer(Modifier.height(18.dp))
        Text("Mobile Home layout", color = PhoebeUi.primaryText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        HomeLayoutModeControl(
            selected = homeScreenLayoutMode,
            onSelected = onHomeScreenLayoutModeChange,
        )
        Spacer(Modifier.height(18.dp))
        Text("Tint", color = PhoebeUi.primaryText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text("Choose the accent color for controls and active states", color = PhoebeUi.secondaryText, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PhoebeTintOption.Options.chunked(5).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { option ->
                        TintSwatch(
                            option = option,
                            selected = option.id == tintId,
                            onClick = { onTintChange(option.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeLayoutModeControl(
    selected: HomeScreenLayoutMode,
    onSelected: (HomeScreenLayoutMode) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(PhoebeUi.subtleFill)
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(10.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HomeScreenLayoutMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Row(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelected(mode) }
                    .background(if (isSelected) PhoebeUi.accent.copy(alpha = 0.16f) else Color.Transparent)
                    .border(
                        BorderStroke(
                            1.dp,
                            if (isSelected) PhoebeUi.accent.copy(alpha = 0.32f) else Color.Transparent,
                        ),
                        RoundedCornerShape(8.dp),
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    mode.label,
                    color = if (isSelected) PhoebeUi.accentLight else PhoebeUi.secondaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TintSwatch(
    option: PhoebeTintOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        Modifier
            .size(34.dp)
            .clip(shape)
            .clickable(onClick = onClick)
            .background(option.color.copy(alpha = 0.18f))
            .border(
                BorderStroke(1.dp, if (selected) PhoebeUi.primaryText else PhoebeUi.border),
                shape,
            )
            .padding(5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(option.color),
        )
        if (selected) {
            PhoebeIconView(PhoebeIcon.Check, tint = Color.White, modifier = Modifier.size(13.dp))
        }
    }
}

@Composable
private fun AudioPlaybackSettingsCard(
    settings: AppSettings,
    onCrossfadeSeconds: (Int) -> Unit,
    onScanLibraryOnLaunch: (Boolean) -> Unit,
    onPersistEqualizerSettings: (Boolean) -> Unit,
    compact: Boolean = false,
) {
    var localCrossfade by remember(settings.crossfadeSeconds) { mutableIntStateOf(settings.crossfadeSeconds) }
    SettingsCard {
        Text("Audio Playback", color = PhoebeUi.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("Transitions and library scan", color = PhoebeUi.mutedText, fontSize = 12.sp, modifier = Modifier.padding(bottom = 14.dp))
        Text("Crossfade", color = PhoebeUi.secondaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Slider(
            value = localCrossfade.toFloat(),
            onValueChange = {
                val seconds = it.roundToInt().coerceIn(AppSettings.MinCrossfadeSeconds, AppSettings.MaxCrossfadeSeconds)
                localCrossfade = seconds
                if (seconds != settings.crossfadeSeconds) {
                    onCrossfadeSeconds(seconds)
                }
            },
            onValueChangeFinished = { onCrossfadeSeconds(localCrossfade) },
            valueRange = AppSettings.MinCrossfadeSeconds.toFloat()..AppSettings.MaxCrossfadeSeconds.toFloat(),
            steps = AppSettings.MaxCrossfadeSeconds - AppSettings.MinCrossfadeSeconds - 1,
            modifier = Modifier.padding(vertical = 4.dp),
            colors = SliderDefaults.colors(
                thumbColor = PhoebeUi.accentLight,
                activeTrackColor = PhoebeUi.accentLight,
                inactiveTrackColor = PhoebeUi.progressTrack,
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0s", color = PhoebeUi.mutedText, fontSize = 11.sp)
            Text("${localCrossfade}s", color = PhoebeUi.accentLight, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text("12s", color = PhoebeUi.mutedText, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        SettingsSwitchRow(
            title = "Persist equalizer",
            subtitle = "Apply the current EQ profile after app restart",
            checked = settings.persistEqualizerSettings,
            onCheckedChange = onPersistEqualizerSettings,
        )
        SettingsSwitchRow(
            title = "Scan library on launch",
            subtitle = "Refresh local folders when Phoebe starts",
            checked = settings.scanLibraryOnLaunch,
            onCheckedChange = onScanLibraryOnLaunch,
        )
    }
}

@Composable
private fun NotificationsSettingsCard(
    settings: AppSettings,
    onNotifyWhenDownloadFinishes: (Boolean) -> Unit,
) {
    SettingsCard {
        Text("Notifications", color = PhoebeUi.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("Download alerts", color = PhoebeUi.mutedText, fontSize = 12.sp, modifier = Modifier.padding(bottom = 14.dp))
        SettingsSwitchRow(
            title = "Download finished",
            subtitle = "Be notified when something finishes downloading",
            checked = settings.notifyWhenDownloadFinishes,
            onCheckedChange = onNotifyWhenDownloadFinishes,
        )
    }
}

@Composable
private fun DownloadsSettingsCard(
    downloadDirectory: String?,
    downloadCount: Int,
    defaultDownloadDirectoryLabel: String,
    onDownloadDirectory: (String?) -> Unit,
    onDeleteAllDownloads: () -> Unit,
    compact: Boolean = false,
) {
    val pickDownloadDirectory = rememberPickDownloadDirectory(onPicked = onDownloadDirectory)
    val display = downloadDirectory?.let(::displayDownloadDirectory) ?: defaultDownloadDirectoryLabel
    var confirmDeleteAll by remember { mutableStateOf(false) }
    SettingsCard {
        Text("Downloads", color = PhoebeUi.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("Offline songs", color = PhoebeUi.mutedText, fontSize = 12.sp, modifier = Modifier.padding(bottom = 14.dp))
        Text("Download Location", color = PhoebeUi.secondaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PhoebeUi.subtleFill)
                .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(10.dp))
                .clickable(onClick = pickDownloadDirectory)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 0.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PhoebeIconView(PhoebeIcon.Download, tint = PhoebeUi.accentLight, modifier = Modifier.size(15.dp))
                if (!compact) {
                    Text(
                        display,
                        color = PhoebeUi.primaryText,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Text("Change", color = PhoebeUi.accentLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            if (compact) {
                Text(
                    display,
                    color = PhoebeUi.primaryText,
                    fontSize = 12.sp,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.fillMaxWidth().padding(start = 25.dp),
                )
            }
        }
        if (downloadDirectory != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onDownloadDirectory(null) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PhoebeIconView(PhoebeIcon.Close, tint = PhoebeUi.mutedText, modifier = Modifier.size(12.dp))
                Text("Use default location", color = PhoebeUi.secondaryText, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Downloaded songs", color = PhoebeUi.secondaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PhoebeUi.subtleFill)
                .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (downloadCount == 1) "1 downloaded song" else "$downloadCount downloaded songs",
                    color = PhoebeUi.primaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("Remove offline files and clear download status", color = PhoebeUi.secondaryText, fontSize = 12.sp)
            }
            Text(
                "Delete all",
                color = if (downloadCount > 0) PhoebeUi.accentLight else PhoebeUi.mutedText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = downloadCount > 0) { confirmDeleteAll = true }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
    if (confirmDeleteAll) {
        DeleteDownloadsDialog(
            downloadCount = downloadCount,
            onDismiss = { confirmDeleteAll = false },
            onConfirm = {
                confirmDeleteAll = false
                onDeleteAllDownloads()
            },
        )
    }
}

@Composable
private fun DeleteDownloadsDialog(
    downloadCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .widthIn(min = 300.dp, max = 420.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(PhoebeUi.modalSurface)
                .border(BorderStroke(1.dp, PhoebeUi.accentLight.copy(alpha = 0.18f)), RoundedCornerShape(18.dp))
                .padding(horizontal = 22.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Delete all downloads?", color = PhoebeUi.primaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "This removes offline files for $downloadCount ${if (downloadCount == 1) "song" else "songs"} and clears download status.",
                color = PhoebeUi.secondaryText,
                fontSize = 13.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = PhoebeUi.secondaryText)
                }
                TextButton(onClick = onConfirm) {
                    Text("Delete", color = PhoebeUi.accentLight, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun displayDownloadDirectory(uri: String): String =
    uri.removePrefix("file:")
        .removePrefix("//")
        .replace("%20", " ")
        .substringAfterLast("tree/", uri)
        .ifBlank { uri }

@Composable
private fun PersonalMixSettingsCard(
    preferences: PersonalMixPreferences,
    onPreferences: (PersonalMixPreferences) -> Unit,
    compact: Boolean = false,
) {
    val normalized = preferences.normalized()
    val weightTotal = normalized.mixWeightTotal()
    fun weightRange(current: Int): IntRange =
        0..(current + (100 - weightTotal)).coerceIn(current, 100)
    SettingsCard {
        Text("Personal Mix", color = PhoebeUi.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Row(
            Modifier.fillMaxWidth().padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Tune the mix created from Home", color = PhoebeUi.mutedText, fontSize = 12.sp)
            Text("$weightTotal%", color = PhoebeUi.accentLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        MixValueSlider(
            label = "Songs",
            value = normalized.limit,
            range = PersonalMixPreferences.MinLimit..PersonalMixPreferences.MaxLimit,
            suffix = "",
            compact = compact,
        ) { onPreferences(normalized.copy(limit = it)) }
        Spacer(Modifier.height(8.dp))
        MixValueSlider("Heavy rotation", normalized.heavyRotationWeight, weightRange(normalized.heavyRotationWeight), "%", compact) {
            onPreferences(normalized.copy(heavyRotationWeight = it))
        }
        MixValueSlider("Recent plays", normalized.recentWeight, weightRange(normalized.recentWeight), "%", compact) {
            onPreferences(normalized.copy(recentWeight = it))
        }
        MixValueSlider("Most played", normalized.mostPlayedWeight, weightRange(normalized.mostPlayedWeight), "%", compact) {
            onPreferences(normalized.copy(mostPlayedWeight = it))
        }
        MixValueSlider("Similar songs", normalized.similarWeight, weightRange(normalized.similarWeight), "%", compact) {
            onPreferences(normalized.copy(similarWeight = it))
        }
        MixValueSlider("Discovery", normalized.discoveryWeight, weightRange(normalized.discoveryWeight), "%", compact) {
            onPreferences(normalized.copy(discoveryWeight = it))
        }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = { onPreferences(PersonalMixPreferences.Default) }) {
            Text("Reset mix", color = PhoebeUi.accentLight)
        }
    }
}

private fun PersonalMixPreferences.mixWeightTotal(): Int =
    heavyRotationWeight + recentWeight + mostPlayedWeight + similarWeight + discoveryWeight

@Composable
private fun MixValueSlider(
    label: String,
    value: Int,
    range: IntRange,
    suffix: String,
    compact: Boolean,
    onValue: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = if (compact) 3.dp else 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = PhoebeUi.secondaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("$value$suffix", color = PhoebeUi.accentLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValue(it.roundToInt().coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = PhoebeUi.accentLight,
                activeTrackColor = PhoebeUi.accentLight,
                inactiveTrackColor = PhoebeUi.progressTrack,
            ),
        )
    }
}

@Composable
private fun GridSettingsCard(
    gridColumns: Int,
    onGridColumns: (Int) -> Unit,
    compact: Boolean = false,
) {
    SettingsCard {
        Text("Grid size", color = PhoebeUi.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(
            "Number of columns when browsing artists, albums, and songs",
            color = PhoebeUi.mutedText,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 14.dp),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (LibraryUiPreferences.MinGridColumns..LibraryUiPreferences.MaxGridColumns).forEach { count ->
                val selected = count == gridColumns
                Box(
                    Modifier
                        .weight(1f)
                        .height(if (compact) 42.dp else 46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onGridColumns(count) }
                        .background(if (selected) PhoebeUi.accent.copy(alpha = 0.22f) else PhoebeUi.subtleFill)
                        .border(BorderStroke(1.dp, if (selected) PhoebeUi.accentLight else PhoebeUi.border), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        count.toString(),
                        color = if (selected) PhoebeUi.primaryText else PhoebeUi.secondaryText,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeSettingsCard(
    sections: List<HomeSection>,
    onSections: (List<HomeSection>) -> Unit,
    compact: Boolean = false,
) {
    var order by remember { mutableStateOf(normalizedHomeSections(sections)) }
    var draggingSection by remember { mutableStateOf<HomeSection?>(null) }
    var dragStartIndex by remember { mutableStateOf<Int?>(null) }
    var dragTargetIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val onSectionsUpdated = rememberUpdatedState(onSections)
    val density = LocalDensity.current
    val rowHeight = if (compact) 46.dp else 50.dp
    val rowSpacing = 8.dp
    val rowStepPx = with(density) { rowHeight.toPx() + rowSpacing.toPx() }
    LaunchedEffect(sections) {
        order = normalizedHomeSections(sections)
    }
    SettingsCard {
        Text("Home", color = PhoebeUi.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("Drag sections into the order you want", color = PhoebeUi.mutedText, fontSize = 12.sp, modifier = Modifier.padding(bottom = 14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
            order.forEachIndexed { index, section ->
                val isDragging = draggingSection == section
                val startIndex = dragStartIndex
                val targetIndex = dragTargetIndex
                val rowOffsetPx = when {
                    draggingSection == null || startIndex == null || targetIndex == null -> 0f
                    isDragging -> dragOffsetPx
                    targetIndex > startIndex && index in (startIndex + 1)..targetIndex -> -rowStepPx
                    targetIndex < startIndex && index in targetIndex until startIndex -> rowStepPx
                    else -> 0f
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .offset { IntOffset(0, rowOffsetPx.roundToInt()) }
                        .zIndex(if (isDragging) 1f else 0f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDragging) PhoebeUi.accent.copy(alpha = 0.14f) else PhoebeUi.subtleFill)
                        .border(
                            BorderStroke(1.dp, if (isDragging) PhoebeUi.accent.copy(alpha = 0.35f) else PhoebeUi.border),
                            RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .pointerInput(section, rowStepPx) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggingSection = section
                                        dragStartIndex = index
                                        dragTargetIndex = index
                                        dragOffsetPx = 0f
                                    },
                                    onDragCancel = {
                                        draggingSection = null
                                        dragStartIndex = null
                                        dragTargetIndex = null
                                        dragOffsetPx = 0f
                                    },
                                    onDragEnd = {
                                        val from = dragStartIndex
                                        val to = dragTargetIndex
                                        draggingSection = null
                                        dragStartIndex = null
                                        dragTargetIndex = null
                                        dragOffsetPx = 0f
                                        if (from != null && to != null && from != to) {
                                            val nextOrder = order.moved(from, to)
                                            order = nextOrder
                                            onSectionsUpdated.value(nextOrder)
                                        }
                                    },
                                    onDrag = { change, drag ->
                                        change.consume()
                                        val start = dragStartIndex ?: return@detectDragGestures
                                        val minOffset = -start * rowStepPx
                                        val maxOffset = (order.lastIndex - start) * rowStepPx
                                        dragOffsetPx = (dragOffsetPx + drag.y).coerceIn(minOffset, maxOffset)
                                        dragTargetIndex = (start + (dragOffsetPx / rowStepPx).roundToInt())
                                            .coerceIn(0, order.lastIndex)
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        PhoebeIconView(PhoebeIcon.Drag, tint = PhoebeUi.mutedText, modifier = Modifier.size(16.dp))
                    }
                    PhoebeIconView(section.icon, tint = PhoebeUi.accentLight, modifier = Modifier.size(15.dp))
                    Text(section.label, color = PhoebeUi.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { onSections(HomeSection.defaultOrder) }) {
            Text("Reset order", color = PhoebeUi.accentLight)
        }
    }
}

@Composable
private fun FavoritePlaylistSettingsCard(
    onExport: () -> Unit,
    onImport: () -> Unit,
    compact: Boolean = false,
) {
    SettingsCard {
        Text("Favorite playlists", color = PhoebeUi.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(
            "Export or import locally saved favorite playlist flags",
            color = PhoebeUi.mutedText,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = if (compact) 8.dp else 12.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = onExport) {
                Text("Export", color = PhoebeUi.accentLight)
            }
            TextButton(onClick = onImport) {
                Text("Import", color = PhoebeUi.accentLight)
            }
        }
    }
}

private val HomeSection.icon: PhoebeIcon
    get() = when (this) {
        HomeSection.Mixes -> PhoebeIcon.Music
        HomeSection.Collections -> PhoebeIcon.Library
        HomeSection.Favorites -> PhoebeIcon.Heart
        HomeSection.FavoritePlaylists -> PhoebeIcon.Heart
        HomeSection.FavoriteArtists -> PhoebeIcon.Library
        HomeSection.FavoriteAlbums -> PhoebeIcon.Grid
        HomeSection.Recents -> PhoebeIcon.Bell
        HomeSection.RecentSongs -> PhoebeIcon.Music
        HomeSection.RecentArtists -> PhoebeIcon.Library
        HomeSection.RecentAlbums -> PhoebeIcon.Grid
        HomeSection.Played -> PhoebeIcon.Play
        HomeSection.Random -> PhoebeIcon.Grid
    }

private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices) return this
    val copy = toMutableList()
    val item = copy.removeAt(from)
    copy.add(to, item)
    return copy
}

private fun normalizedHomeSections(sections: List<HomeSection>): List<HomeSection> =
    sections
        .flatMap { section ->
            when (section) {
                HomeSection.Favorites -> listOf(HomeSection.FavoritePlaylists, HomeSection.FavoriteArtists, HomeSection.FavoriteAlbums)
                HomeSection.Recents -> listOf(HomeSection.RecentSongs, HomeSection.RecentArtists, HomeSection.RecentAlbums)
                else -> listOf(section)
            }
        }
        .filterNot { it == HomeSection.Favorites || it == HomeSection.Recents }
        .let { (it + HomeSection.defaultOrder).distinct() }

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = PhoebeUi.primaryText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = PhoebeUi.secondaryText, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PhoebeUi.accentLight,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = PhoebeUi.progressTrack,
            ),
        )
    }
}

@Composable
private fun AccountSettingsCard(
    session: PlexSession?,
    appSettings: AppSettings,
    listenBrainzCredentialAvailability: SecureCredentialAvailability,
    onConnectListenBrainz: (String) -> Unit,
    onDisconnectListenBrainz: () -> Unit,
    onListenBrainzSubmitNowPlaying: (Boolean) -> Unit,
    onListenBrainzSubmitListens: (Boolean) -> Unit,
    onListenBrainzSubmitCurrentTrackFeedback: (Boolean) -> Unit,
    compact: Boolean = false,
) {
    val signedIn = session?.token?.isNotBlank() == true
    val providerName = session.providerLabel()
    SettingsCard {
        Text("Account", color = PhoebeUi.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(
            if (signedIn) "Your signed-in media provider" else "Connect a media provider to browse your library",
            color = PhoebeUi.mutedText,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = if (compact) 10.dp else 14.dp),
        )
        if (!signedIn) {
            Text("Not signed in", color = PhoebeUi.secondaryText, fontSize = 13.sp)
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (compact) 10.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(if (compact) 40.dp else 48.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF3876C8), Color(0xFFB87C5C)))),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        session.userName,
                        color = PhoebeUi.primaryText,
                        fontSize = if (compact) 15.sp else 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "$providerName signed in",
                        color = PhoebeUi.secondaryText,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(PhoebeUi.subtleFill)
                    .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                AccountDetailRow(label = "Provider", value = providerName)
                AccountDetailRow(label = "Account", value = session.userName)
                session.selectedServer?.name?.takeIf { it.isNotBlank() }?.let { serverName ->
                    AccountDetailRow(label = "Server", value = serverName)
                }
                session.selectedServer?.uri?.takeIf { it.isNotBlank() }?.let { serverUri ->
                    AccountDetailRow(label = "Server URL", value = serverUri)
                }
                session.selectedLibrary?.title?.takeIf { it.isNotBlank() }?.let { libraryTitle ->
                    AccountDetailRow(label = "Library", value = libraryTitle)
                }
            }
        }
        Spacer(Modifier.height(if (compact) 16.dp else 18.dp))
        ListenBrainzSettingsSection(
            appSettings = appSettings,
            credentialAvailability = listenBrainzCredentialAvailability,
            onConnect = onConnectListenBrainz,
            onDisconnect = onDisconnectListenBrainz,
            onSubmitNowPlaying = onListenBrainzSubmitNowPlaying,
            onSubmitListens = onListenBrainzSubmitListens,
            onSubmitCurrentTrackFeedback = onListenBrainzSubmitCurrentTrackFeedback,
            compact = compact,
        )
    }
}

@Composable
private fun ListenBrainzSettingsSection(
    appSettings: AppSettings,
    credentialAvailability: SecureCredentialAvailability,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onSubmitNowPlaying: (Boolean) -> Unit,
    onSubmitListens: (Boolean) -> Unit,
    onSubmitCurrentTrackFeedback: (Boolean) -> Unit,
    compact: Boolean,
) {
    val settings = appSettings.listenBrainz
    val nowMs = LocalNowMs.current
    var token by remember(settings.connected) { mutableStateOf("") }
    var isConnecting by remember(settings.connected) { mutableStateOf(false) }
    LaunchedEffect(settings.connected, settings.lastValidatedAtMs) {
        isConnecting = false
        if (settings.connected) token = ""
    }
    val submitConnect = {
        if (token.isNotBlank() && credentialAvailability.canWrite && !isConnecting) {
            isConnecting = true
            onConnect(token.trim())
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PhoebeUi.subtleFill)
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text("ListenBrainz", color = PhoebeUi.primaryText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(
            if (settings.connected) "Scrobbling as ${settings.username}" else "Connect first-party ListenBrainz scrobbling",
            color = PhoebeUi.secondaryText,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 3.dp, bottom = 12.dp),
        )
        if (settings.connected) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(PhoebeUi.panel.copy(alpha = 0.52f))
                    .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                settings.username?.let { AccountDetailRow(label = "Username", value = it) }
                AccountDetailRow(label = "Storage", value = listenBrainzStorageLabel(settings.storageStatus, credentialAvailability))
                settings.lastNowPlayingSubmittedAtMs?.let {
                    AccountDetailRow(label = "Last now playing", value = formatLastPlayed(it, nowMs))
                }
                settings.lastListenSubmittedAtMs?.let {
                    AccountDetailRow(label = "Last listen", value = formatLastPlayed(it, nowMs))
                }
            }
            Spacer(Modifier.height(8.dp))
            SettingsSwitchRow(
                title = "Now playing",
                subtitle = "Show the current track on ListenBrainz",
                checked = settings.submitNowPlaying,
                onCheckedChange = onSubmitNowPlaying,
            )
            SettingsSwitchRow(
                title = "Listen history",
                subtitle = "Submit after half the track or four minutes",
                checked = settings.submitListens,
                onCheckedChange = onSubmitListens,
            )
            SettingsSwitchRow(
                title = "Current-track feedback",
                subtitle = "Enable Love, Hate, and Clear when ListenBrainz returns an MSID",
                checked = settings.submitCurrentTrackFeedback,
                onCheckedChange = onSubmitCurrentTrackFeedback,
            )
            (settings.lastListenError ?: settings.lastError)?.let { error ->
                Text(error, color = PhoebeUi.accentLight, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            TextButton(onClick = onDisconnect, modifier = Modifier.align(Alignment.End)) {
                Text("Disconnect", color = PhoebeUi.accentLight, fontWeight = FontWeight.SemiBold)
            }
        } else {
            ListenBrainzTokenField(
                token = token,
                onTokenChange = { token = it },
                onSubmit = submitConnect,
                enabled = !isConnecting,
                compact = compact,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                listenBrainzStorageNote(credentialAvailability),
                color = PhoebeUi.secondaryText,
                fontSize = 12.sp,
            )
            if (isConnecting) {
                Text(
                    "Connecting…",
                    color = PhoebeUi.secondaryText,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            settings.lastError?.let { error ->
                Text(error, color = PhoebeUi.accentLight, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { openExternalUrl(ListenBrainzSettingsUrl) }) {
                    Text("Get token", color = PhoebeUi.secondaryText)
                }
                if (token.isNotBlank() && !isConnecting) {
                    TextButton(onClick = { token = "" }) {
                        Text("Clear", color = PhoebeUi.secondaryText)
                    }
                }
                TextButton(
                    enabled = token.isNotBlank() && credentialAvailability.canWrite && !isConnecting,
                    onClick = submitConnect,
                ) {
                    Text(
                        if (isConnecting) "Connecting…" else "Connect",
                        color = if (token.isNotBlank() && credentialAvailability.canWrite && !isConnecting) {
                            PhoebeUi.accentLight
                        } else {
                            PhoebeUi.mutedText
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ListenBrainzTokenField(
    token: String,
    onTokenChange: (String) -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean,
    compact: Boolean,
) {
    BasicTextField(
        value = token,
        onValueChange = onTokenChange,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        visualTransformation = PasswordVisualTransformation(),
        textStyle = TextStyle(color = PhoebeUi.primaryText, fontSize = if (compact) 12.sp else 13.sp),
        cursorBrush = SolidColor(PhoebeUi.accentLight),
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(PhoebeUi.panel.copy(alpha = 0.58f))
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        decorationBox = { innerTextField ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                if (token.isBlank()) {
                    Text("User token", color = PhoebeUi.mutedText, fontSize = if (compact) 12.sp else 13.sp)
                }
                innerTextField()
            }
        },
    )
}

private fun listenBrainzStorageLabel(
    status: ListenBrainzCredentialStorageStatus,
    availability: SecureCredentialAvailability,
): String = when (status) {
    ListenBrainzCredentialStorageStatus.PersistentSecure -> availability.description
    ListenBrainzCredentialStorageStatus.PersistentBrowser -> availability.description
    ListenBrainzCredentialStorageStatus.SessionOnly -> "Session-only"
    ListenBrainzCredentialStorageStatus.Unavailable -> "Unavailable"
    ListenBrainzCredentialStorageStatus.Unknown -> availability.description
}

private fun listenBrainzStorageNote(availability: SecureCredentialAvailability): String =
    when (availability.status) {
        ListenBrainzCredentialStorageStatus.PersistentSecure ->
            "Token storage: ${availability.description}."
        ListenBrainzCredentialStorageStatus.PersistentBrowser ->
            "Token storage: encrypted browser storage. It survives reloads for this origin, but it is not a system keychain."
        ListenBrainzCredentialStorageStatus.SessionOnly ->
            "Token storage: session-only. Web users reconnect after reload."
        ListenBrainzCredentialStorageStatus.Unavailable ->
            availability.description
        ListenBrainzCredentialStorageStatus.Unknown ->
            "Token storage status will be checked when you connect."
    }

@Composable
private fun AccountDetailRow(
    label: String,
    value: String,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            color = PhoebeUi.mutedText,
            fontSize = 12.sp,
            modifier = Modifier.width(88.dp),
        )
        Text(
            value,
            color = PhoebeUi.primaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
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

private const val ListenBrainzSettingsUrl = "https://listenbrainz.org/settings/"

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
