import itertools
import json

from collections import defaultdict

from ea_utils import parse_ea_style
from ea_boxes_queries import fetch_visible_attributes

def normalize_and_extract_bounds(
    raw_elements,
    package_object_id,
    cursor,
    diagram_show_attribs,
    print_debug,
):
    elements_data = build_elements(raw_elements, cursor, diagram_show_attribs, print_debug)

    for el in elements_data:
        calculate_growth(el, print_debug)

    box_gaps_x, box_gaps_y = calculate_gaps(elements_data, print_debug)

    grow_elements(elements_data, print_debug)

    move(elements_data, 'left', 'right', box_gaps_x, gap_x, print_debug)
    move(elements_data, 'ymin', 'ymax', box_gaps_y, gap_y, print_debug)

    # Capture parent boundary after we have moved everything around. This is needed for any nested objects still to be added implicitly.
    parent_bounds = None
    for box in elements_data:
        if package_object_id and (box['id'] == package_object_id):
            parent_bounds = {
                'left': box['left'],
                'right': box['right'], 
                'bottom': box['ymin'],
                'top': box['ymax'],
                'w': box['right'] - box['left'],
                'h': box['ymax'] - box['ymin'],
            }
            break

    return elements_data, parent_bounds

def build_elements(raw_elements, cursor, diagram_show_attribs, print_debug):
    return [{
        'seq': int(sequence) if sequence is not None else 0,
        'id': obj_id,
        'name': name,
        'type': el_type,
        'left': float(left),
        'right': float(right),
        'ymin': float(min(-top, -bottom)),
        'ymax': float(max(-top, -bottom)),
        'style': style,
        'note': note,
        'parent_id': parent_id,
        'attributes': fetch_visible_attributes(cursor, obj_id, el_type, style, diagram_show_attribs),
        'growth_w': 0.0,
        'growth_h': 0.0,
    } for obj_id, name, el_type, left, top, right, bottom, style, note, sequence, parent_id in raw_elements]
    
def calculate_growth(box, print_debug):
    if box['type'] not in ['Class', 'Interface', 'Enumeration']:
        return

    font_size = 8
    fsize_raw = parse_ea_style(box['style'], "FSize")
    if fsize_raw:
        try: font_size = float(fsize_raw) / 10.0
        except ValueError: pass

    orig_w = abs(box['right'] - box['left'])
    orig_h = abs(box['ymax'] - box['ymin'])

    required_height = 30 + (len(box['attributes']) * 14)
    delta_h = max(0.0, required_height - orig_h)
    if delta_h > 0:
        box['growth_h'] = delta_h

    strings_to_check = [box['name'] if box['name'] else ""] + box['attributes']
    if strings_to_check:
        header_name = box['name'] if box['name'] else ""
        header_wide = sum(1 for c in header_name if c.isupper() or c.isdigit() or c in [' ', '_', '-', '<', '>'])
        header_narrow = len(header_name) - header_wide
        calculated_header_w = (header_wide * (font_size * 1.0)) + (header_narrow * (font_size * 0.88))
        
        calculated_attr_w = 0.0
        if box['attributes']:
            max_attr_string = max(box['attributes'], key=len)
            attr_wide = sum(1 for c in max_attr_string if c.isupper() or c.isdigit() or c in [' ', '_', ':', '+', '-', '<', '>'])
            attr_narrow = len(max_attr_string) - attr_wide
            calculated_attr_w = (attr_wide * (font_size * 0.92)) + (attr_narrow * (font_size * 0.64))
        
        if calculated_attr_w > calculated_header_w:
            max_visual_text_w = calculated_attr_w + 8.0
        else:
            max_visual_text_w = calculated_header_w
            
        max_char_len = max(len(s) for s in strings_to_check)
        buffer_padding = int(44 + (max_char_len * 0.45))
        required_width = int(max_visual_text_w + buffer_padding)
        
        delta_w = max(0.0, required_width - orig_w)
        if delta_w > 0:
            box['growth_w'] = delta_w

def calculate_gaps(elements_data, print_debug):
    def gaps_x(box):
        return {other['id']: gap for other in elements_data if (gap := gap_x(box, other)) > 0.0}

    def gaps_y(box):
        return {other['id']: gap for other in elements_data if (gap := gap_y(box, other)) > 0.0}

    return (
        {box['id']: gaps_x(box) for box in elements_data},
        {box['id']: gaps_y(box) for box in elements_data},
    )

def grow_elements(elements_data, print_debug):
    for it in elements_data:
        half_width = it['growth_w'] / 2.0
        half_height = it['growth_h'] / 2.0

        it['left'] -= half_width
        it['right'] += half_width
        it['ymin'] -= half_height
        it['ymax'] += half_height
    
def move(elements_data, min_side, max_side, box_gaps, gap_between, print_debug):
    # Collect by mid points.
    raw_elements_by_mid = defaultdict(list)
    for box in elements_data:
        raw_elements_by_mid[box[min_side] + ((box[max_side] - box[min_side]) / 2.0)].append(box)
    
    # Group by nearly the same mid points (things mostly aligned on the perpendicular axis).
    sorted_mids = sorted(raw_elements_by_mid.keys())
    elements_by_mid = defaultdict(list)
    current = sorted_mids[0]
    
    for key in sorted_mids:
        if key - current > 10.0:
            current = key
        elements_by_mid[current].extend(raw_elements_by_mid[key])

    # Convert to processing group.
    boxes_by_group = []
    for centre, boxes in elements_by_mid.items():
        boxes_by_group.append({
            'id': f"{centre} grouping",
            min_side: min((box[min_side] for box in boxes)),
            max_side: max((box[max_side] for box in boxes)),
            'min_gap': min((box_gap for box in boxes for box_gap in box_gaps[box['id']].values()), default=None),
            'boxes': boxes,
        })
        
    boxes_by_group.sort(key=lambda it: it[min_side])
    elements_data.sort(key=lambda it: it[min_side])
    
    for group in boxes_by_group:
        target_gap = group.get('min_gap', None)
        if target_gap is not None:
            move_by = 0.0
            check_gaps = set()
            for box in group['boxes']:
                check_gaps.update(box_gaps.get(box['id'], {}))

            for other in elements_data:
                if other['id'] in check_gaps:
                    cur_gap = gap_between(group, other)
                    move_by = target_gap - cur_gap

                    # only act if we violated spacing
                    if move_by > 0.0:
                        group[min_side] = group[min_side] + move_by
                        group[max_side] = group[max_side] + move_by
                        for box in group['boxes']:
                            box[min_side] = box[min_side] + move_by
                            box[max_side] = box[max_side] + move_by

def extract_implicit_children(elements_data):
    """Filters out child elements needing grid arrangement."""
    return [e for e in elements_data if e['parent_id'] is not None and e['type'] == 'Package']

def calculate_grid_layout(implicit_children, parent_bounds):
    """Calculates even, padded distributions for implicit boxes inside the parent container."""
    if not parent_bounds or not implicit_children: return
    PADDING_X, PADDING_Y = 25, 35  
    GAP_X, GAP_Y = 20, 20      
    grid_cols = 3 if len(implicit_children) >= 3 else len(implicit_children)
    grid_rows = (len(implicit_children) + grid_cols - 1) // grid_cols
    inner_w = parent_bounds['w'] - (PADDING_X * 2) - (GAP_X * (grid_cols - 1))
    inner_h = parent_bounds['h'] - (PADDING_Y * 2) - (GAP_Y * (grid_rows - 1))
    child_w, child_h = inner_w / grid_cols, inner_h / grid_rows
    for idx, child in enumerate(implicit_children):
        row, col = idx // grid_cols, idx % grid_cols
        child['left'] = parent_bounds['left'] + PADDING_X + (col * (child_w + GAP_X))
        child['ymin'] = parent_bounds['bottom'] + PADDING_Y + (row * (child_h + GAP_Y))
        child['right'] = child['left'] + child_w
        child['ymax'] = child['ymin'] + child_h

def gap_x(box1, box2):
    return box1['left'] - box2['right']

def gap_y(box1, box2):
    return box1['ymin'] - box2['ymax']
