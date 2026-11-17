package io.github.fanfeast.anonpdf.i18n

/**
 * Every sentence the app shows, in English.
 *
 * A translation is a subclass that overrides what it translates; anything it
 * leaves alone stays English, so a missing line is never a blank. Kept in the
 * engine's source set because both apps and the engine's own messages use it,
 * and because Android's string resources do not exist on desktop.
 *
 * Counts are functions, not templates, because languages disagree about plurals:
 * Russian has three forms, Chinese and Japanese have none.
 */
open class Strings {

    // ----------------------------------------------------------------- common

    open val cancel = "Cancel"
    open val apply = "Apply"
    open val close = "Close"
    open val clear = "Clear"
    open val remove = "Remove"
    open val undo = "Undo"
    open val redo = "Redo"
    open val back = "Back"
    open val done = "Done"
    open val saveAs = "Save as…"
    open val saveAllToFolder = "Save all to folder…"
    open val share = "Share"
    open val startOver = "Start over"
    open val choosePdf = "Choose a PDF"
    open val none = "None"
    open val all = "All"
    open val options = "Options"
    open val settings = "Settings"
    open val about = "About"
    open val saved = "Saved."
    open fun savedTo(name: String) = "Saved to $name."
    open fun savedFiles(count: Int) = "Saved $count files."
    open val couldNotSaveFile = "Could not save the file."
    open val couldNotSaveToFolder = "Could not save to that folder."
    open val folderNotWritable = "That folder is not writable."
    open fun couldNotCreate(name: String) = "Could not create $name."
    open val nothingCanReceive = "Nothing on this device can receive that file."
    open val couldNotOpenFile = "Could not open the file."
    open val notAPdf = "This file could not be opened as a PDF."
    open val notAPdfThat = "That file could not be opened as a PDF."
    open val loading = "Loading…"
    open val working = "Working…"
    open val readingFile = "Reading the file…"
    open val thatDidNotWork = "That did not work."
    open val onlyPdfsOpen = "Only PDFs can be opened here."
    open val onlyPdfsAdd = "Only PDFs can be added here."
    open val onlyOnePdf = "Only a PDF can be used here."
    open val onlyPictures = "Only pictures can be added here: JPEG, PNG, GIF or BMP."
    open val pagePreview = "Page preview"
    open val previousPage = "Previous page"
    open val nextPage = "Next page"
    open val zoomIn = "Zoom in"
    open val zoomOut = "Zoom out"
    open val unknownSize = "unknown size"

    open fun pages(count: Int) = if (count == 1) "1 page" else "$count pages"
    open fun files(count: Int) = if (count == 1) "1 file" else "$count files"
    open fun images(count: Int) = if (count == 1) "1 image" else "$count images"
    open fun edits(count: Int) = if (count == 1) "1 edit" else "$count edits"
    open fun pageNumber(number: Int) = "page $number"
    open fun pageTitle(number: Int) = "Page $number"
    open fun pageOf(number: Int, total: Int) = "page $number / $total"

    /** Byte sizes: the unit names are all most languages change. */
    open val bytesUnit = "B"
    open val kilobytesUnit = "KB"
    open val megabytesUnit = "MB"
    open val gigabytesUnit = "GB"

    // --------------------------------------------------------------- passwords

    open val passwordNeeded = "Password needed"
    open val passwordPrompt = "This PDF is protected. Enter its password to continue."
    open val password = "Password"
    open val unlock = "Unlock"
    open val enterPassword = "Enter password"
    open val wrongPassword = "That password did not work."
    open val passwordProtected = "This PDF is password protected."
    open fun protectedNamed(name: String) = "\"$name\" is protected. Enter its password to continue."
    open val mergePasswordPrompt =
        "A PDF you picked is protected. Enter its password to merge it, or cancel to skip that file."

    // ------------------------------------------------------------- hidden info

    open val removeHiddenInfo = "Remove hidden info"
    open fun hiddenAuthor(name: String) = "Author: $name"
    open fun hiddenCommentAuthors(names: String) = "Comment authors: $names"
    open fun hiddenMadeWith(app: String) = "Made with: $app"
    open fun hiddenPdfBy(app: String) = "PDF by: $app"
    open fun hiddenCreated(date: String) = "Created $date"
    open fun hiddenModified(date: String) = "Modified $date"
    open fun hiddenTitle(title: String) = "Title: $title"
    open fun hiddenSubject(subject: String) = "Subject: $subject"
    open fun hiddenKeywords(keywords: String) = "Keywords: $keywords"
    open fun hiddenOtherProperties(count: Int) =
        if (count == 1) "1 other property" else "$count other properties"
    open fun hiddenAttachments(count: Int) = if (count == 1) "1 attached file" else "$count attached files"
    open fun hiddenScripts(count: Int) = if (count == 1) "1 script" else "$count scripts"
    open val hiddenXmp = "XMP metadata"
    open val hiddenEditorData = "editor data"
    open val couldNotRemoveHiddenInfo = "Could not remove the hidden info."

    // ------------------------------------------------------------------ editor

    open val editPdf = "Edit PDF"
    open val editorIntro =
        "Rotate, crop, resize, delete and reorder pages, add text, white-out, highlights or " +
            "drawings, and reach every other tool from here — all with a preview of the real " +
            "result. Nothing is written until you save."
    open val revertAll = "Revert all"
    open val noEditsYet = "No edits yet"
    open val applyingEditsFirst = "Applying your edits first…"
    open val couldNotPrepare = "Could not prepare the document."
    open val writingDocument = "Writing the document…"
    open val couldNotSave = "Could not save the document."
    open fun savedNote(pages: Int, edits: Int) = "${this.pages(pages)}, ${this.edits(edits)} applied."
    open val finishFirst = "Finish what you're doing first, then drop the file again."
    open val everyPageDeleted = "Every page is deleted.\nRestore one to carry on."
    open val resumeEditing = "Resume editing"
    open val movePage = "Move page"
    open val tapToSelect = "Tap to select. Press and hold, then drag to reorder."
    open val holdToReorder = "Press and hold a page, then drag it to reorder."
    open val deletedPage = "Deleted page"
    open val deleted = "Deleted"
    open fun resultSize(width: Int, height: Int) = "Result: $width × $height pt"
    open val hasFormPrompt = "This PDF has a form: fill it in"

    open val scopeThisPage = "This page"
    open val scopeSelected = "Selected"
    open val scopeAllPages = "All pages"
    open val noPages = "no pages"
    open fun allPages(count: Int) = "all $count pages"
    open fun pagesList(list: String) = "pages $list"

    open val groupPages = "Pages"
    open val groupContent = "Content"
    open val groupWholeFile = "Whole file"
    open val groupConvertLock = "Convert & lock"

    open val toolLeft = "Left"
    open val toolRight = "Right"
    open val toolCrop = "Crop"
    open val toolResize = "Resize"
    open val toolDelete = "Delete"
    open val toolRestore = "Restore"
    open val toolReverse = "Reverse"
    open val toolFillForm = "Fill form"
    open val toolText = "Text"
    open val toolWhiteout = "White-out"
    open val toolHighlight = "Highlight"
    open val toolDraw = "Draw"
    open val toolWatermark = "Watermark"
    open val toolNumbers = "Numbers"
    open val toolMerge = "Merge"
    open val toolSplit = "Split"
    open val toolExtract = "Extract"
    open val toolCompress = "Compress"
    open val toolToImages = "To images"
    open val toolFromImages = "From images"
    open val toolGetText = "Get text"
    open val toolSign = "Sign"
    open val toolProtect = "Protect"
    open val toolUnlock = "Unlock"

    // Panels.
    open val applyCrop = "Apply crop"
    open val cropHint =
        "Drag the frame on the page above. Edges are measured from the page as you see it, " +
            "so a rotated page crops the way it looks."
    open fun cropEdges(left: String, top: String, right: String, bottom: String) =
        "L $left   T $top   R $right   B $bottom"
    open val cropAllRound = "6% all round"
    open val sizePaper = "Paper size"
    open val sizePercent = "Percentage"
    open val resizePaperHint = "Puts the page on a sheet of this exact size. The preview above shows the result."
    open val sheet = "Sheet"
    open val orientation = "Orientation"
    open val whereTheyDoNotMatch = "Where they do not match"
    open val fillCropOverflow = "Fill, crop overflow"
    open val fitLeaveMargins = "Fit, leave margins"
    open val resizePercentHint =
        "Keeps the page's proportions and changes its size. The preview above changes size as you drag."
    open val size = "Size"
    open val text = "Text"
    open val placement = "Placement"
    open val opacity = "Opacity"
    open val textSize = "Text size"
    open val every_page = "every page"
    open val pageNumbers = "Page numbers"
    open val format = "Format"
    open val formatHint = "{n} is the number, {total} the count"
    open val numberPresetPage = "Page {n}"
    open val position = "Position"
    open val startNumberingAt = "Start numbering at"
    open val skipFirstPages = "Skip first pages"
    open val addText = "Add text"
    open val dragMarker = "Drag the marker on the page to move the text."
    open val tapToPlace = "Tap the page above to choose where the text starts."
    open val colour = "Colour"
    open val weight = "Weight"
    open val bold = "Bold"
    open val regular = "Regular"
    open fun alreadyAdded(count: Int) =
        (if (count == 1) "1 item" else "$count items") + " already added to this page. \"Remove\" clears them."
    open val highlightHint = "Drag over the text you want to highlight. Drag inside the box to move it."
    open val whiteoutHint =
        "Drag a box over what you want to remove. What it covers is permanently deleted: this " +
            "page is saved as an image, so its text can no longer be selected or searched. To " +
            "change wording, cover it and add your own text on top."
    open val charactersNotDrawable = "Some of these characters cannot be drawn by the built-in PDF fonts and will show as ?."
    open val startAgain = "Start again"
    open val drawHint = "Zoom with + or choose Move page to pinch and pan. Resume editing to draw."
    open val thickness = "Thickness"
    open val undoStroke = "Undo stroke"
    open fun pendingEdits(count: Int) = "Pending edits ($count)"
    open val nothingYet = "Nothing yet."
    open val nothingWrittenYet = "Nothing has been written yet. The original file is untouched until you save."
    open val undoAll = "Undo all"
    open val insert = "Insert"
    open fun toInsert(pages: Int) = "${this.pages(pages)} to insert"
    open fun docPages(name: String, pages: Int) = "$name — ${this.pages(pages)}"
    open val where = "Where"
    open val insertAfter = "After this page"
    open val insertStart = "At the start"
    open val insertEnd = "At the end"
    open val insertHint =
        "They come in as ordinary pages — rotate, reorder, crop, mark or delete them like any other before you save."

    open val colourBlack = "Black"
    open val colourBlue = "Blue"
    open val colourRed = "Red"
    open val colourGreen = "Green"
    open val colourWhite = "White"
    open val colourYellow = "Yellow"
    open val colourPink = "Pink"
    open val colourGrey = "Grey"

    // Forms.
    open val readingForm = "Reading the form…"
    open val xfaOnly =
        "This form is built with XFA, a format only Adobe's apps can fill in. It has no fields " +
            "AnonPDF can fill. To fill it here, cover the blanks with text instead."
    open val noFieldsHere = "No fields on this page."
    open fun fieldsOnPage(number: Int) = "Fields on page $number"
    open val lockAnswers = "Lock the answers when saving"
    open val lockAnswersHint = "They become part of the page, and nobody can change them afterwards, in any app."
    open val choose = "Choose…"
    open fun couldNotFill(field: String) = "Could not fill in \"$field\"."

    // --------------------------------------------------------- pending edits

    open fun pagesScope(count: Int, list: String) = if (count == 1) "page $list" else "pages $list"
    open fun editRotate(degrees: Int, scope: String) = "Rotate $degrees° · $scope"
    open fun editCrop(amount: String, scope: String) = "Crop $amount · $scope"
    open fun editScale(percent: Int, scope: String) = "Scale $percent% · $scope"
    open fun editResize(paper: String, orientation: String?, fill: Boolean, scope: String) =
        "Resize to $paper${orientation?.let { " $it" } ?: ""} (${if (fill) "fill" else "fit"}) · $scope"
    open val portraitLower = "portrait"
    open val landscapeLower = "landscape"
    open fun editDelete(scope: String) = "Delete · $scope"
    open fun editRestore(scope: String) = "Restore · $scope"
    open fun editMove(page: Int, earlier: Boolean) = "Move page $page ${if (earlier) "earlier" else "later"}"
    open fun editInsert(name: String, pages: Int) = "Insert \"$name\" (${this.pages(pages)})"
    open val editReverse = "Reverse page order"
    open fun editWatermark(text: String) = "Watermark \"$text\""
    open val editRemoveWatermark = "Remove watermark"
    open fun editPageNumbers(format: String) = "Page numbers \"$format\""
    open val editRemovePageNumbers = "Remove page numbers"
    open fun editAdd(what: String) = "Add $what"
    open fun editEdit(what: String) = "Edit $what"
    open val editRemoveContent = "Remove added content"
    open fun editFillForm(fields: Int, lock: Boolean) =
        (if (fields == 1) "Fill 1 field" else "Fill $fields fields") + if (lock) " · lock the form" else ""
    open fun markText(snippet: String, page: Int) = "Text \"$snippet\" · page $page"
    open fun markHighlight(page: Int) = "Highlight · page $page"
    open fun markWhiteout(page: Int) = "White-out · page $page"
    open fun markDrawing(strokes: Int, page: Int) =
        "Drawing (${if (strokes == 1) "1 stroke" else "$strokes strokes"}) · page $page"

    // ----------------------------------------------------------- page ranges

    open val noPagesInDocument = "This document has no pages."
    open val enterPages = "Enter which pages you want, for example 1-3, 5."
    open fun notARange(part: String) = "\"$part\" is not a page range."
    open fun notAPageNumber(part: String) = "\"$part\" is not a page number."
    open fun runsBackwards(part: String) = "\"$part\" runs backwards."
    open fun onlyHasPages(count: Int) = "This document only has ${pages(count)}."
    open val rangeNone = "none"

    // ---------------------------------------------------------- engine errors

    open val keepOnePage = "Keep at least one page."
    open val noDocument = "No document to work on."
    open fun pageNotInDocument(page: Int) = "Page $page is not in this document."
    open val pickTwoPdfs = "Pick at least two PDFs to merge."
    open val pdfNeedsAPage = "A PDF needs at least one page."
    open val describeRange = "Describe at least one page range."
    open fun rangeCoversNothing(index: Int) = "Range $index does not cover any pages."
    open val enterWatermark = "Enter some watermark text."
    open val cannotDrawText = "That text cannot be drawn with the built-in fonts."
    open val nothingToPlace = "Nothing to place."
    open val choosePassword = "Choose a password."
    open val notProtected = "This PDF is not password protected."
    open val noTextExtraction = "This PDF does not allow text extraction."
    open val pickOneImage = "Pick at least one image."
    open fun couldNotReadImage(index: Int) = "Could not read image $index."

    open val paperLetter = "Letter"
    open val paperLegal = "Legal"
    open val paperTabloid = "Tabloid"
    open val orientationAuto = "Match page"
    open val orientationPortrait = "Portrait"
    open val orientationLandscape = "Landscape"

    open val layoutDiagonal = "Diagonal"
    open val layoutCenter = "Center"
    open val layoutTiled = "Tiled"
    open val layoutTop = "Top"
    open val layoutBottom = "Bottom"
    open val positionTopLeft = "Top left"
    open val positionTopCenter = "Top center"
    open val positionTopRight = "Top right"
    open val positionBottomLeft = "Bottom left"
    open val positionBottomCenter = "Bottom center"
    open val positionBottomRight = "Bottom right"

    // ------------------------------------------------------------------ tools

    open val groupWholeFiles = "Whole files"
    open val groupConvertCompress = "Convert & compress"
    open val groupMarkup = "Markup"
    open val groupSecurity = "Security"

    open val mergeTitle = "Merge PDFs"
    open val mergeSummary = "Join several files into one, in the order you pick them"
    open val splitTitle = "Split PDF"
    open val splitSummary = "Break one document into several files by page range"
    open val extractTitle = "Extract pages"
    open val extractSummary = "Pull chosen pages out into a single new PDF"
    open val compressTitle = "Compress PDF"
    open val compressSummary = "Make the file smaller by re-encoding its images"
    open val toImagesTitle = "PDF to images"
    open val toImagesSummary = "Save every page as a JPG or PNG"
    open val fromImagesTitle = "Images to PDF"
    open val fromImagesSummary = "Build a PDF from photos or scans"
    open val extractTextTitle = "Extract text"
    open val extractTextSummary = "Pull the text out into a plain .txt file"
    open val extractTextCaveat =
        "Only finds real text. Scanned pages are pictures, so they come out empty — reading " +
            "them would need OCR, which AnonPDF does not do."
    open val signTitle = "Sign PDF"
    open val signSummary = "Draw your signature and place it on a page"
    open val signCaveat = "This draws your signature onto the page as an image. It is not a cryptographic digital signature."
    open val protectTitle = "Protect PDF"
    open val protectSummary = "Add a password, with AES-256 encryption"
    open val unlockTitle = "Remove password"
    open val unlockSummary = "Strip protection from a PDF you know the password to"

    open val images_ = "Images"
    open val document = "Document"
    open val addPdfs = "Add PDFs"
    open val addImages = "Add images"
    open val addOneMore = "Add at least one more file. They are merged in the order listed above."
    open fun imageNumber(number: Int) = "Image $number"
    open val locked = "locked"
    open val unlocked = "unlocked"
    open val mergeOrderHint = "Files are merged top to bottom. Remove and re-add a file to change its place."
    open val howToSplit = "How to split"
    open val byPageRanges = "By page ranges"
    open val everyNPages = "Every N pages"
    open val oneFilePerRange = "One file per range"
    open val pagesPerFile = "Pages per file"
    open val pagesToKeep = "Pages to keep"
    open val mode = "Mode"
    open val keepText = "Keep text"
    open val keepTextHint =
        "Re-encodes the pictures inside the PDF and leaves text and vector artwork alone. The " +
            "document stays selectable and searchable."
    open val smallestFile = "Smallest file"
    open val smallestFileHint =
        "Redraws every page as a flat image. This gives the biggest saving, but text stops being " +
            "selectable or searchable afterwards."
    open val strength = "Strength"
    open val strengthLight = "Light"
    open val strengthBalanced = "Balanced"
    open val strengthStrong = "Strong"
    open val resolution = "Resolution"
    open val jpegQuality = "JPEG quality"
    open val pageSize = "Page size"
    open val fitImage = "Fit image"
    open val margin = "Margin"
    open val marginNone = "None"
    open val marginSlim = "Slim"
    open val marginMedium = "Medium"
    open val marginWide = "Wide"
    open val textToTxt = "Text is written to a .txt file in reading order."
    open val newPassword = "New password"
    open val confirmPassword = "Confirm password"
    open val allowReadersTo = "Allow readers to"
    open val allowPrint = "Print"
    open val allowCopy = "Copy text"
    open val allowEdit = "Edit"
    open val protectHint =
        "AES-256 encryption, done on this device. AnonPDF never stores or transmits the password — " +
            "if you lose it the file cannot be opened. The print, copy and edit limits are honoured " +
            "by standard PDF readers, but anyone with the password can use a tool to lift them."
    open val unlockHint = "The password you entered when opening the file is used to remove its protection."
    open fun documentHasPages(count: Int) = "This document has ${pages(count)}"
    open val allPagesButton = "All pages"
    open val first = "First"
    open val last = "Last"

    open fun hasOwnScreen(title: String) = "$title has its own screen and does not run here."
    open fun removePasswordFirst(name: String) = "Remove the password from \"$name\" first, then merge."
    open fun mergedNote(files: Int, size: String) = "$files files joined into $size."
    open fun splitDoesNotDivide(size: Int, pages: Int) =
        "Splitting every ${pages(size)} would not divide a $pages-page document."
    open fun filesReady(count: Int) = "${files(count)} ready."
    open fun keptPages(count: Int, list: String) = (if (count == 1) "Kept page $list." else "Kept pages $list.")
    open fun alreadyLean(size: String, smart: Boolean) =
        "No saving available in this mode — the file is already lean. $size." +
            if (smart) " Try \"$smallestFile\" if you do not need selectable text." else ""
    open fun compressedNote(before: String, after: String, percent: Int) = "$before → $after ($percent% smaller)."
    open fun imagesAtDpi(count: Int, dpi: Int) = "${images(count)} at $dpi dpi."
    open fun imagesToPdfNote(count: Int, size: String) = "${images(count)} → $size."
    open val probablyScans = "Almost no text found. These pages are probably scans — images of text rather than text itself."
    open fun charactersExtracted(count: Int) = "$count characters extracted."
    open val passwordsDoNotMatch = "The two passwords do not match."
    open val protectedNote = "Encrypted with AES-256. There is no recovery if you forget this password — nothing about it leaves your device."
    open val openWithPasswordFirst = "Open the file with its password first."
    open val unlockedNote = "Password removed. Keep the new file somewhere safe."
    open fun stillPreparing(name: String) = "\"$name\" is still being prepared."
    open val fileMissing = "File is missing."

    // ------------------------------------------------------------------- sign

    open val signIntro = "Draw your signature and drop it onto a page. Everything happens on this device."
    open val drawYourSignature = "1. Draw your signature"
    open val placeIt = "2. Place it"
    open val ink = "Ink"
    open val tapWhereToSign = "Tap or click the page where you want your signature."
    open val signatureWidth = "Signature width"
    open fun percentOfPage(percent: Int) = "$percent% of page"
    open val drawSignatureFirst = "Draw a signature first."
    open val addingSignature = "Adding your signature…"
    open fun signaturePlaced(page: Int) = "Signature placed on page $page."
    open val couldNotSign = "Could not sign the document."
    open val signAndSave = "Sign and save"
    open val signDisclaimer =
        "This stamps a picture of your signature onto the page. It is not a cryptographic digital " +
            "signature, so it proves no more than ink on paper does."
    open val onlyPdfSigned = "Only a PDF can be signed."
    open val signaturePad = "Signature pad"
    open val signature = "Signature"
    open val page = "Page"

    // --------------------------------------------------------- Android: home

    open val openAPdf = "Open a PDF"
    open val myFiles = "My files"
    open val editPages = "Edit pages"
    open val recent = "Recent"
    open val homeTagline = "No ads, no analytics, no account, no network permission. Open source."
    open val nothingLeavesPhone = "Nothing leaves this phone"
    open val nothingLeavesPhoneBody =
        "AnonPDF has no network permission at all, so Android will not let it upload your " +
            "documents even if something tried. No account, no ads, no tracking."
    open val editAPdf = "Edit a PDF"
    open val editAPdfBody =
        "Every tool in one place: rotate, crop, resize, delete and reorder pages, add text, " +
            "white-out, highlights or drawings — several at once, with a live preview of the real " +
            "result before you save."

    // ------------------------------------------------------- Android: browse

    open val browse = "Browse"
    open val rescan = "Rescan"
    open val allPdfs = "All PDFs"
    open val folders = "Folders"
    open val needFileAccess =
        "To list the PDFs on your device, AnonPDF needs Android's \"All files access\". It is " +
            "a switch in Settings rather than a normal permission prompt.\n\nThis only lets the app " +
            "read files. It still has no network permission, so nothing can leave this device either way."
    open val couldNotOpenSettings = "Could not open Settings on this device."
    open val openSettingsAllow = "Open Settings and allow file access"
    open val justPickOne = "Just pick one file instead"
    open val searchByName = "Search by name"
    open val looking = "Looking…"
    open val noMatches = "No matches"
    open fun pdfCount(count: Int) = if (count == 1) "1 PDF" else "$count PDFs"
    open val nothingHere = "Nothing here."
    open val unknownDate = "unknown date"
    open val justNow = "just now"
    open fun minutesAgo(n: Long) = "$n min ago"
    open fun hoursAgo(n: Long) = if (n == 1L) "1 hour ago" else "$n hours ago"
    open fun daysAgo(n: Long) = if (n == 1L) "1 day ago" else "$n days ago"
    open fun monthsAgo(n: Long) = if (n == 1L) "1 month ago" else "$n months ago"
    open fun yearsAgo(n: Long) = if (n == 1L) "1 year ago" else "$n years ago"

    // ------------------------------------------------------- Android: viewer

    open val searchText = "Search text"
    open val edit = "Edit"
    open val invertColours = "Invert colours"
    open val goToPage = "Go to page"
    open val pageNumberLabel = "Page number"
    open fun oneTo(count: Int) = "1 to $count"
    open val go = "Go"
    open val find = "Find"
    open val readingDocument = "Reading the document…"
    open val noMatchesDot = "No matches."
    open val couldNotReadText = "Could not read the text."
    open val search = "Search"

    // ----------------------------------------------------- Android: settings

    open val reading = "Reading"
    open val invertPageColours = "Invert page colours"
    open val invertPageColoursHint = "Dark background for night reading. Only changes how pages are drawn, never the file."
    open val keepScreenOn = "Keep the screen on while reading"
    open val keepScreenOnHint = "Stops the display sleeping in the viewer."
    open val privacy = "Privacy"
    open val rememberRecent = "Remember recent files"
    open val rememberRecentHint =
        "Keeps up to 20 file names on this device so you can reopen them quickly. Switch this off " +
            "and the list is deleted immediately."
    open val recentCleared = "Recent files cleared."
    open val clearRecent = "Clear recent files"
    open val tempDeleted = "Temporary files deleted."
    open val deleteTemp = "Delete temporary working files"
    open val nothingElseToConfigure =
        "There is nothing else to configure, because there is nothing else to switch off. AnonPDF " +
            "has no account, no sync, no ads and no network permission."
    open val language = "Language"
    open val languageHint = "AnonPDF follows your device's language. Change it for this app alone in Android's settings."
    open val chooseLanguage = "Choose language"

    // -------------------------------------------------------- Android: about

    open fun version(name: String) = "Version $name"
    open val aboutTagline = "A PDF reader and editor that does its work on your device and nowhere else."
    open val cannotDo = "What this app cannot do"
    open val cannotNetwork =
        "Reach the network. AnonPDF declares no INTERNET permission, so Android will refuse any " +
            "connection it tried to make. There is no server to upload to and no way to reach one."
    open val cannotTrack =
        "Track you. No analytics, no crash reporting, no advertising SDK, no device identifiers, no account."
    open val cannotCharge = "Charge you. Every tool is included. There is no paid tier and no feature held back."
    open val onePermission = "The one permission it asks for"
    open val permissionWhy =
        "All files access, so the browser can list the PDFs on your device instead of making you " +
            "find each one in the system picker. It is used for reading documents and nothing else."
    open val permissionOptional =
        "It is optional. Decline it and everything still works through the picker, one file at a time."
    open val permissionWhere =
        "Android calls this a \"special app access\", so it does not appear on this app's " +
            "Permissions page — that page says \"no permissions requested\" either way. Look " +
            "under Settings, Apps, Special app access, All files access to see the real state."
    open val permissionCannotLeak = "Reading files still cannot leak them: without network permission there is nowhere for them to go."
    open val whatItStores = "What it stores"
    open val storesRecent =
        "A list of recently opened file names, kept in app-private storage and excluded from cloud " +
            "backup. You can switch it off or clear it in Settings."
    open val storesWorking =
        "Working copies of the file you are editing, in the app cache. These are temporary and " +
            "Android may delete them at any time."
    open val storesPrefs = "Your display preferences. That is the entire list."
    open val noTrustNeeded =
        "You do not have to take any of this on trust. The source is public and the permission " +
            "list is visible in the app's Play Store entry and in Android's own app info screen."
    open val viewSource = "View the source code"
    open val licences = "Open source licences"
    open val licenceMit = "MIT Licence. Copyright (c) 2026 the AnonPDF contributors."
    open val licencePdfBox =
        "Apache License 2.0. Copyright the Apache Software Foundation and Tom Roush. Used for " +
            "reading and writing PDF structure."
    open val licenceAndroidX = "Apache License 2.0. Copyright The Android Open Source Project."
    open val licenceKotlin = "Apache License 2.0. Copyright JetBrains s.r.o."
    open val cryptoNote =
        "Password protection uses AES-256 through Android's own cryptography libraries. No " +
            "third-party crypto library is bundled."
    open val translationsNote =
        "Translations other than English were written with machine help. Corrections are welcome " +
            "on the project's GitHub page."

    // --------------------------------------------------------------- desktop

    open val whatWouldYouLikeToDo = "What would you like to do?"
    open val desktopEditBody =
        "Rotate, reorder, delete or crop pages, add pages from other PDFs, fill, highlight, draw, " +
            "add text, a watermark or page numbers. See every change before you save a new copy."
    open val openAPdfEllipsis = "Open a PDF…"
    open val desktopPrivacy =
        "AnonPDF works on this computer only. A file you open is copied into a private scratch " +
            "folder that only you can read, and that folder is deleted when the app closes. Nothing " +
            "is uploaded, and nothing is written back to your files until you choose where to save."
    open val filesStayOnComputer = "Files stay on this computer"

    open val file = "File"
    open val tools = "Tools"
    open val openPdfToEdit = "Open PDF to edit…"
    open val closeDocument = "Close document"
    open val quit = "Quit"
    open val start = "Start"
    open val dropStart = "Drop a PDF to edit it, several to merge them, or pictures to make a PDF"
    open val dropEditor = "Drop a PDF to open it, or to add its pages to the one open"
    open fun dropForTool(title: String) = "Drop a PDF for $title"
    open fun dropToAdd(title: String) = "Drop PDFs to add them to $title"
    open val dropPictures = "Drop pictures to add them"
    open val saveAsDialog = "Save as"
    open val saveAllDialog = "Save all to folder"
}
