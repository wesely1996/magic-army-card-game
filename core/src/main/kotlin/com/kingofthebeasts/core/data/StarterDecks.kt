package com.kingofthebeasts.core.data

import com.kingofthebeasts.core.deck.Deck
import com.kingofthebeasts.core.model.Race

/** Ready-to-play decks so a new player can start a battle immediately. */
object StarterDecks {
    val all: List<Deck> = listOf(
        Deck(
            name = "Pack & Pride",
            races = listOf(Race.WOLF, Race.LION),
            cards = mapOf(
                "w_king_alpha" to 1,
                "w_pup" to 3, "w_scout" to 2, "w_hunter" to 3, "w_direwolf" to 2, "w_shaman" to 1,
                "l_lioness" to 3, "l_guard" to 2, "l_warlord" to 2, "l_cub" to 1,
                "w_frenzy" to 2, "w_bite" to 2, "w_scatter" to 2, "l_charge" to 1, "l_sunfire" to 3,
                "w_hunt" to 2, "l_banner" to 2,
                "w_collar" to 2, "l_mane" to 2, "l_shield" to 2,
            ),
        ),
        Deck(
            name = "Venom & Wings",
            races = listOf(Race.SERPENT, Race.HAWK),
            cards = mapOf(
                "s_king_naga" to 1,
                "s_viper" to 3, "s_cobra" to 3, "s_python" to 3, "s_mamba" to 3, "s_charmer" to 2, "s_adder" to 2,
                "h_owl" to 2, "h_eagle" to 2, "h_crow" to 1,
                "s_hiss" to 2, "s_venom" to 2, "h_gust" to 2, "h_skystrike" to 1, "h_tailwind" to 2, "s_shed" to 1,
                "h_high" to 2,
                "s_fangs" to 2, "h_talons" to 2, "s_scales" to 2,
            ),
        ),
        Deck(
            name = "Mountain Clans",
            races = listOf(Race.BEAR, Race.HAWK, Race.LION),
            cards = mapOf(
                "b_king_elder" to 1,
                "b_cub" to 3, "b_brawler" to 2, "b_grizzly" to 3, "b_honey" to 2,
                "b_polar" to 2, "h_falcon" to 2, "h_condor" to 2, "l_lioness" to 3,
                "b_hide" to 3, "b_hug" to 3, "b_hibernate" to 1, "h_skystrike" to 2, "l_sunfire" to 2, "l_valor" to 1,
                "l_banner" to 2,
                "b_claws" to 3, "b_bark" to 3,
            ),
        ),
        Deck(
            name = "Warren Horde",
            races = listOf(Race.VERMIN),
            cards = mapOf(
                "v_king_seer" to 1,
                "v_rat" to 3, "v_tunnel" to 3, "v_skulker" to 2, "v_blade" to 2, "v_brood" to 2, "v_driver" to 2,
                "v_warren" to 1, "v_brute" to 2, "v_friar" to 2, "v_tinker" to 1, "v_slinger" to 1,
                "v_blightfire" to 3, "v_vanish" to 2, "v_swarm" to 2, "v_ratrun" to 2,
                "v_tunnels" to 2, "v_plague" to 2,
                "v_grafts" to 3, "v_cloak" to 2,
            ),
        ),
    )
}
