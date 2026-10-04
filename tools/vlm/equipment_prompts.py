"""Descriptions of gym equipment the on-device model matches a photograph against.

Keys match MachineGuide.id in core/.../workout/Equipment.kt. Every machine is described by
its common names — each in two framings, a gym photo and a catalogue product shot, because
people photograph machines both in the gym and off a website — plus a sentence about what it
looks like. The app scores a photo against the best-matching description per machine.

Regenerate the embeddings with embed_prompts.py after any change.
"""

# id: (names people use, descriptions of what it looks like)
MACHINES = {
    "lat_pulldown": (["lat pulldown machine", "cable lat pulldown"],
                     ["a gym machine with a long wide bar hanging from a high cable and a seat with thigh pads"]),
    "lat_row_combo": (["lat pulldown and low row combination machine", "dual lat pulldown seated row machine"],
                      ["a weight stack machine with a high pulldown bar, thigh pads, and a long bench with a low pulley and foot bar"]),
    "cable_row": (["seated cable row machine", "low pulley row station"],
                  ["a long low bench with a foot platform and a cable handle at knee height"]),
    "cable_station": (["cable crossover machine", "dual adjustable pulley cable station", "functional trainer"],
                      ["two tall weight stack towers with adjustable pulleys and handles"]),
    "leg_press": (["leg press machine", "45 degree leg press"],
                  ["an angled sled with a large foot platform and a reclined seat, loaded with weight plates"]),
    "leg_extension": (["leg extension machine", "quadriceps extension machine"],
                      ["a seated machine with a padded roller in front of the ankles"]),
    "leg_curl": (["lying leg curl machine", "prone hamstring curl machine"],
                 ["a padded bench you lie face down on with a roller over the heels"]),
    "seated_leg_curl": (["seated leg curl machine", "seated hamstring curl"],
                        ["a seated machine with a thigh pad on top and a roller behind the calves"]),
    "smith_machine": (["smith machine", "guided barbell rack"],
                      ["a barbell fixed on two vertical steel guide rails inside a frame"]),
    "squat_rack": (["squat rack", "power rack", "power cage"],
                   ["a tall steel cage with j-hooks, safety bars and a loaded barbell"]),
    "bench_press": (["flat bench press station", "olympic bench press"],
                    ["a flat padded bench with uprights holding a barbell above it"]),
    "adjustable_bench": (["adjustable weight bench", "incline bench"],
                         ["a padded bench with an adjustable backrest set at an incline"]),
    "dumbbells": (["dumbbell rack", "set of dumbbells"],
                  ["rows of hexagonal dumbbells on a rack"]),
    "chest_press_machine": (["seated chest press machine", "plate loaded chest press"],
                            ["a seated machine with two handles at chest height that push forward"]),
    "pec_deck": (["pec deck fly machine", "rear delt fly machine", "butterfly machine"],
                 ["a seated machine with two swinging arms that close in front of the chest"]),
    "shoulder_press_machine": (["seated shoulder press machine", "overhead press machine"],
                               ["a seated machine with handles above shoulder height that push upward"]),
    "lateral_raise_machine": (["lateral raise machine", "deltoid raise machine"],
                              ["a seated machine with pads that lift the arms out to the sides"]),
    "biceps_curl_machine": (["biceps curl machine", "arm curl machine"],
                            ["a seated machine with an angled arm pad and a curling handle"]),
    "triceps_machine": (["triceps extension machine", "seated dip machine"],
                        ["a seated machine with handles you push down beside the body"]),
    "pullup_station": (["assisted pull-up machine", "pull-up and dip station", "chin up bar"],
                       ["a tall frame with high handles and a kneeling pad for assisted pull-ups"]),
    "dip_station": (["dip station", "parallel dip bars"],
                    ["two parallel bars at waist height on a frame"]),
    "hack_squat": (["hack squat machine", "angled squat machine"],
                   ["a steep angled sled with shoulder pads and a foot platform"]),
    "belt_squat": (["belt squat machine", "pendulum squat machine"],
                   ["a squat machine loaded from a hip belt or a swinging shoulder-padded arm"]),
    "hip_thrust": (["hip thrust machine", "glute bridge machine"],
                   ["a machine with a padded bench for the upper back and a padded belt across the hips"]),
    "glute_kickback": (["glute kickback machine", "multi hip machine"],
                       ["a standing machine with a chest pad and a foot plate that pushes backward"]),
    "calf_raise_standing": (["standing calf raise machine"],
                            ["a machine with shoulder pads above a small raised step for the toes"]),
    "calf_raise_seated": (["seated calf raise machine"],
                          ["a seated machine with a knee pad on the thighs and a small foot platform"]),
    "hip_abductor": (["hip abductor machine", "hip adductor machine", "inner outer thigh machine"],
                     ["a seated machine with two leg pads that open and close the knees"]),
    "back_extension": (["back extension bench", "roman chair", "hyperextension bench"],
                       ["an angled bench with a hip pad and ankle rollers"]),
    "ghd": (["glute ham developer", "GHD machine"],
            ["a long horizontal frame with a large rounded pad and ankle hooks"]),
    "preacher_curl": (["preacher curl bench", "scott curl bench"],
                      ["a seat with an angled pad to rest the upper arms on"]),
    "ab_crunch_machine": (["ab crunch machine", "abdominal crunch machine"],
                          ["a seated machine with chest pads or handles that curl the torso forward"]),
    "rotary_torso": (["rotary torso machine", "oblique twist machine"],
                     ["a seated machine with a pad that twists the upper body side to side"]),
    "captains_chair": (["captain's chair knee raise station", "vertical knee raise station"],
                       ["a tall frame with arm rests and a back pad for hanging leg raises"]),
    "tbar_row": (["t-bar row machine", "chest supported row machine"],
                 ["a machine with an angled chest pad and handles you pull toward you"]),
    "landmine": (["landmine attachment", "barbell landmine"],
                 ["a barbell with one end anchored to the floor in a pivot"]),
    "barbell": (["olympic barbell with weight plates", "loaded barbell on the floor"],
                ["a long steel bar with round weight plates on each end"]),
    "ez_bar": (["ez curl bar", "cambered curl bar"],
               ["a short zig-zag shaped bar with small weight plates"]),
    "kettlebell": (["kettlebell", "cast iron kettlebells"],
                   ["a round iron weight with a handle on top"]),
    "medicine_ball": (["medicine ball", "slam ball", "wall ball"],
                      ["a heavy rubber ball used for throws and slams"]),
    "plyo_box": (["plyometric box", "jump box"],
                 ["a sturdy wooden or foam box for box jumps"]),
    "suspension_trainer": (["TRX suspension trainer", "suspension straps"],
                           ["two yellow and black straps with handles hanging from an anchor"]),
    "resistance_bands": (["resistance bands", "exercise bands"],
                         ["colourful elastic bands and loops for exercise"]),
    "battle_ropes": (["battle ropes"],
                     ["two thick heavy ropes anchored to the wall on a gym floor"]),
    "treadmill": (["treadmill", "running machine"],
                  ["a running belt with handrails and a display console"]),
    "stationary_bike": (["exercise bike", "spin bike", "stationary bike"],
                        ["an indoor bicycle with a flywheel and no wheels"]),
    "air_bike": (["air bike", "assault bike", "fan bike"],
                 ["an exercise bike with a large fan wheel in front and moving arm handles"]),
    "rowing_machine": (["indoor rowing machine", "rowing ergometer"],
                       ["a long rail with a sliding seat, foot straps and a flywheel with a handle"]),
    "ski_erg": (["ski erg machine", "skiing ergometer"],
                ["a tall standing machine with two cable handles pulled downward"]),
    "elliptical": (["elliptical cross trainer", "elliptical machine"],
                   ["a machine with moving foot pedals and long moving arm handles"]),
    "stair_climber": (["stair climber machine", "stairmaster"],
                      ["a tall machine with a revolving staircase and handrails"]),
    "foam_roller": (["foam roller"],
                    ["a foam cylinder used for self massage"]),
}

EQUIPMENT_PROMPTS = {
    machine: [f"a photo of a {n} in a gym" for n in names]
    + [f"a product photo of a {n} on a white background" for n in names]
    + descriptions
    for machine, (names, descriptions) in MACHINES.items()
}

# Not a machine. A zero-shot model always picks something; without this class a selfie was
# read as a captain's chair at 74%. When it wins, the app says it found no machine instead of
# teaching the wrong exercise.
EQUIPMENT_PROMPTS["__none__"] = [
    "a photo of a person", "a selfie of a man standing in a room",
    "a photo of an empty room", "a photo of a wall", "a photo of a street",
    "a photo of food", "a photo of a person holding a phone",
]
