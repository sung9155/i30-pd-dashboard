#!/usr/bin/env python3
"""Convert the Sketchfab "hyundai i30 n" glTF into the 내 차 (3D car) screen's asset.

    python3 scripts/car3d_model.py <unzipped glTF folder> [--out apps/dashboard/assets/car3d]

Model: "hyundai i30 n" by amogusstrikesback2, https://sketchfab.com/3d-models/hyundai-i30-n-90c3bd24390b46299cf01c16d5e8091f,
CC BY 4.0. The mesh and lamp atlas written here are an adaptation of it (apps/dashboard/assets/car3d/LICENSE.txt); this
script itself is MIT like the rest of the repository. The material and node names below are this model's: the script
is i30-specific (the Veloster N converter it replaces read a different model).

What the conversion does, for a Telechips tcc893x (GLES 2.0) at 800x480:
  * metres (the glTF already is), origin at the ground under the middle of the car, +Z forward, +Y up, +X the car's left
  * the model is light (about 31k triangles), so nothing is decimated (only the tail lamp lens is cut finer, to about
    39k, so its clear bars and its red light apart); it drops the light-beam cones, the flare sprites and the decal
    with the tail lamp's picture (drawn solid it hid the lens and its clear parts), and both number plates (their
    picture is a game logo) become plate parts the app draws (blank, or the owner's text); the front plate's edges and
    the grille behind it are painted grille black
  * bakes the body / tyre / rim colours into vertex colours; Material.001's clearcoat map marks the body paint (CarPaint)
  * the chrome / detail parts (grille, badges, lamp units) and the glow decals keep their textures: one lamp atlas,
    Material.002 on the left half, Material.006 (glows) on the right; the glow map is the same picture
  * the four wheels (rim + tyre: spin + steer) and the callipers (steer only) are separate nodes in the glTF
  * lamp groups: the glow decals by node (brakes = stop, reverse, rear position guides = tail, the bar low in the front
    bumper = turn signal); the lamp units by where they sit and the colour under them (red at the back = tail, the
    tail lamp's lower clear bar = turn signal on the outer part, reversing lamp on the inner part, the inner part's LED
    row = brake, the headlamp's round elements = headlamp). Rear lamps as on the owner's i30 PD (photos). The model has no amber lamp: the glow map is recoloured amber on the turn signals' white /
    clear texels only (red under the high-mount stop lamp's orange picture); the tail lamp's red glows by its redness,
    its clear parts stay unlit
  * run it again on the same zip and the output is byte for byte the same

Needs numpy and Pillow (development only; the app just reads the output).

File format (little endian), assets/car3d/i30n.bin:
  "VN3D" u32 version=2, u32 parts
  per part: u8 program (0 body, 1 lamp, 2 glass, 3 number plate), u8 motion (0 fixed, 1 wheel, 2 calliper), u8 corner (0 FL, 1 FR,
            2 RL, 3 RR), u8 0, f32 pivot x y z, u32 vertices, u32 indices, vertices*28 bytes, indices*u16 (padded to 4)
  vertex (28 bytes): f32 x y z, s16 nx ny nz (/32767), u16 lamp group (8 = body paint), u8 r g b a (body: a = gloss, glass: a = opacity),
          u16 u v (/65535)
  number plates: u v 0..1 across the plate as seen from outside (u to the right, v down), for the app's plate picture
  then u32 spots, per spot: u8 lamp group, 3 x u8 0, f32 x y z, f32 normal x y z, f32 radius (glow halos)
"""
import argparse
import json
import struct
from pathlib import Path

import numpy as np
from PIL import Image

VERSION = 2
OUT_NAME = 'i30n.bin'
BODY, LAMP, GLASS, PLATE = 0, 1, 2, 3
FIXED, WHEEL, CALLIPER = 0, 1, 2
FL, FR, RL, RR = 0, 1, 2, 3
# Lamp groups (CarScene.java uses the same numbers).
NONE, HEAD, POSITION, TAIL, STOP, TURN_L, TURN_R, REVERSE = 0, 1, 2, 3, 4, 5, 6, 7
PAINT = 8   # body vertices that take the chosen factory colour (CarPaint)

ATLAS = 512   # each half of the lamp atlas (Material.002 left, Material.006 right)
# Gloss (vertex alpha of body parts): how much of the fake sky reflection a material shows.
GLOSS = {'paint': 1.0, 'Material.001': 0.15, 'Material.002': 0.6, 'Material.003': 0.05, 'Material.004': 0.05,
         'Material.005': 0.3}
CALLIPER_GLOSS = 0.5
WINDOW, LENS = (0.02, 0.025, 0.03), (0.9, 0.92, 0.95)
OPACITY_WINDOW, OPACITY_LENS = 0.62, 0.12
GRILLE = (0.05, 0.05, 0.05)   # the grille's black, under the front plate and on the plate's edges
# Glow colour where the lamp's own picture has the wrong one: the turn signals (chrome / red pictures) glow amber, the
# high-mount stop lamp (an orange triangle picture) red.
AMBER, RED = (1.0, 0.55, 0.08), (1.0, 0.08, 0.05)
# The front plate: a flat face in front of the grille whose picture (a game logo) sits in Material.002 at this UV box;
# its edges are mapped to a sliver inside the box. The face becomes a number plate part; the box is painted grille black
# so the edges (and the grille behind the plate) show no light outline.
FRONT_PLATE_FACE = (0.169, 0.803, 0.568, 0.886)
FRONT_PLATE = (0.165, 0.799, 0.572, 0.890)

CT = {5120: np.int8, 5121: np.uint8, 5122: np.int16, 5123: np.uint16, 5125: np.uint32, 5126: np.float32}
NC = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'MAT4': 16}


def quat_mat(q):
    x, y, z, w = q
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def local(n):
    if 'matrix' in n:
        return np.array(n['matrix'], dtype=np.float64).reshape(4, 4).T
    m = np.eye(4)
    if 'scale' in n:
        m = np.diag(list(n['scale']) + [1]) @ m
    if 'rotation' in n:
        r = np.eye(4); r[:3, :3] = quat_mat(n['rotation']); m = r @ m
    if 'translation' in n:
        t = np.eye(4); t[:3, 3] = n['translation']; m = t @ m
    return m


def load(folder):
    g = json.load(open(folder / 'scene.gltf'))
    buf = (folder / g['buffers'][0]['uri']).read_bytes()

    def acc(i):
        a = g['accessors'][i]; v = g['bufferViews'][a['bufferView']]; n = NC[a['type']]; dt = np.dtype(CT[a['componentType']])
        off = v.get('byteOffset', 0) + a.get('byteOffset', 0); stride = v.get('byteStride', 0) or n * dt.itemsize
        raw = np.frombuffer(buf, dtype=np.uint8, count=stride * (a['count'] - 1) + n * dt.itemsize, offset=off)
        return np.lib.stride_tricks.as_strided(raw, shape=(a['count'], n * dt.itemsize), strides=(stride, 1)).copy().view(dt).reshape(a['count'], n)

    def image(info):
        if not info:
            return None
        uri = g['images'][g['textures'][info['index']]['source']]['uri']
        return np.asarray(Image.open(folder / uri).convert('RGBA')).astype(np.float64) / 255

    prims = []

    def walk(i, M):
        n = g['nodes'][i]; W = M @ local(n); name = n.get('name', '')
        for p in (g['meshes'][n['mesh']]['primitives'] if 'mesh' in n else []):
            at = p['attributes']; mat = g['materials'][p['material']]
            P = acc(at['POSITION']).astype(np.float64); N = acc(at['NORMAL']).astype(np.float64)
            UV = acc(at['TEXCOORD_0']).astype(np.float64) if 'TEXCOORD_0' in at else np.zeros((len(P), 2))
            I = acc(p['indices']).reshape(-1, 3).astype(np.int64)
            P = (np.c_[P, np.ones(len(P))] @ W.T)[:, :3]
            N = N @ np.linalg.inv(W[:3, :3]); N /= np.linalg.norm(N, axis=1, keepdims=True) + 1e-12
            if np.linalg.det(W[:3, :3]) < 0:
                I = I[:, ::-1]
            pbr = mat.get('pbrMetallicRoughness', {})
            coat = mat.get('extensions', {}).get('KHR_materials_clearcoat', {}).get('clearcoatTexture')
            prims.append(dict(node=name, mat=mat['name'], P=P, N=N, UV=UV, I=I,
                              factor=np.array(pbr.get('baseColorFactor', [1, 1, 1, 1]), dtype=np.float64),
                              base=image(pbr.get('baseColorTexture')), coat=image(coat),
                              blend=mat.get('alphaMode') == 'BLEND'))
        for c in n.get('children', []):
            walk(c, W)

    for r in g['scenes'][0]['nodes']:
        walk(r, np.eye(4))
    return prims


def sample(img, uv):
    h, w = img.shape[:2]
    x = np.clip(((uv[..., 0] % 1.0) * w).astype(int), 0, w - 1)
    y = np.clip(((uv[..., 1] % 1.0) * h).astype(int), 0, h - 1)
    return img[y, x]


def corner_uv(p):
    """Per triangle corner UVs pulled 20 % toward the triangle's centre: a corner on a UV island's edge would
    otherwise pick up the colour of the next island."""
    uv = p['UV'][p['I']]
    return uv * 0.8 + uv.mean(1, keepdims=True) * 0.2


def tri_colour(p):
    """Mean texture colour under each triangle (centre and the three pulled-in corners)."""
    uv = corner_uv(p)
    return (sample(p['base'], uv).sum(1) + sample(p['base'], uv.mean(1)))[:, :3] / 4


def corner_of(c):
    """FL/FR/RL/RR from a point: +X is the car's left, +Z the front."""
    return (FL if c[0] > 0 else FR) if c[2] > 0 else (RL if c[0] > 0 else RR)


def dropped(p):
    """Triangles to leave out: the light-beam cones and the lens-flare sprites in front of the headlamps, and the decal
    with the whole tail lamp's picture and the reversing strip (in the game they are added on as light; drawn solid they
    hide the lens behind them, which has the lamp's own picture with its clear parts)."""
    if p['mat'] == 'Material.006' and 'position_back' in p['node']:
        return lamp_picture(p)
    if p['mat'] == 'Material.006' and 'reverse' in p['node']:   # a flat white strip over the lens's reversing bar
        return np.ones(len(p['I']), bool)
    if p['mat'] != 'Material.006' or 'front_and_back' not in p['node']:
        return np.zeros(len(p['I']), bool)
    c = part_centres(p)
    return (c[:, 2] > 1.5) & (c[:, 1] > 0.55)   # in front, every piece but the bar low in the bumper


def part_centres(p):
    """Per triangle: the centre of the connected piece it belongs to (vertices welded by position), so a lamp piece is
    sorted as a whole."""
    _, weld = np.unique(np.round(p['P'], 4), axis=0, return_inverse=True)
    I = weld.reshape(-1)[p['I']]
    parent = np.arange(weld.max() + 1)

    def root(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]; a = parent[a]
        return a
    for a, b, c in I:
        ra = root(a); parent[root(b)] = ra; parent[root(c)] = ra
    label = np.array([root(t[0]) for t in I])
    c = p['P'][p['I']].mean(1); out = np.empty_like(c)
    for k in np.unique(label):
        out[label == k] = c[label == k].mean(0)
    return out


def side_turn(c):
    return np.where(c[:, 0] > 0, TURN_L, TURN_R)


# The tail lamp picture of Material.002, laid out like the owner's i30 PD rear lamps (photos): each lamp is an inner
# part on the tailgate and an outer part on the body, split by the seam at u = LENS_SEAM_U. Both have the red C light
# guide (tail lamp). The clear ribbed bar along them (CLEAR_BAND, v range; low on the car: the picture is upside down
# on the mesh) is the turn signal on the outer part and the reversing lamp on the inner part. The inner part's row of
# small LEDs (BRAKE_ROW, u0 v0 u1 v1) is the brake lamp. Only white / clear texels of the bar light amber / white.
LENS_SEAM_U = 0.225
CLEAR_BAND = (0.495, 0.548)
BRAKE_ROW = (0.02, 0.553, 0.225, 0.59)
# A lit clear bar reads as a lamp, not a paint fill: its glow keeps the lens's own ribbed pattern, is brightest at the
# bulb and falls off along the bar, and stays below the shader's saturation (glow x 1.9 at full) except at the core.
# The reversing bulb sits at the bar's inner end (the owner's photo), the turn signal's at the outer end.
COOL = (0.86, 0.93, 1.0)        # the reversing lamp's white
REVERSE_HOT_U, REVERSE_FALL = 0.065, 0.06
TURN_HOT_U, TURN_FALL = 0.44, 0.12
LIT_GAIN = 0.52


def bar_falloff(u, group):
    """Brightness along a rear clear bar (u: Material.002 UV) for the reversing lamp or the turn signal, 0..1."""
    if group == REVERSE:
        return 0.3 + 0.7 * np.exp(-np.maximum(u - REVERSE_HOT_U, 0) / REVERSE_FALL)
    return 0.45 + 0.55 * np.exp(-np.maximum(TURN_HOT_U - u, 0) / TURN_FALL)


def lens_pattern(img, m):
    """The lens's own ribs / grid under mask m, 0.5..1 (its whiteness, spread between the 10th and 90th percentile)."""
    v = img[m].min(1)
    lo, hi = np.percentile(v, 10), np.percentile(v, 90)
    return 0.5 + 0.5 * np.clip((v - lo) / max(hi - lo, 1e-3), 0, 1)


def white_px(img):
    """Texels that look white / clear (not red, not dark)."""
    return (img.min(-1) > 0.5 * img.max(-1)) & (img.max(-1) > 0.45)


def atlas_half(img):
    """A picture as it lands in one half of the lamp atlas (ATLAS x ATLAS, RGB 0..1)."""
    im = Image.fromarray((np.clip(img[..., :3], 0, 1) * 255).astype(np.uint8)).resize((ATLAS, ATLAS), Image.LANCZOS)
    return np.asarray(im).astype(np.float64) / 255


def white_share(img, uv):
    """Per triangle (uv: n x 3 x 2): the share of its picture that is white (7 points across it)."""
    w = np.array([[1, 0, 0], [0, 1, 0], [0, 0, 1], [1, 1, 1], [4, 1, 1], [1, 4, 1], [1, 1, 4]], float)
    return white_px(sample(img, np.einsum('kj,njd->nkd', w / w.sum(1, keepdims=True), uv))).mean(1)


def tail_lens(p):
    """Per triangle: part of the tail lamp lens (one piece per side on the hatch's corners, drawn from the tail lamp
    picture of Material.002; the HYUNDAI letters next to it are not)."""
    c = part_centres(p); uv = p['UV'][p['I']].mean(1)
    return (c[:, 2] < -1.55) & (np.abs(c[:, 0]) > 0.5) & (c[:, 1] > 0.75) & (c[:, 1] < 1.2) & (uv[:, 0] < 0.5) & (uv[:, 1] > 0.47) & (uv[:, 1] < 0.66)


def plate_face(p):
    """Per triangle: a number plate: the rear plate (Material.005) or the front plate's face in front of the grille."""
    if p['mat'] == 'Material.005':
        return np.ones(len(p['I']), bool)
    if p['mat'] != 'Material.002' or 'Chrome' not in p['node']:
        return np.zeros(len(p['I']), bool)
    t = p['P'][p['I']]; uvc = p['UV'][p['I']].mean(1)
    n = np.cross(t[:, 1] - t[:, 0], t[:, 2] - t[:, 0]); n /= np.linalg.norm(n, axis=1, keepdims=True) + 1e-12
    u0, v0, u1, v1 = FRONT_PLATE_FACE
    return (uvc[:, 0] > u0) & (uvc[:, 0] < u1) & (uvc[:, 1] > v0) & (uvc[:, 1] < v1) & (np.abs(n[:, 2]) > 0.9)


def plate_uv(tri):
    """UVs across a plate as seen from outside it: u to the viewer's right, v down, both 0..1 over the plate."""
    out = np.array([0, 0, 1.0 if tri[..., 2].mean() > 0 else -1.0]); up = np.array([0, 1.0, 0])
    right = np.cross(-out, up)
    s = tri @ right; t = tri @ up
    return np.stack([(s - s.min()) / np.ptp(s), (t.max() - t) / np.ptp(t)], -1)


def split(p, mask, times=2):
    """The masked triangles cut into 4 ** times (midpoints), so the lens's white patch and its red can be told apart."""
    P, N, UV, I = p['P'], p['N'], p['UV'], p['I']
    keep, cut = I[~mask], I[mask]
    for _ in range(times):
        a, b, c = cut[:, 0], cut[:, 1], cut[:, 2]
        n0 = len(P); k = len(cut)
        mids = [(a, b), (b, c), (c, a)]
        P = np.vstack([P] + [(P[x] + P[y]) / 2 for x, y in mids])
        Nm = [(N[x] + N[y]) for x, y in mids]
        N = np.vstack([N] + [m / (np.linalg.norm(m, axis=1, keepdims=True) + 1e-12) for m in Nm])
        UV = np.vstack([UV] + [(UV[x] + UV[y]) / 2 for x, y in mids])
        ab, bc, ca = n0 + np.arange(k), n0 + k + np.arange(k), n0 + 2 * k + np.arange(k)
        cut = np.vstack([np.c_[a, ab, ca], np.c_[ab, b, bc], np.c_[ca, bc, c], np.c_[ab, bc, ca]])
    return dict(p, P=P, N=N, UV=UV, I=np.vstack([keep, cut]))


def decal_groups(p):
    """Glow decals (Material.006): the lamp group from the node. The bar low in the front bumper is the turn signal
    (the owner's i30 PD: that bar blinks); the rear light guides are the tail lamp."""
    c = part_centres(p); n = len(c); node = p['node']
    if 'brakes' in node:
        return np.full(n, STOP)
    if 'reverse' in node:
        return np.full(n, REVERSE)
    return np.where(c[:, 2] > 0, side_turn(c), TAIL)


def lamp_picture(p):
    """Per triangle: the rear decal that carries the whole tail lamp's picture (Material.006, right of u 0.62)."""
    return ('position_back' in p['node']) & (p['UV'][p['I']].mean(1)[:, 0] > 0.62) & (p['P'][p['I']].mean(1)[:, 2] < 0)


def unit_groups(p):
    """Chrome / detail triangles (Material.002) that belong to a lamp unit, from where they sit and the colour under
    them. The round elements in each headlamp are the headlamp. Red at the back is the tail lamp, in the middle up high
    the high-mount stop lamp; the tail lamp lens as on the owner's i30 PD (LENS_SEAM_U); the red reflectors low in the
    bumper do not light."""
    c = part_centres(p); e = tri_colour(p); r, g, b = e.T
    out = np.full(len(c), NONE)
    red = (r > 0.25) & (g < 0.45 * r) & (b < 0.45 * r)
    x = np.abs(c[:, 0])
    rear = (c[:, 2] < -1.55) & (c[:, 1] > 0.75)
    out[rear & red] = np.where((x < 0.2) & (c[:, 1] > 1.2), STOP, TAIL)[rear & red]
    uvc = p['UV'][p['I']].mean(1)
    lens = tail_lens(p)
    out[lens] = TAIL   # the red C light guides (and what else of the lens is red)
    u0, v0, u1, v1 = BRAKE_ROW
    brake = lens & (uvc[:, 0] > u0) & (uvc[:, 0] < u1) & (uvc[:, 1] > v0) & (uvc[:, 1] < v1)
    out[brake] = STOP
    img = atlas_half(p['base'])   # judged on the atlas's own texels, as the glow map is recoloured
    clear = (white_share(img, p['UV'][p['I']]) >= 0.5) | white_px(sample(img, uvc))   # mostly clear, or clear in its middle
    bar = lens & clear & (uvc[:, 1] > CLEAR_BAND[0]) & (uvc[:, 1] < CLEAR_BAND[1])
    outer = bar & (uvc[:, 0] >= LENS_SEAM_U)
    out[outer] = side_turn(c)[outer]
    out[bar & ~outer] = REVERSE
    out[(c[:, 2] > 1.75) & (x > 0.5) & (c[:, 1] > 0.6) & (c[:, 1] < 0.85) & (e.max(1) > 0.3)] = HEAD
    return out


class Part:
    def __init__(self, program, motion, corner):
        self.program, self.motion, self.corner = program, motion, corner
        self.pos, self.nrm, self.group, self.col, self.uv = [], [], [], [], []

    def add(self, P, N, group, col, uv):   # triangle soups: (n,3,3) (n,3,3) (n,) (n,3,4) (n,3,2)
        self.pos.append(P); self.nrm.append(N); self.group.append(np.repeat(group[:, None], 3, 1)); self.col.append(col); self.uv.append(uv)

    def soup(self):
        return (np.concatenate(self.pos).reshape(-1, 3), np.concatenate(self.nrm).reshape(-1, 3),
                np.concatenate(self.group).reshape(-1), np.concatenate(self.col).reshape(-1, 4), np.concatenate(self.uv).reshape(-1, 2))


def build(prims):
    keep = {id(p): ~dropped(p) for p in prims}
    allP = np.vstack([p['P'][p['I'][keep[id(p)]]].reshape(-1, 3) for p in prims])
    lo, hi = allP.min(0), allP.max(0)
    offset = np.array([-(lo[0] + hi[0]) / 2, -lo[1], -(lo[2] + hi[2]) / 2])
    for p in prims:
        p['P'] = p['P'] + offset
    tyres = [p for p in prims if p['mat'] == 'Material.003']
    centres = {}
    for p in tyres:
        centres[corner_of(p['P'].mean(0))] = (p['P'].min(0) + p['P'].max(0)) / 2
    radius = float(np.mean([np.ptp(p['P'][:, 1]) / 2 for p in tyres]))
    parts = {}

    def part(program, motion=FIXED, corner=0):
        return parts.setdefault((program, motion, corner), Part(program, motion, corner))

    stats = {}
    spots = {}
    tints = []   # (atlas UV triangles, colour): glow map areas to recolour (None = keeps its colour)
    for p in prims:
        I = p['I'][keep[id(p)]]; P = p['P']; N = p['N']; node = p['node']; mat = p['mat']
        tri = P[I]; nrm = N[I]; n = len(I)
        if not n:
            continue
        wheel = 'Wheel' in node; calliper = 'caliper' in node
        plate = plate_face(dict(p, I=I))
        if plate.any():   # number plates: the app draws them (blank, or the owner's plate text)
            part(PLATE).add(tri[plate], nrm[plate], np.full(int(plate.sum()), NONE), np.ones((int(plate.sum()), 3, 4)), plate_uv(tri[plate]))
            stats['plate'] = stats.get('plate', 0) + int(plate.sum())
            I = I[~plate]; tri = P[I]; nrm = N[I]; n = len(I)
            if not n:
                continue
        if p['blend']:   # glass: windows and the lamp lenses
            lens = 'Lights' in node
            col = np.tile(list(LENS if lens else WINDOW) + [OPACITY_LENS if lens else OPACITY_WINDOW], (n, 3, 1))
            part(GLASS).add(tri, nrm, np.full(n, NONE), col, np.zeros((n, 3, 2)))
            key = 'lens' if lens else 'window'
        elif mat in ('Material.002', 'Material.006') and not wheel and not calliper:
            # textured in the lamp atlas: Material.002 on the left half, the glow decals on the right
            q = dict(p, I=I)
            if mat == 'Material.002':   # the tail lamp lens cut finer: its clear patch and its red light apart
                q = split(q, tail_lens(q))
                I = q['I']; P = q['P']; N = q['N']; tri = P[I]; nrm = N[I]; n = len(I)
            groups = decal_groups(q) if mat == 'Material.006' else unit_groups(q)
            uv = q['UV'][I] * [0.5, 1.0] + ([0.5, 0.0] if mat == 'Material.006' else [0.0, 0.0])
            col = np.ones((n, 3, 4))
            part(LAMP).add(tri, nrm, groups, col, uv)
            turn = (groups == TURN_L) | (groups == TURN_R)
            high = (groups == STOP) & (np.abs(tri.mean(1)[:, 0]) < 0.2) & (mat == 'Material.006')
            tints += [(uv[turn], AMBER), (uv[high], RED)]
            tints.append((uv[(groups != NONE) & ~turn & ~high], None))   # lamps that keep their colour (overlap check)
            weight = np.ones(n)
            if mat == 'Material.002':   # the rear bars' halos sit at their bulbs
                ub = q['UV'][I].mean(1)[:, 0]
                for grp in (REVERSE, TURN_L, TURN_R):
                    sel = (groups == grp) & (tri.mean(1)[:, 2] < 0)
                    weight[sel] = bar_falloff(ub[sel], REVERSE if grp == REVERSE else TURN_L) ** 6
            collect_spots(q, groups, spots, weight)
            key = 'lamp ' + mat
        else:
            q = dict(p, I=I); uv = corner_uv(q)
            col = np.tile(p['factor'], (n, 3, 1))
            if p['base'] is not None:
                col = col * sample(p['base'], uv)
            col[..., 3] = CALLIPER_GLOSS if calliper else GLOSS.get(mat, 0.15)
            group = np.full(n, NONE)
            if p['coat'] is not None:   # Material.001: the clearcoated panels are the paint
                paint = sample(p['coat'], p['UV'][I].mean(1))[:, 0] > 0.5
                group[paint] = PAINT
                stats['paint'] = stats.get('paint', 0) + int(paint.sum())
                col[paint, :, 3] = GLOSS['paint']
            if wheel or calliper:
                target = part(BODY, CALLIPER if calliper else WHEEL, corner_of(tri.reshape(-1, 3).mean(0)))
            else:
                target = part(BODY)
            target.add(tri, nrm, group, col, np.zeros((n, 3, 2)))
            key = 'wheel' if wheel else 'calliper' if calliper else mat
        stats[key] = stats.get(key, 0) + n
    return parts, centres, radius, offset, stats, finish_spots(spots), tints


def collect_spots(p, groups, spots, weight=None):
    """Accumulate the glowing area of each lamp group per side (area-weighted, times weight)."""
    I = p['I']; tri = p['P'][I]
    area = np.linalg.norm(np.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0]), axis=1) / 2
    if weight is not None:
        area = area * weight
    c = tri.mean(1); n = p['N'][I].mean(1)
    zone = np.where(c[:, 2] > 1.0, 1, np.where(c[:, 2] < -1.0, -1, 0))   # front lamp, mirror, rear lamp
    side = np.where(np.abs(c[:, 0]) < 0.2, 0, np.sign(c[:, 0]))          # 0: the high-mount stop lamp in the middle
    for g in set(groups.tolist()) - {NONE}:
        for key in set(zip(side[groups == g].tolist(), zone[groups == g].tolist())):
            m = (groups == g) & (side == key[0]) & (zone == key[1])
            s = spots.setdefault((g,) + key, [np.zeros(3), np.zeros(3), 0.0, []])
            w = area[m]
            s[0] += (c[m] * w[:, None]).sum(0); s[1] += (n[m] * w[:, None]).sum(0); s[2] += w.sum(); s[3].append(c[m])


def finish_spots(spots):
    out = []
    for (g, _, _), (pc, pn, w, pts) in sorted(spots.items()):
        if w <= 0:
            continue
        centre = pc / w; nrm = pn / (np.linalg.norm(pn) + 1e-12)
        pts = np.vstack(pts); size = float(np.percentile(np.linalg.norm(pts - centre, axis=1), 80))
        out.append((g, centre, nrm, max(size, 0.03)))
    return out


def pack(parts, centres, spots, out):
    blobs = []
    order = sorted(parts.values(), key=lambda q: (q.program, q.motion, q.corner))
    count = 0; total_tris = 0
    for q in order:
        P, N, G, C, UV = q.soup()
        # Index the soup: identical corners share a vertex.
        key = np.c_[np.round(P * 1e5), np.round(N * 32767), G, np.round(C * 255), np.round(UV * 65535)].astype(np.int64)
        _, first, inv = np.unique(key, axis=0, return_index=True, return_inverse=True)
        inv = inv.reshape(-1); tris = inv.reshape(-1, 3)
        pivot = centres[q.corner] if q.motion != FIXED else np.zeros(3)
        # Split into chunks that fit 16-bit indices.
        start = 0
        while start < len(tris):
            used = {}; end = start; idx = []
            while end < len(tris):
                t = tris[end]; new = sum(1 for v in t if v not in used)
                if len(used) + new > 65535:
                    break
                for v in t:
                    if v not in used:
                        used[v] = len(used)
                    idx.append(used[v])
                end += 1
            verts = np.array(sorted(used, key=used.get))
            src = first[verts]
            rec = np.zeros(len(verts), dtype=[('p', '<f4', 3), ('n', '<i2', 3), ('g', '<u2'), ('c', 'u1', 4), ('u', '<u2', 2)])
            rec['p'] = (P[src] - pivot).astype('<f4')
            rec['n'] = np.clip(np.round(N[src] * 32767), -32767, 32767).astype('<i2')
            rec['g'] = G[src].astype('<u2')
            rec['c'] = np.clip(np.round(C[src] * 255), 0, 255).astype('u1')
            rec['u'] = np.clip(np.round(UV[src] * 65535), 0, 65535).astype('<u2')
            ib = np.array(idx, dtype='<u2').tobytes()
            ib += b'\0' * ((-len(ib)) % 4)
            blobs.append(struct.pack('<BBBB3fII', q.program, q.motion, q.corner, 0, *pivot.astype(float), len(verts), len(idx)) + rec.tobytes() + ib)
            count += 1; total_tris += len(idx) // 3
            start = end
    trailer = struct.pack('<I', len(spots)) + b''.join(
        struct.pack('<BBBB7f', g, 0, 0, 0, *c.astype(float), *n.astype(float), size) for g, c, n, size in spots)
    data = b'VN3D' + struct.pack('<II', VERSION, count) + b''.join(blobs) + trailer
    out.write_bytes(data)
    return count, total_tris, len(data)


def tint_mask(uvs, size, grow=True):
    """Pixels of the atlas (size = w, h) under the given UV triangles, grown by 2 pixels (texture filtering)."""
    from PIL import ImageDraw, ImageFilter
    m = Image.new('L', size, 0); d = ImageDraw.Draw(m)
    for t in uvs:
        d.polygon([(u * size[0], v * size[1]) for u, v in t], fill=255)
    return np.asarray(m.filter(ImageFilter.MaxFilter(5)) if grow else m) > 0


def write_lamp_textures(prims, tints, out):
    """lamp_base.png / lamp_glow.png: Material.002 (front plate blanked) | Material.006; the glow map is the same picture
    (the lamp shader adds it, times the group's brightness, so only lamp triangles light up), recoloured under the
    turn signals and the high-mount stop lamp."""
    unit = next(p['base'] for p in prims if p['mat'] == 'Material.002')[..., :3].copy()
    glows = next(p['base'] for p in prims if p['mat'] == 'Material.006')[..., :3]
    h, w = unit.shape[:2]
    x0, y0, x1, y1 = (int(round(f * n)) for f, n in zip(FRONT_PLATE, (w, h, w, h)))
    unit[y0:y1, x0:x1] = GRILLE
    half = lambda a: Image.fromarray((atlas_half(a) * 255).round().astype(np.uint8))
    base = Image.new('RGB', (ATLAS * 2, ATLAS)); glow = Image.new('RGB', (ATLAS * 2, ATLAS))
    base.paste(half(unit), (0, 0)); base.paste(half(glows * 0.55), (ATLAS, 0))   # an unlit light guide: its colour, dimmed
    glow.paste(half(unit), (0, 0)); glow.paste(half(glows), (ATLAS, 0))
    g = np.asarray(glow).astype(np.float64) / 255; b = np.asarray(base).astype(np.float64) / 255
    keep = np.vstack([t for t, colour in tints if colour is None])
    kc = keep.mean(1); ky = np.clip((kc[:, 1] * ATLAS).astype(int), 0, ATLAS - 1); kx = np.clip((kc[:, 0] * ATLAS * 2).astype(int), 0, ATLAS * 2 - 1)
    for colour in (AMBER, RED):
        uvs = np.vstack([t for t, c in tints if c == colour])
        m = tint_mask(uvs, glow.size); exact = tint_mask(uvs, glow.size, grow=False)
        if colour == AMBER:   # only the white / clear texels turn amber: a red lens part keeps its red glow
            m &= white_px(g); exact &= white_px(g)
        hit = exact[ky, kx]
        if hit.any():
            raise SystemExit(f'{hit.sum()} other lamp triangles sit in a recoloured area {colour} of the glow map')
        if colour == AMBER:   # the lens's pattern, falling off from the bulb (rear lens: Material.002 half)
            u = np.broadcast_to(np.arange(g.shape[1])[None, :] / ATLAS, g.shape[:2])[m]
            fall = np.where(u < 1, bar_falloff(u, TURN_L), 1.0)
            g[m] = (lens_pattern(g, m) * fall * LIT_GAIN)[:, None] * AMBER; amber = m
        else:   # the high-mount stop lamp: red from its brightest channel, dark red when off (not the picture's orange)
            g[m] = g[m].max(1, keepdims=True) * RED
            b[m] = b[m].max(1, keepdims=True) * RED
    # the tail lamp picture's other clear parts do not glow with the tail / stop lamp: only its red does
    lens = np.zeros(g.shape[:2], bool); lens[int(0.47 * ATLAS):int(0.66 * ATLAS), :int(0.5 * ATLAS)] = True
    rev = np.zeros_like(lens); rev[int(CLEAR_BAND[0] * ATLAS):int(CLEAR_BAND[1] * ATLAS) + 1, :int(LENS_SEAM_U * ATLAS)] = True
    rev &= white_px(g)   # the reversing bar's clear texels: cool white, patterned, brightest at the inner end
    u = np.broadcast_to(np.arange(g.shape[1])[None, :] / ATLAS, g.shape[:2])[rev]
    g[rev] = (lens_pattern(g, rev) * bar_falloff(u, REVERSE) * LIT_GAIN)[:, None] * COOL
    sel = lens & ~amber & ~rev; r = g[sel]
    g[sel] = r * np.clip((r[:, :1] - r[:, 1:].max(1, keepdims=True)) / (r[:, :1] + 1e-6) * 1.5, 0, 1)   # by its redness
    glow = Image.fromarray((g * 255).astype(np.uint8)); base = Image.fromarray((b * 255).astype(np.uint8))
    base.save(out / 'lamp_base.png', optimize=True)
    glow.save(out / 'lamp_glow.png', optimize=True)
    return [int(v) for v in (x0, y0, x1, y1)]


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('model', type=Path, help='folder with scene.gltf, scene.bin and textures/')
    ap.add_argument('--out', type=Path, default=Path(__file__).resolve().parents[1] / 'apps/dashboard/assets/car3d')
    args = ap.parse_args()
    prims = load(args.model)
    parts, centres, radius, offset, stats, spots, tints = build(prims)
    args.out.mkdir(parents=True, exist_ok=True)
    count, tris, size = pack(parts, centres, spots, args.out / OUT_NAME)
    plate = write_lamp_textures(prims, tints, args.out)
    allP = np.vstack([np.concatenate(q.pos).reshape(-1, 3) for q in parts.values()])
    print(json.dumps({'chunks': count, 'triangles': tris, 'bytes': size, 'tyre_radius_m': round(radius, 4),
                      'size_m': [round(float(v), 3) for v in np.ptp(allP, 0)],
                      'wheel_centres_m': {k: [round(float(x), 4) for x in v] for k, v in sorted(centres.items())},
                      'front_plate_px': plate,
                      'lamp_spots': [[g, [round(float(x), 3) for x in c], round(sz, 3)] for g, c, n, sz in spots],
                      'triangles_by_part': stats}, indent=1))


if __name__ == '__main__':
    main()
