package com.squeeze.app.scan

import android.content.Context
import android.graphics.Bitmap
import com.squeeze.core.workout.EquipmentCatalog
import com.squeeze.core.workout.EquipmentMatcher
import com.squeeze.core.workout.MachineGuide
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** What the camera saw: the likeliest machines, and whether the model is sure. */
data class MachineMatch(
    val candidates: List<Pair<MachineGuide, Double>>,
    val confident: Boolean,
    /** True when the model thinks the photo shows no gym equipment at all. */
    val noMachine: Boolean,
)

/**
 * Names the gym machine in a photograph, on the phone, with the same vision-language model
 * that reads the body — no new model, no upload. See [EquipmentMatcher].
 */
@Singleton
class EquipmentRecognizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clip: ClipAppearance,
) {
    private var prompts: Map<String, List<DoubleArray>>? = null

    /** Null when the model cannot run on this device. */
    fun recognise(photo: Bitmap): MachineMatch? {
        val embedding = clip.embedImage(photo) ?: return null
        val known = prompts ?: runCatching { load() }.getOrNull()?.also { prompts = it } ?: return null
        val ranked = EquipmentMatcher.rank(embedding, known)
        if (ranked.isEmpty()) return null

        val noMachine = ranked.first().first == NONE
        val machines = ranked.mapNotNull { (id, p) -> EquipmentCatalog.byId(id)?.let { it to p } }.take(4)
        return MachineMatch(
            candidates = machines,
            confident = !noMachine && (machines.firstOrNull()?.second ?: 0.0) >= EquipmentMatcher.CONFIDENT,
            noMachine = noMachine,
        )
    }

    private fun load(): Map<String, List<DoubleArray>> {
        val json = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        val machines = JSONObject(json).getJSONObject("machines")
        return machines.keys().asSequence().associateWith { id ->
            val rows = machines.getJSONArray(id)
            (0 until rows.length()).map { r ->
                val values = rows.getJSONArray(r)
                DoubleArray(values.length()) { values.getDouble(it) }
            }
        }
    }

    private companion object {
        const val ASSET = "clip_equipment.json"
        const val NONE = "__none__"
    }
}
