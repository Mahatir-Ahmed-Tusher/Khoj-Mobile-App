package com.example.overlay

import android.content.ClipboardManager
import android.content.Context

/**
 * Reads the current clipboard text when the FAB is tapped.
 *
 * This helper is intentionally simple: it only reads the clipboard in direct response to
 * an explicit user action (tapping the FAB), never in the background.
 *
 * Android 12+ shows a "X pasted from Khoj" toast automatically when clipboard is read
 * while the app is not in the foreground. This is expected and compliant behaviour — it
 * informs the user that the app accessed the clipboard only when they interacted with the FAB.
 */
object ClipboardHelper {

    /**
     * Returns the current clipboard text, or null if the clipboard is empty,
     * does not contain plain text, or is inaccessible.
     */
    fun getCurrentClipboardText(context: Context): String? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val item = clip.getItemAt(0) ?: return null
        val text = item.coerceToText(context)?.toString()?.trim() ?: return null
        return if (text.isBlank()) null else text
    }
}
