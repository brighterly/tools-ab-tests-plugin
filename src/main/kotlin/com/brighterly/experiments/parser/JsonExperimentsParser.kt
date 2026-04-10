package com.brighterly.experiments.parser

import com.brighterly.experiments.model.ExperimentData
import com.google.gson.JsonParser

object JsonExperimentsParser {

    fun parse(jsonContent: String): Map<String, ExperimentData> {
        val root = runCatching { JsonParser.parseString(jsonContent).asJsonObject }
            .getOrNull() ?: return emptyMap()

        val result = mutableMapOf<String, ExperimentData>()
        for ((key, value) in root.entrySet()) {
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

            result[key] = ExperimentData(
                key = key,
                branches = branches,
                overrideBranch = overrideBranch,
                startDate = null,
            )
        }
        return result
    }
}
