package com.brighterly.experiments.settings

import com.brighterly.experiments.model.ConfigEntry
import com.brighterly.experiments.model.ConfigType
import com.brighterly.experiments.model.Environment
import com.intellij.openapi.components.*

@State(
    name = "ExperimentsPluginSettings",
    storages = [Storage("experimentsPlugin.xml")],
)
@Service(Service.Level.APP)
class ExperimentsSettings : PersistentStateComponent<ExperimentsSettings.State> {

    data class State(
        // Environment model (primary source going forward)
        var environments: MutableList<Environment> = defaultEnvironments(),
        var activeEnvironment: String = "development",
        // When false (default), experiments from local files are not shown.
        var showFileConfigs: Boolean = false,

        // Legacy single-path field — kept for migration only
        var configFilePath: String = "",
        // Deprecated file-based configs — only consulted when showFileConfigs == true
        var configs: MutableList<ConfigEntry> = mutableListOf(),
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state

        // Migrate legacy single-path setting to list format
        if (state.configs.isEmpty() && state.configFilePath.isNotBlank()) {
            state.configs.add(ConfigEntry(state.configFilePath, ConfigType.PHP))
            state.configFilePath = ""
        }

        // Seed environments for users upgrading from a pre-environment version
        if (state.environments.isEmpty()) {
            state.environments = defaultEnvironments()
        }
        if (state.activeEnvironment.isBlank()) {
            state.activeEnvironment = "development"
        }
    }

    companion object {
        fun getInstance(): ExperimentsSettings = service()

        /**
         * The five seeded environments. Only `development` is a confirmed URL;
         * the rest follow the `brighterly-<env>-configs` bucket pattern as an
         * editable best guess (corrected in Settings → Tools → AB Tests).
         */
        fun defaultEnvironments(): MutableList<Environment> = mutableListOf(
            Environment("development", "https://storage.googleapis.com/brighterly-dev-configs/configs.json"),
            Environment("staging", "https://storage.googleapis.com/brighterly-staging-configs/configs.json"),
            Environment("sandbox", "https://storage.googleapis.com/brighterly-sandbox-configs/configs.json"),
            Environment("production", "https://storage.googleapis.com/brighterly-prod-configs/configs.json"),
            Environment("local", "https://storage.googleapis.com/brighterly-local-configs/configs.json"),
        )
    }
}
