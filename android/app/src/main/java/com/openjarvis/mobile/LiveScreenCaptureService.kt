package com.openjarvis.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Latest downscaled frame is held in memory only; it is never uploaded automatically. */
object LiveScreenCaptureState {
    private val mutableFrame = MutableStateFlow<String?>(null)
    private val mutableActive = MutableStateFlow(false)

    val frameBase64: StateFlow<String?> = mutableFrame
    val isActive: StateFlow<Boolean> = mutableActive

    private val mutableOverlayEnabled = MutableStateFlow(false)
    val isOverlayEnabled: StateFlow<Boolean> = mutableOverlayEnabled

    internal fun setOverlayEnabled(value: Boolean) {
        mutableOverlayEnabled.value = value
    }

    internal fun publishFrame(value: String) {
        mutableFrame.value = value
    }

    internal fun setActive(value: Boolean) {
        mutableActive.value = value
        if (!value) mutableFrame.value = null
    }
}

class LiveScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var lastFrameAt = 0L
    @Volatile private var capturePaused = false

    private var overlayRequested = false
    private var appInForeground = true
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            releaseCapture(stopProjection = false)
            overlayRequested = false
            LiveScreenCaptureState.setOverlayEnabled(false)
            removeFloatingPanel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {

            ACTION_SHOW_OVERLAY -> {
                overlayRequested = true
                LiveScreenCaptureState.setOverlayEnabled(true)
                if (!appInForeground) showFloatingPanel()
                return START_NOT_STICKY
            }
            ACTION_HIDE_OVERLAY -> {
                overlayRequested = false
                LiveScreenCaptureState.setOverlayEnabled(false)
                removeFloatingPanel()
                return START_NOT_STICKY
            }
            ACTION_APP_BACKGROUND -> {
                appInForeground = false
                if (overlayRequested) showFloatingPanel()
                return START_NOT_STICKY
            }
            ACTION_APP_FOREGROUND -> {
                appInForeground = true
                removeFloatingPanel()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                releaseCapture(stopProjection = true)
                overlayRequested = false
                LiveScreenCaptureState.setOverlayEnabled(false)
                removeFloatingPanel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                capturePaused = true
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                capturePaused = false
                return START_NOT_STICKY
            }
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = readResultData(intent)
        if (resultCode == 0 || resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            createNotificationChannel()
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            beginProjection(resultCode, resultData)
        } catch (_: Exception) {
            releaseCapture(stopProjection = true)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun readResultData(intent: Intent?): Intent? {
        if (intent == null) return null
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent
        }
    }

    private fun beginProjection(resultCode: Int, resultData: Intent) {
        releaseCapture(stopProjection = true)
        capturePaused = false
        lastFrameAt = 0L
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val density = metrics.densityDpi

        val activeProjection = manager.getMediaProjection(resultCode, resultData)
            ?: error("Android did not grant screen capture.")
        projection = activeProjection
        activeProjection.registerCallback(projectionCallback, Handler(mainLooper))

        captureThread = HandlerThread("JarvisScreenCapture").also { it.start() }
        captureHandler = Handler(captureThread!!.looper)
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
        imageReader = reader
        reader.setOnImageAvailableListener({ source ->
            val image = try {
                source.acquireLatestImage()
            } catch (_: Exception) {
                null
            } ?: return@setOnImageAvailableListener

            try {
                val now = SystemClock.elapsedRealtime()
                if (!capturePaused && now - lastFrameAt >= FRAME_INTERVAL_MS) {
                    lastFrameAt = now
                    val plane = image.planes.firstOrNull() ?: return@setOnImageAvailableListener
                    val pixelStride = plane.pixelStride.coerceAtLeast(1)
                    val rowPadding = (plane.rowStride - pixelStride * image.width).coerceAtLeast(0)
                    val paddedWidth = image.width + rowPadding / pixelStride
                    val padded = Bitmap.createBitmap(
                        paddedWidth,
                        image.height,
                        Bitmap.Config.ARGB_8888,
                    )
                    padded.copyPixelsFromBuffer(plane.buffer)
                    val cropped = if (paddedWidth == image.width) {
                        padded
                    } else {
                        Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
                    }
                    val scale = minOf(1f, MAX_FRAME_WIDTH.toFloat() / cropped.width)
                    val scaled = if (scale < 1f) {
                        Bitmap.createScaledBitmap(
                            cropped,
                            (cropped.width * scale).toInt().coerceAtLeast(1),
                            (cropped.height * scale).toInt().coerceAtLeast(1),
                            true,
                        )
                    } else {
                        cropped
                    }
                    val jpeg = ByteArrayOutputStream().use { output ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                        output.toByteArray()
                    }
                    LiveScreenCaptureState.publishFrame(
                        Base64.encodeToString(jpeg, Base64.NO_WRAP),
                    )
                    if (scaled !== cropped) scaled.recycle()
                    if (cropped !== padded) cropped.recycle()
                    padded.recycle()
                }
            } catch (_: Exception) {
                // A single bad frame must not terminate the active share session.
            } finally {
                image.close()
            }
        }, captureHandler)

        virtualDisplay = activeProjection.createVirtualDisplay(
            "JarvisLiveScreen",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            captureHandler,
        )
        LiveScreenCaptureState.setActive(true)
    }

    private fun releaseCapture(stopProjection: Boolean) {
        overlayRequested = false
        LiveScreenCaptureState.setOverlayEnabled(false)
        removeFloatingPanel()
        imageReader?.setOnImageAvailableListener(null, null)
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        captureThread?.quitSafely()
        captureThread = null
        captureHandler = null

        val activeProjection = projection
        projection = null
        if (stopProjection) {
            try {
                activeProjection?.stop()
            } catch (_: Exception) {
                // It may already have been stopped by Android.
            }
        } else {
            try {
                activeProjection?.unregisterCallback(projectionCallback)
            } catch (_: Exception) {
                // Ignore cleanup after the system ends a projection.
            }
        }
        LiveScreenCaptureState.setActive(false)
    }

    private fun createNotificationChannel() {

    private fun showFloatingPanel() {
        if (!overlayRequested || appInForeground || !LiveScreenCaptureState.isActive.value) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            overlayRequested = false
            LiveScreenCaptureState.setOverlayEnabled(false)
            return
        }
        if (overlayView != null) return

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(AndroidColor.rgb(8, 22, 35))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), AndroidColor.rgb(51, 214, 242))
            }
            elevation = dp(10).toFloat()
        }
        val title = TextView(this).apply {
            text = "J.A.R.V.I.S.  ·  LIVE"
            setTextColor(AndroidColor.rgb(51, 214, 242))
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        val detail = TextView(this).apply {
            text = "Screen sharing is active. Frames are captured on this phone. Return to Jarvis to ask about the latest frame."
            setTextColor(AndroidColor.rgb(224, 239, 248))
            textSize = 12f
            setPadding(0, dp(7), 0, dp(9))
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        fun actionButton(label: String, onClick: () -> Unit): Button =
            Button(this).apply {
                text = label
                isAllCaps = false
                textSize = 11f
                setOnClickListener { onClick() }
                layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                    marginEnd = dp(4)
                }
            }
        actions.addView(actionButton("Open Jarvis") {
            val open = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_OPEN_LIVE, true)
            }
            startActivity(open)
        })
        actions.addView(actionButton("Stop share") {
            startService(stopIntent(this))
        })
        panel.addView(title)
        panel.addView(detail)
        panel.addView(actions)

        val params = WindowManager.LayoutParams(
            dp(286),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(100)
        }
        panel.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var startRawX = 0f
            private var startRawY = 0f
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        startRawX = event.rawX
                        startRawY = event.rawY
                        return false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = (startX - (event.rawX - startRawX).toInt()).coerceAtLeast(0)
                        params.y = (startY + (event.rawY - startRawY).toInt()).coerceAtLeast(dp(24))
                        try {
                            wm.updateViewLayout(view, params)
                        } catch (_: Exception) {
                            return false
                        }
                        return true
                    }
                }
                return false
            }
        })
        try {
            wm.addView(panel, params)
            overlayView = panel
        } catch (_: Exception) {
            overlayRequested = false
            LiveScreenCaptureState.setOverlayEnabled(false)
        }
    }

    private fun removeFloatingPanel() {
        val view = overlayView ?: return
        try {
            (windowManager ?: getSystemService(WINDOW_SERVICE) as WindowManager).removeView(view)
        } catch (_: Exception) {
            // The host activity or system may already have removed the overlay.
        } finally {
            overlayView = null
            windowManager = null
        }
    }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Jarvis screen sharing",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shown while Jarvis is allowed to view your shared screen."
            },
        )
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return builder
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Jarvis is viewing your shared screen")
            .setContentText("Screen frames stay on-device until you choose Ask in Jarvis.")
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_pause,
                    "Stop sharing",
                    stopPendingIntent,
                ).build(),
            )
            .build()
    }

    override fun onDestroy() {
        releaseCapture(stopProjection = true)
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.openjarvis.mobile.action.STOP_SCREEN_SHARE"
        const val ACTION_PAUSE = "com.openjarvis.mobile.action.PAUSE_SCREEN_SHARE"
        const val ACTION_RESUME = "com.openjarvis.mobile.action.RESUME_SCREEN_SHARE"
        const val ACTION_SHOW_OVERLAY = "com.openjarvis.mobile.action.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.openjarvis.mobile.action.HIDE_OVERLAY"
        const val ACTION_APP_BACKGROUND = "com.openjarvis.mobile.action.APP_BACKGROUND"
        const val ACTION_APP_FOREGROUND = "com.openjarvis.mobile.action.APP_FOREGROUND"
        const val EXTRA_RESULT_CODE = "screen_result_code"
        const val EXTRA_RESULT_DATA = "screen_result_data"
        private const val CHANNEL_ID = "jarvis_screen_share"
        private const val NOTIFICATION_ID = 4107
        private const val FRAME_INTERVAL_MS = 1_500L
        private const val MAX_FRAME_WIDTH = 960
        private const val JPEG_QUALITY = 62

        fun startIntent(context: Context, resultCode: Int, resultData: Intent): Intent =
            Intent(context, LiveScreenCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, LiveScreenCaptureService::class.java).setAction(ACTION_STOP)
    }
}
