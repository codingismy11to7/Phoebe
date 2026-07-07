# Minimalistic Desktop And Web Mockups

These generated mockups explore a warmer, quieter take on the Phoebe
minimalistic direction from `docs/design/DESIGN.md`.

## Files

- `desktop-home-library.png` - desktop app shell covering Home, Library, mixes,
  most/recently played rows, playlists, account area, Up Next, and transport.
- `desktop-album-player.png` - desktop album detail covering album metadata,
  actions, track table, credits/lyrics context, Up Next, and transport.
- `web-home-search.png` - browser-hosted app covering Home, Library, Search
  results, queue, and sticky web transport.
- `web-settings-listening.png` - browser-hosted app covering Settings,
  Appearance controls, visualizer options, queue, focused listening preview,
  output, and transport.
- `phone-scenario-atlas.png` - phone scenario atlas covering Home, Library,
  Album, Search, Player, and Settings families.
- `tablet-scenario-atlas.png` - tablet scenario atlas covering split Home,
  detail, Search with Up Next, Player, and Artist/Radio families.
- `special-states-atlas.png` - special-state atlas covering visualizers,
  blurred artwork, queue expansion, tint states, sign-in, events, and favorites.
- `SCREENSHOT_SCENARIO_MATRIX.md` - generated matrix of every current Roborazzi
  screenshot artifact grouped by platform, design, appearance, surface, and
  state.
- `IMPLEMENTATION_SCENARIOS.md` - design-to-code handoff for an AI agent,
  including tokens, components, scenario groups, and verification commands.

## Direction

- Warm archive light mode remains the primary atmosphere.
- Album artwork stays the emotional anchor.
- Archive Blue is the only interactive accent.
- The fun tweak is an archival ledger/catalog-card motif: index marks, blue tabs,
  catalog numbers, ruled dividers, waveform stamps, and paper-sample controls.
- The layout keeps the current data model visible without adding analytics,
  marketing panels, or unrelated dashboard content.

The generated PNGs are target references. The real per-scenario screenshots
should be produced by implementing the tokens/components and then re-recording
Roborazzi using the commands in `IMPLEMENTATION_SCENARIOS.md`.
