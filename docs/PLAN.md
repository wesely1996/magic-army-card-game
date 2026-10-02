# King of the Beasts — MVP plan and decisions

Status: **MVP implemented.** This file records the agreed rules and the design decisions behind them.

## Decisions confirmed by the product owner
- Look: modern hand-drawn style with watercolor coloring.
- Deploy zone: each player's **first 3 rows**.
- Cards: full-art background, rules text in a box at the bottom, base stats (ATK, HP, MOV, **RNG**)
  in separate fields.
- Every race has **2 Kings** with a signature gimmick.
- No hot-seat two-player mode. Online play with friends comes next.
- CI/CD runs on GitHub Actions.
- The game is called **King of the Beasts** (app id `com.kingofthebeasts.app`).
- Press-and-hold opens any card (or a unit on the board) large, with every rule written out.
- Three AI levels: **Beginner** (one step ahead, picks among its top five), **Pro** (2-ply search) and **Master**
  (iterative deepening within a time budget).
- Landscape only, for a bigger board. The hand comes to the front when you choose a card and moves to a
  strip at the side when you choose where to play it; it can be hidden and shown by hand. The board can be
  turned (90° buttons or a two-finger twist) to look at it from any side.
- The right side is a drawer: collapsed it shows only the action queue, whose move it is and the needed buttons;
  expanded it explains the situation, every queued action, both armies, battlefield rules and recent events.

## Rules as implemented
- 8×8 board. Coin-flip winner deploys first and acts first in battle.
- Deploy: alternate placing one unit, up to 6 each, King first, chosen from all unit cards in the deck.
  Then shuffle and draw 5.
- Battle: draw 1 per turn (hand limit 10), then one action: move, attack, ability, play card, or skip. Kings and
  Champions (melee) may attack right after moving; every other unit moves or attacks. Quick spells don't
  use the action. Online battles give each decision 20 seconds.
  Cards have no cost.
- Movement: king-steps in 8 directions, blocked by units unless Flying. Range uses Chebyshev distance.
- Battle-phase units: empty border square, ≥2 squares from every enemy (Ambush strategy relaxes this).
- Interrupts: every action goes on a stack. Magic cards and ⚡ quick abilities can respond, alternating.
  A pass resolves the whole chain last-in-first-out, and illegal actions fizzle. Counter cards cancel the action they answer.
- Strategy cards are fields (see below). Equipment: permanent.
- "+X for 1 turn" effects applied during the unit owner's own turn last through their next turn.
- Exhaustion: from turn 120 each King loses 1 health (+1 every 20 turns) at the start of its owner's turn and can't be healed.
- Decks: 40 cards plus the King. Stars set the copy limit: ★ 3, ★★ 2, ★★★ 1. At most 3 Strategy cards.
- Unit tiers: ★ normal (1 slot), ★★ Elite (2 slots), ★★★ Champion (3 slots). Magic ranked ★ to ★★★.
- Strategy cards are fields: one on the battlefield at a time, until any Strategy card replaces it; exhausted after use.
- Unit slots: 16 per side (22 with the Endless Horde trait). Kings take none.
- Kings take no damage from Magic cards or abilities and are Immovable (can't be pushed, swapped or replaced).
- Racial trait: the race of the deck's King gives the army a bonus (sometimes with a drawback).
- Used cards: units, equipment, Strategy cards, damage and summoning spells are exhausted (out of the game); other
  Magic cards go to the discard pile, which becomes the new deck when the deck runs out.
- King death loses. Both at once is a draw. 200-turn limit is a draw (practically unreachable with Exhaustion).

## Kings
| Race | King | Gimmick |
|---|---|---|
| Wolf | Alpha Wolf | Call the Pack (passive): a Wolf Pup token each turn, at most 2 |
| Wolf | Moon Howler | Bloodthirst: heals and grows on every enemy death |
| Bear | Elder Bear | Unstoppable (no stun, max 3 damage per hit); Earthshaker Roar |
| Bear | Cave Warden | Guardian aura; Regenerate |
| Hawk | Sky Sovereign | Change of Winds: quick swap with any ally |
| Hawk | Storm Eagle | Tempest: 1 damage to a random enemy within 3 each turn; Lightning Strike (quick stun) |
| Serpent | Naga Queen | Enthrall: take control of a weakened enemy |
| Serpent | Basilisk | Petrifying Gaze: its attacks stun |
| Lion | Pride King | Commander aura: +1 attack to allies within 3 |
| Lion | Lioness Queen | Pounce: leap next to a distant enemy and attack |
| Vermin | Rat King | Call the Mischief: two Swarm Rats appear next to him |
| Vermin | Blight Seer | Hidden; Blight Bolt damages the target and everything next to it |

## Roadmap to 1.0
Agreed with the product owner. Every version also gets a **balance pass**: AI-vs-AI simulations keep every
race, King and starter deck between 44% and 55% wins, and new cards or rules are re-checked before release.

1. **0.8.0 — Tutorial battle.** *(done)* A guided first battle against a scripted opponent that teaches deployment,
   moving, attacking, playing Magic, answering an interrupt, Strategy fields, inspecting cards and the
   battle drawer, ending with defeating the enemy King. Offered on first launch and from How to Play.
2. **0.9.0 — Rejoin dropped online games.** *(done)* If the Wi-Fi connection drops, either player can reconnect and
   both phones catch up from the shared move list instead of the battle ending.
3. **0.10.0 — Master AI (Hard).** *(done; levels renamed Beginner / Pro / Master)* Deeper search and smarter interrupts, for players who have outgrown Medium.
4. **0.11.0 — 30 new cards and gentler AI levels.** *(done)* Five new cards per race with a race focus
   (Wolf units, Bear equipment, Hawk and Serpent magic, Lion Strategy, Vermin a mix), five new fields, Pro down
   to 2 moves ahead, Beginner picking among its top five moves.
5. **0.12.0 — Online play over the internet.** An internet `Link` (e.g. a direct WebRTC connection set up
   with invite codes) next to same-Wi-Fi play. Peer-to-peer play shares hidden information (hands, deck order)
   between the two apps; truly hiding it would need a trusted server.
6. **1.0.0 — Play Store readiness (final step).** A private upload/signing key kept out of the repository, an
   Android App Bundle build, store listing text and screenshots, a privacy policy (the app uses the network),
   content rating, and a last full balance and device test pass.

Ongoing, any time: custom painted card art (drop `art_<id>.webp` files in
`app/src/main/res/drawable-nodpi/`), richer animations, accessibility (text size, colour-blind-friendly
team colours, screen reader labels).

Done: landscape 2.5D board and drawer (0.2), Vermin and racial traits (0.3), Champions and fields (0.4),
animations and resume (0.5), music, sound and settings (0.6), same-Wi-Fi online play (0.7), tutorial battle (0.8), rejoining dropped online games (0.9), Master AI (0.10).
