# Serving Artwork to Android Automotive via a ContentProvider

## Goal

Make album, artist, and playlist artwork render in the Android Automotive OS
media surfaces: the browse tree, the playback screen, and the OS now-playing
display. Today every one of them shows a placeholder.

## Root cause

Not a bug in Phoebe, and not an authentication or network problem. The car's
media app cannot load remote images at all.

Decompiling `com.android.car.media` from the emulator's system image shows
`ImageFetcher$ImageLoadingTask` dispatching on exactly two schemes:

```
UriUtils.isAndroidResourceUri(uri) -> getIconResource -> getDrawable
UriUtils.isContentUri(uri)         -> ImageDecoder.createSource(ContentResolver, uri)
```

There is no `http`/`https` path — no `openConnection`, no `URL`. The APK
bundles no HTTP image stack either: zero Glide and zero OkHttp entries.

Phoebe emits Plex URLs such as
`http://192.168.0.2:32400/library/metadata/.../thumb/...?X-Plex-Token=...`,
so the car silently ignores them. Nothing is logged because no fetch is ever
attempted. This also explains why the browse tree's folder icons *do* render:
they are `android.resource://` URIs, one of the two supported schemes.

Cleartext HTTP policy was investigated and ruled out. The URL is never
fetched, so the policy never applies.

## Decisions

| Decision | Choice | Rationale |
| --- | --- | --- |
| Mechanism | ContentProvider serving `content://` URIs | The only remote-artwork path the car supports. Confirmed by decompilation, not inferred. |
| Byte source | Phoebe's existing artwork cache and fetch path | `cachedArtworkPathForUrl` + `storage`, already written by `CatalogRepository.downloadArtworkFor*` and read by `PhoebeArtwork`. Reuses provider-aware auth and needs no Compose context. |
| Rejected: Coil's disk cache | — | Coil's `ImageLoader` is built inside a composable in `ui/media`. A provider would need its own instance, duplicating HTTP and auth configuration. |
| URI identity | Catalog entity type and id | Keeps the Plex token inside Phoebe's process. The provider resolves the id to a `thumbUrl` itself. |
| Access | `exported="true"`, read-only | `MediaBrowser` clients are arbitrary processes, so a signature permission would lock out `com.android.car.media` itself. Per-URI grants expire and fail in ways that are hard to debug. |

## Design

### `ArtworkUris` — `playback/src/androidMain`

Owns the URI format so the producer and the provider cannot drift apart.

```
content://<applicationId>.artwork/<type>/<id>
```

`<type>` is one of `album`, `artist`, `playlist`, `track`. `<id>` is the
catalog's own identifier. The authority is derived from the package name at
runtime, so the `.debug` suffix is handled without special cases.

Provides a builder and a parser, both pure functions.

### `PhoebeArtworkProvider` — `composeApp/src/androidMain`

Declared in `composeApp/src/androidMain/AndroidManifest.xml` beside the
existing `FileProvider`, with `android:exported="true"` and
`android:grantUriPermissions="true"`. Lives in `composeApp` because it is an
app-level component and `composeApp` already depends on both `playback` and
`data:catalog`.

Only `openFile` is meaningful; `query`, `insert`, `update`, and `delete`
return empty or throw `UnsupportedOperationException`.

`openFile` does the following:

1. Parse the URI into a type and id. Malformed URI throws
   `FileNotFoundException`.
2. Look the entity's `thumbUrl` up in the catalog database. Missing entity or
   null/blank `thumbUrl` throws `FileNotFoundException`.
3. Resolve the cache path with `cachedArtworkPathForUrl(thumbUrl)`.
4. On a cache hit, return a read-only `ParcelFileDescriptor` for that file.
5. On a miss, fetch the bytes using the same path
   `CatalogRepository.downloadArtworkFor*` uses — including
   `applyEmbyFamilyArtworkAuth`, so Emby and Jellyfin work, not just Plex —
   write them to the cache, then return a descriptor for the written file.

**The descriptor must reference a real file, not a pipe.**
`ImageDecoder.createSource(ContentResolver, uri)` requires a seekable
descriptor, so streaming bytes through `ParcelFileDescriptor.createPipe` is
not an option. This is why the design is cache-backed rather than a pure
proxy.

### Call sites

In `playback/src/androidMain`, artwork URIs change from remote URLs to
content URIs:

- `BrowseMediaItems.browseTrackItem` — also covers the playback screen and
  the OS now-playing display, because `playbackMediaItem` delegates to it
- `BrowseMediaItems`: `Artist.toBrowseItem`, `Album.toBrowseItem`,
  `Playlist.toBrowseItem`
- `AndroidAutoBrowseTree.toMediaItem`, the `else` branch that currently calls
  `Uri.parse(thumbUrl)`

Folder icons keep their `drawableArtUri` resource URIs. Those already render.

## Consequences

Content that has been downloaded for offline use serves with **no fetch at
all**, because those files are already in this cache under the same key.

Every provider benefits, not only Plex. Emby and Jellyfin authenticate
artwork with headers rather than a URL parameter, which an external process
cannot supply — so proxying is required for correctness there, not merely
convenient.

## Error handling

`openFile` runs on a binder thread, so the fetch is bounded by the existing
`DownloadArtworkTimeoutMs` rather than a new constant. A slow or unreachable
server must produce a placeholder, never a hung car UI.

Every failure path throws `FileNotFoundException`, which the car already
handles by falling back to its own placeholder. Failures are logged at debug
level through `PhoebeLog`, matching `CatalogRepository`'s existing artwork
logging.

## Testing

- `ArtworkUris` build/parse round-trip for all four types, including ids
  containing characters that require escaping.
- Provider resolution under Robolectric: cache hit returns a descriptor;
  unknown id throws `FileNotFoundException`; blank `thumbUrl` throws
  `FileNotFoundException`.

These run in `playback`'s and `composeApp`'s host-test source sets. Note that
`playback`'s Android tests only execute once the source-set wiring from the
AAOS media fixes PR has landed.

## Out of Scope

Image downscaling for the car's thumbnail sizes; cache eviction or size
bounding, which stays as it is today; the local-media artwork path
(`Track.localArtworkUri`), which is untouched because `browseTrackItem`
already uses `thumbUrl` only; and non-Android platforms, which do not have
ContentProviders.

## Risks

- A cold cache means the first browse of a large library issues many fetches,
  one per visible item. The timeout bounds each one, but the first scroll
  through 1300 albums may be slow. Mitigation, if needed later: prefetch on
  browse, or downscale.
- `openFile` blocking on the network is acceptable only because it is
  bounded. If the car turns out to call it on a latency-sensitive thread,
  this needs revisiting.
- The provider is exported, so any app that can guess entity ids can read
  artwork. Judged acceptable: it is album art, and the alternative locks out
  the car itself.
