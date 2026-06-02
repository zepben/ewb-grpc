from ea_utils import parse_ea_style
from ea_boxes_queries import fetch_visible_attributes

def normalize_and_extract_bounds(raw_elements, package_object_id, cursor, diagram_show_attribs, silent=False):
    elements_data = []
    
    # 1. Normalize and extract initial bounds
    for obj_id, name, el_type, left, top, right, bottom, style, note, sequence, parent_id in raw_elements:
        top = top * -1
        bottom = bottom * -1

        # Map raw top/bottom coordinates to standard geometric min/max
        y_min_box = min(float(top), float(bottom))
        y_max_box = max(float(top), float(bottom))
        
        visible_attribs = []
        if el_type in ['Class', 'Interface', 'Enumeration']:
            visible_attribs = fetch_visible_attributes(cursor, obj_id, style, diagram_show_attribs)

        elements_data.append({
            'seq': int(sequence) if sequence is not None else 0,
            'id': obj_id, 
            'name': name, 
            'type': el_type,
            'left': float(left), 
            'ymin': float(y_min_box), 
            'right': float(right), 
            'ymax': float(y_max_box),
            'style': style, 
            'note': note, 
            'parent_id': parent_id,
            'growth_w': 0.0, 
            'growth_h': 0.0,
            'shift_x': 0.0,
            'shift_y': 0.0,
            'attributes': visible_attribs,
        })
        
    # 2. Calculate the required growth for each element
    # (Assuming calculate_growth modifies the dicts in-place)
    calculate_growth(elements_data, silent)
    
    # 3. Process X-axis shifts (Left-to-Right cascade)
    # Sort elements by their initial left position to push items chronologically
    elements_data.sort(key=lambda e: e['left'])
    
    for i, current in enumerate(elements_data):
        # Accumulate shift from previous push operations, then add its own growth
        current['left'] += current['shift_x']
        current['right'] += current['shift_x'] + current['growth_w']
        
        # Calculate how much this element's total change affects items to its right
        total_push_x = current['shift_x'] + current['growth_w']
        
        if total_push_x > 0:
            for downstream in elements_data[i+1:]:
                # Push downstream element if it sits to the right of the current element's original edge
                if downstream['left'] >= (current['left'] - total_push_x):
                    # Only push if they share vertical space (overlap on Y axis)
                    if not (downstream['ymax'] <= current['ymin'] or downstream['ymin'] >= current['ymax']):
                        downstream['shift_x'] = max(downstream['shift_x'], total_push_x)

    # 4. Process Y-axis shifts (Top-to-Bottom cascade)
    # Sort elements by their initial top position
    elements_data.sort(key=lambda e: e['ymin'])
    
    for i, current in enumerate(elements_data):
        current['ymin'] += current['shift_y']
        current['ymax'] += current['shift_y'] + current['growth_h']
        
        total_push_y = current['shift_y'] + current['growth_h']
        
        if total_push_y > 0:
            for downstream in elements_data[i+1:]:
                # Push downstream element if it sits below the current element's original edge
                if downstream['ymin'] >= (current['ymin'] - total_push_y):
                    # Only push if they share horizontal space (overlap on X axis)
                    if not (downstream['right'] <= current['left'] or downstream['left'] >= current['right']):
                        downstream['shift_y'] = max(downstream['shift_y'], total_push_y)

    # 5. Restore original sequence order before returning
    elements_data.sort(key=lambda e: e['seq'])
    
    # Capture parent boundary space limits out of the finished layout dataset array
    parent_bounds = None
    for box in elements_data:
        if package_object_id and box['id'] == package_object_id:
            parent_bounds = {
                'left': box['left'], 'right': box['right'], 
                'bottom': box['ymin'], 'top': box['ymax'],
                'w': abs(box['right'] - box['left']), 'h': abs(box['ymax'] - box['ymin'])
            }
            break

    return elements_data, parent_bounds

def calculate_growth(elements_data, silent):
    for box in elements_data:
        if box['type'] not in ['Class', 'Interface', 'Enumeration']:
            continue

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

        if not silent:
            max_str_label = max(strings_to_check, key=len) if strings_to_check else ""
            print(f"   [Size Debug] Class: '{box['name']}' (ID: {box['id']}) - Longest String: '{max_str_label}'")
            print(f"      Width  -> Orig: {orig_w:.1f}px | Calc: {required_width:.1f}px | Delta: +{delta_w:.1f}px")

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
