package com.zipbug.base.recap

import com.zipbug.base.data.recap.RecapProjectEntity
import org.junit.Assert.*
import org.junit.Test

class RecapScriptGeneratorTest {

    @Test
    fun testBuildPromptIncludesLanguageAndMode() {
        val promptMy = RecapScriptGenerator.buildPrompt(
            transcriptOrTopic = "Sample movie synopsis",
            mode = RecapProjectEntity.MODE_RECAP_DIALOGUE,
            targetLanguage = RecapProjectEntity.LANG_MYANMAR
        )
        assertTrue(promptMy.contains("Myanmar (Burmese)"))
        assertTrue(promptMy.contains("Recap + Dialogue"))
        assertTrue(promptMy.contains("dialogue and character quotes"))

        val promptEn = RecapScriptGenerator.buildPrompt(
            transcriptOrTopic = "Action thriller",
            mode = RecapProjectEntity.MODE_DUB,
            targetLanguage = RecapProjectEntity.LANG_ENGLISH
        )
        assertTrue(promptEn.contains("English"))
        assertTrue(promptEn.contains("Dub"))
        assertTrue(promptEn.contains("dramatic dubbing"))
    }

    @Test
    fun testParsePartsFromStructuredOutput() {
        val sampleAiOutput = """
[PART 1: The Inciting Incident]
In a quiet village, a strange artifact is discovered buried under the ancient pagoda.

[PART 2: The Rising Journey]
Two explorers set off across the misty mountains seeking the ancient master's sanctuary.

[PART 3: The Fatal Confrontation]
The temple grounds shake as the rogue guardian unleashes forbidden elemental magic.

[PART 4: The Final Truth]
Harmony is restored as the artifact is returned to its rightful sacred sanctuary.
        """.trimIndent()

        val parts = RecapScriptGenerator.parsePartsFromOutput("proj-1", sampleAiOutput, "my")
        assertEquals(4, parts.size)

        assertEquals(1, parts[0].partNumber)
        assertEquals("The Inciting Incident", parts[0].title)
        assertTrue(parts[0].scriptText.contains("strange artifact"))

        assertEquals(2, parts[1].partNumber)
        assertEquals("The Rising Journey", parts[1].title)
        assertTrue(parts[1].scriptText.contains("Two explorers"))

        assertEquals(3, parts[2].partNumber)
        assertEquals("The Fatal Confrontation", parts[2].title)
        assertTrue(parts[2].scriptText.contains("temple grounds shake"))

        assertEquals(4, parts[3].partNumber)
        assertEquals("The Final Truth", parts[3].title)
        assertTrue(parts[3].scriptText.contains("Harmony is restored"))
    }

    @Test
    fun testParsePartsFallbackForUnstructuredText() {
        val rawText = """
Paragraph one about introducing the hero and the kingdom.

Paragraph two about the unexpected storm and conflict brewing.

Paragraph three where the fierce battle reaches its dramatic peak.

Paragraph four where peace returns and our heroes find their new destiny.
        """.trimIndent()

        val parts = RecapScriptGenerator.parsePartsFromOutput("proj-1", rawText, "en")
        assertEquals(4, parts.size)
        assertEquals(1, parts[0].partNumber)
        assertEquals(2, parts[1].partNumber)
        assertEquals(3, parts[2].partNumber)
        assertEquals(4, parts[3].partNumber)
        assertTrue(parts[0].scriptText.contains("Paragraph one"))
        assertTrue(parts[3].scriptText.contains("Paragraph four"))
    }
}
