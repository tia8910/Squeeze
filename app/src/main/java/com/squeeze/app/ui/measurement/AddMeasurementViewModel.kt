package com.squeeze.app.ui.measurement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squeeze.app.data.db.MeasurementDao
import com.squeeze.app.data.db.MeasurementEntity
import com.squeeze.core.model.MeasurementSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Fields the user can type into. Every one is optional and held as text rather than a
 * number, so a half-typed "18." is a valid intermediate state instead of a parse error
 * fighting the keyboard.
 */
data class MeasurementForm(
    val weightKg: String = "",
    val neckCm: String = "",
    val waistCm: String = "",
    val hipCm: String = "",
    val chestCm: String = "",
    val thighCm: String = "",
    val armCm: String = "",
    val calfCm: String = "",
    val referenceBodyFat: String = "",
    val note: String = "",
) {
    private fun value(text: String) = text.trim().toDoubleOrNull()

    /**
     * Whether this form carries anything worth storing.
     *
     * Saving an empty measurement would insert a row that contributes nothing to the trend
     * but still counts as a session, which quietly degrades the repeatability estimate.
     */
    val hasAnything: Boolean
        get() = listOf(
            weightKg, neckCm, waistCm, hipCm, chestCm, thighCm, armCm, calfCm, referenceBodyFat,
        ).any { value(it) != null }

    /**
     * Sites the Navy equation needs. Surfaced so the UI can tell the user *why* an entry
     * will not produce a body-fat number, rather than silently storing one that cannot.
     */
    val canEstimateBodyFat: Boolean
        get() = value(neckCm) != null && value(waistCm) != null

    fun toEntity(epochDay: Long): MeasurementEntity = MeasurementEntity(
        epochDay = epochDay,
        // A typed reference scan is the anchor for personal calibration, so it is recorded
        // as a reference rather than as an ordinary tape session.
        source = if (value(referenceBodyFat) != null) {
            MeasurementSource.REFERENCE_SCAN.name
        } else {
            MeasurementSource.TAPE.name
        },
        weightKg = value(weightKg),
        neckCm = value(neckCm),
        waistCm = value(waistCm),
        hipCm = value(hipCm),
        chestCm = value(chestCm),
        thighCm = value(thighCm),
        armCm = value(armCm),
        calfCm = value(calfCm),
        chestMm = null,
        abdomenMm = null,
        thighMm = null,
        tricepsMm = null,
        suprailiacMm = null,
        referenceBodyFatPercent = value(referenceBodyFat),
        note = note.trim().takeIf { it.isNotEmpty() },
    )
}

@HiltViewModel
class AddMeasurementViewModel @Inject constructor(
    private val measurementDao: MeasurementDao,
) : ViewModel() {

    private val _form = MutableStateFlow(MeasurementForm())
    val form: StateFlow<MeasurementForm> = _form.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    fun update(transform: (MeasurementForm) -> MeasurementForm) {
        _form.value = transform(_form.value)
    }

    fun save() {
        val form = _form.value
        if (!form.hasAnything) return

        viewModelScope.launch {
            measurementDao.insert(form.toEntity(LocalDate.now().toEpochDay()))
            _saved.value = true
        }
    }
}
