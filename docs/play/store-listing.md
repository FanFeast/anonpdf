# Google Play: what to enter

Everything the Play Console asks for, written to match what the app does. When
the app changes, change this too. The graphics are in this folder: the icon and
the feature graphic, made by `generate_store_graphics.py`.

## Main store listing

**App name** (30 characters max)

    AnonPDF

**Short description** (80 characters max)

    Read and edit PDFs on your phone. No internet, no ads, no accounts.

**Full description** (4,000 characters max)

    AnonPDF reads and edits PDFs entirely on your phone. It has no internet permission, so Android itself stops it from sending your documents anywhere. No ads, no accounts, no tracking, no paid tier.

    EDIT IN ONE PASS, WITH A LIVE PREVIEW
    • Rotate, crop, delete, reorder or reverse pages
    • Resize: scale by percentage, or refit onto A4, Letter, A3, A5, Legal or Tabloid
    • Add pages from other PDFs, then edit them like any other page
    • Add text, highlight, draw, or white out content (the page is flattened, so it is really gone)
    • Text watermarks and page numbers
    • Every change can be undone until you save, and your original file is never modified

    WHOLE-FILE TOOLS
    • Merge, split, and extract pages
    • Compress: re-encode images only, or flatten pages for the smallest file
    • PDF to JPG or PNG, and pictures to PDF
    • Extract text to a .txt file
    • Protect with an AES-256 password, or remove a password you know
    • Sign: draw your signature and place it on a page

    READING
    • Smooth scrolling, pinch to zoom, search, night mode
    • Opens password-protected PDFs
    • Open PDFs from any other app

    YOUR FILES STAY YOURS
    • No internet permission: the operating system refuses any connection
    • No analytics, crash reports or tracking of any kind
    • One optional permission, "All files access", only so the app can list the PDFs on your phone. Decline it and you pick files with Android's file picker instead; every feature still works.
    • Working copies are kept in the app's private storage and deleted when it starts

    AnonPDF is open source. Read the code, or check every claim above, at github.com/FanFeast/anonpdf

    Not included, on purpose: Word and Office conversion, OCR, cloud sync. Each would need either a server or a converter that does a poor job. "Sign" places a picture of your signature; it is not a cryptographic certificate.

**Graphics**

- App icon: `icon-512.png`
- Feature graphic: `feature-graphic-1024x500.png`
- Phone screenshots: from `docs/screenshots/`, framed to Play's 1:2 limit (see
  "Phone screenshots" below). Use up to 8.

**Category:** Productivity. **Tags:** PDF, Documents, Productivity.

**Contact details:** an email address is required, and shown publicly. Website:
`https://github.com/FanFeast/anonpdf`

## Policy pages ("App content")

**Privacy policy URL**

    https://github.com/FanFeast/anonpdf/blob/main/PRIVACY.md

**App access:** All functionality is available without any special access. No
login.

**Ads:** No, my app does not contain ads.

**Content rating** (IARC questionnaire)

- Category: *All other app types* (utility / productivity)
- Violence, sexuality, language, controlled substances, gambling: **No** to every question
- Users interact or exchange content with each other: **No**
- Shares the user's location with others: **No**
- Allows digital purchases: **No**
- It is not a web browser or search engine

**Target audience:** 18 and over. AnonPDF is fine for any age, but choosing an
under-18 group brings in the Families policy requirements, which a document tool
does not need.

**Data safety**

- Does your app collect or share any of the required user data types? **No**
- That is the whole form. It is true by construction: the app has no network
  permission, so it cannot send anything. CI fails the build if one appears.
- The listing then shows "No data collected" and "No data shared".

**Advertising ID:** No, the app does not use an advertising ID.

**Government app:** No. **Financial features:** None. **Health:** No.
**News app:** No.

## All files access declaration (`MANAGE_EXTERNAL_STORAGE`)

Play asks for this when a bundle with the permission is uploaded.

**Core functionality:** Document management (reading and editing PDF documents).

**Why the app needs it**

    AnonPDF is a PDF reader and editor. Its home screen lists every PDF on the device, newest first and searchable by name, and lets the user browse folders of documents. Building that list requires reading the names and locations of document files across shared storage, which the Storage Access Framework cannot provide; it only grants access to files the user has already picked one at a time.

    The permission is optional. The app explains it before asking, and if the user declines, the app falls back to the system file picker, with every feature still working. AnonPDF reads only document files, does not build an index, and has no internet permission, so nothing it reads can leave the device. Edited documents are always saved through the system picker to a location the user chooses, and the original file is never modified.

**Video:** Play may ask for a short screen recording showing the permission
prompt and the file list. Record it on a phone or the emulator: open the app,
grant the permission, show the list of PDFs, open one.

## Phone screenshots

Play rejects a screenshot whose long side is more than twice its short side,
and the emulator's are 1080x2400. Each one is fitted onto the icon's navy at
1200x2400, scaled to a height of 2240 with rounded corners, so nothing is
cropped.
