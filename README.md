# Bible Quick Search KJV

A King James Version Bible reader for Android phones, foldables, tablets, and the web.

## Features

- Search by reference, book name, or verse text, with matching words highlighted.
- Browse books and chapters, or swipe between chapters.
- Adjust text size with pinch-to-zoom or shared reader and search controls.
- Resume reading where you left off, with saved text size and light/dark themes.

## Android build

Open the project in Android Studio, or use the Gradle wrapper:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
```

## Bible data

The Android app bundles its Bible database for offline use. To rebuild it from the checked-in OSIS XML source (`tools/sources/kjvlite.xml`) and verify it against that source:

```powershell
python .\tools\import_osis_to_sqlite.py
```

## Web reader

The `web/` reader is a static website that can be hosted on GitHub Pages.
It uses compressed chapter data generated from the same Bible source as Android.
Reading content loads first; text and heading search load separately when needed.
No backend is required.

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

The build generates gzip-compressed chapters and search data, with plain JSON
compatibility for browsers without native gzip decoding. Generated files stay
out of source control, and hosting requires no custom compression headers.

### Offline use

Visited chapters and loaded search data are cached automatically. Selecting an
uncached chapter fetches it when online. Open the table of contents and select
**Download entire Bible** for complete offline reading and search, with download
progress, cancellation, and retry. Interrupted downloads reuse cached files.

Offline installation requires the production site over HTTPS or localhost.
After an update, reload and download again to include the new version. Older
offline versions are retained, so updates can use additional storage.
**Verify offline download** checks downloaded files and restores missing ones
when online. Browser storage can be cleared; clearing this site's data also
removes saved preferences and downloads.

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
