"""The descriptions the on-device model compares a photograph against.

Three phrasings of the same five bodies. A single wording is a single guess about how the
model reads language; averaging three is what made the result stable when it was tested —
every phrasing ranked a stage-lean bodybuilder below two photographs of a softer man, with
gaps of 6 to 9 points, and none of them depended on a number the model cannot read.

**Numbers in prompts do not work.** "A man with 15 percent body fat" was tried first and
scored every photograph within a point of 16%. CLIP learned what bodies look like from
captions, and captions describe what is visible, not what a DEXA scan would say.

Changing any line here changes every reading the app produces. Regenerate the embeddings
with embed_prompts.py and say why in the commit.
"""

PROMPT_SETS = [
    [
        (5, "a shirtless bodybuilder on stage, extremely shredded, deeply separated abs, "
            "striated muscle, visible veins"),
        (10, "a shirtless lean athletic man with a clearly visible six pack"),
        (15, "a shirtless fit man, upper abs faint, flat but soft lower stomach"),
        (20, "a shirtless average man, no visible abs, soft rounded stomach"),
        (28, "a shirtless overweight man with a large belly"),
    ],
    [
        (5, "photo of a contest-ready physique competitor, paper-thin skin, every abdominal "
            "muscle sharply outlined"),
        (10, "photo of a lean man with a defined six-pack and obliques"),
        (15, "photo of a reasonably fit man whose lower belly is a little soft"),
        (20, "photo of an ordinary man with a soft midsection and no abs showing"),
        (28, "photo of a heavy man with a big round belly"),
    ],
    [
        (5, "a shredded bodybuilder"),
        (10, "a lean man with abs"),
        (15, "a fit man with a slightly soft stomach"),
        (20, "an average man with a soft belly"),
        (28, "an overweight man"),
    ],
]
