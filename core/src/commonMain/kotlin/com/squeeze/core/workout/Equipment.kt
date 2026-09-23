package com.squeeze.core.workout

import com.squeeze.core.program.MuscleGroup
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * How to use one piece of gym equipment.
 *
 * @param id matches the key in `clip_equipment.json` (tools/vlm/equipment_prompts.py)
 * @param exercise the exercise it is logged as
 * @param muscles in plain words, for the screen
 * @param group what the training log and the programme count it toward; null for cardio
 * @param sport what a cardio machine is logged as
 */
data class MachineGuide(
    val id: String,
    val name: String,
    val exercise: String,
    val muscles: String,
    val group: MuscleGroup?,
    val compound: Boolean,
    val lowerBody: Boolean,
    val setup: String,
    val steps: List<String>,
    val mistakes: List<String>,
    val sport: Sport = Sport.STRENGTH,
) {
    val cardio: Boolean get() = group == null
}

/** The equipment the camera recognises, and how to use each. */
object EquipmentCatalog {

    val ALL: List<MachineGuide> = listOf(
        MachineGuide(
            "lat_pulldown", "Lat pulldown", "Lat Pulldown", "Lats, upper back, biceps",
            MuscleGroup.BACK, true, false,
            "Set the thigh pad so your legs are locked under it. Take a grip just outside shoulder width.",
            listOf(
                "Sit tall, lean back about 15°, chest up.",
                "Pull the bar to your upper chest by driving your elbows down and back.",
                "Pause with the bar at the chest, shoulder blades squeezed.",
                "Let it rise under control until your arms are straight and you feel the stretch.",
            ),
            listOf("Leaning far back and rowing it", "Pulling behind the neck", "Letting the stack slam between reps"),
        ),
        MachineGuide(
            "cable_row", "Seated cable row", "Seated Cable Row", "Mid-back, lats, rear delts",
            MuscleGroup.BACK, true, false,
            "Feet on the platform, knees soft. Use the close-grip V handle.",
            listOf(
                "Sit upright with arms straight and the stack just lifted.",
                "Pull the handle to your lower ribs, elbows close to your sides.",
                "Squeeze your shoulder blades together for a second.",
                "Return until your arms are straight and your upper back is stretched — torso stays still.",
            ),
            listOf("Rocking the torso to move the weight", "Shrugging the shoulders up", "Rounding the lower back"),
        ),
        MachineGuide(
            "cable_station", "Cable machine", "Cable Fly", "Chest (fly), triceps (pushdown), abs (crunch)",
            MuscleGroup.CHEST, false, false,
            "Set both pulleys at shoulder height with single handles. The same station does pushdowns and crunches with a rope.",
            listOf(
                "Step forward into a split stance, arms out wide with a slight bend.",
                "Bring the handles together in front of your chest in a wide arc.",
                "Squeeze your chest for a second with the hands together.",
                "Open back out slowly until you feel the stretch across the chest.",
            ),
            listOf("Bending the elbows into a press", "Going so heavy the shoulders take over", "Losing the stretch at the back"),
        ),
        MachineGuide(
            "leg_press", "Leg press", "Leg Press", "Quads, glutes, hamstrings",
            MuscleGroup.QUADS, true, true,
            "Back flat against the pad. Feet shoulder-width, mid-platform.",
            listOf(
                "Release the safeties and lower the sled by bending your knees toward your chest.",
                "Go as deep as you can while your lower back stays on the pad.",
                "Press through your whole foot until the legs are straight but not locked.",
                "Re-engage the safeties when you finish the set.",
            ),
            listOf("Locking the knees hard at the top", "Lower back peeling off the pad at the bottom", "Knees caving inward"),
        ),
        MachineGuide(
            "leg_extension", "Leg extension", "Leg Extension", "Quads",
            MuscleGroup.QUADS, false, true,
            "Line your knee joint up with the machine's pivot. The ankle pad sits just above your shoes.",
            listOf(
                "Hold the handles and sit back into the seat.",
                "Straighten your legs until they are fully extended.",
                "Squeeze the quads hard for a second at the top.",
                "Lower slowly, taking two to three seconds.",
            ),
            listOf("Swinging the weight up", "Hips lifting off the seat", "Dropping the weight on the way down"),
        ),
        MachineGuide(
            "leg_curl", "Leg curl", "Lying Leg Curl", "Hamstrings",
            MuscleGroup.HAMSTRINGS, false, true,
            "Knees just off the edge of the pad and lined up with the pivot; roller above your heels.",
            listOf(
                "Grip the handles and press your hips into the pad.",
                "Curl your heels toward your glutes as far as they go.",
                "Hold briefly at the top.",
                "Lower under control until the legs are almost straight.",
            ),
            listOf("Hips rising off the pad", "Half reps", "Jerking the weight up"),
        ),
        MachineGuide(
            "smith_machine", "Smith machine", "Smith Machine Squat", "Quads, glutes (squat); chest (press)",
            MuscleGroup.QUADS, true, true,
            "Set the bar at shoulder height and the safety stops just below your lowest point.",
            listOf(
                "Step under the bar with it across your upper back; feet slightly in front of the bar.",
                "Twist the bar to unhook it and brace your core.",
                "Sit down until your thighs are at least parallel.",
                "Drive up through your heels and re-hook the bar when done.",
            ),
            listOf("Feet directly under the bar (knees take all the load)", "Forgetting the safety stops", "Bouncing out of the bottom"),
        ),
        MachineGuide(
            "squat_rack", "Squat rack", "Back Squat", "Quads, glutes, core",
            MuscleGroup.QUADS, true, true,
            "Set the J-hooks at mid-chest and the safety arms just below squat depth.",
            listOf(
                "Bar across your upper back, hands just outside the shoulders; walk it out in two or three steps.",
                "Big breath, brace, and sit down between your heels.",
                "Go to at least parallel with your chest up and knees tracking over your toes.",
                "Drive up, breathing out near the top.",
            ),
            listOf("Knees collapsing inward", "Heels lifting", "Rounding the back at the bottom", "Squatting without safeties set"),
        ),
        MachineGuide(
            "bench_press", "Bench press", "Barbell Bench Press", "Chest, front delts, triceps",
            MuscleGroup.CHEST, true, false,
            "Eyes under the bar. Grip a little wider than your shoulders. Use a spotter or the safety arms.",
            listOf(
                "Squeeze your shoulder blades together and plant your feet.",
                "Unrack the bar over your shoulders with straight arms.",
                "Lower it to your mid-chest, elbows at about 45°.",
                "Press it back up and slightly toward your face.",
            ),
            listOf("Bouncing the bar off the chest", "Elbows flared to 90°", "Butt lifting off the bench"),
        ),
        MachineGuide(
            "adjustable_bench", "Adjustable bench", "Incline Dumbbell Press", "Upper chest, front delts, triceps",
            MuscleGroup.CHEST, true, false,
            "Set the back to 30°. Kick the dumbbells up off your knees as you lie back.",
            listOf(
                "Dumbbells at chest level, palms forward, shoulder blades pinched.",
                "Press up until the arms are straight over your upper chest.",
                "Lower slowly until you feel a stretch across the chest.",
                "Keep your wrists stacked over your elbows.",
            ),
            listOf("Setting the incline too steep (it becomes a shoulder press)", "Clanging the dumbbells together", "Half-depth reps"),
        ),
        MachineGuide(
            "dumbbells", "Dumbbell rack", "Dumbbell Lateral Raise", "Side delts",
            MuscleGroup.SHOULDERS, false, false,
            "Pick a light pair — lateral raises need far less than you think.",
            listOf(
                "Stand tall, dumbbells at your sides, slight bend at the elbows.",
                "Raise your arms out to the sides until they are level with your shoulders.",
                "Lead with the elbows, pinkies slightly up.",
                "Lower slowly over two to three seconds.",
            ),
            listOf("Swinging with the hips", "Shrugging the traps up", "Lifting above shoulder height"),
        ),
        MachineGuide(
            "chest_press_machine", "Chest press machine", "Machine Chest Press", "Chest, front delts, triceps",
            MuscleGroup.CHEST, true, false,
            "Set the seat so the handles are level with your mid-chest.",
            listOf(
                "Back against the pad, shoulder blades pinched.",
                "Press the handles forward until your arms are straight.",
                "Return slowly until you feel a stretch in the chest.",
                "Keep your wrists straight throughout.",
            ),
            listOf("Seat too high (shoulders take over)", "Letting the stack touch between reps", "Shoulders rolling forward"),
        ),
        MachineGuide(
            "pec_deck", "Pec deck", "Pec Deck Fly", "Chest",
            MuscleGroup.CHEST, false, false,
            "Seat height so the handles are at chest level; arms slightly bent.",
            listOf(
                "Sit tall with your back against the pad.",
                "Bring the handles together in front of you in an arc.",
                "Squeeze the chest for a second.",
                "Open slowly until you feel the stretch — not past it.",
            ),
            listOf("Letting the arms go too far back", "Using momentum", "Pressing instead of hugging"),
        ),
        MachineGuide(
            "shoulder_press_machine", "Shoulder press machine", "Machine Shoulder Press", "Front and side delts, triceps",
            MuscleGroup.SHOULDERS, true, false,
            "Seat height so the handles start at shoulder level.",
            listOf(
                "Back against the pad, core braced.",
                "Press overhead until your arms are nearly straight.",
                "Lower under control to shoulder height.",
                "Don't let your lower back arch off the pad.",
            ),
            listOf("Arching the lower back", "Half reps at the top only", "Locking the elbows hard"),
        ),
        MachineGuide(
            "pullup_station", "Pull-up bar / assisted pull-up", "Pull-Up", "Lats, upper back, biceps",
            MuscleGroup.BACK, true, false,
            "On an assisted machine, more weight on the stack means more help. Grip just outside shoulder width.",
            listOf(
                "Hang with straight arms, shoulders pulled down away from the ears.",
                "Pull your chest toward the bar, elbows driving down.",
                "Get your chin over the bar.",
                "Lower all the way to straight arms under control.",
            ),
            listOf("Kipping or swinging", "Half reps", "Shrugging at the bottom"),
        ),
        MachineGuide(
            "dip_station", "Dip station", "Dips", "Chest, triceps, front delts",
            MuscleGroup.TRICEPS, true, false,
            "Grip the bars, jump up to straight arms.",
            listOf(
                "Lean slightly forward for more chest, stay upright for more triceps.",
                "Lower until your upper arms are about parallel to the floor.",
                "Press back up to straight arms.",
                "Keep your shoulders down, away from your ears.",
            ),
            listOf("Dropping too deep with painful shoulders", "Flaring the elbows wide", "Bouncing at the bottom"),
        ),
        MachineGuide(
            "hack_squat", "Hack squat", "Hack Squat", "Quads, glutes",
            MuscleGroup.QUADS, true, true,
            "Shoulders under the pads, back flat. Feet shoulder-width, mid-platform.",
            listOf(
                "Release the safety handles.",
                "Lower until your thighs are at least parallel to the platform.",
                "Drive up through the whole foot.",
                "Stop just short of locking out.",
            ),
            listOf("Heels lifting", "Knees caving in", "Cutting depth as the weight goes up"),
        ),
        MachineGuide(
            "hip_thrust", "Hip thrust / glute machine", "Hip Thrust", "Glutes, hamstrings",
            MuscleGroup.GLUTES, true, true,
            "Upper back against the bench or pad, bar or belt over your hips with padding.",
            listOf(
                "Feet flat, shins vertical at the top.",
                "Drive through your heels and lift your hips until your body is flat.",
                "Squeeze the glutes hard with your chin tucked.",
                "Lower until your hips are just above the floor.",
            ),
            listOf("Overarching the lower back at the top", "Feet too far forward (hamstrings take over)", "Rushing the squeeze"),
        ),
        MachineGuide(
            "calf_raise_standing", "Standing calf raise", "Standing Calf Raise", "Calves",
            MuscleGroup.CALVES, false, true,
            "Shoulders under the pads, balls of your feet on the edge of the step.",
            listOf(
                "Lower your heels as far as they go for a full stretch.",
                "Pause for a second at the bottom.",
                "Rise onto your toes as high as you can.",
                "Hold the top for a second.",
            ),
            listOf("Bouncing at the bottom", "Tiny range of motion", "Bending the knees to cheat"),
        ),
        MachineGuide(
            "calf_raise_seated", "Seated calf raise", "Seated Calf Raise", "Calves (soleus)",
            MuscleGroup.CALVES, false, true,
            "Knee pad snug on your lower thighs, balls of your feet on the platform.",
            listOf(
                "Release the safety and drop your heels for a deep stretch.",
                "Pause at the bottom.",
                "Press up as high as possible.",
                "Squeeze, then lower slowly.",
            ),
            listOf("Bouncing", "Half reps", "Too heavy to reach the full stretch"),
        ),
        MachineGuide(
            "hip_abductor", "Hip abductor / adductor", "Hip Abduction", "Outer glutes (abduction), inner thighs (adduction)",
            MuscleGroup.GLUTES, false, true,
            "Pads on the outside of your knees to push out (abduction), inside to squeeze in (adduction).",
            listOf(
                "Sit tall and hold the handles.",
                "Push your knees apart as far as they go.",
                "Hold for a second.",
                "Return slowly without letting the stack touch.",
            ),
            listOf("Leaning to use body weight", "Going too heavy for the full range", "Snapping back"),
        ),
        MachineGuide(
            "back_extension", "Back extension bench", "Back Extension", "Lower back, glutes, hamstrings",
            MuscleGroup.HAMSTRINGS, false, true,
            "Set the pad so your hip bones are just above its edge.",
            listOf(
                "Cross your arms over your chest.",
                "Lower your torso by hinging at the hips.",
                "Rise until your body is in a straight line — not beyond.",
                "Squeeze the glutes at the top.",
            ),
            listOf("Hyperextending at the top", "Rounding the spine", "Swinging quickly"),
        ),
        MachineGuide(
            "preacher_curl", "Preacher curl bench", "Preacher Curl", "Biceps",
            MuscleGroup.BICEPS, false, false,
            "Seat height so your armpits sit on the top of the pad.",
            listOf(
                "Hold the bar or dumbbell with arms nearly straight on the pad.",
                "Curl it up until your forearms are vertical.",
                "Squeeze the biceps.",
                "Lower slowly to almost straight — keep a slight bend.",
            ),
            listOf("Lifting the elbows off the pad", "Dropping to a locked elbow", "Using body lean"),
        ),
        MachineGuide(
            "kettlebell", "Kettlebell", "Kettlebell Swing", "Glutes, hamstrings, core",
            MuscleGroup.GLUTES, true, true,
            "Feet a little wider than shoulders, the bell a foot in front of you.",
            listOf(
                "Hinge and hike the bell back between your legs.",
                "Snap your hips forward to float it to chest height.",
                "Let it fall and hinge again as it comes back.",
                "The power comes from the hips, not the arms.",
            ),
            listOf("Squatting it instead of hinging", "Lifting with the arms", "Rounding the back"),
        ),
        MachineGuide(
            "tbar_row", "T-bar / chest-supported row", "Chest-Supported Row", "Mid-back, lats, rear delts",
            MuscleGroup.BACK, true, false,
            "Chest on the pad, feet planted, arms hanging straight.",
            listOf(
                "Pull the handles toward your lower ribs.",
                "Squeeze your shoulder blades together.",
                "Lower until your arms are straight and the shoulders stretch forward.",
                "Keep your chest on the pad the whole time.",
            ),
            listOf("Lifting the chest off the pad", "Shrugging instead of rowing", "Short, jerky reps"),
        ),
        MachineGuide(
            "captains_chair", "Captain's chair / ab station", "Hanging Knee Raise", "Abs, hip flexors",
            MuscleGroup.ABS, false, false,
            "Back against the pad, forearms on the rests, legs hanging.",
            listOf(
                "Brace your core and curl your knees toward your chest.",
                "Tilt your pelvis up at the top to really use the abs.",
                "Lower slowly without swinging.",
                "Straighten the legs to make it harder.",
            ),
            listOf("Swinging the legs", "Only lifting the knees to hip height", "Arching off the back pad"),
        ),
        MachineGuide(
            "battle_ropes", "Battle ropes", "Battle Rope Intervals", "Shoulders, arms, conditioning",
            null, false, false,
            "Hold one rope end in each hand, knees bent, standing where the ropes have a little slack.",
            listOf(
                "Make fast alternating waves from the shoulders.",
                "Work 20–30 seconds, rest 30–40.",
                "Keep your core braced and knees soft.",
                "Repeat for 6–10 rounds.",
            ),
            listOf("Standing too upright", "Waves too small to reach the anchor", "Holding the breath"),
            sport = Sport.HIIT,
        ),
        MachineGuide(
            "treadmill", "Treadmill", "Treadmill", "Heart, legs",
            null, false, true,
            "Clip the safety key to your clothes. Start at walking pace.",
            listOf(
                "Warm up 5 minutes at an easy pace.",
                "Intervals: 1 minute hard, 2 minutes easy, 6–8 rounds — or steady 20–40 minutes you could talk through.",
                "Stand tall; don't hold the rails.",
                "Cool down 5 minutes.",
            ),
            listOf("Holding the handrails", "Overstriding", "Starting too fast"),
            sport = Sport.RUNNING,
        ),
        MachineGuide(
            "stationary_bike", "Exercise bike", "Stationary Bike", "Heart, quads",
            null, false, true,
            "Seat height so your knee is slightly bent at the bottom of the pedal stroke.",
            listOf(
                "Warm up 5 minutes, light resistance.",
                "Steady: 20–45 minutes at a pace you could talk through. Intervals: 30 s hard, 90 s easy × 8.",
                "Keep a smooth, round pedal stroke.",
                "Cool down 5 minutes.",
            ),
            listOf("Seat too low (knee pain)", "Bouncing in the saddle", "Resistance too low to mean anything"),
            sport = Sport.CYCLING,
        ),
        MachineGuide(
            "rowing_machine", "Rowing machine", "Rowing Machine", "Legs, back, arms, heart",
            null, false, false,
            "Strap your feet so the strap crosses the widest part. Damper at 3–5.",
            listOf(
                "Push with the legs first, then lean back slightly, then pull the handle to your lower ribs.",
                "Return in reverse: arms, body, then legs.",
                "Aim for 24–28 strokes a minute.",
                "Steady 15–30 minutes, or 250 m hard / 1 min easy intervals.",
            ),
            listOf("Pulling with the arms first", "Damper on 10", "Rushing the slide forward"),
            sport = Sport.ROWING,
        ),
        MachineGuide(
            "elliptical", "Elliptical / cross trainer", "Elliptical", "Heart, legs",
            null, false, true,
            "Hold the moving handles, feet flat on the pedals.",
            listOf(
                "Warm up 5 minutes, low resistance.",
                "Work 20–40 minutes steady, or alternate 1 minute hard with 2 easy.",
                "Push and pull the handles to involve the upper body.",
                "Cool down 5 minutes.",
            ),
            listOf("Leaning on the handles", "Resistance too low", "On your toes the whole time"),
            sport = Sport.OTHER,
        ),
        MachineGuide(
            "stair_climber", "Stair climber", "Stair Climber", "Glutes, legs, heart",
            null, false, true,
            "Start slow; hold the rails only lightly for balance.",
            listOf(
                "Stand tall and step with the whole foot.",
                "Steady 15–30 minutes at a pace you can sustain.",
                "Drive through your heels to use the glutes.",
                "Slow down for the last 3 minutes.",
            ),
            listOf("Leaning on the rails", "Tiptoe steps", "Hunching over"),
            sport = Sport.HIKING,
        ),
        MachineGuide(
            "lat_row_combo", "Lat pulldown / low row combo", "Lat Pulldown", "Lats, upper back, biceps",
            MuscleGroup.BACK, true, false,
            "Two stations on one stack. Pulldown: sit facing the stack, thighs locked under the pads, " +
                "long bar overhead. Low row: move to the long bench, feet on the foot bar, V-handle on the low pulley.",
            listOf(
                "Pulldown — lean back slightly, pull the bar to your upper chest, elbows driving down.",
                "Let the bar rise slowly until your arms are straight and your lats stretch.",
                "Low row — sit tall, pull the handle to your lower ribs, squeeze your shoulder blades.",
                "Return with control, torso still. Do both for a complete back session.",
            ),
            listOf("Leaning far back on pulldowns", "Rocking the torso on rows", "Letting the stack crash between reps"),
        ),
        MachineGuide(
            "seated_leg_curl", "Seated leg curl", "Seated Leg Curl", "Hamstrings",
            MuscleGroup.HAMSTRINGS, false, true,
            "Knees lined up with the pivot, thigh pad snug on top, roller just above your heels.",
            listOf(
                "Sit back with your spine against the pad.",
                "Curl your heels down and back as far as they go.",
                "Hold a second at the bottom.",
                "Let the roller rise slowly until your legs are nearly straight.",
            ),
            listOf("Thigh pad loose, so the hips lift", "Short range", "Letting the weight snap back"),
        ),
        MachineGuide(
            "lateral_raise_machine", "Lateral raise machine", "Machine Lateral Raise", "Side delts",
            MuscleGroup.SHOULDERS, false, false,
            "Seat height so your shoulders line up with the machine's pivots; pads on the outside of your upper arms.",
            listOf(
                "Sit tall, chest against the pad if there is one.",
                "Raise your arms out to the sides to shoulder height, leading with the elbows.",
                "Pause at the top.",
                "Lower slowly over two to three seconds.",
            ),
            listOf("Shrugging the shoulders up", "Going above shoulder height", "Bouncing out of the bottom"),
        ),
        MachineGuide(
            "biceps_curl_machine", "Biceps curl machine", "Machine Biceps Curl", "Biceps",
            MuscleGroup.BICEPS, false, false,
            "Seat height so your upper arms lie flat on the pad and your elbows line up with the pivot.",
            listOf(
                "Grip the handles with arms nearly straight.",
                "Curl up until your forearms are vertical.",
                "Squeeze the biceps for a second.",
                "Lower slowly to almost straight.",
            ),
            listOf("Elbows lifting off the pad", "Leaning back to swing it", "Locking out hard at the bottom"),
        ),
        MachineGuide(
            "triceps_machine", "Triceps / seated dip machine", "Machine Triceps Extension", "Triceps",
            MuscleGroup.TRICEPS, false, false,
            "Seat height so the handles start at about chest level with your elbows bent.",
            listOf(
                "Sit tall, elbows close to your sides.",
                "Push the handles down (or forward) until your arms are straight.",
                "Squeeze the triceps at lockout.",
                "Let the handles come back slowly until your elbows are bent to 90°.",
            ),
            listOf("Elbows flaring out", "Leaning over the handles", "Half reps"),
        ),
        MachineGuide(
            "belt_squat", "Belt / pendulum squat", "Belt Squat", "Quads, glutes",
            MuscleGroup.QUADS, true, true,
            "Belt round your hips (or shoulders under the pendulum pads); feet shoulder-width on the platform.",
            listOf(
                "Stand tall and release the stop.",
                "Sit down between your heels until your thighs are at least parallel.",
                "Drive up through the whole foot.",
                "Keep your chest up — the load is on your hips, not your spine.",
            ),
            listOf("Knees caving in", "Cutting depth", "Rising onto the toes"),
        ),
        MachineGuide(
            "glute_kickback", "Glute kickback / multi-hip", "Glute Kickback", "Glutes",
            MuscleGroup.GLUTES, false, true,
            "Chest on the pad, standing leg soft, the working foot on the plate or behind the roller.",
            listOf(
                "Brace your core and keep your hips square.",
                "Push the working leg back and up by squeezing the glute.",
                "Pause at the top without arching your lower back.",
                "Return slowly; finish all reps, then switch legs.",
            ),
            listOf("Arching the lower back", "Swinging the leg", "Twisting the hips open"),
        ),
        MachineGuide(
            "ghd", "Glute-ham developer (GHD)", "Glute-Ham Raise", "Hamstrings, glutes, lower back",
            MuscleGroup.HAMSTRINGS, true, true,
            "Knees just behind the pad, ankles locked under the hooks, feet flat on the plate.",
            listOf(
                "Start upright on your knees, body in a straight line.",
                "Lower forward slowly by straightening at the knee.",
                "Pull back up with your hamstrings, pressing your toes into the plate.",
                "Use a band or your hands to assist until you can do full reps.",
            ),
            listOf("Bending at the hips instead of the knees", "Dropping fast", "Hooks set too loose"),
        ),
        MachineGuide(
            "ab_crunch_machine", "Ab crunch machine", "Machine Crunch", "Abs",
            MuscleGroup.ABS, false, false,
            "Seat height so the chest pads or handles sit at your upper chest.",
            listOf(
                "Brace and curl your ribs down toward your hips.",
                "Squeeze the abs at the bottom for a second.",
                "Return slowly until you feel the stretch.",
                "Breathe out as you crunch.",
            ),
            listOf("Pulling with the arms", "Hinging at the hips instead of curling", "Too heavy for full range"),
        ),
        MachineGuide(
            "rotary_torso", "Rotary torso machine", "Rotary Torso", "Obliques, core",
            MuscleGroup.ABS, false, false,
            "Set the start angle, sit tall with your chest against the pad and knees locked in.",
            listOf(
                "Rotate your torso slowly away from the start side.",
                "Keep your hips still — only the upper body turns.",
                "Return under control.",
                "Do all reps, then set the other side.",
            ),
            listOf("Twisting fast with momentum", "Turning too far", "Letting the hips move"),
        ),
        MachineGuide(
            "landmine", "Landmine", "Landmine Press", "Shoulders, chest, core",
            MuscleGroup.SHOULDERS, true, false,
            "Barbell end in the pivot, plates on the free end, stand facing it in a split stance.",
            listOf(
                "Hold the bar end at your shoulder with one or both hands.",
                "Press it up and forward until your arm is straight.",
                "Lower back to the shoulder under control.",
                "Also works for rows, squats and rotations.",
            ),
            listOf("Leaning back to press", "Shrugging", "Loading more than you can control"),
        ),
        MachineGuide(
            "barbell", "Barbell & plates", "Barbell Deadlift", "Hamstrings, glutes, back",
            MuscleGroup.HAMSTRINGS, true, true,
            "Bar over the middle of your feet, feet hip-width, collars on.",
            listOf(
                "Hinge down and grip just outside your legs; shins touch the bar.",
                "Chest up, back flat, take the slack out of the bar.",
                "Push the floor away and stand up, bar close to your legs.",
                "Lower by pushing your hips back, then bending the knees.",
            ),
            listOf("Rounding the back", "Bar drifting away from the legs", "Jerking it off the floor"),
        ),
        MachineGuide(
            "ez_bar", "EZ curl bar", "EZ Bar Curl", "Biceps",
            MuscleGroup.BICEPS, false, false,
            "Load light plates; grip on the angled parts, palms up.",
            listOf(
                "Stand tall, elbows by your sides.",
                "Curl the bar up without moving your elbows forward.",
                "Squeeze at the top.",
                "Lower slowly to straight arms.",
            ),
            listOf("Swinging with the hips", "Elbows drifting forward", "Dropping the bar"),
        ),
        MachineGuide(
            "medicine_ball", "Medicine / slam ball", "Medicine Ball Slam", "Core, shoulders, conditioning",
            null, false, false,
            "Pick a ball you can lift overhead comfortably; clear space around you.",
            listOf(
                "Lift the ball overhead, rising onto your toes.",
                "Slam it into the floor in front of you, hinging at the hips.",
                "Catch or pick it up with a flat back.",
                "Work 20–30 s, rest 30 s, 6–8 rounds.",
            ),
            listOf("Rounding the back to pick it up", "Using a bouncy ball for slams", "Throwing toward your feet"),
            sport = Sport.HIIT,
        ),
        MachineGuide(
            "plyo_box", "Plyo box", "Box Jump", "Legs, power",
            MuscleGroup.QUADS, true, true,
            "Start with a low box; stand a foot away.",
            listOf(
                "Swing your arms and dip into a quarter squat.",
                "Jump up and land softly with both feet on the box.",
                "Stand up fully on top.",
                "Step down — don't jump down.",
            ),
            listOf("Box too high", "Landing stiff-legged", "Jumping down repeatedly"),
        ),
        MachineGuide(
            "suspension_trainer", "Suspension trainer (TRX)", "Suspension Row", "Back, biceps, core",
            MuscleGroup.BACK, true, false,
            "Straps at mid length; hold the handles and walk your feet forward to lean back.",
            listOf(
                "Body straight like a plank, arms extended.",
                "Pull your chest up to the handles, elbows back.",
                "Lower slowly to straight arms.",
                "Walk your feet further forward to make it harder.",
            ),
            listOf("Hips sagging", "Straps rubbing on the anchor", "Jerky reps"),
        ),
        MachineGuide(
            "resistance_bands", "Resistance bands", "Band Pull-Apart", "Rear delts, upper back",
            MuscleGroup.SHOULDERS, false, false,
            "Pick a light band; hold it at shoulder width, arms straight in front.",
            listOf(
                "Pull the band apart until it touches your chest.",
                "Squeeze your shoulder blades together.",
                "Return slowly.",
                "Great as a warm-up: 2–3 sets of 15–20.",
            ),
            listOf("Bending the elbows", "Shrugging", "Letting the band snap back"),
        ),
        MachineGuide(
            "air_bike", "Air / assault bike", "Air Bike", "Whole body, heart",
            null, false, true,
            "Seat height so your knee is slightly bent at the bottom.",
            listOf(
                "Push and pull the handles while you pedal.",
                "Intervals: 20 s all-out, 40 s easy, 8–10 rounds.",
                "The harder you go, the harder it gets — pace yourself.",
                "Cool down 3–5 minutes easy.",
            ),
            listOf("Sprinting the first round and dying", "Seat too low", "Only using the legs"),
            sport = Sport.HIIT,
        ),
        MachineGuide(
            "ski_erg", "SkiErg", "SkiErg", "Lats, core, heart",
            null, false, false,
            "Stand close, feet hip-width, one handle in each hand overhead.",
            listOf(
                "Pull down by hinging at the hips and driving your arms toward your thighs.",
                "Let the handles rise as you stand tall again.",
                "Steady 10–20 min, or 30 s hard / 30 s easy × 10.",
                "Keep the rhythm smooth.",
            ),
            listOf("All arms, no hips", "Squatting instead of hinging", "Standing too far back"),
            sport = Sport.ROWING,
        ),
        MachineGuide(
            "foam_roller", "Foam roller", "Foam Rolling", "Recovery",
            null, false, false,
            "Use on a mat; start with a softer roller.",
            listOf(
                "Roll slowly over one area — quads, upper back, calves.",
                "Pause on tight spots for 20–30 seconds.",
                "Breathe; it should be uncomfortable, not painful.",
                "Avoid the lower back and joints.",
            ),
            listOf("Rolling fast", "Rolling the lower back", "Going for pain"),
            sport = Sport.YOGA,
        ),
    )

    fun byId(id: String): MachineGuide? = ALL.firstOrNull { it.id == id }
}

/**
 * Which machine a photograph shows, by the same on-device model that reads the body.
 *
 * The photo's embedding is compared with two or three descriptions of every machine; each
 * machine scores its best description, and a softmax at CLIP's own scale turns the scores into
 * a probability. Below [CONFIDENT] the screen says it is unsure and offers the runners-up,
 * because gym machines look alike and a wrong how-to with confidence is worse than a question.
 */
object EquipmentMatcher {

    /**
     * At or above this the top machine is named outright; below it the screen still shows
     * the best guess's guide, with the runners-up offered as "not this one?".
     */
    const val CONFIDENT = 0.35

    /** The id of the "not a machine" class. */
    const val NONE = "__none__"

    /**
     * Machines by probability, most likely first; empty when the inputs are unusable.
     *
     * **Each machine scores the average of its descriptions**, not its best one. Every
     * machine is described several ways — its names in a gym photo and in a catalogue shot,
     * and what it looks like — and a real machine matches most of them a little, where a
     * look-alike matches one of them a lot. Taking the best single description let a lat
     * pulldown / low row combination read as a pec deck; averaging read it as itself at 55%.
     *
     * **The "not a machine" class scores its best two**, because its descriptions are
     * deliberately unlike each other (a person, a room, food) and an average of them matches
     * nothing. Two rather than one, so a gym photo with someone standing in it is not thrown
     * out for containing a person.
     */
    fun rank(image: DoubleArray, prompts: Map<String, List<DoubleArray>>): List<Pair<String, Double>> {
        if (image.isEmpty() || prompts.isEmpty() || image.any { !it.isFinite() }) return emptyList()
        val norm = sqrt(image.sumOf { it * it })
        if (norm == 0.0) return emptyList()
        val unit = DoubleArray(image.size) { image[it] / norm }

        val logits = prompts.mapNotNull { (id, embeddings) ->
            val sims = embeddings.filter { it.size == unit.size }
                .map { e -> e.indices.sumOf { e[it] * unit[it] } }
                .sortedDescending()
                .takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val score = if (id == NONE) sims.take(2).average() else sims.average()
            id to 100.0 * score
        }
        if (logits.isEmpty()) return emptyList()
        val max = logits.maxOf { it.second }
        val weights = logits.map { (id, l) -> id to exp(l - max) }
        val total = weights.sumOf { it.second }
        return weights.map { (id, w) -> id to w / total }.sortedByDescending { it.second }
    }
}
