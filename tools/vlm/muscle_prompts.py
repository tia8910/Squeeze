"""The descriptions the on-device model weighs each muscle group against.

Each group is judged on its own crop of the photograph (PhysiqueRegions in the core module)
against pairs of sentences: a developed version of that group and an undeveloped one. The
group's score is the probability the model gives the developed side, averaged over the
pairs, so no single wording decides it.

Chosen from two candidate sets on the five test photographs, for one reason: this set
separated the stage-lean bodybuilder from the soft-stomach photographs on every group, and
read the same lean man most alike whether he was framed waist-up or full length. The first
set's abdominal pair ("visible abs" against "a soft belly") scored a soft stomach as a
strength, because next to "a soft belly" any stomach looks firm; these pairs contrast a
visible six-pack with a flat stomach that shows none, which is the distinction a coach draws.

Changing a line changes every report. Regenerate with embed_prompts.py.
"""

MUSCLE_PROMPTS = {
    "SHOULDERS": [
        ("a photo of big, round, muscular deltoids",
         "a photo of small, narrow, untrained shoulders"),
        ("muscular shoulders", "skinny shoulders"),
        ("capped, broad shoulders of a bodybuilder", "average shoulders of an untrained man"),
    ],
    "CHEST": [
        ("a photo of a thick, full, muscular chest", "a photo of a flat, thin, untrained chest"),
        ("big pecs", "a flat chest"),
        ("a well developed chest with defined pectorals", "an average chest with little muscle"),
    ],
    "ARMS": [
        ("a photo of a big muscular arm, large biceps and triceps",
         "a photo of a thin, skinny, untrained arm"),
        ("a muscular arm", "a skinny arm"),
        ("a well trained arm with visible biceps", "an average untrained arm"),
    ],
    "ABS": [
        ("a stomach with a clearly visible six pack", "a flat stomach with no visible abs"),
        ("sharply defined abdominal muscles", "a smooth stomach"),
        ("a lean ripped midsection", "an average midsection"),
    ],
    "V_TAPER": [
        ("a man with a wide V-taper, broad lats and a narrow waist",
         "a man with a straight, blocky torso and no V-taper"),
        ("an athletic V-shaped torso", "a rectangular torso"),
    ],
    "LEGS": [
        ("a photo of big muscular legs, thick quads", "a photo of thin, skinny, untrained legs"),
        ("muscular thighs", "skinny thighs"),
        ("well developed quadriceps", "average untrained thighs"),
    ],
}


# Per-wording calibration: [mid, scale] of the logit difference 100·(developed − undeveloped)·image,
# or None for a wording dropped because it scored trained physiques no higher than average
# ones. Fitted on six reference photographs (one bodybuilder; one lean, well-trained man at two
# framings; one recreationally trained man, FFMI about 20, three times). The photographs are not
# in this repository and must never be: they are people's bodies. The scorer maps each group so
# the average reference reads 0.4 and the trained ones 0.8 — see MuscleScorer in the core
# module. Change a wording and its calibration is void: refit before shipping.
MUSCLE_CALIBRATION = {
    "SHOULDERS": [
        [
            -4.115,
            1.637
        ],
        [
            2.973,
            1.0
        ],
        [
            -3.663,
            1.065
        ]
    ],
    "CHEST": [
        [
            -1.752,
            1.538
        ],
        [
            0.758,
            1.827
        ],
        None
    ],
    "ARMS": [
        [
            -1.027,
            2.091
        ],
        [
            1.079,
            1.994
        ],
        [
            3.953,
            1.0
        ]
    ],
    "ABS": [
        [
            2.629,
            1.0
        ],
        [
            1.269,
            3.277
        ],
        [
            2.091,
            2.949
        ]
    ],
    "V_TAPER": [
        None,
        [
            0.896,
            1.264
        ]
    ],
    "LEGS": [
        [
            2.115,
            3.261
        ],
        [
            1.113,
            1.94
        ],
        None
    ]
}
