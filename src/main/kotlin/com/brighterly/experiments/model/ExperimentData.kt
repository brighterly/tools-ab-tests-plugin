package com.brighterly.experiments.model

data class ExperimentData(
    val key: String,
    val branches: Map<String, Int>,   // e.g. {"original" -> 80, "test" -> 20}
    val overrideBranch: String? = null,
    val startDate: String? = null,
) {
    val isClosed: Boolean get() = overrideBranch != null

    /** E.g. "80/20" or "50/50" */
    val distributionLabel: String
        get() = branches.values.joinToString("/")
}
