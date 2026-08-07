package com.squeeze.core.model

/**
 * Validation for the three fields the app cannot produce a number without.
 *
 * Height, year of birth and sex are not preferences. Every body-fat equation here is
 * sex-specific and age-dependent, and the photo scan uses height as its scale reference, so
 * without all three the app has nothing to say. That is why they are collected during
 * onboarding rather than left in a settings screen the user may never open.
 *
 * The rules live in `:core` so onboarding and settings cannot drift apart. Two screens
 * collecting the same field against two different sets of bounds is the kind of difference
 * nobody notices until one of them accepts a value the other rejects.
 *
 * Bounds are deliberately wider than the population and narrower than a typo. The point is
 * not to police unusual bodies — it is to catch a height entered in metres, a year typed as
 * a two-digit shorthand, or a digit dropped on the way in. Height in particular scales every
 * measurement the scan produces, so a wrong one is not a wrong field, it is a wrong app.
 */
object ProfileValidation {

    const val MIN_HEIGHT_CM = 120.0
    const val MAX_HEIGHT_CM = 230.0

    const val MIN_AGE_YEARS = 13
    const val MAX_AGE_YEARS = 100

    /** What is wrong with one field, phrased for the person who typed it. */
    data class FieldError(val message: String)

    /**
     * @param currentYear passed in rather than read from the clock, so the rules stay pure
     *   and the age bounds can be tested without waiting for a birthday.
     */
    fun heightError(raw: String, blankIsError: Boolean = false): FieldError? {
        if (raw.isBlank()) {
            return if (blankIsError) FieldError("Enter your height") else null
        }

        val value = raw.toDoubleOrNull()
            ?: return FieldError("Enter your height in centimetres, for example 175")

        return when {
            // A metric height typed in metres is the single most common slip here, and it
            // would otherwise be rejected with a bound the user cannot interpret.
            value < 3.0 -> FieldError("Enter your height in centimetres, not metres")
            value < MIN_HEIGHT_CM || value > MAX_HEIGHT_CM ->
                FieldError(
                    "Height should be between ${MIN_HEIGHT_CM.toInt()} and " +
                        "${MAX_HEIGHT_CM.toInt()} cm",
                )
            else -> null
        }
    }

    fun birthYearError(
        raw: String,
        currentYear: Int,
        blankIsError: Boolean = false,
    ): FieldError? {
        if (raw.isBlank()) {
            return if (blankIsError) FieldError("Enter your year of birth") else null
        }

        val value = raw.toIntOrNull()
            ?: return FieldError("Enter a four-digit year, for example 1990")

        val age = currentYear - value
        return when {
            age < MIN_AGE_YEARS || age > MAX_AGE_YEARS ->
                FieldError(
                    "Year of birth should be between ${currentYear - MAX_AGE_YEARS} and " +
                        "${currentYear - MIN_AGE_YEARS}",
                )
            else -> null
        }
    }

    /**
     * True when all three fields are present and within bounds.
     *
     * Sex has no free-text form to get wrong, so it is only checked for presence.
     */
    fun isComplete(
        heightRaw: String,
        birthYearRaw: String,
        sex: Sex?,
        currentYear: Int,
    ): Boolean = sex != null &&
        heightRaw.isNotBlank() &&
        birthYearRaw.isNotBlank() &&
        heightError(heightRaw) == null &&
        birthYearError(birthYearRaw, currentYear) == null

    /**
     * Builds a [Profile] when the inputs are valid, and null when they are not.
     *
     * Returning null rather than throwing keeps the caller from having to guard twice: the
     * screen already knows whether it can enable its button, and this is the same question
     * asked once more at the point of use.
     */
    fun build(
        heightRaw: String,
        birthYearRaw: String,
        sex: Sex?,
        currentYear: Int,
    ): Profile? {
        if (!isComplete(heightRaw, birthYearRaw, sex, currentYear)) return null

        val height = heightRaw.toDoubleOrNull() ?: return null
        val year = birthYearRaw.toIntOrNull() ?: return null

        return runCatching {
            Profile(heightCm = height, birthYear = year, sex = sex!!)
        }.getOrNull()
    }
}
