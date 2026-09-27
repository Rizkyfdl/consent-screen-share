package com.example.observer

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var serverInput: EditText
    private lateinit var roomInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var imageView: ImageView
    private lateinit var statusText: TextView
    private var relay: ObserverRelayClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        val saved = SettingsStore.load(this)
        serverInput = input("WebSocket URL", saved.serverUrl)
        roomInput = input("Room ID", saved.room)
        tokenInput = input("Relay token", saved.token)
        tokenInput.inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        val connect = Button(this).apply { text = "Connect" }
        val disconnect = Button(this).apply { text = "Disconnect" }
        val saveButton = Button(this).apply {
            text = "Simpan pengaturan"
        }
        statusText = TextView(this).apply {
            text = "Belum terhubung"
            setPadding(0, 12, 0, 12)
        }

        val phoneFrame = FrameLayout(this).apply {
            setPadding(18, 28, 18, 28)
            background = GradientDrawable().apply {
                setColor(0xFF050505.toInt())
                cornerRadius = 42f
                setStroke(2, 0xFF555555.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0
            ).apply {
                weight = 1f
                gravity = Gravity.CENTER
            }
        }

        imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setBackgroundColor(0xFF111111.toInt())
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        phoneFrame.addView(imageView)

        root.addView(serverInput)
        root.addView(roomInput)
        root.addView(tokenInput)
        root.addView(saveButton)
        root.addView(connect)
        root.addView(disconnect)
        root.addView(statusText)
        root.addView(phoneFrame)
        setContentView(root)

        connect.setOnClickListener { connectToRelay() }
        saveButton.setOnClickListener {
            saveSettings()
            statusText.text = "Pengaturan tersimpan"
        }
        disconnect.setOnClickListener {
            relay?.close()
            relay = null
            statusText.text = "Terputus"
        }
    }

    private fun input(hint: String, value: String): EditText {
        return EditText(this).apply {
            this.hint = hint
            setText(value)
            singleLine = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8
            }
        }
    }

    private fun connectToRelay() {
        val url = serverInput.text.toString().trim()
        val room = roomInput.text.toString().trim()
        val token = tokenInput.text.toString()

        if ((!url.startsWith("ws://") && !url.startsWith("wss://")) ||
            room.isBlank() ||
            token.isBlank()
        ) {
            statusText.text = "URL, room, dan token harus valid"
            return
        }

        relay?.close()
        saveSettings()
        relay = ObserverRelayClient(
            url = url,
            room = room,
            token = token,
            onFrame = { jpeg ->
                val bitmap = BitmapFactory.decodeByteArray(
                    jpeg,
                    0,
                    jpeg.size
                ) ?: return@ObserverRelayClient
                runOnUiThread { imageView.setImageBitmap(bitmap) }
            },
            onStatus = { status ->
                runOnUiThread { statusText.text = status }
            }
        )
        relay?.connect()
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

    override fun onDestroy() {
        relay?.close()
        relay = null
        super.onDestroy()
    }
}