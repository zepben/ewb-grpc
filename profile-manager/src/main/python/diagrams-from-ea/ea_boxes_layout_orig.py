

from ea_utils import parse_ea_style
from ea_boxes_queries import fetch_visible_attributes

def normalize_and_extract_bounds(raw_elements, package_object_id, cursor, diagram_show_attribs, silent=False):
    """Normalises coordinates, sizes boxes symmetrically, and applies an iterative 

    layout relaxation pass to preserve vertical columns and original spacing metrics.
    """
    parent_bounds = None
    elements_data = []

    # --- PHASE 1: READ BASELINE DATABASE STATES ---
    for obj_id, name, el_type, left, top, right, bottom, style, note, sequence, parent_id in raw_elements:
        norm_top = top * -1
        norm_bottom = bottom * -1
        y_min_box = min(norm_top, norm_bottom)
        y_max_box = max(norm_top, norm_bottom)
        
        visible_attribs = []
        if el_type in ['Class', 'Interface', 'Enumeration']:
            visible_attribs = fetch_visible_attributes(cursor, obj_id, style, diagram_show_attribs)

        elements_data.append({
            'seq': int(sequence) if sequence is not None else 0,
            'id': obj_id, 'name': name, 'type': el_type,
            'left': float(left), 'ymin': float(y_min_box), 'right': float(right), 'ymax': float(y_max_box),
            'style': style, 'note': note, 'parent_id': parent_id,
            'attributes': visible_attribs,
            # Immutable copies of original database geometries
            'orig_left': float(left), 'orig_right': float(right),
            'orig_ymin': float(y_min_box), 'orig_ymax': float(y_max_box),
            'growth_w': 0.0, 'growth_h': 0.0,
            'shift_x': 0.0, 'shift_y': 0.0
        })

    # Capture original baseline gap structures between adjacent elements
    original_gaps_x = {}
    original_gaps_y = {}
    
    for i in range(len(elements_data)):
        for j in range(len(elements_data)):
            if i == j: continue
            b1, b2 = elements_data[i], elements_data[j]
            # Record explicit directional gap if b2 sits to the right or below b1
            if b2['orig_left'] >= b1['orig_right']:
                original_gaps_x[(b1['id'], b2['id'])] = b2['orig_left'] - b1['orig_right']
            if b2['orig_ymin'] >= b1['orig_ymax']:
                original_gaps_y[(b1['id'], b2['id'])] = b2['orig_ymin'] - b1['orig_ymax']

    # --- PHASE 2: INDEPENDENT TEXT WIDTH & HEIGHT FOOTPRINT CALCULATION ---
    for box in elements_data:
        if box['type'] not in ['Class', 'Interface', 'Enumeration']:
            continue

        font_size = 8
        fsize_raw = parse_ea_style(box['style'], "FSize")
        if fsize_raw:
            try: font_size = float(fsize_raw) / 10.0
            except ValueError: pass

        orig_w = abs(box['orig_right'] - box['orig_left'])
        orig_h = abs(box['orig_ymax'] - box['orig_ymin'])

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

        if not silent:
            max_str_label = max(strings_to_check, key=len) if strings_to_check else ""
            print(f"   [Size Debug] Class: '{box['name']}' (ID: {box['id']}) - Longest String: '{max_str_label}'")
            print(f"      Width  -> Orig: {orig_w:.1f}px | Calc: {required_width:.1f}px | Delta: +{delta_w:.1f}px")

    # --- PHASE 3: RELAXATION PROPAGATION ENGINE ---
    # Apply initial sizing growth expansions symmetrically from their own stationary centers
    for box in elements_data:
        half_w = box['growth_w'] / 2
        box['left']  -= half_w
        box['right'] += half_w
        box['ymax']  += box['growth_h']

    # Iterative relaxation pass: cascade shifts dynamically using updated tracking positions
    # to protect downstream rows and columns without scrambling vertical alignments
    for _ in range(8):  # Pass iterations allow dependencies to settle completely
        shifted_any = False
        
        for i in range(len(elements_data)):
            for j in range(len(elements_data)):
                if i == j: continue
                b1, b2 = elements_data[i], elements_data[j]
                
                # 1. Check Horizontal Boundary Infringements
                if (b1['id'], b2['id']) in original_gaps_x:
                    orig_gap_x = original_gaps_x[(b1['id'], b2['id'])]
                    current_gap_x = b2['left'] - b1['right']
                    
                    if current_gap_x < orig_gap_x:
                        push_x = orig_gap_x - current_gap_x
                        # Clean downstream shift cascade: ONLY move the box sitting to the right
                        b2['left'] += push_x
                        b2['right'] += push_x
                        b2['shift_x'] += push_x
                        shifted_any = True
                        if not silent:
                            print(f"      [Relaxation Right] '{b1['name']}' pushed '{b2['name']}' right by +{push_x:.1f}px")

                # 2. Check Vertical Boundary Infringements
                if (b1['id'], b2['id']) in original_gaps_y:
                    orig_gap_y = original_gaps_y[(b1['id'], b2['id'])]
                    current_gap_y = b2['ymin'] - b1['ymax']
                    
                    if current_gap_y < orig_gap_y:
                        push_y = orig_gap_y - current_gap_y
                        # Clean downstream shift cascade: ONLY move the box sitting below
                        b2['ymin'] += push_y
                        b2['ymax'] += push_y
                        b2['shift_y'] += push_y
                        shifted_any = True
                        if not silent:
                            print(f"      [Relaxation Down] '{b1['name']}' pushed '{b2['name']}' down by +{push_y:.1f}px")
                            
        if not shifted_any:
            break  # Layout successfully settled into a stable, collision-free state

    # Capture parent boundary space limits out of the finished layout dataset array
    for box in elements_data:
        if package_object_id and box['id'] == package_object_id:
            parent_bounds = {
                'left': box['left'], 'right': box['right'], 
                'bottom': box['ymin'], 'top': box['ymax'],
                'w': abs(box['right'] - box['left']), 'h': abs(box['ymax'] - box['ymin'])
            }
            break

    return elements_data, parent_bounds

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
