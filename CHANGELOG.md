# Changelog

Versions are tagged `v<version>` on GitHub, and each one has a release with a signed APK.
To cut a release: Actions tab → **Android CI/CD** → **Run workflow**, enter the version (e.g. `0.3.0`).

## 0.2.0 — Landscape, balance and displacement spells

- **Balance pass**, measured with AI-vs-AI simulation and solved mathematically: every race between 48.7%
  and 53.6%, every King between 45.9% and 51.9%, no draws. See [docs/BALANCING.md](docs/BALANCING.md).
- New rules from balancing: "for 1 turn" boosts played on your own turn last through your next turn;
  Exhaustion from turn 120; Tempest range 3; Commander range 3; Call the Pack became a capped passive.
- **Landscape layout.** The board fills the screen height.
- **Hand in front, then aside.** The hand fans out in front of the board when you choose a card and
  moves to a strip at the side while you pick a square; it can be hidden or shown at any time.
- **Rotatable board.** ⟲/⟳ buttons and a two-finger twist.
- **Right-hand drawer.** A slim rail with the action queue; pulled out, it explains what is going on.
- **Board squares in descriptions** (e.g. "Dire Wolf (G4) attacks Bear Cub (G5)").
- **Displacement spells**, one per race: Pack Relay, Mighty Shove, Gale Force, Mirage, Royal Exchange.
- Landscape layouts for the menu, battle setup, deck builder and card inspector.

## 0.1.0 — Playable MVP

- Rules engine: 8×8 board, deployment in the first 3 rows, one action per turn, interrupt chain.
- 5 races, 2 Kings each, unit/magic/strategy/equipment cards, deck builder with saved decks.
- Easy and Medium (3-ply search) AI. Long press to inspect any card or unit.
- 2.5D watercolor board, hand-drawn UI, CI/CD with GitHub Actions.
