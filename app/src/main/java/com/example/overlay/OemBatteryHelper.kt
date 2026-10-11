package com.example.overlay

import android.os.Build
import android.os.Bundle

/**
 * Detects the device manufacturer and returns OEM-specific battery/process optimisation
 * instructions to show during first-time overlay setup.
 *
 * Returns null for manufacturers without known aggressive process-killing behaviour
 * (stock Android / Google Pixel devices), in which case no onboarding dialog is needed.
 */
object OemBatteryHelper {

    data class OemInstructions(
        val manufacturer: String,
        val titleBn: String,
        val titleEn: String,
        val stepsBn: List<String>,
        val stepsEn: List<String>
    )

    fun getInstructionsIfNeeded(): OemInstructions? {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()

        return when {
            brand.contains("xiaomi") || brand.contains("redmi") ||
            brand.contains("poco") || manufacturer.contains("xiaomi") -> xiaomiInstructions()

            brand.contains("samsung") || manufacturer.contains("samsung") -> samsungInstructions()

            brand.contains("oneplus") || manufacturer.contains("oneplus") -> oneplusInstructions()

            brand.contains("huawei") || brand.contains("honor") ||
            manufacturer.contains("huawei") -> huaweiInstructions()

            brand.contains("oppo") || brand.contains("realme") ||
            manufacturer.contains("oppo") -> oppoInstructions()

            brand.contains("vivo") || manufacturer.contains("vivo") -> vivoInstructions()

            // Stock Android (Google, AOSP), Sony, Motorola, Nokia — no special steps needed
            else -> null
        }
    }

    private fun xiaomiInstructions() = OemInstructions(
        manufacturer = "Xiaomi / MIUI / HyperOS",
        titleBn = "Xiaomi-তে ওভারলে সার্ভিস সক্রিয় রাখতে",
        titleEn = "Keep overlay active on Xiaomi",
        stepsBn = listOf(
            "সেটিংস → অ্যাপস → খোঁজ অ্যাপ → ব্যাটারি সেভার → \"কোনো বাধা নেই\" নির্বাচন করুন",
            "সেটিংস → অ্যাপস → খোঁজ → অটোস্টার্ট → চালু করুন",
            "রিসেন্ট অ্যাপস-এ খোঁজ অ্যাপ দীর্ঘক্ষণ ধরে লক আইকন ট্যাপ করুন"
        ),
        stepsEn = listOf(
            "Settings → Apps → Khoj → Battery Saver → select \"No restrictions\"",
            "Settings → Apps → Khoj → Autostart → toggle ON",
            "In Recents, long-press Khoj and tap the lock icon to pin it"
        )
    )

    private fun samsungInstructions() = OemInstructions(
        manufacturer = "Samsung One UI",
        titleBn = "Samsung-এ ওভারলে সার্ভিস সক্রিয় রাখতে",
        titleEn = "Keep overlay active on Samsung",
        stepsBn = listOf(
            "সেটিংস → অ্যাপস → খোঁজ → ব্যাটারি → \"আনরেস্ট্রিক্টেড\" নির্বাচন করুন",
            "সেটিংস → ডিভাইস কেয়ার → ব্যাটারি → ব্যাকগ্রাউন্ড ব্যবহার সীমা → খোঁজ অ্যাপ ব্যতিক্রম হিসেবে যোগ করুন"
        ),
        stepsEn = listOf(
            "Settings → Apps → Khoj → Battery → select \"Unrestricted\"",
            "Settings → Device Care → Battery → Background usage limits → Add Khoj as an exception"
        )
    )

    private fun oneplusInstructions() = OemInstructions(
        manufacturer = "OnePlus OxygenOS",
        titleBn = "OnePlus-এ ওভারলে সার্ভিস সক্রিয় রাখতে",
        titleEn = "Keep overlay active on OnePlus",
        stepsBn = listOf(
            "সেটিংস → ব্যাটারি → ব্যাটারি অপ্টিমাইজেশন → খোঁজ অ্যাপ → \"অপ্টিমাইজ করবেন না\" নির্বাচন করুন",
            "সেটিংস → অ্যাপস → খোঁজ → অ্যাপ লঞ্চার → অটো লঞ্চ চালু করুন"
        ),
        stepsEn = listOf(
            "Settings → Battery → Battery Optimization → Khoj → select \"Don't optimize\"",
            "Settings → Apps → Khoj → App launcher → enable Auto Launch"
        )
    )

    private fun huaweiInstructions() = OemInstructions(
        manufacturer = "Huawei EMUI / Honor",
        titleBn = "Huawei-তে ওভারলে সার্ভিস সক্রিয় রাখতে",
        titleEn = "Keep overlay active on Huawei",
        stepsBn = listOf(
            "সেটিংস → ব্যাটারি → অ্যাপ লঞ্চ → খোঁজ অ্যাপ → ম্যানুয়াল → অটো-লঞ্চ, সেকেন্ডারি লঞ্চ ও ব্যাকগ্রাউন্ড সব চালু করুন",
            "সেটিংস → অ্যাপস → খোঁজ → ব্যাটারি → \"ব্যাকগ্রাউন্ড কার্যকলাপ চালু করুন\" নির্বাচন করুন"
        ),
        stepsEn = listOf(
            "Settings → Battery → App Launch → Khoj → Manual → enable Auto-launch, Secondary launch, and Run in background",
            "Settings → Apps → Khoj → Battery → enable \"Run in background\""
        )
    )

    private fun oppoInstructions() = OemInstructions(
        manufacturer = "OPPO / Realme ColorOS",
        titleBn = "OPPO/Realme-তে ওভারলে সার্ভিস সক্রিয় রাখতে",
        titleEn = "Keep overlay active on OPPO / Realme",
        stepsBn = listOf(
            "সেটিংস → ব্যাটারি → আরও ব্যাটারি সেটিংস → অপ্টিমাইজ করা স্লিপ → খোঁজ অ্যাপ সরিয়ে দিন",
            "সেটিংস → অ্যাপস → ব্যাটারি → খোঁজ → \"ব্যাকগ্রাউন্ড অ্যাক্টিভিটি অনুমতি দিন\" চালু করুন"
        ),
        stepsEn = listOf(
            "Settings → Battery → More Battery Settings → Optimized Sleep → remove Khoj from the list",
            "Settings → Apps → Battery → Khoj → enable \"Allow background activity\""
        )
    )

    private fun vivoInstructions() = OemInstructions(
        manufacturer = "Vivo FunTouch OS",
        titleBn = "Vivo-তে ওভারলে সার্ভিস সক্রিয় রাখতে",
        titleEn = "Keep overlay active on Vivo",
        stepsBn = listOf(
            "সেটিংস → ব্যাটারি → হাই ব্যাকগ্রাউন্ড পাওয়ার কনসামশন → খোঁজ অ্যাপ যোগ করুন",
            "সেটিংস → অ্যাপস → খোঁজ → ডেটা ব্যবহার → ব্যাকগ্রাউন্ড ডেটা চালু করুন"
        ),
        stepsEn = listOf(
            "Settings → Battery → High Background Power Consumption → add Khoj",
            "Settings → Apps → Khoj → Data Usage → enable Background Data"
        )
    )
}
