#!/usr/bin/env python3
"""Convert a design illustration SVG into an Android vector drawable.

The input subset is the one the VIPTV design illustrations use: rect, circle,
ellipse and path geometry with a flat colour or an objectBoundingBox linear or
radial gradient, plus element opacity. Gradients become aapt:attr paints, and a
radial gradient on a non-square bounding box keeps its elliptical falloff by
sitting inside a scaled group. Arcs are exact; cubic bounds are sampled, which
only shifts a gradient's endpoints.

  python3 tools/convert_illustration_svg.py wide.svg \\
      composeApp/src/commonMain/composeResources/drawable/live_empty_stadium_wide.xml
"""
import argparse
import re
import xml.etree.ElementTree as ET

SVG = "{http://www.w3.org/2000/svg}"


def local(tag):
    return tag.rsplit("}", 1)[-1]


def color(value, opacity=1.0, base_alpha=1.0):
    value = value.strip()
    if value.startswith("#"):
        digits = value[1:]
        if len(digits) == 3:
            digits = "".join(c * 2 for c in digits)
        if len(digits) == 6:
            return "#{:02X}{}".format(round(255 * opacity * base_alpha), digits.upper())
        if len(digits) == 8:
            given = int(digits[0:2], 16) / 255
            return "#{:02X}{}".format(round(255 * opacity * given * base_alpha), digits[2:].upper())
    raise SystemExit(f"unsupported colour {value!r}")


def num(value, default=0.0):
    if value is None:
        return default
    return float(value[:-1]) / 100 if value.endswith("%") else float(value)


def path_points(d):
    tokens = re.findall(r"[MmLlHhVvCcSsQqTtAaZz]|-?\d*\.?\d+(?:e-?\d+)?", d)
    points = []
    x = y = 0.0
    start = (0.0, 0.0)
    command = None
    i = 0

    def take(n):
        nonlocal i
        values = [float(v) for v in tokens[i:i + n]]
        i += n
        return values

    while i < len(tokens):
        if re.match(r"[A-Za-z]", tokens[i]):
            command = tokens[i]
            i += 1
            if command in "Zz":
                x, y = start
                continue
        relative = command.islower()
        kind = command.upper()
        if kind == "M":
            nx, ny = take(2)
            x, y = (x + nx, y + ny) if relative else (nx, ny)
            start = (x, y)
            command = "l" if relative else "L"
            points.append((x, y))
        elif kind == "L":
            nx, ny = take(2)
            x, y = (x + nx, y + ny) if relative else (nx, ny)
            points.append((x, y))
        elif kind == "H":
            (nx,) = take(1)
            x = x + nx if relative else nx
            points.append((x, y))
        elif kind == "V":
            (ny,) = take(1)
            y = y + ny if relative else ny
            points.append((x, y))
        elif kind in "CQS":
            n = 6 if kind == "C" else 4
            values = take(n)
            for j in range(0, n, 2):
                px, py = values[j], values[j + 1]
                points.append((x + px, y + py) if relative else (px, py))
            x, y = points[-1]
        elif kind == "T":
            nx, ny = take(2)
            x, y = (x + nx, y + ny) if relative else (nx, ny)
            points.append((x, y))
        elif kind == "A":
            values = take(7)
            nx, ny = values[5], values[6]
            x, y = (x + nx, y + ny) if relative else (nx, ny)
            points.append((x, y))
        else:
            raise SystemExit(f"unsupported path command {command}")
    return points


def rect_path(x, y, w, h, rx, ry):
    if rx <= 0 or ry <= 0:
        return f"M{x:g},{y:g} h{w:g} v{h:g} h{-w:g} Z"
    return (f"M{x + rx:g},{y:g} h{w - 2 * rx:g} a{rx:g},{ry:g} 0 0 1 {rx:g},{ry:g} "
            f"v{h - 2 * ry:g} a{rx:g},{ry:g} 0 0 1 {-rx:g},{ry:g} h{2 * rx - w:g} "
            f"a{rx:g},{ry:g} 0 0 1 {-rx:g},{-ry:g} v{2 * ry - h:g} a{rx:g},{ry:g} 0 0 1 {rx:g},{-ry:g} Z")


def ellipse_path(cx, cy, rx, ry):
    return f"M{cx - rx:g},{cy:g} a{rx:g},{ry:g} 0 1 0 {2 * rx:g},0 a{rx:g},{ry:g} 0 1 0 {-2 * rx:g},0 Z"


def circle_path(cx, cy, r):
    return ellipse_path(cx, cy, r, r)


def bounds(element):
    tag = local(element.tag)
    if tag == "rect":
        x, y = num(element.get("x")), num(element.get("y"))
        return x, y, num(element.get("width")), num(element.get("height"))
    if tag in ("circle", "ellipse"):
        cx, cy = num(element.get("cx")), num(element.get("cy"))
        rx = num(element.get("r")) if tag == "circle" else num(element.get("rx"))
        ry = rx if tag == "circle" else num(element.get("ry"))
        return cx - rx, cy - ry, 2 * rx, 2 * ry
    points = path_points(element.get("d"))
    xs, ys = [p[0] for p in points], [p[1] for p in points]
    return min(xs), min(ys), max(xs) - min(xs), max(ys) - min(ys)


def geometry(element):
    tag = local(element.tag)
    if tag == "rect":
        x, y, w, h = bounds(element)
        rx = num(element.get("rx"), 0.0) or num(element.get("ry"), 0.0)
        ry = num(element.get("ry"), rx) if element.get("rx") else num(element.get("ry"), rx)
        return rect_path(x, y, w, h, rx, ry)
    if tag == "circle":
        return circle_path(num(element.get("cx")), num(element.get("cy")), num(element.get("r")))
    if tag == "ellipse":
        return ellipse_path(num(element.get("cx")), num(element.get("cy")), num(element.get("rx")), num(element.get("ry")))
    return element.get("d")


def gradient(element):
    tag = local(element.tag)
    stops = [child for child in element if local(child.tag) == "stop"]
    scale = 1.0
    if element.get("gradientUnits") == "userSpaceOnUse":
        scale = None
    items = []
    for stop in stops:
        alpha = num(stop.get("stop-opacity"), 1.0)
        items.append((num(stop.get("offset")), color(stop.get("stop-color"), alpha)))
    return {
        "type": "linear" if tag == "linearGradient" else "radial",
        "items": items,
        "x1": num(element.get("x1"), 0.0), "y1": num(element.get("y1"), 0.0),
        "x2": num(element.get("x2"), 1.0), "y2": num(element.get("y2"), 0.0),
        "cx": num(element.get("cx"), 0.5), "cy": num(element.get("cy"), 0.5),
        "r": num(element.get("r"), 0.5),
        "units": element.get("gradientUnits", "objectBoundingBox"),
    }


def paint(indent, name, spec, box):
    bx, by, bw, bh = box
    lines = [f'{indent}<aapt:attr name="android:{name}">']
    pad = indent + "  "
    if spec["type"] == "linear":
        sx, sy = bx + spec["x1"] * bw, by + spec["y1"] * bh
        ex, ey = bx + spec["x2"] * bw, by + spec["y2"] * bh
        lines.append(f'{pad}<gradient android:type="linear" android:startX="{sx:g}" android:startY="{sy:g}" android:endX="{ex:g}" android:endY="{ey:g}">')
    else:
        cx, cy = bx + spec["cx"] * bw, by + spec["cy"] * bh
        radius = spec["r"] * ((bw + bh) / 2 if spec["units"] == "objectBoundingBox" else 1)
        lines.append(f'{pad}<gradient android:type="radial" android:centerX="{cx:g}" android:centerY="{cy:g}" android:gradientRadius="{radius:g}">')
    for offset, value in spec["items"]:
        lines.append(f'{pad}  <item android:offset="{offset:g}" android:color="{value}"/>')
    lines.append(f"{pad}</gradient>")
    lines.append(f"{indent}</aapt:attr>")
    return lines


def convert(source, width, height):
    root = ET.fromstring(source)
    gradients = {}
    for node in root.iter():
        if local(node.tag) in ("linearGradient", "radialGradient"):
            gradients[node.get("id")] = gradient(node)
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
             '    xmlns:aapt="http://schemas.android.com/aapt"',
             f'    android:width="{width}dp" android:height="{height}dp"',
             f'    android:viewportWidth="{width}" android:viewportHeight="{height}">']
    for element in root:
        tag = local(element.tag)
        if tag == "defs":
            continue
        if tag not in ("rect", "circle", "ellipse", "path"):
            raise SystemExit(f"unsupported element {tag}")
        box = bounds(element)
        opacity = num(element.get("opacity"), 1.0)
        fill = element.get("fill")
        stroke = element.get("stroke")
        indent = "  "
        spec = gradients.get(fill[5:-1]) if fill and fill.startswith("url(#") else None
        if spec and spec["type"] == "radial" and abs(box[2] - box[3]) > 0.5:
            cx, cy = box[0] + box[2] / 2, box[1] + box[3] / 2
            lines.append(f'  <group android:translateX="{cx:g}" android:translateY="{cy:g}" android:scaleX="{box[2] / box[3]:g}">')
            indent = "    "
            d = circle_path(0, 0, box[3] / 2)
            spec = dict(spec, cx=0.5, cy=0.5, r=spec["r"])
            box = (-box[3] / 2, -box[3] / 2, box[3], box[3])
        else:
            d = geometry(element)
        attrs = [f'android:pathData="{d}"']
        if spec:
            paint_lines = paint(indent, "fillColor", spec, box)
            if opacity != 1.0:
                attrs.append(f'android:fillAlpha="{opacity:g}"')
        else:
            paint_lines = None
            if fill and fill != "none":
                attrs.append(f'android:fillColor="{color(fill, opacity)}"')
            elif fill == "none":
                attrs.append('android:fillColor="#00000000"')
        if stroke and stroke != "none":
            attrs.append(f'android:strokeColor="{color(stroke, num(element.get("stroke-opacity"), 1.0), opacity)}"')
            attrs.append(f'android:strokeWidth="{num(element.get("stroke-width"), 1.0):g}"')
        if paint_lines:
            lines.append(f"{indent}<path {' '.join(attrs)}>")
            lines.extend(paint_lines)
            lines.append(f"{indent}</path>")
        else:
            lines.append(f"{indent}<path {' '.join(attrs)}/>")
        if indent == "    ":
            lines.append("  </group>")
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source")
    parser.add_argument("destination")
    parser.add_argument("--width", type=int)
    parser.add_argument("--height", type=int)
    arguments = parser.parse_args()
    with open(arguments.source, encoding="utf-8") as handle:
        source = handle.read()
    match = re.search(r'viewBox="0 0 (\d+) (\d+)"', source)
    width = arguments.width or int(match.group(1))
    height = arguments.height or int(match.group(2))
    with open(arguments.destination, "w", encoding="utf-8") as handle:
        handle.write(convert(source, width, height))


if __name__ == "__main__":
    main()