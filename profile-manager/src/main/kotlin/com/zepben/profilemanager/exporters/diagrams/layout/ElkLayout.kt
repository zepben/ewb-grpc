/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.exporters.diagrams.layout

import com.zepben.profilemanager.exporters.diagrams.model.DiagramModel
import com.zepben.profilemanager.exporters.diagrams.model.EdgeKind
import com.zepben.profilemanager.exporters.diagrams.model.labels
import com.zepben.profilemanager.exporters.diagrams.*
import com.zepben.profilemanager.models.Link
import org.eclipse.elk.alg.layered.options.GraphCompactionStrategy
import org.eclipse.elk.alg.layered.options.LayeredMetaDataProvider
import org.eclipse.elk.alg.layered.options.LayeredOptions
import org.eclipse.elk.alg.layered.options.LayeringStrategy
import org.eclipse.elk.core.RecursiveGraphLayoutEngine
import org.eclipse.elk.core.data.LayoutMetaDataService
import org.eclipse.elk.core.math.ElkPadding
import org.eclipse.elk.core.options.CoreOptions
import org.eclipse.elk.core.options.Direction
import org.eclipse.elk.core.options.EdgeLabelPlacement
import org.eclipse.elk.core.options.EdgeRouting
import org.eclipse.elk.core.options.PortConstraints
import org.eclipse.elk.core.options.PortSide
import org.eclipse.elk.core.util.BasicProgressMonitor
import org.eclipse.elk.graph.ElkConnectableShape
import org.eclipse.elk.graph.ElkEdge
import org.eclipse.elk.graph.ElkLabel
import org.eclipse.elk.graph.ElkNode
import org.eclipse.elk.graph.util.ElkGraphUtil

/**
 * Lays out a diagram using the ELK layered algorithm. Generalisation edges point from subclass to superclass with the
 * layout direction set to UP, so superclasses end up above their subclasses. Edges are routed orthogonally, and the
 * role and cardinality of each association end are placed as head/tail labels next to the class they describe.
 *
 * How associations are oriented, which leaves are stacked and (optionally) which layer each class goes in are decided
 * up front by [LayerPlanner]; this class only translates the diagram to and from ELK's graph. The result is ELK's
 * layout as is; [tidy] improves on its routing and labels.
 */
class ElkLayout(private val sizer: BoxSizer, val config: LayoutConfig = LayoutConfig()) {

    init {
        registerAlgorithms
    }

    fun layout(model: DiagramModel): DiagramLayout {
        val sized = model.nodes.associate { it.name to sizer.size(it) }
        val associations = LayerPlanner.orientAssociations(model)
        val layers = if (config.layerByInheritance) LayerPlanner.inheritanceLayers(model, associations) else emptyMap()
        val stacks = LayerPlanner.leafStacks(model, config.stackLeavesFrom)
        val stacked = stacks.flatMap { it.leaves }.toSet()

        val graph = createGraph(model)
        val elkNodes = sized.values.filter { it.node.name !in stacked }.associate { it.node.name to createNode(graph, it, layers[it.node.name]) }
        val stackNodes = stacks.map { createStackNode(graph, it, it.leaves.map(sized::getValue), layers[it.leaves.first()]) }

        // Each edge of the diagram, not yet routed, with the ELK edge that will give its route and labels. Edges are
        // added in this order (generalisations, stack trunks, then associations) as it affects ELK's result.
        val generalisations = model.generalisations.filter { it.sub !in stacked }.map { g ->
            unrouted(EdgeKind.GENERALISATION, g.sub, g.sup) to generalisationEdge(elkNodes.getValue(g.sub), elkNodes.getValue(g.sup))
        }
        val elkStacks = stacks.zip(stackNodes) { stack, node ->
            ElkStack(stack, node, generalisationEdge(node.ports.single(), elkNodes.getValue(stack.parent)))
        }
        val associationEdges = associations.map { link ->
            unrouted(EdgeKind.ASSOCIATION, link.source, link.target) to associationEdge(link, elkNodes)
        }

        RecursiveGraphLayoutEngine().layout(graph, BasicProgressMonitor())

        val positioned = elkNodes.mapValues { (name, node) -> PositionedNode(sized.getValue(name), node.x, node.y) } +
            elkStacks.flatMap { it.leafPositions(sized) }
        val positionedEdges = (generalisations + associationEdges).map { (edge, elk) -> edge.routedBy(elk) } +
            elkStacks.flatMap { it.leafEdges(positioned) }

        return DiagramLayout(
            nodes = model.nodes.map { positioned.getValue(it.name) },
            edges = positionedEdges,
            width = graph.width,
            height = graph.height,
        )
    }

    private fun unrouted(kind: EdgeKind, source: String, target: String) = PositionedEdge(kind, source, target, emptyList(), emptyList())

    private fun PositionedEdge.routedBy(elk: ElkEdge) = copy(points = elk.points(), labels = elk.labels.map { it.positioned() })

    private fun createNode(graph: ElkNode, sized: SizedNode, layer: Int?): ElkNode = ElkGraphUtil.createNode(graph).apply {
        identifier = sized.node.name
        width = sized.width
        height = sized.height
        placeInLayer(layer)
    }

    /**
     * Only used when layering by inheritance: interactive layering assigns layers from the starting position along the
     * layout direction. With the direction UP, layer 0 is at the bottom.
     */
    private fun ElkNode.placeInLayer(layer: Int?) {
        layer?.let { y = -it * 10_000.0 }
    }

    private fun createGraph(model: DiagramModel): ElkNode = ElkGraphUtil.createGraph().apply {
        setProperty(CoreOptions.ALGORITHM, LayeredOptions.ALGORITHM_ID)
        setProperty(CoreOptions.DIRECTION, Direction.UP)
        setProperty(CoreOptions.EDGE_ROUTING, EdgeRouting.ORTHOGONAL)
        setProperty(CoreOptions.PADDING, ElkPadding(DIAGRAM_PADDING))
        setProperty(LayeredOptions.SPACING_NODE_NODE, NODE_SPACING)
        setProperty(LayeredOptions.SPACING_NODE_NODE_BETWEEN_LAYERS, LAYER_SPACING)
        setProperty(LayeredOptions.SPACING_EDGE_NODE, EDGE_NODE_SPACING)
        // Edges turn in the gaps between layers, and by default can turn close enough to a box to be right underneath
        // a generalisation triangle. ELK's orthogonal router only honours this spacing graph wide (not per node), so
        // when there are triangles, keep all turns far enough from boxes to leave a visible stem below each one.
        setProperty(
            LayeredOptions.SPACING_EDGE_NODE_BETWEEN_LAYERS,
            if (model.generalisations.isEmpty()) EDGE_NODE_SPACING
            else maxOf(EDGE_NODE_SPACING, GENERALISATION_ARROW + MIN_ARROW_STEM),
        )
        setProperty(LayeredOptions.SPACING_EDGE_EDGE, EDGE_EDGE_SPACING)
        setProperty(LayeredOptions.SPACING_EDGE_EDGE_BETWEEN_LAYERS, EDGE_EDGE_SPACING)
        setProperty(LayeredOptions.SPACING_EDGE_LABEL, 4.0)
        setProperty(LayeredOptions.SPACING_LABEL_LABEL, 4.0)
        setProperty(LayeredOptions.THOROUGHNESS, THOROUGHNESS)
        // Draw the generalisations into each superclass as a single tree.
        setProperty(LayeredOptions.MERGE_EDGES, true)
        // ELK lays out each connected component on its own and packs them side by side, which leaves a class with no
        // connectors in the diagram stranded in a corner. Compaction moves small components into the gaps of big ones.
        setProperty(LayeredOptions.COMPACTION_CONNECTED_COMPONENTS, true)
        setProperty(LayeredOptions.LAYERING_STRATEGY, if (config.layerByInheritance) LayeringStrategy.INTERACTIVE else config.layering)
        config.maxNodesPerLayer?.let { setProperty(LayeredOptions.LAYERING_COFFMAN_GRAHAM_LAYER_BOUND, it) }
        if (config.compact)
            setProperty(LayeredOptions.COMPACTION_POST_COMPACTION_STRATEGY, GraphCompactionStrategy.EDGE_LENGTH)
    }

    /** A node big enough for [leaves] stacked to the right of a bus line, with a fixed port at the top of the line. */
    private fun createStackNode(graph: ElkNode, stack: LeafStack, leaves: List<SizedNode>, layer: Int?): ElkNode =
        ElkGraphUtil.createNode(graph).apply {
            identifier = "stack of ${stack.parent}"
            placeInLayer(layer)
            width = BUS_INDENT + leaves.maxOf { it.width }
            height = leaves.sumOf { it.height } + STACK_GAP * (leaves.size - 1)
            setProperty(CoreOptions.PORT_CONSTRAINTS, PortConstraints.FIXED_POS)
            ElkGraphUtil.createPort(this).apply {
                x = BUS_INDENT / 2
                y = 0.0
                width = 0.0
                height = 0.0
                setProperty(CoreOptions.PORT_SIDE, PortSide.NORTH)
            }
        }

    /** ELK should care much more about keeping generalisations short and straight than associations. */
    private fun generalisationEdge(sub: ElkConnectableShape, sup: ElkConnectableShape): ElkEdge =
        ElkGraphUtil.createSimpleEdge(sub, sup).apply {
            setProperty(LayeredOptions.PRIORITY_DIRECTION, GENERALISATION_PRIORITY)
            setProperty(LayeredOptions.PRIORITY_SHORTNESS, GENERALISATION_PRIORITY)
            setProperty(LayeredOptions.PRIORITY_STRAIGHTNESS, GENERALISATION_PRIORITY)
        }

    /**
     * ELK only merges edges without ports, so each association gets its own ports to keep it a separate line. Ports
     * are free, letting ELK choose which side of the box each one goes on.
     */
    private fun associationEdge(link: Link, elkNodes: Map<String, ElkNode>): ElkEdge {
        fun ElkNode.freePort() = ElkGraphUtil.createPort(this).apply {
            width = 0.0
            height = 0.0
        }

        return ElkGraphUtil.createSimpleEdge(elkNodes.getValue(link.source).freePort(), elkNodes.getValue(link.target).freePort()).apply {
            val labels = link.labels()
            labels.source?.let { addLabel(it, EdgeLabelPlacement.TAIL) }
            labels.target?.let { addLabel(it, EdgeLabelPlacement.HEAD) }
        }
    }

    private fun ElkEdge.addLabel(text: String, placement: EdgeLabelPlacement) {
        ElkGraphUtil.createLabel(text, this).apply {
            width = sizer.labelWidth(text)
            height = LABEL_HEIGHT
            setProperty(CoreOptions.EDGE_LABELS_PLACEMENT, placement)
        }
    }

    private fun ElkLabel.positioned() = PositionedLabel(
        text, x, y, width, height,
        atSource = getProperty(CoreOptions.EDGE_LABELS_PLACEMENT) == EdgeLabelPlacement.TAIL,
    )

    companion object {

        private const val NODE_SPACING = 25.0
        private const val LAYER_SPACING = 40.0
        private const val EDGE_NODE_SPACING = 12.0
        private const val EDGE_EDGE_SPACING = 10.0
        private const val THOROUGHNESS = 100

        /** How much more ELK should care about keeping generalisations short and straight than associations. */
        private const val GENERALISATION_PRIORITY = 10

        // Outside of Eclipse, layout algorithms must be registered manually.
        private val registerAlgorithms by lazy {
            LayoutMetaDataService.getInstance().registerLayoutMetaDataProviders(LayeredMetaDataProvider())
        }

    }

}

/**
 * A [LeafStack] as laid out by ELK: a single [node] with a fixed port at the top of its bus line, from which the
 * [trunk] runs up to the parent.
 */
private class ElkStack(val stack: LeafStack, val node: ElkNode, val trunk: ElkEdge) {

    /** The positions of the leaves: one above the other, to the right of the bus line. */
    fun leafPositions(sized: Map<String, SizedNode>): List<Pair<String, PositionedNode>> {
        var top = node.y
        return stack.leaves.map { leaf ->
            val s = sized.getValue(leaf)
            (leaf to PositionedNode(s, node.x + BUS_INDENT, top)).also { top += s.height + STACK_GAP }
        }
    }

    /** The generalisation from each leaf to the bus line, which continues up to the parent. */
    fun leafEdges(positioned: Map<String, PositionedNode>): List<PositionedEdge> {
        val trunk = trunk.points()
        val busX = node.x + BUS_INDENT / 2
        return stack.leaves.map { leaf ->
            val node = positioned.getValue(leaf)
            val midY = node.y + node.height / 2
            PositionedEdge(EdgeKind.GENERALISATION, leaf, stack.parent, listOf(Point(node.x, midY), Point(busX, midY)) + trunk, emptyList())
        }
    }

}

private fun ElkEdge.points(): List<Point> = sections.flatMap { section ->
    listOf(Point(section.startX, section.startY)) +
        section.bendPoints.map { Point(it.x, it.y) } +
        Point(section.endX, section.endY)
}
