package com.brighterly.experiments.service

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConfigCacheTest {

    @JvmField @Rule val tmp = TemporaryFolder()

    private var cacheCount = 0
    private fun cache() = ConfigCache(tmp.newFolder("cache${cacheCount++}"))

    @Test
    fun `write creates a per-env json file and read returns its content`() {
        val cache = cache()
        cache.write("staging", """{"experiments":{}}""")
        assertTrue(cache.exists("staging"))
        assertEquals("""{"experiments":{}}""", cache.read("staging"))
    }

    @Test
    fun `read returns null when no cache exists`() {
        assertNull(cache().read("production"))
        assertFalse(cache().exists("production"))
    }

    @Test
    fun `cacheFile name is sanitized and lowercased`() {
        val cache = cache()
        val file = cache.cacheFile("Pro / duction")
        assertEquals("pro___duction.json", file.name)
    }

    @Test
    fun `applyFetch success writes the cache and returns the file`() {
        val cache = cache()
        val file = cache.applyFetch("dev", FetchResult.Success("""{"a":1}"""))
        assertNotNull(file)
        assertEquals("""{"a":1}""", cache.read("dev"))
    }

    @Test
    fun `applyFetch failure keeps an existing cache intact`() {
        val cache = cache()
        cache.write("dev", "GOOD")
        val file = cache.applyFetch("dev", FetchResult.Failure("offline"))
        assertNotNull(file)
        assertEquals("GOOD", cache.read("dev"))
    }

    @Test
    fun `applyFetch failure with no existing cache returns null`() {
        assertNull(cache().applyFetch("dev", FetchResult.Failure("offline")))
    }
}
