from ea_utils import parse_ea_style

def fetch_diagram_metadata(cursor, diagram_id):
    """Fetches home package details that owns this diagram record."""
    cursor.execute("""
        SELECT p.Package_ID, p.Name, d.StyleEx 
        FROM t_package p 
        INNER JOIN t_diagram d ON p.Package_ID = d.Package_ID 
        WHERE d.Diagram_ID = ?
    """, (diagram_id,))
    return cursor.fetchone()

def fetch_raw_elements(cursor, diagram_id, diagram_package_id, diagram_package_name):
    """Queries both explicit diagram shapes and structural implicit children."""
    query = """
    SELECT o.Object_ID, o.Name, o.Object_Type, do.RectLeft, do.RectTop, do.RectRight, do.RectBottom, do.ObjectStyle, o.Note, do.Sequence, null as parent_id
    FROM t_diagramobjects do
    INNER JOIN t_object o ON do.Object_ID = o.Object_ID
    WHERE do.Diagram_ID = ?;
    """
    cursor.execute(query, (diagram_id,))
    rows = cursor.fetchall()
    raw_elements = list(rows) if rows else []
    
    if not rows or any(row[2] == "Package" and row[1] == diagram_package_name for row in rows):
        existing_ids = {it[0] for it in raw_elements}

        cursor.execute(f"""
            SELECT Object_ID, Name, Object_Type, 100 as RectLeft, -100 as RectTop, 300 as RectRight, -200 as RectBottom, 
                   '' as ObjectStyle, Note, - ROW_NUMBER() OVER () as Sequence, {diagram_id} as parent_id
            FROM t_object 
            WHERE Package_ID = ? AND Object_Type = 'Package'
        """, (diagram_package_id,))
        indirect_rows = cursor.fetchall()
        if indirect_rows:
            raw_elements.extend((it for it in indirect_rows if it[0] not in existing_ids))
            
    return raw_elements

def fetch_visible_attributes(cursor, object_id, el_type, obj_style, diagram_show_attribs):
    """Fetches and filters attributes based on StyleEx and ObjectStyle overrides."""
    if (diagram_show_attribs == 0) or (el_type not in ['Class', 'Interface', 'Enumeration'] ) or (parse_ea_style(obj_style, "HideAttr") == "1"):
        return []

    cursor.execute("SELECT Name, Type FROM t_attribute WHERE Object_ID = ? ORDER BY Pos ASC", (object_id,))
    all_attributes = cursor.fetchall()
    advanced_attr_str = parse_ea_style(obj_style, "AdvancedAttr")
    visible_attributes = []

    for name, attr_type in all_attributes:
        if not name: continue
        if advanced_attr_str and f"{name}=0" in advanced_attr_str: continue
        attr_label = f"+ {name}" if not attr_type else f"+ {name}: {attr_type}"
        visible_attributes.append(attr_label)

    return visible_attributes
