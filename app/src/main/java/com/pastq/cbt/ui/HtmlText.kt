package com.pastq.cbt.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * Turns the HTML-ish text stored in the question bank into styled text.
 * Supports <b>/<strong>, <i>/<em>, <u>, <sub>, <sup>, <br> and <p>; every other tag is dropped, and
 * HTML entities (named and numeric, even double-encoded like &amp;nbsp;) are decoded. Raw tags never reach the screen.
 */
object Html {
    private val named = mapOf(
        "nbsp" to " ", "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "ndash" to "–", "mdash" to "—", "hellip" to "…", "lsquo" to "‘", "rsquo" to "’",
        "ldquo" to "“", "rdquo" to "”", "times" to "×", "divide" to "÷", "plusmn" to "±",
        "deg" to "°", "frac12" to "½", "frac14" to "¼", "frac34" to "¾", "le" to "≤", "ge" to "≥",
        "ne" to "≠", "asymp" to "≈", "infin" to "∞", "pi" to "π", "theta" to "θ", "alpha" to "α",
        "beta" to "β", "gamma" to "γ", "delta" to "δ", "lambda" to "λ", "mu" to "μ", "sigma" to "σ",
        "omega" to "ω", "Omega" to "Ω", "micro" to "µ", "sup2" to "²", "sup3" to "³", "sup1" to "¹",
        "middot" to "·", "bull" to "•", "rarr" to "→", "larr" to "←", "copy" to "©", "cent" to "¢",
        "pound" to "£", "euro" to "€", "laquo" to "«", "raquo" to "»", "sect" to "§", "para" to "¶",
        "radic" to "√", "sum" to "∑", "minus" to "−", "prime" to "′", "Prime" to "″",
        "eacute" to "é", "egrave" to "è", "agrave" to "à", "ocirc" to "ô", "uuml" to "ü", "ouml" to "ö",
    )
    private val entity = Regex("&(#[xX][0-9a-fA-F]+|#[0-9]+|[A-Za-z][A-Za-z0-9]*);")
    private val tag = Regex("</?\\s*([A-Za-z][A-Za-z0-9]*)\\b[^>]*>")
    private val dropped = setOf("span", "div", "font", "a", "strong", "em", "ul", "ol", "li", "table", "tr", "td", "th", "h1", "h2", "h3", "h4", "h5", "h6", "center", "o:p", "img")

    /** Decodes entities; repeats so double-encoded text (&amp;nbsp;, &amp;lt;u&amp;gt;) also comes out right. */
    fun decodeEntities(input: String): String {
        var s = input
        repeat(3) {
            val next = entity.replace(s) { m ->
                val body = m.groupValues[1]
                when {
                    body.startsWith("#x") || body.startsWith("#X") ->
                        body.substring(2).toIntOrNull(16)?.let(::cp) ?: m.value
                    body.startsWith("#") -> body.substring(1).toIntOrNull()?.let(::cp) ?: m.value
                    else -> named[body] ?: m.value
                }
            }
            if (next == s) return s
            s = next
        }
        return s
    }

    private fun cp(code: Int): String =
        if (code == 160) " " else if (code in 1..0x10FFFF && code !in 0xD800..0xDFFF) String(Character.toChars(code)) else ""

    /** Plain text with tags removed and entities decoded (for places that cannot show styling). */
    fun plain(input: String): String = toAnnotated(input).text

    fun toAnnotated(input: String): AnnotatedString {
        val src = decodeEntities(input)
        return buildAnnotatedString {
            var bold = 0; var italic = 0; var underline = 0; var sub = 0; var sup = 0
            fun style() = SpanStyle(
                fontWeight = if (bold > 0) FontWeight.Bold else null,
                fontStyle = if (italic > 0) FontStyle.Italic else null,
                textDecoration = if (underline > 0) TextDecoration.Underline else null,
                baselineShift = when { sup > 0 -> BaselineShift.Superscript; sub > 0 -> BaselineShift.Subscript; else -> null },
            )
            var pos = 0
            fun text(s: String) { if (s.isNotEmpty()) withStyle(style()) { append(s) } }
            for (m in tag.findAll(src)) {
                val name = m.groupValues[1].lowercase()
                val closing = m.value.startsWith("</")
                val known = name in setOf("b", "strong", "i", "em", "u", "sub", "sup", "br", "p") || name in dropped
                if (!known) continue
                text(src.substring(pos, m.range.first))
                pos = m.range.last + 1
                val d = if (closing) -1 else 1
                when (name) {
                    "b", "strong" -> bold = (bold + d).coerceAtLeast(0)
                    "i", "em" -> italic = (italic + d).coerceAtLeast(0)
                    "u" -> underline = (underline + d).coerceAtLeast(0)
                    "sub" -> sub = (sub + d).coerceAtLeast(0)
                    "sup" -> sup = (sup + d).coerceAtLeast(0)
                    "br" -> text("\n")
                    "p" -> if (closing) text("\n")
                }
            }
            text(src.substring(pos))
        }
    }
}

/** Drop-in replacement for [Text] for question, option, section and explanation text from the bank. */
@Composable
fun HtmlText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    prefix: String = "",
    suffix: String = "",
) {
    val annotated = remember(text, prefix, suffix) {
        buildAnnotatedString { append(prefix); append(Html.toAnnotated(text.trim())); append(suffix) }
    }
    Text(annotated, modifier = modifier, style = style, color = color, fontWeight = fontWeight)
}
