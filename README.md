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

The app bundles its Bible database for offline use. To rebuild it from the checked-in source and verify verse integrity:

```powershell
python .\tools\build_kjv_pipeline.py
```

See [data tooling](tools/README.md) for details and [third-party notices](THIRD_PARTY_NOTICES.md) for sourcing and attribution.
