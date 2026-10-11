package com.example.overlay

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.example.data.repository.AuthRepository

/**
 * Transparent activity that receives Android's ACTION_PROCESS_TEXT intent.
 *
 * This activity is registered in AndroidManifest.xml with an ACTION_PROCESS_TEXT intent filter,
 * which makes "Fact Check" appear in the system floating text-selection toolbar across all apps
 * (Chrome, Gmail, Facebook, WhatsApp, etc.). No special permissions are required for this.
 *
 * Flow:
 *  1. User selects text in any app → taps "Fact Check" in the system toolbar.
 *  2. Android delivers the selected text to this Activity via EXTRA_PROCESS_TEXT.
 *  3. If the overlay service is running, the text is sent to [OverlayStateRepository] and
 *     the result drawer slides open.
 *  4. If the overlay service is NOT running (user has not enabled overlay mode), the text is
 *     forwarded to [com.example.MainActivity] as a standard fact-check request.
 *  5. This activity immediately finishes — it is never visible to the user.
 */
class ProcessTextActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val selectedText = intent
            .getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            ?.toString()
            ?.trim()

        if (!selectedText.isNullOrBlank()) {
            handleSelectedText(selectedText)
        }

        finish()
    }

    private fun handleSelectedText(text: String) {
        if (OverlayModeService.isRunning) {
            // Overlay is active — push text into the shared state and open the drawer
            OverlayStateRepository.setPendingClaimText(text, source = "text")
            OverlayStateRepository.setDrawerOpen(true)
        } else {
            // Overlay is not active — forward text to the main app
            val intent = Intent(this, com.example.MainActivity::class.java).apply {
                action = ACTION_OVERLAY_FACT_CHECK
                putExtra(EXTRA_CLAIM_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        }
    }

    companion object {
        const val ACTION_OVERLAY_FACT_CHECK = "com.example.action.OVERLAY_FACT_CHECK"
        const val EXTRA_CLAIM_TEXT = "extra_claim_text"
    }
}
