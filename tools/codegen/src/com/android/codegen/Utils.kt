package com.android.codegen

import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * [Iterable.forEach] + [Any.apply]
 */
inline fun <T> Iterable<T>.forEachApply(block: T.() -> Unit) = forEach(block)

inline fun String.mapLines(f: String.() -> String?) = lines().mapNotNull(f).joinToString("\n")
inline fun <T> Iterable<T>.trim(f: T.() -> Boolean) = dropWhile(f).dropLastWhile(f)
fun String.trimBlankLines() = lines().trim { isBlank() }.joinToString("\n")

fun Char.isNewline() = this == '\n' || this == '\r'
fun Char.isWhitespaceNonNewline() = isWhitespace() && !isNewline()

fun if_(cond: Boolean, then: String) = if (cond) then else ""

inline infix fun Int.times(action: () -> Unit) {
    for (i in 1..this) action()
}

/**
 * a bbb
 * cccc dd
 *
 * ->
 *
 * a    bbb
 * cccc dd
 */
fun Iterable<Pair<String, String>>.columnize(separator: String = " | "): String {
    val col1w = map { (a, _) -> a.length }.max()!!
    val col2w = map { (_, b) -> b.length }.max()!!
    return map { it.first.padEnd(col1w) + separator + it.second.padEnd(col2w) }.joinToString("\n")
}

fun String.hasUnbalancedCurlyBrace(): Boolean {
    var braces = 0
    forEach {
        if (it == '{') braces++
        if (it == '}') braces--
        if (braces < 0) return true
    }
    return false
}

fun String.toLowerCamel(): String {
    if (length >= 2 && this[0] == 'm' && this[1].isUpperCase()) return substring(1).capitalize()
    if (all { it.isLetterOrDigit() }) return decapitalize()
    return split("[^a-zA-Z0-9]".toRegex())
            .map { it.toLowerCase().capitalize() }
            .joinToString("")
            .decapitalize()
}

inline fun <T> List<T>.forEachLastAware(f: (T, Boolean) -> Unit) {
    forEachIndexed { index, t -> f(t, index == size - 1) }
}

@Suppress("UNCHECKED_CAST")
fun <T : Expression> AnnotationExpr.singleArgAs()
        = ((this as SingleMemberAnnotationExpr).memberValue as T)

inline operator fun <reified T> Array<T>.minus(item: T) = toList().minus(item).toTypedArray()

fun currentTimestamp() = DateTimeFormatter
        .ofLocalizedDateTime(/* date */ FormatStyle.MEDIUM, /* time */ FormatStyle.LONG)
        .withZone(ZoneId.systemDefault())
        .format(Instant.now())