# AnonPDF Privacy Policy

**Last updated: 26 July 2026**

## The short version

AnonPDF collects nothing, transmits nothing, and shares nothing with anyone.

There is no server. There is no account. The app holds no Android permissions —
including no permission to use the network — so it is technically incapable of
sending your documents anywhere.

## Data we collect

None.

Specifically, AnonPDF does not collect, and has no means of collecting:

- Your documents or any part of their contents
- File names, file paths, or file metadata
- Passwords you type to open or protect a PDF
- Device identifiers, advertising IDs, or installation IDs
- IP addresses
- Usage, analytics, telemetry, or crash reports
- Location, contacts, calendar, camera, microphone, or any other sensor or
  personal data source
- Anything else

## Data stored on your device

The app keeps three things in its own private storage area, which no other app
can read:

1. **A recent-files list** — the display name and system URI of up to 20
   documents you have opened, so you can reopen them quickly. You can delete
   this at any time (Settings → Clear recent files) or turn the feature off
   entirely (Settings → Remember recent files), which deletes the list
   immediately.
2. **Temporary working copies** — while you view or edit a document, AnonPDF
   copies it into its private cache. These are deleted each time the app starts,
   can be deleted on demand (Settings → Delete temporary working files), and may
   be reclaimed by Android at any time.
3. **Display preferences** — whether page colours are inverted, whether the
   screen stays on while reading, and whether recent files are remembered.

All three are excluded from Android's cloud backup and device-to-device transfer,
so they do not leave your phone even that way. Uninstalling the app removes them.

## Permissions

AnonPDF requests **no permissions at all**. You can confirm this in
**Settings → Apps → AnonPDF → Permissions**, or in the "App permissions" section
of its Play Store listing.

Files are opened through Android's Storage Access Framework: you pick a document
in the system file picker, and the system grants AnonPDF read access to that one
document. The app cannot browse or enumerate your storage.

Saving a file works the same way in reverse — you choose the destination in the
system picker, and the app writes only there.

## Sharing

AnonPDF never shares data, because it never has any and cannot transmit any.

The app does offer a **Share** button on files it has produced. That hands the
file to Android's share sheet, where *you* choose an app to send it to. Nothing
is sent unless you pick a destination, and what happens after that is governed by
whichever app you chose. AnonPDF itself has no network access and is not involved.

The **View the source code** link on the About screen opens a web address in your
browser. Your browser makes that request, not AnonPDF.

## Children

AnonPDF is suitable for all ages. It collects no data from anyone, including
children under 13, because it collects no data from anyone at all.

## Third-party components

AnonPDF is built with open-source libraries — AndroidX and Jetpack Compose,
Kotlin, and PdfBox-Android — all of which run entirely on the device. None of
them contain advertising, analytics, or networking code that AnonPDF invokes, and
none could reach the network in any case, since the app has no network
permission.

There are no software development kits from advertising networks, analytics
providers, or crash reporting services.

## Changes

If this policy ever changes, the new version will be published here and the date
at the top updated. Because the app collects nothing, any change would be a
narrowing of scope rather than a broadening of it; if that ever stopped being
true, it would be stated plainly in the app's release notes.

## Verifying all of this

You do not have to trust this document. AnonPDF is open source:
<https://github.com/FanFeast/anonpdf>

- The manifest, which lists the permissions, is at
  `app/src/main/AndroidManifest.xml`.
- The complete dependency list is at `gradle/libs.versions.toml`.
- The automated test suite asserts that the app requests no permissions, that
  backup is disabled, and that an outbound network connection actually fails.
  See `app/src/androidTest/java/io/github/fanfeast/anonpdf/PrivacyContractTest.kt`.

## Contact

Please raise anything you want to ask or report as an issue on the project's
GitHub page: <https://github.com/FanFeast/anonpdf/issues>
