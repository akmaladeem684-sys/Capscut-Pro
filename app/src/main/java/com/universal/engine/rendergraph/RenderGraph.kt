package com.universal.engine.rendergraph

import com.universal.engine.errors.EngineError
import com.universal.engine.frame.FrameMetadata
import com.universal.engine.frame.GpuFrame

class RenderGraph(private val ctx: RenderContext) {

    private val nodes = LinkedHashMap<String, RenderNode>()
    private val edges = HashMap<String, MutableList<String>>()

    fun addNode(node: RenderNode, vararg inputs: RenderNode): RenderGraph {
        check(!nodes.containsKey(node.id)) { "duplicate node id ${node.id}" }
        require(inputs.size == node.inputCount) {
            "node ${node.id} expects ${node.inputCount} inputs, got ${inputs.size}"
        }
        nodes[node.id] = node
        edges[node.id] = inputs.map { it.id }.toMutableList()
        return this
    }

    fun validate() {
        edges.values.flatten().forEach { dep ->
            check(nodes.containsKey(dep)) { "node references missing dependency $dep" }
        }
        val indegree = HashMap<String, Int>()
        nodes.keys.forEach { id -> indegree[id] = edges.getValue(id).count { it != id } }
        val queue = ArrayDeque(indegree.filter { it.value == 0 }.keys)
        var visited = 0
        val outEdges = HashMap<String, MutableList<String>>()
        edges.forEach { (id, deps) -> deps.forEach { outEdges.getOrPut(it) { mutableListOf() }.add(id) } }
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst(); visited++
            outEdges[n]?.forEach { next ->
                indegree[next] = indegree.getValue(next) - 1
                if (indegree.getValue(next) == 0) queue.add(next)
            }
        }
        if (visited != nodes.size) throw EngineError.RenderError("render graph contains a cycle")
    }

    fun execute(metadata: FrameMetadata, sourceFrames: Map<String, GpuFrame>): GpuFrame {
        ctx.assertOnRenderThread()
        if (nodes.isEmpty()) throw EngineError.RenderError("empty render graph")

        val order = topologicalOrder()
        val results = HashMap<String, GpuFrame>()

        for (id in order) {
            val node = nodes.getValue(id)
            val depIds = edges.getValue(id)
            val inputs = depIds.map { dep ->
                results.getValue(dep)
            }
            if (inputs.isEmpty() && sourceFrames.containsKey(id)) {
                results[id] = sourceFrames.getValue(id)
                continue
            }
            results[id] = stageFrame(node, inputs, metadata)
        }

        val terminal = order.last()
        val consumed = edges.values.flatten().toSet()
        for ((id, frame) in results) {
            if (id != terminal && !consumed.contains(id)) {
                if (!isFrameConsumedDownstream(id, results)) frame.release()
            }
        }
        val out = results.getValue(terminal)
        results.entries.filter { it.key != terminal && !retainedIds.contains(it.key) }
            .forEach { if (it.value !== out && !it.value.isReleased) it.value.release() }
        return out
    }

    private val retainedIds = mutableSetOf<String>()

    private fun isFrameConsumedDownstream(id: String, results: Map<String, GpuFrame>): Boolean =
        edges.values.any { it.contains(id) }

    private fun topologicalOrder(): List<String> {
        validate()
        val order = mutableListOf<String>()
        val temp = HashSet<String>()
        val perm = HashSet<String>()
        fun visit(id: String) {
            if (perm.contains(id)) return
            if (temp.contains(id)) throw EngineError.RenderError("cycle at $id")
            temp.add(id)
            edges.getValue(id).forEach { if (nodes.containsKey(it)) visit(it) }
            temp.remove(id)
            perm.add(id)
            order.add(id)
        }
        nodes.keys.forEach { visit(it) }
        return order
    }

    private fun stageFrame(node: RenderNode, inputs: List<GpuFrame>, meta: FrameMetadata): GpuFrame {
        val stage = when (node.stage) {
            RenderDiagnosticsCompat.Stage.LAYER_2D -> com.universal.engine.diagnostics.RenderDiagnostics.Stage.LAYER_2D
            RenderDiagnosticsCompat.Stage.LAYER_3D -> com.universal.engine.diagnostics.RenderDiagnostics.Stage.LAYER_3D
            RenderDiagnosticsCompat.Stage.EFFECTS -> com.universal.engine.diagnostics.RenderDiagnostics.Stage.EFFECTS
            RenderDiagnosticsCompat.Stage.COMPOSITE -> com.universal.engine.diagnostics.RenderDiagnostics.Stage.COMPOSITE
            RenderDiagnosticsCompat.Stage.COLOR -> com.universal.engine.diagnostics.RenderDiagnostics.Stage.COLOR
            RenderDiagnosticsCompat.Stage.TRANSFORM -> com.universal.engine.diagnostics.RenderDiagnostics.Stage.TRANSFORM
            RenderDiagnosticsCompat.Stage.OUTPUT -> com.universal.engine.diagnostics.RenderDiagnostics.Stage.OUTPUT
            else -> com.universal.engine.diagnostics.RenderDiagnostics.Stage.EVALUATE
        }
        return ctx.diagnostics.stage(stage) {
            val t0 = System.nanoTime()
            try {
                node.render(ctx, inputs)
            } finally {
                ctx.diagnostics.gpuTimeNanos.addAndGet(System.nanoTime() - t0)
                ctx.diagnostics.renderedFrames.incrementAndGet()
            }
        }
    }

    fun release(ctx: RenderContext) { nodes.values.forEach { it.release(ctx) }; nodes.clear(); edges.clear() }
}
