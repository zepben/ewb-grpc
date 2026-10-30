import re

def parse_geometry_path(geometry_str):
    """Parses manual breakpoints out of EA's connection geometry string and normalises Y."""
    if not geometry_str: 
        return []
    match = re.search(r'PATH=([^;]+)', geometry_str)
    if not match: 
        return []
    points = []
    for pt in match.group(1).split(':'):
        if ',' in pt:
            try:
                x_str, y_str = pt.split(',')
                points.append((float(x_str), float(y_str) * -1))
            except ValueError: 
                continue
    return points

def generate_orthogonal_route(path):
    """Inserts missing 90-degree stair-step corners between non-aligned path coordinates."""
    if len(path) < 2:
        return path
    ortho_path = [path[0]]
    for i in range(len(path) - 1):
        p1 = ortho_path[-1]
        p2 = path[i+1]
        x1, y1 = p1
        x2, y2 = p2
        if abs(x1 - x2) < 1.0 or abs(y1 - y2) < 1.0:
            ortho_path.append(p2)
        else:
            corner = (x2, y1)
            ortho_path.extend([corner, p2])
    return ortho_path

def is_point_inside(point, box):
    """Returns True if a coordinate point rests within the boundaries of a box."""
    x, y = point
    return (box['left'] <= x <= box['right']) and (box['bottom'] <= y <= box['top'])

def clip_line_to_box(current_pt, next_pt, box):
    """Clips a line segment to a box boundary, allowing a wider tolerance

    for minor coordinate alignment drift in the EA layout.
    """
    x1, y1 = current_pt
    x2, y2 = next_pt
    
    # 1. Check vertical walls first (for horizontal or slightly diagonal movements)
    if min(x1, x2) <= box['left'] <= max(x1, x2):
        # Line crosses left wall plane; calculate intersecting Y via linear interpolation
        if abs(x2 - x1) > 0.01:
            intersect_y = y1 + (y2 - y1) * (box['left'] - x1) / (x2 - x1)
            if box['bottom'] <= intersect_y <= box['top']:
                return (box['left'], intersect_y)
        else:
            return (box['left'], y1)

    if min(x1, x2) <= box['right'] <= max(x1, x2):
        if abs(x2 - x1) > 0.01:
            intersect_y = y1 + (y2 - y1) * (box['right'] - x1) / (x2 - x1)
            if box['bottom'] <= intersect_y <= box['top']:
                return (box['right'], intersect_y)
        else:
            return (box['right'], y1)
            
    # 2. Check horizontal walls next (for vertical or slightly diagonal movements)
    if min(y1, y2) <= box['bottom'] <= max(y1, y2):
        if abs(y2 - y1) > 0.01:
            intersect_x = x1 + (x2 - x1) * (box['bottom'] - y1) / (y2 - y1)
            if box['left'] <= intersect_x <= box['right']:
                return (intersect_x, box['bottom'])
        else:
            return (x1, box['bottom'])

    if min(y1, y2) <= box['top'] <= max(y1, y2):
        if abs(y2 - y1) > 0.01:
            intersect_x = x1 + (x2 - x1) * (box['top'] - y1) / (y2 - y1)
            if box['left'] <= intersect_x <= box['right']:
                return (intersect_x, box['top'])
        else:
            return (x1, box['top'])
            
    return current_pt  # Safe fallback if no boundary intersection is verified
