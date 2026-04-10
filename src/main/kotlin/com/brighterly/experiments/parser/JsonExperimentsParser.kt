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
                ?.associate { branch ->
                    val b = branch.asJsonObject
                    b.get("value").asString to b.get("percentage").asInt
                } ?: emptyMap()

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
