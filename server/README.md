# Online relay server

Internet play goes through this tiny server. Both phones connect to it with the same room code and it
passes their messages back and forth. It runs no game logic and stores nothing: each phone runs the rules
engine itself, and the two apps check each other after every move, exactly as on Wi-Fi.

It runs on **Cloudflare Workers with a Durable Object per room**, which the free plan covers with lots of
room to spare for a group of friends: 100,000 requests a day, and a room costs almost nothing between
messages because it uses WebSocket hibernation. No credit card is needed.

## Protocol

| | |
|---|---|
| `GET /` | `ok` — health check |
| `GET /room/<CODE>?role=host\|guest[&new=1]` | WebSocket into a room (codes are 4–8 of `A–Z`, `2–9`) |

`new=1` marks a first visit: a host's code must be free (else closed with 4409), and a guest's code must
have a host waiting (else 4404); a third player gets 4409. Without it the phone is coming back (after a
dropped connection or a restart) and replaces its own older connection (closed with 4000).

The relay adds three messages of its own: `{"relay":"waiting"}`, `{"relay":"paired"}` and
`{"relay":"left"}`. Everything else is passed through unchanged (text only, up to 64 KB).

## Running and testing locally

```bash
cd server
npm ci
npm test              # starts `wrangler dev` and plays two phones against it
npx wrangler dev      # http://127.0.0.1:8787
```

The app's own tests can also play a whole battle through it:
`RELAY_URL=http://127.0.0.1:8787 ./gradlew :app:testDebugUnitTest --tests '*RelayTest*'`.

## Deploying (one-time setup)

1. Create a free account at <https://dash.cloudflare.com/sign-up>.
2. In the dashboard open **Workers & Pages** once, so the account gets its `workers.dev` subdomain
   (you can pick its name there).
3. Copy your **Account ID** (shown on the Workers & Pages overview, or under any domain's overview).
4. Create an API token: **My Profile → API Tokens → Create Token → "Edit Cloudflare Workers" template →
   Continue → Create Token**, and copy it.
5. In GitHub: **Settings → Secrets and variables → Actions → New repository secret**, add
   `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID`.
6. Run the **Online server** workflow (Actions tab → Online server → Run workflow). It tests and deploys;
   the log shows the address, e.g. `https://kotb-relay.<your-subdomain>.workers.dev`.

Every later change under `server/` redeploys by itself. The app has the address built in
(`Relay.DEFAULT_URL` in `app/.../net/Relay.kt`); Settings → Online → Game server can point it elsewhere.
