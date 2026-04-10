package com.brighterly.experiments.model

enum class ConfigType { PHP, JSON }

data class ConfigEntry(
    var path: String = "",
    var type: ConfigType = ConfigType.PHP,
)
