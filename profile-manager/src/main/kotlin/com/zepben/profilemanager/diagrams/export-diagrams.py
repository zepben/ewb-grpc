import os
import sqlite3
import traceback

import matplotlib.pyplot as plt

from ea_boxes import draw_diagram_boxes
from ea_paths_orth import draw_diagram_paths
from config import DB_PATH, OUTPUT_ROOT, START_PACKAGE_NAME

plt.rcParams['svg.fonttype'] = 'path'

# --- TREE WALKING UTILITIES ---
def build_package_maps(cursor):
    cursor.execute("SELECT Package_ID, Parent_ID, Name FROM t_package")
    parent_map, name_map, child_map = {}, {}, {}
    for pkg_id, parent_id, name in cursor.fetchall():
        parent_map[pkg_id] = parent_id
        clean = "".join(c for c in name if c.isalnum() or c in (' ', '_', '-')).strip()
        name_map[pkg_id] = clean or f"Package_{pkg_id}"
        child_map.setdefault(parent_id, []).append(pkg_id)
    return parent_map, name_map, child_map

def find_target_package_id(name_map, target_name):
    for pkg_id, name in name_map.items():
        if name.lower() == target_name.lower(): 
            return pkg_id
    return None

def get_all_descendant_packages(root_id, child_map):
    descendants = {root_id}
    queue = [root_id]
    while queue:
        curr = queue.pop(0)
        for child in child_map.get(curr, []):
            if child not in descendants:
                descendants.add(child)
                queue.append(child)
    return descendants

def get_package_path_scoped(package_id, root_id, parent_map, name_map):
    path_parts = []
    current_id = package_id
    while current_id in name_map and current_id != 0:
        path_parts.append(name_map[current_id])
        if current_id == root_id: 
            break
        current_id = parent_map.get(current_id, 0)
    path_parts.reverse()
    return os.path.join(*path_parts) if path_parts else "Root"

# --- MAIN CANVAS ORCHESTRATOR ---
def export_single_diagram(cursor, diagram_id, diagram_name, output_path):
    """Dynamically scales the Matplotlib canvas to establish a strict 1-to-1 pixel-locked 
    coordinate system, ensuring text text scales identically across all diagrams.
    """
    clean_name = "".join(c for c in diagram_name if c.isalnum() or c in (' ', '_', '-')).strip()
    print(f" -> Creating SVG: '{clean_name}' ({diagram_id})...", end="")

    # 1. Run a silent pass to discover the true diagram bounds first
    tmp_fig, tmp_ax = plt.subplots()
    registry, bounds = draw_diagram_boxes(cursor, diagram_id, tmp_ax, print_debug=True)
    plt.close(tmp_fig)
    
    if not registry:
        return

    x_min, x_max, y_min, y_max = bounds
    geo_width = abs(x_max - x_min)
    geo_height = abs(y_max - y_min)
    
    if geo_width == 0: geo_width = 100
    if geo_height == 0: geo_height = 100
    
    # 2. FIXED UNIFIED PIXEL ARCHITECTURE
    # Force the figure size to be defined directly by your coordinate pixel layout width.
    # By setting a fixed 100 DPI standard, 1 layout unit matches exactly 1 canvas display pixel!
    DPI_STANDARD = 100
    fig_w = (geo_width + 120) / DPI_STANDARD   # Add padding area inside the inch calculations
    fig_h = (geo_height + 120) / DPI_STANDARD

    fig, ax = plt.subplots(figsize=(fig_w, fig_h), dpi=DPI_STANDARD)
    
    # Execute the true production draw pass with accurate console tracing logs
    registry, bounds = draw_diagram_boxes(cursor, diagram_id, ax)
    draw_diagram_paths(cursor, diagram_id, registry, ax)

    # 3. Apply uniform padding limits
    padding = 60
    ax.set_xlim(x_min - padding, x_max + padding)
    ax.set_ylim(y_min - padding, y_max + padding)
    ax.set_aspect('equal')
    ax.axis('off')
    
    ax.invert_yaxis()

    full_file_path = os.path.join(output_path, f"{clean_name}_{diagram_id}.svg")
    try:
        plt.savefig(full_file_path, format='svg', bbox_inches='tight', transparent=False)
        print(f"done [Resolution: {geo_width:.0f}x{geo_height:.0f} px]")
    except Exception as ex:
        print(f"failed: {ex}")
        traceback.print_exc()
    finally:
        plt.close(fig)

def main():
    if not os.path.exists(DB_PATH):
        print(f"Error: Database missing at {DB_PATH}"); return
        
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    parent_map, name_map, child_map = build_package_maps(cursor)
    
    root_id = find_target_package_id(name_map, START_PACKAGE_NAME)
    if root_id is None:
        print(f"Error: Target root package '{START_PACKAGE_NAME}' not found."); conn.close(); return

    allowed_packages = get_all_descendant_packages(root_id, child_map)
    cursor.execute("SELECT Diagram_ID, Package_ID, Name FROM t_diagram")
    diagrams = cursor.fetchall()

    print(f"Running automated package branch crawl for '{START_PACKAGE_NAME}'...")
    count = 0
    for diag_id, pkg_id, diag_name in diagrams:
        if pkg_id not in allowed_packages: 
            continue

        # TODO: DEBUG WHITELIST FILTER
        debug = {}
#        debug = {3611, 3620}
#        debug = {3620}
#        debug = {3611}
#        debug = {13194}
#        debug = {13195}
        if debug and diag_id not in debug:
            continue

        target_directory = os.path.join(OUTPUT_ROOT, get_package_path_scoped(pkg_id, root_id, parent_map, name_map))
        os.makedirs(target_directory, exist_ok=True)
        export_single_diagram(cursor, diag_id, diag_name, target_directory)
        
    conn.close()
    print("\nBatch process execution finished successfully!")

if __name__ == "__main__":
    main()
