package com.squeeze.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import com.squeeze.app.ui.components.AuroraBackground
import com.squeeze.app.ui.components.PageDots
import com.squeeze.app.ui.components.ScanPulse
import com.squeeze.app.ui.components.entrance
import kotlinx.coroutines.launch
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

    // Three short steps instead of one long form: who you are (and whether you have a
    // backup), your body, your goal. The pager only swipes back — forward goes through the
    // button, so the body step cannot be skipped by a stray swipe.
    val pager = rememberPagerState(pageCount = { STEPS.size })
    val scope = rememberCoroutineScope()
    fun go(page: Int) = scope.launch { pager.animateScrollToPage(page) }

    fun finish() {
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
                onComplete(it.heightCm, it.birthYear, it.sex, option.goal, fat, targetWeight, deadline)
            }
            ?: go(1)
    }

    AuroraBackground(Modifier.fillMaxSize(), intense = true) {
        Column(
            Modifier
                .fillMaxSize()
                // Without this the keyboard covers the button on a short screen, and the user
                // fills the form in with no way to submit it.
                .imePadding()
                .padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            // Header: the mark, where you are, and a bar that fills as you go.
            Row(verticalAlignment = Alignment.CenterVertically) {
                SqueezeMark(size = 36.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Step ${pager.currentPage + 1} of ${STEPS.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = muted,
                    modifier = Modifier.weight(1f),
                )
                PageDots(count = STEPS.size, current = pager.currentPage)
            }
            val fill by animateFloatAsState((pager.currentPage + 1f) / STEPS.size, tween(500), label = "fill")
            LinearProgressIndicator(
                progress = { fill },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(5.dp).clip(RoundedCornerShape(50)),
            )

            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).padding(top = 8.dp),
                userScrollEnabled = false,
                verticalAlignment = Alignment.Top,
            ) { page ->
                // Parallax: the page slides and fades as it moves, the heading a little
                // further than the body, so the change of step has depth.
                val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction)
                Column(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 1f - kotlin.math.abs(offset).coerceIn(0f, 1f) * 0.6f
                            val s = 1f - kotlin.math.abs(offset).coerceIn(0f, 1f) * 0.06f
                            scaleX = s
                            scaleY = s
                        }
                        .verticalScroll(rememberScrollState())
                        .padding(top = 18.dp, bottom = 12.dp),
                ) {
                    Text(
                        STEPS[page].title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.graphicsLayer { translationX = offset * 120f },
                    )
                    Text(
                        STEPS[page].subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = sub,
                        modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
                    )
                    when (page) {
                        0 -> AccountStep()
                        1 -> BodyStep(
                            heightText = heightText,
                            onHeight = { heightText = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                            heightError = heightError?.message,
                            yearText = yearText,
                            onYear = { yearText = it.filter { c -> c.isDigit() }.take(4) },
                            yearError = yearError?.message,
                            sex = sex,
                            onSex = { sex = it },
                            sexMissing = sexMissing,
                            muted = muted,
                        )
                        else -> GoalPrompt(
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
                    }
                }
            }

            if (submitted && !complete && pager.currentPage == 1) {
                NoticePill(text = "Fill in all three to continue", modifier = Modifier.padding(bottom = 10.dp))
            }

            val last = pager.currentPage == STEPS.lastIndex
            PrimaryButton(
                text = when (pager.currentPage) {
                    0 -> "Continue"
                    1 -> "Next: your goal"
                    else -> "Start tracking"
                },
                onClick = {
                    when {
                        pager.currentPage == 1 && !complete -> { submitted = true }
                        last -> { finish() }
                        else -> { go(pager.currentPage + 1) }
                    }
                },
            )
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                if (pager.currentPage > 0) {
                    TextButton(onClick = { go(pager.currentPage - 1) }) { Text("Back") }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                Text(
                    "Change any of this later under You.",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
    }
}

private class Step(val title: String, val subtitle: String)

private val STEPS = listOf(
    Step("Welcome to Squeeze", "Sign in to back up and restore everything — or carry on without an account."),
    Step("Your body", "Three details every measurement is calculated from."),
    Step("Your goal", "What you're training for shapes your plan, your food and your coaching."),
)

/** Step 1: the animated scan, and Google sign-in with restore. */
@Composable
private fun AccountStep() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        ScanPulse(size = 190.dp, modifier = Modifier.entrance(0))
        Row(
            Modifier.fillMaxWidth().padding(vertical = 18.dp).entrance(1),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FeaturePill("AI scan", Icons.Rounded.CenterFocusStrong, Modifier.weight(1f))
            FeaturePill("Smart plan", Icons.Rounded.FitnessCenter, Modifier.weight(1f))
            FeaturePill("Fuel", Icons.Rounded.Restaurant, Modifier.weight(1f))
        }
        BrandCard(Modifier.fillMaxWidth().entrance(2)) {
            com.squeeze.app.ui.backup.GoogleBackupCard(compact = true)
        }
    }
}

@Composable
private fun FeaturePill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    val dark = LocalIsDarkTheme.current
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (dark) Brand.DarkCard.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.85f))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Step 2: height, year of birth and the equation variant, each saying what it is for. */
@Composable
private fun BodyStep(
    heightText: String,
    onHeight: (String) -> Unit,
    heightError: String?,
    yearText: String,
    onYear: (String) -> Unit,
    yearError: String?,
    sex: Sex?,
    onSex: (Sex) -> Unit,
    sexMissing: Boolean,
    muted: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BrandCard(Modifier.fillMaxWidth().entrance(0)) {
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
                onValueChange = onHeight,
                label = { Text("Height (cm)") },
                isError = heightError != null,
                supportingText = heightError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        BrandCard(Modifier.fillMaxWidth().entrance(1)) {
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
                onValueChange = onYear,
                label = { Text("Year of birth") },
                isError = yearError != null,
                supportingText = yearError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        BrandCard(Modifier.fillMaxWidth().entrance(2)) {
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
                        onClick = { onSex(option) },
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

    BrandCard(Modifier.fillMaxWidth().entrance(0)) {
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
