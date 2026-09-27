const http = require("http");
const https = require("https");
const fs = require("fs");
const { WebSocketServer, WebSocket } = require("ws");

const PORT = Number(process.env.PORT || 8080);
const RELAY_TOKEN = process.env.RELAY_TOKEN || "change-this-token";
const rooms = new Map();

const TLS_KEY_FILE = process.env.TLS_KEY_FILE;
const TLS_CERT_FILE = process.env.TLS_CERT_FILE;

const server = TLS_KEY_FILE && TLS_CERT_FILE
  ? https.createServer({
      key: fs.readFileSync(TLS_KEY_FILE),
      cert: fs.readFileSync(TLS_CERT_FILE),
    }, requestHandler)
  : http.createServer(requestHandler);

function getRoom(id) {
  if (!rooms.has(id)) {
    rooms.set(id, { target: null, observers: new Set() });
  }
  return rooms.get(id);
}

function removeClient(client) {
  if (!client) return;
  const room = rooms.get(client.roomId);
  if (!room) return;

  if (client.role === "target" && room.target === client.ws) {
    room.target = null;
  }
  if (client.role === "observer") {
    room.observers.delete(client.ws);
  }
  if (!room.target && room.observers.size === 0) {
    rooms.delete(client.roomId);
  }
}

function requestHandler(req, res) {
  if (req.url === "/health") {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ ok: true }));
    return;
  }
  res.writeHead(404);
  res.end("Not found");
}

const wss = new WebSocketServer({
  server,
  maxPayload: 4 * 1024 * 1024,
  perMessageDeflate: false,
});

wss.on("connection", (ws) => {
  let client = null;
  let joined = false;

  ws.on("message", (data, isBinary) => {
    if (!joined) {
      let message;
      try {
        message = JSON.parse(data.toString());
      } catch {
        ws.close(1008, "Invalid join message");
        return;
      }

      const roomId = String(message.room || "").trim();
      const role = String(message.role || "").trim();
      const token = String(message.token || "");

      if (
        message.type !== "join" ||
        !/^[a-zA-Z0-9_-]{1,64}$/.test(roomId) ||
        !["target", "observer"].includes(role) ||
        token !== RELAY_TOKEN
      ) {
        ws.close(1008, "Unauthorized or invalid join");
        return;
      }

      const room = getRoom(roomId);
      if (
        role === "target" &&
        room.target &&
        room.target.readyState === WebSocket.OPEN
      ) {
        ws.close(4002, "Room already has a target");
        return;
      }

      client = { ws, roomId, role };
      if (role === "target") room.target = ws;
      else room.observers.add(ws);
      joined = true;
      ws.send(JSON.stringify({ type: "joined", room: roomId, role }));
      return;
    }

    if (!client || client.role !== "target" || !isBinary) return;
    const room = rooms.get(client.roomId);
    if (!room) return;

    for (const observer of room.observers) {
      if (observer.readyState === WebSocket.OPEN) {
        observer.send(data, { binary: true });
      }
    }
  });

  ws.on("close", () => removeClient(client));
  ws.on("error", () => removeClient(client));
});

server.listen(PORT, "0.0.0.0", () => {
  const protocol = TLS_KEY_FILE && TLS_CERT_FILE ? "wss" : "ws";
  console.log(`Relay listening on ${protocol}://0.0.0.0:${PORT}`);
});