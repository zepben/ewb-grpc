def normalize_and_extract_bounds(raw_elements, package_object_id, cursor,
                                 diagram_show_attribs, silent=False):

    elements_data = []

    # -----------------------------
    # 1. Build elements
    # -----------------------------
    for obj_id, name, el_type, left, top, right, bottom, style, note, sequence, parent_id in raw_elements:
        # ✅ requested coordinate flip
        top = -top
        bottom = -bottom

        y_min_box = min(top, bottom)
        y_max_box = max(top, bottom)

        # ✅ requested attribute fetch
        visible_attribs = []
        if el_type in ['Class', 'Interface', 'Enumeration']:
            visible_attribs = fetch_visible_attributes(cursor, obj_id, style, diagram_show_attribs)

        elements_data.append({
            'seq': int(sequence) if sequence is not None else 0,
            'id': obj_id,
            'name': name,
            'type': el_type,
            'left': float(left),
            'right': float(right),
            'ymin': float(y_min_box),
            'ymax': float(y_max_box),
            'style': style,
            'note': note,
            'parent_id': parent_id,
            'attributes': visible_attribs,   # ✅ added
            'growth_w': 0.0,
            'growth_h': 0.0,
            'shift_x': 0.0,
            'shift_y': 0.0
        })

    # -----------------------------
    # 2. Calculate growth
    # -----------------------------
    for el in elements_data:
        calculate_growth(el, silent)   # ✅ updated call

    # -----------------------------
    # 3. Capture ORIGINAL pair spacing
    # -----------------------------
    pair_gaps = {}

    def compute_gap(a, b):
        gx = max(0.0, max(b['left'] - a['right'], a['left'] - b['right']))
        gy = max(0.0, max(b['ymin'] - a['ymax'], a['ymin'] - b['ymax']))
        return gx, gy

    n = len(elements_data)
    for i in range(n):
        for j in range(i + 1, n):
            gx, gy = compute_gap(elements_data[i], elements_data[j])
            pair_gaps[(i, j)] = (gx, gy)

    # -----------------------------
    # 4. Grow from center (correct)
    # -----------------------------
    for el in elements_data:
        hw = el['growth_w'] / 2.0
        hh = el['growth_h'] / 2.0

        el['left']  -= hw
        el['right'] += hw
        el['ymin']  -= hh
        el['ymax']  += hh

    # -----------------------------
    # 5. Helpers
    # -----------------------------
    def cx(el):
        return (el['left'] + el['right']) / 2.0

    def cy(el):
        return (el['ymin'] + el['ymax']) / 2.0

    def aligned_x(a, b, tol=1e-6):
        return abs(cx(a) - cx(b)) < tol

    def aligned_y(a, b, tol=1e-6):
        return abs(cy(a) - cy(b)) < tol

    # -----------------------------
    # 6. Iterative relaxation (pairwise constraints)
    # -----------------------------
    max_iter = 30

    for _ in range(max_iter):
        moved = False

        for i in range(n):
            for j in range(i + 1, n):
                a = elements_data[i]
                b = elements_data[j]

                target_gx, target_gy = pair_gaps[(i, j)]

                # current gaps
                cur_gx, cur_gy = compute_gap(a, b)

                need_x = target_gx - cur_gx
                need_y = target_gy - cur_gy

                # only act if we violated spacing
                fix_x = need_x > 1e-6
                fix_y = need_y > 1e-6

                if not (fix_x or fix_y):
                    continue

                # choose axis (respect alignment)
                if aligned_x(a, b):
                    axis = 'y'
                elif aligned_y(a, b):
                    axis = 'x'
                else:
                    if fix_x and fix_y:
                        axis = 'x' if need_x < need_y else 'y'
                    else:
                        axis = 'x' if fix_x else 'y'
