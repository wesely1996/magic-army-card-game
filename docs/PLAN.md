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
- Two AI levels: **Easy** (greedy, one step ahead) and **Medium** (3-ply alpha-beta search).

## Rules as implemented
- 8×8 board. Coin-flip winner deploys first and acts first in battle.
- Deploy: alternate placing one unit, up to 5 each, King first, chosen from all unit cards in the deck.
  Then shuffle and draw 5.
- Battle: draw 1 per turn (hand limit 8), then one action: move, attack, ability, play card, or skip.
  Cards have no cost.
- Movement: king-steps in 8 directions, blocked by units unless Flying. Range uses Chebyshev distance.
- Battle-phase units: empty border square, ≥2 squares from every enemy (Ambush strategy relaxes this).
- Interrupts: every action goes on a stack. Magic cards and ⚡ quick abilities can respond, alternating.
  A pass resolves the whole chain last-in-first-out, and illegal actions fizzle. Counter cards cancel the action they answer.
- Strategy: one active per player, lasts N of the owner's turns. Equipment: permanent.
- King death loses. Both at once is a draw. 200-turn limit is a draw.

## Kings
| Race | King | Gimmick |
|---|---|---|
| Wolf | Alpha Wolf | Call the Pack: summons Wolf Pup tokens |
| Wolf | Moon Howler | Bloodthirst: heals and grows on every enemy death |
| Bear | Elder Bear | Unstoppable (no stun, max 3 damage per hit); Earthshaker Roar |
| Bear | Cave Warden | Guardian aura; Regenerate |
| Hawk | Sky Sovereign | Change of Winds: quick swap with any ally |
| Hawk | Storm Eagle | Tempest: random 1 damage each turn; Lightning Strike (quick stun) |
| Serpent | Naga Queen | Enthrall: take control of a weakened enemy |
| Serpent | Basilisk | Petrifying Gaze: its attacks stun |
| Lion | Pride King | Commander aura: +1 attack to nearby allies |
| Lion | Lioness Queen | Pounce: leap next to a distant enemy and attack |

## Next steps
- Online multiplayer. The engine is deterministic and action-based, so a server or peer can
  relay `Action`s. Hidden information (hands, deck order) needs server-side authority.
- Real painted art: drop `art_<id>.webp` files in `app/src/main/res/drawable-nodpi/`.
- Balance pass using AI-vs-AI statistics, sound, richer animations, tutorial.
