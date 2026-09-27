package com.example.target

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_CAPTURE = 1001
        private const val REQUEST_NOTIFICATIONS = 1002
    }

    private lateinit var serverInput: EditText
    private lateinit var roomInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var statusText: TextView
    private lateinit var resumeCheck: CheckBox
    private var pendingStart = false
    private var pendingResumeChoice = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 32, 32, 32)
        }

        val saved = SettingsStore.load(this)
        serverInput = input("WebSocket URL", saved.serverUrl)
        roomInput = input("Room ID", saved.room)
        tokenInput = input("Relay token", saved.token)
        tokenInput.inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        resumeCheck = CheckBox(this).apply {
            text = "Tawarkan lanjut setelah reboot"
            isChecked = getSharedPreferences(
                BootResumeReceiver.PREFS_NAME,
                MODE_PRIVATE
            )
                .getBoolean(BootResumeReceiver.PREF_RESUME_AFTER_REBOOT, false)
        }

        val startButton = Button(this).apply {
            text = "Mulai berbagi layar"
        }
        val stopButton = Button(this).apply {
            text = "Hentikan berbagi layar"
        }
        val saveButton = Button(this).apply {
            text = "Simpan pengaturan"
        }
        statusText = TextView(this).apply {
            text = "Belum aktif"
            setPadding(0, 24, 0, 0)
        }

        root.addView(serverInput)
        root.addView(roomInput)
        root.addView(tokenInput)
        root.addView(resumeCheck)
        root.addView(saveButton)
        root.addView(startButton)
        root.addView(stopButton)
        root.addView(statusText)
        setContentView(root)

        startButton.setOnClickListener { validateAndStart() }
        saveButton.setOnClickListener {
            saveSettings()
            statusText.text = "Pengaturan tersimpan"
        }
        stopButton.setOnClickListener {
            stopService(
                Intent(this, ScreenCaptureService::class.java)
                    .setAction(ScreenCaptureService.ACTION_STOP)
            )
            statusText.text = "Berbagi layar dihentikan"
        }
    }

    private fun input(hint: String, value: String): EditText {
        return EditText(this).apply {
            this.hint = hint
            setText(value)
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
        }
    }

    private fun validateAndStart() {
        val url = serverInput.text.toString().trim()
        val room = roomInput.text.toString().trim()
        val token = tokenInput.text.toString()

        if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
            statusText.text = "URL harus dimulai dengan ws:// atau wss://"
            return
        }
        if (room.isBlank() || token.isBlank()) {
            statusText.text = "Room dan token harus diisi"
            return
        }

        saveSettings()
        pendingResumeChoice = resumeCheck.isChecked
        pendingStart = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATIONS
            )
        } else {
            requestScreenCapture()
        }
    }

    private fun saveSettings() {
        SettingsStore.save(
            this,
            RelaySettings(
                serverUrl = serverInput.text.toString().trim(),
                room = roomInput.text.toString().trim(),
                token = tokenInput.text.toString()
            )
        )
    }

    private fun requestScreenCapture() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(
            manager.createScreenCaptureIntent(),
            REQUEST_CAPTURE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_NOTIFICATIONS || !pendingStart) return

        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            requestScreenCapture()
        } else {
            statusText.text = "Izin notifikasi diperlukan untuk sesi yang terlihat"
            pendingStart = false
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CAPTURE) return

        if (resultCode != RESULT_OK || data == null) {
            statusText.text = "Izin MediaProjection dibatalkan"
            pendingStart = false
            return
        }

        getSharedPreferences(
            BootResumeReceiver.PREFS_NAME,
            MODE_PRIVATE
        )
            .edit()
            .putBoolean(
                BootResumeReceiver.PREF_RESUME_AFTER_REBOOT,
                pendingResumeChoice
            )
            .apply()

        val serviceIntent = Intent(this, ScreenCaptureService::class.java)
            .setAction(ScreenCaptureService.ACTION_START)
            .putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            .putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
            .putExtra(
                ScreenCaptureService.EXTRA_SERVER_URL,
                serverInput.text.toString().trim()
            )
            .putExtra(
                ScreenCaptureService.EXTRA_ROOM,
                roomInput.text.toString().trim()
            )
            .putExtra(
                ScreenCaptureService.EXTRA_TOKEN,
                tokenInput.text.toString()
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        statusText.text = "Berbagi aktif; hentikan dari tombol atau notifikasi"
        pendingStart = false
    }
}