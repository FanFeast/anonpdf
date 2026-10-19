package io.github.fanfeast.anonpdf.ui.components

/** Surfaces the useful part of an exception without leaking a stack trace at the user. */
fun Throwable.friendlyMessage(fallback: String): String =
    message?.takeIf { it.isNotBlank() && it.length < 240 } ?: fallback
