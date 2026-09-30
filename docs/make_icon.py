"""Иконка MultiTogether: два силуэта Стива, между ними провисшая цепь. Чёрное на белом."""
import math
import sys

from PIL import Image, ImageDraw

SS = 4                      # суперсэмплинг
SIZE = 1024 * SS
BLACK, WHITE = 0, 255

img = Image.new('L', (SIZE, SIZE), WHITE)
d = ImageDraw.Draw(img)

U = 18 * SS                 # один «пиксель» скина
GAP = max(1, int(U * 0.28)) # белый зазор между руками, телом и ногами
TOP = (SIZE - 32 * U) // 2 + 20 * SS
LEFT_X = 40 * SS
RIGHT_X = SIZE - 40 * SS - 16 * U


def rect(x0, y0, x1, y1, color=BLACK):
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=color)


def steve(x):
    """Стив спереди: голова 8x8, тело 8x12, руки 4x12, ноги 4x12. x — левый край рук."""
    y = TOP
    rect(x + 4 * U, y, x + 12 * U, y + 8 * U)                        # голова
    rect(x + 4 * U, y + 8 * U, x + 12 * U, y + 20 * U)               # тело
    rect(x, y + 8 * U, x + 4 * U, y + 20 * U)                        # левая рука
    rect(x + 12 * U, y + 8 * U, x + 16 * U, y + 20 * U)              # правая рука
    rect(x + 4 * U, y + 20 * U, x + 12 * U, y + 32 * U)              # ноги
    # зазоры: руки отделены от тела, ноги друг от друга, голова от тела
    rect(x + 4 * U - GAP // 2, y + 8 * U, x + 4 * U + GAP - GAP // 2, y + 20 * U, WHITE)
    rect(x + 12 * U - GAP // 2, y + 8 * U, x + 12 * U + GAP - GAP // 2, y + 20 * U, WHITE)
    rect(x + 8 * U - GAP // 2, y + 20 * U, x + 8 * U + GAP - GAP // 2, y + 32 * U, WHITE)
    rect(x + 4 * U, y + 8 * U - GAP // 2, x + 12 * U, y + 8 * U + GAP - GAP // 2, WHITE)


def catenary(p0, p1, sag, samples=2000):
    """Точки провисшей цепи от p0 до p1 (парабола) и длины дуги для каждой."""
    pts = []
    for i in range(samples + 1):
        t = i / samples
        x = p0[0] + (p1[0] - p0[0]) * t
        y = p0[1] + (p1[1] - p0[1]) * t + sag * 4 * t * (1 - t)
        pts.append((x, y))
    arc = [0.0]
    for a, b in zip(pts, pts[1:]):
        arc.append(arc[-1] + math.dist(a, b))
    return pts, arc


def at(pts, arc, s):
    """Точка и направление на расстоянии s по дуге."""
    for i in range(1, len(arc)):
        if arc[i] >= s:
            a, b = pts[i - 1], pts[i]
            k = (s - arc[i - 1]) / max(arc[i] - arc[i - 1], 1e-9)
            p = (a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k)
            return p, math.atan2(b[1] - a[1], b[0] - a[0])
    return pts[-1], math.atan2(pts[-1][1] - pts[-2][1], pts[-1][0] - pts[-2][0])


def stadium(cx, cy, angle, length, width, n=48):
    """Контур «стадиона» (звено цепи) длиной length вдоль angle."""
    r = width / 2
    half = length / 2 - r
    out = []
    for i in range(n + 1):
        a = -math.pi / 2 + math.pi * i / n
        out.append((half + r * math.cos(a), r * math.sin(a)))
    for i in range(n + 1):
        a = math.pi / 2 + math.pi * i / n
        out.append((-half + r * math.cos(a), r * math.sin(a)))
    ca, sa = math.cos(angle), math.sin(angle)
    return [(cx + x * ca - y * sa, cy + x * sa + y * ca) for x, y in out]


# концы цепи — нижние углы внутренних рук
hand_y = TOP + 19 * U
start = (LEFT_X + 15 * U, hand_y)
end = (RIGHT_X + 1 * U, hand_y)
pts, arc = catenary(start, end, sag=165 * SS)
total = arc[-1]

LINK_LEN = 84 * SS
LINK_W = 46 * SS
STROKE = 15 * SS
STEP = LINK_LEN * 0.66
count = int(total // STEP) + 1
offset = (total - (count - 1) * STEP) / 2

links = [at(pts, arc, offset + i * STEP) for i in range(count)]

# сначала звенья плашмя (с дыркой), потом ребром — они проходят сквозь дырки
for i, (p, ang) in enumerate(links):
    if i % 2 == 0:
        d.polygon(stadium(p[0], p[1], ang, LINK_LEN, LINK_W), fill=BLACK)
        d.polygon(stadium(p[0], p[1], ang, LINK_LEN - 2 * STROKE, LINK_W - 2 * STROKE), fill=WHITE)
for i, (p, ang) in enumerate(links):
    if i % 2 == 1:
        d.polygon(stadium(p[0], p[1], ang, LINK_LEN, STROKE * 1.35), fill=BLACK)

steve(LEFT_X)
steve(RIGHT_X)

out = sys.argv[1] if len(sys.argv) > 1 else 'icon'
for size in (1024, 512, 256, 64):
    img.resize((size, size), Image.LANCZOS).save(f'{out}-{size}.png', optimize=True)
print('links', count, 'arc', round(total / SS))
