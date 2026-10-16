package com.example.utils

/**
 * Universal utility for converting western numerals and numeric strings
 * into standard Bengali digits.
 */
object BengaliNumberUtils {

    private val BENGALI_DIGITS = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

    fun toBengaliDigits(input: String): String {
        return buildString {
            for (ch in input) {
                if (ch in '0'..'9') {
                    append(BENGALI_DIGITS[ch - '0'])
                } else {
                    append(ch)
                }
            }
        }
    }

    fun toBengaliDigits(number: Long): String {
        return toBengaliDigits(number.toString())
    }

    fun toBengaliDigits(number: Int): String {
        return toBengaliDigits(number.toString())
    }

    fun toBengaliDigits(number: Double, decimals: Int = 1): String {
        val formatted = if (decimals > 0) {
            String.format(java.util.Locale.US, "%.${decimals}f", number)
        } else {
            number.toLong().toString()
        }
        return toBengaliDigits(formatted)
    }

    fun formatNumber(number: Int, isBn: Boolean): String {
        return if (isBn) toBengaliDigits(number) else number.toString()
    }

    fun formatNumber(number: Long, isBn: Boolean): String {
        return if (isBn) toBengaliDigits(number) else number.toString()
    }

    fun formatNumber(number: Double, isBn: Boolean, decimals: Int = 1): String {
        return if (isBn) {
            toBengaliDigits(number, decimals)
        } else {
            String.format(java.util.Locale.US, "%.${decimals}f", number)
        }
    }
}
