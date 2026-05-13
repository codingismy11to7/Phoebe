# Music Player Light Mode Design DNA

Use this document as an implementation and creative brief for recreating the light mode desktop and mobile music player mockup. It extends the original dark, cinematic Melodic/Phoebe music player design into a bright, premium, editorial-style interface while preserving the same layout, spacing, hierarchy, and purple playback identity.

---

## 1. Core Product Feel

The light mode should feel:

```text
clean, premium, spacious, calm, editorial, high-fidelity, modern, native-app-like
```

It should look like the same product as the dark mode mockups, not a separate redesign. The light version keeps the same information architecture and component behavior, but swaps the atmospheric dark glass aesthetic for a soft white, warm-gray, high-clarity interface.

The UI should feel suitable for:

```text
daytime listening
desktop music library management
mobile now-playing playback
premium local music playback
audiophile metadata workflows
```

Avoid harsh pure white surfaces. Use very soft grays, subtle borders, and restrained shadows.

---

## 2. Light Mode Visual Direction

### Theme

- Primary theme: soft white and warm gray surfaces.
- Accent color remains purple.
- Album artwork remains vivid and cinematic.
- Text should be crisp and dark, but not pure black.
- Controls should feel lightweight and native.
- Panels should separate through soft borders and subtle shadows rather than dark contrast.

### Design Keywords

```text
soft white
warm gray
minimal
premium
quiet contrast
purple accent
editorial spacing
native desktop app
mobile parity
album-art focused
```

---

## 3. Light Mode Color Palette

Use these values as implementation tokens.

```text
App background:              #F3F4F7
Main surface:                #FFFFFF
Sidebar surface:             #F7F8FA
Secondary panel surface:     #FAFAFC
Elevated card surface:       #FFFFFF
Bottom player surface:       rgba(255, 255, 255, 0.92)

Primary text:                #181B22
Secondary text:              #4D5563
Muted text:                  #7A8190
Subtle text:                 #9AA1AE

Hairline border:             rgba(20, 24, 32, 0.08)
Soft divider:                rgba(20, 24, 32, 0.06)
Hover surface:               rgba(20, 24, 32, 0.04)
Selected surface:            rgba(155, 77, 255, 0.10)

Accent purple:               #8B3DFF
Accent purple hover:         #7C2CF2
Accent purple soft:          rgba(139, 61, 255, 0.12)
Accent purple border:        rgba(139, 61, 255, 0.28)

Progress track:              rgba(20, 24, 32, 0.12)
Progress fill:               #8B3DFF
Icon default:                #4B5260
Icon muted:                  #8A91A0
Icon active:                 #8B3DFF

Input background:            #F1F2F5
Input border:                rgba(20, 24, 32, 0.08)
Input focused border:        rgba(139, 61, 255, 0.45)

Shadow color:                rgba(20, 24, 32, 0.08)
Strong shadow:               rgba(20, 24, 32, 0.14)
```

### Avoid

```text
Pure black text:             #000000
Pure white-only layout:      flat #FFFFFF everywhere
Heavy gray borders
Bright neon glow
Dark-mode glass panels copied directly into light mode
```

---

## 4. Typography

Use the same typography as the dark mode system.

Recommended fonts:

```text
Inter
SF Pro
Helvetica Neue
system-ui
```

Suggested type scale:

```text
Desktop title / song title:       30–38 px, 700 weight
Mobile song title:                20–24 px, 700 weight
Section labels:                   11–12 px, 600 weight, uppercase, letter spacing 0.08em
Body text:                        14–16 px, 400–500 weight
Metadata text:                    12–14 px, 400 weight
Sidebar labels:                   14–15 px, 500 weight
```

Text color rules:

```text
Primary labels and titles:        #181B22
Artist names and metadata:        #4D5563
Secondary actions and timestamps: #7A8190
Disabled text:                    #B5BAC4
Purple active states:             #8B3DFF
```

---

## 5. Desktop Light Mode Layout

The desktop light mode retains the original four-region structure.

```text
Left sidebar navigation
Central album / now-playing content
Right Up Next queue
Bottom playback bar
```

### Desktop Shell

The app should sit on a very light gray background with a large rounded white window.

```text
Canvas background:        #F3F4F7
App shell background:     #FFFFFF
App shell radius:         18–24 px
App shell border:         1 px rgba(20, 24, 32, 0.06)
App shell shadow:         0 24px 70px rgba(20, 24, 32, 0.12)
```

The shell should still feel like a native desktop app, including macOS-style red/yellow/green window controls when presenting a mockup.

### Desktop Grid

```text
App width:                1120–1280 px
App height:               720–820 px
Sidebar width:            220–250 px
Main content padding:     36–48 px
Album art size:           280–340 px square
Queue column width:       280–330 px
Bottom player height:     90–110 px
```

### Layout Behavior

- The sidebar stays fixed on the left.
- The album artwork remains the emotional anchor.
- Now-playing text sits beside the album artwork.
- The queue remains on the right with clear vertical rhythm.
- The bottom player spans the full width of the app window.
- The player bar uses a translucent white surface and a soft top border.

---

## 6. Sidebar Light Mode

The sidebar should look quiet, structured, and slightly separated from the main content.

```text
Sidebar background:       #F7F8FA
Sidebar border-right:     1 px rgba(20, 24, 32, 0.06)
Sidebar padding:          28–32 px top, 24 px horizontal
```

Sidebar sections:

```text
Logo / brand
Primary navigation
Playlists
User profile
Mini now-playing item at bottom
```

Primary navigation:

```text
Home
Search
Your Library
Browse
Radio
```

Active state:

```text
Icon color:               #8B3DFF
Text color:               #8B3DFF
Optional left indicator:  2 px purple vertical bar
Background:               rgba(139, 61, 255, 0.08)
```

Playlist thumbnails should remain colorful and album-art-based, with small rounded corners.

---

## 7. Desktop Now Playing Content

### Album Artwork

Album artwork remains vivid and cinematic. It should not be washed out by the light mode.

```text
Artwork size:             280–340 px
Radius:                   8–12 px
Shadow:                   0 14px 32px rgba(20, 24, 32, 0.14)
```

### Track Identity

Use the same hierarchy as dark mode.

```text
NOW PLAYING label:        uppercase, purple, 11–12 px
Song title:               large, bold, dark
Artist name:              uppercase or slightly spaced, muted
Actions:                  favorite, share, more
```

Example:

```text
NOW PLAYING
A Moment Apart
ODESZA
```

Favorite heart should be purple when active.

### Album Description

Use small uppercase section labels and readable body text.

```text
ABOUT THE ALBUM
A Moment Apart is the third studio album by American electronic music duo ODESZA.

Sep 8, 2017     49 min
```

Metadata icons should be simple line icons in muted gray.

---

## 8. Up Next Queue in Light Mode

The Up Next queue should feel lightweight and readable.

Queue item structure:

```text
Small album thumbnail
Song title
Artist name
Duration
Optional reorder icon
```

Queue styling:

```text
Row height:               52–60 px
Thumbnail size:           40–46 px
Title color:              #181B22
Artist color:             #7A8190
Duration color:           #7A8190
Active title color:       #8B3DFF
```

The queue should not sit inside a heavy dark card. It can be directly on the white surface or inside a very subtle panel.

```text
Panel background:         transparent or #FFFFFF
Panel border-left:        optional 1 px rgba(20, 24, 32, 0.06)
```

### Collapsible Queue States

Use the same behavior as the dark-mode expandable/collapsible queue mockups.

Expanded desktop queue:

```text
Full queue column visible
Header: UP NEXT
Optional Clear action
Rows include thumbnails, title, artist, duration
Chevron-up or collapse icon in top-right
```

Collapsed desktop queue:

```text
Narrow vertical rail on the right
Header still reads UP NEXT
Only stacked thumbnails are visible
Bottom badge shows remaining count
Chevron/down or expand icon restores full queue
```

Expanded mobile queue:

```text
Bottom sheet shows several upcoming tracks
Rounded top corners
Drag handle
Header: UP NEXT
Chevron indicates collapsible state
Rows include thumbnail, title, artist, duration, reorder icon
```

Collapsed mobile queue:

```text
Small bottom sheet preview
Shows “Next: [song title]”
Shows one thumbnail and artist
Chevron expands
Leaves more space for artwork and playback controls
```

---

## 9. Bottom Playback Bar in Light Mode

The bottom player must remain persistent and visually anchored.

```text
Height:                   90–110 px desktop
Surface:                  rgba(255, 255, 255, 0.92)
Border top:               1 px rgba(20, 24, 32, 0.06)
Backdrop blur:            16–24 px when supported
```

Desktop player layout:

```text
Left: current track thumbnail, title, artist, favorite
Center: shuffle, previous, play/pause, next, repeat
Below center or inline: progress bar and timestamps
Right: volume, output, queue/device icons
```

Play/pause button:

```text
Shape:                    circle
Size:                     52–60 px
Background:               #8B3DFF
Icon:                     #FFFFFF
Shadow:                   0 10px 24px rgba(139, 61, 255, 0.28)
```

Progress bar:

```text
Track:                    rgba(20, 24, 32, 0.12)
Fill:                     #8B3DFF
Height:                   3–4 px
Handle:                   8–10 px circle
```

---

## 10. Mobile Light Mode Layout

The mobile light mode should feel native, clean, and touch-friendly.

Top to bottom:

```text
Status/header area
Album artwork
Track identity
Progress area
Playback controls
Up Next bottom sheet
Mini-player when in library/search/settings views
Bottom navigation where applicable
```

### Mobile Screen

```text
Screen background:        #FFFFFF
Device frame:             optional silver/light hardware frame in mockups
Content padding:          20–24 px
Album art size:           260–300 px square
Primary control size:     56–64 px circle
Bottom sheet radius:      20–24 px top corners
```

### Mobile Header

```text
Left: back/down chevron
Center: NOW PLAYING / page title
Right: overflow menu
```

Header text:

```text
Label color:              #181B22
Secondary label:          #7A8190
Icons:                    #4B5260
```

### Mobile Now Playing

```text
Artwork:                  large, centered, 8–12 px radius
Song title:               20–24 px bold
Artist:                   14–15 px muted
Favorite heart:           purple, aligned right
```

Mobile controls:

```text
Shuffle
Previous
Large purple play/pause
Next
Repeat
```

---

## 11. Mobile Up Next Sheet in Light Mode

The Up Next drawer should look like a raised white card.

```text
Background:               #FFFFFF
Top border:               1 px rgba(20, 24, 32, 0.06)
Shadow:                   0 -18px 40px rgba(20, 24, 32, 0.10)
Radius:                   20–24 px top corners
Drag handle:              rgba(20, 24, 32, 0.14)
```

Expanded sheet:

```text
UP NEXT label
3–5 visible queue rows
Thumbnail, title, artist, duration, reorder icon
```

Collapsed sheet:

```text
UP NEXT label
One compact “Next:” row
Chevron to expand
```

The sheet should never obscure the main play/pause controls unless intentionally expanded.

---

## 12. Search Page Light Mode

Use this design DNA for the light mode search page.

### Desktop Search

Desktop search layout:

```text
Sidebar
Page title: Search
Subtitle: Find your favorite music
Large focused search input at top
Top result card
Songs table
Albums row
Artists row
Recent searches panel
Suggested searches panel
Bottom playback bar
```

Search input:

```text
Background:               #F1F2F5
Border:                   1 px rgba(20, 24, 32, 0.08)
Focused border:           1 px rgba(139, 61, 255, 0.45)
Radius:                   999 px
Height:                   42–48 px
```

Top result card:

```text
Background:               #FFFFFF
Border:                   1 px rgba(20, 24, 32, 0.06)
Shadow:                   0 12px 32px rgba(20, 24, 32, 0.08)
Radius:                   14–18 px
```

Song search results should use row-based layouts with subtle hover and selected states.

### Mobile Search

Mobile search layout:

```text
Header: Search
Search input
Top result card
Songs section
Albums horizontal row
Artists section
Mini-player
Bottom navigation
```

Mobile search input should be full width and pill-shaped.

---

## 13. Settings Page Light Mode

Settings should preserve the same structure as dark mode but use bright cards and quiet dividers.

### Desktop Settings

Desktop settings layout:

```text
Sidebar
Page title: Settings
Subtitle: Customize your listening experience
Settings category sidebar
Account card
Library storage card
Main settings cards
Bottom playback bar
```

Category list:

```text
Account
Playback
Audio Quality
Library
Downloads
Appearance
Notifications
Advanced
```

Active category:

```text
Background:               rgba(139, 61, 255, 0.10)
Text/icon:                #8B3DFF
Border:                   1 px rgba(139, 61, 255, 0.18)
```

Cards:

```text
Background:               #FFFFFF
Border:                   1 px rgba(20, 24, 32, 0.06)
Radius:                   14–18 px
Shadow:                   0 10px 28px rgba(20, 24, 32, 0.06)
```

Controls:

```text
Toggle active:            #8B3DFF
Toggle inactive track:    rgba(20, 24, 32, 0.16)
Slider active:            #8B3DFF
Dropdown background:      #F7F8FA
```

### Mobile Settings

Mobile settings layout:

```text
Header with back arrow and Settings title
Grouped sections
Playback
Audio Quality
Library
Downloads
Mini-player
Bottom navigation if used
```

Rows should be 48–56 px tall and grouped into rounded white cards.

---

## 14. Metadata Edit Light Mode

Use this when implementing metadata editing in light mode.

### Desktop Metadata Edit

The desktop metadata editor should appear as an elevated modal over the Songs library view.

Modal structure:

```text
Header: Edit Song Metadata
Close icon
Left artwork panel
Editable metadata form
Read-only technical metadata section
Footer actions
```

Editable fields:

```text
Title
Artist
Album
Album Artist
Track Number
Disc Number
Genre
Year
Composer
Comments
Artwork
```

Read-only technical metadata:

```text
Codec
Quality
Sample Rate
File Type
File Size
File Path
```

Modal styling:

```text
Background:               #FFFFFF
Border:                   1 px rgba(20, 24, 32, 0.08)
Radius:                   16–20 px
Shadow:                   0 30px 80px rgba(20, 24, 32, 0.16)
Backdrop:                 rgba(243, 244, 247, 0.64)
```

Form fields:

```text
Background:               #F7F8FA
Border:                   1 px rgba(20, 24, 32, 0.08)
Focused border:           rgba(139, 61, 255, 0.45)
Radius:                   8–10 px
Height:                   38–44 px
```

Footer buttons:

```text
Cancel:                   secondary ghost
Revert:                   secondary outlined
Save Changes:             primary purple
```

### Mobile Metadata Edit

Mobile metadata editing should be a full-screen page, not a tiny modal.

Structure:

```text
Top bar: Cancel | Edit Metadata | Save
Artwork + song identity summary
Editable fields
Technical Info accordion/card
Sticky Save Changes button
```

Keep technical metadata read-only and visually separated from editable tags.

---

## 15. Library Views in Light Mode

The Library views preserve the same behavior as the dark-mode Artists, Albums, and Songs screens.

### Artists

Desktop:

```text
Artist table
Right selected artist inspector
Columns: Artist, Genre, Albums, Songs, Duration, Last Played, Favorite
```

Mobile:

```text
Artist list
Sort dropdown
Filter icon
Mini-player
Bottom navigation
```

### Albums

Desktop:

```text
Album grid
Right album metadata inspector
Cards include artwork, title, artist, year, track count, duration, genre chip, codec chip
```

Mobile:

```text
Two-column album grid
Selected album bottom sheet with metadata
```

### Songs

Desktop:

```text
Dense song table
Right song metadata inspector
Columns: Title, Artist, Album, Duration, Codec, Bitrate, Sample Rate, File Type, Date Added, File Path
```

Mobile:

```text
Song list with metadata chips
Selected song bottom sheet
```

Light mode metadata chips:

```text
Background:               rgba(20, 24, 32, 0.045)
Border:                   rgba(20, 24, 32, 0.07)
Text:                     #4D5563
Selected chip background: rgba(139, 61, 255, 0.12)
Selected chip text:       #8B3DFF
```

---

## 16. Component Tokens

### Buttons

Primary button:

```css
background: #8B3DFF;
color: #FFFFFF;
border-radius: 10px;
box-shadow: 0 8px 20px rgba(139, 61, 255, 0.22);
```

Secondary button:

```css
background: #F7F8FA;
color: #181B22;
border: 1px solid rgba(20, 24, 32, 0.08);
border-radius: 10px;
```

Ghost button:

```css
background: transparent;
color: #4D5563;
border-radius: 10px;
```

Icon button:

```css
background: rgba(20, 24, 32, 0.035);
border: 1px solid rgba(20, 24, 32, 0.06);
color: #4B5260;
border-radius: 10px;
```

### Cards

```css
background: #FFFFFF;
border: 1px solid rgba(20, 24, 32, 0.06);
border-radius: 16px;
box-shadow: 0 10px 28px rgba(20, 24, 32, 0.06);
```

### Inputs

```css
background: #F1F2F5;
border: 1px solid rgba(20, 24, 32, 0.08);
border-radius: 999px;
color: #181B22;
```

Focused input:

```css
border-color: rgba(139, 61, 255, 0.45);
box-shadow: 0 0 0 3px rgba(139, 61, 255, 0.10);
```

### Tables

```text
Header text:              uppercase, muted, 11–12 px
Row height:               52–64 px
Row border:               1 px rgba(20, 24, 32, 0.05)
Hover:                    rgba(20, 24, 32, 0.035)
Selected:                 rgba(139, 61, 255, 0.10)
Current playing marker:   2 px purple left border
```

### Toggles

```text
Active track:             #8B3DFF
Active thumb:             #FFFFFF
Inactive track:           rgba(20, 24, 32, 0.16)
Inactive thumb:           #FFFFFF
```

### Sliders

```text
Track:                    rgba(20, 24, 32, 0.12)
Fill:                     #8B3DFF
Thumb:                    #8B3DFF
```

---

## 17. Light Mode Accessibility Rules

- Text contrast must remain strong on white and gray surfaces.
- Do not use very pale purple text for important labels.
- Purple should indicate activity, selection, or primary actions.
- Use shape, weight, or borders in addition to color for selected states.
- Touch targets on mobile should be at least 44 px.
- File paths and technical metadata should remain readable at small sizes.
- Do not place light-gray text on white without sufficient contrast.
- Album artwork should retain enough shadow or border to separate from the white background.

---

## 18. Implementation Strategy

Implement light mode using theme tokens rather than separate component logic.

Example token structure:

```ts
const lightTheme = {
  background: "#F3F4F7",
  surface: "#FFFFFF",
  surfaceMuted: "#F7F8FA",
  panel: "#FAFAFC",
  border: "rgba(20, 24, 32, 0.08)",
  divider: "rgba(20, 24, 32, 0.06)",
  textPrimary: "#181B22",
  textSecondary: "#4D5563",
  textMuted: "#7A8190",
  accent: "#8B3DFF",
  accentHover: "#7C2CF2",
  accentSoft: "rgba(139, 61, 255, 0.12)",
  progressTrack: "rgba(20, 24, 32, 0.12)",
  shadow: "rgba(20, 24, 32, 0.08)"
};
```

Theme switching should update:

```text
Backgrounds
Panel surfaces
Borders
Text colors
Icon colors
Input surfaces
Table rows
Bottom player
Mobile sheets
Settings controls
Metadata chips
```

It should not alter:

```text
Layout structure
Navigation structure
Playback behavior
Queue behavior
Metadata fields
Library data model
Search result grouping
```

---

## 19. Light Mode Mockup Prompt

Use this prompt to generate additional light-mode mockups in the same style:

```text
Create a premium light-mode music player UI mockup for both desktop and mobile using the same layout and product DNA as the dark Melodic/Phoebe music player. Use a soft white and warm-gray interface, subtle borders, quiet shadows, vivid album artwork, and a restrained purple accent for active states and playback controls.

Desktop should show a native app window with a left sidebar, central now-playing album artwork and track details, a right Up Next queue, and a persistent bottom playback bar. Use clean typography, soft dividers, and a purple circular play/pause button.

Mobile should show the matching now-playing screen with a white background, large album artwork, song title and artist, purple progress bar, centered purple play/pause button, and a raised white Up Next bottom sheet. Keep the design minimal, calm, modern, and premium. Avoid dark panels, neon glow, heavy gradients, or overly glassy effects.
```

---

## 20. One-Line Design Summary

```text
A bright, premium, native-feeling music player theme that preserves the dark mode’s cinematic structure and purple identity while using soft white surfaces, warm-gray panels, subtle shadows, and clear audiophile metadata presentation.
```
