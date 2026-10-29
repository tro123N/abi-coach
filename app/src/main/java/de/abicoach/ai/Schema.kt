package de.abicoach.ai

import org.json.JSONArray
import org.json.JSONObject

/**
 * A tiny description of the JSON the app expects back from a model. It is written once and rendered
 * in the dialect of each provider: Gemini's OpenAPI subset ("STRING", "OBJECT", …) or the JSON Schema
 * that Claude's structured outputs require (lower-case types, additionalProperties = false).
 * Every property is required, which keeps both providers strict and avoids Claude's limit on optional fields.
 */
sealed class Sch {
    abstract val description: String?

    class Str(val enum: List<String>? = null, override val description: String? = null) : Sch()

    class Integer(override val description: String? = null) : Sch()

    class Num(override val description: String? = null) : Sch()

    class Bool(override val description: String? = null) : Sch()

    class Arr(val items: Sch, override val description: String? = null) : Sch()

    class Obj(val props: List<Pair<String, Sch>>, override val description: String? = null) : Sch()

    /** Gemini `responseSchema` (OpenAPI 3 subset). */
    fun gemini(): JSONObject {
        val o = JSONObject()
        when (this) {
            is Str -> {
                o.put("type", "STRING")
                enum?.let { o.put("enum", JSONArray(it)) }
            }
            is Integer -> o.put("type", "INTEGER")
            is Num -> o.put("type", "NUMBER")
            is Bool -> o.put("type", "BOOLEAN")
            is Arr -> o.put("type", "ARRAY").put("items", items.gemini())
            is Obj -> {
                val properties = JSONObject()
                props.forEach { (k, v) -> properties.put(k, v.gemini()) }
                val names = JSONArray(props.map { it.first })
                o.put("type", "OBJECT").put("properties", properties).put("required", names).put("propertyOrdering", JSONArray(props.map { it.first }))
            }
        }
        description?.let { o.put("description", it) }
        return o
    }

    /** JSON Schema for Claude's `output_config.format`. */
    fun jsonSchema(): JSONObject {
        val o = JSONObject()
        when (this) {
            is Str -> {
                o.put("type", "string")
                enum?.let { o.put("enum", JSONArray(it)) }
            }
            is Integer -> o.put("type", "integer")
            is Num -> o.put("type", "number")
            is Bool -> o.put("type", "boolean")
            is Arr -> o.put("type", "array").put("items", items.jsonSchema())
            is Obj -> {
                val properties = JSONObject()
                props.forEach { (k, v) -> properties.put(k, v.jsonSchema()) }
                o.put("type", "object").put("properties", properties).put("required", JSONArray(props.map { it.first }))
                    .put("additionalProperties", false)
            }
        }
        description?.let { o.put("description", it) }
        return o
    }

    /** A readable outline of the expected JSON, used when a model has to be asked for JSON in plain text. */
    fun outline(indent: String = ""): String =
        when (this) {
            is Str -> if (enum != null) enum.joinToString("|", "\"", "\"") else "\"…\""
            is Integer -> "0"
            is Num -> "0.0"
            is Bool -> "true|false"
            is Arr -> "[" + items.outline("$indent  ") + ", …]"
            is Obj ->
                props.joinToString(",\n", "{\n", "\n$indent}") { (k, v) -> "$indent  \"$k\": ${v.outline("$indent  ")}" }
        }
}

fun str(description: String? = null) = Sch.Str(null, description)

fun enumOf(values: List<String>, description: String? = null) = Sch.Str(values, description)

fun int(description: String? = null) = Sch.Integer(description)

fun num(description: String? = null) = Sch.Num(description)

fun bool(description: String? = null) = Sch.Bool(description)

fun arr(items: Sch, description: String? = null) = Sch.Arr(items, description)

fun obj(vararg props: Pair<String, Sch>, description: String? = null) = Sch.Obj(props.toList(), description)
