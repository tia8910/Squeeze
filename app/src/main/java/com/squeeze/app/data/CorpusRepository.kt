package com.squeeze.app.data

import com.squeeze.app.data.db.DefinitionLabelDao
import com.squeeze.app.data.db.DefinitionLabelEntity
import com.squeeze.app.data.photo.ScanPhotoStore
import com.squeeze.core.corpus.DefinitionRegion
import com.squeeze.core.corpus.LabelFile
import com.squeeze.core.corpus.PhotoLabels
import com.squeeze.core.corpus.RegionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The labelled-photo corpus, as it accumulates on the device.
 *
 * This is the bottleneck for everything else. An on-device classifier that says whether
 * abdominal separation is visible cannot be built, tuned or even honestly evaluated without
 * labelled photographs, and no such set exists — which is why this app's one attempt at a
 * definition signal shipped a score that ran 5.76 at eight per cent body fat and 6.03 at
 * twenty. Nobody could have caught that, because nothing here could score a candidate.
 *
 * The design goal is that the corpus **grows as a by-product of using the app** rather than
 * as a project someone has to sit down and do. Scans already store their photograph; all that
 * is missing is being asked three questions about one.
 *
 * **Labels leave, photographs do not.** [export] produces a text file of judgements keyed by
 * the hash of the image bytes. That file describes bodies without containing any, so it can be
 * committed, reviewed in a pull request and replayed in CI — while the photographs stay in the
 * app's encrypted storage on the device that took them.
 */
@Singleton
class CorpusRepository @Inject constructor(
    private val labelDao: DefinitionLabelDao,
    private val photoStore: ScanPhotoStore,
) {

    /** How many photographs have been judged. The corpus's real size. */
    fun observeSize(): Flow<Int> = labelDao.observePhotoCount()

    /**
     * The stable identity of a stored photograph.
     *
     * Hashed from the decrypted pixels rather than from the file on disk, because the file is
     * encrypted under a per-device key with a fresh IV each write — two devices holding the
     * same photograph produce different ciphertext, and even re-saving it on one device would.
     * A hash that changes when nothing about the image did would make every label a dangling
     * reference the moment anything touched storage.
     *
     * @return lowercase hex SHA-256, or null when the photograph cannot be read
     */
    suspend fun hashOf(photoId: String): String? = withContext(Dispatchers.IO) {
        photoStore.pixelHash(photoId)
    }

    /** What has already been judged about this photograph, so an edit shows current answers. */
    suspend fun labelsFor(photoHash: String): List<RegionLabel> =
        labelDao.forPhoto(photoHash).map {
            RegionLabel(
                region = DefinitionRegion.valueOf(it.region),
                visible = it.visible,
                unusable = it.unusable,
            )
        }

    /**
     * Records judgements about one photograph.
     *
     * Upserts, so labelling the same photograph again replaces the earlier answers. A second
     * judgement is someone changing their mind, and the later one is the one to keep —
     * ignoring it would silently discard a correction, which is the worst thing that can
     * happen to a set whose whole value is that a person stands behind every row.
     */
    suspend fun record(
        photoHash: String,
        capturedEpochDay: Long,
        labels: List<RegionLabel>,
    ) {
        if (labels.isEmpty()) return
        val today = LocalDate.now().toEpochDay()

        labelDao.upsert(
            labels.map { label ->
                DefinitionLabelEntity(
                    photoHash = photoHash,
                    region = label.region.name,
                    capturedEpochDay = capturedEpochDay,
                    visible = label.visible,
                    unusable = label.unusable,
                    labelledEpochDay = today,
                )
            },
        )
    }

    /**
     * The whole corpus as a labels file; see [LabelFile].
     *
     * Grouped by photograph so each becomes one line, and returned as text rather than written
     * anywhere: where it goes is the caller's decision, and this class has no business
     * choosing a destination for something the user has to consent to sharing.
     */
    suspend fun export(): String = withContext(Dispatchers.IO) {
        val rows = labelDao.all()

        LabelFile.write(
            rows.groupBy { it.photoHash }.map { (hash, forPhoto) ->
                PhotoLabels(
                    photoHash = hash,
                    capturedEpochDay = forPhoto.first().capturedEpochDay,
                    regions = forPhoto.map {
                        RegionLabel(
                            region = DefinitionRegion.valueOf(it.region),
                            visible = it.visible,
                            unusable = it.unusable,
                        )
                    },
                )
            },
        )
    }
}
