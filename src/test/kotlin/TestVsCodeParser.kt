import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import software.bevel.code_to_knowledge_graph.providers.IoFileHandler
import software.bevel.code_to_knowledge_graph.providers.RegexIdentifierTokenizer
import software.bevel.code_to_knowledge_graph.vscode.VsCodeParser
import software.bevel.code_to_knowledge_graph.vscode.data.*
import software.bevel.code_to_knowledge_graph.vscode.languageSpecs.GeneralLanguageSpecification
import software.bevel.file_system_domain.FileWalker
import software.bevel.file_system_domain.web.LocalCommunicationInterface
import software.bevel.graph_domain.graph.*
import software.bevel.graph_domain.graph.builder.FullyQualifiedNodeBuilder
import software.bevel.graph_domain.hashing.LocalitySensitiveHasher
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Properties

class TestVsCodeParser {
    private val logger: Logger = LoggerFactory.getLogger(TestVsCodeParser::class.java)
    companion object {
        @BeforeAll
        @JvmStatic
        fun prepareTest() {
            // Create test version.properties if it doesn't exist
            val versionProps = Properties()
            versionProps.setProperty("version", "1.0.0-TEST")
            val testResourcesPath = Paths.get("src/test/resources")
            if (!Files.exists(testResourcesPath)) {
                Files.createDirectories(testResourcesPath)
            }
            val versionFile = testResourcesPath.resolve("version.properties")
            versionProps.store(Files.newOutputStream(versionFile), "Test version properties")
        }
    }

    val fileHandler = IoFileHandler()
    val hasher: LocalitySensitiveHasher = object : LocalitySensitiveHasher {
        override fun hash(input: String): String {
            return input.hashCode().toUInt().toString()
        }

        override fun similarity(hash1: String, hash2: String): Double {
            return 0.0
        }
    }
    val identifierTokenizer = RegexIdentifierTokenizer()
    val language = GeneralLanguageSpecification(fileHandler, VsCodeNodeBuilder(fileHandler, hasher), identifierTokenizer)
    val communicationInterface: LocalCommunicationInterface = Mockito.mock(LocalCommunicationInterface::class.java)
    val fileWalker = Mockito.mock(FileWalker::class.java)

    fun getParser(): VsCodeParser {
        return VsCodeParser(
            "src/test/resources/tmp",
            communicationInterface,
            fileHandler,
            language,
            fileWalker,
            logger,
            "1.0.0-TEST"
        )
    }

    private fun mockFileWalker(vararg files: ParsingTestHelper.CodeFile) {
        val absolutePaths = files.map { "src/test/resources/tmp/${it.fileName}" }
        Mockito.`when`(fileWalker.walk(Mockito.anyString())).thenReturn(absolutePaths)
    }

    private fun mockVsCodeResponses(documentSymbols: Map<String, List<VsCodeDocumentSymbol>>) {
        // Mock document symbols response for single command
        Mockito.`when`(communicationInterface.send(Mockito.anyString()))
            .thenAnswer {
                val message = it.arguments[0] as String
                val documentSymbolsJson = jacksonObjectMapper().writeValueAsString(
                    VsCodeDocumentSymbolResponse(
                    documentSymbols.entries.find { entry ->
                        entry.key in message || entry.key.replace("/", "\\") in message
                    }?.value ?: emptyList()
                )
                )
                documentSymbolsJson
            }

        // Mock document symbols response for batch command
        Mockito.`when`(communicationInterface.send(Mockito.anyList<VsCodeCommand>()))
            .thenAnswer { invocation ->
                val commands = invocation.arguments[0] as List<*>
                val responses = commands.map { command ->
                    when ((command as VsCodeCommand).command) {
                        "vscode.executeDocumentSymbolProvider" -> VsCodeDocumentSymbolResponse(
                            documentSymbols.entries.find { entry ->
                                entry.key in (command.args[0] as String)
                                        || entry.key.replace("/", "\\") in (command.args[0] as String)
                            }?.value ?: emptyList()
                        )
                        else -> null
                    }
                }
                jacksonObjectMapper().writeValueAsString(BatchNodeRequestResponse(responses))
            }

        // Mock progress update
        Mockito.doNothing().`when`(communicationInterface).sendWithoutResponse(Mockito.matches(".*progressUpdate.*"))
    }

    @Test
    fun testPackageHeaderConverter() {
        // Create a test Kotlin file with a package declaration
        val code = ParsingTestHelper.CodeFile("test.kt", """
            package com.example

            class MyClass
        """.trimIndent())

        // Mock file walker to return our test file
        mockFileWalker(code)

        // Mock VSCode responses
        val documentSymbols = listOf(
            VsCodeDocumentSymbol(
                "MyClass",
                "",
                VsCodeSymbolKind.Class,
                VsCodeRange(2, 0, 2, 13),
                VsCodeRange(2, 6, 2, 13),
                listOf()
            )
        )
        val filePath = "tmp/test.kt"
        mockVsCodeResponses(mapOf(filePath to documentSymbols))

        ParsingTestHelper.runParseGraphBuilderOnCode(getParser(), code) { graph, _ ->
            // Get expected node ID based on file path and ranges
            val classNodeId = ".MyClass"

            // Verify the nodes and their connections
            assert(graph.nodes.values.find { it is FullyQualifiedNodeBuilder && it.id.endsWith(classNodeId) && it.nodeType == NodeType.Class } != null)
        }
    }

    @Test
    fun testObjectDeclarationConverter() {
        // Create a test Kotlin file with an object declaration
        val code = ParsingTestHelper.CodeFile("test.kt", """
            object MyObject
        """.trimIndent())

        // Mock file walker to return our test file
        mockFileWalker(code)

        // Mock VSCode responses
        val documentSymbols = listOf(
            VsCodeDocumentSymbol(
                "MyObject",
                "",
                VsCodeSymbolKind.Object,
                VsCodeRange(0, 0, 0, 14),
                VsCodeRange(0, 7, 0, 14),
                listOf()
            )
        )
        val filePath = "tmp/test.kt"
        mockVsCodeResponses(mapOf(filePath to documentSymbols))

        ParsingTestHelper.runParseGraphBuilderOnCode(getParser(), code) { graph, _ ->
            // Get expected node ID based on file path and ranges
            val objectNodeId = ".MyObject"

            // Verify the nodes and their connections
            assert(graph.nodes.values.find { it.id.endsWith(objectNodeId) && it is FullyQualifiedNodeBuilder && it.nodeType == NodeType.Class } != null)
        }
    }

    @Test
    fun testClassWithFunctionAndProperty() {
        // Create a test Kotlin file with a class containing function and property
        val code = ParsingTestHelper.CodeFile("test.kt", """
            package test
            class MyClass {
                var property: MyClass
                fun foo() {
                    val variable: Int
                    for (i in 1..10) {
                        println(i + variable + property)
                    }
                }
            }
        """.trimIndent())

        // Mock file walker to return our test file
        mockFileWalker(code)

        // Mock VSCode responses
        val propertySymbol = VsCodeDocumentSymbol(
            "property",
            "",
            VsCodeSymbolKind.Property,
            VsCodeRange(2, 4, 2, 24),
            VsCodeRange(2, 8, 2, 16),
            listOf()
        )
        
        val fooMethodSymbol = VsCodeDocumentSymbol(
            "foo",
            "",
            VsCodeSymbolKind.Function,
            VsCodeRange(3, 4, 8, 5),
            VsCodeRange(3, 8, 3, 11),
            listOf()
        )

        val classSymbol = VsCodeDocumentSymbol(
            "MyClass",
            "",
            VsCodeSymbolKind.Class,
            VsCodeRange(1, 0, 9, 1),
            VsCodeRange(1, 6, 1, 13),
            listOf(propertySymbol, fooMethodSymbol)
        )
        val filePath = "tmp/test.kt"
        val fileNode = ".test"
        val classNodeId = ".MyClass"
        val propertyNodeId = ".property"
        val methodNodeId = ".foo"

        mockVsCodeResponses(mapOf(filePath to listOf(classSymbol)))

        ParsingTestHelper.runParseGraphBuilderOnCode(getParser(), code) { graph, _ ->
            // Get expected node IDs based on file path and ranges

            // Verify the nodes
            assert(graph.nodes.values.find { it.id.endsWith(fileNode) && it is FullyQualifiedNodeBuilder && it.nodeType == NodeType.File } != null)
            assert(graph.nodes.values.find { it.id.endsWith(classNodeId) && it is FullyQualifiedNodeBuilder && it.nodeType == NodeType.Class } != null)
            assert(graph.nodes.values.find { it.id.endsWith(propertyNodeId) && it is FullyQualifiedNodeBuilder && it.nodeType == NodeType.Property } != null)
            assert(graph.nodes.values.find { it.id.endsWith(methodNodeId) && it is FullyQualifiedNodeBuilder && it.nodeType == NodeType.Function } != null)

            assertEquals(5, graph.nodes.size)

            // Verify the connections
            assert(graph.connectionsBuilder.getAllConnections().isEmpty())
        }
    }

    @Test
    fun testCrossReferencesAndCompanionObjects() {
        val codeFile1 = ParsingTestHelper.CodeFile("test1.kt", """
            package test2
    
            class ClassA {
                
            }
        """.trimIndent())

        val codeFile2 = ParsingTestHelper.CodeFile("test2.kt", """
            package test

            import test.ClassA

            class ClassB(
                val param: Int
            ) {
                fun methodB() {
                    ClassA().methodA {
                        println("Lambda in methodA called from methodB")
                    }
                    ClassA().methodA() {
                        println("Lambda in methodA called from methodB ${'$'}{ClassA.CONST_A}")
                    }
                }
                
                companion object {
                    fun methodB() {
                        println("Companion B")
                    }
                }
            }
        """.trimIndent())

        val codeFile3 = ParsingTestHelper.CodeFile("test3.kt", """
            package test
    
            import test.ClassB
            
            class ClassA {
                fun methodA(lambda: () -> Unit) {
                    lambda()
                    ClassB(0).methodB()
                }
                
                companion object {
                    const val CONST_A = "A"
                }
            }
        """.trimIndent())

        // Mock file walker to return our test files
        mockFileWalker(codeFile1, codeFile2, codeFile3)

        // Calculate node IDs based on file paths and ranges
        val test1Path = "tmp/test1.kt"
        val test2Path = "tmp/test2.kt"
        val test3Path = "tmp/test3.kt"

        // Mock VSCode responses
        val classASymbol = VsCodeDocumentSymbol(
            "ClassA",
            "",
            VsCodeSymbolKind.Class,
            VsCodeRange(2, 0, 4, 1),
            VsCodeRange(2, 6, 2, 12),
            listOf()
        )

        val paramSymbol = VsCodeDocumentSymbol(
            "param",
            "",
            VsCodeSymbolKind.Property,
            VsCodeRange(5, 4, 5, 18),
            VsCodeRange(5, 8, 5, 13),
            listOf()
        )

        val methodBSymbol = VsCodeDocumentSymbol(
            "methodB",
            "",
            VsCodeSymbolKind.Function,
            VsCodeRange(7, 4, 13, 5),
            VsCodeRange(7, 8, 7, 15),
            listOf()
        )

        val companionMethodBSymbol = VsCodeDocumentSymbol(
            "methodB",
            "",
            VsCodeSymbolKind.Function,
            VsCodeRange(17, 8, 19, 9),
            VsCodeRange(17, 12, 17, 19),
            listOf()
        )

        val companionObjectSymbol = VsCodeDocumentSymbol(
            "Companion",
            "",
            VsCodeSymbolKind.Object,
            VsCodeRange(16, 4, 20, 5),
            VsCodeRange(16, 13, 16, 21),
            listOf(companionMethodBSymbol)
        )

        val classBSymbol = VsCodeDocumentSymbol(
            "ClassB",
            "",
            VsCodeSymbolKind.Class,
            VsCodeRange(4, 0, 21, 1),
            VsCodeRange(4, 6, 4, 12),
            listOf(paramSymbol, methodBSymbol, companionObjectSymbol)
        )

        val methodASymbol = VsCodeDocumentSymbol(
            "methodA",
            "",
            VsCodeSymbolKind.Function,
            VsCodeRange(5, 4, 8, 5),
            VsCodeRange(5, 8, 5, 15),
            listOf()
        )

        val constASymbol = VsCodeDocumentSymbol(
            "CONST_A",
            "",
            VsCodeSymbolKind.Variable,
            VsCodeRange(11, 18, 11, 25),
            VsCodeRange(11, 18, 11, 25),
            listOf()
        )

        val companionObjectASymbol = VsCodeDocumentSymbol(
            "Companion",
            "",
            VsCodeSymbolKind.Object,
            VsCodeRange(9, 4, 11, 5),
            VsCodeRange(9, 13, 9, 21),
            listOf(constASymbol)
        )

        val classA3Symbol = VsCodeDocumentSymbol(
            "ClassA",
            "",
            VsCodeSymbolKind.Class,
            VsCodeRange(4, 0, 12, 1),
            VsCodeRange(4, 6, 4, 12),
            listOf(methodASymbol, companionObjectASymbol)
        )

        // Calculate expected node IDs
        val classANodeId = ".ClassA"
        val classBNodeId = ".ClassB"
        val paramNodeId = ".param"
        val methodBNodeId = ".methodB"
        val companionBNodeId = ".Companion"
        val companionMethodBNodeId = ".methodB"
        val classA3NodeId = ".ClassA"
        val methodANodeId = ".methodA"
        val companionANodeId = ".Companion"
        val constANodeId = ".CONST_A"
        val test1File = ".test1"
        val test2File = ".test2"
        val test3File = ".test3"

        // Create a list of all nodes that need to be checked
        val nodesToCheck = listOf(
            Pair(language.defaultPackageName, NodeType.Package),
            Pair(classANodeId, NodeType.Class),
            Pair(classBNodeId, NodeType.Class),
            Pair(paramNodeId, NodeType.Property),
            Pair(methodBNodeId, NodeType.Function),
            Pair(companionBNodeId, NodeType.Class),
            Pair(companionMethodBNodeId, NodeType.Function),
            Pair(classA3NodeId, NodeType.Class),
            Pair(methodANodeId, NodeType.Function),
            Pair(companionANodeId, NodeType.Class),
            Pair(constANodeId, NodeType.Property),
            Pair(test1File, NodeType.File),
            Pair(test2File, NodeType.File),
            Pair(test3File, NodeType.File)
        )

        mockVsCodeResponses(mapOf(test1Path to listOf(classASymbol), test2Path to listOf(classBSymbol), test3Path to listOf(classA3Symbol)))

        ParsingTestHelper.runParseOnCode(getParser(), codeFile1, codeFile2, codeFile3) { graph, _ ->
            // Verify each node exists in the graph
            nodesToCheck.forEach { nodeId ->
                assertNotNull(graph.nodes.values.find { it.id.endsWith(nodeId.first) && it.nodeType == nodeId.second }, "Expected node ${nodeId.first} of type ${nodeId.second} not found in graph")
            }

            // Verify the expected number of nodes
            assertEquals(nodesToCheck.size, graph.nodes.size)

            assert(graph.connections.getAllConnections().isEmpty())
        }
    }
}