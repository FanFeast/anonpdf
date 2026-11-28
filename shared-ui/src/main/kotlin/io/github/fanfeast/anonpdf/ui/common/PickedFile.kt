package io.github.fanfeast.anonpdf.ui.common

import java.io.InputStream

/** A file the user picked, by the name they know it by, to be read once. */
class PickedFile(val name: String, val open: () -> InputStream)
