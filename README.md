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
