import java.net.URI
import java.security.MessageDigest

/**
 * Downloads the on-device vision models into the APK's assets at build time.
 *
 * The models are not committed. They total roughly 6 MB of binary that would sit in every
 * clone and every diff forever, and they are immutable published artefacts, so fetching
 * them is cheaper than versioning them.
 *
 * Two properties make that safe rather than reckless:
 *
 *  - **Version-pinned URLs.** The `latest` alias Google also publishes would let a model
 *    change under a build that has not changed, which is exactly the kind of drift that
 *    makes a measurement app's output move for no visible reason.
 *  - **Checksum verification.** A download is rejected unless it matches the expected
 *    SHA-256, so a corrupted transfer or a substituted file fails the build rather than
 *    shipping.
 *
 * Existing files with a correct checksum are left alone, so this costs nothing after the
 * first build and works offline thereafter.
 */

data class VisionModel(val fileName: String, val url: String, val sha256: String)

val visionModels = listOf(
    VisionModel(
        fileName = "pose_landmarker_lite.task",
        url = "https://storage.googleapis.com/mediapipe-models/pose_landmarker/" +
            "pose_landmarker_lite/float16/1/pose_landmarker_lite.task",
        sha256 = "59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a",
    ),
    VisionModel(
        fileName = "selfie_segmenter.tflite",
        url = "https://storage.googleapis.com/mediapipe-models/image_segmenter/" +
            "selfie_segmenter/float16/1/selfie_segmenter.tflite",
        sha256 = "191ac9529ae506ee0beefa6b2c945a172dab9d07d1e802a290a4e4038226658b",
    ),
)

fun sha256Of(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { stream ->
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val read = stream.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val downloadVisionModels by tasks.registering {
    description = "Downloads MediaPipe vision models into src/main/assets."
    group = "build setup"

    val assetsDir = layout.projectDirectory.dir("src/main/assets").asFile
    outputs.files(visionModels.map { File(assetsDir, it.fileName) })

    doLast {
        assetsDir.mkdirs()

        for (model in visionModels) {
            val target = File(assetsDir, model.fileName)

            if (target.exists() && sha256Of(target) == model.sha256) {
                logger.lifecycle("${model.fileName}: already present and verified")
                continue
            }

            logger.lifecycle("Downloading ${model.fileName}")
            // Written to a temporary file first so an interrupted download cannot leave a
            // truncated model in assets that a later build would try to load.
            val temp = File(assetsDir, "${model.fileName}.part")
            URI(model.url).toURL().openStream().use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }

            val actual = sha256Of(temp)
            if (actual != model.sha256) {
                temp.delete()
                throw GradleException(
                    "Checksum mismatch for ${model.fileName}.\n" +
                        "  expected ${model.sha256}\n" +
                        "  actual   $actual",
                )
            }

            temp.renameTo(target)
            logger.lifecycle("${model.fileName}: downloaded and verified")
        }
    }
}

// Models must exist before assets are packaged, for every variant.
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }
    .configureEach { dependsOn(downloadVisionModels) }
