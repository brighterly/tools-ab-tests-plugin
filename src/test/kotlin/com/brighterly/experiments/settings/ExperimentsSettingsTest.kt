package com.brighterly.experiments.settings

import com.brighterly.experiments.model.ConfigEntry
import com.brighterly.experiments.model.ConfigType
import org.junit.Assert.*
import org.junit.Test

class ExperimentsSettingsTest {

    @Test
    fun `fresh state seeds five default environments`() {
        val state = ExperimentsSettings.State()
        assertEquals(5, state.environments.size)
        assertEquals(
            listOf("development", "staging", "sandbox", "production", "local"),
            state.environments.map { it.label },
        )
        assertEquals("development", state.activeEnvironment)
        assertFalse(state.showFileConfigs)
    }

    @Test
    fun `development url is the confirmed bucket`() {
        val dev = ExperimentsSettings.State().environments.first { it.label == "development" }
        assertEquals(
            "https://storage.googleapis.com/brighterly-dev-configs/configs.json",
            dev.url,
        )
    }

    @Test
    fun `loadState seeds environments when persisted state has none`() {
        val settings = ExperimentsSettings()
        val persisted = ExperimentsSettings.State().apply { environments = mutableListOf() }
        settings.loadState(persisted)
        assertEquals(5, settings.state.environments.size)
        assertEquals("development", settings.state.activeEnvironment)
    }

    @Test
    fun `loadState migrates legacy single configFilePath into configs`() {
        val settings = ExperimentsSettings()
        val persisted = ExperimentsSettings.State().apply {
            configFilePath = "/x/config/experiments.php"
            configs = mutableListOf()
        }
        settings.loadState(persisted)
        assertEquals(1, settings.state.configs.size)
        assertEquals("/x/config/experiments.php", settings.state.configs[0].path)
        assertEquals(ConfigType.PHP, settings.state.configs[0].type)
        assertEquals("", settings.state.configFilePath)
    }

    @Test
    fun `loadState preserves legacy configs but keeps file display off by default`() {
        val settings = ExperimentsSettings()
        val persisted = ExperimentsSettings.State().apply {
            configs = mutableListOf(ConfigEntry("/x/experiments.json", ConfigType.JSON))
        }
        settings.loadState(persisted)
        assertEquals(1, settings.state.configs.size)
        assertFalse(settings.state.showFileConfigs)
    }
}
