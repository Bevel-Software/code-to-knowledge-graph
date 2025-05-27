package software.bevel.code_to_knowledge_graph.antlr.kotlin

//DEPRECATED
/*open class ConverterBasedAntlrKotlinSpecification: ConverterBasedAntlrLanguageSpecification<KotlinParser, KotlinLexer> {
    override fun shouldParseFile(fileName: String): Boolean {
        return fileName.endsWith(".kt") && !fileName.contains("/resources/") && !fileName.contains("\\resources\\")
    }

    override fun getParser(input: TokenStream): KotlinParser = KotlinParser(input)

    override fun getLexer(input: CharStream): KotlinLexer = KotlinLexer(input)

    override fun runTopLevelRule(parser: KotlinParser): ParserRuleContext = parser.kotlinFile()

    private fun addDefinedByAndParentConnections(
        graph: GraphBuilder,
        node: NodeBuilder,
        pathToFile: String,
        definedBy: Pair<NodeBuilder, ParseTreeRule>,
        parent: Pair<String, ParseTreeRule>? = null
    ) {
        graph.addConnectionAndMissingNodes(Connection(
            definedBy.first.id,
            node.id,
            ConnectionType.DEFINES,
            pathToFile,
            definedBy.second.sidx,
            definedBy.second.eidx
        ), definedBy.first)
        if(parent != null) {
            graph.addConnectionAndMissingNodes(Connection(
                parent.first,
                node.id,
                ConnectionType.PARENT_OF,
                pathToFile,
                parent.second.sidx,
                parent.second.eidx
            ), definedBy.first)
        }
    }

    private fun getTypeName(typeNode: ParseTreeRule): String {
        return (getTypeNameRecursive(typeNode) ?: typeNode.sourceCode).replace("\n", "").replace(" ", "")
    }

    private fun getTypeNameRecursive(typeNode: ParseTreeRule): String? {
        if(typeNode.rule == "functionType" && typeNode.rule == "userType") {
            return typeNode.sourceCode
        }
        typeNode.children
            .forEach{ child ->
                val type = getTypeNameRecursive(child)
                if(type != null) {
                    return type
                }
            }
        return null
    }

    override fun declareConverters(pathToFile: String, fileImports: MutableList<ImportStatement>, startIndex: Int): List<AntlrTreeWalker.TreeToGraphConverter> {
        //TODO: implement startIndex
        val converters = listOf(
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("importHeader", "identifier")
            ) { _, matchedNodes, _ ->
                val importName = if(matchedNodes[0].sourceCode.contains(".*")) {
                    matchedNodes[1].sourceCode + ".*"
                } else {
                    matchedNodes[1].sourceCode
                }
                val importAlias = matchedNodes[0].children.find { it.rule == "importAlias" }
                if(importAlias != null) {
                    fileImports.add(ImportStatement(importName, mapOf("alias" to importAlias.children.find { it.rule == "simpleIdentifier" }!!.sourceCode)))
                } else {
                    fileImports.add(ImportStatement(importName, mapOf()))
                }
                null
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("kotlinFile", "*", "packageHeader", "identifier")
            ) { definedBy, matchedNodes, graph ->
                val node = FullyQualifiedNodeBuilder(
                    name = matchedNodes[2].sourceCode,
                    nodeType = NodeType.Package,
                )
                graph.nodes[node.id] = node
                if(definedBy != null && definedBy.id != defaultPackageName) {
                    addDefinedByAndParentConnections(
                        graph,
                        node,
                        pathToFile,
                        definedBy to matchedNodes[1]
                    )
                }
                node
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("classDeclaration", "simpleIdentifier")
            ) { definedBy, matchedNodes, graph ->
                if(definedBy == null) throw Exception("Class must have a definedBy")
                val node = FullyQualifiedNodeBuilder(
                    name = definedBy.id + "." + matchedNodes[1].sourceCode,
                    nodeType = NodeType.Class
                )
                graph.nodes[node.id] = node
                addDefinedByAndParentConnections(
                    graph,
                    node,
                    pathToFile,
                    definedBy to matchedNodes[0]
                )

                val primaryConstructor = matchedNodes[0].children.find { it.rule == "primaryConstructor" }

                if(primaryConstructor != null) {
                    val constructor = FullyQualifiedNodeBuilder(
                        name = node.id + primaryConstructor.sourceCode,
                        nodeType = NodeType.Function
                    )
                    graph.nodes[constructor.id] = constructor
                    addDefinedByAndParentConnections(
                        graph,
                        constructor,
                        pathToFile,
                        node to primaryConstructor
                    )
                }
                node
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("typeParameter", "simpleIdentifier")
            ) { definedBy, matchedNodes, graph ->
                if(definedBy == null) throw Exception("Type must have a definedBy")
                val node = FullyQualifiedNodeBuilder(
                    name = definedBy.id + "." + matchedNodes[1].sourceCode,
                    nodeType = NodeType.Alias
                )
                graph.nodes[node.id] = node
                addDefinedByAndParentConnections(
                    graph,
                    node,
                    pathToFile,
                    definedBy to matchedNodes[1]
                )
                val typeConstraint = matchedNodes[0].children.find { it.sourceCode == "type" }
                if(typeConstraint != null) {
                    val typeName = getTypeName(typeConstraint)
                    graph.addConnectionAndMissingNodes(Connection(node.id, typeName, ConnectionType.INHERITS_FROM, pathToFile, matchedNodes[0].sidx, matchedNodes[0].eidx),
                        targetNodeFallback = { DanglingNodeBuilder(name = typeName, pathToFile, context = definedBy, nodeType = NodeType.Alias) })
                }
                node
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("typeConstraints", "typeConstraint", "simpleIdentifier")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Type must have a definedBy")
                val typeConstraint = matchedNodes[0].children.find { it.sourceCode == "type" }
                if(typeConstraint != null && graph.nodes.containsKey(matchedNodes[2].sourceCode)) {
                    val typeName = getTypeName(typeConstraint)
                    graph.addConnectionAndMissingNodes(Connection(matchedNodes[2].sourceCode, typeName, ConnectionType.INHERITS_FROM, pathToFile, matchedNodes[0].sidx, matchedNodes[0].eidx),
                        targetNodeFallback = { DanglingNodeBuilder(name = typeName, pathToFile, context = definedBy, nodeType = NodeType.Alias) })
                }
                else if(!graph.nodes.containsKey(matchedNodes[2].sourceCode)) {
                    BevelLogger.logger.info("Type constraint not found for ${matchedNodes[0].sourceCode}")
                }
                null
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("primaryConstructor", "classParameters", "classParameter", "simpleIdentifier")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Primary constructor parameter must have a definedBy")
                val typeNode = matchedNodes[2].children.firstOrNull { it.rule == "type" }
                val node = FullyQualifiedNodeBuilder(
                    name = definedBy.id + "." + matchedNodes[3].sourceCode,
                    nodeType = NodeType.Property
                )
                graph.nodes[node.id] = node
                addDefinedByAndParentConnections(
                    graph,
                    node,
                    pathToFile,
                    definedBy to matchedNodes[0]
                )
                if(typeNode != null) {
                    val typeName = getTypeName(typeNode)
                    graph.addConnectionAndMissingNodes(
                        Connection(
                            node.id,
                            typeName,
                            ConnectionType.IS_OF_TYPE,
                            pathToFile,
                            matchedNodes[0].sidx,
                            matchedNodes[0].eidx
                        ),
                        targetNodeFallback = { DanglingNodeBuilder(name = typeName, pathToFile, context = definedBy, nodeType = NodeType.Alias) }
                    )
                }
                node
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("delegationSpecifier", "constructorInvocation", "userType")
            ) { parent, matchedNodes, graph ->
                if (parent == null)
                    throw Exception("Delegation specifier must have a parent")
                graph.addConnectionAndMissingNodes(Connection(parent.id, matchedNodes[2].sourceCode, ConnectionType.INHERITS_FROM, pathToFile, matchedNodes[0].sidx, matchedNodes[0].eidx), parent)
                null
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("delegationSpecifier", "userType")
            ) { parent, matchedNodes, graph ->
                if (parent == null)
                    throw Exception("Delegation specifier must have a parent")
                graph.addConnectionAndMissingNodes(Connection(parent.id, matchedNodes[1].sourceCode, ConnectionType.INHERITS_FROM, pathToFile, matchedNodes[0].sidx, matchedNodes[0].eidx), parent)
                null
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("objectDeclaration", "simpleIdentifier")
            ) { definedBy, matchedNodes, graph ->
                if(definedBy == null) throw Exception("Object must have a definedBy")
                val node = FullyQualifiedNodeBuilder(
                    name = definedBy.id + "." +  matchedNodes[1].sourceCode,
                    nodeType = NodeType.Class
                )
                graph.nodes[node.id] = node
                addDefinedByAndParentConnections(
                    graph,
                    node,
                    pathToFile,
                    definedBy to matchedNodes[0]
                )
                node
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("companionObject")
            ) { definedBy, matchedNodes, graph ->
                if(definedBy == null) throw Exception("Companion object must have a definedBy")
                val simpleIdentifier = matchedNodes[0].children.find { it.rule == "simpleIdentifier" }
                val name = if (simpleIdentifier != null) "." + simpleIdentifier.sourceCode else ".<companion>"
                val node = FullyQualifiedNodeBuilder(
                    name = definedBy.id + name,
                    nodeType = NodeType.Class
                )
                graph.nodes[node.id] = node
                addDefinedByAndParentConnections(
                    graph,
                    node,
                    pathToFile,
                    definedBy to matchedNodes[0]
                )
                node
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("propertyDeclaration", "*", "variableDeclaration", "simpleIdentifier")
            ) { definedBy, matchedNodes, graph ->
                if(definedBy == null) throw Exception("Property must have a definedBy")
                val simpleIdentifier = matchedNodes[2]
                val type = matchedNodes[1].children.find { it.rule == "type" }
                val parentType = matchedNodes[0].children.find { it.rule == "type" }
                val parentName = parentType?.let { getTypeName(it) } ?: definedBy.id
                val node = FullyQualifiedNodeBuilder(
                    name = parentName + "." + simpleIdentifier.sourceCode,
                    nodeType = NodeType.Property
                )
                graph.nodes[node.id] = node
                addDefinedByAndParentConnections(
                    graph,
                    node,
                    pathToFile,
                    definedBy to matchedNodes[0],
                    parentType?.let { parentName to it }
                )
                if(type != null) {
                    val typeName = getTypeName(type)
                    graph.addConnectionAndMissingNodes(
                        Connection(node.id, typeName, ConnectionType.IS_OF_TYPE, pathToFile, matchedNodes[0].sidx, matchedNodes[0].eidx),
                        targetNodeFallback = { DanglingNodeBuilder(typeName, pathToFile, context = definedBy, nodeType = NodeType.Alias) }
                    )
                }
                node
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("functionDeclaration", "simpleIdentifier")
            ) { definedBy, matchedNodes, graph ->
                if(definedBy == null) throw Exception("Function must have a definedBy")
                val functionParams = matchedNodes[0].children
                    .find { it.rule == "functionValueParameters" } ?: throw Exception("Function declaration must have function value parameters")
                val identifier = matchedNodes[1].sourceCode + functionParams.sourceCode
                val parentType = matchedNodes[0].children
                    .subList(0, matchedNodes[0].children.indexOf(functionParams))
                    .find { it.rule == "type" }
                val parentName = parentType?.let { getTypeName(it) } ?: definedBy.id
                val returnType = matchedNodes[0].children
                    .subList(matchedNodes[0].children.indexOf(functionParams), matchedNodes[0].children.count())
                    .find { it.rule == "type" }

                val node = FullyQualifiedNodeBuilder(
                    name = "$parentName.$identifier",
                    nodeType = NodeType.Function
                )
                graph.nodes[node.id] = node

                val generalFunctionName = "$parentName.${matchedNodes[1].sourceCode}"
                if(graph.nodes[generalFunctionName] == null) {
                    graph.nodes[generalFunctionName] =
                        FullyQualifiedNodeBuilder(generalFunctionName, nodeType = NodeType.Function)
                    addDefinedByAndParentConnections(
                        graph,
                        graph.nodes[generalFunctionName]!!,
                        pathToFile,
                        definedBy to matchedNodes[0],
                        parentType?.let { parentName to it }
                    )
                }
                addDefinedByAndParentConnections(
                    graph,
                    node,
                    pathToFile,
                    graph.nodes[generalFunctionName]!! to matchedNodes[0],
                )

                if(returnType != null) {
                    val typeName = getTypeName(returnType)
                    graph.addConnectionAndMissingNodes(Connection(node.id, typeName, ConnectionType.USES, pathToFile, matchedNodes[0].sidx, matchedNodes[0].eidx),
                        targetNodeFallback = { DanglingNodeBuilder(typeName, pathToFile, context = definedBy, nodeType = NodeType.Alias) })
                }
                node
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("typeAlias", "simpleIdentifier")
            ) { definedBy, matchedNodes, graph ->
                if(definedBy == null) throw Exception("Type alias must have a definedBy")
                val simpleIdentifier = matchedNodes[1]
                val parentType = matchedNodes[0].children.find { it.rule == "type" }
                val parentName = parentType?.let { getTypeName(it) } ?: definedBy.id

                val node = FullyQualifiedNodeBuilder(
                    name = parentName + "." + simpleIdentifier.sourceCode,
                    nodeType = NodeType.Alias
                )
                graph.nodes[node.id] = node
                addDefinedByAndParentConnections(
                    graph,
                    node,
                    pathToFile,
                    definedBy to matchedNodes[0],
                    parentType?.let { parentName to it }
                )
                node
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("anonymousInitializer")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("AnonymousInitializer must have a parent")
                val node = FullyQualifiedNodeBuilder(
                    name = definedBy.id + ".<init>",
                    nodeType = NodeType.Function
                )
                graph.nodes[node.id] = node
                addDefinedByAndParentConnections(
                    graph,
                    node,
                    pathToFile,
                    definedBy to matchedNodes[0]
                )
                node
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("secondaryConstructor", "functionValueParameters")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Secondary constructor must have a definedBy")
                val node = FullyQualifiedNodeBuilder(
                    name = definedBy.id + "." + matchedNodes[1].sourceCode,
                    nodeType = NodeType.Function
                )
                graph.nodes[node.id] = node
                addDefinedByAndParentConnections(
                    graph,
                    node,
                    pathToFile,
                    definedBy to matchedNodes[0]
                )
                node
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("parameter", "simpleIdentifier")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Parameter must have a definedBy")
                val typeNode = matchedNodes[0].children.firstOrNull { it.rule == "type" }
                val variable = FullyQualifiedNodeBuilder(
                    name = definedBy.id + "." + matchedNodes[1].sourceCode,
                    nodeType = NodeType.Property
                )
                graph.nodes[variable.id] = variable
                addDefinedByAndParentConnections(
                    graph,
                    variable,
                    pathToFile,
                    definedBy to matchedNodes[0]
                )
                if(typeNode != null) {
                    val typeName = getTypeName(typeNode)
                    graph.addConnectionAndMissingNodes(
                        Connection(
                            variable.id,
                            typeName,
                            ConnectionType.IS_OF_TYPE,
                            pathToFile,
                            matchedNodes[0].sidx,
                            matchedNodes[0].eidx
                        ),
                        targetNodeFallback = { DanglingNodeBuilder(typeName, pathToFile, context = definedBy, nodeType = NodeType.Alias) }
                    )
                }
                variable
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("infixFunctionCall", "simpleIdentifier")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Infix function call must have a definedBy")
                graph.addConnectionAndMissingNodes(Connection(definedBy.id, matchedNodes[1].sourceCode, ConnectionType.USES, pathToFile, matchedNodes[0].sidx, matchedNodes[0].eidx), definedBy)
                null
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("namedInfix", "type")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Infix function call must have a definedBy")
                val typeName = getTypeName(matchedNodes[0])
                graph.addConnectionAndMissingNodes(Connection(definedBy.id, typeName, ConnectionType.USES, pathToFile, matchedNodes[0].sidx, matchedNodes[0].eidx),
                    targetNodeFallback = { DanglingNodeBuilder(typeName, pathToFile, context = definedBy, nodeType = NodeType.Alias) })
                null
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("annotatedLambda", "*", "lambdaLiteral")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Function literal must have a definedBy")
                when (definedBy) {
                    is DanglingNodeBuilder -> if (definedBy.nodeType == NodeType.Class) return@TreeToGraphConverter null
                    is FullyQualifiedNodeBuilder -> if (definedBy.nodeType == NodeType.Class) return@TreeToGraphConverter null
                }
                val node = FullyQualifiedNodeBuilder(
                    name = definedBy.id + ".<lambda${matchedNodes[1].sourceCode.hashCode()}>",
                    nodeType = NodeType.Function
                )
                graph.nodes[node.id] = node
                addDefinedByAndParentConnections(
                    graph,
                    node,
                    pathToFile,
                    definedBy to matchedNodes[0]
                )
                /*val callerName: String
                var chainCall = matchedNodes[0].label.removeSuffix(matchedNodes[1].label)

                if(chainCall.contains("."))
                    chainCall = chainCall.split(".").last()
                callerName = graph.nodes.keys.firstOrNull { it.endsWith(".$chainCall") || it.contains(".$chainCall(") }
                    ?: chainCall
                graph.addConnectionAndMissingNodes(Connection(callerName, node.name, ConnectionType.USES, pathToFile, matchedNodes[0].sidx, matchedNodes[0].eidx), definedBy)*/
                node
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("lambdaLiteral")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Function literal must have a definedBy")
                when (definedBy) {
                    is DanglingNodeBuilder -> if (definedBy.nodeType == NodeType.Class) return@TreeToGraphConverter null
                    is FullyQualifiedNodeBuilder -> if (definedBy.nodeType == NodeType.Class) return@TreeToGraphConverter null
                }
                val node = FullyQualifiedNodeBuilder(
                    name = definedBy.id + ".<lambda${matchedNodes[0].sourceCode.hashCode()}>",
                    nodeType = NodeType.Function
                )
                graph.nodes[node.id] = node
                addDefinedByAndParentConnections(
                    graph,
                    node,
                    pathToFile,
                    definedBy to matchedNodes[0]
                )
                node
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("variableDeclaration", "simpleIdentifier")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Variable declaration must have a definedBy")
                val typeNode = matchedNodes[0].children.find { it.sourceCode == "type" }
                val variable = FullyQualifiedNodeBuilder(
                    name = definedBy.id + "." +  matchedNodes[1].sourceCode,
                    nodeType = NodeType.Property
                )
                graph.nodes[variable.id] = variable
                addDefinedByAndParentConnections(
                    graph,
                    variable,
                    pathToFile,
                    definedBy to matchedNodes[0]
                )
                if(typeNode != null) {
                    val typeName = getTypeName(typeNode)
                    graph.addConnectionAndMissingNodes(
                        Connection(
                            variable.id,
                            typeName,
                            ConnectionType.IS_OF_TYPE,
                            pathToFile,
                            matchedNodes[0].sidx,
                            matchedNodes[0].eidx
                        ),
                        targetNodeFallback = { DanglingNodeBuilder(typeName, pathToFile, context = definedBy, nodeType = NodeType.Alias) }
                    )
                }
                variable
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("objectLiteral")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Object literal must have a parent")
                val objectName = definedBy.id + ".<objectLiteral>${matchedNodes[0].sourceCode.hashCode()}"
                val node = FullyQualifiedNodeBuilder(
                    name = objectName,
                    nodeType = NodeType.Class
                )
                graph.nodes[node.id] = node
                addDefinedByAndParentConnections(
                    graph,
                    node,
                    pathToFile,
                    definedBy to matchedNodes[0]
                )
                node
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("atomicExpression", "simpleIdentifier")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Atomic expression must have been used by something")
                graph.addConnectionAndMissingNodes(Connection(definedBy.id, matchedNodes[1].sourceCode, ConnectionType.USES, pathToFile, matchedNodes[0].sidx, matchedNodes[0].eidx), definedBy)
                null
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("callableReference", "usertype")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Callable reference must have been used by something")
                graph.addConnectionAndMissingNodes(Connection(definedBy.id, matchedNodes[1].sourceCode, ConnectionType.USES, pathToFile, matchedNodes[0].sidx, matchedNodes[0].eidx), definedBy)
                null
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("callableReference", "identifier")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Callable reference must have been used by something")
                graph.addConnectionAndMissingNodes(Connection(definedBy.id, matchedNodes[1].sourceCode, ConnectionType.USES, pathToFile, matchedNodes[0].sidx, matchedNodes[0].eidx), definedBy)
                null
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("constructorDelegationCall", "*", "valueArgument", "simpleIdentifier")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Constructor delegation call must have been used by something")

                /*while (definedBy != null && graph.nodes[definedBy.parentName] is FullyQualifiedNodeBuilder && definedBy.nodeType != NodeType.CLASS) {
                    definedBy = graph.nodes[definedBy.parentName] as FullyQualifiedNodeBuilder
                }*/
                graph.addConnectionAndMissingNodes(Connection(definedBy.id, matchedNodes[2].sourceCode, ConnectionType.USES, pathToFile, matchedNodes[0].sidx, matchedNodes[0].eidx), definedBy)
                    //?: throw Exception("Constructor delegation call must be used by someone")
                null
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("postfixUnaryExpression", "primaryExpression", "simpleIdentifier")
            ) {definedBy, matchedNodes, graph ->
                if(definedBy == null) throw Exception("Postfix unary expression must have been used by something")
                val callerName = matchedNodes[2].sourceCode
                graph.addConnectionAndMissingNodes(Connection(definedBy.id, callerName, ConnectionType.USES, pathToFile, matchedNodes[0].sidx, matchedNodes[0].eidx), definedBy)
                null
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("postfixUnaryExpression", "primaryExpression", "callableReference")
            ) {definedBy, matchedNodes, graph ->
                if(definedBy == null) throw Exception("Postfix unary expression must have been used by something")
                val callerName = matchedNodes[2].sourceCode
                graph.addConnectionAndMissingNodes(Connection(definedBy.id, callerName, ConnectionType.USES, pathToFile, matchedNodes[0].sidx, matchedNodes[0].eidx), definedBy)
                null
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("postfixUnaryExpression", "postfixUnarySuffix", "navigationSuffix", "simpleIdentifier")
            ) {definedBy, matchedNodes, graph ->
                if(definedBy == null) throw Exception("Postfix unary expression must have been used by something")
                val callerName = matchedNodes[3].sourceCode
                graph.addConnectionAndMissingNodes(Connection(definedBy.id, callerName, ConnectionType.USES, pathToFile, matchedNodes[0].sidx, matchedNodes[0].eidx), definedBy)
                null
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("postfixUnaryExpression", "postfixUnarySuffix", "*", "valueArguments", "valueArgument", "*", "simpleIdentifier")
            ) { definedBy, matchedNodes, graph ->
                if(definedBy == null) throw Exception("Postfix unary expression must have been used by something")
                //var callerName: String? = null

                val primaryExpression = matchedNodes[0].children
                    .find { it.rule == "primaryExpression" }
                val caller = primaryExpression?.children?.find { it.rule == "simpleIdentifier" || it.rule == "callableReference" || it.rule == "thisExpression" || it.rule == "superExpression" }
                if(caller != null && (caller.rule == "simpleIdentifier" || caller.rule == "callableReference")) {
                    var chainCall = caller.sourceCode
                    if(chainCall.contains("."))
                        chainCall = chainCall.split(".").last()
                    val callerName = chainCall
                    if(matchedNodes[4].parent.children.size > 1
                        && matchedNodes[4].parent.children.find { it.sourceCode == "=" } != null
                        && matchedNodes[4].parent.children[1] == matchedNodes[4]) {
                        return@TreeToGraphConverter null //TODO: verify this
                    }
                    graph.addConnectionAndMissingNodes(Connection(callerName, matchedNodes[4].sourceCode, ConnectionType.CALLED_WITH, pathToFile, matchedNodes[0].sidx, matchedNodes[0].eidx), definedBy)
                } else if(caller != null && (caller.rule == "thisExpression" || caller.rule == "superExpression")) {
                    val parentClass = findParentClassNode(definedBy, graph)
                    if(parentClass != null) {
                        if(caller.rule == "superExpression") {
                            graph.getSuperType(parentClass.id).forEach {
                                graph.addConnectionAndMissingNodes(Connection(it.id, matchedNodes[4].sourceCode, ConnectionType.USES, pathToFile, matchedNodes[0].sidx, matchedNodes[0].eidx), definedBy)
                            }
                        } else {
                            graph.addConnectionAndMissingNodes(Connection(parentClass.id, matchedNodes[4].sourceCode, ConnectionType.USES, pathToFile, matchedNodes[0].sidx, matchedNodes[0].eidx), definedBy)
                        }
                    }
                }
                null
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("valueArgument", "simpleIdentifier")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Value argument must have been used by something")
                graph.addConnectionAndMissingNodes(Connection(definedBy.id, matchedNodes[1].sourceCode, ConnectionType.USES, pathToFile, matchedNodes[0].sidx, matchedNodes[0].eidx), definedBy)
                null
            },
            AntlrTreeWalker.TreeToGraphConverter(
                listOf("type", "*", "userType")
            ) { definedBy, matchedNodes, graph ->
                if (definedBy == null) throw Exception("Value argument must have been used by something")
                val typeName = getTypeName(matchedNodes[0])
                if(typeName != matchedNodes[1].sourceCode)
                    graph.addConnectionAndMissingNodes(Connection(typeName, matchedNodes[1].sourceCode, ConnectionType.IS_OF_TYPE, pathToFile, matchedNodes[0].sidx, matchedNodes[0].eidx),
                        sourceNodeFallback = { DanglingNodeBuilder(typeName, pathToFile, nodeType = NodeType.Alias) })
                null
            }
            )
        return converters
    }

    private fun findParentClassNode(node: NodeBuilder, graph: GraphBuilder): NodeBuilder? {
        var currentNode: NodeBuilder? = node
        while (currentNode != null) {
            if ((currentNode is FullyQualifiedNodeBuilder && currentNode.nodeType == NodeType.Class)
                || (currentNode is DanglingNodeBuilder && currentNode.nodeType == NodeType.Class)) {
                return currentNode
            }
            currentNode = graph.getParent(currentNode.id)
        }
        return null
    }

    override fun findFullyQualifiedNodeInImports(node: DanglingNodeBuilder, graph: GraphBuilder, nodesToReplace: MutableMap<String, String>): FullyQualifiedNodeBuilder? {
        //logger.info("Looking for fully qualified node in imports: ${node.name}")
        for (importStatement in node.importStatements) {
            val importName = importStatement.importedPackage
            // Check if the import directly imports the class
            if (importName.endsWith(".${node.id}")) {
                val fqNode = graph.nodes.values.find {
                    it is FullyQualifiedNodeBuilder && it.id == importName
                }
                if (fqNode != null && fqNode is FullyQualifiedNodeBuilder) {
                    return fqNode
                } else {
                    qualifyNode(node, importName, graph, nodesToReplace)
                }
            } else if (importName.endsWith(".*")) {
                // Handle wildcard imports
                val packageName = importName.removeSuffix(".*")
                val fqNode = graph.nodes.values.find {
                    it is FullyQualifiedNodeBuilder && it.id == "$packageName.${node.id}"
                }
                if (fqNode != null && fqNode is FullyQualifiedNodeBuilder) {
                    return fqNode
                }

                // Check child nodes of the imported package or class
                val parentNode = graph.nodes.values.find {
                    it is FullyQualifiedNodeBuilder && it.id == packageName
                }
                if (parentNode != null && parentNode is FullyQualifiedNodeBuilder) {
                    val childNode = graph.getChildren(parentNode.id).find { it is FullyQualifiedNodeBuilder && it.id.endsWith(".${node.id}") }
                    if (childNode is FullyQualifiedNodeBuilder) {
                        return childNode
                    }
                }
            }
        }
        return null
    }
}
*/