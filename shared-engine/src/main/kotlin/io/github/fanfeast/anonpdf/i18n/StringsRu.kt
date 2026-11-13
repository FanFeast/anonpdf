package io.github.fanfeast.anonpdf.i18n

/** Russian. */
internal object StringsRu : Strings() {

    /** Russian counts take one of three forms: 1 страница, 2 страницы, 5 страниц. */
    private fun plural(count: Long, one: String, few: String, many: String): String {
        val tens = count % 100
        val units = count % 10
        val form = when {
            tens in 11..14 -> many
            units == 1L -> one
            units in 2..4 -> few
            else -> many
        }
        return "$count $form"
    }

    private fun plural(count: Int, one: String, few: String, many: String) =
        plural(count.toLong(), one, few, many)

    override val cancel = "Отмена"
    override val apply = "Применить"
    override val close = "Закрыть"
    override val clear = "Очистить"
    override val remove = "Убрать"
    override val undo = "Отменить"
    override val redo = "Повторить"
    override val back = "Назад"
    override val done = "Готово"
    override val saveAs = "Сохранить как…"
    override val saveAllToFolder = "Сохранить всё в папку…"
    override val share = "Поделиться"
    override val startOver = "Начать заново"
    override val choosePdf = "Выбрать PDF"
    override val none = "Нет"
    override val all = "Все"
    override val options = "Параметры"
    override val settings = "Настройки"
    override val about = "О приложении"
    override val saved = "Сохранено."
    override fun savedTo(name: String) = "Сохранено как $name."
    override fun savedFiles(count: Int) = "Сохранено файлов: $count."
    override val couldNotSaveFile = "Не удалось сохранить файл."
    override val couldNotSaveToFolder = "Не удалось сохранить в эту папку."
    override val folderNotWritable = "В эту папку нельзя записывать."
    override fun couldNotCreate(name: String) = "Не удалось создать $name."
    override val nothingCanReceive = "На этом устройстве нет приложения, которое может принять этот файл."
    override val couldNotOpenFile = "Не удалось открыть файл."
    override val notAPdf = "Этот файл не удалось открыть как PDF."
    override val notAPdfThat = "Этот файл не удалось открыть как PDF."
    override val loading = "Загрузка…"
    override val working = "Обработка…"
    override val readingFile = "Чтение файла…"
    override val thatDidNotWork = "Не получилось."
    override val onlyPdfsOpen = "Здесь можно открывать только PDF."
    override val onlyPdfsAdd = "Здесь можно добавлять только PDF."
    override val onlyOnePdf = "Здесь можно использовать только PDF."
    override val onlyPictures = "Здесь можно добавлять только изображения: JPEG, PNG, GIF или BMP."
    override val pagePreview = "Предпросмотр страницы"
    override val previousPage = "Предыдущая страница"
    override val nextPage = "Следующая страница"
    override val zoomIn = "Увеличить"
    override val zoomOut = "Уменьшить"
    override val unknownSize = "размер неизвестен"

    override fun pages(count: Int) = plural(count, "страница", "страницы", "страниц")
    override fun files(count: Int) = plural(count, "файл", "файла", "файлов")
    override fun images(count: Int) = plural(count, "изображение", "изображения", "изображений")
    override fun edits(count: Int) = plural(count, "изменение", "изменения", "изменений")
    override fun pageNumber(number: Int) = "страница $number"
    override fun pageTitle(number: Int) = "Страница $number"
    override fun pageOf(number: Int, total: Int) = "страница $number / $total"

    override val bytesUnit = "Б"
    override val kilobytesUnit = "КБ"
    override val megabytesUnit = "МБ"
    override val gigabytesUnit = "ГБ"

    override val passwordNeeded = "Нужен пароль"
    override val passwordPrompt = "Этот PDF защищён. Введите пароль, чтобы продолжить."
    override val password = "Пароль"
    override val unlock = "Открыть"
    override val enterPassword = "Ввести пароль"
    override val wrongPassword = "Пароль не подошёл."
    override val passwordProtected = "Этот PDF защищён паролем."
    override fun protectedNamed(name: String) = "«$name» защищён. Введите пароль, чтобы продолжить."
    override val mergePasswordPrompt =
        "Один из выбранных PDF защищён. Введите пароль, чтобы объединить его, или отмените, чтобы пропустить этот файл."

    override val removeHiddenInfo = "Удалить скрытые сведения"
    override fun hiddenAuthor(name: String) = "Автор: $name"
    override fun hiddenCommentAuthors(names: String) = "Авторы комментариев: $names"
    override fun hiddenMadeWith(app: String) = "Создано в: $app"
    override fun hiddenPdfBy(app: String) = "PDF создан: $app"
    override fun hiddenCreated(date: String) = "Создан $date"
    override fun hiddenModified(date: String) = "Изменён $date"
    override fun hiddenTitle(title: String) = "Заголовок: $title"
    override fun hiddenSubject(subject: String) = "Тема: $subject"
    override fun hiddenKeywords(keywords: String) = "Ключевые слова: $keywords"
    override fun hiddenOtherProperties(count: Int) =
        "ещё " + plural(count, "свойство", "свойства", "свойств")
    override fun hiddenAttachments(count: Int) =
        plural(count, "вложенный файл", "вложенных файла", "вложенных файлов")
    override fun hiddenScripts(count: Int) = plural(count, "скрипт", "скрипта", "скриптов")
    override val hiddenXmp = "метаданные XMP"
    override val hiddenEditorData = "данные редактора"
    override val couldNotRemoveHiddenInfo = "Не удалось удалить скрытые сведения."

    override val editPdf = "Редактировать PDF"
    override val editorIntro =
        "Поворачивайте, обрезайте, меняйте размер, удаляйте и переставляйте страницы, добавляйте " +
            "текст, замазку, выделение или рисунки и открывайте отсюда любой другой инструмент — " +
            "всегда с предпросмотром настоящего результата. Ничего не записывается, пока вы не сохраните."
    override val revertAll = "Отменить всё"
    override val noEditsYet = "Изменений пока нет"
    override val applyingEditsFirst = "Сначала применяются ваши изменения…"
    override val couldNotPrepare = "Не удалось подготовить документ."
    override val writingDocument = "Запись документа…"
    override val couldNotSave = "Не удалось сохранить документ."
    override fun savedNote(pages: Int, edits: Int) = "${this.pages(pages)}, применено: ${this.edits(edits)}."
    override val finishFirst = "Сначала закончите текущее действие, затем перетащите файл снова."
    override val everyPageDeleted = "Все страницы удалены.\nВосстановите одну, чтобы продолжить."
    override val resumeEditing = "Вернуться к правке"
    override val movePage = "Двигать страницу"
    override val tapToSelect = "Нажмите, чтобы выбрать. Нажмите и удерживайте, затем перетащите, чтобы переставить."
    override val holdToReorder = "Нажмите и удерживайте страницу, затем перетащите, чтобы переставить."
    override val deletedPage = "Удалённая страница"
    override val deleted = "Удалена"
    override fun resultSize(width: Int, height: Int) = "Результат: $width × $height pt"
    override val hasFormPrompt = "В этом PDF есть форма: заполнить"

    override val scopeThisPage = "Эта страница"
    override val scopeSelected = "Выбранные"
    override val scopeAllPages = "Все страницы"
    override val noPages = "нет страниц"
    override fun allPages(count: Int) = "все страницы ($count)"
    override fun pagesList(list: String) = "страницы $list"

    override val groupPages = "Страницы"
    override val groupContent = "Содержимое"
    override val groupWholeFile = "Весь файл"
    override val groupConvertLock = "Преобразовать и защитить"

    override val toolLeft = "Влево"
    override val toolRight = "Вправо"
    override val toolCrop = "Обрезать"
    override val toolResize = "Размер"
    override val toolDelete = "Удалить"
    override val toolRestore = "Вернуть"
    override val toolReverse = "Обратно"
    override val toolFillForm = "Форма"
    override val toolText = "Текст"
    override val toolWhiteout = "Замазать"
    override val toolHighlight = "Выделить"
    override val toolDraw = "Рисовать"
    override val toolWatermark = "Водяной знак"
    override val toolNumbers = "Номера"
    override val toolMerge = "Объединить"
    override val toolSplit = "Разделить"
    override val toolExtract = "Извлечь"
    override val toolCompress = "Сжать"
    override val toolToImages = "В картинки"
    override val toolFromImages = "Из картинок"
    override val toolGetText = "Взять текст"
    override val toolSign = "Подписать"
    override val toolProtect = "Защитить"
    override val toolUnlock = "Снять пароль"

    override val applyCrop = "Применить обрезку"
    override val cropHint =
        "Перетащите рамку на странице выше. Края отсчитываются от страницы так, как вы её видите, " +
            "поэтому повёрнутая страница обрезается так, как выглядит."
    override fun cropEdges(left: String, top: String, right: String, bottom: String) =
        "Л $left   В $top   П $right   Н $bottom"
    override val cropAllRound = "6% со всех сторон"
    override val sizePaper = "Формат бумаги"
    override val sizePercent = "Проценты"
    override val resizePaperHint = "Помещает страницу на лист точно этого формата. Результат виден в предпросмотре выше."
    override val sheet = "Лист"
    override val orientation = "Ориентация"
    override val whereTheyDoNotMatch = "Если пропорции не совпадают"
    override val fillCropOverflow = "Заполнить, обрезать лишнее"
    override val fitLeaveMargins = "Вписать, оставить поля"
    override val resizePercentHint =
        "Сохраняет пропорции страницы и меняет её размер. Предпросмотр меняется, пока вы тянете ползунок."
    override val size = "Размер"
    override val text = "Текст"
    override val placement = "Расположение"
    override val opacity = "Непрозрачность"
    override val textSize = "Размер текста"
    override val every_page = "все страницы"
    override val pageNumbers = "Номера страниц"
    override val format = "Формат"
    override val formatHint = "{n} — номер, {total} — общее число"
    override val numberPresetPage = "Страница {n}"
    override val position = "Положение"
    override val startNumberingAt = "Начать нумерацию с"
    override val skipFirstPages = "Пропустить первые страницы"
    override val addText = "Добавить текст"
    override val dragMarker = "Перетащите метку на странице, чтобы переместить текст."
    override val tapToPlace = "Нажмите на страницу выше, чтобы выбрать, где начнётся текст."
    override val colour = "Цвет"
    override val weight = "Начертание"
    override val bold = "Жирный"
    override val regular = "Обычный"
    override fun alreadyAdded(count: Int) =
        "На эту страницу уже добавлено: ${plural(count, "элемент", "элемента", "элементов")}. «Убрать» удаляет их."
    override val highlightHint = "Проведите по тексту, который нужно выделить. Перетащите рамку изнутри, чтобы сдвинуть её."
    override val whiteoutHint =
        "Нарисуйте рамку поверх того, что нужно убрать. Закрытое ею удаляется навсегда: страница " +
            "сохраняется как изображение, поэтому её текст больше нельзя выделить или найти поиском. " +
            "Чтобы изменить текст, закройте его и напишите свой поверх."
    override val startAgain = "Начать сначала"
    override val drawHint = "Увеличьте кнопкой + или выберите «Двигать страницу», чтобы масштабировать и сдвигать. «Вернуться к правке» — чтобы рисовать."
    override val thickness = "Толщина"
    override val undoStroke = "Отменить штрих"
    override fun pendingEdits(count: Int) = "Ожидающие изменения ($count)"
    override val nothingYet = "Пока ничего."
    override val nothingWrittenYet = "Ещё ничего не записано. Исходный файл не меняется, пока вы не сохраните."
    override val undoAll = "Отменить всё"
    override val insert = "Вставить"
    override fun toInsert(pages: Int) = "для вставки: ${this.pages(pages)}"
    override fun docPages(name: String, pages: Int) = "$name — ${this.pages(pages)}"
    override val where = "Куда"
    override val insertAfter = "После этой страницы"
    override val insertStart = "В начало"
    override val insertEnd = "В конец"
    override val insertHint =
        "Они добавляются как обычные страницы — поворачивайте, переставляйте, обрезайте, отмечайте " +
            "или удаляйте их, как любые другие, до сохранения."

    override val colourBlack = "Чёрный"
    override val colourBlue = "Синий"
    override val colourRed = "Красный"
    override val colourGreen = "Зелёный"
    override val colourWhite = "Белый"
    override val colourYellow = "Жёлтый"
    override val colourPink = "Розовый"
    override val colourGrey = "Серый"

    override val readingForm = "Чтение формы…"
    override val xfaOnly =
        "Эта форма сделана в XFA — формате, который заполняют только приложения Adobe. В ней нет " +
            "полей, которые AnonPDF может заполнить. Чтобы заполнить её здесь, закройте пустые места текстом."
    override val noFieldsHere = "На этой странице нет полей."
    override fun fieldsOnPage(number: Int) = "Поля на странице $number"
    override val lockAnswers = "Закрепить ответы при сохранении"
    override val lockAnswersHint = "Они станут частью страницы, и никто не сможет изменить их потом ни в одном приложении."
    override val choose = "Выбрать…"
    override fun couldNotFill(field: String) = "Не удалось заполнить «$field»."

    override fun pagesScope(count: Int, list: String) = if (count == 1) "страница $list" else "страницы $list"
    override fun editRotate(degrees: Int, scope: String) = "Поворот на $degrees° · $scope"
    override fun editCrop(amount: String, scope: String) = "Обрезка $amount · $scope"
    override fun editScale(percent: Int, scope: String) = "Масштаб $percent% · $scope"
    override fun editResize(paper: String, orientation: String?, fill: Boolean, scope: String) =
        "Размер $paper${orientation?.let { ", $it" } ?: ""} (${if (fill) "заполнить" else "вписать"}) · $scope"
    override val portraitLower = "книжная"
    override val landscapeLower = "альбомная"
    override fun editDelete(scope: String) = "Удалить · $scope"
    override fun editRestore(scope: String) = "Вернуть · $scope"
    override fun editMove(page: Int, earlier: Boolean) =
        "Переместить страницу $page ${if (earlier) "ближе к началу" else "ближе к концу"}"
    override fun editInsert(name: String, pages: Int) = "Вставить «$name» (${this.pages(pages)})"
    override val editReverse = "Обратный порядок страниц"
    override fun editWatermark(text: String) = "Водяной знак «$text»"
    override val editRemoveWatermark = "Убрать водяной знак"
    override fun editPageNumbers(format: String) = "Номера страниц «$format»"
    override val editRemovePageNumbers = "Убрать номера страниц"
    override fun editAdd(what: String) = "Добавить: $what"
    override fun editEdit(what: String) = "Изменить: $what"
    override val editRemoveContent = "Убрать добавленное"
    override fun editFillForm(fields: Int, lock: Boolean) =
        "Заполнить: ${plural(fields, "поле", "поля", "полей")}" + if (lock) " · закрепить форму" else ""
    override fun markText(snippet: String, page: Int) = "текст «$snippet» · страница $page"
    override fun markHighlight(page: Int) = "выделение · страница $page"
    override fun markWhiteout(page: Int) = "замазка · страница $page"
    override fun markDrawing(strokes: Int, page: Int) =
        "рисунок (${plural(strokes, "штрих", "штриха", "штрихов")}) · страница $page"

    override val noPagesInDocument = "В этом документе нет страниц."
    override val enterPages = "Укажите нужные страницы, например 1-3, 5."
    override fun notARange(part: String) = "«$part» — не диапазон страниц."
    override fun notAPageNumber(part: String) = "«$part» — не номер страницы."
    override fun runsBackwards(part: String) = "«$part» идёт в обратном порядке."
    override fun onlyHasPages(count: Int) = "В этом документе всего ${pages(count)}."
    override val rangeNone = "нет"

    override val keepOnePage = "Оставьте хотя бы одну страницу."
    override val noDocument = "Нет документа для работы."
    override fun pageNotInDocument(page: Int) = "Страницы $page нет в этом документе."
    override val pickTwoPdfs = "Выберите хотя бы два PDF для объединения."
    override val pdfNeedsAPage = "В PDF должна быть хотя бы одна страница."
    override val describeRange = "Укажите хотя бы один диапазон страниц."
    override fun rangeCoversNothing(index: Int) = "Диапазон $index не включает ни одной страницы."
    override val enterWatermark = "Введите текст водяного знака."
    override val cannotDrawText = "Этот текст нельзя вывести встроенными шрифтами."
    override val nothingToPlace = "Нечего размещать."
    override val choosePassword = "Придумайте пароль."
    override val notProtected = "Этот PDF не защищён паролем."
    override val noTextExtraction = "Этот PDF запрещает извлекать текст."
    override val pickOneImage = "Выберите хотя бы одно изображение."
    override fun couldNotReadImage(index: Int) = "Не удалось прочитать изображение $index."

    override val orientationAuto = "Как у страницы"
    override val orientationPortrait = "Книжная"
    override val orientationLandscape = "Альбомная"

    override val layoutDiagonal = "По диагонали"
    override val layoutCenter = "По центру"
    override val layoutTiled = "Плиткой"
    override val layoutTop = "Сверху"
    override val layoutBottom = "Снизу"
    override val positionTopLeft = "Сверху слева"
    override val positionTopCenter = "Сверху по центру"
    override val positionTopRight = "Сверху справа"
    override val positionBottomLeft = "Снизу слева"
    override val positionBottomCenter = "Снизу по центру"
    override val positionBottomRight = "Снизу справа"

    override val groupWholeFiles = "Файлы целиком"
    override val groupConvertCompress = "Преобразовать и сжать"
    override val groupMarkup = "Пометки"
    override val groupSecurity = "Безопасность"

    override val mergeTitle = "Объединить PDF"
    override val mergeSummary = "Соединить несколько файлов в один в выбранном порядке"
    override val splitTitle = "Разделить PDF"
    override val splitSummary = "Разбить документ на несколько файлов по диапазонам страниц"
    override val extractTitle = "Извлечь страницы"
    override val extractSummary = "Вынести выбранные страницы в новый PDF"
    override val compressTitle = "Сжать PDF"
    override val compressSummary = "Уменьшить файл, перекодировав изображения"
    override val toImagesTitle = "PDF в изображения"
    override val toImagesSummary = "Сохранить каждую страницу как JPG или PNG"
    override val fromImagesTitle = "Изображения в PDF"
    override val fromImagesSummary = "Собрать PDF из фотографий или сканов"
    override val extractTextTitle = "Извлечь текст"
    override val extractTextSummary = "Вынести текст в обычный файл .txt"
    override val extractTextCaveat =
        "Находит только настоящий текст. Отсканированные страницы — это картинки, они выйдут пустыми: " +
            "для них нужно распознавание (OCR), а AnonPDF его не делает."
    override val signTitle = "Подписать PDF"
    override val signSummary = "Нарисовать подпись и поставить её на страницу"
    override val signCaveat = "Подпись наносится на страницу как изображение. Это не криптографическая электронная подпись."
    override val protectTitle = "Защитить PDF"
    override val protectSummary = "Поставить пароль с шифрованием AES-256"
    override val unlockTitle = "Снять пароль"
    override val unlockSummary = "Снять защиту с PDF, пароль от которого вы знаете"

    override val images_ = "Изображения"
    override val document = "Документ"
    override val addPdfs = "Добавить PDF"
    override val addImages = "Добавить изображения"
    override val addOneMore = "Добавьте ещё хотя бы один файл. Они объединяются в порядке списка выше."
    override fun imageNumber(number: Int) = "Изображение $number"
    override val locked = "защищён"
    override val unlocked = "открыт"
    override val mergeOrderHint = "Файлы объединяются сверху вниз. Чтобы изменить место файла, уберите его и добавьте снова."
    override val howToSplit = "Как разделить"
    override val byPageRanges = "По диапазонам"
    override val everyNPages = "Каждые N страниц"
    override val oneFilePerRange = "Один файл на диапазон"
    override val pagesPerFile = "Страниц в файле"
    override val pagesToKeep = "Какие страницы оставить"
    override val mode = "Режим"
    override val keepText = "Сохранить текст"
    override val keepTextHint =
        "Перекодирует изображения внутри PDF, не трогая текст и векторную графику. Текст документа " +
            "по-прежнему можно выделять и искать."
    override val smallestFile = "Самый маленький файл"
    override val smallestFileHint =
        "Перерисовывает каждую страницу как плоское изображение. Это даёт наибольшую экономию, но " +
            "текст потом нельзя будет выделять или искать."
    override val strength = "Степень"
    override val strengthLight = "Лёгкая"
    override val strengthBalanced = "Средняя"
    override val strengthStrong = "Сильная"
    override val resolution = "Разрешение"
    override val jpegQuality = "Качество JPEG"
    override val pageSize = "Размер страницы"
    override val fitImage = "По изображению"
    override val margin = "Поля"
    override val marginNone = "Без полей"
    override val marginSlim = "Узкие"
    override val marginMedium = "Средние"
    override val marginWide = "Широкие"
    override val textToTxt = "Текст записывается в файл .txt в порядке чтения."
    override val newPassword = "Новый пароль"
    override val confirmPassword = "Повторите пароль"
    override val allowReadersTo = "Разрешить читателям"
    override val allowPrint = "Печатать"
    override val allowCopy = "Копировать текст"
    override val allowEdit = "Редактировать"
    override val protectHint =
        "Шифрование AES-256 выполняется на этом устройстве. AnonPDF никогда не хранит и не передаёт " +
            "пароль — если вы его потеряете, файл нельзя будет открыть. Обычные программы для PDF " +
            "соблюдают запреты на печать, копирование и правку, но любой, кто знает пароль, может снять их."
    override val unlockHint = "Для снятия защиты используется пароль, который вы ввели при открытии файла."
    override fun documentHasPages(count: Int) = "В этом документе ${pages(count)}"
    override val allPagesButton = "Все страницы"
    override val first = "Первая"
    override val last = "Последняя"

    override fun hasOwnScreen(title: String) = "У инструмента «$title» свой экран, здесь он не запускается."
    override fun removePasswordFirst(name: String) = "Сначала снимите пароль с «$name», затем объединяйте."
    override fun mergedNote(files: Int, size: String) = "Объединено ${this.files(files)}, итог: $size."
    override fun splitDoesNotDivide(size: Int, pages: Int) =
        "Деление по ${pages(size)} не разбивает документ из ${this.pages(pages)}."
    override fun filesReady(count: Int) = "Готово: ${files(count)}."
    override fun keptPages(count: Int, list: String) =
        if (count == 1) "Оставлена страница $list." else "Оставлены страницы $list."
    override fun alreadyLean(size: String, smart: Boolean) =
        "В этом режиме сжать не получится — файл уже компактный. $size." +
            if (smart) " Попробуйте «$smallestFile», если выделяемый текст не нужен." else ""
    override fun compressedNote(before: String, after: String, percent: Int) = "$before → $after (на $percent% меньше)."
    override fun imagesAtDpi(count: Int, dpi: Int) = "${images(count)}, $dpi dpi."
    override val probablyScans =
        "Текста почти нет. Скорее всего, это сканы — изображения текста, а не сам текст."
    override fun charactersExtracted(count: Int) = "Извлечено ${plural(count, "символ", "символа", "символов")}."
    override val passwordsDoNotMatch = "Пароли не совпадают."
    override val protectedNote =
        "Зашифровано AES-256. Если вы забудете этот пароль, восстановить его нельзя — ничего о нём не покидает ваше устройство."
    override val openWithPasswordFirst = "Сначала откройте файл с паролем."
    override val unlockedNote = "Пароль снят. Храните новый файл в надёжном месте."
    override fun stillPreparing(name: String) = "«$name» ещё готовится."
    override val fileMissing = "Файл не найден."

    override val signIntro = "Нарисуйте подпись и поставьте её на страницу. Всё происходит на этом устройстве."
    override val drawYourSignature = "1. Нарисуйте подпись"
    override val placeIt = "2. Поставьте её"
    override val ink = "Чернила"
    override val tapWhereToSign = "Нажмите на то место страницы, где должна быть подпись."
    override val signatureWidth = "Ширина подписи"
    override fun percentOfPage(percent: Int) = "$percent% страницы"
    override val drawSignatureFirst = "Сначала нарисуйте подпись."
    override val addingSignature = "Добавляем подпись…"
    override fun signaturePlaced(page: Int) = "Подпись поставлена на страницу $page."
    override val couldNotSign = "Не удалось подписать документ."
    override val signAndSave = "Подписать и сохранить"
    override val signDisclaimer =
        "Это ставит на страницу изображение вашей подписи. Это не криптографическая электронная " +
            "подпись, поэтому она доказывает не больше, чем чернила на бумаге."
    override val onlyPdfSigned = "Подписать можно только PDF."
    override val signaturePad = "Поле для подписи"
    override val signature = "Подпись"
    override val page = "Страница"

    override val openAPdf = "Открыть PDF"
    override val myFiles = "Мои файлы"
    override val editPages = "Править страницы"
    override val recent = "Недавние"
    override val homeTagline = "Без рекламы, аналитики, аккаунта и доступа к сети. Открытый код."
    override val nothingLeavesPhone = "Ничего не покидает телефон"
    override val nothingLeavesPhoneBody =
        "У AnonPDF вообще нет доступа к сети, поэтому Android не даст ему отправить ваши документы, " +
            "даже если бы что-то попыталось. Без аккаунта, без рекламы, без слежки."
    override val editAPdf = "Редактировать PDF"
    override val editAPdfBody =
        "Все инструменты в одном месте: поворачивайте, обрезайте, меняйте размер, удаляйте и " +
            "переставляйте страницы, добавляйте текст, замазку, выделение или рисунки — несколько " +
            "сразу, с живым предпросмотром настоящего результата перед сохранением."

    override val browse = "Обзор"
    override val rescan = "Искать заново"
    override val allPdfs = "Все PDF"
    override val folders = "Папки"
    override val needFileAccess =
        "Чтобы показать PDF на вашем устройстве, AnonPDF нужен «Доступ ко всем файлам» Android. " +
            "Это переключатель в Настройках, а не обычный запрос разрешения.\n\nОн позволяет только " +
            "читать файлы. Доступа к сети у приложения по-прежнему нет, так что ничего не может " +
            "покинуть устройство в любом случае."
    override val couldNotOpenSettings = "Не удалось открыть Настройки на этом устройстве."
    override val openSettingsAllow = "Открыть Настройки и разрешить доступ"
    override val justPickOne = "Лучше выбрать один файл"
    override val searchByName = "Поиск по имени"
    override val looking = "Поиск…"
    override val noMatches = "Ничего не найдено"
    override fun pdfCount(count: Int) = "$count PDF"
    override val nothingHere = "Здесь ничего нет."
    override val unknownDate = "дата неизвестна"
    override val justNow = "только что"
    override fun minutesAgo(n: Long) = "$n мин назад"
    override fun hoursAgo(n: Long) = plural(n, "час", "часа", "часов") + " назад"
    override fun daysAgo(n: Long) = plural(n, "день", "дня", "дней") + " назад"
    override fun monthsAgo(n: Long) = plural(n, "месяц", "месяца", "месяцев") + " назад"
    override fun yearsAgo(n: Long) = plural(n, "год", "года", "лет") + " назад"

    override val searchText = "Поиск по тексту"
    override val edit = "Править"
    override val invertColours = "Инвертировать цвета"
    override val goToPage = "Перейти к странице"
    override val pageNumberLabel = "Номер страницы"
    override fun oneTo(count: Int) = "От 1 до $count"
    override val go = "Перейти"
    override val find = "Найти"
    override val readingDocument = "Чтение документа…"
    override val noMatchesDot = "Ничего не найдено."
    override val couldNotReadText = "Не удалось прочитать текст."
    override val search = "Искать"

    override val reading = "Чтение"
    override val invertPageColours = "Инвертировать цвета страниц"
    override val invertPageColoursHint = "Тёмный фон для чтения ночью. Меняет только отображение страниц, но не файл."
    override val keepScreenOn = "Не выключать экран при чтении"
    override val keepScreenOnHint = "Экран не гаснет в режиме просмотра."
    override val privacy = "Конфиденциальность"
    override val rememberRecent = "Запоминать недавние файлы"
    override val rememberRecentHint =
        "Хранит до 20 имён файлов на этом устройстве, чтобы их можно было быстро открыть снова. Если " +
            "выключить, список сразу удаляется."
    override val recentCleared = "Список недавних файлов очищен."
    override val clearRecent = "Очистить недавние файлы"
    override val tempDeleted = "Временные файлы удалены."
    override val deleteTemp = "Удалить временные рабочие файлы"
    override val nothingElseToConfigure =
        "Больше настраивать нечего, потому что больше нечего отключать. У AnonPDF нет аккаунта, " +
            "синхронизации, рекламы и доступа к сети."
    override val language = "Язык"
    override val languageHint = "AnonPDF использует язык устройства. Для этого приложения его можно сменить в настройках Android."
    override val chooseLanguage = "Выбрать язык"

    override fun version(name: String) = "Версия $name"
    override val aboutTagline = "Программа для чтения и правки PDF, которая работает на вашем устройстве и больше нигде."
    override val cannotDo = "Чего это приложение не может"
    override val cannotNetwork =
        "Выходить в сеть. AnonPDF не запрашивает разрешение INTERNET, поэтому Android отклонит " +
            "любое подключение. Нет сервера, куда что-то отправить, и нет способа до него добраться."
    override val cannotTrack =
        "Следить за вами. Ни аналитики, ни отчётов о сбоях, ни рекламных SDK, ни идентификаторов устройства, ни аккаунта."
    override val cannotCharge = "Брать с вас деньги. Все инструменты включены. Нет платной версии и скрытых функций."
    override val onePermission = "Единственное разрешение, которое оно просит"
    override val permissionWhy =
        "Доступ ко всем файлам, чтобы обзор мог показать PDF на устройстве, а не заставлять искать " +
            "каждый в системном окне выбора. Он используется только для чтения документов."
    override val permissionOptional =
        "Он необязателен. Откажитесь — и всё будет работать через окно выбора, по одному файлу."
    override val permissionWhere =
        "Android называет это «специальным доступом», поэтому оно не видно на странице разрешений " +
            "приложения — там в любом случае написано «разрешения не запрашивались». Настоящее " +
            "состояние смотрите в Настройки → Приложения → Специальный доступ → Доступ ко всем файлам."
    override val permissionCannotLeak = "Чтение файлов не может привести к утечке: без доступа к сети им просто некуда уйти."
    override val whatItStores = "Что оно хранит"
    override val storesRecent =
        "Список имён недавно открытых файлов — в личном хранилище приложения, без облачного " +
            "резервного копирования. Его можно выключить или очистить в Настройках."
    override val storesWorking =
        "Рабочие копии редактируемого файла в кеше приложения. Они временные, Android может удалить их в любой момент."
    override val storesPrefs = "Ваши настройки отображения. Это весь список."
    override val noTrustNeeded =
        "Ничего из этого не нужно принимать на веру. Исходный код открыт, а список разрешений виден " +
            "в карточке приложения в Play Store и на экране сведений о приложении в Android."
    override val viewSource = "Посмотреть исходный код"
    override val licences = "Лицензии открытого ПО"
    override val licenceMit = "Лицензия MIT. Copyright (c) 2026 участники проекта AnonPDF."
    override val licencePdfBox =
        "Лицензия Apache 2.0. Copyright Apache Software Foundation и Tom Roush. Используется для " +
            "чтения и записи структуры PDF."
    override val licenceAndroidX = "Лицензия Apache 2.0. Copyright The Android Open Source Project."
    override val licenceKotlin = "Лицензия Apache 2.0. Copyright JetBrains s.r.o."
    override val cryptoNote =
        "Защита паролем использует AES-256 через собственные криптографические библиотеки Android. " +
            "Сторонних криптобиблиотек нет."
    override val translationsNote =
        "Перевод сделан с помощью машинного перевода. Исправления приветствуются на странице проекта на GitHub."

    override val whatWouldYouLikeToDo = "Что вы хотите сделать?"
    override val desktopEditBody =
        "Поворачивайте, переставляйте, удаляйте или обрезайте страницы, добавляйте страницы из " +
            "других PDF, заполняйте, выделяйте, рисуйте, добавляйте текст, водяной знак или номера " +
            "страниц. Каждое изменение видно до того, как вы сохраните новую копию."
    override val openAPdfEllipsis = "Открыть PDF…"
    override val desktopPrivacy =
        "AnonPDF работает только на этом компьютере. Открытый файл копируется в личную рабочую папку, " +
            "доступную только вам, и эта папка удаляется при закрытии приложения. Ничего не " +
            "отправляется и ничего не записывается в ваши файлы, пока вы не выберете, куда сохранить."
    override val filesStayOnComputer = "Файлы остаются на этом компьютере"
    override val file = "Файл"
    override val tools = "Инструменты"
    override val openPdfToEdit = "Открыть PDF для правки…"
    override val closeDocument = "Закрыть документ"
    override val quit = "Выход"
    override val start = "Начало"
    override val dropStart = "Перетащите PDF, чтобы править его, несколько — чтобы объединить, или изображения, чтобы сделать PDF"
    override val dropEditor = "Перетащите PDF, чтобы открыть его или добавить его страницы к открытому"
    override fun dropForTool(title: String) = "Перетащите PDF: $title"
    override fun dropToAdd(title: String) = "Перетащите PDF, чтобы добавить: $title"
    override val dropPictures = "Перетащите изображения, чтобы добавить их"
    override val saveAsDialog = "Сохранить как"
    override val saveAllDialog = "Сохранить всё в папку"
}
