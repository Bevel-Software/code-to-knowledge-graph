package software.bevel.code_to_knowledge_graph.plain

//DEPRECATED
/*class AngularJsParser(
    val angularHtmlParser: AngularHtmlParser,
    val angularJavaScriptParser: ConverterBasedAntlrParser<JavaScriptParser, JavaScriptLexer>
): Parser {

    fun shouldParseFile(fileName: String): Boolean {
        return ( fileName.endsWith(".js") || fileName.endsWith(".html")) && !fileName.contains("/node_modules/") && !fileName.contains("\\node_modules\\")
                && !fileName.startsWith("node_modules/") && !fileName.startsWith("node_modules\\")
    }

    private fun fileWalker(directory: String): List<String> {
        return Files.walk(Paths.get(directory))
            .filter { Files.isRegularFile(it) }
            .map { it.toString() }
            .filter { (it.endsWith(".js") || it.endsWith(".html")) && !it.contains("node_modules") }
            .toList()
    }

    override fun parseToGraphBuilder(pathsToProjects: List<String>): GraphBuilder {
        val files = pathsToProjects.flatMap { fileWalker(it) }
        val mergedGraph = parseFiles(files)
        augmentGraph(mergedGraph)
        validateGraph(mergedGraph)
        Metrics.getMetrics(mergedGraph)
        return mergedGraph
    }

    private fun validateGraph(graph: GraphBuilder) {
        graph.connectionsBuilder.getAllConnections().forEach { connection ->
            if (!graph.nodes.containsKey(connection.sourceNodeName) || !graph.nodes.containsKey(connection.targetNodeName)) {
                BevelLogger.logger.error("Connection: $connection with sourceNode: ${connection.sourceNodeName} and targetNode: ${connection.targetNodeName} is invalid")
                if (graph.nodes.containsKey(connection.sourceNodeName)) {
                    BevelLogger.logger.error("SourceNode: ${graph.nodes[connection.sourceNodeName]}")
                }
                if (graph.nodes.containsKey(connection.targetNodeName)) {
                    BevelLogger.logger.error("TargetNode: ${graph.nodes[connection.targetNodeName]}")
                }
            }
        }
        graph.nodes.values.filterIsInstance<DanglingNodeBuilder>().forEach {
            BevelLogger.logger.error("DanglingNodeBuilder ${it.id} could not be qualified")
        }
    }

    private fun augmentGraph(graph: GraphBuilder) {
        GraphAugmenter.mergeModuleNodes(graph)
        GraphAugmenter.addAngularJsDefaultConstructor(graph)
        GraphAugmenter.fullyQualifyNodeNames(graph, angularJavaScriptParser.languageSpecification::findFullyQualifiedNodeInImports)
        GraphAugmenter.forceQualify(graph)
        GraphAugmenter.inferModuleConnections(graph)
    }

    private fun parseFiles(files: List<String>): GraphBuilder {
        return files.mapNotNull { file ->
            BevelLogger.logger.info("Parsing: $file")
            try {
                if(file.endsWith(".js"))
                    angularJavaScriptParser.parseFile(file)
                else if(file.endsWith(".html"))
                    angularHtmlParser.parseString(File(file).readText(), file, 0)
                else
                    null
            } catch (e: Exception) {
                BevelLogger.logger.error("Failed to parse file: $file: ${e.message}")
                null
            }
        }.fold(GraphBuilder(mutableMapOf())) { acc, graph ->
            acc.addAll(graph)
            acc
        }
    }

}*/