package com.squeeze.app.ui.onboarding

import androidx.compose.foundation.background
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
    onComplete: (heightCm: Double, birthYear: Int, sex: Sex) -> Unit,
) {
    var heightText by remember { mutableStateOf("") }
    var yearText by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf<Sex?>(null) }

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

        Spacer(Modifier.height(22.dp))

        PrimaryButton(
            text = "Start tracking",
            onClick = {
                submitted = true
                ProfileValidation
                    .build(heightText, yearText, sex, currentYear)
                    ?.let { onComplete(it.heightCm, it.birthYear, it.sex) }
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
