package com.brighterly.experiments.settings

import com.brighterly.experiments.model.ConfigEntry
import com.brighterly.experiments.model.ConfigType
import com.intellij.openapi.components.*

@State(
    name = "ExperimentsPluginSettings",
    storages = [Storage("experimentsPlugin.xml")],
)
@Service(Service.Level.APP)
class ExperimentsSettings : PersistentStateComponent<ExperimentsSettings.State> {

    data class State(
        // Legacy single-path field — kept for migration only
        var configFilePath: String = "",
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
    }

    companion object {
        fun getInstance(): ExperimentsSettings = service()
    }
}
