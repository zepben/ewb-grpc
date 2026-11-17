/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.exporters.diagrams.render

/**
 * The data model site's component that shows a diagram (`evolve-datamodel-site/src/components/UmlDiagram.js`).
 */
internal const val UML_DIAGRAM_COMPONENT = "@site/src/components/UmlDiagram"

/**
 * The Docusaurus MDX page showing a diagram. The SVG is imported as a component (Docusaurus turns SVGs imported from
 * MDX into React components with SVGR) rather than linked as an image, so it renders into the page's DOM, letting its
 * styles use the page's theme variables to follow light/dark mode.
 *
 * The site's `UmlDiagram` component ([UML_DIAGRAM_COMPONENT]) draws it, wrapping each class box marked with
 * [SvgRenderer.HREF_ATTRIBUTE] in a Docusaurus `<Link>`, so they get the base URL, client side navigation and broken
 * link checking like every other link on the site. It scrolls rather than shrinking diagrams too wide for the page.
 *
 * Like the class pages, the page sets its [slug] to its file name, as otherwise Docusaurus serves a page named after
 * its folder (e.g. `Feeder/Feeder.mdx`) as the folder's index (`Feeder/`), breaking links to it.
 *
 * @param name The diagram's display name.
 * @param svgPath The path of the SVG, relative to the page.
 */
internal fun diagramPageMdx(name: String, slug: String, svgPath: String): String =
    """
    |---
    |title: $name 🔹
    |hide_table_of_contents: true
    |slug: $slug
    |sidebar_position: 99999
    |---
    |import UmlDiagram from '$UML_DIAGRAM_COMPONENT';
    |import Diagram from '$svgPath';
    |
    |### $name - *UML Class Diagram*
    |
    |<UmlDiagram svg={Diagram} hrefAttribute='${SvgRenderer.HREF_ATTRIBUTE}' />
    |
    |[View as SVG]($svgPath)
    |""".trimMargin()
