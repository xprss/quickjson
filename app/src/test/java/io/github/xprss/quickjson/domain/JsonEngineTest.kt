package io.github.xprss.quickjson.domain

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class JsonEngineTest {
    @Test
    fun parsesEveryJsonTypeAndUnicodeEscapes() {
        val raw = """{"object":{},"array":[],"string":"caf\u00e9 \\ \"","number":-1.25e2,"true":true,"false":false,"null":null}"""
        val result = assertIs<JsonValidation.Valid>(JsonEngine.validate(raw))
        val root = assertIs<JsonObject>(result.element)
        assertEquals("café \\ \"", (root["string"] as JsonPrimitive).content)
        assertEquals(7, root.size)
    }

    @Test
    fun reportsSyntaxLocation() {
        val result = assertIs<JsonValidation.Invalid>(JsonEngine.validate("{\n  \"a\": 1,\n}"))
        assertTrue(result.error.line >= 2)
        assertTrue(result.error.column >= 1)
    }

    @Test
    fun rejectsDuplicateKeysWithoutLosingData() {
        val result = assertIs<JsonValidation.Invalid>(JsonEngine.validate("{\n  \"a\": 1,\n  \"a\": 2\n}"))
        assertTrue(result.error.duplicateKey)
        assertEquals(3, result.error.line)
        assertEquals("$.a", result.error.path)
    }

    @Test
    fun duplicateDetectionIsScopedToEachObject() {
        assertIs<JsonValidation.Valid>(JsonEngine.validate("{\"a\":1,\"nested\":{\"a\":2}}"))
    }

    @Test
    fun formatsWithTwoOrFourSpacesAndMinifies() {
        val raw = "{\"a\":[1,true]}"
        assertTrue(JsonEngine.format(raw, 2).getOrThrow().contains("\n  \"a\""))
        assertTrue(JsonEngine.format(raw, 4).getOrThrow().contains("\n    \"a\""))
        assertEquals(raw, JsonEngine.minify(JsonEngine.format(raw, 4).getOrThrow()).getOrThrow())
    }

    @Test
    fun treeSupportsAddReplaceDuplicateRemoveAndReorder() {
        var root = JsonObject(linkedMapOf("a" to JsonPrimitive(1), "b" to JsonPrimitive(2)))
        root = JsonTree.replace(root, JsonPath().key("a"), JsonPrimitive(3)) as JsonObject
        assertEquals(3, root["a"]?.toString()?.toInt())
        root = JsonTree.duplicate(root, JsonPath().key("a")) as JsonObject
        assertTrue("a_copy" in root)
        root = JsonTree.move(root, JsonPath().key("b"), -1) as JsonObject
        assertEquals("b", root.keys.first())
        root = JsonTree.remove(root, JsonPath().key("a_copy")) as JsonObject
        assertFalse("a_copy" in root)

        var array = JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b")))
        array = JsonTree.duplicate(array, JsonPath().index(0)) as JsonArray
        assertEquals(3, array.size)
        array = JsonTree.move(array, JsonPath().index(0), 1) as JsonArray
        assertEquals("a", (array[1] as JsonPrimitive).content)
    }

    @Test
    fun renameRefusesDuplicateObjectKey() {
        val root = JsonObject(mapOf("a" to JsonPrimitive(1), "b" to JsonPrimitive(2)))
        assertTrue(JsonTree.renameKey(root, JsonPath().key("a"), "b").isFailure)
    }
}
