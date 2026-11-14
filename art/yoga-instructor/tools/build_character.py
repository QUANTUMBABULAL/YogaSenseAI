"""
YogaSenseAI instructor character — single source of truth for all artwork.

Every body part is drawn ONCE here, in local coordinates with its pivot (joint) at (0, 0).
Rigged SVGs are assembled by nesting parts in the skeleton hierarchy with
translate(child joint) rotate(angle) transforms, so any pose re-uses identical artwork.

Run:  python build_character.py   (writes ../*.svg, ../parts/**, ../rig_spec.json)
"""
import json
import os

OUT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# ---------------------------------------------------------------------------------------------
# Palette (fixed — do not change between poses)
# ---------------------------------------------------------------------------------------------
P = {
    "skin": "#E2A985", "skin_shade": "#C68A63", "skin_line": "#8A5234",
    "skin_far": "#C99173", "skin_far_shade": "#A97354",
    "navy": "#2B3758", "navy_shade": "#18213A", "navy_line": "#0E1426", "navy_hi": "#3A4A72",
    "navy_far": "#222C48", "navy_far_shade": "#131A2E",
    "hair": "#2A1C15", "hair_hi": "#4A3226", "hair_line": "#160E0A",
    "eye": "#24170F", "lip": "#B8624A", "blush": "#E8826A", "coral": "#FF6B4A",
    "white": "#FFFFFF",
}
STROKE = 2.0

# Skeleton dimensions (character units; full height ~ 990)
L = {
    "upper_arm": 160, "forearm": 145, "hand": 70,
    "thigh": 225, "shin": 220,
    "torso": 250,   # waist -> neck base
    "neck": 36,
}


def defs():
    """Shared gradients. Horizontal light-to-shade gives consistent soft shading after rotation."""
    def lg(i, a, b):
        return (f'<linearGradient id="{i}" x1="0" y1="0" x2="1" y2="0">'
                f'<stop offset="0" stop-color="{a}"/><stop offset="0.55" stop-color="{a}"/>'
                f'<stop offset="1" stop-color="{b}"/></linearGradient>')
    return ("<defs>"
            + lg("gSkin", P["skin"], P["skin_shade"])
            + lg("gSkinFar", P["skin_far"], P["skin_far_shade"])
            + lg("gNavy", P["navy"], P["navy_shade"])
            + lg("gNavyFar", P["navy_far"], P["navy_far_shade"])
            + lg("gHair", P["hair_hi"], P["hair"])
            + "</defs>")


def fill(kind, far):
    return {"skin": "url(#gSkinFar)" if far else "url(#gSkin)",
            "navy": "url(#gNavyFar)" if far else "url(#gNavy)"}[kind]


def line(kind):
    return {"skin": P["skin_line"], "navy": P["navy_line"], "hair": P["hair_line"]}[kind]


def shape(d, kind, far=False, extra=""):
    return f'<path d="{d}" fill="{fill(kind, far)}" stroke="{line(kind)}" stroke-width="{STROKE}" stroke-linejoin="round" {extra}/>'


def shape_open(fill_d, stroke_d, kind, far=False):
    """Filled shape whose outline omits the edge that joins the parent, so joints read as continuous."""
    return (f'<path d="{fill_d}" fill="{fill(kind, far)}" stroke="none"/>'
            f'<path d="{stroke_d}" fill="none" stroke="{line(kind)}" stroke-width="{STROKE}" '
            f'stroke-linejoin="round" stroke-linecap="round"/>')


def capsule_open(a, b, length, bulge=3, at=0.45):
    """Outline of a capsule without its top cap."""
    return (f"M {-a} 0 Q {-(a + bulge)} {length * at:.1f} {-b} {length} "
            f"A {b} {b} 0 0 0 {b} {length} Q {a + bulge} {length * at:.1f} {a} 0")


def capsule(a, b, length, bulge=3, at=0.45):
    """Tapered limb from width 2a at the pivot to 2b at the far joint, round caps, slight muscle bulge."""
    return (f"M {-a} 0 Q {-(a + bulge)} {length * at:.1f} {-b} {length} "
            f"A {b} {b} 0 0 0 {b} {length} "
            f"Q {a + bulge} {length * at:.1f} {a} 0 "
            f"A {a} {a} 0 0 0 {-a} 0 Z")


# ---------------------------------------------------------------------------------------------
# Parts (local coordinates, pivot at 0,0; limbs extend toward +y)
# ---------------------------------------------------------------------------------------------
def part_upper_arm(far=False):
    return (shape(capsule(21, 15, L["upper_arm"], 3, 0.35), "skin", far)
            + f'<path d="M -6 30 Q -9 80 -6 120" fill="none" stroke="{P["skin_shade"]}" stroke-width="2" opacity="0.5"/>')


def part_forearm(far=False):
    return shape_open(capsule(16, 11, L["forearm"], 3, 0.3), capsule_open(16, 11, L["forearm"], 3, 0.3), "skin", far)


def part_hand(far=False):
    palm_o = "M -13 -4 Q -18 26 -13 50 Q -6 70 4 70 Q 14 66 15 46 Q 16 22 12 -4"
    palm = palm_o + " Z"
    thumb = "M 9 12 Q 22 20 21 36 Q 19 44 13 40 Q 12 30 6 24 Z"
    fingers = (f'<path d="M -6 48 L -5 64 M 1 50 L 2 68 M 8 48 L 9 62" fill="none" '
               f'stroke="{P["skin_line"]}" stroke-width="1.2" stroke-linecap="round" opacity="0.55"/>')
    return shape_open(palm, palm_o, "skin", far) + shape(thumb, "skin", far) + fingers


def part_thigh(far=False):
    return shape_open(capsule(31, 23, L["thigh"], 5, 0.35), capsule_open(31, 23, L["thigh"], 5, 0.35), "navy", far)


def part_shin(far=False):
    body = shape_open(capsule(23, 14, L["shin"], 6, 0.28), capsule_open(23, 14, L["shin"], 6, 0.28), "navy", far)
    cuff = f'<path d="M -14 {L["shin"] - 4} L 14 {L["shin"] - 4}" stroke="{P["navy_hi"]}" stroke-width="2.4" stroke-linecap="round"/>'
    return body + cuff


def part_foot_front(far=False):
    o = "M -11 -4 Q -13 12 -19 23 Q -25 34 -14 40 L 13 40 Q 24 37 20 24 Q 14 12 11 -4"
    d = o + " Z"
    toes = (f'<path d="M -10 34 L -10 39 M -2 35 L -2 40 M 6 35 L 6 40 M 13 33 L 14 38" '
            f'stroke="{P["skin_line"]}" stroke-width="1.1" stroke-linecap="round" opacity="0.5"/>')
    return shape_open(d, o, "skin", far) + toes


def part_foot_side(far=False):
    o = "M -12 -4 Q -24 12 -18 28 L 66 30 Q 80 28 72 15 Q 50 4 20 -2 Q 15 -5 12 -4"
    return shape_open(o + " Z", o, "skin", far) + f'<path d="M 58 24 Q 64 21 70 22" stroke="{P["skin_line"]}" stroke-width="1.1" fill="none" opacity="0.5"/>'


def part_pelvis_front():
    body = "M -51 -62 Q -60 -30 -68 4 Q -70 32 -54 50 Q -26 60 0 52 Q 26 60 54 50 Q 70 32 68 4 Q 60 -30 51 -62 Z"
    sides = "M -54 50 Q -70 32 -68 4 Q -60 -30 -51 -62 M 54 50 Q 70 32 68 4 Q 60 -30 51 -62"
    crotch = f'<path d="M -10 47 Q 0 55 10 47" stroke="{P["navy_line"]}" stroke-width="1.6" fill="none" opacity="0.8"/>'
    band = f'<path d="M -51 -62 Q 0 -58 51 -62 L 53 -50 Q 0 -45 -53 -50 Z" fill="{P["navy_hi"]}" stroke="{P["navy_line"]}" stroke-width="{STROKE}"/>'
    piping = f'<path d="M -53 -50 Q 0 -45 53 -50" stroke="{P["coral"]}" stroke-width="2.2" fill="none"/>'
    seam = f'<path d="M 0 -46 L 0 44" stroke="{P["navy_shade"]}" stroke-width="1.5" opacity="0.6"/>'
    return shape_open(body, sides, "navy") + band + piping + seam + crotch


def part_pelvis_side():
    body = "M -38 -62 Q -44 -30 -46 0 Q -44 30 -26 44 Q 6 52 28 40 Q 40 20 40 -10 Q 40 -40 38 -62 Z"
    sides = "M -26 44 Q -44 30 -46 0 Q -44 -30 -38 -62 M 28 40 Q 40 20 40 -10 Q 40 -40 38 -62"
    band = f'<path d="M -38 -62 L 38 -62 L 40 -50 L -40 -50 Z" fill="{P["navy_hi"]}" stroke="{P["navy_line"]}" stroke-width="{STROKE}"/>'
    piping = f'<path d="M -40 -50 L 40 -50" stroke="{P["coral"]}" stroke-width="2.2"/>'
    return shape_open(body, sides, "navy") + band + piping


def part_torso_front():
    skin = ("M -51 0 Q -46 -40 -50 -80 Q -60 -130 -62 -170 Q -64 -200 -66 -214 Q -68 -233 -52 -241 "
            "Q -34 -248 -18 -250 L 18 -250 Q 34 -248 52 -241 Q 68 -233 66 -214 Q 64 -200 62 -170 "
            "Q 60 -130 50 -80 Q 46 -40 51 0 Z")
    top = ("M -51 0 Q -46 -40 -50 -80 Q -60 -130 -62 -170 Q -63 -190 -60 -204 L -46 -241 L -31 -241 "
           "Q -26 -206 0 -198 Q 26 -206 31 -241 L 46 -241 L 60 -204 Q 63 -190 62 -170 "
           "Q 60 -130 50 -80 Q 46 -40 51 0 Z")
    seam = f'<path d="M -61 -150 Q 0 -137 61 -150" stroke="{P["navy_hi"]}" stroke-width="2.2" fill="none"/>'
    clav = f'<path d="M -22 -226 Q -12 -222 -6 -224 M 22 -226 Q 12 -222 6 -224" stroke="{P["skin_line"]}" stroke-width="1.2" fill="none" opacity="0.5"/>'
    return shape(skin, "skin") + clav + shape(top, "navy") + seam


def part_torso_side():
    skin = ("M -36 0 Q -40 -50 -36 -100 Q -44 -160 -40 -210 Q -36 -236 -22 -248 L 14 -250 "
            "Q 34 -238 40 -214 Q 48 -186 46 -160 Q 42 -140 34 -128 Q 30 -80 38 0 Z")
    top = ("M -36 0 Q -40 -50 -36 -100 Q -44 -160 -40 -210 Q -38 -228 -30 -238 L -17 -240 "
           "Q -9 -214 12 -208 Q 26 -212 33 -226 Q 44 -206 46 -184 Q 47 -170 46 -160 "
           "Q 42 -140 34 -128 Q 30 -80 38 0 Z")
    seam = f'<path d="M -42 -146 Q 0 -134 40 -136" stroke="{P["navy_hi"]}" stroke-width="2.2" fill="none"/>'
    return shape(skin, "skin") + shape(top, "navy") + seam


def part_neck():
    d = "M -16 6 L -15 -40 L 15 -40 L 16 6 Z"
    shade = f'<path d="M -15 -38 Q 0 -26 15 -38 L 15 -30 Q 0 -18 -15 -30 Z" fill="{P["skin_shade"]}" opacity="0.55"/>'
    return shape(d, "skin") + shade


def part_head_front():
    bun = (f'<circle cx="0" cy="-128" r="25" fill="url(#gHair)" stroke="{P["hair_line"]}" stroke-width="{STROKE}"/>'
           f'<path d="M -12 -140 Q 0 -150 12 -140" stroke="{P["hair_hi"]}" stroke-width="2" fill="none"/>')
    back_hair = shape("M -48 -56 Q -54 -112 0 -118 Q 54 -112 48 -56 Q 44 -40 40 -36 L -40 -36 Q -44 -40 -48 -56 Z", "skin")
    back_hair = back_hair.replace(fill("skin", False), "url(#gHair)").replace(P["skin_line"], P["hair_line"])
    ears = "".join(
        f'<ellipse cx="{s * 44}" cy="-52" rx="7" ry="12" fill="url(#gSkin)" stroke="{P["skin_line"]}" stroke-width="{STROKE}"/>'
        for s in (-1, 1))
    face = shape("M -43 -62 Q -43 -22 -24 -3 Q -10 7 0 7 Q 10 7 24 -3 Q 43 -22 43 -62 "
                 "Q 43 -110 0 -110 Q -43 -110 -43 -62 Z", "skin")
    fringe = ("M -45 -58 Q -48 -104 -6 -114 Q 34 -118 46 -90 Q 48 -76 45 -58 "
              "Q 40 -84 24 -92 Q 4 -100 -16 -94 Q -36 -86 -45 -58 Z")
    hair = f'<path d="{fringe}" fill="url(#gHair)" stroke="{P["hair_line"]}" stroke-width="{STROKE}" stroke-linejoin="round"/>'
    strands = f'<path d="M -20 -104 Q -2 -112 18 -106 M -30 -94 Q -10 -104 10 -100" stroke="{P["hair_hi"]}" stroke-width="1.6" fill="none" opacity="0.8"/>'
    eyes = "".join(
        f'<path d="M {s * 16 - 7} -57 Q {s * 16} -63 {s * 16 + 7} -57 Q {s * 16} -53 {s * 16 - 7} -57 Z" fill="{P["eye"]}"/>'
        f'<circle cx="{s * 16 + 2}" cy="-58.5" r="1.4" fill="{P["white"]}"/>'
        for s in (-1, 1))
    lashes = "".join(f'<path d="M {s * 16 - 8} -58 Q {s * 16} -66 {s * 16 + 8} -58" stroke="{P["eye"]}" stroke-width="1.6" fill="none"/>' for s in (-1, 1))
    brows = "".join(f'<path d="M {s * 8} -71 Q {s * 17} -76 {s * 26} -71" stroke="{P["hair"]}" stroke-width="2.6" stroke-linecap="round" fill="none"/>' for s in (-1, 1))
    nose = f'<path d="M 1 -50 Q -4 -38 1 -35 Q 4 -34 6 -36" stroke="{P["skin_line"]}" stroke-width="1.5" fill="none" stroke-linecap="round"/>'
    mouth = (f'<path d="M -10 -21 Q 0 -13 10 -21 Q 0 -17 -10 -21 Z" fill="{P["lip"]}" stroke="{P["skin_line"]}" stroke-width="1.2" stroke-linejoin="round"/>')
    blush = "".join(f'<ellipse cx="{s * 25}" cy="-33" rx="7" ry="4" fill="{P["blush"]}" opacity="0.28"/>' for s in (-1, 1))
    return bun + back_hair + ears + face + hair + strands + blush + brows + lashes + eyes + nose + mouth


def part_head_side():
    bun = (f'<circle cx="-34" cy="-104" r="24" fill="url(#gHair)" stroke="{P["hair_line"]}" stroke-width="{STROKE}"/>'
           f'<path d="M -46 -114 Q -36 -124 -24 -118" stroke="{P["hair_hi"]}" stroke-width="2" fill="none"/>')
    face = shape("M -36 -60 Q -40 -110 0 -114 Q 34 -114 42 -82 L 45 -66 Q 46 -58 52 -46 "
                 "Q 49 -41 45 -40 L 45 -31 Q 47 -25 43 -21 Q 38 -6 22 2 Q 2 7 -12 0 Q -36 -18 -36 -60 Z", "skin")
    ear = f'<ellipse cx="-6" cy="-52" rx="8" ry="12" fill="url(#gSkin)" stroke="{P["skin_line"]}" stroke-width="{STROKE}"/>'
    hair = (f'<path d="M -40 -46 Q -48 -112 0 -120 Q 36 -122 44 -88 Q 24 -100 4 -96 Q -14 -92 -18 -70 '
            f'Q -22 -52 -40 -46 Z" fill="url(#gHair)" stroke="{P["hair_line"]}" stroke-width="{STROKE}" stroke-linejoin="round"/>')
    eye = (f'<path d="M 26 -58 Q 32 -62 37 -58 Q 32 -55 26 -58 Z" fill="{P["eye"]}"/>'
           f'<path d="M 25 -59 Q 31 -66 38 -60" stroke="{P["eye"]}" stroke-width="1.6" fill="none"/>')
    brow = f'<path d="M 24 -71 Q 32 -75 40 -71" stroke="{P["hair"]}" stroke-width="2.6" stroke-linecap="round" fill="none"/>'
    mouth = f'<path d="M 36 -24 Q 41 -22 44 -26" stroke="{P["lip"]}" stroke-width="2.2" fill="none" stroke-linecap="round"/>'
    blush = f'<ellipse cx="24" cy="-34" rx="8" ry="4" fill="{P["blush"]}" opacity="0.28"/>'
    return bun + face + ear + hair + blush + brow + eye + mouth


# ---------------------------------------------------------------------------------------------
# Rig definition
# ---------------------------------------------------------------------------------------------
def node(name, art, joint, angle=0.0, children_before=(), children_after=()):
    return {"name": name, "art": art, "joint": joint, "angle": angle,
            "before": list(children_before), "after": list(children_after)}






def build_front(pose):
    """pose: dict of joint angles in degrees (SVG rotate: positive = clockwise on screen)."""
    a = lambda k: pose.get(k, 0.0)
    head = node("head", part_head_front(), (0, -L["neck"] + 10), a("head"))
    neck = node("neck", part_neck(), (0, -L["torso"]), a("neck"), children_after=[head])

    def full_arm(side, sx):
        hand = node(f"hand_{side}", part_hand(), (0, L["forearm"]), a(f"hand_{side}"))
        fore = node(f"forearm_{side}", part_forearm(), (0, L["upper_arm"]), a(f"forearm_{side}"), children_after=[hand])
        return node(f"upper_arm_{side}", part_upper_arm(), (sx * 70, -226), a(f"upper_arm_{side}"), children_after=[fore])

    # Arms sit behind the torso at the shoulder so the shoulder line stays clean.
    torso = node("torso", part_torso_front(), (0, -62), a("torso"),
                 children_before=[full_arm("R", -1), full_arm("L", 1)], children_after=[neck])

    def full_leg(side, sx):
        foot = node(f"foot_{side}", part_foot_front(), (0, L["shin"]), a(f"foot_{side}"))
        if sx < 0:  # mirror right foot so toes point outward
            foot["mirror"] = True
        shin = node(f"shin_{side}", part_shin(), (0, L["thigh"]), a(f"shin_{side}"), children_after=[foot])
        return node(f"thigh_{side}", part_thigh(), (sx * 38, 8), a(f"thigh_{side}"), children_after=[shin])

    # Thighs sit behind the pelvis; the pelvis has no lower outline, so the leggings read as one garment.
    return node("pelvis", part_pelvis_front(), (300, 540), a("pelvis"),
                children_before=[full_leg("R", -1), full_leg("L", 1)], children_after=[torso])


def build_side(pose):
    """Profile facing screen-right. 'far' limbs are drawn behind the body in a darker tint."""
    a = lambda k: pose.get(k, 0.0)
    head = node("head", part_head_side(), (0, -L["neck"] + 10), a("head"))
    neck = node("neck", part_neck(), (-2, -L["torso"]), a("neck"), children_after=[head])

    def full_arm(side, far):
        hand = node(f"hand_{side}", part_hand(far), (0, L["forearm"]), a(f"hand_{side}"))
        fore = node(f"forearm_{side}", part_forearm(far), (0, L["upper_arm"]), a(f"forearm_{side}"), children_after=[hand])
        return node(f"upper_arm_{side}", part_upper_arm(far), (2, -226), a(f"upper_arm_{side}"), children_after=[fore])

    torso = node("torso", part_torso_side(), (0, -62), a("torso"),
                 children_before=[full_arm("R", True)], children_after=[neck, full_arm("L", False)])

    def full_leg(side, far, dx):
        foot = node(f"foot_{side}", part_foot_side(far), (0, L["shin"]), a(f"foot_{side}"))
        shin = node(f"shin_{side}", part_shin(far), (0, L["thigh"]), a(f"shin_{side}"), children_after=[foot])
        return node(f"thigh_{side}", part_thigh(far), (dx, 8), a(f"thigh_{side}"), children_after=[shin])

    return node("pelvis", part_pelvis_side(), (300, 540), a("pelvis"),
                children_before=[full_leg("R", True, -6), full_leg("L", False, 4)], children_after=[torso])


def render(n, indent=1):
    pad = "  " * indent
    jx, jy = n["joint"]
    mirror = " scale(-1,1)" if n.get("mirror") else ""
    out = [f'{pad}<g id="{n["name"]}" data-pivot="{jx},{jy}" transform="translate({jx},{jy}) rotate({n["angle"]}){mirror}">']
    for c in n["before"]:
        out.append(render(c, indent + 1))
    out.append(f'{pad}  <g id="{n["name"]}_art">{n["art"]}</g>')
    for c in n["after"]:
        out.append(render(c, indent + 1))
    out.append(f"{pad}</g>")
    return "\n".join(out)


def svg_doc(body, w=600, h=1080, bg=None, extra_defs=""):
    bgr = f'<rect width="{w}" height="{h}" fill="{bg}"/>' if bg else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}">\n'
            f"{defs()}{extra_defs}\n{bgr}\n{body}\n</svg>\n")




def joint_table(root):
    """Walk the rig, applying rotations, to compute global pivot coordinates."""
    import math
    rows = []

    def walk(n, ox, oy, rot, parent):
        jx, jy = n["joint"]
        r = math.radians(rot)
        gx = ox + jx * math.cos(r) - jy * math.sin(r)
        gy = oy + jx * math.sin(r) + jy * math.cos(r)
        rows.append({"part": n["name"], "parent": parent, "local_pivot": [jx, jy],
                     "global_pivot": [round(gx, 1), round(gy, 1)], "angle": n["angle"]})
        for c in n["before"] + n["after"]:
            walk(c, gx, gy, rot + n["angle"], n["name"])
    walk(root, 0, 0, 0, None)
    return rows


# Neutral master poses (A-pose front so the armpit and joints are clear for rigging).
NEUTRAL_FRONT = {"upper_arm_L": -9, "upper_arm_R": 9, "forearm_L": -3, "forearm_R": 3,
                 "thigh_L": -1.5, "thigh_R": 1.5}
NEUTRAL_SIDE = {"upper_arm_L": 4, "upper_arm_R": -2, "forearm_L": -6, "forearm_R": -4}

PART_FUNCS_FRONT = {
    "head": part_head_front, "neck": part_neck, "torso": part_torso_front, "pelvis": part_pelvis_front,
    "upper_arm": part_upper_arm, "forearm": part_forearm, "hand": part_hand,
    "thigh": part_thigh, "shin": part_shin, "foot": part_foot_front,
}
PART_FUNCS_SIDE = {
    "head": part_head_side, "neck": part_neck, "torso": part_torso_side, "pelvis": part_pelvis_side,
    "upper_arm": part_upper_arm, "forearm": part_forearm, "hand": part_hand,
    "thigh": part_thigh, "shin": part_shin, "foot": part_foot_side,
}
CHILD_JOINT = {"upper_arm": (0, L["upper_arm"]), "forearm": (0, L["forearm"]), "thigh": (0, L["thigh"]),
               "shin": (0, L["shin"]), "torso": (0, -L["torso"]), "neck": (0, -L["neck"] + 10)}
PART_BOX = {  # viewBox for standalone part files: x, y, w, h (pivot at 0,0)
    "head": (-70, -160, 140, 175), "neck": (-25, -50, 50, 65), "torso": (-85, -260, 170, 270),
    "pelvis": (-80, -75, 160, 135), "upper_arm": (-35, -30, 70, 205), "forearm": (-25, -22, 50, 182),
    "hand": (-25, -10, 52, 88), "thigh": (-45, -40, 90, 300), "shin": (-35, -30, 70, 265), "foot": (-30, -12, 115, 58),
}


def write(path, text):
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


def main():
    front = build_front(NEUTRAL_FRONT)
    side = build_side(NEUTRAL_SIDE)
    write(os.path.join(OUT, "instructor_front_rig.svg"), svg_doc(render(front)))
    write(os.path.join(OUT, "instructor_side_rig.svg"), svg_doc(render(side)))

    # Standalone part files, pivot at origin.
    for view, funcs in (("front", PART_FUNCS_FRONT), ("side", PART_FUNCS_SIDE)):
        for name, fn in funcs.items():
            if view == "side" and name not in ("head", "torso", "pelvis", "foot"):
                continue  # identical to the front parts; reuse them
            x, y, w, h = PART_BOX[name]
            if view == "side" and name == "foot":
                x, w = -30, 115
            write(os.path.join(OUT, "parts", view, f"{name}.svg"),
                  f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{x} {y} {w} {h}" width="{w * 2}" height="{h * 2}">'
                  f'{defs()}<g id="{name}" data-pivot="0,0">{fn()}</g></svg>\n')

    spec = {
        "units": "character units (SVG user units); neutral full height ~ 990",
        "angle_convention": "SVG rotate(): degrees, positive = clockwise on screen, 0 = limb pointing straight down "
                            "(torso/neck/head: 0 = upright). Child angles are relative to the parent.",
        "side_convention": "L/R = the instructor's own left/right. In the front view the instructor faces the viewer, "
                           "so *_L parts are on the viewer's right.",
        "segment_lengths": L,
        "hierarchy": "pelvis(root) -> [thigh_L/R -> shin -> foot], torso -> [neck -> head, upper_arm_L/R -> forearm -> hand]",
        "front_neutral": {"pose": NEUTRAL_FRONT, "joints": joint_table(front)},
        "side_neutral": {"pose": NEUTRAL_SIDE, "joints": joint_table(side)},
        "palette": P,
    }
    write(os.path.join(OUT, "rig_spec.json"), json.dumps(spec, indent=2))

    # Exploded parts sheet (documentation only; labels allowed here, not in the art).
    cells, x, y, rowh = [], 30, 40, 0
    order = ["head", "neck", "torso", "pelvis", "upper_arm", "forearm", "hand", "thigh", "shin", "foot"]
    for view, funcs in (("front", PART_FUNCS_FRONT), ("side", PART_FUNCS_SIDE)):
        for name in order:
            if view == "side" and name not in ("head", "torso", "pelvis", "foot"):
                continue
            bx, by, bw, bh = PART_BOX[name]
            if x + bw > 1180:
                x, y, rowh = 30, y + rowh + 60, 0
            ox, oy = x - bx, y - by
            child = CHILD_JOINT.get(name)
            extra = {"torso": [(-70, -226), (70, -226)] if view == "front" else [(2, -226)],
                     "pelvis": [(-38, 8), (38, 8), (0, -62)] if view == "front" else [(0, 8), (0, -62)]}.get(name, [])
            dots = f'<circle cx="0" cy="0" r="6" fill="#FF6B4A" stroke="#fff" stroke-width="2"/>'
            for jx, jy in ([child] if child else []) + extra:
                dots += f'<circle cx="{jx}" cy="{jy}" r="5" fill="#B7F36B" stroke="#171C28" stroke-width="1.5"/>'
            label = f"{view}/{name}" if view == "side" else name
            cells.append(f'<g transform="translate({ox},{oy})">{funcs[name]()}{dots}</g>'
                         f'<text x="{x + bw / 2}" y="{y + bh + 24}" text-anchor="middle" font-family="sans-serif" '
                         f'font-size="15" fill="#171C28">{label}</text>')
            x += bw + 40
            rowh = max(rowh, bh)
    legend = ('<text x="30" y="24" font-family="sans-serif" font-size="15" fill="#5E6472">'
              'Coral dot = part pivot (attaches to parent). Lime dots = where child parts attach.</text>')
    sheet_h = y + rowh + 70
    write(os.path.join(OUT, "parts_sheet.svg"), svg_doc(legend + "".join(cells), 1200, sheet_h, bg="#F5F3EE"))
    print("ok")


if __name__ == "__main__":
    main()
