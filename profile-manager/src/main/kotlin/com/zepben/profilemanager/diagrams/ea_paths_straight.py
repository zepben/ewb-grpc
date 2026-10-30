from ea_utils import parse_ea_style, extract_ea_color
from ea_router import parse_geometry_path

def draw_diagram_paths(cursor, diagram_id, elements_registry, ax):
    """DEBUG MODE: Draws raw, straight center-to-center layout paths."""
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
            
        src = elements_registry[src_id]
        dest = elements_registry[dest_id]
        
        # Build direct path: [Source Center] + [Any Manual Breakpoints] + [Destination Center]
        full_path = [(src['cx'], src['cy'])] + parse_geometry_path(geom) + [(dest['cx'], dest['cy'])]
            
        # Isolate the coordinate channels directly from the raw point tuples
        x_coords = [point[0] for point in full_path]
        y_coords = [point[1] for point in full_path]

        # Formatting configurations
        ls, color, marker = '-', '#555555', None
        color = extract_ea_color(style, "LCol", color)
        lw_raw = parse_ea_style(style, "LWth")
        lw = float(lw_raw) if lw_raw else 1.2

        if conn_type in ['Generalization', 'Realization']:
            ls = '-' if conn_type == 'Generalization' else '--'
            marker = '^'
        elif conn_type in ['Aggregation', 'Composition']:
            marker = 'd'

        # Render connection lines
        ax.plot(x_coords, y_coords, color=color, linewidth=lw, linestyle=ls, zorder=3)

        # Draw the target arrowhead marker at the absolute center point of the dest box
        if marker:
            ax.scatter(x_coords[-1], y_coords[-1], marker=marker, color=color, s=50,
                       edgecolors=color, facecolors='white' if marker=='^' else color, zorder=4)

        if src_card and src_card.strip():
            ax.text(x_coords[0], y_coords[0] + 6, src_card.strip(), color='#880000', fontsize=7, weight='semibold', zorder=4)
        if dest_card and dest_card.strip():
            ax.text(x_coords[-1], y_coords[-1] + 6, dest_card.strip(), color='#880000', fontsize=7, weight='semibold', zorder=4)
