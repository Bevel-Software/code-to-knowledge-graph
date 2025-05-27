package software.bevel.code_to_knowledge_graph.plain

//DEPRECATED
/*class AngularHtmlParser(private val javaScriptParser: IntermediateStringParser): IntermediateStringParser {

    override fun parseString(text: String, filePath: String, startIndex: Int): GraphBuilder {
        val graph = GraphBuilder(mutableMapOf())

        val packageNode = FullyQualifiedNodeBuilder("'$filePath'", NodeType.Package)
        graph.nodes[packageNode.id] = packageNode

        if(filePath.endsWith(".html")) {
            val fileName: String
            if(File(filePath.replace(".html", ".scss")).exists()) {
                fileName = filePath.replace(".html", ".scss")
            } else if(File(filePath.replace(".html", ".css")).exists()) {
                fileName = filePath.replace(".html", ".css")
            } else {
                fileName = ""
            }
            if(fileName.isNotEmpty()) {
                val cssName = FullyQualifiedNodeBuilder("'${fileName}'", NodeType.Package)
                graph.nodes[cssName.id] = cssName
                graph.addConnectionAndMissingNodes(
                    Connection(
                        packageNode.id,
                        cssName.id,
                        ConnectionType.USES,
                        filePath,
                        0,
                        0
                    ),
                    targetNodeFallback = { FullyQualifiedNodeBuilder("'${fileName}'", NodeType.Package) }
                )
            }
        }

        val angularMarkups = mutableListOf<Pair<Int, String>>()
        var counter = 0
        val sb = StringBuilder()
        text.forEachIndexed { index, c ->
            when (c) {
                '{' -> {
                    if(sb.length == 1 && sb[0] == '{')
                        counter++
                    sb.append(c)
                }
                '}' -> {
                    sb.append(c)
                    if(sb.isNotEmpty() && sb[sb.length - 2] == '}') {
                        counter--
                        if (counter == 0) {
                            val str = sb.toString()
                            angularMarkups.add(index - str.length to str.substring(2, str.length - 2))
                            sb.clear()
                        }
                        if(counter < 0)
                            counter = 0
                    }
                }
                else -> {
                    if(counter != 0)
                        sb.append(c)
                    else
                        sb.clear()
                }
            }
        }

        angularMarkups.forEach {
            try {
                graph.addAll(javaScriptParser.parseString(it.second, filePath, startIndex + it.first))
            } catch (e: Exception) {
                BevelLogger.logger.error("Error parsing angular markup in HTML", e)
            }
        }

        val directiveRegex = Regex("""ng(-|:\s|_)[a-zA-Z${'$'}_][\w${'$'}]*\s*=\s*"""")
        val directives = directiveRegex.findAll(text)
        directives.forEach { directive ->
            val substr = text.substring(directive.range.last + 1)
            val name = substr.takeWhile { it != '"' }
            if (name.startsWith("{{") && name.endsWith("}}"))
                return@forEach
            val possibleFilePath = name.trim('\'', '"', '`')
            if(possibleFilePath.endsWith(".html") || possibleFilePath.endsWith(".js")) {
                val htmlModuleAbsolutePath: String
                if(Path(possibleFilePath).isAbsolute) {
                    htmlModuleAbsolutePath = possibleFilePath
                } else if (possibleFilePath.startsWith(".")) {
                    htmlModuleAbsolutePath = Path(filePath).parent.resolve(possibleFilePath).normalize().toString()
                } else {
                    var htmlModuleParent = Path(possibleFilePath).parent
                    while (htmlModuleParent.pathString.isNotEmpty() && !filePath.contains(htmlModuleParent.pathString)) {
                        htmlModuleParent = htmlModuleParent.parent
                    }
                    var modulePathParent = Path(filePath).parent
                    while (!modulePathParent.endsWith(htmlModuleParent)) {
                        modulePathParent = modulePathParent.parent
                    }
                    htmlModuleAbsolutePath =
                        Path(modulePathParent.pathString.replace(htmlModuleParent.pathString, ""))
                            .resolve(possibleFilePath).normalize().toString()
                }
                val htmlModuleName = "'${htmlModuleAbsolutePath}'"
                if(htmlModuleAbsolutePath.endsWith(".js")) {
                    graph.addAll(javaScriptParser.parseString(File(htmlModuleAbsolutePath).readText(), htmlModuleAbsolutePath, 0))
                } else {
                    graph.addAll(parseString(File(htmlModuleAbsolutePath).readText(), htmlModuleAbsolutePath, 0))
                }
                graph.addConnectionAndMissingNodes(
                    Connection(
                        packageNode.id,
                        htmlModuleName,
                        ConnectionType.USES,
                        filePath,
                        startIndex + directive.range.last + 1,
                        startIndex + directive.range.last + 1 + name.length
                    )
                )
            } else {
                graph.addAll(javaScriptParser.parseString(name, filePath, startIndex + directive.range.last + 1))
            }
        }

        val tagRegex = Regex("""<[a-zA-Z_${'$'}\-:][\w${'$'}\-:_]*(\s|>)""")
        val tags = tagRegex.findAll(text)
        tags.forEach { tag ->
            val actualTag = tag.value.substring(1, tag.value.length - 1).lowercase(Locale.getDefault())
                .replace(Regex("-[a-z]")) { m -> m.value[1].uppercaseChar().toString() }
                .replace(Regex(":[a-z]")) { m -> m.value[1].uppercaseChar().toString() }
                .replace(Regex("_[a-z]")) { m -> m.value[1].uppercaseChar().toString() }
            graph.addConnectionAndMissingNodes(
                Connection(
                    packageNode.id,
                    actualTag,
                    ConnectionType.USES,
                    filePath,
                    startIndex + tag.range.first,
                    startIndex + tag.range.last
                ),
                packageNode
            )
        }
        return graph
    }
}*/