package com.brighterly.experiments.service

import com.brighterly.experiments.model.ExperimentData
import org.junit.Assert.*
import org.junit.Test

class ExperimentsMergerTest {

    private fun exp(key: String, pct: Int) =
        ExperimentData(key = key, branches = mapOf("original" to pct, "test" to (100 - pct)))

    @Test
    fun `earlier source wins on key conflict`() {
        val url = mapOf("exp-1" to exp("exp-1", 90))
        val file = mapOf("exp-1" to exp("exp-1", 10), "exp-2" to exp("exp-2", 50))
        val merged = ExperimentsMerger.merge(listOf(url, file))
        assertEquals(90, merged["exp-1"]!!.branches["original"]) // url wins
        assertEquals(50, merged["exp-2"]!!.branches["original"]) // file-only survives
        assertEquals(2, merged.size)
    }

    @Test
    fun `empty sources merge to empty`() {
        assertTrue(ExperimentsMerger.merge(emptyList()).isEmpty())
    }
}
