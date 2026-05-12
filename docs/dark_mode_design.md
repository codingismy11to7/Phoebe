# Music Player UI Design DNA

Use this document as a creative and implementation brief for recreating the attached music player mockup across desktop and mobile.

## 1. Core Product Feel

The interface should feel like a premium, immersive music app: cinematic, calm, modern, and emotionally focused on the album artwork. It should combine a dark glassy operating-system aesthetic with soft gradients, subtle shadows, and restrained neon-purple accents.

**Keywords:** dark mode, glassmorphism, cinematic, spacious, premium, soft glow, editorial album focus, minimal controls, desktop + mobile parity.

## 2. Visual Direction

### Theme

- Primary theme: dark, almost-black UI with subtle navy and purple undertones.
- Overall contrast: high enough for readability, but not stark. Avoid pure black and pure white where possible.
- Mood: late-night listening, premium streaming, calm focus.
- The UI should feel spacious and breathable rather than dense.

### Color Palette

Use a dark neutral base with purple accents.

```text
Background base:        #080B12 / #0B0F17
Panel background:       #10151F / #121722
Sidebar background:     #0A0D14
Elevated glass panel:   rgba(18, 23, 34, 0.78)
Soft border:            rgba(255, 255, 255, 0.06)
Primary text:           #F4F5F7
Secondary text:         #B6BBC7
Muted text:             #7D8493
Accent purple:          #9B4DFF / #A855F7
Accent purple glow:     rgba(155, 77, 255, 0.45)
Inactive controls:      #C5CAD5
Progress track:         rgba(255, 255, 255, 0.14)
```

### Typography

- Use a clean sans-serif font similar to Inter, SF Pro, or Helvetica Neue.
- Song title should be bold, large, and emotionally prominent.
- Artist names and metadata should be smaller, lighter, and slightly muted.
- Section labels should be uppercase, small, letter-spaced, and muted.

Suggested scale:

```text
Desktop page title / song title: 32–40 px, 700 weight
Mobile song title:               20–24 px, 700 weight
Section labels:                  11–12 px, 600 weight, uppercase, letter spacing 0.08em
Body text:                       14–16 px, 400–500 weight
Metadata:                        12–14 px, 400 weight
```

## 3. Layout DNA

### Desktop Layout

The desktop view is a wide music player dashboard with four main regions:

1. **Left sidebar navigation**
   - Fixed vertical sidebar, approximately 220–260 px wide.
   - Contains brand/logo, primary navigation, playlists, and user profile.
   - Darker than the main content to create hierarchy.

2. **Central album feature area**
   - Large square album artwork placed left-center.
   - Now-playing text and song details to the right of the artwork.
   - Album description and metadata below the artwork.
   - Prioritize the album art as the emotional anchor.

3. **Right queue column**
   - “Up Next” list with small thumbnails, song titles, artists, and durations.
   - Current/active upcoming track uses purple text or accent highlight.
   - Queue should feel compact but not cramped.

4. **Bottom playback bar**
   - Full-width persistent bar across the bottom.
   - Shows small album thumbnail and track info on the left.
   - Playback controls centered.
   - Volume/device controls on the right.
   - Use a subtle top border or shadow to separate from main content.

### Desktop Composition Rules

- Main window should have rounded corners and a soft drop shadow.
- Use a macOS-style top-left window control cluster if presenting as a desktop app mockup.
- Keep search and utility icons in the upper-right area.
- Allow generous negative space around the album artwork.
- Queue column should align vertically with the now-playing content.
- The bottom player should visually anchor the entire desktop interface.

Suggested desktop grid:

```text
App width:             1120–1280 px
Sidebar width:         220–250 px
Main content padding:  36–48 px
Album art size:        280–340 px square
Queue column width:    280–330 px
Bottom player height:  90–110 px
Border radius:         14–20 px for app shell, 8–14 px for internal cards
```

### Mobile Layout

The mobile view is a focused “Now Playing” screen with queue preview.

Top to bottom:

1. **Status/header area**
   - Time/status area at top.
   - Minimal navigation: down chevron/back control, “Now Playing” label, overflow menu.

2. **Album artwork**
   - Large square artwork, nearly full width.
   - Rounded corners.
   - Centered with comfortable horizontal margins.

3. **Track identity**
   - Song title and artist below artwork.
   - Favorite/heart icon aligned right.

4. **Progress area**
   - Purple progress line with small handle.
   - Current time left, total duration right.

5. **Playback controls**
   - Shuffle, previous, large play/pause, next, repeat.
   - Play/pause is the visual centerpiece: circular purple gradient button.

6. **Up Next drawer**
   - Rounded top sheet rising from the bottom.
   - Contains a compact queue preview.
   - Use a drag handle at the top of the sheet.

Suggested mobile dimensions:

```text
Screen width:          390–430 px
Outer device radius:   iPhone-style rounded hardware frame
Content horizontal pad: 20–24 px
Album art size:        260–300 px square
Primary control size:  56–64 px circle
Queue sheet radius:    18–24 px top corners
```

## 4. Component Guidelines

### App Shell

- Dark rounded rectangle with a subtle layered shadow.
- Desktop shell should resemble a native app window.
- Use a very subtle top-to-bottom gradient in the main content.

Example style:

```css
background: radial-gradient(circle at 35% 10%, rgba(120, 80, 180, 0.16), transparent 35%),
            linear-gradient(180deg, #141826 0%, #0B0F17 100%);
border: 1px solid rgba(255, 255, 255, 0.06);
box-shadow: 0 30px 80px rgba(0, 0, 0, 0.35);
```

### Sidebar

- Keep the sidebar darker and flatter than the content panel.
- Icons should be thin-line, monochrome, and softly muted.
- Active or important items can use the purple accent.
- Playlist thumbnails can be tiny album covers or colored squares.

Sidebar sections:

```text
Logo / brand
Primary navigation: Home, Search, Your Library
Playlist section: Create Playlist, playlist rows, Liked Songs
User profile row anchored at bottom
```

### Album Art

- Album art is the emotional centerpiece.
- Use a cinematic cover image with a dusk/night gradient, horizon, and small central figure silhouette.
- Corners should be softly rounded, not circular.
- Add a subtle shadow beneath the artwork.

Art direction:

```text
Atmospheric landscape, twilight gradient, reflective water or horizon, solitary figure, minimal typography, deep blue/purple tones.
```

### Now Playing Details

- Include a small uppercase “NOW PLAYING” label in purple.
- Song title should break naturally across two lines on desktop if needed.
- Artist name should be uppercase or spaced slightly for a polished feel.
- Place favorite/share/more actions below the artist.

### Queue / Up Next

Each queue item contains:

```text
Small square thumbnail
Song title
Artist name
Duration aligned right
Optional drag/reorder icon on mobile
```

Rules:

- Current or highlighted item uses purple text.
- Durations are muted and right-aligned.
- Keep thumbnails consistent, around 42–48 px desktop and 32–40 px mobile.
- The queue should be readable but visually secondary to the now-playing track.

### Playback Controls

Control hierarchy:

```text
Primary: play/pause
Secondary: previous, next
Tertiary: shuffle, repeat, volume, queue/device
```

Rules:

- Play/pause button should be purple and circular.
- Other icons should be thin, white or muted gray.
- Use consistent spacing between controls.
- Progress bar uses purple fill over a translucent gray track.
- Progress handle should be subtle and small.

### Search Bar

- Desktop search should sit near the top-right.
- Pill-shaped field with dark translucent background.
- Placeholder text should be muted.
- Magnifying glass icon on the left.

## 5. Interaction & State Notes

Design these states even if the mockup is static:

- Hover on sidebar items: slightly lighter background and brighter text.
- Active navigation item: purple icon or subtle purple indicator.
- Hover on queue item: translucent highlight row.
- Favorite active: filled purple heart.
- Playback active: purple pause/play button with soft glow.
- Progress drag: brighter handle and expanded hit area.
- Mobile queue expanded: bottom sheet grows upward with blurred/dimmed content behind it.

## 6. Spacing & Shape Rules

- Use rounded corners consistently.
- Avoid sharp, boxy edges except for tiny icons or dividers.
- Use generous outer padding and tighter internal row spacing.
- Prefer soft dividers over hard lines.
- Keep icon sizes consistent.

Suggested values:

```text
Large container radius: 18–24 px
Card/artwork radius:    10–16 px
Pill/search radius:     999 px
Mobile sheet radius:    22–28 px top corners
Desktop gap:            24–40 px
Mobile gap:             14–24 px
```

## 7. Motion Direction

For prototypes or implementation, use subtle motion:

- Album artwork fades/slides in softly.
- Queue rows stagger in by 30–60 ms.
- Play button scales down slightly on press.
- Progress handle grows slightly while dragging.
- Mobile bottom sheet uses spring motion.
- Avoid flashy animations; motion should feel premium and calm.

## 8. Content Model

Use realistic music metadata instead of placeholder lorem ipsum.

Example content:

```text
App name: Melodic
Current song: A Moment Apart
Artist: ODESZA
Album: A Moment Apart
Duration: 3:53
Album date: Sep 8, 2017
Album length: 49 min
Queue: Higher Ground, Line Of Sight, Late Night, Across The Room, Meridian, Sun Models
```

## 9. Accessibility Requirements

- Ensure text contrast remains readable on dark backgrounds.
- Do not rely on purple alone for active states; combine color with shape, weight, or position.
- Playback controls should have large click/tap targets.
- Mobile tap targets should be at least 44 x 44 px.
- Provide visible focus states for keyboard navigation.
- Use semantic labels for icons such as Play, Pause, Next Track, Favorite, Shuffle, Repeat, Volume, Queue.

## 10. Prompt for Recreating the Design

Use this prompt when asking another model or designer to recreate the mockup:

> Create a premium dark-mode music player UI mockup showing both desktop and mobile views side by side. The design should feel cinematic, modern, glassy, and calm. Use a nearly black navy background with subtle purple gradients and soft glassmorphism. The desktop app should have a left sidebar with logo, navigation, playlists, and user profile; a large central album artwork area; now-playing song information; a right-side “Up Next” queue; and a full-width bottom playback bar. The mobile view should show a focused now-playing screen with large album artwork, song title, artist, purple progress bar, large circular purple play/pause button, and a rounded bottom-sheet queue preview. Use clean sans-serif typography, muted gray secondary text, purple accent highlights, rounded corners, subtle shadows, and realistic music metadata. The album art should be atmospheric twilight landscape imagery with deep blue and purple tones.

## 11. Implementation Checklist

Before finalizing the UI, verify:

- [ ] Desktop and mobile feel like the same product.
- [ ] Album artwork is the primary visual anchor.
- [ ] Purple accent is used sparingly and consistently.
- [ ] The queue is readable but secondary.
- [ ] Playback controls are immediately recognizable.
- [ ] Mobile layout works with thumb-friendly spacing.
- [ ] Backgrounds have depth without becoming noisy.
- [ ] Text hierarchy is clear at a glance.
- [ ] All icons have consistent stroke weight.
- [ ] The design still works with different album artwork.
## 13. Library View Design DNA

Use this section to extend the original now-playing mockup into full **Library** views for desktop and mobile. The Library should preserve the same dark cinematic, glassy, premium music-player feel while shifting the content model from “emotional playback focus” to “organized collection management.”

The Library experience must support three primary content modes:

```text
Artists
Albums
Songs
```

Each mode should feel like part of the same system, not a separate product. Keep the same sidebar, search bar, purple active states, soft borders, glass panels, bottom playback bar, and mobile mini-player used in the original mockup.

---

### 13.1 Library Product Feel

The Library view should feel:

```text
organized, searchable, premium, technical when needed, calm, fast, dense but not cluttered
```

The design should support both casual browsing and power-user metadata inspection.

The key balance:

```text
Beautiful enough for music discovery.
Structured enough for managing a local music library.
Detailed enough for codec, bitrate, sample rate, filepath, and file metadata.
```

Avoid making the Library feel like a spreadsheet-only utility. The UI may include tables, but it should still feel like a music app.

---

### 13.2 Desktop Library Shell

The desktop Library screen keeps the same overall shell as the now-playing view:

```text
Left sidebar
Top navigation/search area
Main library content area
Optional right-side detail inspector
Persistent bottom playback bar
```

Recommended desktop composition:

```text
Sidebar width:          220–250 px
Main content padding:   28–40 px
Content max width:      flexible
Right inspector width:  260–320 px
Bottom player height:   90–110 px
```

The active sidebar item should be **Your Library**, highlighted with the same purple accent used in the original mockup.

The top area should include:

```text
Back / forward controls
Search field: “Search songs, artists, albums”
Notification or utility icon
Page label: “Your Library”
Large mode title: Artists / Albums / Songs
Segmented view switcher: Artists | Albums | Songs
Sort controls
Order controls
View controls
Column controls
Filter controls where relevant
```

The Library content should sit above the persistent bottom player and should never visually collide with it.

---

### 13.3 Library Navigation Tabs

Use a segmented control for switching between library modes.

```text
Artists | Albums | Songs
```

Design rules:

- Active tab uses a purple translucent pill.
- Inactive tabs are muted, with thin divider lines.
- Keep the control compact and aligned near the page title.
- On desktop, place it below or beside the page heading.
- On mobile, place it directly below the “Library” header.

Example styling:

```css
background: rgba(255, 255, 255, 0.035);
border: 1px solid rgba(255, 255, 255, 0.06);
border-radius: 10px;
```

Active tab:

```css
background: linear-gradient(180deg, rgba(155, 77, 255, 0.35), rgba(155, 77, 255, 0.18));
border: 1px solid rgba(155, 77, 255, 0.45);
color: #F4F5F7;
```

---

## 14. Artists Library View

The Artists view is the most directory-like view. It should provide a clean overview of all artists while still allowing quick inspection of artist-level stats.

### 14.1 Desktop Artists View

Preferred layout:

```text
Main content: table/list of artists
Right panel: selected artist summary
Bottom: persistent playback bar
```

Artist table columns:

```text
Artist
Genre
Albums
Songs
Total Duration
Last Played
Favorite
More Actions
```

Each artist row should include:

```text
Circular or softly rounded artist image
Artist name
Optional genre
Album count
Song count
Total duration
Last played date
Favorite heart
Overflow menu
```

Recommended row height:

```text
56–68 px
```

The selected row may use a subtle purple highlight:

```css
background: linear-gradient(90deg, rgba(155, 77, 255, 0.22), rgba(255,255,255,0.02));
border-left: 2px solid #9B4DFF;
```

### 14.2 Desktop Artist Detail Inspector

When an artist is selected, show a right-side inspector card.

Content:

```text
Artist image
Artist name
Primary genre
Follow/following button
Albums count
Songs count
Total duration
Last played
Top songs list
View all link
```

The inspector should be glassy and slightly elevated, matching the original “Up Next” panel tone.

### 14.3 Mobile Artists View

Mobile uses a compact list.

Top to bottom:

```text
Status bar
Header: Library
Left utility icon / right filter icon
Segmented tabs: Artists | Albums | Songs
Sort dropdown row
Artist list
Mini-player
Bottom navigation
```

Artist list item:

```text
Circular artist image
Artist name
Genre · album count
Song count · total duration
Favorite heart
Chevron or overflow
```

Rows should be tappable and spacious enough for thumb interaction.

Recommended mobile row height:

```text
64–76 px
```

---

## 15. Albums Library View

The Albums view should feel more visual and artwork-driven than Artists or Songs.

### 15.1 Desktop Albums View

Preferred layout:

```text
Main content: album grid
Right panel: selected album metadata inspector
```

Album grid card contains:

```text
Album artwork
Favorite heart overlay
Album title
Artist name
Year · track count · duration
Genre chip
Codec chip
```

Recommended desktop grid:

```text
Card width:       150–190 px
Artwork ratio:    1:1
Grid gap:         18–24 px
Card radius:      12–16 px
```

Selected album card:

```text
Purple border
Slight glow
Darker elevated background
```

Example selected card style:

```css
border: 1px solid rgba(155, 77, 255, 0.75);
box-shadow: 0 0 0 1px rgba(155, 77, 255, 0.18), 0 18px 40px rgba(0,0,0,0.32);
```

### 15.2 Album Detail Inspector

The album inspector is the main place for deeper metadata.

Content:

```text
Large album cover
Album title
Artist
Favorite heart
Release date
Genre
Track count
Total duration
Codec
Bitrate / quality
Sample rate
File size
Location
File path
Top tracks
View all link
Copy path icon
```

For local library workflows, include both friendly and technical metadata.

Example metadata fields:

```text
Released: Sep 8, 2017
Genre: Electronic
Tracks: 16
Total Duration: 49 min
Codec: FLAC
Quality: Lossless
Sample Rate: 44.1 kHz
File Size: 348.6 MB
Location: Local Library
File Path: /Music/ODESZA/A Moment Apart/
```

Long file paths should truncate in the middle or end, with the full path available on hover, tap, or copy.

### 15.3 Desktop Album View Controls

Albums should support multiple view modes:

```text
Grid
Compact grid
List
```

Recommended controls:

```text
Sort by: Recently Added / Title / Artist / Year / Genre / Duration
Order: Asc / Desc
View: Grid / List
Columns
```

### 15.4 Mobile Albums View

Mobile albums should use a two-column grid.

Mobile album card:

```text
Album artwork
Album title
Artist
Year
Track count · duration
Favorite heart
```

Selected album behavior:

```text
A bottom sheet slides up with album metadata.
```

Mobile album metadata sheet contains:

```text
Mini album cover
Album title
Artist
Genre
Track count
Duration
Codec
Quality
Sample rate
File size
Location
File path
Copy path icon
```

The sheet should have a rounded top edge and a small drag handle, consistent with the original Up Next drawer.

---

## 16. Songs Library View

The Songs view is the most metadata-heavy and should support table-style inspection without losing the music-app feel.

### 16.1 Desktop Songs View

Preferred layout:

```text
Main content: dense song table
Right panel: selected song metadata inspector
```

Song table columns:

```text
Title
Artist
Album
Duration
Codec
Bitrate
Sample Rate
File Type
Date Added
File Path
More Actions
```

Optional columns:

```text
Track #
Disc #
Genre
Year
Play Count
Last Played
File Size
Channels
Bit Depth
BPM
Rating
Favorite
```

Song row content:

```text
Small square thumbnail
Song title
Artist subtitle if needed
Album
Duration
Codec chip or text
Bitrate / quality
Sample rate
File type
Date added
Truncated file path
Overflow menu
```

Selected or currently playing row:

```text
Purple left indicator
Soft purple row highlight
Tiny equalizer icon or waveform indicator
```

Currently playing example:

```css
border-left: 2px solid #9B4DFF;
background: rgba(155, 77, 255, 0.18);
```

### 16.2 Song Metadata Inspector

The song inspector should expose detailed technical metadata.

Content:

```text
Album art
Song title
Artist
Album
Favorite
Duration
Codec
Bitrate / quality
Sample rate
Channels
File size
Date added
Play count
File path
Reveal in Finder
Add to Playlist
View Album
```

Example metadata:

```text
Duration: 3:53
Codec: FLAC
Bitrate: Lossless
Sample Rate: 44.1 kHz
Channels: 2 (Stereo)
File Size: 28.6 MB
Date Added: May 12, 2024, 10:14 AM
Play Count: 24
File Path: /Music/ODESZA/A Moment Apart/01 A Moment Apart.flac
```

For desktop, show action rows at the bottom of the inspector:

```text
Reveal in Finder
Add to Playlist
View Album
Copy File Path
```

### 16.3 Mobile Songs View

Mobile Songs should use a stacked list rather than a wide table.

Song list item:

```text
Small album thumbnail
Song title
Artist
Duration aligned right
Metadata chips: codec, sample rate, quality
Overflow menu
```

Example mobile row:

```text
[A Moment Apart thumbnail]  A Moment Apart        3:53
                            ODESZA
                            FLAC · 44.1 kHz · Lossless
```

Selected-song metadata should appear in a bottom sheet.

Mobile metadata sheet content:

```text
Small album thumbnail
Song title
Artist
Close icon
Metadata grid
Full filepath
Mini-player beneath or integrated below
```

The mobile Songs screen should also keep:

```text
Mini-player above bottom navigation
Bottom navigation with Library active
```

---

## 17. Sorting, Filtering, and Column Controls

Library views should include sorting and filtering while keeping controls visually minimal.

### 17.1 Sort Controls

Use compact glass dropdowns.

Artists sorting:

```text
Name
Recently Played
Most Played
Albums Count
Songs Count
Duration
Genre
```

Albums sorting:

```text
Recently Added
Title
Artist
Release Year
Genre
Track Count
Duration
Codec
File Size
```

Songs sorting:

```text
Title
Artist
Album
Date Added
Duration
Codec
Bitrate
Sample Rate
File Type
File Size
Play Count
Last Played
```

Order control:

```text
A–Z
Z–A
Ascending
Descending
Newest First
Oldest First
```

### 17.2 Filter Controls

Filters should be available through a funnel icon or compact dropdown.

Useful filters:

```text
Genre
Artist
Album
Codec
File Type
Quality
Sample Rate
Bitrate
Location
Date Added
Favorites
Downloaded / Local Only
Missing Metadata
Duplicate Files
```

Mobile filters should open as a bottom sheet.

### 17.3 Column Controls

Desktop tables should include a “Columns” control for metadata visibility.

Column picker behavior:

```text
Popover opens from Columns button
Checkbox list of available metadata fields
User can show/hide technical fields
Preserve essential columns by default
```

Default columns:

```text
Artists: Artist, Genre, Albums, Songs, Duration, Last Played
Albums: Artwork, Album, Artist, Year, Tracks, Duration, Genre, Codec
Songs: Title, Artist, Album, Duration, Codec, Bitrate, Sample Rate, File Type, Date Added
```

Advanced optional columns:

```text
File Path
File Size
Channels
Bit Depth
BPM
Play Count
Last Played
Date Modified
Location
```

---

## 18. Metadata Presentation Rules

Technical metadata should be useful but visually quiet.

### 18.1 Metadata Hierarchy

Prioritize human-readable music metadata first:

```text
Title
Artist
Album
Artwork
Duration
Genre
Year
```

Then technical metadata:

```text
Codec
Bitrate
Sample Rate
File Type
File Size
Channels
Bit Depth
File Path
Location
```

### 18.2 Metadata Chips

Use small pill chips for quick technical metadata.

Examples:

```text
FLAC
AAC
MP3
Lossless
320 kbps
44.1 kHz
48 kHz
Local
```

Chip style:

```css
font-size: 11px;
border-radius: 6px;
padding: 3px 7px;
background: rgba(255, 255, 255, 0.055);
border: 1px solid rgba(255, 255, 255, 0.06);
color: #B6BBC7;
```

Accent chips may use purple only when selected or important.

### 18.3 File Paths

File paths must be treated carefully because they can become visually noisy.

Rules:

```text
Use monospaced or slightly condensed text only in inspectors, not primary lists.
Truncate long paths in tables.
Show full path in metadata sheets or tooltips.
Provide copy-path icon.
Provide “Reveal in Finder” action on desktop.
```

Example truncation:

```text
/Music/ODESZA/A Moment Apart/...
```

---

## 19. Mobile Library Interaction Model

Mobile Library views should favor progressive disclosure.

Do not attempt to show all metadata in the list. Instead:

```text
List/Grid shows essential identity and 2–3 key metadata chips.
Tap opens detail bottom sheet.
Filter icon opens filter sheet.
Sort dropdown remains compact near top.
Mini-player remains persistent.
Bottom navigation remains visible.
```

Mobile bottom sheets should use:

```text
Rounded top corners: 20–24 px
Drag handle
Glass/dark panel background
Soft top border
Metadata grid
Copy/reveal actions where applicable
```

Bottom sheet metadata grid:

```text
Two columns where space allows
Label above value or muted label beside value
Small text, clear hierarchy
```

Example mobile metadata grid:

```text
Duration     3:53
Codec        FLAC
Quality      Lossless
Sample Rate  44.1 kHz
File Size    28.6 MB
Location     Local Library
```

---

## 20. Library Empty, Loading, and Error States

### Empty States

Use calm, helpful language.

Examples:

```text
No artists found
No albums match this filter
No songs with missing metadata
```

Include a subtle icon and optional action:

```text
Import Music
Clear Filters
Scan Library
```

### Loading States

Use skeleton rows/cards with soft shimmer or static placeholders.

Desktop:

```text
Skeleton table rows
Skeleton album cards
Inspector placeholder
```

Mobile:

```text
Skeleton list rows
Skeleton grid cards
Mini-player remains stable
```

### Error States

For library scanning or local file access errors:

```text
Some files could not be read
File path unavailable
Codec metadata missing
```

Keep errors muted but actionable.

---

## 21. Library Accessibility and Usability

- All metadata text must remain readable against dark panels.
- Do not rely on purple alone to indicate selected state; also use borders, row highlights, icons, or labels.
- Touch targets on mobile should be at least 44 px tall.
- Tables should support keyboard navigation on desktop.
- Column headers should clearly indicate active sort direction.
- Long file paths should be copyable.
- Favorite icons need accessible labels.
- Overflow menus should include text labels, not icon-only mystery actions.

---

## 22. Library Implementation Notes

### Data Model Fields

The UI should be able to represent these entities.

Artist:

```text
id
name
image
genre
albumCount
songCount
totalDuration
lastPlayed
isFavorite
topSongs
```

Album:

```text
id
title
artist
artwork
year
releaseDate
genre
trackCount
duration
codec
quality
sampleRate
bitrate
fileSize
location
filePath
isFavorite
tracks
```

Song:

```text
id
title
artist
album
artwork
duration
codec
bitrate
quality
sampleRate
channels
bitDepth
fileType
fileSize
dateAdded
dateModified
playCount
lastPlayed
filePath
location
isFavorite
```

### Recommended Desktop View Defaults

```text
Artists: table with right artist inspector
Albums: grid with right album inspector
Songs: table with right song inspector
```

### Recommended Mobile View Defaults

```text
Artists: vertical list
Albums: two-column grid
Songs: vertical list with metadata chips
```

### Persistent Playback Behavior

The Library must always retain playback continuity:

```text
Desktop: full-width bottom player remains visible.
Mobile: compact mini-player remains above bottom navigation.
Current playing item can be highlighted in library lists.
```

The now-playing track should use the same title, artwork, progress, favorite, and play/pause language as the original mockup.

---

## 23. Library Mockup Prompt Addendum

Use this addendum when generating additional mockups from this design DNA:

```text
Create a premium dark-mode music library UI mockup using the same design DNA as the original Melodic music player. Show both desktop and mobile views side by side. Preserve the dark glassmorphism shell, navy-black gradients, purple accent states, soft borders, cinematic album art, persistent playback controls, and mobile mini-player.

The Library screen should include tabs for Artists, Albums, and Songs. Create variants for each tab.

For Artists, show a desktop artist table with columns for artist, genre, albums, songs, total duration, last played, favorite, and actions. Include a right-side selected artist inspector with artist image, following button, album count, song count, total duration, last played, and top songs. On mobile, show a compact artist list with image, genre, album count, song count, duration, favorite heart, and sort/filter controls.

For Albums, show a desktop album grid with album artwork cards, favorite overlays, title, artist, year, track count, duration, genre chip, and codec chip. Include a right-side album metadata inspector with release date, genre, tracks, duration, codec, quality, sample rate, file size, location, and file path. On mobile, show a two-column album grid and a bottom metadata sheet for the selected album.

For Songs, show a desktop dense song table with columns for title, artist, album, duration, codec, bitrate, sample rate, file type, date added, and file path. Include a selected song inspector with album art, title, artist, album, duration, codec, bitrate, sample rate, channels, file size, date added, play count, full filepath, and actions like Reveal in Finder, Add to Playlist, and View Album. On mobile, show a song list with metadata chips and a bottom sheet with detailed codec and filepath metadata.

Use compact sorting controls such as Sort by, Order, View, Filter, and Columns. Keep the aesthetic cinematic, premium, minimal, and technically capable without looking like enterprise software.
```
