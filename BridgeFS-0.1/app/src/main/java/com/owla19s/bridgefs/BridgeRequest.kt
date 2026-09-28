package com.owla19s.bridgefs

object BridgeRequest {
    private val block = Regex("(?is)\\[bridgefs\\](.*?)\\[/bridgefs\\]")

    fun extractAll(text: String): List<String> =
        block.findAll(text).map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.toList()
}
