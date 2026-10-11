package com.example.overlay

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

/**
 * Lightweight transparent activity whose sole purpose is to trigger the system
 * screen capture consent dialog (MediaProjection).
 *
 * Android requires this dialog to be shown while an Activity is in the foreground.
 * The OverlayModeService starts this activity when the user taps "Screenshot" and
 * no MediaProjection token is yet available. After the user responds to the system
 * dialog, the result is forwarded back to the service.
 *
 * This activity is never visible to the user — it is declared with a fully transparent
 * theme and immediately finishes after handing off the result.
 */
class MediaProjectionConsentActivity : Activity() {

    private val MEDIA_PROJECTION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(), MEDIA_PROJECTION_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == MEDIA_PROJECTION_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                OverlayModeService.deliverMediaProjectionResult(this, resultCode, data)
            }
            // Either way, this activity's job is done
            finish()
        }
    }
}
