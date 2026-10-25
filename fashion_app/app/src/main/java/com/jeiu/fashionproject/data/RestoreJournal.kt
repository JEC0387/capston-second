package com.jeiu.fashionproject.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Redo journal: durable original + replacement before the first live preferences write.
 * Startup completes an interrupted install before any store or sync worker is loaded.
 * Original preferences (including the unsent queue) stay in noBackupFilesDir.
 */
internal object RestoreJournal {
    private fun journal(context: Context) = AtomicFile(File(context.noBackupFilesDir, "restore-journal.json"))

    private fun encode(values: Map<String, *>): JSONObject = JSONObject().apply {
        values.forEach { (key, value) ->
            val type = when (value) {
                is String -> "string"; is Boolean -> "boolean"; is Int -> "int"
                is Long -> "long"; is Float -> "float"; is Set<*> -> "set"
                else -> error("Unsupported preference type")
            }
            put(key, JSONObject().put("type", type).put("value", if (value is Set<*>) JSONArray(value) else value))
        }
    }

    private fun install(prefs: SharedPreferences, values: JSONObject) {
        val edit = prefs.edit().clear()
        values.keys().forEach { key ->
            val entry = values.getJSONObject(key)
            when (entry.getString("type")) {
                "string" -> edit.putString(key, entry.getString("value"))
                "boolean" -> edit.putBoolean(key, entry.getBoolean("value"))
                "int" -> edit.putInt(key, entry.getInt("value"))
                "long" -> edit.putLong(key, entry.getLong("value"))
                "float" -> edit.putFloat(key, entry.getDouble("value").toFloat())
                "set" -> edit.putStringSet(key, entry.getJSONArray("value").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() })
                else -> error("Invalid restore journal")
            }
        }
        check(edit.commit()) { "Restore storage unavailable" }
    }

    private fun save(file: AtomicFile, value: JSONObject) {
        val stream = file.startWrite()
        try {
            stream.write(value.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Throwable) { file.failWrite(stream); throw error }
    }

    /** Writers only touch staging preferences; the journal includes all changed stores. */
    @Synchronized fun stage(context: Context, write: (Context) -> Unit) {
        check(!journal(context).baseFile.exists()) { "Restore restart required" }
        val names = linkedSetOf<String>()
        val old = JSONObject()
        val staging = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val prefs = context.getSharedPreferences("restore_stage_$name", mode)
                if (names.add(name)) {
                    val original = encode(context.getSharedPreferences(name, mode).all)
                    old.put(name, original)
                    install(prefs, original)
                }
                return prefs
            }
        }
        try {
            write(staging)
            val replacement = JSONObject()
            names.forEach { replacement.put(it, encode(context.getSharedPreferences("restore_stage_$it", Context.MODE_PRIVATE).all)) }
            val record = JSONObject().put("version", 1).put("old", old).put("new", replacement)
            save(AtomicFile(File(context.noBackupFilesDir, "restore-original-${System.currentTimeMillis()}.json")), record)
            save(journal(context), record)
            recover(context)
        } finally {
            names.forEach { context.getSharedPreferences("restore_stage_$it", Context.MODE_PRIVATE).edit().clear().commit() }
        }
    }

    @Synchronized fun recover(context: Context): Boolean {
        val file = journal(context)
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return false
        val record = JSONObject(file.openRead().bufferedReader().use { it.readText() })
        check(record.getInt("version") == 1)
        val replacement = record.getJSONObject("new")
        replacement.keys().forEach { name -> install(context.getSharedPreferences(name, Context.MODE_PRIVATE), replacement.getJSONObject(name)) }
        return true
    }

    fun finish(context: Context) { journal(context).delete() }
}
