package com.example.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.R
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.random.Random

object NetworkErrorHelper {

    val OFFLINE_ANIMATIONS = listOf(
        R.raw.no_internet,
        R.raw.no_connection
    )

    /**
     * Determines whether the given throwable or its underlying cause represents a
     * network transport, DNS, timeout, or connectivity failure.
     */
    fun isNetworkException(throwable: Throwable?): Boolean {
        if (throwable == null) return false

        var current: Throwable? = throwable
        while (current != null) {
            if (current is UnknownHostException ||
                current is ConnectException ||
                current is SocketTimeoutException ||
                current is SocketException ||
                current is NoRouteToHostException ||
                current is ProtocolException ||
                current is SSLException
            ) {
                return true
            }

            val msg = current.message?.lowercase() ?: ""
            if (msg.contains("unable to resolve host") ||
                msg.contains("failed to connect") ||
                msg.contains("no address associated with hostname") ||
                msg.contains("network error") ||
                msg.contains("connection error") ||
                msg.contains("timed out") ||
                msg.contains("timeout") ||
                msg.contains("connection reset") ||
                msg.contains("software caused connection abort") ||
                msg.contains("নেটওয়ার্ক সমস্যা") ||
                msg.contains("ইন্টারনেট সংযোগ")
            ) {
                return true
            }

            current = current.cause
        }
        return false
    }

    /**
     * Checks synchronously if the device currently has an active Internet connection.
     */
    fun isDeviceOnline(context: Context): Boolean {
        val connectivityManager =
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Returns one of the two offline Lottie animations (no_internet or no_connection),
     * dynamically shuffled or picked based on an optional seed/key.
     */
    fun getShuffledOfflineAnimation(seed: Any? = null): Int {
        return if (seed != null) {
            val hash = kotlin.math.abs(seed.hashCode())
            OFFLINE_ANIMATIONS[hash % OFFLINE_ANIMATIONS.size]
        } else {
            OFFLINE_ANIMATIONS[Random.nextInt(OFFLINE_ANIMATIONS.size)]
        }
    }

    /**
     * Returns a friendly offline error message based on the language.
     */
    fun getOfflineTitle(isBn: Boolean): String {
        return if (isBn) "ইন্টারনেট সংযোগ নেই" else "No Internet Connection"
    }

    fun getOfflineDescription(isBn: Boolean, featureName: String? = null): String {
        return if (isBn) {
            if (featureName != null) {
                "$featureName ব্যবহার করতে সক্রিয় ইন্টারনেট সংযোগ প্রয়োজন। দয়া করে আপনার ওয়াইফাই বা মোবাইল ডাটা পরীক্ষা করুন।"
            } else {
                "এই সেবাটি ব্যবহার করতে সক্রিয় ইন্টারনেট সংযোগ প্রয়োজন। দয়া করে আপনার ওয়াইফাই বা মোবাইল ডাটা চালু করুন।"
            }
        } else {
            if (featureName != null) {
                "$featureName requires an active internet connection. Please check your Wi-Fi or mobile data."
            } else {
                "An active internet connection is required to use this service. Please check your Wi-Fi or mobile data."
            }
        }
    }
}
