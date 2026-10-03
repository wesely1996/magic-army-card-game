/**
 * The King of the Beasts online relay.
 *
 * Two phones that can't reach each other directly (different networks, mobile data) both connect
 * here with the same room code: one as the host, one as the guest. The relay pairs them and passes
 * every text message from one to the other, unchanged. It knows nothing about the game: both apps run
 * the same rules engine, check each other's versions and catch any disagreement themselves.
 *
 *   GET /                                       "ok" (lets the app check the server is up)
 *   GET /room/<CODE>?role=host|guest[&new=1]    WebSocket
 *
 * `new=1` is a first visit: a host's code must be free, and a guest's code must have a host waiting.
 * Without it the phone is coming back to a room (after a dropped connection or a restart) and simply
 * waits for the other player.
 *
 * Besides the players' own messages the relay sends three of its own, one JSON line each:
 *   {"relay":"waiting"}   connected; the other player isn't here (yet)
 *   {"relay":"paired"}    both players are here; messages now flow
 *   {"relay":"left"}      the other player's connection closed
 * and turns a phone away by closing with 4404 (no such game), 4409 (code taken / game full) or
 * 4400 (bad request). A phone that reconnects replaces its own older connection (closed with 4000).
 */

const CODE = /^[A-Z2-9]{4,8}$/;
const MAX_MESSAGE = 64 * 1024;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === "/" || url.pathname === "/health") {
      return new Response("ok\n", { headers: { "content-type": "text/plain" } });
    }
    const match = url.pathname.match(/^\/room\/([^/]+)$/);
    if (!match) return new Response("Not found\n", { status: 404 });
    const code = match[1].toUpperCase();
    const role = url.searchParams.get("role");
    if (!CODE.test(code) || (role !== "host" && role !== "guest")) {
      return new Response("Bad room code or role\n", { status: 400 });
    }
    if (request.headers.get("Upgrade") !== "websocket") {
      return new Response("Expected a WebSocket\n", { status: 426 });
    }
    const room = env.ROOMS.get(env.ROOMS.idFromName(code));
    return room.fetch(request);
  },
};

/**
 * One room: at most one host and one guest. Uses the WebSocket Hibernation API, so a room costs
 * nothing between messages.
 */
export class Room {
  constructor(ctx) {
    this.ctx = ctx;
  }

  async fetch(request) {
    const url = new URL(request.url);
    const role = url.searchParams.get("role");
    const other = role === "host" ? "guest" : "host";
    const fresh = url.searchParams.get("new") === "1";
    const [client, server] = Object.values(new WebSocketPair());

    const mine = this.live(role);
    const theirs = this.live(other);
    let refusal = null;
    if (fresh && role === "host" && (mine.length > 0 || theirs.length > 0)) refusal = [4409, "That code is taken."];
    else if (fresh && role === "guest" && theirs.length === 0) refusal = [4404, "No game with that code is waiting."];
    else if (fresh && role === "guest" && mine.length > 0) refusal = [4409, "That game already has two players."];
    if (refusal) {
      server.accept();
      server.close(refusal[0], refusal[1]);
      return new Response(null, { status: 101, webSocket: client });
    }

    // Coming back: the phone's older connection (if the relay still thinks it's open) is replaced.
    for (const old of mine) {
      old.serializeAttachment({ replaced: true });
      try {
        old.close(4000, "Replaced by a new connection.");
      } catch (_) {
        // already closing
      }
    }
    this.ctx.acceptWebSocket(server, [role]);
    server.serializeAttachment({ replaced: false });
    if (theirs.length > 0) {
      for (const ws of [server, ...theirs]) say(ws, "paired");
    } else {
      say(server, "waiting");
    }
    return new Response(null, { status: 101, webSocket: client });
  }

  /** The open connections for [role] that haven't been replaced. */
  live(role) {
    return this.ctx.getWebSockets(role).filter((ws) => {
      const a = ws.deserializeAttachment();
      return !(a && a.replaced) && ws.readyState === WebSocket.OPEN;
    });
  }

  roleOf(ws) {
    return this.ctx.getTags(ws).find((t) => t === "host" || t === "guest");
  }

  async webSocketMessage(ws, message) {
    if (typeof message !== "string" || message.length > MAX_MESSAGE) return;
    const role = this.roleOf(ws);
    const a = ws.deserializeAttachment();
    if (!role || (a && a.replaced)) return;
    for (const peer of this.live(role === "host" ? "guest" : "host")) {
      try {
        peer.send(message);
      } catch (_) {
        // the peer is going away; its close handler tells the other side
      }
    }
  }

  async webSocketClose(ws, code) {
    this.gone(ws);
    try {
      ws.close(code === 1005 || code === 1006 ? 1000 : code);
    } catch (_) {
      // already closed
    }
  }

  async webSocketError(ws) {
    this.gone(ws);
  }

  gone(ws) {
    const a = ws.deserializeAttachment();
    if (a && a.replaced) return;
    ws.serializeAttachment({ replaced: true });
    const role = this.roleOf(ws);
    if (!role) return;
    for (const peer of this.live(role === "host" ? "guest" : "host")) say(peer, "left");
  }
}

function say(ws, what) {
  try {
    ws.send(JSON.stringify({ relay: what }));
  } catch (_) {
    // closing
  }
}
