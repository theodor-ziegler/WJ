package org.sbx.llama.rag

import java.io.File

class RAGContextFinder(val selectorFunction: (List<String>, String) -> String) {
    fun select(path: String = DATA_PATH): String {
        println("RAG: path: $path")
        val file = File(path)
        if (file.isDirectory) {
            return select("$path/${selectorFunction(file.listFiles().run { buildList { this@run.forEach { add(it.name) } } }, path)}")
        }

        return path
    }

    fun ragContentDetermine(): String  {
        val selection = select()
        return if (!selection.endsWith("none")) File(selection).readText()
        else ""
    }

    companion object {
        const val DATA_PATH = "./auxiliary-data"
    }
}