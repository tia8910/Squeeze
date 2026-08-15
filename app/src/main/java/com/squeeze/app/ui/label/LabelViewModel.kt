package com.squeeze.app.ui.label

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squeeze.app.data.CorpusRepository
import com.squeeze.app.data.db.MeasurementDao
import com.squeeze.app.data.photo.ScanPhotoStore
import com.squeeze.core.corpus.DefinitionRegion
import com.squeeze.core.corpus.RegionLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** One photograph waiting to be judged. */
data class LabelSubject(
    val photoId: String,
    val photoHash: String,
    val capturedEpochDay: Long,
    val bitmap: Bitmap,
)

/**
 * @param answers the judgement so far. Absent means unanswered, which is distinct from
 *   answered "no" — a screen that cannot tell those apart would let a half-finished
 *   photograph be saved as a set of noes.
 */
data class LabelUiState(
    val subject: LabelSubject? = null,
    val answers: Map<DefinitionRegion, Boolean> = emptyMap(),
    val unusable: Set<DefinitionRegion> = emptySet(),
    val remaining: Int = 0,
    val labelled: Int = 0,
    val loading: Boolean = true,
    val exported: String? = null,
) {
    /** Every region either answered or marked unusable. */
    val complete: Boolean
        get() = DefinitionRegion.entries.all { it in answers || it in unusable }
}

/**
 * Judging stored scan photographs, one at a time.
 *
 * The queue is **unlabelled photographs, oldest first**, and it deliberately does not offer a
 * way to pick which one to judge. Choosing invites judging the flattering ones, and a corpus
 * assembled from photographs someone was pleased with teaches a classifier that definition is
 * always visible. Oldest first also means the set fills evenly across a body's changes rather
 * than clustering on whatever week the labelling happened.
 */
@HiltViewModel
class LabelViewModel @Inject constructor(
    private val measurementDao: MeasurementDao,
    private val photoStore: ScanPhotoStore,
    private val corpus: CorpusRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LabelUiState())
    val state: StateFlow<LabelUiState> = _state.asStateFlow()

    /** Photographs already judged this session, so the queue advances without a re-query. */
    private val done = mutableSetOf<String>()

    init {
        advance()
    }

    fun answer(region: DefinitionRegion, visible: Boolean) {
        _state.value = _state.value.copy(
            answers = _state.value.answers + (region to visible),
            unusable = _state.value.unusable - region,
            exported = null,
        )
    }

    /**
     * Marks a region unanswerable in this photograph.
     *
     * Recorded rather than skipped. How many photographs cannot answer the question is itself
     * a measurement — of the capture guidance, not of the body — and dropping them would hide
     * a framing problem behind a smaller corpus.
     */
    fun cannotTell(region: DefinitionRegion) {
        _state.value = _state.value.copy(
            answers = _state.value.answers - region,
            unusable = _state.value.unusable + region,
            exported = null,
        )
    }

    /** Stores the judgement and moves to the next photograph. */
    fun saveAndNext() {
        val current = _state.value
        val subject = current.subject ?: return
        if (!current.complete) return

        viewModelScope.launch {
            corpus.record(
                photoHash = subject.photoHash,
                capturedEpochDay = subject.capturedEpochDay,
                labels = DefinitionRegion.entries.map { region ->
                    RegionLabel(
                        region = region,
                        visible = current.answers[region] ?: false,
                        unusable = region in current.unusable,
                    )
                },
            )

            done += subject.photoId
            _state.value = current.copy(labelled = current.labelled + 1)
            advance()
        }
    }

    /** Skips without recording, for a photograph the labeller does not want to judge at all. */
    fun skip() {
        _state.value.subject?.let { done += it.photoId }
        advance()
    }

    fun export() {
        viewModelScope.launch {
            _state.value = _state.value.copy(exported = corpus.export())
        }
    }

    private fun advance() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, subject = null)

            val candidates = withContext(Dispatchers.IO) {
                measurementDao.since(0L)
                    .filter { it.photoId != null && it.photoId !in done }
                    .sortedBy { it.epochDay }
            }

            // The first one that can actually be shown. A row can reference a photograph that
            // failed to decrypt or was removed from storage, and a queue that stopped on the
            // first of those would look like an empty corpus.
            val next = withContext(Dispatchers.IO) {
                candidates.firstNotNullOfOrNull { entry ->
                    val id = entry.photoId ?: return@firstNotNullOfOrNull null
                    val hash = corpus.hashOf(id) ?: return@firstNotNullOfOrNull null
                    val bitmap = photoStore.load(id) ?: return@firstNotNullOfOrNull null

                    LabelSubject(
                        photoId = id,
                        photoHash = hash,
                        capturedEpochDay = entry.epochDay,
                        bitmap = bitmap,
                    )
                }
            }

            // Pre-filled with what was said last time, so opening a judged photograph shows
            // the current answers rather than a blank form inviting a contradictory one.
            val existing = next?.let { corpus.labelsFor(it.photoHash) }.orEmpty()

            _state.value = _state.value.copy(
                subject = next,
                answers = existing.filterNot { it.unusable }
                    .associate { it.region to it.visible },
                unusable = existing.filter { it.unusable }.map { it.region }.toSet(),
                remaining = candidates.size,
                loading = false,
            )
        }
    }
}
