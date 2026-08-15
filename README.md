# TSURE

TSURE is a simple Android checklist for inspecting a Tesla on collection day.

Current version: **1.0.3**

## Features

- **Model 3 and Model Y checklists** — choose your vehicle and work through delivery-day checks covering the exterior, interior, technology, charging, documents, and final handover.
- **Saved progress** — checklist responses, notes, and photos are stored locally, with separate progress maintained for each supported model.
- **Clear inspection status** — mark every check as Pass, Issue, or N/A and see overall and section-level progress as you work.
- **Section navigation** — quickly jump between checklist sections and see which sections are complete or contain issues.
- **Issue evidence** — describe a problem and attach a photo from the gallery or camera when an item is marked as an issue.
- **Focused issue review** — review reported issues by section in a dedicated view that is easy to work through with Tesla staff.
- **Flexible exports** — share the complete checklist as a text report, or export an issue-only report with attached photos through Android's share menu.
- **Custom checks** — add your own inspection items for accessories, options, or anything else you want to verify.
- **Private and offline** — no account or internet connection is required; checklist data remains on the device unless you choose to share it.

## Exporting reports

Open the hamburger menu from the checklist to share a full report containing every item and its status. From Issue Review, use its hamburger menu to export only reported issues, including descriptions and any supporting photos. Android's share menu lets you choose the available email, messaging, storage, or other compatible app.

## Installation

Download the latest APK from the [GitHub releases page](https://github.com/MGRayd/TesSure/releases) and install it on a device running Android 10 or later. Android may ask you to allow installation from your browser or file manager.

## Building locally

Build a debug APK from the repository root:

```powershell
.\gradlew.bat assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`.
