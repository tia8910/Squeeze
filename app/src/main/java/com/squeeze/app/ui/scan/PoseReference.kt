package com.squeeze.app.ui.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.squeeze.app.ui.theme.Brand
import com.squeeze.app.ui.theme.LocalIsDarkTheme

/**
 * What a usable scan photograph looks like, shown before the camera opens.
 *
 * Every wrong figure this app has produced was a pose or a framing problem rather than an
 * arithmetic one. An arm resting against the waist merged into the shoulder run and the app
 * reported 4.93%. A waistband inside the hip band gave 6.83%. A frame lying on its side gave
 * 5.0%. In each case the pipeline measured exactly what it was shown; what it was shown was
 * not the body.
 *
 * The written instructions for all of this already existed and were read past, which is the
 * ordinary fate of a paragraph standing between someone and a camera button. A picture of the
 * pose is not a nicer way of saying the same thing — it is the only form of it that gets
 * looked at, and copying a shape is a task people are good at in a way that following five
 * clauses is not.
 *
 * **Drawn rather than photographed, for two reasons.** A photograph of a real person is a
 * photograph of *a* body, and every reader who does not look like that person has to work out
 * which parts of it they are being asked to copy — a lean man demonstrating the pose teaches
 * leanness alongside the pose. And no reference photography ships in this APK; the physique
 * references are third-party imagery and the same rule applies here. A drawn figure has
 * neither problem: it is nobody, so the only thing it can be showing is the geometry.
 */
@Composable
fun PoseReference(step: ScanStep, modifier: Modifier = Modifier) {
    val dark = LocalIsDarkTheme.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (dark) Brand.DarkSunken else Brand.Sunken)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "STAND LIKE THIS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (dark) Brand.DarkBlue else Brand.Blue,
        )

        val photo = rememberPosePhoto(step)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (photo != null) {
                PosePanel(
                    photo = photo,
                    step = step,
                    modifier = Modifier.weight(1f),
                )
            } else {
                PoseFigure(
                    sideOn = step == ScanStep.SIDE,
                    modifier = Modifier.weight(1f).aspectRatio(0.62f),
                )
            }

            Column(
                modifier = Modifier.weight(1.55f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { PoseRules() }
        }

        Text(
            text = "Shoulders and hips both in shot, plain background, even light, " +
                "close-fitting clothing or none. Head to feet is optional — it adds " +
                "centimetres, and framing closer on your trunk reads your shape better.",
            style = MaterialTheme.typography.bodySmall,
            color = if (dark) Brand.DarkMuted else Brand.Muted,
        )
    }
}

/**
 * The pose photograph for this step, when one has shipped.
 *
 * Resolved by name at run time rather than referenced directly, so a build with no pose
 * photographs falls back to the drawn figure instead of failing to compile — the same
 * arrangement the physique references use, and for the same reason: the code should not have
 * to change when the artwork arrives.
 *
 * A photograph beats the drawing once one exists. The objection to a photograph was that it
 * shows *a* body, so a reader who looks nothing like the model has to work out which parts
 * they are meant to copy — but that objection is about a real person's photograph. A generated
 * figure is nobody in particular, which removes both that problem and the copyright one, and a
 * reader copying a pose from something that looks like a person will copy it more accurately
 * than from a blue silhouette.
 *
 * Expected names: `pose_front`, `pose_side`, `pose_back`.
 */
@Composable
private fun rememberPosePhoto(step: ScanStep): Int? {
    val context = LocalContext.current
    return remember(step) {
        // The per-view image if it shipped, otherwise a single sheet showing all three.
        //
        // Both are supported because cropping one supplied image into three is a chore that
        // stands between having the artwork and shipping it, and a step that can be skipped
        // is one worth removing. A sheet showing front, side and back at once is also not
        // worse for the reader: the pose is the same in all three, and seeing the set makes
        // the relationship between them obvious.
        val perView = when (step) {
            ScanStep.SIDE -> "pose_side"
            ScanStep.BACK -> "pose_back"
            else -> "pose_front"
        }

        listOf(perView, "pose_reference").firstNotNullOfOrNull { name ->
            context.resources
                .getIdentifier(name, "drawable", context.packageName)
                .takeIf { it != 0 }
        }
    }
}

/**
 * One panel of the pose sheet, cropped to the view this step is asking for.
 *
 * The supplied artwork is a contact sheet: three views across, two rows down. Shown whole it
 * did two things wrong — it displayed the side and back views to someone being asked for a
 * front photograph, and at full width it was tall enough to push the camera button off the
 * bottom of the screen. A reference that hides the control it is a reference for has cost more
 * than it gave.
 *
 * Cropped in the layout rather than by cutting the file, because the file is the artwork and
 * the app's needs will change again. The geometry comes from the painter's own intrinsic size,
 * so there are no magic numbers here and a re-exported sheet at a different resolution keeps
 * working: the image is laid out at three times the panel's width and aligned to the start,
 * which places one column of one row exactly inside the clip.
 *
 * The top row is used. Both rows show the same pose; the top one is framed head to feet, which
 * is what the instruction beside it describes.
 */
@Composable
private fun PosePanel(photo: Int, step: ScanStep, modifier: Modifier = Modifier) {
    val painter = painterResource(photo)
    val intrinsic = painter.intrinsicSize

    // Three columns, two rows. Falls back to the drawn figure's aspect when the painter cannot
    // report a size, which is the case for a vector with no fixed dimensions.
    val panelAspect = if (intrinsic.isSpecified && intrinsic.height > 0f) {
        (intrinsic.width / COLUMNS) / (intrinsic.height / ROWS)
    } else {
        0.62f
    }

    val column = when (step) {
        ScanStep.SIDE -> 1
        ScanStep.BACK -> 2
        else -> 0
    }

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(panelAspect)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        Image(
            painter = painter,
            contentDescription = "Someone standing in the pose to copy",
            contentScale = ContentScale.FillWidth,
            alignment = Alignment.TopStart,
            modifier = Modifier
                .requiredWidth(maxWidth * COLUMNS)
                .offset(x = -maxWidth * column),
        )
    }
}

/** The pose sheet's grid. Three views across, two framings down. */
private const val COLUMNS = 3
private const val ROWS = 2

/**
 * The four rules, ranked by how much damage each one prevents rather than by order of
 * operations. Extracted so the photograph layout and the drawing layout cannot drift into
 * showing different advice.
 */
@Composable
private fun PoseRules() {
    PoseRule(
        "Arms clear of your sides",
        "The one that matters most. An arm touching your waist is measured as part of you, " +
            "and the reading comes back far too lean.",
    )
    PoseRule(
        "Stand relaxed, not braced",
        "Bracing narrows the waist and flattens the stomach. Breathe out and let it go.",
    )
    PoseRule(
        "Phone at chest height, level",
        "Shooting up or down foreshortens the trunk and moves every band.",
    )
    PoseRule(
        "Waistband below your hip bones",
        "Shorts sitting on the hip are measured instead of the hip.",
    )
}

@Composable
private fun PoseRule(title: String, detail: String) {
    val dark = LocalIsDarkTheme.current

    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = if (dark) Brand.DarkInk else Brand.Navy,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.labelSmall,
            color = if (dark) Brand.DarkMuted else Brand.Muted,
        )
    }
}

/**
 * The figure itself, in the pose the pipeline needs.
 *
 * Built from separate simple shapes rather than one outline. An earlier drawn figure in this
 * project concatenated the feet onto the leg polygon and produced a filled bow tie, because a
 * single path through every part of a body self-intersects the moment two parts overlap.
 * Limbs drawn as their own rounded shapes cannot do that, and the arms have to be their own
 * shapes anyway — the whole point of the picture is the gap between arm and torso.
 *
 * The A-pose angle is deliberately wide. A reader copying this at half the angle still clears
 * their waist, which is the property that matters; a reader copying an arms-almost-down figure
 * at half the angle does not.
 */
@Composable
private fun PoseFigure(sideOn: Boolean, modifier: Modifier = Modifier) {
    val dark = LocalIsDarkTheme.current
    val body = if (dark) Brand.DarkBlue else Brand.Blue
    val frame = (if (dark) Brand.DarkMuted else Brand.Muted).copy(alpha = 0.5f)

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // The framing guide: what the camera should hold. Drawn behind the figure so the
        // figure reads as sitting inside it rather than on top of it.
        drawRoundRect(
            color = frame,
            topLeft = Offset(w * 0.04f, h * 0.03f),
            size = Size(w * 0.92f, h * 0.94f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f),
            style = Stroke(width = w * 0.012f),
        )

        if (sideOn) drawSideFigure(body, w, h) else drawFrontFigure(body, w, h)
    }
}

/** Front and back share this: the difference is which way the subject faces, not the shape. */
private fun DrawScope.drawFrontFigure(body: Color, w: Float, h: Float) {
    val centre = w / 2f

    // Head and neck.
    drawCircle(body, radius = w * 0.082f, center = Offset(centre, h * 0.10f))
    drawRoundRect(
        color = body,
        topLeft = Offset(centre - w * 0.030f, h * 0.160f),
        size = Size(w * 0.060f, h * 0.030f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.02f),
    )

    // Torso, narrower than the shoulder line so the arms have somewhere to be. The first
    // version drew the torso out to the full shoulder width and then rotated the arms about a
    // pivot inside it, so the arms never cleared the body and the whole figure rendered as one
    // blob — the opposite of the thing the picture exists to show.
    val shoulderHalf = w * 0.125f
    val waistHalf = w * 0.088f
    val hipHalf = w * 0.105f

    drawPath(
        path = Path().apply {
            moveTo(centre - shoulderHalf, h * 0.192f)
            lineTo(centre + shoulderHalf, h * 0.192f)
            lineTo(centre + waistHalf, h * 0.360f)
            lineTo(centre + hipHalf, h * 0.470f)
            lineTo(centre - hipHalf, h * 0.470f)
            lineTo(centre - waistHalf, h * 0.360f)
            close()
        },
        color = body,
    )

    // Arms, hanging with a slight outward angle rather than held out.
    //
    // Matched to the reference photograph: this is how someone stands when told to stand
    // still, which is the point — a pose people adopt naturally is one they will reproduce,
    // and an A-pose held at forty-five degrees is one they will approximate badly.
    //
    // The angle is small and the gap does the work. Rotating outward about the shoulder means
    // the separation is narrowest at the armpit and widest at the waist, which is exactly
    // where it is needed: the waist band is the measurement everything else divides by, and
    // the shoulder band is the one both arms ruined on the scan that read 4.93%.
    val armHalfWidth = w * 0.028f

    listOf(-1f, 1f).forEach { side ->
        val shoulder = Offset(centre + side * (shoulderHalf - w * 0.010f), h * 0.196f)

        rotate(degrees = side * 9f, pivot = shoulder) {
            drawRoundRect(
                color = body,
                // Meeting the torso at the shoulder rather than starting clear of it, so
                // the deltoid reads as attached. The rotation opens the gap on the way down,
                // which puts the separation where it is needed and none where it is not.
                topLeft = Offset(
                    shoulder.x - armHalfWidth + side * armHalfWidth,
                    shoulder.y,
                ),
                size = Size(armHalfWidth * 2f, h * 0.315f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(armHalfWidth),
            )
        }
    }

    // Legs, with a clear gap between them. Feet are not drawn: at this size they add nothing,
    // and every attempt to attach a foot to a leg in this project has produced a shape that
    // reads as a mistake.
    listOf(-1f, 1f).forEach { side ->
        drawRoundRect(
            color = body,
            topLeft = Offset(centre + side * w * 0.058f - w * 0.042f, h * 0.455f),
            size = Size(w * 0.084f, h * 0.470f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.040f),
        )
    }
}

/**
 * The side view, which reads depth rather than width.
 *
 * The belly is drawn with a slight forward curve on purpose. A side reference with a flat
 * front teaches the reader to suck in, and the abdominal depth measurement is the one
 * quantity in the scan that a braced stomach destroys outright.
 */
private fun DrawScope.drawSideFigure(body: Color, w: Float, h: Float) {
    val centre = w / 2f

    drawCircle(body, radius = w * 0.088f, center = Offset(centre, h * 0.11f))
    drawRoundRect(
        color = body,
        topLeft = Offset(centre - w * 0.03f, h * 0.175f),
        size = Size(w * 0.06f, h * 0.035f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.02f),
    )

    drawPath(
        path = Path().apply {
            // Back: a straight line down, which is what the measurement uses as its reference.
            moveTo(centre - w * 0.09f, h * 0.205f)
            lineTo(centre - w * 0.095f, h * 0.50f)
            lineTo(centre + w * 0.10f, h * 0.50f)
            // Front: chest, then a small forward curve at the belly.
            cubicTo(
                centre + w * 0.13f, h * 0.44f,
                centre + w * 0.13f, h * 0.33f,
                centre + w * 0.09f, h * 0.26f,
            )
            lineTo(centre + w * 0.085f, h * 0.205f)
            close()
        },
        color = body,
    )

    // One arm, hanging just clear of the trunk. Edge-on an arm sits inside the body's
    // front-to-back extent, so it cannot corrupt this view the way it corrupts the front.
    drawRoundRect(
        color = body,
        topLeft = Offset(centre - w * 0.035f, h * 0.225f),
        size = Size(w * 0.06f, h * 0.28f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.03f),
    )

    drawRoundRect(
        color = body,
        topLeft = Offset(centre - w * 0.055f, h * 0.485f),
        size = Size(w * 0.12f, h * 0.46f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.05f),
    )
}
