package com.oneclickalias.app.ui.account

/** Display and input rules for the 16-digit account number. */
object AccountNumberFormat {
    const val LENGTH = 16
    private const val GROUP = 4

    /** Keeps ASCII digits only (pasted spaces, dashes, NBSPs are dropped) and caps at [LENGTH]. */
    fun sanitize(input: String): String = input.filter { it in '0'..'9' }.take(LENGTH)

    fun isComplete(digits: String): Boolean = digits.length == LENGTH && digits.all { it in '0'..'9' }

    /** "1234 5678 9012 3456", like the website. */
    fun grouped(digits: String): String = digits.chunked(GROUP).joinToString(" ")

    /** For screen readers: digit by digit, with a pause after each group. */
    fun spoken(digits: String): String = digits.chunked(GROUP).joinToString(", ") { it.toList().joinToString(" ") }
}
