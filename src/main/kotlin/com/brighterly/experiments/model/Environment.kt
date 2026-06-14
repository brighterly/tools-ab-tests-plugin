package com.brighterly.experiments.model

/**
 * A named experiment source environment, e.g. label="staging",
 * url="https://storage.googleapis.com/brighterly-staging-configs/configs.json".
 *
 * `var` properties + default no-arg constructor are required by the IntelliJ
 * XML serializer used for plugin settings persistence.
 */
data class Environment(
    var label: String = "",
    var url: String = "",
)
