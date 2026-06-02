import matplotlib.patches as patches
from ea_utils import extract_ea_color, parse_ea_style, clean_html_markup

# Link modular sub-files cleanly
from ea_boxes_queries import fetch_diagram_metadata, fetch_raw_elements
from ea_boxes_layout import normalize_and_extract_bounds, extract_implicit_children, calculate_grid_layout

def _render_box_element(box, ax, elements_registry):
    """Renders visual vectors, package header shapes, and fonts on the axis canvas."""
    w, h = abs(box['right'] - box['left']), abs(box['ymax'] - box['ymin'])
    elements_registry[box['id']] = {
        'cx': box['left'] + (w / 2), 'cy': box['ymin'] + (h / 2),
        'left': box['left'], 'right': box['right'], 'bottom': box['ymin'], 'top': box['ymax']      
    }
    edge, face, ls, lw, txt = '#1f77b4', '#F0F7FF', '-', 1.2, '#333333'
    box_zorder = -box['seq']
    
    if box['type'] in ['Boundary', 'Note', 'Text']:
        edge, face, ls, txt = '#aaaaaa', ('#fafafa' if box['type'] == 'Boundary' else '#fffde6'), '--', '#555555'
        if box['type'] == 'Text': edge, face, ls, lw = 'none', 'none', 'none', 0.0
    elif box['type'] == 'Package': edge, face = '#b38600', '#fff9e6'

    face = extract_ea_color(box['style'], "BCol", face)
    edge = extract_ea_color(box['style'], "LCol", edge)
    lw_raw = parse_ea_style(box['style'], "LWth")
    if lw_raw: lw = float(lw_raw)

    if edge != 'none' and face != 'none':
        ax.add_patch(patches.Rectangle((box['left'], box['ymin']), w, h, lw=lw, ec=edge, fc=face, linestyle=ls, zorder=box_zorder))
    if box['type'] == 'Package' and edge != 'none' and face != 'none':
        tab_w, tab_h = min(w * 0.3, 60), min(h * 0.15, 20)
        ax.add_patch(patches.Rectangle((box['left'], box['ymin'] - tab_h), tab_w, tab_h, lw=lw, ec=edge, fc=face, zorder=box_zorder))
        
    txt_color = extract_ea_color(box['style'], "BFol", txt)
    font_size = 8
    fsize_raw = parse_ea_style(box['style'], "FSize")
    if fsize_raw:
        try: font_size = float(fsize_raw) / 10.0
        except ValueError: pass
            
    font_weight = 'bold' if box['type'] not in ['Boundary', 'Note', 'Text'] else 'normal'
    font_style = 'italic' if parse_ea_style(box['style'], "I") == "1" else 'normal'
    if parse_ea_style(box['style'], "B") == "1": font_weight = 'bold'

    if box['type'] in ['Text', 'Note']:
        raw_note = box['note'].strip() if (box['note'] and box['note'].strip()) else (box['name'] if box['name'] else "")
        text_label = clean_html_markup(raw_note)
    else:
        text_label = box['name'] if box['name'] else f"[{box['type']}]"

    if box['type'] in ['Class', 'Interface', 'Enumeration']:
        xt_name, yt_name = box['left'] + (w / 2), box['ymin'] + 6
        ax.text(xt_name, yt_name, text_label, color=txt_color, fontsize=font_size, fontweight='bold', fontstyle=font_style, ha='center', va='top', zorder=box_zorder + 1)
        
        separator_y = yt_name + 14
        ax.plot([box['left'], box['right']], [separator_y, separator_y], color=edge, linewidth=0.8, linestyle='-', zorder=box_zorder + 1)
        
        current_y = separator_y + 6
        for attr_str in box.get('attributes', []):
            ax.text(box['left'] + 8, current_y, attr_str, color=txt_color, fontsize=font_size - 0.5, fontweight='normal', ha='left', va='top', zorder=box_zorder + 1)
            current_y += 12
    else:
        v_align = 'top' if box['type'] in ['Package', 'Text', 'Note'] else 'center'
        h_align = 'left' if box['type'] in ['Package', 'Text', 'Note'] else 'center'
        xt = box['left'] + 4 if box['type'] in ['Package', 'Text', 'Note'] else box['left'] + (w / 2)
        yt = box['ymin'] + 4 if box['type'] in ['Package', 'Text', 'Note'] else box['ymin'] + (h / 2)
        if box['type'] not in ['Package', 'Text', 'Note']: text_label = f"{text_label}\n({box['type']})"
        if text_label:
            ax.text(xt, yt, text_label, color=txt_color, fontsize=font_size, fontweight=font_weight, fontstyle=font_style, ha=h_align, va=v_align, wrap=False, zorder=box_zorder + 1)

def draw_diagram_boxes(cursor, diagram_id, ax, print_debug=False):
    """Main interface coordinating metadata queries, layout cascade routing, and canvas renders."""
    diag_row = fetch_diagram_metadata(cursor, diagram_id)
    if not diag_row: return None, (0, 0, 0, 0)
    
    # FIX: Explicitly unpack the query tuple slots so strings stay strings and integers stay integers
    diagram_package_id   = diag_row[0]
    diagram_package_name = diag_row[1]
    diagram_style_ex     = diag_row[2] if diag_row[2] else ""
    
    diagram_show_attribs = 0 if parse_ea_style(diagram_style_ex, "ShowAttr") == "0" else 1

    raw_elements = fetch_raw_elements(cursor, diagram_id, diagram_package_id, diagram_package_name)
    if not raw_elements: return None, (0, 0, 0, 0)
    
    package_objects = [row for row in raw_elements if row[2] == "Package" and row[1] == diagram_package_name]
    package_object_id = package_objects[0][0] if package_objects else None

    # Execute Pass 1-3 layout engine via decoupling file imports
    all_elements_data, parent_bounds = normalize_and_extract_bounds(raw_elements, package_object_id, cursor, diagram_show_attribs, print_debug=print_debug)
    implicit_children = extract_implicit_children(all_elements_data)
    calculate_grid_layout(implicit_children, parent_bounds)

    elements_registry = {}
    x_min, x_max = float('inf'), float('-inf')
    y_min, y_max = float('inf'), float('-inf')

    # Draw the final vector output configurations onto the live production axis frame map
    for box in all_elements_data:
        _render_box_element(box, ax, elements_registry)
        tab_h = min(abs(box['ymax'] - box['ymin']) * 0.15, 20) if box['type'] == 'Package' else 0
        x_min, x_max = min(x_min, box['left']), max(x_max, box['right'])
        y_min, y_max = min(y_min, box['ymin'] - tab_h), max(y_max, box['ymax'])

    return elements_registry, (x_min, x_max, y_min, y_max)
