package io.github.fanfeast.anonpdf.pdf

/**
 * A recipe step as a JSON object, and back.
 *
 * Every field is spelled out by name, enums by their constant's name and colours
 * as "#AARRGGBB", so a recipe file can be read, and if need be edited, by hand.
 * Page positions are written counting from 1, as the user counts them.
 */
internal object RecipeCodec {

    fun encode(step: RecipeStep): Map<String, Any?> {
        val out = linkedMapOf<String, Any?>()
        step.pages?.let { out["pages"] = pages(it) }
        when (val op = step.op) {
            is RotatePages -> out.put("do", "rotate").also { out["degrees"] = op.degrees }
            is CropPages -> {
                out["do"] = "crop"
                out["left"] = op.insets.left; out["top"] = op.insets.top
                out["right"] = op.insets.right; out["bottom"] = op.insets.bottom
            }
            is ScalePages -> out.put("do", "scale").also { out["factor"] = op.factor }
            is ResizePages -> {
                out["do"] = "resize"
                out["paper"] = op.target.paper.name
                out["orientation"] = op.target.orientation.name
                out["fill"] = op.target.fill
            }
            is DeletePages -> out["do"] = "delete"
            is MovePage -> out.put("do", "move").also { out["offset"] = op.offset }
            ReversePages -> out["do"] = "reverse"
            is SetWatermark -> {
                out["do"] = "watermark"
                op.options?.let {
                    out["text"] = it.text; out["fontSize"] = it.fontSize; out["opacity"] = it.opacity
                    out["layout"] = it.layout.name; out["color"] = color(it.color); out["bold"] = it.bold
                } ?: out.put("remove", true)
            }
            is SetPageNumbers -> {
                out["do"] = "pageNumbers"
                op.options?.let {
                    out["format"] = it.format; out["position"] = it.position.name
                    out["startNumber"] = it.startNumber; out["firstPage"] = it.firstPageIndex + 1
                    out["fontSize"] = it.fontSize; out["margin"] = it.marginPoints; out["color"] = color(it.color)
                } ?: out.put("remove", true)
            }
            is SetHeaderFooter -> {
                out["do"] = "headerFooter"
                op.options?.let {
                    out["slots"] = it.slots.filterValues(String::isNotBlank).mapKeys { (k, _) -> k.name }
                    out["startNumber"] = it.startNumber; out["firstPage"] = it.firstPageIndex + 1
                    out["fontSize"] = it.fontSize; out["margin"] = it.marginPoints; out["color"] = color(it.color)
                } ?: out.put("remove", true)
            }
            is AddRedaction -> {
                out["do"] = "redact"
                out["find"] = op.redaction.rule.kind.name
                if (op.redaction.rule.kind == RedactKind.WORDS) out["text"] = op.redaction.rule.text
            }
            is SetMarkups -> out.put("do", "markups").also { out["mode"] = op.mode.name }
            is SetGreyscale -> out.put("do", "greyscale").also { out["on"] = op.on }
            is FillForm -> {
                out["do"] = "fillForm"
                out["values"] = op.values
                out["flatten"] = op.flatten
            }
            is AddMark -> when (val mark = op.mark) {
                is TextMark -> {
                    out["do"] = "text"
                    out["text"] = mark.text; out["left"] = mark.left; out["top"] = mark.top
                    out["fontSize"] = mark.fontSize; out["color"] = color(mark.color); out["bold"] = mark.bold
                }
                is FillMark -> {
                    out["do"] = "box"
                    out["left"] = mark.rect.left; out["top"] = mark.rect.top
                    out["right"] = mark.rect.right; out["bottom"] = mark.rect.bottom
                    out["color"] = color(mark.color); out["opacity"] = mark.opacity
                }
                is InkMark -> {
                    out["do"] = "ink"
                    out["strokes"] = mark.strokes.map { stroke -> stroke.flatMap { (x, y) -> listOf(x, y) } }
                    out["color"] = color(mark.color); out["width"] = mark.widthRatio
                }
            }
            else -> error("${op::class.simpleName} is not recorded")
        }
        // "do" first, for whoever reads the file.
        return linkedMapOf<String, Any?>("do" to out.remove("do")) + out
    }

    fun decode(json: Map<*, *>): RecipeStep {
        val r = Fields(json)
        val pages = json["pages"]?.let(::pagesOf)
        val none = emptySet<Int>()
        val op: EditOp = when (r.string("do")) {
            "rotate" -> RotatePages(r.int("degrees"), none)
            "crop" -> CropPages(CropInsets(r.fraction("left"), r.fraction("top"), r.fraction("right"), r.fraction("bottom")), none)
            "scale" -> ScalePages(r.float("factor").coerceIn(0.1f, 4f), none)
            "resize" -> ResizePages(
                ResizeTarget(enumValueOf(r.string("paper")), enumValueOf(r.string("orientation", "AUTO")), r.bool("fill")),
                none,
            )
            "delete" -> DeletePages(none)
            "move" -> MovePage(0, r.int("offset"))
            "reverse" -> ReversePages
            "watermark" -> SetWatermark(
                if (r.bool("remove")) null else WatermarkOptions(
                    text = r.string("text"),
                    fontSize = r.float("fontSize", 52f),
                    opacity = r.fraction("opacity", 0.22f),
                    layout = enumValueOf(r.string("layout", "DIAGONAL")),
                    color = r.color("color", 0xFF757575.toInt()),
                    bold = r.bool("bold", true),
                ),
            )
            "pageNumbers" -> SetPageNumbers(
                if (r.bool("remove")) null else PageNumberOptions(
                    format = r.string("format", "{n}"),
                    position = enumValueOf(r.string("position", "BOTTOM_CENTER")),
                    startNumber = r.int("startNumber", 1),
                    firstPageIndex = (r.int("firstPage", 1) - 1).coerceAtLeast(0),
                    fontSize = r.float("fontSize", 11f),
                    marginPoints = r.float("margin", 28f),
                    color = r.color("color", 0xFF000000.toInt()),
                ),
            )
            "headerFooter" -> SetHeaderFooter(
                if (r.bool("remove")) null else HeaderFooterOptions(
                    slots = (json["slots"] as? Map<*, *>).orEmpty().entries.associate { (k, v) ->
                        enumValueOf<NumberPosition>(k as String) to (v as String)
                    },
                    startNumber = r.int("startNumber", 1),
                    firstPageIndex = (r.int("firstPage", 1) - 1).coerceAtLeast(0),
                    fontSize = r.float("fontSize", 10f),
                    marginPoints = r.float("margin", 28f),
                    color = r.color("color", 0xFF000000.toInt()),
                ),
            )
            "redact" -> AddRedaction(Redaction(RedactRule(enumValueOf(r.string("find")), r.string("text", "")), none))
            "markups" -> SetMarkups(enumValueOf(r.string("mode")))
            "greyscale" -> SetGreyscale(r.bool("on", true))
            "fillForm" -> FillForm(
                (json["values"] as? Map<*, *>).orEmpty().entries.associate { (k, v) -> (k as String) to (v as String) },
                r.bool("flatten"),
            )
            "text" -> AddMark(
                TextMark(
                    id = 0, sourceIndex = 0, text = r.string("text"),
                    left = r.fraction("left"), top = r.fraction("top"),
                    fontSize = r.float("fontSize", 12f), color = r.color("color", 0xFF000000.toInt()),
                    bold = r.bool("bold"),
                ),
            )
            "box" -> AddMark(
                FillMark(
                    id = 0, sourceIndex = 0,
                    rect = MarkRect(r.fraction("left"), r.fraction("top"), r.fraction("right"), r.fraction("bottom")).tidied(),
                    color = r.color("color", 0xFFFFFFFF.toInt()), opacity = r.fraction("opacity", 1f),
                ),
            )
            "ink" -> AddMark(
                InkMark(
                    id = 0, sourceIndex = 0,
                    strokes = (json["strokes"] as List<*>).map { stroke ->
                        (stroke as List<*>).map { (it as Double).toFloat().coerceIn(0f, 1f) }
                            .chunked(2).filter { it.size == 2 }.map { (x, y) -> x to y }
                    },
                    color = r.color("color", 0xFF1A1A1A.toInt()), widthRatio = r.float("width", 0.004f),
                ),
            )
            else -> throw IllegalArgumentException("unknown step")
        }
        return RecipeStep(op, pages)
    }

    private fun pages(choice: PageChoice): Any = when (choice) {
        PageChoice.All -> "all"
        PageChoice.First -> "first"
        PageChoice.Last -> "last"
        PageChoice.Odd -> "odd"
        PageChoice.Even -> "even"
        is PageChoice.Positions -> choice.positions.map { it + 1 }
    }

    private fun pagesOf(value: Any): PageChoice = when (value) {
        "all" -> PageChoice.All
        "first" -> PageChoice.First
        "last" -> PageChoice.Last
        "odd" -> PageChoice.Odd
        "even" -> PageChoice.Even
        is List<*> -> PageChoice.Positions(value.map { (it as Double).toInt() - 1 }.filter { it >= 0 }.distinct().sorted())
        else -> throw IllegalArgumentException("bad pages")
    }

    private fun color(argb: Int) = "#%08X".format(argb)

    private class Fields(val json: Map<*, *>) {
        fun string(key: String, default: String? = null): String =
            json[key] as? String ?: default ?: throw IllegalArgumentException("missing $key")

        fun float(key: String, default: Float? = null): Float =
            (json[key] as? Double)?.toFloat()?.takeIf { it.isFinite() } ?: default
                ?: throw IllegalArgumentException("missing $key")

        fun fraction(key: String, default: Float = 0f) = float(key, default).coerceIn(0f, 1f)

        fun int(key: String, default: Int? = null): Int =
            (json[key] as? Double)?.toInt() ?: default ?: throw IllegalArgumentException("missing $key")

        fun bool(key: String, default: Boolean = false): Boolean = json[key] as? Boolean ?: default

        fun color(key: String, default: Int): Int =
            (json[key] as? String)?.removePrefix("#")?.toLongOrNull(16)?.toInt() ?: default
    }
}
