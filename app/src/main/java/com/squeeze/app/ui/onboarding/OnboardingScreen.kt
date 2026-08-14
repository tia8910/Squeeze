package com.squeeze.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.squeeze.app.ui.brand.SqueezeMark
import com.squeeze.app.ui.components.BrandCard
import com.squeeze.app.ui.components.NoticePill
import com.squeeze.app.ui.components.PrimaryButton
import com.squeeze.app.ui.theme.Brand
import com.squeeze.app.ui.theme.LocalIsDarkTheme
import com.squeeze.core.model.ProfileValidation
import com.squeeze.app.ui.settings.GoalOption
import com.squeeze.core.model.Goal
import com.squeeze.core.model.Sex
import java.time.LocalDate

/**
 * Collects the three things the app cannot work without, before it is first used.
 *
 * Height, year of birth and sex are not settings. Every body-fat equation here is
 * sex-specific and age-dependent, and the photo scan converts pixels into centimetres using
 * the stated height. Without all three the app can produce nothing at all — a scan taken
 * before they are set fails after the user has already undressed and framed a photograph,
 * which is the worst possible moment to discover a form was never filled in.
 *
 * So this screen has no skip. It is the one place in the app where that is the right call:
 * skipping does not defer the cost, it moves it somewhere far more annoying.
 *
 * What the screen owes the user in exchange is a reason. Each field says what it is for, and
 * height says plainly that everything scales with it, because a user who rounds 174 up to
 * 176 shifts every measurement they will ever take by about one per cent.
 */
@Composable
fun OnboardingScreen(
    onComplete: (
        heightCm: Double,
        birthYear: Int,
        sex: Sex,
        goal: Goal,
        targetBodyFatPercent: Double?,
        targetWeightKg: Double?,
        targetEpochDay: Long?,
    ) -> Unit,
) {
    var heightText by remember { mutableStateOf("") }
    var yearText by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf<Sex?>(null) }

    // Optional, unlike the three above, and it has to stay that way. The app produces
    // nothing at all without height, year and sex; it works perfectly well without a goal,
    // it just cannot tell the user whether what they are doing is enough. Making this
    // mandatory would force a number out of someone who has not decided yet, and a target
    // invented to get past a form is worse than none.
    var option by remember { mutableStateOf(GoalOption.DEFAULT) }
    var targetText by remember { mutableStateOf("") }
    var targetWeightText by remember { mutableStateOf("") }
    var weeks by remember { mutableStateOf(12) }

    // Errors stay hidden until the user has tried to continue. Showing them as the screen
    // opens would put a red message under every empty box before anything was typed.
    var submitted by remember { mutableStateOf(false) }

    val currentYear = LocalDate.now().year
    val heightError = ProfileValidation.heightError(heightText, blankIsError = submitted)
    val yearError = ProfileValidation.birthYearError(yearText, currentYear, blankIsError = submitted)
    val sexMissing = submitted && sex == null

    val complete = ProfileValidation.isComplete(heightText, yearText, sex, currentYear)
    val muted = if (LocalIsDarkTheme.current) Brand.DarkMuted else Brand.Muted
    val sub = if (LocalIsDarkTheme.current) Brand.DarkSub else Brand.Sub

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            // Without this the keyboard covers the button on a short screen, and the user
            // fills the form in with no way to submit it.
            .imePadding()
            .padding(horizontal = 22.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SqueezeMark(size = 64.dp)

        Text(
            text = "A few details first",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 18.dp),
        )

        Text(
            text = "These three decide how every measurement is calculated. Nothing here " +
                "leaves your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = sub,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )

        BrandCard(Modifier.fillMaxWidth()) {
            Text("Height", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "The scan has no depth sensor, so it uses your height to turn the " +
                    "photo into centimetres. Every measurement scales with it — be exact.",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            OutlinedTextField(
                value = heightText,
                onValueChange = { heightText = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                label = { Text("Height (cm)") },
                isError = heightError != null,
                supportingText = heightError?.let { { Text(it.message) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(12.dp))

        BrandCard(Modifier.fillMaxWidth()) {
            Text("Year of birth", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "The equations are age-dependent — body composition at the same " +
                    "measurements means something different at 25 and at 55.",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            OutlinedTextField(
                value = yearText,
                onValueChange = { yearText = it.filter { c -> c.isDigit() }.take(4) },
                label = { Text("Year of birth") },
                isError = yearError != null,
                supportingText = yearError?.let { { Text(it.message) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(12.dp))

        BrandCard(Modifier.fillMaxWidth()) {
            Text("Equation variant", style = MaterialTheme.typography.titleSmall)
            Text(
                // Said explicitly, because the field is unavoidable and its purpose is
                // narrow. It picks a formula; it is not a question about identity.
                text = "The validated equations were derived from sex-separated study " +
                    "groups and have no defined form outside them. This picks which one is " +
                    "used, and nothing else.",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sex.entries.forEach { option ->
                    FilterChip(
                        selected = sex == option,
                        onClick = { sex = option },
                        label = { Text(if (option == Sex.MALE) "Male" else "Female") },
                    )
                }
            }
            if (sexMissing) {
                Text(
                    text = "Pick one to continue",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        GoalPrompt(
            option = option,
            onOptionChange = { option = it },
            targetText = targetText,
            onTargetChange = { targetText = it },
            targetWeightText = targetWeightText,
            onTargetWeightChange = { targetWeightText = it },
            weeks = weeks,
            onWeeksChange = { weeks = it },
            muted = muted,
        )

        Spacer(Modifier.height(22.dp))

        PrimaryButton(
            text = "Start tracking",
            onClick = {
                submitted = true
                // Only the fields the chosen goal asks for are read. Taking a target body
                // fat from someone who picked "build muscle" would store a number they typed
                // into a box that happened to be on screen, and then report progress
                // against it.
                val fat = targetText.trim().replace(',', '.').toDoubleOrNull()
                    ?.takeIf { option.wantsBodyFat && it in 3.0..60.0 }
                val targetWeight = targetWeightText.trim().replace(',', '.').toDoubleOrNull()
                    ?.takeIf { option.wantsWeight && it in 30.0..300.0 }
                val deadline = LocalDate.now().plusWeeks(weeks.toLong()).toEpochDay()
                    .takeIf { fat != null || targetWeight != null }

                ProfileValidation
                    .build(heightText, yearText, sex, currentYear)
                    ?.let {
                        onComplete(
                            it.heightCm,
                            it.birthYear,
                            it.sex,
                            option.goal,
                            fat,
                            targetWeight,
                            deadline,
                        )
                    }
            },
        )

        if (submitted && !complete) {
            Spacer(Modifier.height(12.dp))
            NoticePill(text = "Fill in all three to continue")
        }

        Text(
            text = "You can change any of these later under You.",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/** Preset horizons, in weeks. Matches the Settings editor so the two cannot drift. */
private val ONBOARDING_HORIZONS = listOf(8, 12, 16, 24)

/**
 * Asks what the user is actually here for, and by when.
 *
 * Optional, and it says so, which is the difference between asking and demanding. Someone
 * who does not yet know their body fat cannot pick a sensible target, and the field left
 * blank simply means the dashboard shows a number instead of a verdict — a real loss, but a
 * smaller one than a target picked to satisfy a form.
 *
 * The horizon is a preset rather than a date picker because nobody's goal is "17 September".
 * It is "before the summer" or "in three months", and a picker turns a choice about pace
 * into a calendar puzzle. The resulting date is printed so the shorthand stays honest.
 */
@Composable
private fun GoalPrompt(
    option: GoalOption,
    onOptionChange: (GoalOption) -> Unit,
    targetText: String,
    onTargetChange: (String) -> Unit,
    targetWeightText: String,
    onTargetWeightChange: (String) -> Unit,
    weeks: Int,
    onWeeksChange: (Int) -> Unit,
    muted: androidx.compose.ui.graphics.Color,
) {
    val deadline = LocalDate.now().plusWeeks(weeks.toLong())
    val anyTarget = (option.wantsBodyFat && targetText.isNotBlank()) ||
        (option.wantsWeight && targetWeightText.isNotBlank())

    BrandCard(Modifier.fillMaxWidth()) {
        Text("Your goal — optional", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "A target with a date is what lets the app tell you whether what you are " +
                "doing is working, rather than only showing you a number. Skip it if you do " +
                "not know yet — you can set one any time under You.",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
        )

        // The same four goals the settings screen offers, from the same enum. This screen had
        // its own field asking only for a body fat percentage, so the first thing the app ever
        // asked a new user was to state a goal it could hold and three it could not — and
        // someone whose actual aim was to add size either invented a percentage or skipped
        // the question entirely.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            GoalOption.entries.forEach { candidate ->
                FilterChip(
                    selected = option == candidate,
                    onClick = { onOptionChange(candidate) },
                    label = { Text(candidate.label) },
                )
            }
        }

        Text(
            text = option.blurb,
            style = MaterialTheme.typography.bodySmall,
            color = muted,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
        )

        if (option.wantsBodyFat) {
            OutlinedTextField(
                value = targetText,
                onValueChange = { onTargetChange(it.take(4)) },
                label = { Text("Target body fat (%)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = if (option.wantsWeight) ImeAction.Next else ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        if (option.wantsWeight) {
            OutlinedTextField(
                value = targetWeightText,
                onValueChange = { onTargetWeightChange(it.take(5)) },
                label = { Text("Target weight (kg)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        if (anyTarget) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                ONBOARDING_HORIZONS.forEach { horizon ->
                    FilterChip(
                        selected = weeks == horizon,
                        onClick = { onWeeksChange(horizon) },
                        label = { Text("${horizon}w") },
                    )
                }
            }
            Text(
                text = "By ${deadline.dayOfMonth} ${deadline.month.name.lowercase()
                    .replaceFirstChar { it.uppercase() }} ${deadline.year}.",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
