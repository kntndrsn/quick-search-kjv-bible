# Bible Quick Search KJV

An offline King James Version Bible reader for Android phones, foldables, and tablets.

## Features

- Search by reference, book name, or verse text, with matching words highlighted.
- Browse books and chapters, or swipe between chapters.
- Adjust text size with pinch-to-zoom or shared reader and search controls.
- Resume reading where you left off, with saved text size and light/dark themes.

## Build

Open the project in Android Studio, or use the Gradle wrapper:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
```

## Bible data

The app bundles its Bible database for offline use. To rebuild it from the checked-in OSIS XML source (`tools/sources/kjvlite.xml`) and verify it against that source:

```powershell
python .\tools\import_osis_to_sqlite.py
```

## Web reader

The `web/` app is a static website: no application server, Node process, or
SQLite/WebAssembly runtime is required in production. It shares the checked-in
OSIS source and parser with Android but does not modify the Android database or
Gradle build.

Use Node.js 22+ and Python 3.12+ (standard library only):

```powershell
Set-Location web
npm.cmd ci
npm.cmd test
npm.cmd run dev
```

On systems without PowerShell's script restriction, `npm` works in place of
`npm.cmd`. Python must be available as `python`; set `PYTHON` to another Python
executable if needed.

For a production build:

```powershell
Set-Location web
$env:WEB_BASE = "/quick-search-kjv-bible/bible/"
npm.cmd run build
```

Publish the **contents** of `web/dist/` at that base path. Leave `WEB_BASE` unset
for a site served at `/`. Development and preview servers are only local tools,
not hosting requirements.

### Loading and compression

The build exports one compact payload per chapter and a separate precomputed
search index from `tools/sources/kjvlite.xml`. It produces deterministic gzip
assets and plain JSON compatibility assets under an immutable data version.
Browsers with `DecompressionStream('gzip')` fetch and decode the gzip files;
older browsers fetch JSON instead. Hosting does not need custom compression
headers. Generated data and build output are not checked into Git.

The reader loads the saved chapter first, then prefetches adjacent chapters.
Text and heading search load in a Web Worker on the first qualifying query,
without blocking reading. Reference and book-name lookup do not need the search
download. Payload size and SHA-256 checks reject incomplete or corrupt data.
The web tests verify every verse and heading against the Android asset and
compare representative searches with its SQLite behavior.

### Offline use

On the production HTTPS site (or localhost), visited chapters and loaded search
data are cached. Open the table of contents and select **Download entire Bible**
to download all chapters and search data, with progress and cancel/retry.
Interrupted downloads reuse verified cached files. A chapter not yet cached
requires internet access; search likewise requires its own initial download.
Failures are shown with retry controls instead of empty results.
Selecting an uncached chapter fetches it automatically when online. Invalid
cached payloads are discarded and fetched again, with the replacement verified
before it is displayed or cached.

The production service worker installs the app shell, including the search
worker, separately from Bible downloads. It is scoped to the reader, not the
privacy page, and does not delete other websites' caches. Older versioned
offline sets are retained so updates do not discard a working offline copy.
Reload after an update and run the full download again to verify the new set.
This can consume extra storage across updates; clearing this site's browser
data removes old sets **and** saved preferences/downloads.

Persistent storage is requested when starting a full download where supported.
Browsers or users may still clear storage; **Verify offline download** rechecks
the files and restores missing ones when online. Service workers require a
secure context and are not installed by the development server.

### GitHub Pages

In repository **Settings > Pages**, select **GitHub Actions** as the publishing
source. The `Publish Bible and privacy page` workflow builds/tests the reader
and publishes one artifact:

- `/quick-search-kjv-bible/privacy.html` retains the root privacy-page URL.
- `/quick-search-kjv-bible/bible/` serves the reader.

The workflow derives its base path from Pages configuration, including custom
domains. It runs on relevant changes pushed to `main`, or manually through the
Actions tab. Node and Python run only during the build; Pages serves static
files. Android source, assets, and build tasks remain separate and unchanged.
