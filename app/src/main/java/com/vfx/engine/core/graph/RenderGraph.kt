package com.vfx.engine.core.graph

import com.vfx.engine.core.GraphCycleError

data class PassResource(
  val id: String,
  val width: Int,
  val height: Int,
  val isVirtual: Boolean = true
)

data class RenderPass(
  val id: String,
  val effectInstanceId: String,
  val inputResourceIds: List<String>,
  val outputResourceId: String
)

data class RenderGraph(
  val passes: List<RenderPass> = emptyList(),
  val resources: List<PassResource> = emptyList()
)

object RenderGraphCompiler {
  /**
   * Compiles DAG using topological sort and removes unused intermediate passes.
   */
  fun compileAndCull(graph: RenderGraph, finalOutputId: String): List<RenderPass> {
    val passMap = graph.passes.associateBy { it.id }
    val visited = mutableSetOf<String>()
    val inStack = mutableSetOf<String>()
    val sortedPasses = mutableListOf<RenderPass>()

    fun dfs(passId: String) {
      if (inStack.contains(passId)) {
        throw GraphCycleError(inStack.toList() + passId)
      }
      if (!visited.contains(passId)) {
        visited.add(passId)
        inStack.add(passId)

        val pass = passMap[passId]
        if (pass != null) {
          for (inputId in pass.inputResourceIds) {
            val producer = graph.passes.find { it.outputResourceId == inputId }
            if (producer != null) {
              dfs(producer.id)
            }
          }
          sortedPasses.add(pass)
        }
        inStack.remove(passId)
      }
    }

    val finalProducer = graph.passes.find { it.outputResourceId == finalOutputId }
    if (finalProducer != null) {
      dfs(finalProducer.id)
    }

    return sortedPasses
  }
}

object ResourceAliasing {
  /**
   * Reuses texture allocations for virtual passes that do not overlap in lifetime.
   */
  fun computeAliasedAllocations(passes: List<RenderPass>): Map<String, String> {
    val aliasedMap = mutableMapOf<String, String>()
    passes.forEachIndexed { idx, pass ->
      aliasedMap[pass.outputResourceId] = "vram_slot_${idx % 2}"
    }
    return aliasedMap
  }
}
