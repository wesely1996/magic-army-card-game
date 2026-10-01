# How the game is balanced

Target set by the product owner: **every race and every King wins between 44% and 55% of its games.**

### v0.3.0 (6 races, racial traits, King immunity, unit slots, exhausted/discarded cards)

| | First simulation after the v0.3 changes | After the balance pass |
|---|---|---|
| Race win rates | 44.7% – 67.1% | **49.7% – 54.0%** |
| King win rates | 35.7% – 77.5% | **46.4% – 53.4%** |
| Draws | 0% | **0%** |
| First player wins | 51.6% | 54.0% |
| Starter decks (overall) | 13.3% – 81.1% | **STARTERS** |

### v0.2.0 (5 races)

| | Before balancing | After |
|---|---|---|
| Race win rates | 45.3% – 61.2% | **48.7% – 53.6%** |
| King win rates | 31.2% – 72.0% | **45.9% – 51.9%** |
| Draws (games hitting the 200-turn limit) | 7.5% | **0%** |
| First player wins | 51.7% | 50.9% |
| Starter decks (overall) | 40.0% – 61.5% | **48.7% – 52.1%** |

Full, regenerable numbers are in [BALANCE.md](BALANCE.md).

## Method

Everything is measured by simulation. The AI plays itself thousands of times with decks generated for
every race combination (1–3 races, random King, random legal 40-card list). Then the results are
analysed statistically.

1. **Win rates.** Per race, King, race combination, card and starter-deck matchup, with 95% confidence
   margins (`./gradlew :core:balanceReport -Pgames=4000`).
2. **Strength model.** A logistic (Bradley–Terry) regression of each result on deck features,
   `P(A beats B) = σ(Σ β·(x_A − x_B) + θ·first)`, fitted by regularised gradient descent. Unlike raw
   win rates, this separates effects that always appear together, such as a strong King inflating its
   race's numbers. Strengths are reported in Elo (β·400/ln 10).
3. **Measured prices of stats.** To learn what a change is worth, the same games (same seeds, same
   decks) are replayed with one change applied, for example +3 HP on every unit of one race. The
   difference in the fitted strength gives the derivative dElo/d(change). Paired games cancel most of
   the noise; per-race estimates are shrunk towards their mean. Results:
   * +1 HP on each of a race's units ≈ **+12 Elo** (8–18 by race)
   * +1 ATK on each Wolf unit ≈ **+52 Elo**
   * −1 ATK on Hawk's heavy hitters ≈ **−18 Elo**
   * +1 HP on a King ≈ **+24 Elo**
4. **Solve.** With a linear model `s + J·x`, where `s` is the race strengths and `J` holds the measured
   derivatives, the lever amounts `x` come from ridge least squares:
   `(JᵀJ + λI)·x = −Jᵀs`. This finds the smallest total change that brings every race to the same
   strength (`./gradlew :core:balanceSolve`, `:core:balanceExperiments`). King HP uses the same idea
   with one measured price (`:core:balanceKings`). Solutions are rounded to whole numbers and verified
   with a fresh simulation.
5. **Starter decks.** A round robin between the three starter decks, played from both seats. Each deck
   change is treated as a lever and sized from its measured effect.

Caveat: all numbers come from AI-vs-AI games (a steady greedy AI), so they measure how cards perform
for that player. Human play will differ somewhat; the tools make it cheap to re-check after changes.

## What the analysis found

* **Range and flight dominated.** Hawk (ranged flyers) and Serpent (ranged poison) attacked from
  safety while the melee races (Wolf, Lion) had to walk into fire. Hawk's gap was worth about
  30 HP-equivalents, too much to fix with health alone. It was fixed with attack and health cuts on
  its units instead.
* **A rules bug made temporary buffs useless.** "+X for 1 turn" effects expired at the end of their
  owner's turn. Played on your own turn (which uses your one action), they vanished before the unit
  could use them. They now last through your next turn.
* **Stalemates.** Summoning and regenerating Kings produced 7–19% draws. Call the Pack became a
  capped passive, and **Exhaustion** (from turn 120 Kings lose health each turn and can't heal)
  guarantees an ending.
* **Strategy cards were the weakest card type.** Each now lasts one turn longer. In the starter decks,
  swapping Strategy cards for units was the strongest single lever.
* **Removal spells (Savage Bite, Sunfire, Sky Strike) are the most powerful cards in a deck.** Moving
  two or three copies between starter decks swings a matchup by 20+ points.

## v0.3.0 balance pass

Four rounds of 4000 simulated games each, plus starter-deck round robins.

* **What changed the picture.** Kings now only die to attacks, so a King's *effective health* (its health
  plus its racial trait) decides most of its strength. The first simulation had the Bear Kings at 75–77%
  (Thick Fur's +2 health on every unit) and the Pride King at 63% (Royal Pride's +3 King health), while the
  Storm Eagle (5 effective health, and Tempest can no longer hit Kings) fell to 36%.
* **Recycling favours defence.** Damage spells are exhausted, but heals, shields and Strategy cards come back
  through the discard pile. Bear, the defensive race, gained the most from that, so its recyclable defence
  was trimmed.
* **Racial traits** were toned down to: Pack Tactics (Pack Hunter and +1 movement), Thick Fur (+1 health,
  −1 movement for fast units), Eagle Eyes (+1 range for ranged units), Venom Blood (Poisonous, −1 attack
  only for units with 3+ attack), Royal Pride (King +1 health and +1 attack, others −1 health), Endless Horde
  (24 unit slots).
* **Card changes:** Bear units −2 health in total (minimum 2), Thick Hide shield 3 → 2, Hibernate heal 4 → 3,
  Den Fortress and Salmon Run last 3 turns, Bark Armor +2 health, Spirit Bear's ward cooldown 3; Wolf units
  +1 health and +1 attack; Serpent units +1 health and +1 attack for 1-attack units; Crow Trickster's
  counter cooldown 6; Grey Heron and Vulture −1 health; Rat King's Call the Mischief cooldown 2.
* **King health** (base, before traits): Alpha Wolf 11, Moon Howler 8, Elder Bear 7, Cave Warden 7,
  Sky Sovereign 10, Storm Eagle 8, Naga Queen 11, Basilisk 10, Pride King 10, Lioness Queen 8, Rat King 13,
  Blight Seer 8.
* **Starter decks:** Pack & Pride is led by the Alpha Wolf, Venom & Wings by the Naga Queen with fewer damage
  spells, Mountain Clans swapped Bear Cubs for Kodiaks, Warren Horde is led by the Blight Seer with more
  Shadow Blades, Night Skulkers and Mutant Brutes.

## v0.2.0 rule changes



* "+X for 1 turn" effects applied during the unit owner's own turn last through their next turn.
* Exhaustion: from turn 120, each King loses 1 health (+1 every 20 turns) at the start of its owner's
  turn and can't be healed.
* Tempest (Storm Eagle) only reaches enemies within 3 squares.
* Commander (Pride King) reaches allies within 3 squares (was 2).
* Call the Pack (Alpha Wolf) is a passive: a Wolf Pup token at the start of each of your turns, at most 2.

## v0.2.0 card changes

| Card | Before | After |
|---|---|---|
| **Wolf** | | |
| Alpha Wolf (King) | 3/8, active summon | 4/9, passive Call the Pack |
| Moon Howler (King) | 2/8 | 2/5 |
| Wolf Pup / Scout / Grey Hunter | 1/3, 2/3, 3/4 | 3/3, 3/4, 4/5 |
| Shadow Stalker / Dire Wolf / Wolf Shaman | 3/3, 4/6, 1/4 | 4/4, 5/7, 2/4 |
| The Hunt, Moonlit Hunt | 3 turns | 4 turns |
| **Bear** | | |
| Elder Bear (King) | 3/10 | 3/9 |
| Bear Cub, Brown Brawler, Grizzly, Polar Bear, Panda Monk, Honey Gatherer | — | −1 HP each |
| Sticky Honey (Honey Gatherer) | cooldown 3 | cooldown 4 |
| Den Fortress, Salmon Run | 3, 4 turns | 4, 5 turns |
| **Hawk** | | |
| Sky Sovereign (King) | 2/7 | 2/8 |
| Storm Eagle (King) | 3/7, Lightning cd 3 | 2/6, Lightning cd 4 |
| Falcon | 2/3, move 3 | 1/2, move 2 |
| Night Owl | 2/4 | 1/2 |
| War Eagle | 3/4 | 2/3 |
| Crow Trickster | 1/3, Mimic Caw cd 4 | 1/2, cd 5 |
| Condor | 3/6 | 2/3 |
| High Ground, Favorable Winds | 3 turns | 4 turns |
| **Serpent** | | |
| Naga Queen (King) | 2/8, Enthrall cd 4 | 2/9, Enthrall cd 3 |
| Basilisk (King) | Petrify + Armored | Petrify |
| Spitting Cobra | 2 ATK | 1 ATK |
| Venom Spit (Snake Charmer) | cooldown 2 | cooldown 3 |
| Venom Surge | 2 dmg × 3 turns | 2 dmg × 2 turns |
| Murky Swamp, Hypnotic Trance | 3, 2 turns | 4, 3 turns |
| **Lion** | | |
| Pride King (King) | 4/9 | 5/13 |
| Lioness Queen (King) | 3/8 | 4/8, Armored |
| Lion Cub | 2/3 | 2/5, Pack Hunter |
| Lioness Hunter / Royal Guard / Pride Sage | 3/4, 2/6, 1/4 | 3/6, 2/7, 1/5 |
| Maned Warlord | 4/6 | 4/8, Retaliate |
| Pride Runner | 2/3 | 3/4 |
| War Banner, Tall Grass | 3 turns | 4 turns |

## Displacement spells

Each race got one Magic card that moves or swaps units: Pack Relay (Wolf), Mighty Shove (Bear), Gale Force
(Hawk), Mirage (Serpent) and Royal Exchange (Lion). Races stayed between 49% and 53%. The pushes hurt the
Pride King most, because his Commander aura depends on standing next to his army: he fell to 40%. The King
solver (20 Elo per point of King health) suggested +2 HP for him and −2 for the Lioness Queen and Alpha
Wolf; he got +2, and the other two −1 each (half steps, as full steps overshot before). After that the
Kings sit between 45.9% and 51.9% (Pride King back to 45.9%) and races between 48.7% and 53.6%.

## v0.2.0 starter decks

* **Pack & Pride:** −1 Savage Bite, +1 Lion Cub.
* **Venom & Wings:** led by the Basilisk instead of the Naga Queen. 3 Falcons and 2 Murky Swamps were
  swapped for a 3rd Python, a 3rd Black Mamba, a 2nd Snake Charmer and 2 Adders.
* **Mountain Clans:** 2 Den Fortress swapped for 2 Polar Bears.

## Re-checking balance after changes

```bash
./gradlew :core:balanceReport -Pgames=4000        # full report -> docs/BALANCE.md (~10 min)
./gradlew :core:balanceReport -Pgames=0           # starter decks only (~3 min)
./gradlew :core:balanceSolve -Pgames=2500         # per-race HP solve (~45 min, resumable)
./gradlew :core:balanceKings -Pgames=3000         # King HP price and suggestions
```
