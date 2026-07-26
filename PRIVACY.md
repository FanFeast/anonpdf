# AnonPDF Privacy Policy

**Last updated: 26 July 2026**

## The short version

AnonPDF collects nothing, transmits nothing, and shares nothing with anyone.

There is no server and no account. The app has no permission to use the network,
so it is technically incapable of sending your documents anywhere — the operating
system refuses the connection, whatever the app might try.

AnonPDF can read PDF files on your device, if you allow it, so that it can show
you a list of them instead of making you hunt through the system file picker every
time. Reading them is all it does with them.

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

AnonPDF declares exactly one permission:

**`MANAGE_EXTERNAL_STORAGE`** ("All files access") — so the built-in browser can
list the PDFs on your device. It is used for reading document files and nothing
else. AnonPDF does not scan your photos, does not build an index, and does not
send any part of what it reads anywhere, because it has no way to.

This permission is **optional**. If you never grant it, AnonPDF falls back to
Android's Storage Access Framework: you pick a document in the system file
picker, and the system grants access to that one document. Every feature still
works; you just have to pick files one at a time.

Saving always works through the picker — you choose the destination and the app
writes only there. AnonPDF never overwrites your original file.

### One thing worth being clear about

All-files access is what Android calls a *special app access*, not a normal
runtime permission. It lives under **Settings → Apps → Special app access → All
files access**, and it does **not** show up on the app's ordinary "Permissions"
page. That page will say "No permissions requested" whether or not you have
granted it.

We are pointing this out rather than leaving you to discover it, because that
screen is a natural place to go looking and it would give you a misleadingly
reassuring answer. To check the real state, use the Special app access screen.

### What is deliberately not requested

No network. No location, contacts, camera, microphone, phone state, or Bluetooth.
No photo or media permissions. An automated test fails the build if anything
beyond the single storage permission ever appears in the app's manifest.

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
- The automated test suite asserts that no permission beyond the single storage
  one is declared, that nothing capable of moving data off the device is declared,
  that backup is disabled, and that an outbound socket *and* a DNS lookup both
  actually fail. See
  `app/src/androidTest/java/io/github/fanfeast/anonpdf/PrivacyContractTest.kt`.

## Contact

Please raise anything you want to ask or report as an issue on the project's
GitHub page: <https://github.com/FanFeast/anonpdf/issues>
