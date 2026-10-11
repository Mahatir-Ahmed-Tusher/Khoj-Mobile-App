package com.example.ui.components

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage

sealed interface HtmlBlock {
    data class TextBlock(val html: String) : HtmlBlock
    data class ImageBlock(val imageUrl: String, val altText: String = "") : HtmlBlock
}

/**
 * Parses raw HTML string into sequential text and image blocks.
 */
fun parseHtmlContent(rawHtml: String): List<HtmlBlock> {
    if (rawHtml.isBlank()) return emptyList()

    val blocks = mutableListOf<HtmlBlock>()
    // Regex matching <img ... src="..." ...>
    val imgRegex = Regex("""<img[^>]+src=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)

    var lastIndex = 0
    imgRegex.findAll(rawHtml).forEach { matchResult ->
        val textBefore = rawHtml.substring(lastIndex, matchResult.range.first).trim()
        if (textBefore.isNotBlank()) {
            blocks.add(HtmlBlock.TextBlock(textBefore))
        }

        val imageUrl = matchResult.groupValues.getOrNull(1) ?: ""
        if (imageUrl.isNotBlank()) {
            blocks.add(HtmlBlock.ImageBlock(imageUrl = imageUrl))
        }

        lastIndex = matchResult.range.last + 1
    }

    if (lastIndex < rawHtml.length) {
        val remainingText = rawHtml.substring(lastIndex).trim()
        if (remainingText.isNotBlank()) {
            blocks.add(HtmlBlock.TextBlock(remainingText))
        }
    }

    return if (blocks.isEmpty() && rawHtml.isNotBlank()) {
        listOf(HtmlBlock.TextBlock(rawHtml))
    } else {
        blocks
    }
}

@Composable
fun CmsHtmlContentRenderer(
    htmlContent: String,
    isBn: Boolean,
    bengaliFont: String = "tiro",
    modifier: Modifier = Modifier
) {
    val blocks = remember(htmlContent) { parseHtmlContent(htmlContent) }
    val textColor = MaterialTheme.colorScheme.onBackground.toArgb()
    val linkColor = Color(0xFF0084C7).toArgb()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is HtmlBlock.TextBlock -> {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth(),
                        factory = { ctx ->
                            TextView(ctx).apply {
                                movementMethod = LinkMovementMethod.getInstance()
                                textSize = 16f
                                setTextColor(textColor)
                                setLinkTextColor(linkColor)
                                setLineSpacing(0f, 1.4f)
                                setPadding(0, 0, 0, 0)

                                val fontResId = if (isBn) {
                                    if (bengaliFont == "anek") com.example.R.font.anekbangla_semicondensed_regular else com.example.R.font.tiro_bangla_regular
                                } else {
                                    null
                                }
                                if (fontResId != null) {
                                    typeface = androidx.core.content.res.ResourcesCompat.getFont(ctx, fontResId)
                                }
                            }
                        },
                        update = { textView ->
                            textView.setTextColor(textColor)
                            textView.setLinkTextColor(linkColor)
                            textView.text = HtmlCompat.fromHtml(
                                block.html,
                                HtmlCompat.FROM_HTML_MODE_LEGACY
                            )

                            val fontResId = if (isBn) {
                                if (bengaliFont == "anek") com.example.R.font.anekbangla_semicondensed_regular else com.example.R.font.tiro_bangla_regular
                            } else {
                                null
                            }
                            if (fontResId != null) {
                                textView.typeface = androidx.core.content.res.ResourcesCompat.getFont(textView.context, fontResId)
                            }
                        }
                    )
                }

                is HtmlBlock.ImageBlock -> {
                    AsyncImage(
                        model = block.imageUrl,
                        contentDescription = block.altText.ifBlank { "Article Image" },
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }
    }
}
