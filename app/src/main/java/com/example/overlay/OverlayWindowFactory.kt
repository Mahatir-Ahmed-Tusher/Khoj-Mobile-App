package com.example.overlay

import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Shared factory for creating TYPE_APPLICATION_OVERLAY ComposeView windows.
 *
 * Centralises the repetitive WindowManager.LayoutParams setup and the
 * ViewTree wiring (lifecycle owner + saved-state registry owner) that every
 * overlay window needs.
 *
 * Uses the Kotlin extension-function API:
 *   • [setViewTreeLifecycleOwner]   — from androidx.lifecycle:lifecycle-runtime-ktx
 *   • [setViewTreeSavedStateRegistryOwner] — from androidx.savedstate:savedstate-ktx
 */
object OverlayWindowFactory {

    val overlayType: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    /** Full-screen touch-intercepting window (for dialogs, drawer). */
    fun fullScreenParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        overlayType,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    )

    /** Full-screen touch-intercepting window configured with SOFT_INPUT_ADJUST_RESIZE for chat text input. */
    fun chatDrawerParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        overlayType,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    }

    /** Wrap-content, non-focusable window for the draggable FAB. */
    fun fabParams(x: Int = 0, y: Int = 200): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }

    /**
     * Builds a fresh [SavedStateRegistryOwner] whose lifecycle starts at [Lifecycle.State.INITIALIZED].
     *
     * [SavedStateRegistryController.performRestore] internally calls [performAttach] which asserts
     * `lifecycle.currentState == INITIALIZED`. Using the *service's* lifecycle (already STARTED or
     * RESUMED by the time overlay windows are created) crashes with:
     *
     *   IllegalStateException: Restarter must be created only during owner's initialization stage
     *
     * Solution: every overlay window gets its own [LifecycleRegistry] that starts at INITIALIZED,
     * satisfying the assertion, then immediately advances to RESUMED so Compose effects run.
     * The service lifecycle is set separately via [setViewTreeLifecycleOwner] and is used for
     * all Compose coroutines / state collection.
     */
    fun buildSavedStateOwner(service: OverlayModeService): SavedStateRegistryOwner {
        return object : SavedStateRegistryOwner {
            private val localLifecycle = LifecycleRegistry(this)
            private val controller = SavedStateRegistryController.create(this)

            init {
                // lifecycle is INITIALIZED here → performAttach() assertion passes
                controller.performRestore(null)
                // Advance to RESUMED so Compose can run LaunchedEffect / remember / etc.
                localLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                localLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
                localLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }

            override val savedStateRegistry: SavedStateRegistry
                get() = controller.savedStateRegistry
            override val lifecycle: Lifecycle
                get() = localLifecycle
        }
    }

    /**
     * Creates a [ComposeView] correctly wired to [service]'s lifecycle and a fresh
     * [SavedStateRegistryOwner]. Used by windows created from [OverlayWindowFactory]
     * directly rather than from the service's own [addOverlayComposeWindow].
     */
    fun createComposeView(
        service: OverlayModeService,
        content: @Composable () -> Unit
    ): ComposeView {
        val savedStateOwner = buildSavedStateOwner(service)
        return ComposeView(service).apply {
            setViewTreeLifecycleOwner(service as LifecycleOwner)
            setViewTreeSavedStateRegistryOwner(savedStateOwner)
            setContent(content)
        }
    }
}
