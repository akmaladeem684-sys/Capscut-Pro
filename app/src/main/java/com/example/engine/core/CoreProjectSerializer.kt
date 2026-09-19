package com.example.engine.core

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/** Stable text project format; media bytes are never embedded. */
object CoreProjectSerializer {
  private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
  private val adapter = moshi.adapter(CoreProject::class.java).indent("  ")

  fun toJson(project: CoreProject): String = adapter.toJson(project)

  fun fromJson(json: String): CoreProject = adapter.fromJson(json)
    ?: error("Project JSON did not contain a project")
}

