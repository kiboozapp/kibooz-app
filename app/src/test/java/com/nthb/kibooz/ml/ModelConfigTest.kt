package com.nthb.kibooz.ml

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ModelConfigTest {

    private val emotions = Emotion.entries.map { it.name }.toSet()
    private val trash = TrashCategory.entries.map { it.name }.toSet()

    private fun config(groups: String = """{"SENANG":["a"],"MARAH":["b"]}""", extra: String = "") = ModelConfig.parse(
        """{"name":"uji","colorMode":"RGB","groups":$groups $extra}"""
    )

    private fun refused(block: () -> Unit): String {
        try {
            block()
        } catch (e: IllegalArgumentException) {
            return e.message.orEmpty()
        }
        fail("expected the config to be refused")
        return ""
    }

    @Test
    fun `defaults are safe`() {
        val config = config()

        assertTrue("a model counts as a placeholder until the config says otherwise", config.placeholder)
        assertFalse(config.centerCrop)
        assertEquals(255f, config.std, 0f)
        assertEquals(3, config.channels)
    }

    @Test
    fun `a valid config passes`() {
        config().validate(listOf("a", "b", "c"), emotions)
    }

    @Test
    fun `a group that is not an enum of the app is refused`() {
        val message = refused { config("""{"KALEM":["a"]}""").validate(listOf("a"), emotions) }

        assertTrue(message, message.contains("KALEM"))
    }

    @Test
    fun `an emotion group is refused for the trash model`() {
        refused { config().validate(listOf("a", "b"), trash) }
    }

    @Test
    fun `a label that the model does not have is refused`() {
        val message = refused { config().validate(listOf("a"), emotions) }

        assertTrue(message, message.contains("\"b\""))
    }

    @Test
    fun `a label in two groups is refused`() {
        val message = refused { config("""{"SENANG":["a"],"MARAH":["a"]}""").validate(listOf("a"), emotions) }

        assertTrue(message, message.contains("lebih dari satu"))
    }

    @Test
    fun `an empty label list is refused`() {
        refused { config().validate(emptyList(), emotions) }
    }

    @Test
    fun `a duplicated label is fine when no group uses it, as in ImageNet`() {
        config().validate(listOf("a", "b", "crane", "crane"), emotions)
    }

    @Test
    fun `a duplicated label that a group uses is ambiguous and refused`() {
        val message = refused { config().validate(listOf("a", "a", "b"), emotions) }

        assertTrue(message, message.contains("ambigu"))
    }

    @Test
    fun `an unknown color mode is refused`() {
        refused { ModelConfig.parse("""{"name":"x","colorMode":"CMYK","groups":{"SENANG":["a"]}}""").channels }
    }

    @Test
    fun `a zero std is refused`() {
        refused { config(extra = ""","std":0""").validate(listOf("a", "b"), emotions) }
    }

    // Berkas yang benar-benar dikirim bersama aplikasi

    private val assets = File("src/main/assets")

    private fun labels(config: ModelConfig): List<String> =
        config.labels ?: File(assets, config.labelsFile!!).readLines().map { it.trim() }.filter { it.isNotEmpty() }

    @Test
    fun `the shipped emotion config is valid and maps the 7 labels to the 4 emotions`() {
        val config = ModelConfig.parse(File(assets, "kibooz_emotion_v1.json").readText())

        config.validate(labels(config), emotions)
        assertEquals(7, labels(config).size)
        assertEquals(emotions, config.groups.keys)
        assertEquals(1, config.channels)
        assertTrue(config.placeholder)
    }

    @Test
    fun `the shipped trash config is valid and covers the three categories`() {
        val config = ModelConfig.parse(File(assets, "kibooz_trash_classifier_v1.json").readText())

        config.validate(labels(config), trash)
        assertEquals(1000, labels(config).size)
        assertEquals(trash, config.groups.keys)
        assertTrue(config.centerCrop)
        assertTrue(config.placeholder)
    }

    @Test
    fun `the model files are real TFLite files within the size budget of the AI doc`() {
        fun check(name: String, maxBytes: Long) {
            val file = File(assets, name)
            assertTrue("$name is missing", file.exists())
            assertTrue("$name is ${file.length()} bytes, over $maxBytes", file.length() in 1..maxBytes)
            // Berkas FlatBuffer TFLite punya identifier "TFL3" di byte 4 sampai 7
            val header = file.inputStream().use { it.readNBytes(8) }
            assertEquals("TFL3", String(header, 4, 4, Charsets.US_ASCII))
        }

        check("kibooz_emotion_v1.tflite", 8L * 1024 * 1024)
        check("kibooz_trash_classifier_v1.tflite", 12L * 1024 * 1024)
    }

    @Test
    fun `the license notices of both placeholder models ship with the app`() {
        val mit = File(assets, "licenses/emotion-nxp-deepface-MIT.txt").readText()
        val apache = File(assets, "licenses/trash-efficientnet-lite0-Apache-2.0.txt").readText()

        assertTrue(mit.contains("Sefik Ilkin Serengil") && mit.contains("NXP"))
        assertTrue(apache.contains("Apache License") && apache.contains("Version 2.0"))
    }
}
