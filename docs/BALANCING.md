# How the game is balanced

Target set by the product owner: **no race (clan) above a 55% win rate.**

| | Before balancing | After |
|---|---|---|
| Race win rates | 45.3% – 61.2% | **51.2% – 54.6%** |
| King win rates | 31.2% – 72.0% | **46.5% – 53.2%** |
| Draws (games hitting the 200-turn limit) | 7.5% | **0%** |
| First player wins | 51.7% | 52.8% |
| Starter decks (overall) | 40.0% – 61.5% | **46.6% – 53.8%** |

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

## Rule changes

* "+X for 1 turn" effects applied during the unit owner's own turn last through their next turn.
* Exhaustion: from turn 120, each King loses 1 health (+1 every 20 turns) at the start of its owner's
  turn and can't be healed.
* Tempest (Storm Eagle) only reaches enemies within 3 squares.
* Commander (Pride King) reaches allies within 3 squares (was 2).
* Call the Pack (Alpha Wolf) is a passive: a Wolf Pup token at the start of each of your turns, at most 2.

## Card changes

| Card | Before | After |
|---|---|---|
| **Wolf** | | |
| Alpha Wolf (King) | 3/8, active summon | 4/10, passive Call the Pack |
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
| Pride King (King) | 4/9 | 5/11 |
| Lioness Queen (King) | 3/8 | 4/9, Armored |
| Lion Cub | 2/3 | 2/5, Pack Hunter |
| Lioness Hunter / Royal Guard / Pride Sage | 3/4, 2/6, 1/4 | 3/6, 2/7, 1/5 |
| Maned Warlord | 4/6 | 4/8, Retaliate |
| Pride Runner | 2/3 | 3/4 |
| War Banner, Tall Grass | 3 turns | 4 turns |

## Starter decks

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
