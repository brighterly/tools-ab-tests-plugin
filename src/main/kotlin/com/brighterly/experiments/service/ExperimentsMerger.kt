package com.brighterly.experiments.service

import com.brighterly.experiments.model.ExperimentData

object ExperimentsMerger {
    /** Merges experiment maps with first-source-wins precedence on key conflicts. */
    fun merge(sources: List<Map<String, ExperimentData>>): Map<String, ExperimentData> {
        val merged = LinkedHashMap<String, ExperimentData>()
        for (source in sources) {
            for ((key, value) in source) merged.putIfAbsent(key, value)
        }
        return merged
    }
}
