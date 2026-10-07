package org.ole.planet.myplanet.ui.voices

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import org.ole.planet.myplanet.utils.GoldenFixtures.TRICKY
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesGolden
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesRawGolden

/** Pins the image entries a post is saved with, and how the voices screens read them back. */
class VoicesImagesGoldenTest {
    private val entries = listOf(
        VoicesImages.entry("/storage/emulated/0/DCIM/a.jpg", "a.jpg"),
        VoicesImages.entry("/p/it's=<x>&.png", "it's=<x>&.png"),
        VoicesImages.entry(TRICKY, TRICKY),
        VoicesImages.entry("", ""),
    )

    @Test
    fun `entries keep their stored bytes`() {
        assertMatchesRawGolden("ui/voices/image-entries", entries.joinToString("\n"))
    }

    @Test
    fun `imageUrl reads entries as the voices screens do`() {
        val inputs = entries + listOf(
            """{"imageUrl":1.50}""",
            """{"imageUrl":true}""",
            """{"imageUrl":null}""",
            """{"imageUrl":["/a.png"]}""",
            """{"fileName":"a.png"}""",
            """{'imageUrl':'/single.png'}""",
            """{imageUrl:/unquoted.png}""",
            "",
            "   ",
            "null",
            "[1]",
            "x",
            "{",
        )
        val rows = JsonArray(
            inputs.map { input ->
                buildJsonObject {
                    put("entry", input)
                    put("imageUrl", outcome { VoicesImages.imageUrl(input) })
                }
            }
        )
        assertMatchesGolden("ui/voices/image-urls", rows)
    }

    /** A thrown exception as `throws`: the screens catch any exception here, whatever its type. */
    private fun outcome(block: () -> String): JsonPrimitive = try {
        JsonPrimitive(block())
    } catch (_: Exception) {
        JsonPrimitive("<throws>")
    }
}
