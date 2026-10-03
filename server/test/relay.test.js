// Runs the relay locally with `wrangler dev` and plays the parts of two phones against it.
// Run with `npm test` (Node 22+, for the built-in WebSocket).
import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import { spawn } from "node:child_process";

const PORT = 8790 + Math.floor(Math.random() * 100);
const BASE = `http://127.0.0.1:${PORT}`;
let dev;

before(async () => {
  dev = spawn("npx", ["wrangler", "dev", "--port", String(PORT), "--ip", "127.0.0.1"], { stdio: "ignore" });
  for (let i = 0; i < 120; i++) {
    try {
      if ((await fetch(BASE)).ok) return;
    } catch (_) {
      // not up yet
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error("wrangler dev didn't start");
});

after(() => dev.kill());

let codes = 0;
const newCode = () => "T" + Date.now().toString(36).toUpperCase().replace(/[01]/g, "Z").slice(-4) + "ABCDEFGH"[codes++ % 8];

/** A phone: a WebSocket with a queue of received lines and the close event, if any. */
function phone(code, role, fresh) {
  const ws = new WebSocket(`ws://127.0.0.1:${PORT}/room/${code}?role=${role}${fresh ? "&new=1" : ""}`);
  const p = { ws, lines: [], closed: null, waiters: [] };
  const wake = () => p.waiters.splice(0).forEach((w) => w());
  ws.addEventListener("message", (e) => {
    p.lines.push(String(e.data));
    wake();
  });
  ws.addEventListener("close", (e) => {
    p.closed = { code: e.code, reason: e.reason };
    wake();
  });
  p.next = async () => {
    while (p.lines.length === 0) {
      if (p.closed) throw new Error(`closed ${p.closed.code} ${p.closed.reason}`);
      await new Promise((r) => {
        p.waiters.push(r);
        setTimeout(r, 5000);
      });
    }
    return p.lines.shift();
  };
  p.closedWith = async () => {
    for (let i = 0; i < 50 && !p.closed; i++) await new Promise((r) => { p.waiters.push(r); setTimeout(r, 200); });
    return p.closed;
  };
  p.send = (s) => ws.send(s);
  return p;
}

const relay = (what) => JSON.stringify({ relay: what });

test("health check", async () => {
  assert.equal((await (await fetch(BASE)).text()).trim(), "ok");
});

test("host waits, guest joins, messages flow both ways", async () => {
  const code = newCode();
  const host = phone(code, "host", true);
  assert.equal(await host.next(), relay("waiting"));
  const guest = phone(code, "guest", true);
  assert.equal(await guest.next(), relay("paired"));
  assert.equal(await host.next(), relay("paired"));
  host.send('{"t":"hello","name":"A"}');
  guest.send('{"t":"hello","name":"B"}');
  assert.equal(await guest.next(), '{"t":"hello","name":"A"}');
  assert.equal(await host.next(), '{"t":"hello","name":"B"}');
  host.ws.close();
  guest.ws.close();
});

test("a guest can't join a code nobody is hosting", async () => {
  const guest = phone(newCode(), "guest", true);
  const c = await guest.closedWith();
  assert.equal(c.code, 4404);
});

test("a taken code is refused to a second host, a full game to a second guest", async () => {
  const code = newCode();
  const host = phone(code, "host", true);
  await host.next();
  const host2 = phone(code, "host", true);
  assert.equal((await host2.closedWith()).code, 4409);
  const guest = phone(code, "guest", true);
  await guest.next();
  const guest2 = phone(code, "guest", true);
  assert.equal((await guest2.closedWith()).code, 4409);
  host.ws.close();
  guest.ws.close();
});

test("when one side drops the other hears it, and both can come back to the room", async () => {
  const code = newCode();
  const host = phone(code, "host", true);
  await host.next();
  const guest = phone(code, "guest", true);
  await guest.next();
  await host.next();
  guest.ws.close();
  assert.equal(await host.next(), relay("left"));
  // The guest comes back (not a first visit) and the two are paired again.
  const back = phone(code, "guest", false);
  assert.equal(await back.next(), relay("paired"));
  assert.equal(await host.next(), relay("paired"));
  back.send("again");
  assert.equal(await host.next(), "again");
  host.ws.close();
  back.ws.close();
});

test("a phone coming back replaces its stale connection without the other side noticing a drop", async () => {
  const code = newCode();
  const host = phone(code, "host", true);
  await host.next();
  const guest = phone(code, "guest", true);
  await guest.next();
  await host.next();
  const again = phone(code, "guest", false);
  assert.equal(await again.next(), relay("paired"));
  assert.equal(await host.next(), relay("paired"));
  assert.equal((await guest.closedWith()).code, 4000);
  again.send("from the new one");
  assert.equal(await host.next(), "from the new one");
  host.send("to the new one");
  assert.equal(await again.next(), "to the new one");
  // The replaced connection closing must not have told the host its friend left.
  await new Promise((r) => setTimeout(r, 300));
  assert.deepEqual(host.lines, []);
  host.ws.close();
  again.ws.close();
});

test("both sides can rejoin a room after a restart, in either order", async () => {
  const code = newCode();
  const guest = phone(code, "guest", false);
  assert.equal(await guest.next(), relay("waiting"));
  const host = phone(code, "host", false);
  assert.equal(await host.next(), relay("paired"));
  assert.equal(await guest.next(), relay("paired"));
  host.ws.close();
  guest.ws.close();
});

test("bad codes are rejected", async () => {
  assert.equal((await fetch(`${BASE}/room/ab!?role=host`)).status, 400);
  assert.equal((await fetch(`${BASE}/room/ABCDE?role=king`)).status, 400);
  assert.equal((await fetch(`${BASE}/elsewhere`)).status, 404);
});
