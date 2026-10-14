package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.remember
import com.example.ui.theme.SlatePrimary

sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class LaTeXBlock(val formula: String) : MarkdownBlock()
    data class Blockquote(val text: String) : MarkdownBlock()
    data class UnorderedList(val items: List<String>) : MarkdownBlock()
    data class OrderedList(val items: List<String>) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
}

fun formatLaTeXToUnicode(text: String): String {
    var result = text
    
    // Replace fractions \frac{a}{b} -> (a)/(b)
    val fracRegex = Regex("""\\frac\s*\{([^}]+)\}\s*\{([^}]+)\}""")
    result = fracRegex.replace(result) { matchResult ->
        val num = matchResult.groupValues[1]
        val den = matchResult.groupValues[2]
        "($num)/($den)"
    }
    
    // Clean common command wraps like \mathrm{...}, \mathbf{...}, \text{...}
    val commandWraps = listOf("mathrm", "mathbf", "text", "textsf", "texttt", "textit")
    for (cmd in commandWraps) {
        val wrapRegex = Regex("""\\""" + cmd + """\s*\{([^}]+)\}""")
        result = wrapRegex.replace(result) { it.groupValues[1] }
    }
    
    // LaTeX symbol replacements
    val replacements = mapOf(
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
        "\\epsilon" to "ε", "\\varepsilon" to "ε", "\\zeta" to "ζ", "\\eta" to "η",
        "\\theta" to "θ", "\\iota" to "ι", "\\kappa" to "κ", "\\lambda" to "λ",
        "\\mu" to "μ", "\\nu" to "ν", "\\xi" to "ξ", "\\pi" to "π",
        "\\rho" to "ρ", "\\sigma" to "σ", "\\tau" to "τ", "\\upsilon" to "υ",
        "\\phi" to "φ", "\\chi" to "χ", "\\psi" to "ψ", "\\omega" to "ω",
        "\\Delta" to "Δ", "\\Sigma" to "Σ", "\\Omega" to "Ω", "\\Pi" to "Π",
        "\\times" to "×", "\\div" to "÷", "\\pm" to "±", "\\mp" to "∓",
        "\\le" to "≤", "\\ge" to "≥", "\\neq" to "≠", "\\approx" to "≈",
        "\\infty" to "∞", "\\in" to "∈", "\\sum" to "∑", "\\int" to "∫",
        "\\sqrt" to "√", "\\partial" to "∂", "\\nabla" to "∇", "\\forall" to "∀",
        "\\exists" to "∃", "\\propto" to "∝", "\\cdot" to "·", "\\to" to "→",
        "\\rightarrow" to "→", "\\leftarrow" to "←", "\\implies" to "⇒", "\\iff" to "⇔",
        "\\deg" to "°", "\\angle" to "∠", "\\triangle" to "△", "\\square" to "□",
        "\\therefore" to "∴", "\\because" to "∵"
    )
    
    for ((key, value) in replacements) {
        result = result.replace(key, value)
    }
    
    // Replace superscript braces ^{123} or ^1
    val superBracesRegex = Regex("""\^\{([^}]+)\}""")
    result = superBracesRegex.replace(result) { matchResult ->
        convertSubSup(matchResult.groupValues[1], isSup = true)
    }
    val superSingleRegex = Regex("""\^([a-zA-Z0-9])""")
    result = superSingleRegex.replace(result) { matchResult ->
        convertSubSup(matchResult.groupValues[1], isSup = true)
    }
    
    // Replace subscript braces _{abc} or _1
    val subBracesRegex = Regex("""_\{([^}]+)\}""")
    result = subBracesRegex.replace(result) { matchResult ->
        convertSubSup(matchResult.groupValues[1], isSup = false)
    }
    val subSingleRegex = Regex("""_([a-zA-Z0-9])""")
    result = subSingleRegex.replace(result) { matchResult ->
        convertSubSup(matchResult.groupValues[1], isSup = false)
    }
    
    return result
}

private fun convertSubSup(text: String, isSup: Boolean): String {
    val supMap = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'a' to 'ᵃ', 'b' to 'ᵇ', 'c' to 'ᶜ', 'd' to 'ᵈ', 'e' to 'ᵉ',
        'f' to 'ᶠ', 'g' to 'ᵍ', 'h' to 'ʰ', 'i' to 'ⁱ', 'j' to 'ʲ',
        'k' to 'ᵏ', 'l' to 'ˡ', 'm' to 'ᵐ', 'n' to 'ⁿ', 'o' to 'ᵒ',
        'p' to 'ᵖ', 'r' to 'ʳ', 's' to 'ˢ', 't' to 'ᵗ', 'u' to 'ᵘ',
        'v' to 'ᵛ', 'w' to 'ʷ', 'x' to 'ˣ', 'y' to 'ʸ', 'z' to 'ᶻ',
        'A' to 'ᴬ', 'B' to 'ᴮ', 'D' to 'ᴰ', 'E' to 'ᴱ', 'G' to 'ᴳ',
        'H' to 'ᴴ', 'I' to 'ᴵ', 'J' to 'ᴶ', 'K' to 'ᴲ', 'L' to 'ᴸ',
        'M' to 'ᴹ', 'N' to 'ᴺ', 'O' to 'ᴼ', 'P' to 'ᴾ', 'R' to 'ᴿ',
        'T' to 'ᵀ', 'U' to 'ᵁ', 'W' to 'ᵂ'
    )
    val subMap = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ',
        'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ',
        'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ',
        'v' to 'ᵥ', 'x' to 'ₓ'
    )
    
    val map = if (isSup) supMap else subMap
    return text.map { char -> map[char] ?: char }.joinToString("")
}

fun parseMarkdown(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = text.split("\n")
    var i = 0
    val orderedRegex = Regex("""^\s*\d+\.\s+(.*)$""")
    
    while (i < lines.size) {
        val line = lines[i]
        
        // Code Block
        if (line.trim().startsWith("```")) {
            val lang = line.trim().substring(3).trim()
            val codeBuilder = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeBuilder.append(lines[i]).append("\n")
                i++
            }
            blocks.add(MarkdownBlock.CodeBlock(lang, codeBuilder.toString().trimEnd()))
            i++
            continue
        }
        
        // LaTeX Block (multiple lines)
        if (line.trim().startsWith("$$")) {
            val contentBuilder = StringBuilder()
            var singleLine = false
            if (line.trim().endsWith("$$") && line.trim().length > 4) {
                // Single line $$formula$$
                val content = line.trim().substring(2, line.trim().length - 2).trim()
                blocks.add(MarkdownBlock.LaTeXBlock(content))
                singleLine = true
            } else {
                contentBuilder.append(line.trim().substring(2)).append("\n")
                i++
                while (i < lines.size && !lines[i].trim().endsWith("$$")) {
                    contentBuilder.append(lines[i]).append("\n")
                    i++
                }
                if (i < lines.size) {
                    val lastLine = lines[i].trim()
                    val cleanedLast = if (lastLine.endsWith("$$")) lastLine.substring(0, lastLine.length - 2) else lastLine
                    contentBuilder.append(cleanedLast)
                }
            }
            if (!singleLine) {
                blocks.add(MarkdownBlock.LaTeXBlock(contentBuilder.toString().trim()))
            }
            i++
            continue
        }
        
        // LaTeX Block (alternative \[ \])
        if (line.trim().startsWith("\\[")) {
            val contentBuilder = StringBuilder()
            var singleLine = false
            if (line.trim().endsWith("\\]") && line.trim().length > 4) {
                val content = line.trim().substring(2, line.trim().length - 2).trim()
                blocks.add(MarkdownBlock.LaTeXBlock(content))
                singleLine = true
            } else {
                contentBuilder.append(line.trim().substring(2)).append("\n")
                i++
                while (i < lines.size && !lines[i].trim().endsWith("\\]")) {
                    contentBuilder.append(lines[i]).append("\n")
                    i++
                }
                if (i < lines.size) {
                    val lastLine = lines[i].trim()
                    val cleanedLast = if (lastLine.endsWith("\\]")) lastLine.substring(0, lastLine.length - 2) else lastLine
                    contentBuilder.append(cleanedLast)
                }
            }
            if (!singleLine) {
                blocks.add(MarkdownBlock.LaTeXBlock(contentBuilder.toString().trim()))
            }
            i++
            continue
        }

        // Heading
        if (line.startsWith("#")) {
            val level = line.takeWhile { it == '#' }.length
            val content = line.substring(level).trim()
            if (level in 1..6 && line.substring(0, level).all { it == '#' } && (line.getOrNull(level) == ' ' || line.getOrNull(level) == null)) {
                blocks.add(MarkdownBlock.Heading(level, content))
                i++
                continue
            }
        }

        // Blockquote
        if (line.trimStart().startsWith(">")) {
            val contentBuilder = StringBuilder()
            contentBuilder.append(line.trimStart().substring(1).trim()).append("\n")
            i++
            while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                contentBuilder.append(lines[i].trimStart().substring(1).trim()).append("\n")
                i++
            }
            blocks.add(MarkdownBlock.Blockquote(contentBuilder.toString().trimEnd()))
            continue
        }

        // Unordered List
        if (line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") || line.trimStart().startsWith("+ ")) {
            val items = mutableListOf<String>()
            items.add(line.trimStart().substring(2))
            i++
            while (i < lines.size) {
                val nextLine = lines[i]
                if (nextLine.trimStart().startsWith("- ") || nextLine.trimStart().startsWith("* ") || nextLine.trimStart().startsWith("+ ")) {
                    items.add(nextLine.trimStart().substring(2))
                    i++
                } else if (nextLine.trim().isEmpty() && i + 1 < lines.size && (lines[i+1].trimStart().startsWith("- ") || lines[i+1].trimStart().startsWith("* ") || lines[i+1].trimStart().startsWith("+ "))) {
                    i++
                } else {
                    break
                }
            }
            blocks.add(MarkdownBlock.UnorderedList(items))
            continue
        }

        // Ordered List
        if (orderedRegex.matches(line)) {
            val items = mutableListOf<String>()
            val match = orderedRegex.matchEntire(line)
            items.add(match?.groupValues?.get(1) ?: "")
            i++
            while (i < lines.size) {
                val nextLine = lines[i]
                val nextMatch = orderedRegex.matchEntire(nextLine)
                if (nextMatch != null) {
                    items.add(nextMatch.groupValues[1])
                    i++
                } else if (nextLine.trim().isEmpty() && i + 1 < lines.size && orderedRegex.matches(lines[i+1])) {
                    i++
                } else {
                    break
                }
            }
            blocks.add(MarkdownBlock.OrderedList(items))
            continue
        }

        // Paragraph
        if (line.trim().isNotEmpty()) {
            val paraBuilder = StringBuilder()
            paraBuilder.append(line).append("\n")
            i++
            while (i < lines.size) {
                val nextLine = lines[i]
                if (nextLine.trim().isEmpty() || 
                    nextLine.startsWith("#") || 
                    nextLine.trimStart().startsWith(">") || 
                    nextLine.trimStart().startsWith("- ") || 
                    nextLine.trimStart().startsWith("* ") || 
                    nextLine.trimStart().startsWith("+ ") || 
                    nextLine.trim().startsWith("```") || 
                    nextLine.trim().startsWith("$$") ||
                    nextLine.trim().startsWith("\\[") ||
                    orderedRegex.matches(nextLine)) {
                    break
                }
                paraBuilder.append(nextLine).append("\n")
                i++
            }
            blocks.add(MarkdownBlock.Paragraph(paraBuilder.toString().trimEnd()))
            continue
        }

        // Empty line
        i++
    }
    return blocks
}

private fun parseCitationNumber(raw: String): Int? {
    val trimmed = raw.trim()
    val normalized = trimmed.map { ch ->
        when (ch) {
            '০' -> '0'
            '১' -> '1'
            '২' -> '2'
            '৩' -> '3'
            '৪' -> '4'
            '৫' -> '5'
            '৬' -> '6'
            '৭' -> '7'
            '৮' -> '8'
            '৯' -> '9'
            else -> ch
        }
    }.joinToString("")
    return normalized.toIntOrNull()
}

fun parseInlineMarkdown(
    text: String,
    fontToUse: FontFamily,
    textColor: Color,
    linkColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val n = text.length
        
        while (i < n) {
            // Check for link [label](url)
            if (text.startsWith("[", i)) {
                val endLabel = text.indexOf("]", i)
                if (endLabel != -1 && text.startsWith("(", endLabel + 1)) {
                    val endUrl = text.indexOf(")", endLabel + 2)
                    if (endUrl != -1) {
                        val label = text.substring(i + 1, endLabel)
                        val url = text.substring(endLabel + 2, endUrl)
                        pushStringAnnotation(tag = "URL", annotation = url)
                        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold, fontFamily = fontToUse)) {
                            append(label)
                        }
                        pop()
                        i = endUrl + 1
                        continue
                    }
                }
            }
            
            // Check for inline LaTeX math $ math $
            if (text.startsWith("$", i)) {
                val endMath = text.indexOf("$", i + 1)
                if (endMath != -1) {
                    val mathContent = text.substring(i + 1, endMath)
                    val unicodeMath = formatLaTeXToUnicode(mathContent)
                    withStyle(SpanStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, color = if (textColor == Color.White) Color(0xFF93C5FD) else Color(0xFF0369A1))) {
                        append(unicodeMath)
                    }
                    i = endMath + 1
                    continue
                }
            }
            
            // Check for inline code ` code `
            if (text.startsWith("`", i)) {
                val endCode = text.indexOf("`", i + 1)
                if (endCode != -1) {
                    val codeContent = text.substring(i + 1, endCode)
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = textColor.copy(alpha = 0.12f), fontSize = 12.sp)) {
                        append(codeContent)
                    }
                    i = endCode + 1
                    continue
                }
            }
            
            // Check for bold-italic ***text***
            if (text.startsWith("***", i)) {
                val endStyle = text.indexOf("***", i + 3)
                if (endStyle != -1) {
                    val styleContent = text.substring(i + 3, endStyle)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontFamily = fontToUse)) {
                        append(styleContent)
                    }
                    i = endStyle + 3
                    continue
                }
            }
            
            // Check for bold **text**
            if (text.startsWith("**", i)) {
                val endStyle = text.indexOf("**", i + 2)
                if (endStyle != -1) {
                    val styleContent = text.substring(i + 2, endStyle)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontFamily = fontToUse)) {
                        append(styleContent)
                    }
                    i = endStyle + 2
                    continue
                }
            }
            
            // Check for italic *text*
            if (text.startsWith("*", i)) {
                val endStyle = text.indexOf("*", i + 1)
                if (endStyle != -1) {
                    val styleContent = text.substring(i + 1, endStyle)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontFamily = fontToUse)) {
                        append(styleContent)
                    }
                    i = endStyle + 1
                    continue
                }
            }
            
            // Check for bold __text__
            if (text.startsWith("__", i)) {
                val endStyle = text.indexOf("__", i + 2)
                if (endStyle != -1) {
                    val styleContent = text.substring(i + 2, endStyle)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontFamily = fontToUse)) {
                        append(styleContent)
                    }
                    i = endStyle + 2
                    continue
                }
            }
            
            // Check for italic _text_
            if (text.startsWith("_", i)) {
                val endStyle = text.indexOf("_", i + 1)
                if (endStyle != -1) {
                    val styleContent = text.substring(i + 1, endStyle)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontFamily = fontToUse)) {
                        append(styleContent)
                    }
                    i = endStyle + 1
                    continue
                }
            }
            
            // Check for numbered citation [1] / [1, 2] / [১, ২] WITHOUT a following (url)
            if (text.startsWith("[", i)) {
                val endBracket = text.indexOf("]", i + 1)
                if (endBracket != -1) {
                    val nextChar = text.getOrNull(endBracket + 1)
                    if (nextChar != '(') { // not a markdown link [label](url)
                        val inner = text.substring(i + 1, endBracket).trim()
                        val parts = inner.split(",")
                        val parsedItems = parts.mapNotNull { part ->
                            val num = parseCitationNumber(part)
                            if (num != null) Pair(part.trim(), num) else null
                        }
                        if (parsedItems.isNotEmpty() && parsedItems.size == parts.size) {
                            append("[")
                            parsedItems.forEachIndexed { idx, (origText, num) ->
                                if (idx > 0) append(", ")
                                pushStringAnnotation(tag = "CITATION", annotation = "$num")
                                withStyle(SpanStyle(
                                    color = linkColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    textDecoration = TextDecoration.Underline,
                                    fontFamily = fontToUse
                                )) { append(origText) }
                                pop()
                            }
                            append("]")
                            i = endBracket + 1
                            continue
                        }
                    }
                }
            }
            
            append(text[i])
            i++
        }
    }
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    fontToUse: FontFamily = FontFamily.Default,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: Int = 14,
    lineHeight: Int = 20,
    isUser: Boolean = false,
    onCitationClick: ((Int) -> Unit)? = null
) {
    val uriHandler = LocalUriHandler.current
    val blocks = remember(text) { parseMarkdown(text) }
    
    val linkColor = if (isUser) Color(0xFFE0F2FE) else Color(0xFF0284C7)
    val displayTextColor = if (isUser) Color.White else textColor

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val headingSize = when (block.level) {
                        1 -> (fontSize + 4).sp
                        2 -> (fontSize + 2).sp
                        3 -> (fontSize + 1).sp
                        else -> fontSize.sp
                    }
                    val headingLineHeight = when (block.level) {
                        1 -> (lineHeight + 4).sp
                        2 -> (lineHeight + 2).sp
                        3 -> (lineHeight + 1).sp
                        else -> lineHeight.sp
                    }
                    val annotatedHeading = parseInlineMarkdown(block.text, fontToUse, displayTextColor, linkColor)
                    
                    Text(
                        text = annotatedHeading,
                        fontFamily = fontToUse,
                        fontSize = headingSize,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = headingLineHeight,
                        color = displayTextColor,
                        style = TextStyle(
                            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            )
                        ),
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                is MarkdownBlock.Paragraph -> {
                    val annotatedParagraph = parseInlineMarkdown(block.text, fontToUse, displayTextColor, linkColor)
                    
                    ClickableText(
                        text = annotatedParagraph,
                        style = TextStyle(
                            fontFamily = fontToUse,
                            fontSize = fontSize.sp,
                            lineHeight = lineHeight.sp,
                            color = displayTextColor,
                            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            )
                        ),
                        onClick = { offset ->
                            // Check URL annotation first
                            annotatedParagraph.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                .firstOrNull()?.let { annotation ->
                                    if (annotation.item.startsWith("http")) {
                                        uriHandler.openUri(annotation.item)
                                    }
                                    return@ClickableText
                                }
                            // Then check inline citation [1]
                            annotatedParagraph.getStringAnnotations(tag = "CITATION", start = offset, end = offset)
                                .firstOrNull()?.let { ann ->
                                    ann.item.toIntOrNull()?.let { id -> onCitationClick?.invoke(id) }
                                }
                        }
                    )
                }
                is MarkdownBlock.CodeBlock -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isUser) Color.Black.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .border(1.dp, if (isUser) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        if (block.language.isNotEmpty()) {
                            Text(
                                text = block.language.uppercase(),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isUser) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        Text(
                            text = block.code,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = if (isUser) Color(0xFFF3F4F6) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is MarkdownBlock.LaTeXBlock -> {
                    val formattedMath = formatLaTeXToUnicode(block.formula)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isUser) Color.Black.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                            .border(1.dp, if (isUser) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formattedMath,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontSize = (fontSize + 1).sp,
                            lineHeight = (lineHeight + 2).sp,
                            textAlign = TextAlign.Center,
                            color = if (isUser) Color.White else MaterialTheme.colorScheme.primary
                        )
                    }
                }
                is MarkdownBlock.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(if (isUser) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        val annotatedQuote = parseInlineMarkdown(block.text, fontToUse, displayTextColor, linkColor)
                        Text(
                            text = annotatedQuote,
                            style = TextStyle(
                                fontFamily = fontToUse,
                                fontSize = fontSize.sp,
                                fontStyle = FontStyle.Italic,
                                lineHeight = lineHeight.sp,
                                color = displayTextColor.copy(alpha = 0.85f),
                                platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.None
                                )
                            )
                        )
                    }
                }
                is MarkdownBlock.UnorderedList -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        block.items.forEach { item ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "•",
                                    fontFamily = fontToUse,
                                    fontSize = fontSize.sp,
                                    lineHeight = lineHeight.sp,
                                    color = displayTextColor,
                                    style = TextStyle(
                                        platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                        lineHeightStyle = LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.None
                                        )
                                    ),
                                    modifier = Modifier.width(16.dp)
                                )
                                val annotatedItem = parseInlineMarkdown(item, fontToUse, displayTextColor, linkColor)
                                ClickableText(
                                    text = annotatedItem,
                                    style = TextStyle(
                                        fontFamily = fontToUse,
                                        fontSize = fontSize.sp,
                                        lineHeight = lineHeight.sp,
                                        color = displayTextColor,
                                        platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                        lineHeightStyle = LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.None
                                        )
                                    ),
                                    onClick = { offset ->
                                        annotatedItem.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                            .firstOrNull()?.let { annotation ->
                                                val url = annotation.item
                                                if (url.startsWith("http")) {
                                                    uriHandler.openUri(url)
                                                }
                                                return@ClickableText
                                            }
                                        annotatedItem.getStringAnnotations(tag = "CITATION", start = offset, end = offset)
                                            .firstOrNull()?.let { ann ->
                                                ann.item.toIntOrNull()?.let { id -> onCitationClick?.invoke(id) }
                                            }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                is MarkdownBlock.OrderedList -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        block.items.forEachIndexed { index, item ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "${index + 1}.",
                                    fontFamily = fontToUse,
                                    fontSize = fontSize.sp,
                                    lineHeight = lineHeight.sp,
                                    color = displayTextColor,
                                    style = TextStyle(
                                        platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                        lineHeightStyle = LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.None
                                        )
                                    ),
                                    modifier = Modifier.width(22.dp)
                                )
                                val annotatedItem = parseInlineMarkdown(item, fontToUse, displayTextColor, linkColor)
                                ClickableText(
                                    text = annotatedItem,
                                    style = TextStyle(
                                        fontFamily = fontToUse,
                                        fontSize = fontSize.sp,
                                        lineHeight = lineHeight.sp,
                                        color = displayTextColor,
                                        platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                        lineHeightStyle = LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.None
                                        )
                                    ),
                                    onClick = { offset ->
                                        annotatedItem.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                            .firstOrNull()?.let { annotation ->
                                                val url = annotation.item
                                                if (url.startsWith("http")) {
                                                    uriHandler.openUri(url)
                                                }
                                                return@ClickableText
                                            }
                                        annotatedItem.getStringAnnotations(tag = "CITATION", start = offset, end = offset)
                                            .firstOrNull()?.let { ann ->
                                                ann.item.toIntOrNull()?.let { id -> onCitationClick?.invoke(id) }
                                            }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Parsed Block System for Rich Markdown (DeepSearch / FactCheck Reports) ──

sealed class DsBlock {
    data class Heading(val level: Int, val raw: String) : DsBlock()
    data class Paragraph(val raw: String) : DsBlock()
    data class BulletItem(val raw: String) : DsBlock()
    data class NumberedItem(val number: String, val raw: String) : DsBlock()
    data class CodeFence(val code: String) : DsBlock()
    data class Blockquote(val raw: String) : DsBlock()
    data class MathBlock(val equation: String) : DsBlock()
    data class TableBlock(val rows: List<String>) : DsBlock()
    object HorizontalRule : DsBlock()
    object EmptyLine : DsBlock()
}

private fun parseDsBlocks(markdownText: String): List<DsBlock> {
    val lines = markdownText.split("\n")
    val blocks = mutableListOf<DsBlock>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            val sb = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                sb.append(lines[i]).append("\n")
                i++
            }
            if (i < lines.size) i++
            blocks.add(DsBlock.CodeFence(sb.toString().trimEnd()))
            continue
        }

        if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
            val tableRows = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                tableRows.add(lines[i].trim())
                i++
            }
            blocks.add(DsBlock.TableBlock(tableRows))
            continue
        }

        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            blocks.add(DsBlock.HorizontalRule)
            i++; continue
        }

        if (trimmed.startsWith("#### ")) {
            blocks.add(DsBlock.Heading(4, trimmed.removePrefix("#### ")))
            i++; continue
        }
        if (trimmed.startsWith("### ")) {
            blocks.add(DsBlock.Heading(3, trimmed.removePrefix("### ")))
            i++; continue
        }
        if (trimmed.startsWith("## ")) {
            blocks.add(DsBlock.Heading(2, trimmed.removePrefix("## ")))
            i++; continue
        }
        if (trimmed.startsWith("# ") && !trimmed.startsWith("## ")) {
            blocks.add(DsBlock.Heading(1, trimmed.removePrefix("# ")))
            i++; continue
        }

        if (trimmed.startsWith("> ")) {
            blocks.add(DsBlock.Blockquote(trimmed.removePrefix("> ")))
            i++; continue
        }

        if (trimmed.startsWith("$$") && trimmed.endsWith("$$") && trimmed.length > 4) {
            blocks.add(DsBlock.MathBlock(trimmed.removePrefix("$$").removeSuffix("$$")))
            i++; continue
        }

        if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
            blocks.add(DsBlock.BulletItem(trimmed.substring(2)))
            i++; continue
        }

        if (trimmed.matches(Regex("^\\d+\\.\\s.*"))) {
            val num = trimmed.substringBefore(".")
            val content = trimmed.substringAfter(". ").trim()
            blocks.add(DsBlock.NumberedItem(num, content))
            i++; continue
        }

        if (trimmed.isBlank()) {
            blocks.add(DsBlock.EmptyLine)
            i++; continue
        }

        blocks.add(DsBlock.Paragraph(line))
        i++
    }
    return blocks
}

@Composable
fun DeepSearchMarkdownRenderer(
    markdownText: String,
    onCitationClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fontToUse: FontFamily = FontFamily.Default
) {
    val blocks = remember(markdownText) { parseDsBlocks(markdownText) }

    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEach { block ->
            when (block) {
                is DsBlock.Heading -> when (block.level) {
                    1 -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = parseInlineDs(block.raw, fontToUse, onCitationClick),
                            fontFamily = fontToUse,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                lineHeight = 27.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(
                            color = SlatePrimary.copy(alpha = 0.35f),
                            thickness = 1.2.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    2 -> {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = parseInlineDs(block.raw, fontToUse, onCitationClick),
                            fontFamily = fontToUse,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                lineHeight = 23.sp
                            ),
                            color = SlatePrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        HorizontalDivider(
                            color = SlatePrimary.copy(alpha = 0.15f),
                            thickness = 0.8.dp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    3 -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = parseInlineDs(block.raw, fontToUse, onCitationClick),
                            fontFamily = fontToUse,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                lineHeight = 21.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    else -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = parseInlineDs(block.raw, fontToUse, onCitationClick),
                            fontFamily = fontToUse,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                    }
                }

                DsBlock.HorizontalRule -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 1.dp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                is DsBlock.Blockquote -> {
                    DsBlockquoteItem(block.raw, fontToUse, onCitationClick)
                }

                is DsBlock.MathBlock -> {
                    DsMathItem(block.equation)
                }

                is DsBlock.CodeFence -> {
                    DsCodeItem(block.code)
                }

                is DsBlock.TableBlock -> {
                    DsTableItem(block.rows, fontToUse)
                }

                is DsBlock.BulletItem -> {
                    DsBulletItem(block.raw, fontToUse, onCitationClick)
                }

                is DsBlock.NumberedItem -> {
                    DsNumberedItem(block.number, block.raw, fontToUse, onCitationClick)
                }

                DsBlock.EmptyLine -> {
                    Spacer(modifier = Modifier.height(6.dp))
                }

                is DsBlock.Paragraph -> {
                    val annotated = parseInlineDs(block.raw, fontToUse, onCitationClick)
                    ClickableText(
                        text = annotated,
                        style = TextStyle(
                            fontFamily = fontToUse,
                            fontSize = 15.sp,
                            lineHeight = 23.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        onClick = { offset ->
                            annotated.getStringAnnotations(tag = "CITATION", start = offset, end = offset)
                                .firstOrNull()?.let { ann ->
                                    ann.item.toIntOrNull()?.let { id -> onCitationClick(id) }
                                }
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun DsBlockquoteItem(raw: String, fontToUse: FontFamily, onCitationClick: (Int) -> Unit) {
    val annotated = parseInlineDs(raw, fontToUse, onCitationClick)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
            .background(SlatePrimary.copy(alpha = 0.06f))
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(44.dp)
                .background(SlatePrimary)
        )
        Spacer(modifier = Modifier.width(12.dp))
        ClickableText(
            text = annotated,
            style = TextStyle(
                fontFamily = fontToUse,
                fontStyle = FontStyle.Italic,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
            ),
            onClick = { offset ->
                annotated.getStringAnnotations(tag = "CITATION", start = offset, end = offset)
                    .firstOrNull()?.let { ann ->
                        ann.item.toIntOrNull()?.let { id -> onCitationClick(id) }
                    }
            }
        )
    }
}

@Composable
private fun DsMathItem(equation: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "∑  ${equation.trim()}",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = SlatePrimary
        )
    }
}

@Composable
private fun DsCodeItem(code: String) {
    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(
            text = code,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.horizontalScroll(scrollState)
        )
    }
}

@Composable
private fun DsTableItem(tableLines: List<String>, fontToUse: FontFamily) {
    val contentRows = tableLines.filter { row ->
        row.trim().replace("|", "").replace("-", "").replace(":", "").replace(" ", "").isNotEmpty()
    }
    if (contentRows.isEmpty()) return

    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .horizontalScroll(scrollState)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            contentRows.forEachIndexed { rowIdx, rowLine ->
                val cells = rowLine.trim()
                    .removePrefix("|")
                    .removeSuffix("|")
                    .split("|")
                    .map { it.trim() }

                val isHeader = rowIdx == 0
                val rowBg = when {
                    isHeader -> SlatePrimary.copy(alpha = 0.10f)
                    rowIdx % 2 == 0 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
                    else -> Color.Transparent
                }

                Row(
                    modifier = Modifier.background(rowBg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    cells.forEachIndexed { cellIdx, cellText ->
                        if (cellIdx > 0) {
                            Box(
                                modifier = Modifier
                                    .width(0.8.dp)
                                    .height(44.dp)
                                    .background(borderColor)
                            )
                        }
                        Text(
                            text = cellText,
                            fontFamily = fontToUse,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            ),
                            color = if (isHeader) SlatePrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .width(140.dp)
                        )
                    }
                }

                if (rowIdx < contentRows.size - 1) {
                    HorizontalDivider(color = borderColor, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun DsBulletItem(raw: String, fontToUse: FontFamily, onCitationClick: (Int) -> Unit) {
    val annotated = parseInlineDs(raw, fontToUse, onCitationClick)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            fontFamily = fontToUse,
            fontWeight = FontWeight.ExtraBold,
            color = SlatePrimary,
            fontSize = 16.sp,
            modifier = Modifier.padding(end = 10.dp, top = 2.dp)
        )
        ClickableText(
            text = annotated,
            style = TextStyle(
                fontFamily = fontToUse,
                fontSize = 15.sp,
                lineHeight = 23.sp,
                color = MaterialTheme.colorScheme.onBackground
            ),
            onClick = { offset ->
                annotated.getStringAnnotations(tag = "CITATION", start = offset, end = offset)
                    .firstOrNull()?.let { ann ->
                        ann.item.toIntOrNull()?.let { id -> onCitationClick(id) }
                    }
            }
        )
    }
}

@Composable
private fun DsNumberedItem(number: String, raw: String, fontToUse: FontFamily, onCitationClick: (Int) -> Unit) {
    val annotated = parseInlineDs(raw, fontToUse, onCitationClick)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$number.",
            fontFamily = fontToUse,
            fontWeight = FontWeight.Bold,
            color = SlatePrimary,
            fontSize = 15.sp,
            modifier = Modifier.padding(end = 10.dp, top = 2.dp)
        )
        ClickableText(
            text = annotated,
            style = TextStyle(
                fontFamily = fontToUse,
                fontSize = 15.sp,
                lineHeight = 23.sp,
                color = MaterialTheme.colorScheme.onBackground
            ),
            onClick = { offset ->
                annotated.getStringAnnotations(tag = "CITATION", start = offset, end = offset)
                    .firstOrNull()?.let { ann ->
                        ann.item.toIntOrNull()?.let { id -> onCitationClick(id) }
                    }
            }
        )
    }
}

private fun parseInlineDs(
    text: String,
    fontToUse: FontFamily,
    onCitationClick: (Int) -> Unit
): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val length = text.length
        val citationBlue = Color(0xFF0284C7)

        while (cursor < length) {
            when {
                text.startsWith("**", cursor) -> {
                    val end = text.indexOf("**", cursor + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontFamily = fontToUse)) {
                            append(text.substring(cursor + 2, end))
                        }
                        cursor = end + 2
                    } else {
                        append(text[cursor]); cursor++
                    }
                }

                text.startsWith("*", cursor) && !text.startsWith("**", cursor) -> {
                    val end = text.indexOf("*", cursor + 1)
                    if (end != -1 && end > cursor + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontFamily = fontToUse)) {
                            append(text.substring(cursor + 1, end))
                        }
                        cursor = end + 1
                    } else {
                        append(text[cursor]); cursor++
                    }
                }

                text.startsWith("_", cursor) && !text.startsWith("__", cursor) -> {
                    val end = text.indexOf("_", cursor + 1)
                    if (end != -1 && end > cursor + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontFamily = fontToUse)) {
                            append(text.substring(cursor + 1, end))
                        }
                        cursor = end + 1
                    } else {
                        append(text[cursor]); cursor++
                    }
                }

                text.startsWith("`", cursor) -> {
                    val end = text.indexOf("`", cursor + 1)
                    if (end != -1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color(0xFF1E1E1E).copy(alpha = 0.10f),
                                fontSize = 13.sp
                            )
                        ) {
                            append(" ")
                            append(text.substring(cursor + 1, end))
                            append(" ")
                        }
                        cursor = end + 1
                    } else {
                        append(text[cursor]); cursor++
                    }
                }

                text.startsWith("[", cursor) -> {
                    val closeIdx = text.indexOf("]", cursor + 1)
                    if (closeIdx != -1) {
                        val inner = text.substring(cursor + 1, closeIdx).trim()
                        val nums = inner.split(",").mapNotNull { it.trim().toIntOrNull() }
                        if (nums.isNotEmpty()) {
                            append("[")
                            nums.forEachIndexed { idx, num ->
                                if (idx > 0) append(", ")
                                pushStringAnnotation(tag = "CITATION", annotation = "$num")
                                withStyle(
                                    SpanStyle(
                                        color = citationBlue,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        textDecoration = TextDecoration.Underline,
                                        fontFamily = fontToUse
                                    )
                                ) {
                                    append("$num")
                                }
                                pop()
                            }
                            append("]")
                            cursor = closeIdx + 1
                        } else {
                            append(text[cursor]); cursor++
                        }
                    } else {
                        append(text[cursor]); cursor++
                    }
                }

                text.startsWith("$", cursor) && !text.startsWith("$$", cursor) -> {
                    val end = text.indexOf("$", cursor + 1)
                    if (end != -1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                color = citationBlue
                            )
                        ) {
                            append(text.substring(cursor + 1, end))
                        }
                        cursor = end + 1
                    } else {
                        append(text[cursor]); cursor++
                    }
                }

                else -> {
                    append(text[cursor]); cursor++
                }
            }
        }
    }
}
