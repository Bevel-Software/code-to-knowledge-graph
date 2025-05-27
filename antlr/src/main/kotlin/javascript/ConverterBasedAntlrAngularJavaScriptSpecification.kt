package software.bevel.code_to_knowledge_graph.antlr.javascript

/*class ConverterBasedAntlrAngularJavaScriptSpecification : ConverterBasedAntlrLanguageSpecification<JavaScriptParser, JavaScriptLexer> {
    private var packageName: String = ""
    private val fileEnding = ".js"

    override val defaultPackageName: String
        get() = packageName

    override fun shouldParseFile(fileName: String): Boolean {
        return fileName.endsWith(fileEnding) && !fileName.contains("/node_modules/") && !fileName.contains("\\node_modules\\")
                && !fileName.startsWith("node_modules/") && !fileName.startsWith("node_modules\\")
    }

    override fun getParser(input: TokenStream): JavaScriptParser = JavaScriptParser(input)

    override fun getLexer(input: CharStream): JavaScriptLexer = JavaScriptLexer(input)


    override fun runTopLevelRule(parser: JavaScriptParser): ParserRuleContext = parser.program()

    private fun addDefinedByAndParentConnections(
        startIndex: Int,
        graph: GraphBuilder,
        node: NodeBuilder,
        pathToFile: String,
        definedBy: Pair<NodeBuilder, ParseTreeRule>,
        parent: Pair<String, ParseTreeRule>? = null
    ) {
        graph.addConnectionAndMissingNodes(
            Connection(
                definedBy.first.id,
                node.id,
                ConnectionType.DEFINES,
                pathToFile,
                definedBy.second.sidx + startIndex,
                definedBy.second.eidx + startIndex
            ), definedBy.first
        )
        if (parent != null) {
            graph.addConnectionAndMissingNodes(
                Connection(
                    parent.first,
                    node.id,
                    ConnectionType.PARENT_OF,
                    pathToFile,
                    parent.second.sidx + startIndex,
                    parent.second.eidx + startIndex
                ), definedBy.first
            )
        }
    }

    override fun declareConverters(pathToFile: String, fileImports: MutableList<ImportStatement>, startIndex: Int): List<AntlrTreeWalker.TreeToGraphConverter> {
        packageName = "'" + Paths.get(pathToFile).toString() + "'"
        val converters = listOf(
            // Converter for function declarations
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("functionDeclaration", "identifier")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Function must have a definedBy")
                val functionName = matchedNodes.last().sourceCode
                val functionNode = FullyQualifiedNodeBuilder(
                    name = definedBy.id + "." + functionName,
                    nodeType = NodeType.Function
                )
                graph.nodes[functionNode.id] = functionNode
                addDefinedByAndParentConnections(startIndex,
                    graph,
                    functionNode,
                    pathToFile,
                    definedBy to matchedNodes.first()
                )
                functionNode
            },

            // Converter for class declarations
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("classDeclaration", "identifier")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Class must have a definedBy")
                val className = matchedNodes.last().sourceCode
                val classNode = FullyQualifiedNodeBuilder(
                    name = definedBy.id + "." + className,
                    nodeType = NodeType.Class
                )
                graph.nodes[classNode.id] = classNode
                addDefinedByAndParentConnections(startIndex,
                    graph,
                    classNode,
                    pathToFile,
                    definedBy to matchedNodes.first()
                )
                // Handle inheritance
                val classTailNode = matchedNodes.first().children.find { it.rule == "classTail" }
                val extendsNode = classTailNode?.children?.find { it.rule == "Extends" }
                if (extendsNode != null) {
                    val extendsIndex = classTailNode.children.indexOf(extendsNode)
                    if (extendsIndex + 1 < classTailNode.children.size) {
                        val parentClassNode = classTailNode.children[extendsIndex + 1]
                        val parentClassName = parentClassNode.sourceCode
                        graph.addConnectionAndMissingNodes(
                            Connection(
                                classNode.id,
                                parentClassName,
                                ConnectionType.INHERITS_FROM,
                                pathToFile,
                                classTailNode.sidx + startIndex,
                                classTailNode.eidx + startIndex
                            ),
                            targetNodeFallback = { DanglingNodeBuilder(parentClassName, pathToFile, nodeType = NodeType.Class, context = classNode) }
                        )
                    }
                }
                classNode
            },

            // Converter for variable declarations
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("variableDeclaration", "assignable", "identifier")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Variable declaration must have a definedBy")
                val variableName = matchedNodes.last().sourceCode
                val variableNode = FullyQualifiedNodeBuilder(
                    name = definedBy.id + "." + variableName,
                    nodeType = NodeType.Property
                )
                graph.nodes[variableNode.id] = variableNode
                addDefinedByAndParentConnections(startIndex,
                    graph,
                    variableNode,
                    pathToFile,
                    definedBy to matchedNodes.first()
                )
                variableNode
            },

            // Converter for function parameters
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("formalParameterArg", "assignable", "identifier")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Parameter must have a definedBy")
                val paramName = matchedNodes.last().sourceCode
                val paramNode = FullyQualifiedNodeBuilder(
                    name = definedBy.id + "." + paramName,
                    nodeType = NodeType.Property
                )
                graph.nodes[paramNode.id] = paramNode
                addDefinedByAndParentConnections(startIndex,
                    graph,
                    paramNode,
                    pathToFile,
                    definedBy to matchedNodes.first()
                )
                paramNode
            },

            // Converter for method definitions inside classes
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("methodDefinition", "classElementName")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Method must have a definedBy")
                val methodName = matchedNodes.last().sourceCode
                val methodNode = FullyQualifiedNodeBuilder(
                    name = definedBy.id + "." + methodName,
                    nodeType = NodeType.Function
                )
                graph.nodes[methodNode.id] = methodNode
                addDefinedByAndParentConnections(startIndex,
                    graph,
                    methodNode,
                    pathToFile,
                    definedBy to matchedNodes.first()
                )
                methodNode
            },

            AntlrTreeWalker.TreeToGraphConverter(
                listOf("fieldDefinition", "classElementName")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Property must have a definedBy")
                val methodName = matchedNodes.last().sourceCode
                val methodNode = FullyQualifiedNodeBuilder(
                    name = definedBy.id + "." + methodName,
                    nodeType = NodeType.Property
                )
                graph.nodes[methodNode.id] = methodNode
                addDefinedByAndParentConnections(startIndex,
                    graph,
                    methodNode,
                    pathToFile,
                    definedBy to matchedNodes.first()
                )
                methodNode
            },

            // Converter for import statements
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("importStatement", "importFromBlock", "importDefault", "aliasName")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Method must have a definedBy")
                val importPath = getImportFromProperty(matchedNodes[1], pathToFile) ?: throw Exception("Import path is null")
                addObjectImport(startIndex, graph, fileImports, pathToFile, definedBy, matchedNodes.first(), matchedNodes.last().sourceCode, importPath, "<exporter>.<default>")
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("importStatement", "importFromBlock", "importNamespace")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Method must have a definedBy")
                val importPath = getImportFromProperty(matchedNodes[1], pathToFile) ?: throw Exception("Import path is null")

                val alias = if(matchedNodes.last().children.find { it.rule == "As" } != null) {
                    matchedNodes.last().children.findLast { it.rule == "identifierName" }!!.sourceCode
                } else {
                    null
                }
                val identifiers = matchedNodes.last().children.filter { it.rule == "identifierName" }
                if(alias == null && identifiers.size == 1) {
                    addObjectImport(startIndex, graph, fileImports, pathToFile, definedBy, matchedNodes.first(), identifiers.first().sourceCode, importPath, "<exporter>.<default>")
                } else if(alias != null && identifiers.size == 1) {
                    addStarImport(startIndex, graph, fileImports, pathToFile, definedBy, matchedNodes.first(), alias, importPath)
                } else {
                    BevelLogger.logger.error("Unknown import statement: ${matchedNodes[0].sourceCode}")
                }
                null
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("importStatement", "importFromBlock", "importModuleItems", "importAliasName", "moduleExportName")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Method must have a definedBy")
                val importPath = getImportFromProperty(matchedNodes[1], pathToFile) ?: throw Exception("Import path is null")
                val alias = matchedNodes[3].children.findLast { it.rule == "importedBinding" }?.sourceCode
                if(alias == null){
                    fileImports.add(ImportStatement(importPath, mapOf("importedEntityName" to matchedNodes.last().sourceCode)))
                }
                else {
                    addObjectImport(startIndex,
                        graph,
                        fileImports,
                        pathToFile,
                        definedBy,
                        matchedNodes.first(),
                        alias,
                        importPath,
                        matchedNodes.last().sourceCode
                    )
                }
                null
            },

            AntlrTreeWalker.TreeToGraphConverter(
                listOf("singleExpression", "arguments", "argument", "singleExpression", "objectLiteral", "propertyAssignment", "propertyName", "identifierName")
            ) { definedBy, matchedNodes, graph ->
                if(definedBy == null) throw Exception("Property must have a definedBy")
                if(matchedNodes[0].children[0].sourceCode.endsWith(".component") || matchedNodes[0].children[0].sourceCode.endsWith(".state")) {
                    if(matchedNodes[7].sourceCode == "template") {
                        AngularHtmlParser(ConverterBasedAntlrParser(this))
                            .parseString(
                                matchedNodes[5].children[2].sourceCode.trim('\'', '"', '`'),
                                pathToFile,
                                matchedNodes[5].children[2].sidx + startIndex
                            )
                    } else if (matchedNodes[7].sourceCode == "templateUrl") {
                        val moduleName = findParentModuleNode(definedBy, graph)?.id ?: "'${pathToFile}'"
                        val htmlModuleAbsolutePath: String

                        val htmlModulePath = matchedNodes[5].children[2].sourceCode.trim('\'', '"', '`')
                        if(Path(htmlModulePath).isAbsolute) {
                            htmlModuleAbsolutePath = htmlModulePath
                        } else if(htmlModulePath.startsWith(".")) {
                            htmlModuleAbsolutePath = Path(pathToFile).parent.resolve(htmlModulePath).normalize().toString()
                        } else {
                            var htmlModuleParent = Path(htmlModulePath).parent
                            while (htmlModuleParent.pathString.isNotEmpty() && !pathToFile.contains(htmlModuleParent.pathString)) {
                                htmlModuleParent = htmlModuleParent.parent
                            }
                            var modulePathParent = Path(pathToFile).parent
                            while (!modulePathParent.endsWith(htmlModuleParent)) {
                                modulePathParent = modulePathParent.parent
                            }
                            htmlModuleAbsolutePath =
                                Path(modulePathParent.pathString.replace(htmlModuleParent.pathString, ""))
                                    .resolve(htmlModulePath).normalize().toString()
                        }
                        val htmlModuleName = "'${htmlModuleAbsolutePath}'"

                        graph.addConnectionAndMissingNodes(
                            Connection(
                                moduleName,
                                htmlModuleName,
                                ConnectionType.USES,
                                pathToFile,
                                matchedNodes.first().sidx + startIndex,
                                matchedNodes.first().eidx + startIndex
                            ),
                            targetNodeFallback = { FullyQualifiedNodeBuilder(htmlModuleName, NodeType.Package)}
                        )
                        AngularHtmlParser(ConverterBasedAntlrParser(this))
                            .parseString(
                                File(htmlModuleAbsolutePath).readText(),
                                htmlModuleAbsolutePath,
                                0
                            )
                    }
                    return@TreeToGraphConverter null
                }
                null
            },

            // Converter for function calls
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("singleExpression", "arguments")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Function call must have a definedBy")

                if(matchedNodes[0].children[0].sourceCode == "require") {
                    handleRequireCall(startIndex, matchedNodes, graph, definedBy, fileImports, pathToFile)
                    return@TreeToGraphConverter null
                }
                val functionNameNode = if(matchedNodes[0].children.any { it.rule == "New" }) {
                    matchedNodes[0]
                } else {
                    matchedNodes[0].children.find { it.rule == "singleExpression" || it.rule == "identifier" }
                        ?: throw Exception("Function call must have a function name")
                }

                val functionName = fixChainCallVariableName(functionNameNode, graph, definedBy)
                /*if (matchedNodes[0].children.find { it.rule == "New" } != null) {
                    functionName += ".constructor"
                }*/

                if(functionNameNode.sourceCode == "angular.module") {
                    if(matchedNodes[1].children.count { it.rule == "argument" } >= 2) {
                        return@TreeToGraphConverter handleAngularModule(startIndex, matchedNodes, graph, definedBy, fileImports, pathToFile)
                    }
                    if(matchedNodes[1].children.count { it.rule == "argument" } == 1) {
                        val currentModule = findParentModuleNode(definedBy, graph)?.id ?: "'${pathToFile}'"
                        val argumentModule = "'${matchedNodes[1].children[1].sourceCode.trim('\'', '"', '`')}'"

                        graph.addConnectionAndMissingNodes(
                            Connection(
                                currentModule,
                                argumentModule,
                                ConnectionType.USES,
                                pathToFile,
                                matchedNodes.first().sidx + startIndex,
                                matchedNodes.first().eidx + startIndex
                            ),
                            targetNodeFallback = { FullyQualifiedNodeBuilder(argumentModule, NodeType.Package) }
                        )
                        return@TreeToGraphConverter graph.nodes[argumentModule]
                    }
                }

                if(functionNameNode.sourceCode.length < 160) {
                    graph.addConnectionAndMissingNodes(
                        Connection(
                            definedBy.id,
                            functionName,
                            ConnectionType.USES,
                            pathToFile,
                            matchedNodes.first().sidx + startIndex,
                            matchedNodes.first().eidx + startIndex
                        ),
                        targetNodeFallback = { DanglingNodeBuilder(functionName, pathToFile, nodeType = NodeType.Function, context = definedBy) }
                    )
                }
                graph.nodes.values.find { it.id == functionName || it.id == DanglingNodeBuilder(functionName, pathToFile, nodeType = NodeType.Function, context = definedBy).id }
            },

            // Converter for property declaration
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("singleExpression", "singleExpression", "*", "This")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Member access must have a definedBy")
                if (matchedNodes[0].children.find { it.rule == "Dot" } == null || matchedNodes[0].children[0].sourceCode != "this")
                    return@TreeToGraphConverter null
                if(!definedBy.id.endsWith("constructor"))
                    return@TreeToGraphConverter null
                val memberName = (matchedNodes[0].children.findLast { it.rule == "Hashtag" }?.sourceCode ?: "") +
                        matchedNodes[0].children.findLast { it.rule == "identifierName" }!!.sourceCode

                val actualParent = findParentClassOrModuleNode(definedBy, graph) ?: definedBy
                if(graph.nodes[actualParent.id + "." + memberName] != null)
                    return@TreeToGraphConverter null
                val propertyNode = FullyQualifiedNodeBuilder(
                    name = actualParent.id + "." + memberName,
                    nodeType = NodeType.Property,
                )
                graph.nodes[propertyNode.id] = propertyNode
                addDefinedByAndParentConnections(startIndex,
                    graph,
                    propertyNode,
                    pathToFile,
                    actualParent to matchedNodes.first()
                )
                null
            },

            // Converter for variable usages
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("singleExpression", "identifier")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Variable usage must have a definedBy")
                if(matchedNodes[0].parent.children.find { it.rule == "arguments" || it.rule == "Extends" } != null) return@TreeToGraphConverter null
                var variableName = matchedNodes[1].sourceCode
                if(variableName == "module" || variableName == "exports"){
                    if(variableName == "module") {
                        variableName = "'$pathToFile'.module"
                    } else {
                        variableName = "'$pathToFile'.module.exports"
                    }
                    val node = FullyQualifiedNodeBuilder(variableName, NodeType.Property)
                    graph.nodes[node.id] = node
                }
                val nodeType = if(variableName.first().isUpperCase()) NodeType.Class else NodeType.Property
                graph.addConnectionAndMissingNodes(
                    Connection(
                        definedBy.id,
                        variableName,
                        ConnectionType.USES,
                        pathToFile,
                        matchedNodes[0].sidx + startIndex,
                        matchedNodes[0].eidx + startIndex
                    ),
                    targetNodeFallback = { DanglingNodeBuilder(variableName, pathToFile, nodeType = nodeType, context = definedBy) }
                )
                graph.nodes.values.find { it.id == variableName || it.id == DanglingNodeBuilder(variableName, pathToFile, nodeType = nodeType, context = definedBy).id }
            },

            AntlrTreeWalker.TreeToGraphConverter(
                listOf("singleExpression", "identifierName")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Variable usage must have a definedBy")
                val callerNode = if(matchedNodes[0].children[0].children.find { it.rule == "arguments" || it.rule == "OpenBracket" } != null) {
                    matchedNodes[0].children[0].children.find { it.rule == "singleExpression" } ?: matchedNodes[0].children[0]
                } else {
                    matchedNodes[0].children[0]
                }

                val variableName = fixChainCallVariableName(matchedNodes[0], graph, definedBy)
                val callerName = fixChainCallVariableName(callerNode, graph, definedBy)

                val positionNode = if(matchedNodes[0].parent.children.find { it.rule == "arguments" } != null){
                    matchedNodes[0].parent
                } else {
                    matchedNodes[0]
                }
                val variableNameNodeType = if(matchedNodes[0].parent.children.find { it.rule == "arguments" } != null){
                    NodeType.Function
                } else {
                    NodeType.Property
                }
                val callerNameNodeType = if(matchedNodes[0].children[0].children.find { it.rule == "arguments" } != null){
                    NodeType.Function
                } else if(callerName.endsWith(".constructor")) {
                    NodeType.Class
                } else {
                    NodeType.Property
                }


                graph.addConnectionAndMissingNodes(
                    Connection(
                        definedBy.id,
                        variableName,
                        ConnectionType.USES,
                        pathToFile,
                        positionNode.sidx + startIndex,
                        positionNode.eidx + startIndex
                    ),
                    targetNodeFallback = { DanglingNodeBuilder(variableName, pathToFile, nodeType = variableNameNodeType, context = definedBy) }
                )
                graph.addConnectionAndMissingNodes(
                    Connection(
                        variableName,
                        callerName,
                        ConnectionType.INVOKED_BY,
                        pathToFile,
                        matchedNodes[0].sidx + startIndex,
                        matchedNodes[0].eidx + startIndex
                    ),
                    sourceNodeFallback = { DanglingNodeBuilder(variableName, pathToFile, nodeType = variableNameNodeType, context = definedBy) },
                    targetNodeFallback = { DanglingNodeBuilder(callerName, pathToFile, nodeType = callerNameNodeType, context = definedBy) }
                )
                null
            },

            // Converter for function arguments
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("arguments", "argument", "identifier")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Argument usage must have a definedBy")
                val argumentName = matchedNodes.last().sourceCode
                graph.addConnectionAndMissingNodes(
                    Connection(
                        definedBy.id,
                        argumentName,
                        ConnectionType.USES,
                        pathToFile,
                        matchedNodes.first().sidx + startIndex,
                        matchedNodes.first().eidx + startIndex
                    ),
                    targetNodeFallback = { DanglingNodeBuilder(argumentName, pathToFile, nodeType = NodeType.Property, context = definedBy) }
                )
                null
            },

            // Converter for anonymous functions
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("anonymousFunction", "arrowFunctionBody")
            ) { defBy, matchedNodes, graph ->
                if (defBy == null) throw Exception("Arrow function must have a definedBy")
                val definedBy = getActualDefiner(defBy, graph)
                val functionName = definedBy.id + ".<lambda" + matchedNodes[0].hashCode() + ">"
                val functionNode = FullyQualifiedNodeBuilder(
                    name = functionName,
                    nodeType = NodeType.Function
                )
                graph.nodes[functionNode.id] = functionNode
                addDefinedByAndParentConnections(startIndex,
                    graph,
                    functionNode,
                    pathToFile,
                    definedBy to matchedNodes.first()
                )
                functionNode
            },

            // Converter for template literals
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("templateStringLiteral")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Template literal must have a definedBy")
                matchedNodes[0].children.filter { it.rule == "templateStringAtom" }.forEach { atomNode ->
                    val expressionNode = atomNode.children.find { it.rule == "singleExpression" }
                    if (expressionNode != null) {
                        val variableName = expressionNode.sourceCode
                        graph.addConnectionAndMissingNodes(
                            Connection(
                                definedBy.id,
                                variableName,
                                ConnectionType.USES,
                                pathToFile,
                                expressionNode.sidx + startIndex,
                                expressionNode.eidx + startIndex
                            ),
                            targetNodeFallback = { DanglingNodeBuilder(variableName, pathToFile, nodeType = NodeType.Property, context = definedBy) }
                        )
                    }
                }
                null
            },

            // Converter for destructuring assignments (object)
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("variableDeclaration", "assignable", "objectLiteral")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Destructuring assignment must have a definedBy")
                val objectLiteralNode = matchedNodes.last()
                objectLiteralNode.children.filter { it.rule == "propertyAssignment" }.forEach { propertyNode ->
                    val propertyNameNode = propertyNode.children.find { it.rule == "propertyName" }
                    if (propertyNameNode != null) {
                        val variableName = propertyNameNode.sourceCode
                        val variableNode = FullyQualifiedNodeBuilder(
                            name = definedBy.id + "." + variableName,
                            nodeType = NodeType.Property
                        )
                        graph.nodes[variableNode.id] = variableNode
                        addDefinedByAndParentConnections(startIndex,
                            graph,
                            variableNode,
                            pathToFile,
                            definedBy to matchedNodes.first()
                        )
                    }
                }
                null
            },

            AntlrTreeWalker.TreeToGraphConverter(
                listOf("exportStatement", "Export")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Export statement must have a definedBy")
                val node = FullyQualifiedNodeBuilder(
                    name = definedBy.id + ".<exporter>",
                    nodeType = NodeType.Alias
                )
                graph.nodes[node.id] = node
                addDefinedByAndParentConnections(startIndex,
                    graph,
                    node,
                    pathToFile,
                    definedBy to matchedNodes.first()
                )
                node
            },

            AntlrTreeWalker.TreeToGraphConverter(
                listOf("exportStatement", "Default")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Export statement must have a definedBy")
                val node = FullyQualifiedNodeBuilder(
                    name = definedBy.id + ".<default>",
                    nodeType = NodeType.Alias
                )
                graph.nodes[node.id] = node
                addDefinedByAndParentConnections(startIndex,
                    graph,
                    node,
                    pathToFile,
                    definedBy to matchedNodes.first()
                )
                node
            },
        )
        return converters
    }

    private fun handleAngularModule(startIndex: Int, matchedNodes: List<ParseTreeRule>, graph: GraphBuilder, definedBy: NodeBuilder, fileImports: MutableList<ImportStatement>, pathToFile: String): FullyQualifiedNodeBuilder {
        val moduleName = "'${matchedNodes[1].children[1].sourceCode.trim('\'', '"', '`')}'"
        val moduleNode = FullyQualifiedNodeBuilder(
            name = moduleName,
            nodeType = NodeType.Package
        )
        graph.nodes[moduleNode.id] = moduleNode
        graph.addConnectionAndMissingNodes(
            Connection(
                moduleNode.id,
                definedBy.id,
                ConnectionType.IS_OF_TYPE,
                pathToFile,
                matchedNodes.first().sidx + startIndex,
                matchedNodes.first().eidx + startIndex
            ),
        )
        val dependencies = matchedNodes[1].children[3].children[0].children[0].children[1].children.filter { it.rule == "arrayElement" }
            .filter { it.sourceCode.startsWith("'") || it.sourceCode.startsWith("`") || it.sourceCode.startsWith("\"") }
            .map { "'${it.sourceCode.trim('\'', '"', '`')}'" }
        for (dependency in dependencies) {
            graph.addConnectionAndMissingNodes(
                Connection(
                    moduleNode.id,
                    dependency,
                    ConnectionType.USES,
                    pathToFile,
                    matchedNodes.first().sidx + startIndex,
                    matchedNodes.first().eidx + startIndex
                ),
                targetNodeFallback = { FullyQualifiedNodeBuilder(dependency, nodeType = NodeType.Package) }
            )
        }
        return moduleNode
    }

    private fun handleRequireCall(startIndex: Int, matches: List<ParseTreeRule>, graph: GraphBuilder, definedBy: NodeBuilder, fileImports: MutableList<ImportStatement>, pathToFile: String) {
        val relativeImportPath = matches[0].children[1].children[1].sourceCode.trim('\'', '"', '`')
        val importPath = if(relativeImportPath.startsWith(".")) {
            "'${Path(pathToFile).parent.resolve(relativeImportPath).normalize()}'"
        } else {
            "'${relativeImportPath}'"
        }
        val aliasAssignable = matches[0].parent.children[0]
        if(aliasAssignable.children[0].rule == "identifier") {
            val propertyName = aliasAssignable.children[0].sourceCode
            fileImports.add(ImportStatement(importPath, mapOf("importedEntityName" to propertyName)))
            addSimpleConnectionToModuleExport(startIndex,
                graph,
                propertyName,
                importPath,
                pathToFile,
                matches[0].parent,
            )
        } else {
            val properties = searchInParseTreeRecursively(aliasAssignable, { it.rule == "propertyAssignment" })
            for (property in properties) {
                if(property.children[0].rule == "singleExpression") {
                    val propertyName = property.children[0].sourceCode
                    fileImports.add(ImportStatement(importPath, mapOf("importedEntityName" to propertyName)))
                    addSimpleConnectionToModuleExport(startIndex,
                        graph,
                        propertyName,
                        importPath,
                        pathToFile,
                        matches[0].parent,
                    )
                } else if(property.children[0].rule == "propertyName" && property.children[2].rule == "singleExpression") {
                    val propertyName = property.children[0].sourceCode
                    val alias = property.children[2].sourceCode
                    addObjectImport(startIndex,
                        graph,
                        fileImports,
                        pathToFile,
                        definedBy,
                        matches[0].parent,
                        alias,
                        importPath,
                        propertyName
                    )
                    addSimpleConnectionToModuleExport(startIndex,
                        graph,
                        propertyName,
                        importPath,
                        pathToFile,
                        matches[0].parent,
                    )
                }
            }
        }
    }

    private fun addSimpleConnectionToModuleExport(startIndex: Int, graph: GraphBuilder, propertyName: String, importPath: String, pathToFile: String, location: ParseTreeRule) {
        graph.addConnectionAndMissingNodes(Connection(
            propertyName,
            "$importPath.module.exports",
            ConnectionType.USES,
            pathToFile,
            location.sidx + startIndex,
            location.eidx + startIndex,
        ),
        targetNodeFallback = { FullyQualifiedNodeBuilder("$importPath.module.exports", NodeType.Property) })
        /*graph.addConnectionAndMissingNodes(Connection(
            propertyName,
            "$importPath.exports",
            ConnectionType.USES,
            pathToFile,
            location.sidx + startIndex,
            location.eidx + startIndex,
        ),
        targetNodeFallback = { FullyQualifiedNodeBuilder("$importPath.exports", NodeType.PROPERTY) })*/
    }

    private fun getNodeType(node: NodeBuilder): NodeType? {
        when (node) {
            is DanglingNodeBuilder -> return node.nodeType
            is FullyQualifiedNodeBuilder -> return node.nodeType
        }
        return null
    }

    private fun getActualDefiner(definedBy: NodeBuilder, graph: GraphBuilder): NodeBuilder {
        var currentNode = definedBy
        while (graph.connectionsBuilder.findConnectionsToOfType(currentNode.id, ConnectionType.DEFINES).isEmpty() || getNodeType(currentNode) == NodeType.Property) {
            currentNode = graph.getDefiner(currentNode.id) ?: graph.getInvoker(currentNode.id) ?: break
        }
        return currentNode
    }

    private fun fixChainCallVariableName(node: ParseTreeRule, graph: GraphBuilder, definedBy: NodeBuilder): String {
        var variableName = if(node.sourceCode.startsWith("this.") || node.sourceCode == "this") {
            node.sourceCode.replaceFirst ("this", (findParentClassOrModuleNode(definedBy, graph) ?: definedBy).id)
        } else if(node.sourceCode.startsWith("${'$'}scope.") || node.sourceCode == "${'$'}scope") {
            node.sourceCode.replaceFirst ("${'$'}scope", (findParentClassOrModuleNode(definedBy, graph) ?: definedBy).id)
        } else {
            node.sourceCode
        }
        val newRule = searchInParseTreeRecursively(node, { it.rule == "New" }).firstOrNull()
        if(newRule != null && variableName.startsWith(newRule.parent.sourceCode)) {
            val constructorName =
                newRule.parent.children.find { it.rule == "singleExpression" || it.rule == "identifier" }?.sourceCode
            variableName = variableName.replaceFirst(newRule.parent.sourceCode, constructorName ?: newRule.parent.sourceCode.replaceFirst("new", ""))
        }
        /*val arguments = searchInParseTreeRecursively(node, { it.rule == "arguments" }).sortedByDescending { it.label.length }
        arguments.forEach {
            variableName = variableName.replace(it.label, "");
        }*/
        return variableName
    }

    private fun searchInParseTreeRecursively(startNode: ParseTreeRule, predicate: (ParseTreeRule) -> Boolean): List<ParseTreeRule> {
        val results = mutableListOf<ParseTreeRule>()
        if (predicate(startNode)) {
            results.add(startNode)
        }
        for (child in startNode.children) {
            results.addAll(searchInParseTreeRecursively(child, predicate))

        }
        return results
    }

    private fun findParentModuleNode(node: NodeBuilder, graph: GraphBuilder): NodeBuilder? {
        var currentNode: NodeBuilder? = node
        while (currentNode != null) {
            if ((currentNode is FullyQualifiedNodeBuilder && currentNode.nodeType == NodeType.Package)
                || (currentNode is DanglingNodeBuilder && currentNode.nodeType == NodeType.Package)
            ) {
                var connections = graph.connectionsBuilder.findConnectionsFromOfType(currentNode.id, ConnectionType.IS_OF_TYPE)
                while(connections.isNotEmpty()) {
                    currentNode = graph.nodes[connections.first().targetNodeName] ?: return currentNode
                    connections = graph.connectionsBuilder.findConnectionsFromOfType(currentNode.id, ConnectionType.IS_OF_TYPE)
                }
                return currentNode
            }
            currentNode = graph.getParent(currentNode.id)
        }
        return null
    }

    private fun findParentClassOrModuleNode(node: NodeBuilder, graph: GraphBuilder): NodeBuilder? {
        var currentNode: NodeBuilder? = node
        while (currentNode != null) {
            if ((currentNode is FullyQualifiedNodeBuilder && (currentNode.nodeType == NodeType.Class || currentNode.nodeType == NodeType.Package))
                || (currentNode is DanglingNodeBuilder && (currentNode.nodeType == NodeType.Class || currentNode.nodeType == NodeType.Package))
            ) {
                return currentNode
            }
            currentNode = graph.getParent(currentNode.id)
        }
        return null
    }

    private fun addObjectImport(startIndex: Int, graph: GraphBuilder, fileImports: MutableList<ImportStatement>, pathToFile: String, currentModule: NodeBuilder, location: ParseTreeRule, alias: String, importedModule: String, importedObjectName: String): FullyQualifiedNodeBuilder {
        val aliasNode = FullyQualifiedNodeBuilder(
            name = currentModule.id + "." + alias,
            nodeType = NodeType.Alias,
        )
        graph.nodes[aliasNode.id] = aliasNode
        fileImports.add(ImportStatement(importedModule, mapOf("importedEntityName" to importedObjectName, "alias" to alias)))
        addDefinedByAndParentConnections(startIndex,
            graph,
            aliasNode,
            pathToFile,
            currentModule to location
        )
        graph.addConnectionAndMissingNodes(Connection(
            aliasNode.id,
            "$importedModule.$importedObjectName",
            ConnectionType.USES,
            pathToFile,
            location.sidx + startIndex,
            location.eidx + startIndex,
        ),
        targetNodeFallback = { FullyQualifiedNodeBuilder("$importedModule.$importedObjectName", NodeType.Alias) })
        return aliasNode
    }

    private fun addStarImport(startIndex: Int, graph: GraphBuilder, fileImports: MutableList<ImportStatement>, pathToFile: String, currentModule: NodeBuilder, location: ParseTreeRule, alias: String, importedModule: String): FullyQualifiedNodeBuilder {
        val aliasNode = FullyQualifiedNodeBuilder(
            name = currentModule.id + "." + alias,
            nodeType = NodeType.Alias,
        )
        graph.nodes[aliasNode.id] = aliasNode
        fileImports.add(ImportStatement(importedModule, mapOf("importedEntityName" to "<exporter>", "alias" to alias)))
        addDefinedByAndParentConnections(startIndex,
            graph,
            aliasNode,
            pathToFile,
            currentModule to location
        )
        graph.addConnectionAndMissingNodes(Connection(
                aliasNode.id,
                "$importedModule.<exporter>",
                ConnectionType.USES,
                pathToFile,
                location.sidx + startIndex,
                location.eidx + startIndex,
            ),
            targetNodeFallback = { FullyQualifiedNodeBuilder("$importedModule.<exporter>", NodeType.Alias) })
        return aliasNode
    }

    private fun getImportFromProperty(importFrom: ParseTreeRule, currentFilePath: String): String?{
        val stringLiteral = importFrom.children.find { it.rule == "importFrom" }?.children?.find { it.rule == "StringLiteral" }?.sourceCode
        if(stringLiteral == null || stringLiteral.length <= 2) return null
        val literalImport = stringLiteral.trim('\'', '"')
        if(!literalImport.startsWith("."))
            return "'$literalImport'"

        val absoluteImportPath = Path(currentFilePath).parent.resolve(literalImport).normalize().toString()
        return "'$absoluteImportPath'"
    }

    override fun findFullyQualifiedNodeInImports(
        node: DanglingNodeBuilder,
        graph: GraphBuilder,
        nodesToReplace: MutableMap<String, String>
    ): FullyQualifiedNodeBuilder? {
        val nodeName = node.id
        if(nodeName == "Test2.ClassB.constructor") {
            println("Something")
        }

        for (importStatement in node.importStatements) {
            if(!importStatement.data.containsKey("importedEntityName")) {
                BevelLogger.logger.error("Unknown import statement type")
                continue
            }
            val importedEntityName = importStatement.data["importedEntityName"]!!
            val importPath = importStatement.importedPackage
            val alias = importStatement.data["alias"]

            val importedName = alias ?: importedEntityName

            if (importedName == nodeName || nodeName.startsWith("$importedName.")) {
                val potentialNodeNames = mutableListOf<String>()
                when (importedEntityName) {
                    "<default>" -> {
                        potentialNodeNames.add("${importPath}.<exporter>.<default>" + nodeName.replaceFirst(importedName, ""))
                        potentialNodeNames.add("${importPath}.<exporter>" + nodeName.replaceFirst(importedName, ""))
                        potentialNodeNames.add(importPath + nodeName.replaceFirst(importedName, ""))
                    }
                    "<exporter>" -> {
                        potentialNodeNames.add("${importPath}.<exporter>" + nodeName.replaceFirst(importedName, ""))
                        potentialNodeNames.add("${importPath}.<exporter>.<default>" + nodeName.replaceFirst(importedName, ""))
                        potentialNodeNames.add(importPath + nodeName.replaceFirst(importedName, ""))
                    }
                    else -> {
                        potentialNodeNames.add("${importPath}.<exporter>." + nodeName.replaceFirst(importedName, importedEntityName))
                        potentialNodeNames.add("${importPath}.<exporter>.<default>." + nodeName.replaceFirst(importedName, importedEntityName))
                        potentialNodeNames.add("${importPath}." + nodeName.replaceFirst(importedName, importedEntityName))
                    }
                }

                // Attempt to match the imported entity name with exported nodes
                val matchedNode = potentialNodeNames.firstNotNullOfOrNull {
                    graph.nodes[it]
                }

                if (matchedNode != null && matchedNode is FullyQualifiedNodeBuilder) {
                    // Found matching node, qualify and replace
                    qualifyNode(node, matchedNode.id, graph, nodesToReplace)
                    return matchedNode
                }
            }
        }

        return null
    }
}*/