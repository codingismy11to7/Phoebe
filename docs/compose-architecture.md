# Compose Architecture Guidelines

Phoebe keeps app coordination at the root and keeps reusable UI as plain state plus callbacks.

## Navigation

- Routes are serializable app locations, not domain models. A route may contain an ID, enum, or small value object, but not `Artist`, `Album`, `Track`, repository objects, or loaded UI state.
- `PhoebeRoute` is the shared route key type for Navigation 3. It implements `NavKey` and is registered in a `SavedStateConfiguration` serializers module so desktop, iOS, and web all use explicit serialization instead of JVM reflection.
- `PhoebeNavigator` owns back-stack mutation. UI code should call callbacks such as `onArtist(artistId)`, `onPlaylist(playlistId)`, or `onBack()` rather than mutating the stack directly.
- Navigation 3 is still alpha. Keep direct list operations isolated behind `PhoebeNavigator` and avoid spreading raw back-stack mutations through screens.
- Browser URL and platform history integration is separate from the in-app stack and should be added deliberately when the core stack is stable.

## Root And State Holders

- `PhoebeRoot` is the app coordinator. It collects app flows, derives route-facing UI state, handles one-shot effects, and wires callbacks.
- Business state stays in `AppState`; navigation state stays in the root coordinator. `AppState` can report app-flow outcomes such as "library selected" or "player should open", but it should not own detail stacks.
- Resolve route IDs to domain objects at the route entry. If an ID is missing from the active catalog, show a lightweight fallback with Back instead of crashing or storing stale domain objects in the route.

## UI Contracts

- UI composables take immutable state and callbacks. They should not take `AppState`, repositories, storage, clients, or raw back-stack lists.
- State-holder composables collect flows and handle effects. Plain UI composables render from state and emit events.
- When a composable starts accumulating many parameters, group the contract by feature responsibility before adding more parameters. Prefer names such as `PlaybackUiState`, `PlaybackActions`, `BrowseUiState`, and `SettingsActions`.
- Reusable layout components should expose slot APIs for variable visual regions instead of boolean shape flags or hard-coded children.

## Destination Boundaries

- Shared destinations belong in `PhoebeNavDisplay` entries.
- Platform/adaptive shells should wrap those entries rather than duplicating route ownership.
- Detail destinations should accept callbacks like `onArtist(artistId)`, `onAlbum(albumId)`, `onSong(trackId)`, and `onBack()`. Domain object lookup belongs at the route edge.
