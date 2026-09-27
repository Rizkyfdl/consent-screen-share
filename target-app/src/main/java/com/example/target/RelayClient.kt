package com.example.target

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class RelayClient(
    private val url: String,
    private val room: String,
    private val token: String
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var open = false

    fun connect() {
        socket = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    open = true
                    webSocket.send(
                        JSONObject()
                            .put("type", "join")
                            .put("room", room)
                            .put("role", "target")
                            .put("token", token)
                            .toString()
                    )
                }

                override fun onClosed(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String
                ) {
                    open = false
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: Response?
                ) {
                    open = false
                }
            }
        )
    }

    fun sendFrame(jpeg: ByteArray): Boolean {
        val current = socket ?: return false
        if (!open) return false
        return current.send(ByteString.of(*jpeg))
    }

    fun close() {
        open = false
        socket?.close(1000, "Capture stopped")
        socket = null
        client.dispatcher.executorService.shutdown()
    }
}