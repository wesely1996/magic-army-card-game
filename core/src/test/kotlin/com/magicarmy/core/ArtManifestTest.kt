package com.magicarmy.core

import com.magicarmy.core.data.CardDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every card needs an entry in the art generator's manifest (and so a generated illustration). */
class ArtManifestTest {
    @Test
    fun everyCardHasArt() {
        val manifest = Json.parseToJsonElement(File("../tools/art/art_manifest.json").readText()).jsonObject
        assertEquals(CardDatabase.all.map { it.id }.toSet(), manifest.keys)
    }
}
