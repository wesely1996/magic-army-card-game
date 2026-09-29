# Magic Army — MVP implementation plan

Status: **draft, awaiting confirmation** of the items marked ⚠.

## 1. Tech stack
- Kotlin + Jetpack Compose (Material 3), native Android.
- 2.5D: perspective-projected 8×8 board drawn on a Compose `Canvas`, upright unit "standees"
  with shadows, painter's-order depth sorting, inverse projection for touch.
- Modules: `core` (pure Kotlin: cards, deck rules, engine, AI — JVM unit tests) and `app` (Android UI).
- GitHub Actions: run tests + build a downloadable debug APK.

## 2. Rules (interpretations flagged ⚠)
- 8×8 board; "own fields" = your first two rows. Coin flip winner deploys first and acts first in battle.
- Deploy: alternate placing one unit, up to 5 each; King must be first; may stop early after the King.
- ⚠ Deploy units are chosen from **all unit cards in the deck** (not a random hand). Afterwards the deck
  is shuffled and each player draws 5.
- Battle: one action per turn — move, attack in range, use ability, play a card, or skip.
- ⚠ Draw 1 card at the start of each turn, hand limit 8, **no card costs** (the action is the cost).
- Movement: up to Move king-steps (8 directions), blocked by units unless Flying.
  Range: Chebyshev distance (range 1 = melee). Attackers do not move into the target square.
- ⚠ Battle-phase unit placement: empty border square, ≥2 squares from every enemy (never adjacent).
- King dies → owner loses; both at once → draw.
- Interrupts: every action goes on a stack; the opponent may respond with a Magic card or a *quick*
  ability, the other player may respond back, etc. On a pass the stack resolves LIFO; actions that are no
  longer legal fizzle (e.g. a stunned unit's attack). Counter cards cancel the action they answer and can
  themselves be countered.
- ⚠ All Magic cards can be used as interrupts; Strategy/Equipment/Unit cards only as your turn action;
  abilities only if marked quick.
- Strategy: field-wide rule changes for N of your turns, one active per player (Blitz, Silence, Ambush,
  Fortify, Swamp, War Drums, …). Equipment: permanent stat/keyword upgrades on a unit.

## 3. Content
- 5 races × 15 cards: Wolf, Bear, Hawk, Serpent, Lion (2 Kings, 6 Units, 3 Magic, 2 Strategy, 2 Equipment each).
- Keywords: Flying, Armored, Retaliate, Poisonous, Pack Hunter, Regenerate; abilities with cooldowns.
- Deck rules: ≤3 races, exactly 40 cards, exactly 1 King, ≤3 copies. 3 starter decks.

## 4. Screens
Main menu → Deck list → Deck builder (name, race picker, filtered pool with type tabs, x/40 counter,
validation, save as JSON) → Game setup → Game (2.5D board, highlights, hand, unit panel, interrupt
banner with Pass, log, damage popups, game-over dialog).

## 5. Opponent
Vs AI for the MVP (local hot-seat later). 1-ply simulation over all legal actions with a heuristic
(material, King HP, next-turn threat map, advancement). Responses: compare pass vs each response,
respond only when clearly better.

## 6. Build order
1. Gradle setup + core model  2. Card DB, starter decks, validator, codec
3. Engine (deploy, move/attack, effects, stack, turn lifecycle, win)  4. AI
5. Tests (rules, counter chains, AI-vs-AI soak)  6. Android UI  7. README, CI, APK build, push.

## 7. Out of scope for MVP
Art/animation beyond simple tweens, sound, online/hot-seat multiplayer, progression, mana, balance.
