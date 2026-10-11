package com.example.data.telemetry

import android.util.Log

enum class PipelineStage(val tag: String) {
    PLANNER("Planner"),
    SEARCH("Search"),
    RERANK("Rerank"),
    LLM("LLM"),
    STREAM("Stream"),
    PERSISTENCE("Persistence")
}

enum class PipelineType(val prefix: String) {
    FACT_CHECK("FactCheck"),
    DEEP_SEARCH("DeepSearch")
}

object PipelineTelemetry {

    fun log(type: PipelineType, stage: PipelineStage, message: String) {
        val tag = "[${type.prefix}:${stage.tag}]"
        Log.d(tag, message)
    }

    fun warn(type: PipelineType, stage: PipelineStage, message: String, throwable: Throwable? = null) {
        val tag = "[${type.prefix}:${stage.tag}]"
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
    }

    fun error(type: PipelineType, stage: PipelineStage, message: String, throwable: Throwable? = null) {
        val tag = "[${type.prefix}:${stage.tag}]"
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
}
