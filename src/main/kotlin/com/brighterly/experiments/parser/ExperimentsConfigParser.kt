package com.brighterly.experiments.parser

import com.brighterly.experiments.model.ExperimentData

object ExperimentsConfigParser {

    // Finds an experiment key followed by `=> [`  — used to locate block starts
    private val KEY_OPENER_REGEX = Regex("""'(exp-[^']+)'\s*=>\s*\[""")

    // ExperimentBranch::ORIGINAL->value => ['percentage' => 80]
    private val BRANCH_PERCENTAGE_REGEX = Regex(
        """ExperimentBranch::(\w+)->value\s*=>\s*\['percentage'\s*=>\s*(\d+)\]""",
    )

    // 'override_branch' => ExperimentBranch::TEST->value
    private val OVERRIDE_BRANCH_REGEX = Regex(
        """'override_branch'\s*=>\s*ExperimentBranch::(\w+)->value""",
    )

    // 'start_date' => '2024-06-01'
    private val START_DATE_REGEX = Regex(
        """'start_date'\s*=>\s*'([^']+)'""",
    )

    fun parse(phpContent: String): Map<String, ExperimentData> {
        val result = mutableMapOf<String, ExperimentData>()
        // Strip single-line comments to avoid false matches on comment text
        val stripped = phpContent.replace(Regex("""//[^\n]*"""), "")

        KEY_OPENER_REGEX.findAll(stripped).forEach { match ->
            val key = match.groupValues[1]
            // `match.range.last` is the position of the opening `[`
            val openPos = match.range.last

            val body = extractBlock(stripped, openPos) ?: return@forEach

            val branches = mutableMapOf<String, Int>()
            BRANCH_PERCENTAGE_REGEX.findAll(body).forEach { branchMatch ->
                branches[branchMatch.groupValues[1].lowercase()] =
                    branchMatch.groupValues[2].toIntOrNull() ?: 0
            }

            val overrideBranch = OVERRIDE_BRANCH_REGEX.find(body)
                ?.groupValues?.get(1)?.lowercase()

            val startDate = START_DATE_REGEX.find(body)?.groupValues?.get(1)

            result[key] = ExperimentData(
                key = key,
                branches = branches,
                overrideBranch = overrideBranch,
                startDate = startDate,
            )
        }
        return result
    }

    /**
     * Given the position of an opening `[`, walks forward counting brackets
     * and returns the content between `[` and its matching `]` (exclusive).
     */
    private fun extractBlock(text: String, openPos: Int): String? {
        if (openPos >= text.length || text[openPos] != '[') return null
        var depth = 1
        var i = openPos + 1
        while (i < text.length && depth > 0) {
            when (text[i]) {
                '[' -> depth++
                ']' -> depth--
            }
            i++
        }
        if (depth != 0) return null
        return text.substring(openPos + 1, i - 1)
    }
}
