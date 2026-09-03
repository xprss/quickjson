package io.github.xprss.quickjson.domain

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

data class JsonError(
    val message: String,
    val line: Int,
    val column: Int,
    val offset: Int,
    val path: String? = null,
    val duplicateKey: Boolean = false,
)

sealed interface JsonValidation {
    data class Valid(val element: JsonElement) : JsonValidation
    data class Invalid(val error: JsonError) : JsonValidation
}

object JsonEngine {
    private val parser = Json { isLenient = false; allowSpecialFloatingPointValues = false }

    fun validate(raw: String): JsonValidation {
        DuplicateKeyDetector.find(raw)?.let { return JsonValidation.Invalid(it) }
        return try {
            JsonValidation.Valid(parser.parseToJsonElement(raw))
        } catch (error: Exception) {
            val offset = Regex("(?:offset|position)\\s+(\\d+)", RegexOption.IGNORE_CASE)
                .find(error.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
                ?.coerceIn(0, raw.length) ?: 0
            val (line, column) = lineColumn(raw, offset)
            JsonValidation.Invalid(
                JsonError(
                    message = error.message?.substringBefore(" at path") ?: "Invalid JSON",
                    line = line,
                    column = column,
                    offset = offset,
                ),
            )
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun format(raw: String, indent: Int): Result<String> = runCatching {
        val validation = validate(raw)
        val element = (validation as? JsonValidation.Valid)?.element
            ?: error((validation as JsonValidation.Invalid).error.message)
        Json { prettyPrint = true; prettyPrintIndent = " ".repeat(if (indent == 4) 4 else 2) }
            .encodeToString(JsonElement.serializer(), element)
    }

    fun minify(raw: String): Result<String> = runCatching {
        val result = validate(raw)
        require(result is JsonValidation.Valid) { (result as JsonValidation.Invalid).error.message }
        result.element.toString()
    }

    fun lineColumn(raw: String, offset: Int): Pair<Int, Int> {
        var line = 1
        var column = 1
        raw.take(offset.coerceIn(0, raw.length)).forEach {
            if (it == '\n') { line++; column = 1 } else column++
        }
        return line to column
    }
}

private object DuplicateKeyDetector {
    fun find(raw: String): JsonError? = runCatching { Scanner(raw).scan() }.getOrNull()

    private class Scanner(private val source: String) {
        private var position = 0
        private var duplicate: JsonError? = null

        fun scan(): JsonError? {
            whitespace()
            value("$")
            return duplicate
        }

        private fun value(path: String) {
            whitespace()
            when (peek()) {
                '{' -> objectValue(path)
                '[' -> arrayValue(path)
                '"' -> stringToken()
                else -> while (position < source.length && source[position] !in ",]} \t\r\n") position++
            }
            whitespace()
        }

        private fun objectValue(path: String) {
            position++
            whitespace()
            val keys = mutableSetOf<String>()
            if (peek() == '}') { position++; return }
            while (position < source.length) {
                whitespace()
                val keyOffset = position
                val key = stringToken()
                whitespace()
                if (peek() != ':') return
                position++
                val childPath = "$path.${escapePath(key)}"
                if (!keys.add(key) && duplicate == null) {
                    val (line, column) = JsonEngine.lineColumn(source, keyOffset)
                    duplicate = JsonError(
                        message = "Duplicate key: $key",
                        line = line,
                        column = column,
                        offset = keyOffset,
                        path = childPath,
                        duplicateKey = true,
                    )
                }
                value(childPath)
                when (peek()) {
                    ',' -> position++
                    '}' -> { position++; return }
                    else -> return
                }
            }
        }

        private fun arrayValue(path: String) {
            position++
            whitespace()
            if (peek() == ']') { position++; return }
            var index = 0
            while (position < source.length) {
                value("$path[$index]")
                index++
                when (peek()) {
                    ',' -> position++
                    ']' -> { position++; return }
                    else -> return
                }
            }
        }

        private fun stringToken(): String {
            val start = position
            if (peek() != '"') return ""
            position++
            var escaped = false
            while (position < source.length) {
                val char = source[position++]
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') break
            }
            return runCatching {
                Json.parseToJsonElement(source.substring(start, position)).jsonPrimitive.content
            }.getOrDefault(source.substring((start + 1).coerceAtMost(position), (position - 1).coerceAtLeast(start + 1)))
        }

        private fun whitespace() { while (position < source.length && source[position].isWhitespace()) position++ }
        private fun peek(): Char? = source.getOrNull(position)
        private fun escapePath(key: String) = if (key.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) key else "['${key.replace("'", "\\'")}']"
    }
}

sealed interface PathPart {
    data class Key(val value: String) : PathPart
    data class Index(val value: Int) : PathPart
}

data class JsonPath(val parts: List<PathPart> = emptyList()) {
    fun key(value: String) = JsonPath(parts + PathPart.Key(value))
    fun index(value: Int) = JsonPath(parts + PathPart.Index(value))
    val parent get() = takeIf { parts.isNotEmpty() }?.let { JsonPath(parts.dropLast(1)) }
    override fun toString(): String = buildString {
        append('$')
        parts.forEach { part ->
            when (part) {
                is PathPart.Key -> if (part.value.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) append('.').append(part.value)
                    else append("['").append(part.value.replace("'", "\\'")).append("']")
                is PathPart.Index -> append('[').append(part.value).append(']')
            }
        }
    }
}

enum class JsonType { OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL }

object JsonTree {
    fun typeOf(element: JsonElement) = when (element) {
        is JsonObject -> JsonType.OBJECT
        is JsonArray -> JsonType.ARRAY
        JsonNull -> JsonType.NULL
        is JsonPrimitive -> when {
            element.isString -> JsonType.STRING
            element.booleanOrNull != null -> JsonType.BOOLEAN
            else -> JsonType.NUMBER
        }
    }

    fun default(type: JsonType): JsonElement = when (type) {
        JsonType.OBJECT -> JsonObject(emptyMap())
        JsonType.ARRAY -> JsonArray(emptyList())
        JsonType.STRING -> JsonPrimitive("")
        JsonType.NUMBER -> JsonPrimitive(0)
        JsonType.BOOLEAN -> JsonPrimitive(false)
        JsonType.NULL -> JsonNull
    }

    fun get(root: JsonElement, path: JsonPath): JsonElement? = path.parts.fold(root as JsonElement?) { current, part ->
        when {
            current is JsonObject && part is PathPart.Key -> current[part.value]
            current is JsonArray && part is PathPart.Index -> current.getOrNull(part.value)
            else -> null
        }
    }

    fun replace(root: JsonElement, path: JsonPath, value: JsonElement): JsonElement {
        if (path.parts.isEmpty()) return value
        return mutateParent(root, path) { parent, last ->
            when {
                parent is JsonObject && last is PathPart.Key -> JsonObject(parent.toMutableMap().apply { put(last.value, value) })
                parent is JsonArray && last is PathPart.Index -> JsonArray(parent.toMutableList().apply { this[last.value] = value })
                else -> parent
            }
        }
    }

    fun remove(root: JsonElement, path: JsonPath): JsonElement {
        if (path.parts.isEmpty()) return root
        return mutateParent(root, path) { parent, last ->
            when {
                parent is JsonObject && last is PathPart.Key -> JsonObject(parent.filterKeys { it != last.value })
                parent is JsonArray && last is PathPart.Index -> JsonArray(parent.filterIndexed { index, _ -> index != last.value })
                else -> parent
            }
        }
    }

    fun duplicate(root: JsonElement, path: JsonPath): JsonElement {
        val value = get(root, path) ?: return root
        return mutateParent(root, path) { parent, last ->
            when {
                parent is JsonObject && last is PathPart.Key -> {
                    var key = "${last.value}_copy"
                    var number = 2
                    while (key in parent) key = "${last.value}_copy${number++}"
                    JsonObject(LinkedHashMap(parent).apply { put(key, value) })
                }
                parent is JsonArray && last is PathPart.Index -> JsonArray(parent.toMutableList().apply { add(last.value + 1, value) })
                else -> parent
            }
        }
    }

    fun move(root: JsonElement, path: JsonPath, delta: Int): JsonElement = mutateParent(root, path) { parent, last ->
        when {
            parent is JsonArray && last is PathPart.Index -> {
                val target = (last.value + delta).coerceIn(0, parent.lastIndex)
                JsonArray(parent.toMutableList().apply { add(target, removeAt(last.value)) })
            }
            parent is JsonObject && last is PathPart.Key -> {
                val entries = parent.entries.toMutableList()
                val from = entries.indexOfFirst { it.key == last.value }
                val target = (from + delta).coerceIn(0, entries.lastIndex)
                if (from >= 0) entries.add(target, entries.removeAt(from))
                JsonObject(linkedMapOf<String, JsonElement>().apply { entries.forEach { put(it.key, it.value) } })
            }
            else -> parent
        }
    }

    fun addChild(root: JsonElement, path: JsonPath, type: JsonType = JsonType.STRING): JsonElement =
        addChild(root, path, default(type))

    /** Adds a complete value in one mutation, preserving object order and making the key unique. */
    fun addChild(root: JsonElement, path: JsonPath, value: JsonElement, requestedKey: String? = null): JsonElement {
        val parent = get(root, path) ?: return root
        val changed = when (parent) {
            is JsonArray -> JsonArray(parent + value)
            is JsonObject -> {
                val baseKey = requestedKey?.trim().takeUnless { it.isNullOrEmpty() } ?: "key"
                var key = baseKey
                var number = 2
                while (key in parent) key = "$baseKey${number++}"
                JsonObject(LinkedHashMap(parent).apply { put(key, value) })
            }
            else -> parent
        }
        return replace(root, path, changed)
    }

    fun renameKey(root: JsonElement, path: JsonPath, newKey: String): Result<JsonElement> = runCatching {
        val old = path.parts.lastOrNull() as? PathPart.Key ?: error("Not an object member")
        val parentPath = path.parent ?: error("Root has no key")
        val parent = get(root, parentPath) as? JsonObject ?: error("Not an object member")
        require(newKey == old.value || newKey !in parent) { "Duplicate key: $newKey" }
        val renamed = linkedMapOf<String, JsonElement>()
        parent.forEach { (key, value) -> renamed[if (key == old.value) newKey else key] = value }
        replace(root, parentPath, JsonObject(renamed))
    }

    private fun mutateParent(
        root: JsonElement,
        path: JsonPath,
        mutation: (JsonElement, PathPart) -> JsonElement,
    ): JsonElement {
        val last = path.parts.lastOrNull() ?: return root
        val parentPath = JsonPath(path.parts.dropLast(1))
        val parent = get(root, parentPath) ?: return root
        return replace(root, parentPath, mutation(parent, last))
    }
}

class UndoHistory<T>(initial: T, private val capacity: Int = 100) {
    private val past = ArrayDeque<T>()
    private val future = ArrayDeque<T>()
    var current: T = initial
        private set

    val canUndo get() = past.isNotEmpty()
    val canRedo get() = future.isNotEmpty()

    fun push(value: T): T {
        if (value == current) return current
        past.addLast(current)
        while (past.size > capacity) past.removeFirst()
        current = value
        future.clear()
        return current
    }

    fun undo(): T {
        if (past.isEmpty()) return current
        future.addLast(current)
        current = past.removeLast()
        return current
    }

    fun redo(): T {
        if (future.isEmpty()) return current
        past.addLast(current)
        current = future.removeLast()
        return current
    }
}
