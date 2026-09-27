package com.example.target

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import android.util.DisplayMetrics
import android.view.WindowManager
import android.content.pm.ServiceInfo
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ScreenCaptureService : Service() {

    companion object {
        const val ACTION_START = "com.example.target.START"
        const val ACTION_STOP = "com.example.target.STOP"
        const val EXTRA_RESULT_CODE = "com.example.target.RESULT_CODE"
        const val EXTRA_RESULT_DATA = "com.example.target.RESULT_DATA"
        const val EXTRA_SERVER_URL = "com.example.target.SERVER_URL"
        const val EXTRA_ROOM = "com.example.target.ROOM"
        const val EXTRA_TOKEN = "com.example.target.TOKEN"

        private const val CHANNEL_ID = "screen_share"
        private const val NOTIFICATION_ID = 4001
    }

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var relay: RelayClient? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var lastFrameAt = 0L
    private var stopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action != ACTION_START || projection != null) {
            return START_NOT_STICKY
        }

        val serverUrl = intent.getStringExtra(EXTRA_SERVER_URL)
            ?: return START_NOT_STICKY
        val room = intent.getStringExtra(EXTRA_ROOM)
            ?: return START_NOT_STICKY
        val token = intent.getStringExtra(EXTRA_TOKEN)
            ?: return START_NOT_STICKY
        val resultCode = intent.getIntExtra(
            EXTRA_RESULT_CODE,
            Activity.RESULT_CANCELED
        )

        val resultData: Intent? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_RESULT_DATA)
            }

        if (resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        startCapture(resultCode, resultData, serverUrl, room, token)
        return START_NOT_STICKY
    }

    private fun startCapture(
        resultCode: Int,
        resultData: Intent,
        serverUrl: String,
        room: String,
        token: String
    ) {
        relay = RelayClient(serverUrl, room, token).also { it.connect() }

        thread = HandlerThread("screen-capture-thread").also { it.start() }
        handler = Handler(thread!!.looper)

        val manager = getSystemService(MediaProjectionManager::class.java)
        projection = manager.getMediaProjection(resultCode, resultData)

        val metrics = screenMetrics()
        val scale = min(
            1.0f,
            720.0f / max(metrics.widthPixels, metrics.heightPixels)
        )
        val width = (metrics.widthPixels * scale).roundToInt().coerceAtLeast(1)
        val height = (metrics.heightPixels * scale).roundToInt().coerceAtLeast(1)

        reader = ImageReader.newInstance(
            width,
            height,
            android.graphics.PixelFormat.RGBA_8888,
            2
        )

        reader!!.setOnImageAvailableListener(
            { processLatestImage(it, width, height) },
            handler
        )

        projection!!.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    stopCapture()
                    stopSelf()
                }
            },
            handler
        )

        display = projection!!.createVirtualDisplay(
            "ConsentScreenShare",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface,
            null,
            handler
        )
    }

    private fun processLatestImage(
        imageReader: ImageReader,
        width: Int,
        height: Int
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastFrameAt < 100L) {
            imageReader.acquireLatestImage()?.close()
            return
        }
        lastFrameAt = now

        val image = imageReader.acquireLatestImage() ?: return
        try {
            val plane = image.planes.firstOrNull() ?: return
            val pixelStride = plane.pixelStride
            val rowPadding = plane.rowStride - pixelStride * width
            val paddedWidth = width + rowPadding / pixelStride
            val bitmap = Bitmap.createBitmap(
                paddedWidth,
                height,
                Bitmap.Config.ARGB_8888
            )

            plane.buffer.rewind()
            bitmap.copyPixelsFromBuffer(plane.buffer)

            val cropped = if (paddedWidth != width) {
                Bitmap.createBitmap(bitmap, 0, 0, width, height)
            } else {
                bitmap
            }

            val jpeg = ByteArrayOutputStream().use { output ->
                cropped.compress(Bitmap.CompressFormat.JPEG, 60, output)
                output.toByteArray()
            }

            relay?.sendFrame(jpeg)
            if (cropped !== bitmap) cropped.recycle()
            bitmap.recycle()
        } catch (_: Throwable) {
            // A single unreadable frame should not stop the session.
        } finally {
            image.close()
        }
    }

    private fun screenMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        val windowManager =
            getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Screen sharing",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, ScreenCaptureService::class.java)
            .setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            500,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Berbagi layar aktif")
            .setContentText("Layar sedang dikirim ke observer")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(
                        this,
                        android.R.drawable.ic_media_pause
                    ),
                    "Hentikan",
                    stopPendingIntent
                ).build()
            )
            .build()
    }

    private fun stopCapture() {
        if (stopping) return
        stopping = true

        reader?.setOnImageAvailableListener(null, null)
        reader?.close()
        reader = null
        display?.release()
        display = null
        projection?.stop()
        projection = null
        relay?.close()
        relay = null
        thread?.quitSafely()
        thread = null
        handler = null
        stopping = false
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }
}