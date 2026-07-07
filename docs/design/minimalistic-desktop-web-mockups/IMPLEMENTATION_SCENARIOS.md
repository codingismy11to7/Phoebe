# Minimalistic Redesign Implementation Scenarios

This handoff translates the generated mockups and the current Roborazzi coverage into an implementation plan for an AI coding agent. The goal is not to replace the existing screenshot tests with generated art. The goal is to implement the visual system in Compose, then re-record the real Roborazzi images for every scenario listed in `SCREENSHOT_SCENARIO_MATRIX.md`.

## Source Inputs

- `docs/design/DESIGN.md` is the source of truth for the Phoebe Elegant Player direction.
- `desktop-home-library.png`, `desktop-album-player.png`, `web-home-search.png`, `web-settings-listening.png`, `phone-scenario-atlas.png`, `tablet-scenario-atlas.png`, and `special-states-atlas.png` are target references.
- `composeApp/src/screenshotTest/roborazzi/` is the current screenshot artifact set.
- `composeApp/src/androidUnitTest/kotlin/com/phoebe/app/PhoebeAndroidScreenshotTest.kt` and `composeApp/src/desktopTest/kotlin/com/phoebe/app/PhoebeDesktopScreenshotTest.kt` define the capture coverage.

## Non-Negotiable Design Tokens

### Color

Use these as Minimalist design-system tokens, not one-off colors inside screens.

| Role | Value | Use |
|---|---:|---|
| Warm Bone | `#F7F6F3` | primary canvas, app background |
| Porcelain Surface | `#FFFFFF` | raised panels, sheets, list fields |
| Soft Paper | `#FBFAF7` | secondary panels, alternating rows |
| Warm Divider | `#EAE6DE` | default 1px dividers and panel borders |
| Faint Grid Line | `rgba(17, 17, 17, 0.045)` | optional archive ledger motif |
| Charcoal Ink | `#111111` | primary text and filled controls |
| Soft Charcoal | `#2F3437` | secondary headings and strong metadata |
| Muted Stone | `#787774` | helper text, inactive nav, metadata |
| Disabled Stone | `#AAA6A0` | disabled labels and timestamps |
| Archive Blue | `#2F6F92` | only interactive accent |
| Archive Blue Wash | `#E1F3FE` | selected rows, hover fills, active chips |
| Listening Charcoal | `#111111` | dark listening/player canvas only |
| Dark Panel | `#181818` | dark player side panels |
| Warm White | `#F7F6F3` | dark player primary text |
| Archive Blue On Dark | `#A8CEE2` | dark progress and active states |

Rules:

- Album artwork may contain other colors; chrome does not.
- Tints are low-opacity paper washes. Red/blue tint screenshot scenarios must not become saturated full themes.
- Dark mode is for focused listening and dark test variants; do not let dark player styling leak into light library/settings surfaces.

### Typography

Use the existing font resources already present in `Theme.kt`:

- Display/music-object text: Instrument Serif.
- UI/body/navigation/settings text: Geist.
- Durations, catalog numbers, codec labels, bitrates, timestamps: Geist Mono.
- Do not use Inter.

Target scale:

| Role | Desktop | Tablet | Phone |
|---|---:|---:|---:|
| Brand/title | 40-56sp serif | 36-48sp serif | 36-44sp serif |
| Album/player title | 34-48sp serif | 32-44sp serif | 26-34sp serif |
| Section heading | 16-18sp sans | 15-17sp sans | 14-16sp sans |
| Row title | 14-16sp sans | 14-16sp sans | 14-16sp sans |
| Body/metadata | 13-15sp sans | 13-15sp sans | 13-15sp sans |
| Tiny labels | minimum 11sp | minimum 11sp | minimum 11sp |

Rules:

- Serif is for Phoebe brand and music objects only: album, playlist, artist, and now-playing titles.
- Settings and dense tables stay sans.
- Use mono only for technical/numeric metadata.
- Normal text uses zero letter spacing; uppercase labels may use at most `0.08em`.

### Shape

| Token | Value | Use |
|---|---:|---|
| App/window radius | 8-12dp | desktop mock shell only |
| Panel radius | 8dp | primary panels, settings groups, right columns |
| Control radius | 6-10dp | search fields, segmented controls, buttons |
| Media radius | 8-12dp | album art, thumbnails |
| Mobile sheet top radius | 22-28dp | queue, lyrics, settings sheets |
| Circular control | circle | play/pause and media transport only |

Rules:

- No giant pill containers except tiny badges/toggles and circular transport controls.
- Every structural panel gets a 1px border with Warm Divider.
- Shadows are absent or extremely soft, max alpha `0.04`.
- Prefer dividers, whitespace, and tonal fills over stacked cards.

### Spacing

Use a stable spacing scale:

- 4dp hairline offsets and icon nudge.
- 8dp tight internal gaps.
- 12dp row internal gaps.
- 16dp standard component padding.
- 24dp major section spacing.
- 32dp desktop content gutters.
- 40dp large desktop/tablet header breathing room.

Rules:

- Parent composables own gaps via `Arrangement.spacedBy`.
- Fixed-format UI such as transport, queue rows, album cards, tab bars, and visualizer tiles must have stable dimensions.
- Do not let hover/selected states change measured size.

## Component Inventory

Implement tokens first, then components. Do not scatter visual decisions in feature screens.

### Token Layer

Update:

- `ui/core/src/commonMain/kotlin/com/phoebe/app/ui/PhoebeTokens.kt`
- `ui/core/src/commonMain/kotlin/com/phoebe/app/ui/Theme.kt`

Expected changes:

- Adjust `PhoebeDesignSystem.Minimalist` palettes to the Warm Bone / Archive Blue model.
- Keep existing Minimalist tint options, but make Archive Blue the default and use Slate/Graphite as restrained alternatives.
- Keep `PhoebeShapeTokens.Minimalist` crisp: panel 8dp, control 8-10dp, media 8-12dp, sheet 24dp, button circular only for media controls.
- Add any missing spacing/elevation tokens rather than hardcoding numbers.

### Atoms

Create or standardize reusable primitives:

- `PhoebePanel`: bordered surface, token background, 8dp radius, no heavy shadow.
- `PhoebeArtworkFrame`: stable square/rect media frame with 8-12dp radius.
- `PhoebeSectionHeader`: sans heading, optional `View all` action.
- `PhoebeArchiveLabel`: mono/uppercase tiny label for catalog numbers and codec metadata.
- `PhoebeWaveform`: shared waveform renderer for transport, rows, receipts, and player.
- `PhoebeIconButton`: stable 44dp touch target on mobile/tablet, 36-44dp desktop.
- `PhoebeSegmentedControl`: tabs/design choices/settings choices, token selected fill.
- `PhoebeToggleRow`: settings row with direct label, helper text, switch.

### Molecules

- `PhoebeNavigationRow`: icon, label, selected rail/fill, active Archive Blue.
- `PhoebePlaylistShortcutRow`: thumbnail/icon, title, count, stable row height.
- `PhoebeMixCatalogCard`: archival mix card with catalog number, cropped art, title, small metadata.
- `PhoebeTrackRow`: artwork/index/current indicator, title, artist/album, duration, favorite, overflow.
- `PhoebeQueueRow`: thumbnail, title, artist, duration, reorder handle.
- `PhoebeTransport`: current track identity, controls, waveform, volume/output/queue/lyrics.
- `PhoebeSearchResultGroup`: grouped Artists, Albums, Tracks, Playlists.
- `PhoebeSettingsChoiceTile`: appearance and visualizer tiles.
- `PhoebeCreditsStrip`: producer/composer/label/year/listening note.

### Organisms

- Desktop shell: sidebar, main stage, right context, bottom transport.
- Home content: mix shelf, most played, recently played, optional accordions.
- Library content: artists/albums/songs tabs, grid/list variants, scrollbar and dense-grid states.
- Detail content: album, playlist, song, artist, artist events, artist radio.
- Player content: full player, visualizer presets, queue-expanded, blurred artwork on/off.
- Search content: query, saved searches, top result, grouped results.
- Settings content: category list, appearance, audio playback, library, downloads, notifications, events, about, advanced.
- Sign-in content: welcome, provider buttons, account state.

## Scenario Implementation Plan

### Scenario 1: Desktop Core Shell

Covers: `desktop-home-*`, `desktop-library-*`, `desktop-album-*`, `desktop-player-*`, `desktop-search-*`, `desktop-settings-*`, favorites, playlist, radio, sign-in.

Implementation:

- Keep the four zones: left rail 220-260dp, main stage, right context 300-360dp, bottom transport 88-112dp.
- Make the left rail quiet and editorial: large serif Phoebe brand, 44-48dp rows, selected row with Archive Blue left rail and wash fill.
- Use catalog-card mix cards on Home instead of heavy image tiles.
- Use row dividers and table-like metadata instead of card stacks.
- Preserve right Up Next on desktop scenarios unless the current scenario intentionally hides or expands it.
- Bottom transport is visually identical across all desktop non-player screens.

Verification:

- `desktopCoreFlowsDark`
- `desktopRepresentativeFlowsLight`
- `desktopTintedBackgroundsDark`
- `desktopTintSettings`

### Scenario 2: Desktop Detail Surfaces

Covers: album, album old layout, artist, artist old layout, artist events, artist events link, artist radio, playlist, song.

Implementation:

- Use large artwork as the emotional anchor.
- Detail headers use serif object titles, sans metadata, mono catalog numbers.
- Track lists use aligned columns for title, artist, album, duration, codec, bitrate.
- Credits, lyrics, notes, events, gallery, or radio context live in the right column or a lower strip.
- Old artwork layout screenshots should still inherit tokens, typography, and transport, even if structure remains old-layout specific.

Verification:

- `desktopDetailOldArtworkLayoutDark`
- `desktopArtistEventsDark`
- desktop core detail captures.

### Scenario 3: Desktop Player And Visualizers

Covers: player, player visualizer, visualizer presets, TV frame, Nocturne player queue.

Implementation:

- Player can use dark listening mode as the primary immersive exception.
- Keep visualizers musical and calm: no neon glows, no psychedelic gradients, no analytics charts.
- Each preset should share a frame: artwork/title/metadata, waveform/progress, transport controls, output, queue.
- Presets differ by visualizer drawing only: Artwork, Alchemy, Battery, Bars & Waves, Blazing Colors, Plenoptic, Vortex Spectrum, Classic EQ, Halo Spectrum, Wireframe Spectrum 3D, TV Frame.
- Queue-expanded player keeps queue rows readable and stable.

Verification:

- `desktopVisualizerPresetsDark`
- `desktopVisualizerTvFrameDark`
- `desktopNocturnePlayerQueueDark`

### Scenario 4: Phone Home And Library

Covers: home dark/light, expanded, accordions collapsed/expanded, played rows, favorite playlists/artists/albums, library, library scrollbar, five-column grid, red tint, design-system representative flows.

Implementation:

- Use true phone flow, not a squeezed desktop shell.
- Home: large serif title, compact mix catalog strip, accordion sections where applicable, sticky mini-player, bottom navigation.
- Library: segmented Artists/Albums/Songs switch, 2-column default grid, dense five-column grid only in that scenario, visible scrollbar in scrollbar scenario.
- Favorite surfaces reuse collection shelves and row/list components; do not invent separate card styling.
- Red tint remains a restrained paper wash plus active accent; it must not override typography or chrome.

Verification:

- phone Home, HomeExpanded, HomeAccordionsCollapsed, HomeAccordionsExpanded, HomePlayedRows.
- phone FavoritePlaylists, FavoriteArtists, FavoriteAlbums.
- phone Library, LibraryScrollbar, LibraryFiveColumnGrid.
- phone red tint captures.

### Scenario 5: Phone Detail, Search, Player, Settings, Sign-In

Covers: album, artist, artist events/link, artist radio, playlist, radio, song, search, player, player up-next expanded, blurred artwork, visualizers, settings, sign-in, sign-in providers.

Implementation:

- Album/playlist/song detail: framed artwork, serif title, metadata, Play All/Download/Favorite, track rows, credits/about at bottom.
- Artist detail: artist header, albums/songs/events/radio affordances; event cards use bordered ledger rows.
- Search: quiet 44-48dp field, saved searches, top result, grouped results.
- Player: full art-led listening screen, readable title/artist, waveform, scrubber, controls, output, queue. Up Next is a bottom sheet or dedicated tab.
- Blurred artwork ON uses a soft reflected artwork panel; OFF uses plain Warm Bone/Porcelain surfaces.
- Settings: grouped cells, direct controls, appearance tiles, visualizer choices; no serif in dense settings rows except screen title.
- Sign-in: minimal welcome, provider buttons, no marketing copy.

Verification:

- phone Search dark/light/red tint.
- phone Player dark/light/upnext/blurred-artwork/visualizer variants.
- phone Settings and SignIn captures.

### Scenario 6: Tablet Split Layout

Covers: tablet home, library, library up-next expanded, radio, playlist, artist, artist radio, search, search up-next expanded, player, design-system representative flows.

Implementation:

- Tablet is touch-first split layout, not desktop squeezed down.
- Use a compact left rail 88-140dp where needed.
- Main detail/list column takes 55-65 percent of width.
- Right column holds Up Next, credits, lyrics, notes, gallery, or queue.
- Bottom mini-player remains persistent and touch-friendly.
- Track and queue rows stay at least 48dp tall.

Verification:

- tablet base captures.
- tablet up-next expanded captures.
- tablet design-system representative flows.

### Scenario 7: Web-Hosted App

Covers: current web app behavior even though Roborazzi directory has no separate web PNGs today.

Implementation:

- Use the same Compose shared tokens and components.
- Web can use a top app bar plus left rail, but it remains an app, not a landing page.
- Sticky bottom transport spans the browser app frame.
- Browser-hosted layout uses the same data surfaces: Home, Library, Search, Settings, Up Next, focused listening preview.
- Reuse phone/tablet/desktop breakpoints rather than creating a separate visual system.

Verification:

- Existing Playwright web screenshots/e2e checks.
- Add web screenshots only after shared Compose UI is stable, if desired.

## Per-Surface Acceptance Criteria

### Home

- Mix cards are archival catalog cards with fixed aspect ratio.
- Most Played and Recently Played are rows with artwork, title, artist, album, plays/date, favorite, overflow.
- Accordions and expanded layouts preserve spacing and typography.

### Library

- Artists, Albums, Songs tabs are consistent across phone/tablet/desktop.
- Dense grid scenarios still respect minimum readable labels.
- Scrollbar scenarios show scrolling without layout shift.

### Search

- Search field is quiet, 44-48dp, with grouped results.
- Top result uses a single highlighted row or panel, not a dashboard card.
- Saved search and Save current controls remain visible.

### Detail

- Album art is large and stable.
- Titles use serif; metadata and actions use sans/mono.
- Track rows align duration/codec/bitrate and support current-row highlight.

### Queue

- Queue rows show thumbnail, title, artist, duration, and reorder handle.
- Current/upcoming state uses Archive Blue plus weight/shape, not color alone.
- Queue is never hidden behind an unlabeled icon in player scenarios.

### Transport

- Current track identity is left on desktop/web, compact on mobile/tablet.
- Center controls never resize or shift.
- Waveform is musical, not chart-like.
- Volume/output/EQ/lyrics/queue controls keep stable hit targets.

### Settings

- Category list is simple and utilitarian.
- Appearance tile selection is bordered and readable.
- Toggles and segmented controls use Archive Blue active state.
- Visualizer tiles are flat paper samples, not glossy cards.

### Visualizers

- Presets differ in drawing style only; chrome stays consistent.
- Blazing Colors can be richer, but still controlled and not neon.
- Wireframe Spectrum 3D remains a calm technical drawing, not a 3D hero.

## Recommended AI Rollout Sequence

1. Token pass: update Minimalist palettes, shapes, typography mapping, spacing/elevation locals.
2. Primitive pass: implement/standardize panel, artwork frame, section header, archive label, waveform, segmented control, queue row, track row.
3. Shell pass: desktop sidebar/main/right/transport and mobile/tablet nav/mini-player templates.
4. Core surfaces: Home, Library, Search, Album/Playlist/Artist detail.
5. Player surfaces: full player, queue expansion, blurred artwork, visualizers.
6. Settings/sign-in/radio/events/favorites polish.
7. Web pass: ensure browser-hosted breakpoints use the same components.
8. Screenshot pass: run and inspect representative captures first, then record full matrix.

## Commands For Verification

Use these after implementation, not before:

```bash
./gradlew :composeApp:recordRoborazziAndroidHostTest --console=plain
./gradlew :composeApp:desktopTest --tests com.phoebe.app.PhoebeDesktopScreenshotTest --console=plain
npm run web:screenshots:update
```

If only verifying before recording:

```bash
./gradlew :composeApp:verifyRoborazziAndroidHostTest --console=plain
./gradlew :composeApp:desktopTest --tests com.phoebe.app.PhoebeDesktopScreenshotTest --console=plain
npm run web:screenshots
```

## Guardrails

- Do not change playback behavior, queue behavior, sync behavior, or provider data flows while implementing the visual redesign.
- Do not remove existing screenshot scenarios unless explicitly requested.
- Do not make default/porcelain/nocturne/brutalist regress while improving minimalist.
- Preserve user settings and current `PhoebeDesignSystem` plumbing.
- Use shared components and tokens; avoid per-screen color/shape forks.
- Keep all text readable in generated and recorded screenshots.
