# Publishing AnonPDF to Google Play

Everything you need to fill in the console, plus the answers to the questions
Play will ask. Written for a personal Google account.

## Before you start

You need:

- A **Google Play Developer account** — one-off **US$25** registration at
  <https://play.google.com/console/signup>. Personal accounts also require
  identity verification, which can take a couple of days, so start there first.
- The signed bundle: `app/build/outputs/bundle/release/app-release.aab`
- The upload keystore and its password, kept somewhere safe and backed up

### The fee, from India

There is no regional discount. The fee is US$25 flat worldwide, billed in
rupees at the conversion rate on the day, and Indian GST is added on top — so
expect roughly **₹2,100–2,500** rather than less. It is one-time and
non-refundable; the account then stays active indefinitely.

Use a card that permits international transactions. Indian cards are declined
fairly often on this payment because of the 2021 RBI rules on recurring and
cross-border card mandates. A card that has worked for other USD purchases is
the safest bet.

### Read this before you plan a launch date

If you register a **personal** account (which is what an individual Google
account gets you), Google requires a closed test before you may publish
publicly:

> you must run a closed test for your app with a minimum of 12 testers who have
> been opted-in for at least the last 14 days continuously

The 14 days must be consecutive, and if a tester opts out the clock is affected.
So realistically:

| | |
|---|---|
| Account registration + identity verification | 1–3 days |
| Recruit 12 testers and get them opted in | your call |
| Closed test running | 14 consecutive days minimum |
| Apply for production access, then review | days |

That is **two to three weeks minimum** from registering to being live, and it is
a queue you cannot shorten by paying. Line the 12 testers up early — they need
Google accounts, and each has to accept the opt-in link and keep the app
installed.

An **organization** account is exempt from the 12-tester requirement, but needs a
D-U-N-S number for a registered business entity. If AnonPDF is a personal
project, the personal account plus the closed test is the normal path.

None of this blocks anything today: the app is finished and the signed bundle is
built. It is purely Google's queue.

### About the keystore

The build reads signing details from `keystore.properties` in the project root,
which is gitignored and must never be committed.

Turn on **Play App Signing** when prompted (it is the default). Google then holds
the real app signing key and your keystore is only an *upload* key. If you ever
lose the upload key you can ask Google to reset it — but you cannot recover the
app signing key, so letting Google manage it is the safer choice.

Back up both the keystore file and `keystore.properties`. Do not put them in the
repository.

## Store listing copy

**App name** (30 char limit)

```
AnonPDF
```

**Short description** (80 char limit — this is 79)

```
Offline PDF reader and editor. No ads, no accounts, no tracking, no permissions.
```

**Full description** (4000 char limit)

```
AnonPDF is a PDF reader and editor that does its work on your device and nowhere else.

No ads. No accounts. No subscriptions. No tracking. No cloud.

AnonPDF requests no Android permissions at all — including no permission to use the network. It is not that we promise not to upload your documents; the app has no way to reach the internet, and Android enforces that. You can check for yourself in Settings > Apps > AnonPDF > Permissions.

READ
• Smooth continuous scrolling, using the PDF renderer built into Android
• Pinch to zoom, with pages redrawn sharply as you zoom
• Jump to any page, search the text, invert colours for night reading
• Opens password-protected PDFs
• Open PDFs straight from your files app, email or browser downloads

ORGANISE PAGES
• Merge several PDFs into one
• Split a document into separate files by page range, or every N pages
• Extract just the pages you want
• Reorder, rotate and delete pages on a thumbnail grid
• Rotate every page at once
• Crop margins

CONVERT AND COMPRESS
• Compress a PDF, with a choice between keeping text selectable or getting the smallest possible file — the app tells you which trade-off you are making
• Export pages as JPG or PNG at the resolution you choose
• Turn photos and scans into a PDF, with A4, Letter or fit-to-image pages
• Extract the text into a plain .txt file

MARKUP
• Add a text watermark — diagonal, centred, tiled, top or bottom, with adjustable opacity and size
• Add page numbers, with a format of your choosing, and skip a cover page if you need to
• Draw your signature and place it on a page

SECURITY
• Protect a PDF with a password, using AES-256 encryption
• Remove a password from a PDF you can already open

WHAT IT STORES
A list of recently opened file names, temporary working copies of the file you are editing, and your display preferences. All of it stays in the app's own private storage, all of it is excluded from cloud backup, and you can clear any of it from Settings. There are no identifiers of any kind.

EVERY FEATURE IS INCLUDED
There is no paid tier, no unlock, and nothing held back. If it is in the list above, it works.

OPEN SOURCE
The complete source code is public, so the privacy claims above can be checked rather than taken on faith:
https://github.com/FanFeast/anonpdf

WHAT IT DOES NOT DO
Being straight about the edges: there is no Word, Excel or PowerPoint conversion, and no OCR for reading text out of scanned pages. Both need either a very large bundled engine or a server, and this app is built around having neither. "Sign PDF" places a picture of your signature on the page — it is ink on paper, not a cryptographic certificate.

Requires Android 12 or newer.
```

## Graphics you need to produce

Play will not accept the listing without these.

| Asset | Requirement | Notes |
|---|---|---|
| App icon | 512×512 PNG, 32-bit, no transparency | Reuse the launcher icon artwork: navy background, white page, blue keyhole |
| Feature graphic | 1024×500 PNG or JPG, no transparency | Wordmark plus a short line like "Offline. No permissions. No ads." |
| Phone screenshots | At least 2, up to 8. 16:9 or 9:16, min 1080 px on the shorter side | Use `docs/screenshots/` from the repo — they are 1080×2400 |

The repo screenshots you can upload as-is:

- `docs/screenshots/home.png` — tool library and the privacy banner
- `docs/screenshots/viewer.png` — reading a document
- `docs/screenshots/viewer-night.png` — night mode
- `docs/screenshots/organize.png` — the page grid
- `docs/screenshots/watermark.png` — a tool with its options
- `docs/screenshots/protect.png` — password protection

To capture more, run the app on a device or emulator and use
`adb exec-out screencap -p > shot.png`.

## Console walkthrough

1. **Create app** — name `AnonPDF`, default language English, type *App*,
   free. Accept the declarations.
2. **Store listing** — paste the copy above, upload the graphics.
3. **App category** — Category *Productivity*. Tags: PDF, document reader,
   privacy. Add your email as the contact address.
4. **Privacy policy** — required. Point it at the policy in this repo:
   `https://github.com/FanFeast/anonpdf/blob/main/PRIVACY.md`
   If Play objects to a GitHub URL, enable GitHub Pages on the repository and use
   the resulting `github.io` address instead.
5. **App access** — choose **All functionality is available without any special
   access**. There is no login.
6. **Ads** — **No, my app does not contain ads.**
7. **Content rating** — fill in the questionnaire. Every answer is *No* /
   *None*: no violence, no sexual content, no profanity, no gambling, no user
   interaction, no data sharing, no location. You should end up with the lowest
   rating in each region.
8. **Target audience** — 13+ is the simplest choice. The app is suitable for all
   ages, but selecting an under-13 audience pulls you into the Families policy
   programme and extra review for no benefit here.
9. **Data safety** — see the exact answers below.
10. **Government apps** — No. **Financial features** — None.
11. **Health apps** — No.
12. **Closed test first** (personal accounts) — create a closed testing track,
    upload `app-release.aab` there, and share the opt-in link with your 12
    testers. Leave it running 14 consecutive days. See the timing note at the
    top of this document.
13. **Production release** — once you have production access, promote the same
    bundle to production, write the release notes, set the countries, then roll
    out.

Review itself takes hours to days. The 14-day closed test is the part that
dominates the schedule for a new personal account.

## Data safety form — exact answers

This is the section people get wrong. For AnonPDF every answer is the negative one.

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | *(not asked once you answer No above)* |
| Do you provide a way for users to request that their data is deleted? | *(not asked)* |

Because the answer to the first question is **No**, the whole rest of the form
disappears. The resulting store listing will read "No data collected" and
"No data shared with third parties".

If Play asks you to justify it during review, the substance of the reply is:

> The app declares no Android permissions, including no INTERNET permission, so
> it cannot transmit data. Files are read via the Storage Access Framework only
> when the user selects them in the system picker. All processing is on-device.
> The source is public at https://github.com/FanFeast/anonpdf and the test suite
> asserts that the app requests no permissions and that outbound network
> connections fail.

Note the distinction Play draws: data the app keeps **only on the device** and
never sends anywhere is *not* "collection". The recent-files list and cached
working copies are local-only, so they do not need to be declared.

## Version bumps

For each subsequent release, edit `app/build.gradle.kts`:

```kotlin
versionCode = 2          // must increase every upload
versionName = "1.0.1"    // what users see
```

Then `./gradlew bundleRelease` and upload the new `.aab`. Play rejects a bundle
whose `versionCode` it has seen before.

Tagging the commit `v1.0.1` and pushing the tag also triggers
`.github/workflows/release.yml`, which builds the same artifacts in CI — provided
you have added the four signing secrets described in that file.

## Things worth knowing

- **targetSdk** is 36 and **compileSdk** is 37, which satisfies Play's current
  target-API requirement for new apps. Play raises this yearly, so expect to bump
  `targetSdk` roughly every August to stay updatable.
- **minSdk 31** means Android 12 and newer — a little under 80% of active devices
  at time of writing. Lowering it later is possible but would need testing on
  older releases.
- The bundle is about 6 MB; Play will generate per-device downloads smaller than
  that.
- Keep the "no data collected" declaration accurate. If a future version ever
  adds anything that touches the network, the declaration and the privacy policy
  have to change with it in the same release.
