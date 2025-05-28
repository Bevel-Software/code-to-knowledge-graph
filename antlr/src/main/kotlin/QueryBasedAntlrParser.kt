package software.bevel.code_to_knowledge_graph.antlr

import org.antlr.runtime.CharStream.EOF
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.atn.PredictionMode
import org.antlr.v4.runtime.tree.ParseTreeWalker
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.snt.inmemantlr.listener.DefaultTreeListener
import org.snt.inmemantlr.listener.InmemantlrErrorListener
import org.snt.inmemantlr.tree.ParseTree
import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.QueryMatcher
import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.QueryParser
import software.bevel.graph_domain.*
import software.bevel.graph_domain.graph.Graphlike
import software.bevel.graph_domain.graph.builder.DanglingNodeBuilder
import software.bevel.graph_domain.graph.builder.GraphBuilder
import software.bevel.graph_domain.parsing.IntermediateFileParser
import software.bevel.graph_domain.parsing.IntermediateStringParser
import software.bevel.graph_domain.parsing.Parser
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * An ANTLR-based parser that utilizes query matching for constructing a knowledge graph.
 * This parser is guided by a [QueryBasedAntlrLanguageSpecification] to understand the language structure
 * and apply query patterns to extract relevant information for the graph.
 *
 * Implements [Parser], [IntermediateFileParser], and [IntermediateStringParser] to provide different parsing entry points.
 *
 * @param PARSER The type of the ANTLR parser, extending [org.antlr.v4.runtime.Parser].
 * @param LEXER The type of the ANTLR lexer, extending [org.antlr.v4.runtime.Lexer].
 * @property languageSpecification The language-specific configuration providing lexer, parser, query patterns, and other rules.
 * @property logger The logger instance for this parser.
 * @property graphAugmenter An instance of [GraphAugmenter] used to enhance and normalize the constructed graph.
 */
class QueryBasedAntlrParser<PARSER: org.antlr.v4.runtime.Parser, LEXER: org.antlr.v4.runtime.Lexer>(
    val languageSpecification: QueryBasedAntlrLanguageSpecification<PARSER, LEXER>,
    override val logger: Logger = LoggerFactory.getLogger(QueryBasedAntlrParser::class.java),
    val graphAugmenter: GraphAugmenter = GraphAugmenter(logger)
    ): Parser, IntermediateFileParser, IntermediateStringParser {
    /**
     * A [DefaultTreeListener] used to listen to ANTLR parse tree events. It's configured to produce a parse tree.
     */
    val listener = DefaultTreeListener(true)

    /**
     * Walks a given directory to find all files that should be parsed according to the [languageSpecification].
     *
     * @param directory The root directory to search for files.
     * @return A list of absolute file paths that match the parsing criteria.
     */
    private fun fileWalker(directory: String): List<String> {
        return Files.walk(Paths.get(directory))
            .filter { Files.isRegularFile(it) }
            .map { it.toString() }
            .filter { languageSpecification.shouldParseFile(it) }
            .toList()
    }

    /**
     * Parses projects located at the given paths and builds a combined knowledge graph.
     * The project path of the first path in [pathsToProjects] is used for the final graph build.
     *
     * @param pathsToProjects A list of root directory paths for the projects to be parsed.
     * @return A [Graphlike] object representing the combined knowledge graph.
     */
    override fun parse(pathsToProjects: List<String>): Graphlike {
        return parseToGraphBuilder(pathsToProjects).build(projectPath = pathsToProjects[0])
    }

    /**
     * Parses all parsable files within the specified project paths into a single [GraphBuilder].
     * It first collects all relevant files using [fileWalker], then calls [parseFiles] (implicitly, as it's not defined
     * in this snippet but assumed to be part of the class or an extension) to process them into a graph.
     * The resulting graph is then augmented, validated, and metrics are collected.
     *
     * @param pathsToProjects A list of root directory paths for the projects to be parsed.
     * @return A [GraphBuilder] containing the combined graph structure from all parsed files, after processing.
     */
    override fun parseToGraphBuilder(pathsToProjects: List<String>): GraphBuilder {
        val files = pathsToProjects.flatMap { fileWalker(it) }
        val mergedGraph = parseFiles(files)
        augmentGraph(mergedGraph)
        validateGraph(mergedGraph)
        Metrics.getMetrics(mergedGraph)
        return mergedGraph
    }

    /**
     * Validates the integrity of the constructed graph.
     * It checks for connections with missing source or target nodes and logs errors for them.
     * It also logs errors for any [DanglingNodeBuilder] instances that could not be fully qualified.
     *
     * @param graph The [GraphBuilder] instance to validate.
     */
    private fun validateGraph(graph: GraphBuilder) {
        graph.connectionsBuilder.getAllConnections().forEach { connection ->
            if (!graph.nodes.containsKey(connection.sourceNodeName) || !graph.nodes.containsKey(connection.targetNodeName)) {
                logger.error("Connection: $connection with sourceNode: ${connection.sourceNodeName} and targetNode: ${connection.targetNodeName} is invalid")
                if (graph.nodes.containsKey(connection.sourceNodeName)) {
                    logger.error("SourceNode: ${graph.nodes[connection.sourceNodeName]}")
                }
                if (graph.nodes.containsKey(connection.targetNodeName)) {
                    logger.error("TargetNode: ${graph.nodes[connection.targetNodeName]}")
                }
            }
        }
        graph.nodes.values.filterIsInstance<DanglingNodeBuilder>().forEach {
            logger.error("DanglingNodeBuilder ${it.id} could not be qualified")
        }
    }

    /**
     * Augments the provided [GraphBuilder] instance using various strategies from [GraphAugmenter].
     * This includes merging module nodes, adding default constructors, fully qualifying node names,
     * attempting to force qualification for remaining dangling nodes, and inferring module connections.
     * The commented-out call to `addParentConnectionsToFullyQualifiedNodes` suggests it's a deprecated or optional step.
     *
     * @param graph The [GraphBuilder] instance to augment.
     */
    private fun augmentGraph(graph: GraphBuilder) {
        graphAugmenter.mergeModuleNodes(graph)
        graphAugmenter.addDefaultConstructor(graph)
        graphAugmenter.fullyQualifyNodeNames(graph, languageSpecification::findFullyQualifiedNodeInImports)
        //GraphAugmenter.addParentConnectionsToFullyQualifiedNodes(graph)
        graphAugmenter.forceQualify(graph)
        graphAugmenter.inferModuleConnections(graph)
    }

    /**
     * Parses a single file specified by its path into a [GraphBuilder].
     * Reads the file content and delegates to [parseString].
     *
     * @param absolutePathToFile The absolute path to the file to be parsed.
     * @return A [GraphBuilder] representing the knowledge graph of the parsed file.
     */
    override fun parseFile(absolutePathToFile: String, initialGraph: GraphBuilder?): GraphBuilder {
        val codeFile = File(absolutePathToFile)
        return parseString(codeFile.readText(), absolutePathToFile, 0).let {
            initialGraph?.addAll(it)
            it
        }
    }

    /**
     * Parses the given string content, treating it as if it originated from the specified [filePath].
     * This is the core parsing logic that uses ANTLR to generate a parse tree, then applies query matching
     * using [QueryMatcher] and [QueryMatchTreeWalker] to construct an [AntlrGraphBuilder].
     *
     * It performs several steps:
     * 1. Initializes ANTLR lexer and parser with the input string.
     * 2. Builds a parse tree using the [listener].
     * 3. Uses [QueryMatcher] to find matches for patterns defined in [languageSpecification].
     * 4. Walks the parse tree and query match tree using [QueryMatchTreeWalker] to build the graph.
     * 5. Optionally, performs identifier validation if `baseIdentifierName` is set in [languageSpecification].
     * 6. Populates `importStatements` for any [DanglingNodeBuilder] instances.
     *
     * @param text The string content to parse.
     * @param filePath The original file path associated with the text (used for context).
     * @param startIndex The starting index within the original file, if applicable (not directly used in current ANTLR setup).
     * @return An [AntlrGraphBuilder] containing the graph structure derived from the text.
     * @throws Exception if the ANTLR listener fails to produce a parse tree.
     */
    override fun parseString(text: String, filePath: String, startIndex: Int): GraphBuilder {
        listener.reset()
        val charStream: CharStream = CharStreams.fromString(text)
        var firstChar = charStream.LA(1)
        while((firstChar > 127 || firstChar < 0) && charStream.LA(1) != EOF) {
            charStream.consume()
            firstChar = charStream.LA(1)
        }
        val lexer = languageSpecification.getLexer(charStream)
        val el: InmemantlrErrorListener = InmemantlrErrorListener()
        lexer.addErrorListener(el)
        val tokens: CommonTokenStream = CommonTokenStream(lexer)
        val parser = languageSpecification.getParser(tokens)
        listener.setParser(parser)
        //parser.addParseListener(DefaultListener())
        parser.addErrorListener(el)
        parser.interpreter.predictionMode = PredictionMode.LL
        parser.buildParseTree = true
        parser.tokenStream = tokens

        val data = languageSpecification.runTopLevelRule(parser)
        ParseTreeWalker.DEFAULT.walk(listener, data)

        parser.removeParseListeners()
        parser.removeErrorListeners()

        val pt: ParseTree = listener.parseTree ?: throw Exception("Failed to parse file")

        val matcher = QueryMatcher(logger)
        val queryMatchTreeRoot = matcher.findMatchesAsTree(pt.root, languageSpecification.getPatterns())

        val graph = QueryMatchTreeWalker.walk(pt.root, queryMatchTreeRoot, languageSpecification.defaultPackageName, filePath, AntlrGraphBuilder(projectPath = filePath))

        if(languageSpecification.baseIdentifierName != null) {
            val validationPattern = QueryParser.parse("""
                patterns:
                  - pattern:
                      rule: simpleIdentifier
                    converters:
                      - function: validate-identifier
                        identifier: "@simpleIdentifier"
            """.trimIndent())
            QueryMatchTreeWalker.walk(pt.root, matcher.findMatchesAsTree(pt.root, validationPattern), languageSpecification.defaultPackageName, filePath, graph)
        }

        graph.nodes.values.filterIsInstance<DanglingNodeBuilder>().forEach { node ->
            node.importStatements = graph.fileImports
        }

        return graph
    }

    /**
     * Utility function to print the JSON representation of an ANTLR [ParseTree] to a file.
     * Useful for debugging the parse tree structure.
     *
     * @param pt The [ParseTree] to serialize and print.
     * @param pathToFile The path to the output file. Defaults to ".\\output\\output.json".
     */
    private fun printTreeToFile(pt: ParseTree, pathToFile: String = ".\\output\\output.json") {
        val content: String = pt.toJson()
        Files.write(
            Paths.get(pathToFile),
            content.toByteArray(),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        )
    }
}