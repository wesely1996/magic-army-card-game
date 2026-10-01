# Changelog

Versions are tagged `v<version>` on GitHub, and each one has a release with a signed APK.
To cut a release: Actions tab → **Android CI/CD** → **Run workflow**, enter the version (e.g. `0.3.0`).

## 0.5.0 — More Champions, resume, painless updates

- **A second Champion for every race:** Midnight Fang, Quakeback Bear, Storm Griffin, Stone-Eyed Gorgon,
  Sunfire Chimera and Warren Matriarch.
- **Resume battles:** the battle is saved after every move. Leave it (or close the app) and the main menu
  offers **Resume battle**; **Forfeit** ends it.
- **Updates install over the old version**: all builds share one signing key and version codes always
  increase (0.5.0 → 500).
- **36 new cards:** 3 Magic and 3 Equipment for every race.
- **Starter decks can be viewed** read-only (View), or copied to edit. Your own decks open for editing with a
  tap; decks saved before a card was renamed or removed no longer fail to open.
- BALANCE_PLACEHOLDER

## 0.4.0 — Champions, star ranks and field Strategies

- **Champions** (★★★): one mighty unit per race — Ironjaw Packlord, Ancient Cave Bear, Thunderbird,
  Great Hydra, Sunmane Paragon, Blightspawn Colossus. They take 3 unit slots, 1 copy per deck.
- **Star tiers**: units are ★ normal, ★★ Elite or ★★★ Champion; Magic cards are ranked ★ to ★★★. Copies
  per deck: 3 / 2 / 1. The ★★★ spells got stronger (Savage Bite 4, Bear Hug stuns 2 turns, Sky Strike 3,
  Sunfire 3, Blightfire 4).
- **Strategy cards are fields**: one field on the battlefield, lasting until any Strategy card replaces it.
  They are exhausted after use, at most 3 per deck, and several fields got stronger.
- **Decks are 40 cards plus the King.** Starter decks rebuilt (each now has its race's Champion).
- **Card frames by type**: brown studded units (gold for Kings), violet starred Magic, teal Strategy with a
  pennant, steel riveted Equipment.
- **Rebalanced** (target 44–55%): races 48.9–53.1%, Kings 46.0–54.3%, starter decks 46.4–52.8%. Endless
  Horde gives 22 slots; Royal Pride no longer weakens other units; War Drums adds +1 movement; several King
  health values tuned. Warren Horde is now a Vermin + Serpent deck. See [docs/BALANCING.md](docs/BALANCING.md).

## 0.3.0 — The Vermin Horde, racial traits and new card rules

- **New race: Vermin Horde** (inspired by mutant rat-folk). Hidden units, Backstab, Brood (a Swarm Rat
  pops out on each of the next 2 turns), units that arrive with 2–3 rats, Warren Tunnels, Creeping Plague,
  Rat Run and the Kings **Rat King** and **Blight Seer**. Starter deck: **Warren Horde**.
- **Racial traits**: the race of your King gives your whole army a bonus (some with a drawback) — Pack
  Tactics, Thick Fur, Eagle Eyes, Venom Blood, Royal Pride, Endless Horde.
- **Kings** take no damage from Magic cards or abilities (only attacks and Exhaustion hurt them) and are
  **Immovable**. Royal Exchange became **Rally to the King**.
- **Unit slots**: 16 per side (24 for the Endless Horde). **Elite** units (★) take 2 slots, other units 1,
  Kings none.
- **Used cards**: unit cards, equipment and damage/summoning spells are **exhausted** (used once). Strategy
  and other Magic cards go to the **discard pile**, which becomes the new deck when the deck runs out.
- **5 new units** for each of the five original races, all with new watercolor art.
- New keywords: Hidden, Backstab, Brood, Immovable; arrival effects.
- APKs are named `KingOfTheBeasts-<version>.apk`.
- **Rebalanced** for the new target of 44–55% for every race and every King: races 49.7–54.0%,
  Kings 46.4–53.4%, starter decks 44.9–54.9%. Racial traits, King health and many cards were tuned; see
  [docs/BALANCING.md](docs/BALANCING.md).

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
