package com.networktools.opencode.util

import android.util.Log

object Errors {

    private const val TAG = "NetworkTools"
    private const val STACK_FRAMES = 8

    fun formatAndLog(context: String, e: Throwable): String {
        Log.e(TAG, "[$context] 操作失败", e)
        val name = e::class.simpleName ?: "Exception"
        val msg = e.message?.trim()?.takeIf { it.isNotEmpty() } ?: "无详细信息"
        val stack = e.stackTrace
            .take(STACK_FRAMES)
            .joinToString("\n") { "    at $it" }
        return buildString {
            append(name)
            append(": ")
            append(msg)
            if (stack.isNotEmpty()) {
                append("\n")
                append(stack)
            }
        }
    }
}