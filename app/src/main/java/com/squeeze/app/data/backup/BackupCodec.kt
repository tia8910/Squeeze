package com.squeeze.app.data.backup

import androidx.room.withTransaction
import com.squeeze.app.data.db.ActivitySessionEntity
import com.squeeze.app.data.db.DefinitionLabelEntity
import com.squeeze.app.data.db.LoggedSetEntity
import com.squeeze.app.data.db.MeasurementEntity
import com.squeeze.app.data.db.MesocycleEntity
import com.squeeze.app.data.db.PhysiqueReadEntity
import com.squeeze.app.data.db.ProfileEntity
import com.squeeze.app.data.db.SqueezeDatabase
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every table as one JSON document, and back.
 *
 * **Data, never photographs.** Measurements keep their numbers but lose their photo reference:
 * the photographs stay on the phone, which is the promise the user chose when they turned
 * backup on, and a restored reference to a file that is not there would only break the
 * detail screen.
 *
 * Restoring replaces everything in one transaction, so a backup that fails to parse halfway
 * leaves the database as it was rather than half-overwritten.
 */
@Singleton
class BackupCodec @Inject constructor(private val db: SqueezeDatabase) {

    suspend fun export(): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("exportedAtMs", System.currentTimeMillis())
        db.profileDao().get()?.let { root.put("profile", it.toJson()) }
        root.put("measurements", JSONArray(db.measurementDao().since(Long.MIN_VALUE).map { it.toJson() }))
        root.put("sets", JSONArray(db.workoutDao().since(Long.MIN_VALUE).map { it.toJson() }))
        root.put("sessions", JSONArray(db.activityDao().since(Long.MIN_VALUE).map { it.toJson() }))
        root.put("physique", JSONArray(db.physiqueDao().all().map { it.toJson() }))
        root.put("mesocycles", JSONArray(db.mesocycleDao().all().map { it.toJson() }))
        root.put("labels", JSONArray(db.definitionLabelDao().all().map { it.toJson() }))
        return root.toString()
    }

    /** True when there is anything worth backing up: no profile means nothing to keep yet. */
    suspend fun hasData(): Boolean = db.profileDao().get() != null

    suspend fun restore(json: String) {
        val root = JSONObject(json)
        require(root.optInt("format") in 1..FORMAT) { "This backup is from a newer version of the app, update it first." }
        // Parsed in full before anything is deleted.
        val profile = root.optJSONObject("profile")?.toProfile()
        val measurements = root.objects("measurements").map { it.toMeasurement() }
        val sets = root.objects("sets").map { it.toSet() }
        val sessions = root.objects("sessions").map { it.toSession() }
        val physique = root.objects("physique").map { it.toPhysique() }
        val mesocycles = root.objects("mesocycles").map { it.toMesocycle() }
        val labels = root.objects("labels").map { it.toLabel() }

        db.withTransaction {
            db.measurementDao().deleteAll()
            db.workoutDao().deleteAll()
            db.activityDao().deleteAll()
            db.physiqueDao().deleteAll()
            db.mesocycleDao().deleteAll()
            db.definitionLabelDao().deleteAll()
            db.profileDao().deleteAll()
            measurements.forEach { db.measurementDao().insert(it) }
            sets.forEach { db.workoutDao().insert(it) }
            sessions.forEach { db.activityDao().insert(it) }
            physique.forEach { db.physiqueDao().upsert(it) }
            mesocycles.forEach { db.mesocycleDao().insert(it) }
            if (labels.isNotEmpty()) db.definitionLabelDao().upsert(labels)
            // Last, because the app leaves onboarding the moment a profile exists.
            profile?.let { db.profileDao().upsert(it) }
        }
    }

    private companion object {
        const val FORMAT = 1
    }
}

// ── Entities ⇄ JSON ─────────────────────────────────────────────────────────────────────

private fun JSONObject.n(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)
private fun JSONObject.d(key: String): Double? = if (isNull(key)) null else getDouble(key)
private fun JSONObject.i(key: String): Int? = if (isNull(key)) null else getInt(key)
private fun JSONObject.l(key: String): Long? = if (isNull(key)) null else getLong(key)
private fun JSONObject.s(key: String): String? = if (isNull(key)) null else getString(key)
private fun JSONObject.objects(key: String): List<JSONObject> =
    optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()

private fun ProfileEntity.toJson() = JSONObject()
    .put("heightCm", heightCm).put("birthYear", birthYear).put("sex", sex)
    .put("trainingAge", trainingAge).put("goal", goal).put("unitSystem", unitSystem)
    .n("targetBodyFatPercent", targetBodyFatPercent).n("targetWeightKg", targetWeightKg)
    .n("targetEpochDay", targetEpochDay).n("trainingDaysPerWeek", trainingDaysPerWeek)
    .n("disciplines", disciplines).n("favouriteFoods", favouriteFoods).n("trainingTime", trainingTime)

private fun JSONObject.toProfile() = ProfileEntity(
    heightCm = getDouble("heightCm"), birthYear = getInt("birthYear"), sex = getString("sex"),
    trainingAge = getString("trainingAge"), goal = getString("goal"), unitSystem = getString("unitSystem"),
    targetBodyFatPercent = d("targetBodyFatPercent"), targetWeightKg = d("targetWeightKg"),
    targetEpochDay = l("targetEpochDay"), trainingDaysPerWeek = i("trainingDaysPerWeek"),
    disciplines = s("disciplines"), favouriteFoods = s("favouriteFoods"), trainingTime = s("trainingTime"),
)

private fun MeasurementEntity.toJson() = JSONObject()
    .put("id", id).put("epochDay", epochDay).put("source", source)
    .n("weightKg", weightKg).n("neckCm", neckCm).n("waistCm", waistCm).n("hipCm", hipCm)
    .n("chestCm", chestCm).n("thighCm", thighCm).n("armCm", armCm).n("calfCm", calfCm)
    .n("chestMm", chestMm).n("abdomenMm", abdomenMm).n("thighMm", thighMm).n("tricepsMm", tricepsMm)
    .n("suprailiacMm", suprailiacMm).n("referenceBodyFatPercent", referenceBodyFatPercent).n("note", note)
    .n("visualBodyFatPercent", visualBodyFatPercent).n("shapeBodyFatPercent", shapeBodyFatPercent)
    .n("abdominalBodyFatPercent", abdominalBodyFatPercent).n("shapeStandardErrorPercent", shapeStandardErrorPercent)

private fun JSONObject.toMeasurement() = MeasurementEntity(
    id = getLong("id"), epochDay = getLong("epochDay"), source = getString("source"),
    weightKg = d("weightKg"), neckCm = d("neckCm"), waistCm = d("waistCm"), hipCm = d("hipCm"),
    chestCm = d("chestCm"), thighCm = d("thighCm"), armCm = d("armCm"), calfCm = d("calfCm"),
    chestMm = d("chestMm"), abdomenMm = d("abdomenMm"), thighMm = d("thighMm"), tricepsMm = d("tricepsMm"),
    suprailiacMm = d("suprailiacMm"), referenceBodyFatPercent = d("referenceBodyFatPercent"), note = s("note"),
    photoId = null,
    visualBodyFatPercent = d("visualBodyFatPercent"), shapeBodyFatPercent = d("shapeBodyFatPercent"),
    abdominalBodyFatPercent = d("abdominalBodyFatPercent"), shapeStandardErrorPercent = d("shapeStandardErrorPercent"),
)

private fun LoggedSetEntity.toJson() = JSONObject()
    .put("id", id).put("epochDay", epochDay).put("exerciseName", exerciseName).put("muscleGroup", muscleGroup)
    .put("weightKg", weightKg).put("reps", reps).n("rir", rir).n("programWeekIndex", programWeekIndex)

private fun JSONObject.toSet() = LoggedSetEntity(
    id = getLong("id"), epochDay = getLong("epochDay"), exerciseName = getString("exerciseName"),
    muscleGroup = getString("muscleGroup"), weightKg = getDouble("weightKg"), reps = getInt("reps"),
    rir = i("rir"), programWeekIndex = i("programWeekIndex"),
)

private fun ActivitySessionEntity.toJson() = JSONObject()
    .put("id", id).put("epochDay", epochDay).put("sport", sport).put("title", title).put("minutes", minutes)
    .put("intensity", intensity).n("distanceKm", distanceKm).put("netKcal", netKcal)

private fun JSONObject.toSession() = ActivitySessionEntity(
    id = getLong("id"), epochDay = getLong("epochDay"), sport = getString("sport"), title = getString("title"),
    minutes = getInt("minutes"), intensity = getString("intensity"), distanceKm = d("distanceKm"), netKcal = getInt("netKcal"),
)

private fun PhysiqueReadEntity.toJson() = JSONObject().put("epochDay", epochDay).put("goal", goal).put("scores", scores)

private fun JSONObject.toPhysique() = PhysiqueReadEntity(getLong("epochDay"), getString("goal"), getString("scores"))

private fun MesocycleEntity.toJson() = JSONObject()
    .put("id", id).put("createdEpochDay", createdEpochDay).put("name", name).put("goal", goal)
    .put("payload", payload).put("isActive", isActive)

private fun JSONObject.toMesocycle() = MesocycleEntity(
    id = getLong("id"), createdEpochDay = getLong("createdEpochDay"), name = getString("name"),
    goal = getString("goal"), payload = getString("payload"), isActive = getBoolean("isActive"),
)

private fun DefinitionLabelEntity.toJson() = JSONObject()
    .put("photoHash", photoHash).put("region", region).put("capturedEpochDay", capturedEpochDay)
    .put("visible", visible).put("unusable", unusable).put("labelledEpochDay", labelledEpochDay)

private fun JSONObject.toLabel() = DefinitionLabelEntity(
    photoHash = getString("photoHash"), region = getString("region"), capturedEpochDay = getLong("capturedEpochDay"),
    visible = getBoolean("visible"), unusable = getBoolean("unusable"), labelledEpochDay = getLong("labelledEpochDay"),
)
