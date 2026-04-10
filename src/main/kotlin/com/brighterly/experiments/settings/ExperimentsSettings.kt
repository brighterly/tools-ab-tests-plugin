package com.brighterly.experiments.settings

import com.intellij.openapi.components.*

@State(
    name = "ExperimentsPluginSettings",
    storages = [Storage("experimentsPlugin.xml")],
)
@Service(Service.Level.APP)
class ExperimentsSettings : PersistentStateComponent<ExperimentsSettings.State> {

    data class State(var configFilePath: String = "")

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }

    companion object {
        fun getInstance(): ExperimentsSettings = service()
    }
}
