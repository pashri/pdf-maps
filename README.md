# PDF Maps

An Android app for reading PDF maps — transit networks, bike path
networks, anything diagrammatic. Import a PDF, pick it from an
alphabetical list, zoom and pan.

These maps are diagrams, not survey sheets, so the app assumes
nothing about scale or geography: no GPS, no georeferencing, no
accounts. Just the map, as large as you want it.

## Install

Grab the APK from the [latest release][releases] and open it on your
phone. Android will ask you to allow installing from unknown
sources, since this is not distributed through the Play Store.

Requires Android 8.0 (API 26) or newer.

[releases]: https://github.com/pashri/pdf-maps/releases/latest

## What it does

- **Import** — share or "open with" any PDF, or tap `+` to pick one.
  The file is copied into app-private storage, so the library is
  unaffected by the original being moved or deleted.
- **One entry per page** — a 3-page PDF becomes 3 maps, named
  `Something (1/3)` … `(3/3)`. Single-page PDFs get no suffix.
- **Library** — starred maps pinned on top, alphabetical within each
  group, with thumbnails. Long-press to rename, star or delete.
- **Viewer** — pinch, pan and double-tap. Tiles are re-rendered from
  the PDF at the current zoom level, so text stays sharp all the way
  in. Zoom and position are remembered per map.

## Building

Requires JDK 17 and the Android SDK (platform 35).

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.
Install it with `adb install -r <that path>`.

`local.properties` points at the SDK and is not committed; create it
with `sdk.dir=/path/to/Android/sdk` if Android Studio hasn't.

## How the viewer works

`PdfRenderer` is not thread-safe and allows one open page at a time,
so `PdfDocumentSource` funnels every render through a single mutex on
a background dispatcher. Getting this wrong produces crashes that
only appear under fast panning.

`TileRenderer` addresses tiles by zoom *level* (level N renders at
`2^N` times fit-to-screen scale) rather than by continuous scale, so
panning at a steady zoom reuses cached tiles. Level 0 tiles are drawn
underneath as an underlay, so zooming never shows blank space while
sharper tiles are still rendering. `TileCache` is an LRU bounded to a
quarter of the device's heap budget.

## Layout

```
data/     Room entity, DAO, database, file store, repository, import
render/   PdfDocumentSource, TileRenderer, TileCache, thumbnails
ui/       LibraryScreen, MapViewerScreen, view models, theme
```

## Not included

Search, manual rotation, inverted/dark rendering, keep-screen-awake,
image formats other than PDF, anything GPS.
