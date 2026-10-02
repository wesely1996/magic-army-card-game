# Changelog

Versions are tagged `v<version>` on GitHub, and each one has a release with a signed APK.
To cut a release: Actions tab → **Android CI/CD** → **Run workflow**, enter the version (e.g. `0.3.0`).

## 0.12.0 — Faster battles, structures, evolution and Quick spells

**Rules**
- **Kings and Champions can move and then attack in the same turn** (melee only). Every other unit, and every
  ranged unit, still moves *or* attacks.
- **Kings are protected rule engines:** much tougher (20–45 health) but mostly weak fighters (2 attack) whose
  value is their abilities; only the Pride King and Alpha Wolf hit hard. Ranged Kings got a little extra
  health since they can't charge.
- **6 starting units** per side instead of 5, so there's a wall of bodies around each King.
- **Hand limit 10** (was 8).
- **Quick spells** (marked QUICK): a little weaker, but playing one on your turn doesn't use up your action;
  they also work as interrupts. Interrupt abilities are now called ⚡ interrupt abilities.
- **Online turn timer:** 20 seconds per decision in online battles; when it runs out you pass (or a unit is
  placed for you). No timer against the computer.
- Battles now last about 40 turns on average (they were about 87).

**68 new cards (272 in all)**
- **5 new spells per race**, each race in its own style: Wolf buffs and quick tricks, Bear protection, Hawk
  direct damage (Static Spark, Strafing Run, Chain Lightning), Serpent status effects (Blinding Spit, Venom Wave),
  Lion buffs and healing (Battle Cry, Inspire, Mane of Light), Vermin dirty tricks (Rat Bite, Warp Storm).
  Every race has a **draw-2 spell** and a spell that **ends the Strategy field** on the battlefield.
- **Evolving units (2 per race):** they turn into a stronger form after surviving some turns or defeating
  enemies, some twice — e.g. Wolf Whelp → Young Wolf → Pack Leader, Hawk Hatchling → Fledgling Hawk → Sky Raptor,
  Sewer Runt → Plague Rat → Rat Ogre. Evolving heals fully and keeps equipment. 18 evolved forms.
- **Structures (1–2 per race):** never move or attack by themselves. Sentry towers fire at the weakest enemy in
  range each turn (Hunters' Watchtower, Aerie Tower, Serpent Idol, Warpstone Spire); Taunt walls force adjacent
  enemies to attack them (Stone Cairn, War Monument); Mending Aura totems heal adjacent allies (Healing Totem,
  Sun Shrine).

**Balance**
- Champions were trimmed now that they can charge (e.g. Sunmane Paragon 5/9, Fenrir 4/9 without Retaliate,
  Ironjaw Packlord 4/10); Sky Sovereign's swap now reaches 3 squares with a cooldown of 3.
- Starter decks re-tuned: each now carries Champions of similar weight.
- BALANCE_PLACEHOLDER

## 0.11.2 — Top-down 2D board

- **Board view option:** a flat **top-down 2D** board with round unit tokens (art, team ring, attack and health),
  next to the 2.5D perspective view. Switch with the **2D / 2.5D** button at the top left of the battle screen
  or under Settings → Board view; the choice is remembered. Turning the board, highlights, taps and all
  animations work in both views.
- The battle log now says "Your hand is full" instead of "You's hand is full".

## 0.11.1 — Collapsible tutorial notes

- The tutorial's instruction note has a **▲ Hide** button that folds it into a small tab
  ("🎓 6/12 · Move ▼ Show") at the top of the board, so you can see the whole board while you follow the
  instruction. It also folds by itself when you pick a card or unit, and opens again for each new lesson.

## 0.11.0 — 30 new cards, gentler Beginner and Pro

- **30 new cards, 5 per race, each race with its own focus:**
  - **Wolf Pack — units:** a third Champion, **Fenrir the Devourer** (6/10, Bloodthirst, Retaliate), and three
    Elites: **Rimefang Alpha** (Frost Bite stuns), **Warg Rider** (4 movement) and **Howling Elder** (War Howl
    boosts allies within 2); plus **Moonlit Pursuit**.
  - **Bear Clan — equipment:** Horned Helm, Stone Gauntlets, Bearhide Plate, Clan Standard (Guardian) and the
    spell Tremor.
  - **Hawk Aerie — magic:** Lightning Jolt, Wind Shear, Keen Sight (draw 2), Rain of Feathers and the Windrider
    Harness.
  - **Serpent Coil — magic:** Paralytic Bite, Acid Spit, Molting, Serpent's Patience and the field Hypnotic Haze.
  - **Lion Pride — Strategy:** three new fields — Royal Decree, Golden Dawn, Pride Formation — plus Roar of the
    Pride and the Sun Scepter (Commander).
  - **Vermin Horde — a mix:** Tail Blade, Glowshard Charm, Plague Bomb, Gorge and the field Rat Tide.
- **Five new fields:** Royal Decree (your King +2 attack; units next to it take 1 less damage), Golden Dawn (draw
  an extra card each turn), Pride Formation (+1 attack next to an ally), Hypnotic Haze (enemy ranged units −1
  range and −1 attack), Rat Tide (a Swarm Rat appears next to your King each turn).
- **Pro** now thinks 2 moves ahead instead of 3 (its move and your best reply) and thinks one move past an
  interrupt before answering. **Beginner** takes its best-looking move only half the time and otherwise one of
  its next four. Pro still beats Beginner in 27 of 32 test games.
- **Balance:** every race wins 46.7–51.5%, every King 45.9–54.4% and every starter deck 45.4–53.3% with the
  new cards in the pool (target 44–55%); no changes were needed.

## 0.10.0 — Master AI

- **New difficulty, Master:** searches as far ahead as its thinking time allows (about 1.5 seconds per move),
  deepening one move at a time and checking the most promising lines first, so it plays as strong as the
  phone allows. It also looks two actions past an interrupt before deciding whether to answer.
- In AI-vs-AI testing Master beat Pro in 30 of 48 games (62.5%).
- **Difficulties renamed:** Easy is now **Beginner**, Medium is **Pro**, and the new Hard level is **Master**.
  Saved battles keep their difficulty.

## 0.9.0 — Rejoin dropped online games

- **Dropped connections no longer end online battles.** The battle shows "Reconnecting…" and keeps going
  once the phones find each other again: the host reopens the game on the Wi-Fi and the guest keeps trying
  the host's last address, or finds the game again by name if the address changed.
- **Nothing is lost:** moves made while disconnected are sent as soon as the connection is back, and each
  side resends whatever the other missed. Checksums still confirm both copies match.
- **Leave for now / Rejoin:** online battles are saved after every move. Leave for now (or closing the app)
  keeps the battle; under **With friends** both players tap **Rejoin** to carry on. **Abandon** discards it,
  and **Forfeit** still hands your friend the win.
- The Play menu reminds you of an unfinished battle with a friend.
- Network protocol version 2: 0.9.0 can't play online against 0.7.0 or 0.8.0 — update both phones.

## 0.8.0 — Tutorial battle

- **Tutorial battle:** a guided first battle as the Pride King against a scripted rival Moon Howler.
  Twelve short lessons teach deploying your King and army, inspecting cards, moving, answering the
  rival's attack with a Magic card, the action queue, playing Magic, attacking, Strategy fields, and
  finally defeating the enemy King. Each lesson lights up only the move it teaches.
- Offered once on first launch, and always available under **Play → Tutorial** and in **How to Play**.
- The coach note can be folded away with a tap; buttons the lesson doesn't allow are hidden.

## 0.7.0 — Play with friends (same Wi-Fi)

- **With friends:** peer-to-peer online battles between two phones on the same Wi-Fi — no server, no
  account. Pick your name and army, then **Host a game** or **Join a game**. Hosted games appear in a
  list (Network Service Discovery); you can also join by the address shown on the host's screen.
- **Lockstep play:** both phones run the same deterministic battle and send only their moves. Every move
  carries a checksum of the whole game, so a mismatch (or a tampered move) stops the battle instead of
  letting it drift. Apps on different versions or with different cards refuse to pair.
- The guest sees the board from their own side; the opponent's name is shown everywhere.
- **Rematch** when both players ask for it; leaving a battle hands your friend the win, and a dropped
  connection is noticed within about 20 seconds.
- The connection layer is separate from the game, so internet play can be added later.

## 0.6.0 — Music, sound, settings and a new menu

- **Background music:** medieval themes for the menus and battles, plus victory and defeat themes, with
  smooth crossfades. Music pauses when the app is in the background.
- **Sound effects** for every action, timed with the animations: sword strikes, arrows, hits, blocks,
  falling units, spells that harm, heal or control, equipment, Strategy fields, cards, moves and your turn.
- **Settings** (⚙ wheel in the top corner of the main menu, or during a battle): music and sound volume,
  animation speed (Slow / Normal / Fast, also paces the AI) and keeping the screen on during battles.
- **New main menu:** Play, Deck Builder and How to Play. **Play** opens Continue, New game, With friends
  (coming in 0.7.0) and Back.
- **Bigger battle drawer:** Details now covers 80% of the screen in two columns with larger text, and the
  full battle log opens nearly full screen.
- **Credits:** in Settings and in CREDITS.md — the creator, every game-icons.net artist behind the card art,
  and the music, sound and font authors.

## 0.5.0 — More Champions, animations, resume, painless updates

- **A second Champion for every race:** Midnight Fang, Quakeback Bear, Storm Griffin, Stone-Eyed Gorgon,
  Sunfire Chimera and Warren Matriarch.
- **Resume battles:** the battle is saved after every move. Leave it (or close the app) and the main menu
  offers **Resume battle**; **Forfeit** ends it.
- **Updates install over the old version**: all builds share one signing key and version codes always
  increase (0.5.0 → 500).
- **36 new cards:** 3 Magic and 3 Equipment for every race.
- **Starter decks can be viewed** read-only (View), or copied to edit. Your own decks open for editing with a
  tap; decks saved before a card was renamed or removed no longer fail to open.
- **Battle animations:** melee lunges, arrows, spell and ability orbs with coloured bursts (orange harms,
  green helps, violet controls), hit shakes and red flashes, fallen units toppling, cards flying in from their
  owner's side, units dropping onto the board, and a board-wide wave for Strategy cards. Events play in order
  (a spell flies, then hits), and the AI paces its moves so each one can be followed.
- **Balance:** every race wins 47.7–51.4%, every King 46.0–53.7% and every starter deck 45.4–53.3%
  (target 44–55%). Wolf Kings, the Pride King, Midnight Fang, Ironjaw Packlord (5/10) and War Harness
  (+1/+1) were trimmed; the Naga Queen, Blight Seer, Hawk Kings, Stone-Eyed Gorgon, Warren Matriarch and the
  weakest Hawk and Vermin equipment were strengthened. See docs/BALANCING.md.

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
