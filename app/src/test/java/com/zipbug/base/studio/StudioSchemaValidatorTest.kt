package com.zipbug.base.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioSchemaValidatorTest {
    @Test
    fun acceptsLegacyComponentsSchemaForMigrationCompatibility() {
        val schema = """
            {
              "id":"legacy",
              "screens":[{
                "id":"main",
                "name":"Main",
                "components":[{
                  "id":"hero_text",
                  "type":"Text",
                  "x":16,"y":24,"width":120,"height":40,
                  "fontSize":14,"cornerRadius":0,
                  "fillColor":"#112233","textColor":"#FFFFFF"
                }]
              }]
            }
        """.trimIndent()

        assertTrue(StudioSchemaValidator.validate(schema).isEmpty())
    }

    @Test
    fun reportsExactFieldPathForInvalidNode() {
        val schema = """
            {
              "screens":[{
                "id":"main",
                "name":"Main",
                "nodes":[{
                  "id":"Bad-ID",
                  "type":"Text",
                  "x":-1,"y":0,"width":0,"height":40,
                  "fontSize":14,"radius":0,
                  "color":"#FFFFFF","textColor":"#FFFFFF"
                }]
              }]
            }
        """.trimIndent()

        val issues = StudioSchemaValidator.validate(schema)
        assertTrue(issues.any { it.path == "screens[0].nodes[0].id" })
        assertTrue(issues.any { it.path == "screens[0].nodes[0].x" })
        assertTrue(issues.any { it.path == "screens[0].nodes[0].width" })
    }

    @Test
    fun rejectsDuplicateComposeFunctionNames() {
        val schema = """
            {
              "screens":[
                {"id":"a","name":"My Screen","nodes":[]},
                {"id":"b","name":"My-Screen","nodes":[]}
              ]
            }
        """.trimIndent()

        val issues = StudioSchemaValidator.validate(schema, requireComposeNames = true)
        assertEquals("screens[1].name", issues.first { it.message.contains("duplicate Compose") }.path)
    }
}
