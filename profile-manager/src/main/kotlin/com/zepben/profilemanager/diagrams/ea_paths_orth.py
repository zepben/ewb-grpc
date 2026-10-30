from ea_utils import parse_ea_style, extract_ea_color
from ea_router import generate_orthogonal_route, clip_line_to_box, parse_geometry_path, is_point_inside

def draw_diagram_paths(cursor, diagram_id, elements_registry, ax):
    """Queries links, routes orthogonally, recursively traces/clips path borders, and plots."""
    if not elements_registry:
        return
    query = """
    SELECT c.Start_Object_ID, c.End_Object_ID, c.SourceCard, c.DestCard, c.Connector_Type, dl.Geometry, dl.Style
    FROM t_diagramlinks dl
    INNER JOIN t_connector c ON dl.ConnectorID = c.Connector_ID
    WHERE dl.DiagramID = ?;
    """
    cursor.execute(query, (diagram_id,))
    
    for src_id, dest_id, src_card, dest_card, conn_type, geom, style in cursor.fetchall():
        if src_id not in elements_registry or dest_id not in elements_registry:
            continue
            
        src, dest = elements_registry[src_id], elements_registry[dest_id]
        raw_nodes = [(src['cx'], src['cy'])] + parse_geometry_path(geom) + [(dest['cx'], dest['cy'])]
        
        mode = parse_ea_style(style, "Mode")
        if mode == "8" or conn_type in ['Generalization', 'Realization']:
            full_path = generate_orthogonal_route(raw_nodes)
        else:
            full_path = list(raw_nodes)

        # --- SEQUENTIAL PATH TRACING LOOPS ---
        try:
            # 1. TRACE SOURCE END: Walk forward dropping points buried inside the source box
            if is_point_inside(full_path[0], src):
                while len(full_path) >= 2:
                    current_pt = full_path[0]
                    next_pt    = full_path[1]
                    
                    if is_point_inside(next_pt, src):
                        full_path.pop(0)
                    else:
                        full_path[0] = clip_line_to_box(current_pt, next_pt, src)
                        break
                        
            # 2. TRACE DESTINATION END: Walk backward dropping points buried inside the target box
            if is_point_inside(full_path[-1], dest):
                while len(full_path) >= 2:
                    current_pt = full_path[-1]
                    next_pt    = full_path[-2]
                    
                    if is_point_inside(next_pt, dest):
                        full_path.pop()
                    else:
                        full_path[-1] = clip_line_to_box(current_pt, next_pt, dest)
                        break
        except Exception:
            pass

        if len(full_path) < 2:
            continue
            
        # Target index 0 explicitly for X and index 1 explicitly for Y
        x_coords = [node[0] for node in full_path]
        y_coords = [node[1] for node in full_path]

        ls, color, use_arrow = '-', '#555555', False
        color = extract_ea_color(style, "LCol", color)
        lw_raw = parse_ea_style(style, "LWth")
        lw = float(lw_raw) if lw_raw else 1.2

        if conn_type in ['Generalization', 'Realization']:
            ls = '-' if conn_type == 'Generalization' else '--'
            use_arrow = True

        # Render connection lines
        ax.plot(x_coords, y_coords, color=color, linewidth=lw, linestyle=ls, zorder=3)

        # Draw hollow triangle arrowheads
        if use_arrow and len(full_path) >= 2:
            x_tip, y_tip = x_coords[-1], y_coords[-1]
            x_tail, y_tail = x_coords[-2], y_coords[-2]
            
            ax.annotate(
                "", 
                xy=(x_tip, y_tip), 
                xytext=(x_tail, y_tail),
                zorder=4,
                arrowprops=dict(
                    arrowstyle="-|>",          
                    edgecolor=color,           
                    facecolor='white',         
                    patchA=None, patchB=None, lw=lw,                     
                    mutation_scale=14,         
                    shrinkA=0, shrinkB=0                   
                )
            )

        if src_card and src_card.strip():
            ax.text(x_coords[0], y_coords[0] + 6, src_card.strip(), color='#880000', fontsize=7, weight='semibold', zorder=4)
        if dest_card and dest_card.strip():
            ax.text(x_coords[-1], y_coords[-1] + 6, dest_card.strip(), color='#880000', fontsize=7, weight='semibold', zorder=4)
