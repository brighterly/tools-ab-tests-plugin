package com.brighterly.experiments.parser

import com.brighterly.experiments.model.ExperimentData
import org.junit.Assert.*
import org.junit.Test

class JsonExperimentsConfigParserTest {

    private val sampleJson = """
        {
            "exp-48_comprehension-retesting": {
                "branches": [
                    {"value": "original", "percentage": 50},
                    {"value": "test", "percentage": 50}
                ]
            },
            "exp-69_add-math-booking": {
                "branches": [
                    {"value": "original", "percentage": 80},
                    {"value": "test", "percentage": 20}
                ],
                "override_branch": "test"
            }
        }
    """.trimIndent()

    @Test
    fun `parses experiment keys`() {
        val result = JsonExperimentsParser.parse(sampleJson)
        assertEquals(2, result.size)
        assertTrue(result.containsKey("exp-48_comprehension-retesting"))
        assertTrue(result.containsKey("exp-69_add-math-booking"))
    }

    @Test
    fun `parses branch percentages`() {
        val result = JsonExperimentsParser.parse(sampleJson)
        val exp = result["exp-48_comprehension-retesting"]!!
        assertEquals(50, exp.branches["original"])
        assertEquals(50, exp.branches["test"])
    }

    @Test
    fun `detects closed experiment with override_branch`() {
        val result = JsonExperimentsParser.parse(sampleJson)
        val closed = result["exp-69_add-math-booking"]!!
        assertTrue(closed.isClosed)
        assertEquals("test", closed.overrideBranch)
    }

    @Test
    fun `open experiment has no overrideBranch`() {
        val result = JsonExperimentsParser.parse(sampleJson)
        assertFalse(result["exp-48_comprehension-retesting"]!!.isClosed)
        assertNull(result["exp-48_comprehension-retesting"]!!.overrideBranch)
    }

    @Test
    fun `distributionLabel formats correctly`() {
        val result = JsonExperimentsParser.parse(sampleJson)
        assertEquals("50/50", result["exp-48_comprehension-retesting"]!!.distributionLabel)
    }

    @Test
    fun `ignores non-exp keys`() {
        val json = """{"some_other_key": {"branches": []}, "exp-1_real": {"branches": [{"value": "a", "percentage": 100}]}}"""
        val result = JsonExperimentsParser.parse(json)
        assertEquals(1, result.size)
        assertTrue(result.containsKey("exp-1_real"))
    }

    @Test
    fun `returns empty map for empty json object`() {
        assertTrue(JsonExperimentsParser.parse("{}").isEmpty())
    }

    @Test
    fun `returns empty map for invalid json`() {
        assertTrue(JsonExperimentsParser.parse("not json at all").isEmpty())
    }

    @Test
    fun `skips malformed branch without crashing`() {
        val json = """
            {
                "exp-1_test": {
                    "branches": [
                        {"value": "original", "percentage": 50},
                        {"missing_fields": true}
                    ]
                }
            }
        """.trimIndent()
        val result = JsonExperimentsParser.parse(json)
        // Should parse successfully, skipping the malformed branch
        assertEquals(1, result.size)
        val exp = result["exp-1_test"]!!
        assertEquals(1, exp.branches.size)
        assertEquals(50, exp.branches["original"])
    }
}
