package com.example.observer

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ObserverRelayClient(
    private val url: String,
    private val room: String,
    private val token: String,
    private val onFrame: (ByteArray) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private var socket: WebSocket? = null

    fun connect() {
        socket = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(
                    webSocket: WebSocket,
                    response: Response
                ) {
                    webSocket.send(
                        JSONObject()
                            .put("type", "join")
                            .put("room", room)
                            .put("role", "observer")
                            .put("token", token)
                            .toString()
                    )
                    onStatus("Observer terhubung")
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    bytes: ByteString
                ) {
                    onFrame(bytes.toByteArray())
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    text: String
                ) {
                    onStatus(text)
                }

                override fun onClosed(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String
                ) {
                    onStatus("Koneksi ditutup: $reason")
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: Response?
                ) {
                    onStatus("Koneksi gagal: ${t.message}")
                }
            }
        )
    }

    fun close() {
        socket?.close(1000, "Observer stopped")
        socket = null
        client.dispatcher.executorService.shutdown()
    }
}