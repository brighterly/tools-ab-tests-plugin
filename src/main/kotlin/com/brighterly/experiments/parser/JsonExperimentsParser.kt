package com.brighterly.experiments.parser

import com.brighterly.experiments.model.ExperimentData
import com.google.gson.JsonParser

object JsonExperimentsParser {

    fun parse(jsonContent: String): Map<String, ExperimentData> {
        val root = runCatching { JsonParser.parseString(jsonContent).asJsonObject }
            .getOrNull() ?: return emptyMap()

        // Remote/cached configs nest experiments under an "experiments" object;
        // legacy local files put exp-* keys at the top level. Support both.
        val container = runCatching { root.getAsJsonObject("experiments") }.getOrNull() ?: root

        val result = mutableMapOf<String, ExperimentData>()
        for ((key, value) in container.entrySet()) {
            if (!key.startsWith("exp-")) continue
            val obj = runCatching { value.asJsonObject }.getOrNull() ?: continue

            val branches = obj.getAsJsonArray("branches")
                ?.mapNotNull { element ->
                    val b = runCatching { element.asJsonObject }.getOrNull() ?: return@mapNotNull null
                    val branchValue = b.get("value")?.takeIf { !it.isJsonNull }?.asString ?: return@mapNotNull null
                    val pct = runCatching { b.get("percentage")?.asInt }.getOrNull() ?: return@mapNotNull null
                    branchValue to pct
                }
                ?.toMap()
                ?: emptyMap()

            val overrideBranch = obj.get("override_branch")?.takeIf { !it.isJsonNull }?.asString
            val startDate = obj.get("start_date")?.takeIf { !it.isJsonNull }?.asString

            result[key] = ExperimentData(
                key = key,
                branches = branches,
                overrideBranch = overrideBranch,
                startDate = startDate,
            )
        }
        return result
    }
}
