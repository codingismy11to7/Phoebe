# Phoebe Brutalist Coverage Mockups

This folder contains generated concept coverage for a stricter minimal
brutalist Phoebe direction. It is based on:

- `docs/design/DESIGN_DNA.md`
- `composeApp/src/desktopTest/kotlin/com/phoebe/app/PhoebeDesktopScreenshotTest.kt`
- `composeApp/src/androidUnitTest/kotlin/com/phoebe/app/PhoebeAndroidScreenshotTest.kt`
- the current roborazzi images under `composeApp/src/screenshotTest/roborazzi/`

These images are implementation direction, not pixel baselines. Text inside
generated bitmap mockups can be imperfect; use the route labels, layout
coverage, component shapes, density, and token rules as the source of truth.

## Generated Boards

- `desktop-core-coverage.png` - desktop route coverage for home, library,
  favorites, playlist, artist, events, album, search, player, settings, sign-in.
- `desktop-variants-coverage.png` - desktop light/tint/design/scrollbar/old
  artwork/visualizer variant coverage.
- `phone-core-coverage.png` - phone route coverage for the mobile screenshot
  matrix, including accordion states, dense library, events, song detail, queue,
  settings, and sign-in.
- `phone-variants-coverage.png` - phone light mode, blurred artwork, visualizer,
  and design-system comparison coverage.
- `tablet-web-coverage.png` - tablet route coverage plus two web viewport
  concepts that reuse the same information architecture.
- `implementation-scenarios-board.png` - compact visual handoff for tokens,
  components, responsive rules, state rules, and implementation order.

## Design Target

The target is a minimal brutalist "tactical hi-fi" system:

- Hard-edged music terminal, not SaaS dashboard.
- Album art supplies warmth; the app chrome stays disciplined.
- Black, white, gray, and red are the system colors.
- Layout uses seams, rules, tables, and typography rather than floating cards.
- Controls feel like hardware: square utility buttons, circular play/pause only.
- Metadata feels intentional: durations, codecs, bitrates, source, and catalog
  values use mono typography.

## Tokens

### Dark Mode

- Background: `#0A0A0A`
- Panel: `#101010`
- Raised panel: `#151515`
- Pressed/hover: `#1E1E1E`
- Primary text: `#EAEAEA`
- Secondary text: `#A8A8A8`
- Muted text: `#6F6F6F`
- Divider: `rgba(234,234,234,0.14)`
- Faint divider: `rgba(234,234,234,0.075)`
- Accent: `#FF2A2A`
- Accent wash: `rgba(255,42,42,0.14)`
- Success/status only: `#4AF626`

### Light Mode

Use light mode only where the screenshot matrix requires it.

- Background: `#F3F0E8`
- Panel: `#FFFFFF`
- Raised panel: `#EFECE4`
- Pressed/hover: `#E5E0D8`
- Primary text: `#111111`
- Secondary text: `#3A3A3A`
- Muted text: `#6D6D6D`
- Divider: `rgba(17,17,17,0.18)`
- Accent: `#D71920`

### Accent Rules

- Red marks active playback, selected navigation, current rows, focus outlines,
  progress, primary destructive/critical state, and favorite-on.
- Do not introduce purple, blue, rainbow, or neon accents in the Brutalist
  system.
- Tinted-background screenshots should express tint as a restrained wash plus
  red selection, not as a new brand color.
- Blue tint settings screenshots remain a test case for the settings picker, but
  the brutalist implementation should keep system chrome black/white/red and
  confine blue to the swatch preview/selected tint sample.

## Shape

- App panels: `0dp` radius.
- Album artwork: `0dp` to `4dp` radius.
- Phone bottom sheet top corners: max `8dp`.
- Utility buttons: square `44dp` minimum target.
- Play/pause: circular allowed.
- Search fields and segmented controls: rectangular, max `4dp`.
- Focus rings: `1dp` to `2dp` red outline, offset by `2dp`, no glow.
- Avoid pills except unavoidable short technical chips.

## Typography

- Display: heavy grotesk, 700-900 weight, for `PHOEBE`, screen titles, album
  titles, artist names.
- Body: clean sans, 400-700 weight, for rows, settings, labels, lyrics.
- Mono: technical metadata, durations, codec, bitrate, source, timestamps,
  catalog IDs, row numbers, uppercase section labels.
- Mobile body text should not drop below `14sp`.
- Desktop rows should land around `14sp` to `16sp`.
- Mono labels can be smaller but must remain readable in screenshots.

## Layout Scenarios

### Desktop

Desktop keeps five persistent zones:

- Left nav rail: `220dp` to `260dp`, brand, primary routes, playlists, account.
- Main stage: route content, tables, shelves, and detail views.
- Right context rail: `300dp` to `380dp`, Up Next, events, radio, inspector.
- Bottom transport: `92dp` to `112dp`, current track, waveform, controls,
  volume, queue/tools.
- Top utility strip where needed: search, source/sync, filters, view controls.

Use dividers between zones. Do not wrap major zones in soft cards.

### Tablet

Tablet keeps desktop information density but reduces rail width and preserves
touch targets:

- Left rail: compact labeled rail or icon rail depending on available width.
- Right rail: queue/inspector remains visible for landscape tests.
- Bottom transport: persistent and shorter than desktop.
- Up Next expanded states must not occlude primary route content.

### Phone

Phone uses a route-first flow:

- Top app bar/search/route title.
- Scrollable route content.
- Sticky mini-player above bottom navigation where applicable.
- Bottom navigation for Home, Search, Library, Playlists, Radio.
- Full player and queue are pushed screens or sheets.
- Keep two dense blocks per screen at most; use section dividers instead of
  nested cards.

### Web

The web concepts in this folder are not current roborazzi tests, but they map
the same data to browser constraints:

- Browser command bar at top.
- Compact left navigation.
- Main split grid for home/search/library.
- Right inspector for queue/settings/events/radio/source.
- Persistent bottom transport.

## Component Scenarios

Implement shared components before route-specific work:

- `BrutalistNavRail`: square active indicator, red left rule, no pill selected
  background.
- `BrutalistBottomTransport`: current artwork, title/artist/album, quality,
  waveform, playback controls, volume, output, EQ, lyrics, Ultimate Guitar,
  queue.
- `BrutalistMiniPlayer`: compact current track row for phone/tablet route
  screens.
- `BrutalistQueue`: title, All toggle, Keep Playing switch, Clear, current row,
  durations, drag handles.
- `BrutalistSearchField`: rectangular, bordered, red focus state, no glow.
- `BrutalistTabs`: rectangular segmented tabs for Artists/Albums/Songs/Playlists
  and local settings categories.
- `BrutalistTrackRow`: index/status, optional thumbnail, title, artist, album,
  duration, codec, bitrate, favorite, overflow.
- `BrutalistMediaGrid`: stable square artwork cells for albums, artists,
  playlists, mixes, and dense five-column library.
- `BrutalistEventRow`: artwork/date block, title, date/time, venue, city,
  price, provider, availability, ticket action.
- `BrutalistRadioRow`: station artwork, title, subtitle, edit/delete/play.
- `BrutalistSettingsGroup`: category nav, rectangular selectors, toggles,
  sliders, visualizer selector, tint swatches.
- `BrutalistSignInProvider`: provider rows/buttons for Plex, Jellyfin, Emby,
  Subsonic, Music Assistant, local folder, Radio.
- `BrutalistVisualizerPanel`: preset-specific visualizer surface using the same
  red/white/gray discipline.

## Screenshot Scenario Matrix

### Desktop Core

- `desktop-home-dark`
- `desktop-homeplayedrows-dark`
- `desktop-favoriteplaylists-dark`
- `desktop-favoriteartists-dark`
- `desktop-favoritealbums-dark`
- `desktop-library-dark`
- `desktop-library-scrollbar-dark`
- `desktop-playlist-dark`
- `desktop-artist-dark`
- `desktop-artistradio-dark`
- `desktop-artist-events-link-dark`
- `desktop-artist-events-dark`
- `desktop-album-dark`
- `desktop-search-dark`
- `desktop-player-dark`
- `desktop-playervisualizer-dark`
- `desktop-settings-dark`
- `desktop-signin-dark`

### Desktop Variants

- `desktop-home-light`
- `desktop-library-light`
- `desktop-search-light`
- `desktop-player-light`
- `desktop-home-red-tint-dark`
- `desktop-library-red-tint-dark`
- `desktop-search-red-tint-dark`
- `desktop-settings-blue-tint-dark`
- `desktop-settings-blue-tint-light`
- `desktop-artist-old-artwork-layout-dark`
- `desktop-album-old-artwork-layout-dark`
- `desktop-brutalist-home-dark`
- `desktop-brutalist-home-light`
- `desktop-brutalist-library-dark`
- `desktop-brutalist-library-light`
- `desktop-brutalist-album-dark`
- `desktop-brutalist-album-light`
- `desktop-brutalist-player-dark`
- `desktop-brutalist-player-light`
- `desktop-brutalist-settings-dark`
- `desktop-brutalist-settings-light`
- `desktop-brutalist-library-scrollbar-dark`
- `desktop-nocturne-player-queue-dark`

### Desktop Visualizers

- `desktop-player-visualizer-alchemy-dark`
- `desktop-player-visualizer-battery-dark`
- `desktop-player-visualizer-bars-and-waves-dark`
- `desktop-player-visualizer-blazing-colors-dark`
- `desktop-player-visualizer-plenoptic-dark`
- `desktop-player-visualizer-vortex-spectrum-dark`
- `desktop-player-visualizer-classic-eq-dark`
- `desktop-player-visualizer-halo-spectrum-dark`
- `desktop-player-visualizer-wireframe-spectrum-3d-dark`
- `desktop-player-visualizer-tv-frame-dark`

### Phone Core

- `android-phone-home-dark`
- `android-phone-home-expanded-dark`
- `android-phone-home-accordions-collapsed-dark`
- `android-phone-home-accordions-expanded-dark`
- `android-phone-home-played-rows-dark`
- `android-phone-favorite-playlists-dark`
- `android-phone-favorite-artists-dark`
- `android-phone-favorite-albums-dark`
- `android-phone-artist-radio-dark`
- `android-phone-library-dark`
- `android-phone-library-scrollbar-dark`
- `android-phone-library-five-column-grid-dark`
- `android-phone-radio-dark`
- `android-phone-playlist-dark`
- `android-phone-artist-dark`
- `android-phone-artist-events-link-dark`
- `android-phone-artist-events-dark`
- `android-phone-album-dark`
- `android-phone-song-dark`
- `android-phone-search-dark`
- `android-phone-player-dark`
- `android-phone-player-visualizer-dark`
- `android-phone-player-upnext-expanded-dark`
- `android-phone-settings-dark`
- `android-phone-signin-dark`
- `android-phone-signin-providers-dark`
- `android-phone-home-red-tint-dark`
- `android-phone-library-red-tint-dark`
- `android-phone-search-red-tint-dark`

### Phone Variants

- `android-phone-home-light`
- `android-phone-home-expanded-light`
- `android-phone-home-accordions-collapsed-light`
- `android-phone-home-accordions-expanded-light`
- `android-phone-library-light`
- `android-phone-search-light`
- `android-phone-player-light`
- `android-phone-player-blurred-artwork-on-light`
- `android-phone-player-blurred-artwork-off-light`
- `android-phone-player-upnext-expanded-light`
- `android-phone-player-visualizer-alchemy-light`
- `android-phone-player-visualizer-battery-light`
- `android-phone-player-visualizer-bars-and-waves-light`
- `android-phone-player-visualizer-blazing-colors-light`
- `android-phone-player-visualizer-plenoptic-light`
- `android-phone-player-visualizer-vortex-spectrum-light`
- `android-phone-player-visualizer-classic-eq-light`
- `android-phone-player-visualizer-halo-spectrum-light`
- `android-phone-player-visualizer-wireframe-spectrum-3d-light`

### Phone Design Systems

The tests include representative flows for Porcelain, Nocturne, Brutalist, and
Minimalist across dark and light appearances:

- Home
- Library
- Album
- Player
- Settings
- Search

For the Brutalist implementation, these become the acceptance set for the new
tokens. The other systems should continue to render as comparison/regression
coverage unless the product decision is to remove or replace them.

### Tablet Core

- `android-tablet-home-dark`
- `android-tablet-favorite-playlists-dark`
- `android-tablet-favorite-artists-dark`
- `android-tablet-artist-radio-dark`
- `android-tablet-library-dark`
- `android-tablet-library-upnext-expanded-dark`
- `android-tablet-radio-dark`
- `android-tablet-playlist-dark`
- `android-tablet-artist-dark`
- `android-tablet-search-dark`
- `android-tablet-search-upnext-expanded-dark`
- `android-tablet-player-dark`

### Tablet Design Systems

The tests include Porcelain, Nocturne, Brutalist, and Minimalist across dark and
light appearances for:

- Home
- Library
- Album
- Player
- Settings

## Implementation Scenarios For An AI Agent

### Scenario 1: Token Pass

Create or update Brutalist design tokens only. Wire colors, typography scale,
spacing, shape, divider, focus, and state colors through the existing design
system entry points. Do not touch route layout yet.

Acceptance:

- Brutalist dark and light screenshots use the token palette above.
- Red is the only accent in Brutalist route chrome.
- Focus, hover, selected, pressed, disabled, and current-playing states are
  visually distinct.

### Scenario 2: Shape Pass

Replace rounded cards and soft pill controls in Brutalist surfaces with square
panels, seams, and rectangular controls.

Acceptance:

- Major panels use 0dp radius.
- Album art remains square or near-square.
- Utility controls keep 44dp minimum targets.
- Play/pause remains the only circular primary control.

### Scenario 3: Typography Pass

Apply display/body/mono roles consistently.

Acceptance:

- Route titles and album/artist names use heavy display.
- Track rows and settings remain readable.
- Codec, bitrate, duration, source, timestamps, and row numbers use mono.
- No critical phone text drops below readable size.

### Scenario 4: Navigation And Transport Pass

Implement Brutalist rail, bottom transport, mini-player, queue, and phone bottom
navigation.

Acceptance:

- Desktop and tablet preserve persistent playback and Up Next context.
- Phone preserves mini-player and bottom navigation where currently present.
- Queue expanded states remain navigable and do not hide primary controls.

### Scenario 5: Route Pass

Apply the shared primitives to Home, Library, Playlist, Artist, Artist Events,
Artist Radio, Album, Song, Search, Player, Settings, Sign In, and Radio.

Acceptance:

- Every scenario in the matrix above renders with the same component family.
- Tables and lists use dividers, red current-row markers, and stable row heights.
- Home shelves and media grids keep stable image aspect ratios.
- Events, radio, and sign-in provider rows keep all current data visible.

### Scenario 6: Visualizer Pass

Translate every visualizer preset into the Brutalist language without making
all presets look identical.

Acceptance:

- Bars and Waves: red/gray waveform.
- Alchemy: red geometric diagram.
- Battery: stacked charge cells.
- Blazing Colors: high-energy red/white spectrum, no rainbow chrome.
- Plenoptic: lens/grid diagram.
- Vortex Spectrum: spiral/radial meter.
- Classic EQ: rectangular spectrum columns.
- Halo Spectrum: circular meter.
- Wireframe Spectrum 3D: red wireframe plane.
- TV frame: visualizer is framed, readable, and not cropped.

### Scenario 7: Responsive Pass

Tune desktop, tablet, phone, and web-like breakpoints.

Acceptance:

- Desktop `1365x900` keeps five zones.
- Tablet `1180x820` keeps a split layout with persistent context.
- Phone `430x932` stays touch-safe and avoids cramped tables.
- Tall phone sign-in providers fit in the extended height scenario.

### Scenario 8: Screenshot Test Pass

Run/update screenshot tests last.

Acceptance:

- Existing scenario names remain stable unless the test matrix intentionally
  changes.
- Regenerate roborazzi baselines only after tokens, components, routes, states,
  and responsive layouts are complete.
- Compare dark, light, tint, dense-list, queue-expanded, blurred-artwork, old
  artwork, design-system, and visualizer variants.

## Anti-Patterns

- Do not make a generic music SaaS dashboard.
- Do not use blue/purple gradients for Brutalist chrome.
- Do not use glass cards, glow, large soft radii, or nested cards.
- Do not hide codec/bitrate/duration/source metadata to make screens prettier.
- Do not let phone layouts become tiny desktop tables.
- Do not let visualizer presets introduce unrelated brand palettes.
