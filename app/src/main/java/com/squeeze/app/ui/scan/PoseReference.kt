package com.squeeze.app.ui.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PoseFigure(
                sideOn = step == ScanStep.SIDE,
                modifier = Modifier.weight(1f).aspectRatio(0.62f),
            )

            Column(
                modifier = Modifier.weight(1.55f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PoseRule(
                    "Arms clear of your sides",
                    "The one that matters most. An arm touching your waist is measured as " +
                        "part of you, and the reading comes back far too lean.",
                )
                PoseRule(
                    "Stand relaxed, not braced",
                    "Bracing narrows the waist and flattens the stomach. Breathe out and let " +
                        "it go.",
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
    drawCircle(body, radius = w * 0.088f, center = Offset(centre, h * 0.11f))
    drawRoundRect(
        color = body,
        topLeft = Offset(centre - w * 0.035f, h * 0.175f),
        size = Size(w * 0.07f, h * 0.035f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.02f),
    )

    // Torso: shoulders wider than waist, hips between the two. A trapezoid rather than a
    // rectangle, because the taper is the quantity the scan reads and a figure that does not
    // show one teaches the wrong silhouette.
    val shoulderHalf = w * 0.155f
    val waistHalf = w * 0.105f
    val hipHalf = w * 0.125f

    drawPath(
        path = Path().apply {
            moveTo(centre - shoulderHalf, h * 0.205f)
            lineTo(centre + shoulderHalf, h * 0.205f)
            lineTo(centre + waistHalf, h * 0.39f)
            lineTo(centre + hipHalf, h * 0.50f)
            lineTo(centre - hipHalf, h * 0.50f)
            lineTo(centre - waistHalf, h * 0.39f)
            close()
        },
        color = body,
    )

    // Arms, angled out. Each is a rounded bar rotated about its own shoulder, so the gap
    // between arm and waist is the thing the reader copies.
    listOf(-1f, 1f).forEach { side ->
        val shoulder = Offset(centre + side * shoulderHalf * 0.85f, h * 0.225f)
        rotate(degrees = side * 22f, pivot = shoulder) {
            drawRoundRect(
                color = body,
                topLeft = Offset(shoulder.x - w * 0.035f, shoulder.y),
                size = Size(w * 0.07f, h * 0.30f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.035f),
            )
        }
    }

    // Legs, slightly apart. Feet are not drawn: at this size they add nothing the reader
    // needs and every attempt to attach them to a leg in this project has produced a shape
    // that reads as a mistake.
    listOf(-1f, 1f).forEach { side ->
        drawRoundRect(
            color = body,
            topLeft = Offset(centre + side * w * 0.115f - w * 0.055f, h * 0.485f),
            size = Size(w * 0.11f, h * 0.46f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.05f),
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
