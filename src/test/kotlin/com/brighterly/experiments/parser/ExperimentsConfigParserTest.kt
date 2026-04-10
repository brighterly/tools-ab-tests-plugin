package com.brighterly.experiments.parser

import com.brighterly.experiments.model.ExperimentData
import org.junit.Assert.*
import org.junit.Test

class ExperimentsConfigParserTest {

    private val sampleConfig = """
        <?php
        return [
            'exp-1_example_experiment' => [
                'branches' => [
                    ExperimentBranch::ORIGINAL->value => ['percentage' => 80],
                    ExperimentBranch::TEST->value => ['percentage' => 20],
                ],
            ],
            'exp-2_example_experiment_2' => [
                'branches' => [
                    ExperimentBranch::ORIGINAL->value => ['percentage' => 50],
                    ExperimentBranch::TEST->value => ['percentage' => 50],
                ],
                'override_branch' => ExperimentBranch::TEST->value,
            ],
            'exp-3_example_experiment_3' => [
                'branches' => [
                    ExperimentBranch::ORIGINAL->value => ['percentage' => 70],
                    ExperimentBranch::TEST->value => ['percentage' => 30],
                ],
                'override_branch' => ExperimentBranch::ORIGINAL->value,
            ],
        ];
    """.trimIndent()

    @Test
    fun `parses experiment keys`() {
        val result = ExperimentsConfigParser.parse(sampleConfig)
        assertEquals(3, result.size)
        assertTrue(result.containsKey("exp-1_example_experiment"))
        assertTrue(result.containsKey("exp-2_example_experiment_2"))
        assertTrue(result.containsKey("exp-3_example_experiment_3"))
    }

    @Test
    fun `parses branch percentages`() {
        val result = ExperimentsConfigParser.parse(sampleConfig)
        val exp = result["exp-1_example_experiment"]!!
        assertEquals(80, exp.branches["original"])
        assertEquals(20, exp.branches["test"])
    }

    @Test
    fun `detects closed experiment with override_branch`() {
        val result = ExperimentsConfigParser.parse(sampleConfig)
        val closed = result["exp-2_example_experiment_2"]!!
        assertTrue(closed.isClosed)
        assertEquals("test", closed.overrideBranch)
    }

    @Test
    fun `open experiment has no overrideBranch`() {
        val result = ExperimentsConfigParser.parse(sampleConfig)
        assertFalse(result["exp-1_example_experiment"]!!.isClosed)
        assertNull(result["exp-1_example_experiment"]!!.overrideBranch)
    }

    @Test
    fun `distributionLabel formats correctly`() {
        val exp = ExperimentData(key = "exp-test", branches = mapOf("original" to 80, "test" to 20))
        assertEquals("80/20", exp.distributionLabel)
    }

    @Test
    fun `parses start_date when present`() {
        val configWithDate = """
            <?php
            return [
                'exp-1_with-date' => [
                    'branches' => [
                        ExperimentBranch::ORIGINAL->value => ['percentage' => 50],
                        ExperimentBranch::TEST->value => ['percentage' => 50],
                    ],
                    'start_date' => '2024-06-01',
                ],
            ];
        """.trimIndent()
        val result = ExperimentsConfigParser.parse(configWithDate)
        assertEquals("2024-06-01", result["exp-1_with-date"]?.startDate)
    }

    @Test
    fun `returns empty map for empty config`() {
        assertTrue(ExperimentsConfigParser.parse("<?php return [];").isEmpty())
    }
}
