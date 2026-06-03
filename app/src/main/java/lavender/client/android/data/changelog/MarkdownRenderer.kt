package lavender.client.android.data.changelog

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan

/**
 * Simple markdown-to-Spannable renderer for release notes.
 * Supports: **bold**, `code`, ## headings, ### subheadings, — bullets, • bullets, links.
 */
object MarkdownRenderer {

    fun render(
        markdown: String,
        textColor: Int,
        headingColor: Int,
        linkColor: Int,
        codeBgColor: Int
    ): SpannableStringBuilder {
        if (markdown.isEmpty()) return SpannableStringBuilder("")

        val sb = SpannableStringBuilder()
        val lines = markdown.split("\n")
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            when {
                // Empty line
                trimmed.isEmpty() -> {
                    if (sb.isNotEmpty() && sb.last() != '\n') {
                        sb.append("\n")
                    }
                }

                // ## Heading
                trimmed.startsWith("## ") -> {
                    if (sb.isNotEmpty() && sb.last() != '\n') sb.append("\n")
                    val headingText = trimmed.removePrefix("## ").trim()
                    val start = sb.length
                    sb.append(headingText)
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(RelativeSizeSpan(1.15f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(ForegroundColorSpan(headingColor), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.append("\n")
                }

                // ### Subheading
                trimmed.startsWith("### ") -> {
                    if (sb.isNotEmpty() && sb.last() != '\n') sb.append("\n")
                    val headingText = trimmed.removePrefix("### ").trim()
                    val start = sb.length
                    sb.append(headingText)
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(RelativeSizeSpan(1.05f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(ForegroundColorSpan(headingColor), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.append("\n")
                }

                // — Bullet or • Bullet
                trimmed.startsWith("— ") || trimmed.startsWith("• ") || trimmed.startsWith("- ") -> {
                    val bulletText = trimmed.removePrefix("— ").removePrefix("• ").removePrefix("- ").trim()
                    val start = sb.length
                    sb.append("  •  ")
                    val textStart = sb.length
                    sb.append(renderInline(bulletText, textColor, linkColor, codeBgColor))
                    sb.append("\n")
                }

                // Numbered list: 1. 2. etc.
                trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                    val start = sb.length
                    sb.append("  ")
                    sb.append(renderInline(trimmed, textColor, linkColor, codeBgColor))
                    sb.append("\n")
                }

                // Regular text
                else -> {
                    val start = sb.length
                    sb.append(renderInline(trimmed, textColor, linkColor, codeBgColor))
                    sb.append("\n")
                }
            }
            i++
        }

        // Remove trailing newlines
        while (sb.isNotEmpty() && sb.last() == '\n') {
            sb.delete(sb.length - 1, sb.length)
        }

        return sb
    }

    /**
     * Render inline markdown: **bold**, `code`, [text](url)
     */
    private fun renderInline(
        text: String,
        textColor: Int,
        linkColor: Int,
        codeBgColor: Int
    ): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        var pos = 0

        while (pos < text.length) {
            when {
                // **bold**
                pos + 1 < text.length && text.substring(pos).startsWith("**") -> {
                    val end = text.indexOf("**", pos + 2)
                    if (end > pos + 2) {
                        val boldText = text.substring(pos + 2, end)
                        val start = sb.length
                        sb.append(boldText)
                        sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        pos = end + 2
                    } else {
                        sb.append(text[pos])
                        pos++
                    }
                }

                // `code`
                text[pos] == '`' -> {
                    val end = text.indexOf('`', pos + 1)
                    if (end > pos + 1) {
                        val codeText = text.substring(pos + 1, end)
                        val start = sb.length
                        sb.append(codeText)
                        sb.setSpan(TypefaceSpan("monospace"), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        sb.setSpan(ForegroundColorSpan(textColor), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        pos = end + 1
                    } else {
                        sb.append(text[pos])
                        pos++
                    }
                }

                // [text](url) — simplified: just show text with link color
                text[pos] == '[' -> {
                    val closeBracket = text.indexOf(']', pos + 1)
                    if (closeBracket > pos + 1 && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                        val closeParen = text.indexOf(')', closeBracket + 2)
                        if (closeParen > closeBracket + 1) {
                            val linkText = text.substring(pos + 1, closeBracket)
                            val start = sb.length
                            sb.append(linkText)
                            sb.setSpan(ForegroundColorSpan(linkColor), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            sb.setSpan(StyleSpan(Typeface.ITALIC), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            pos = closeParen + 1
                        } else {
                            sb.append(text[pos])
                            pos++
                        }
                    } else {
                        sb.append(text[pos])
                        pos++
                    }
                }

                // Emoji at line start (🐛 🔒 🎨 etc.) — just pass through
                else -> {
                    sb.append(text[pos])
                    pos++
                }
            }
        }

        return sb
    }
}
