package com.pastq.cbt.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One valid question read from an import file. Live tests use four options (A-D). */
data class ImportedQuestion(
    val question: String,
    val section: String,
    val a: String,
    val b: String,
    val c: String,
    val d: String,
    val answer: Char,
    val explanation: String,
    val subject: String,
)

/** Everything found in an import file: the good questions, optional test settings, and a list of rows that were skipped. */
data class LiveImport(
    val title: String?,
    val minutes: Int?,
    val questions: List<ImportedQuestion>,
    val problems: List<String>,
)

/**
 * Reads live-test questions from a JSON or CSV file the admin picked.
 * Bad rows are skipped and reported; they never block the good ones.
 * The format is described in docs/LIVE_TEST_IMPORT.md and in the "File format" help inside the app.
 */
object LiveImportParser {
    const val MAX_BYTES = 1_000_000

    private val aliases = mapOf(
        "question" to listOf("question", "q", "text", "stem"),
        "a" to listOf("a", "option_a", "optiona", "opt_a"),
        "b" to listOf("b", "option_b", "optionb", "opt_b"),
        "c" to listOf("c", "option_c", "optionc", "opt_c"),
        "d" to listOf("d", "option_d", "optiond", "opt_d"),
        "e" to listOf("e", "option_e", "optione", "opt_e"),
        "answer" to listOf("answer", "correct", "correct_answer", "key", "answer_key"),
        "explanation" to listOf("explanation", "solution", "reason"),
        "section" to listOf("section", "passage", "instructions"),
        "subject" to listOf("subject"),
    )

    fun parse(raw: String): LiveImport {
        val text = raw.removePrefix("﻿").trim()
        if (text.isEmpty()) throw IllegalArgumentException("The file is empty.")
        return if (text.startsWith("[") || text.startsWith("{")) fromJson(text) else fromCsv(text)
    }

    // ---------- JSON ----------

    private fun prim(e: JsonElement?): String? = (e as? JsonPrimitive)?.contentOrNull

    private fun fromJson(text: String): LiveImport {
        val root = try {
            Json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("That is not valid JSON: ${e.message.orEmpty().take(120)}")
        }
        var title: String? = null
        var minutes: Int? = null
        var defaultSubject = ""
        val problems = mutableListOf<String>()
        val list: JsonArray = when (root) {
            is JsonArray -> root
            is JsonObject -> {
                title = prim(root["title"])?.trim()?.take(80)?.takeIf { it.isNotEmpty() }
                defaultSubject = prim(root["subject"])?.trim().orEmpty()
                val m = (prim(root["minutes"]) ?: prim(root["duration_minutes"]) ?: prim(root["duration"]))
                    ?.trim()?.toDoubleOrNull()?.toInt()
                if (m != null) {
                    if (m in 1..120) minutes = m else problems.add("Time ignored: it must be 1 to 120 minutes.")
                }
                (root["questions"] ?: root["items"]) as? JsonArray
                    ?: throw IllegalArgumentException("Put the questions in a \"questions\" list.")
            }
            else -> throw IllegalArgumentException("The file must be a list of questions or an object with a \"questions\" list.")
        }
        val out = mutableListOf<ImportedQuestion>()
        list.forEachIndexed { i, el ->
            val obj = el as? JsonObject
            if (obj == null) {
                problems.add("Question ${i + 1}: not a question object.")
                return@forEachIndexed
            }
            val rec = HashMap<String, String>()
            for ((k, v) in obj) {
                val key = normKey(k)
                when (v) {
                    is JsonPrimitive -> v.contentOrNull?.let { rec[key] = it }
                    is JsonArray -> if (key == "options" || key == "choices") {
                        v.forEachIndexed { j, o -> prim(o)?.let { rec["abcde".getOrNull(j)?.toString() ?: "opt$j"] = it } }
                    }
                    else -> {}
                }
            }
            val r = build(rec, defaultSubject)
            if (r.q != null) out.add(r.q) else problems.add("Question ${i + 1}: ${r.problem}")
        }
        return LiveImport(title, minutes, out, problems)
    }

    // ---------- CSV ----------

    private fun fromCsv(text: String): LiveImport {
        val table = readTable(text, detectDelimiter(text))
        if (table.size < 2) throw IllegalArgumentException("The file needs a header row and at least one question.")
        val header = table[0].map { normKey(it) }
        if (aliases.getValue("question").none { it in header }) {
            throw IllegalArgumentException("The header row needs a \"question\" column (plus a, b, c, d and answer).")
        }
        val out = mutableListOf<ImportedQuestion>()
        val problems = mutableListOf<String>()
        table.drop(1).forEachIndexed { idx, row ->
            if (row.all { it.isBlank() }) return@forEachIndexed
            val rec = HashMap<String, String>()
            header.forEachIndexed { c, name -> rec[name] = row.getOrElse(c) { "" } }
            val r = build(rec, "")
            if (r.q != null) out.add(r.q) else problems.add("Row ${idx + 2}: ${r.problem}")
        }
        return LiveImport(null, null, out, problems)
    }

    private fun detectDelimiter(text: String): Char {
        val first = text.lineSequence().first()
        return listOf(',', ';', '\t').maxByOrNull { d -> first.count { it == d } }
            ?.takeIf { d -> first.any { it == d } } ?: ','
    }

    /** Minimal RFC 4180 reader: quoted fields, escaped quotes, delimiters and line breaks inside quotes. */
    private fun readTable(text: String, delim: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        var field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
            } else {
                when {
                    c == '"' -> inQuotes = true
                    c == delim -> { row.add(field.toString()); field = StringBuilder() }
                    c == '\r' -> {}
                    c == '\n' -> { row.add(field.toString()); field = StringBuilder(); rows.add(row); row = mutableListOf() }
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows
    }

    // ---------- shared ----------

    private class Built(val q: ImportedQuestion?, val problem: String?)

    private fun normKey(k: String) = k.trim().lowercase().replace(Regex("[\\s-]+"), "_")

    private fun pick(rec: Map<String, String>, name: String): String =
        aliases.getValue(name).firstNotNullOfOrNull { rec[it]?.trim()?.takeIf { s -> s.isNotEmpty() } }.orEmpty()

    private fun build(rec: Map<String, String>, defaultSubject: String): Built {
        val question = pick(rec, "question").take(3000)
        if (question.isEmpty()) return Built(null, "the question is empty.")
        val opts = listOf("a", "b", "c", "d").map { pick(rec, it).take(800) }
        val missing = opts.indexOfFirst { it.isEmpty() }
        if (missing >= 0) return Built(null, "option ${'A' + missing} is empty.")
        val rawAnswer = pick(rec, "answer")
        if (rawAnswer.isEmpty()) return Built(null, "no answer given.")
        val answer = parseAnswer(rawAnswer, opts)
            ?: return Built(null, "answer \"${rawAnswer.take(30)}\" is not A, B, C or D (live tests use four options).")
        return Built(
            ImportedQuestion(
                question = question,
                section = pick(rec, "section").take(6000),
                a = opts[0], b = opts[1], c = opts[2], d = opts[3],
                answer = answer,
                explanation = pick(rec, "explanation").take(1200),
                subject = pick(rec, "subject").ifEmpty { defaultSubject }.take(60),
            ),
            null,
        )
    }

    /** Accepts a/b/c/d (any case, also "B)" or "Option B"), 1-4, or the exact text of one option. */
    private fun parseAnswer(raw: String, opts: List<String>): Char? {
        val s = raw.trim().lowercase().removePrefix("option").trim().trimEnd(')', '.', ':').trim()
        if (s.length == 1 && s[0] in 'a'..'d') return s[0]
        s.toIntOrNull()?.let { if (it in 1..4) return 'a' + (it - 1) }
        val idx = opts.indexOfFirst { it.trim().equals(raw.trim(), ignoreCase = true) }
        return if (idx >= 0) 'a' + idx else null
    }

    // ---------- sample files (shown in the app and saved on request) ----------

    val SAMPLE_JSON = """
        {
          "title": "Mock Test 1",
          "minutes": 40,
          "subject": "Biology",
          "questions": [
            {
              "question": "Which organelle is the site of photosynthesis?",
              "a": "Mitochondrion", "b": "Chloroplast", "c": "Ribosome", "d": "Nucleus",
              "answer": "b",
              "explanation": "Chloroplasts contain chlorophyll."
            },
            {
              "subject": "Mathematics",
              "question": "Solve 2x + 3 = 11",
              "options": ["x = 3", "x = 4", "x = 5", "x = 7"],
              "answer": "B"
            },
            {
              "question": "The <u>underlined</u> word is a noun. Which word class is <b>goat</b>?",
              "section": "Read the sentence and answer.",
              "a": "Verb", "b": "Adjective", "c": "Noun", "d": "Adverb",
              "answer": "c"
            }
          ]
        }
    """.trimIndent()

    val SAMPLE_CSV = """
        subject,question,a,b,c,d,answer,explanation,section
        Biology,Which organelle is the site of photosynthesis?,Mitochondrion,Chloroplast,Ribosome,Nucleus,B,Chloroplasts contain chlorophyll.,
        Mathematics,Solve 2x + 3 = 11,x = 3,x = 4,x = 5,x = 7,B,,
        English,"Which word is a noun, <b>goat</b> or run?",run,quickly,goat,under,C,"A noun names a thing, so goat.",Read the question carefully.
    """.trimIndent()
}
