package software.bevel.code_to_knowledge_graph.vscode

import software.bevel.graph_domain.parsing.IntermediateFileParser
import software.bevel.graph_domain.parsing.Parser
import software.bevel.graph_domain.graph.Graphlike
import software.bevel.graph_domain.graph.builder.GraphBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ParsingTestHelper {
    class CodeFile(
        val fileName: String,
        val code: String
    )

    companion object {

        fun runParseFileOnCode(parser: IntermediateFileParser, code: CodeFile, assertions: (GraphBuilder, Path) -> Unit) {
            if(!Files.exists(Paths.get("src/test/resources/tmp"))) {
                Files.createDirectory(Paths.get("src/test/resources/tmp"))
            }
            Files.write(Paths.get("src/test/resources/tmp/${code.fileName}"), code.code.toByteArray())

            // Parse the code
            val graph = parser.parseFile("src/test/resources/tmp/${code.fileName}")

            assertions(graph, Paths.get("src/test/resources/tmp/${code.fileName}"))

            // Cleanup the test file
            Files.delete(Paths.get("src/test/resources/tmp/${code.fileName}"))
        }

        fun<T> runParseGraphBuilderOnCode(parser: Parser, vararg codes: CodeFile, assertions: (GraphBuilder, List<Path>) -> T): T {
            Files.walk(Paths.get("src/test/resources/tmp"))
                .filter { Files.isRegularFile(it) }
                .forEach { Files.delete(it) }
            if(!Files.exists(Paths.get("src/test/resources/tmp"))) {
                Files.createDirectory(Paths.get("src/test/resources/tmp"))
            }
            val paths = mutableListOf<Path>()
            codes.forEachIndexed { i, code ->
                val path = Paths.get("src/test/resources/tmp/${code.fileName}")
                paths.add(path)
                Files.write(path, code.code.toByteArray())
            }
            val graph = parser.parseToGraphBuilder(listOf("src/test/resources/tmp"))
            val result = assertions(graph, paths)
            codes.forEachIndexed { i, code ->
                Files.delete(Paths.get("src/test/resources/tmp/${code.fileName}"))
            }
            return result
        }

        fun runParseOnCode(parser: Parser, vararg codes: CodeFile, assertions: (Graphlike, List<Path>) -> Unit) {
            Files.walk(Paths.get("src/test/resources/tmp"))
                .filter { Files.isRegularFile(it) }
                .forEach { Files.delete(it) }
            if(!Files.exists(Paths.get("src/test/resources/tmp"))) {
                Files.createDirectory(Paths.get("src/test/resources/tmp"))
            }
            val paths = mutableListOf<Path>()
            codes.forEachIndexed { i, code ->
                val path = Paths.get("src/test/resources/tmp/${code.fileName}")
                paths.add(path)
                Files.write(path, code.code.toByteArray())
            }
            val graph = parser.parse(listOf("C:/src/test/resources/tmp"))
            assertions(graph, paths)
            codes.forEachIndexed { i, code ->
                Files.delete(Paths.get("src/test/resources/tmp/${code.fileName}"))
            }
        }
    }
}