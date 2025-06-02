import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import software.bevel.code_to_knowledge_graph.providers.GraphMergingServiceImpl
import software.bevel.code_to_knowledge_graph.providers.MinHasher
import software.bevel.code_to_knowledge_graph.providers.RegexIdentifierTokenizer
import software.bevel.code_to_knowledge_graph.vscode.VsCodeGraphUpdater
import software.bevel.code_to_knowledge_graph.vscode.VsCodeParser
import software.bevel.code_to_knowledge_graph.vscode.data.*
import software.bevel.code_to_knowledge_graph.vscode.languageSpecs.GeneralLanguageSpecification
import software.bevel.file_system_domain.FileWalker
import software.bevel.file_system_domain.LCPosition
import software.bevel.file_system_domain.web.LocalCommunicationInterface
import software.bevel.graph_domain.graph.*
import software.bevel.graph_domain.hashing.LocalitySensitiveHasher
import software.bevel.graph_domain.parsing.PotentialSymbol
import software.bevel.graph_domain.tokenizers.IdentifierTokenizer
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import kotlin.math.abs
import kotlin.math.max

class TestVsCodeGraphUpdater {
    private val logger: Logger = LoggerFactory.getLogger(TestVsCodeGraphUpdater::class.java)
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

    val hasher: LocalitySensitiveHasher = MinHasher()
    private val identifierTokenizer: IdentifierTokenizer = RegexIdentifierTokenizer()
    val fileHandler = FakeFileHandler()
    val language = GeneralLanguageSpecification(fileHandler, VsCodeNodeBuilder(fileHandler, hasher), identifierTokenizer)
    val communicationInterface: LocalCommunicationInterface = Mockito.mock(LocalCommunicationInterface::class.java)
    val fileWalker = Mockito.mock(FileWalker::class.java)
    val projectPath = "src/test/resources/tmp"
    val graphMergingService = GraphMergingServiceImpl(hasher, fileHandler)

    @BeforeEach
    fun setup() {
        // Clear any previous test data
        fileHandler.clearFiles()
        
        // Reset mocks
        Mockito.reset(communicationInterface)
        Mockito.reset(fileWalker)
    }

    fun getParser(): VsCodeParser {
        return VsCodeParser(
            projectPath,
            communicationInterface,
            fileHandler,
            language,
            fileWalker,
            logger,
            "1.0.0-TEST"
        )
    }

    fun getGraphUpdater(parser: VsCodeParser): VsCodeGraphUpdater {
        return VsCodeGraphUpdater(
            projectPath,
            parser,
            graphMergingService,
        )
    }

    private fun mockFileWalker(vararg files: ParsingTestHelper.CodeFile) {
        val absolutePaths = files.map { "$projectPath/${it.fileName}" }
        Mockito.`when`(fileWalker.walk(Mockito.anyString())).thenReturn(absolutePaths)
    }

    private fun mockVsCodeResponses(documentSymbols: Map<String, List<VsCodeDocumentSymbol>>) {
        // Mock document symbols response for single command
        Mockito.`when`(communicationInterface.send(Mockito.anyString()))
            .thenAnswer {
                val message = it.arguments[0] as String
                val documentSymbolsJson = jacksonObjectMapper().writeValueAsString(VsCodeDocumentSymbolResponse(
                    documentSymbols.entries.find { entry ->
                        entry.key in message || entry.key.replace("/", "\\") in message
                    }?.value ?: emptyList()
                ))
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
    fun testAddFiles() {
        // Create initial file
        val initialCode = ParsingTestHelper.CodeFile("initial.kt", """
            package test
            
            class InitialClass {
                fun initialMethod() {}
            }
        """.trimIndent())

        // Create a file to add later
        val addedCode = ParsingTestHelper.CodeFile("added.kt", """
            package test
            
            class AddedClass {
                fun addedMethod() {}
            }
        """.trimIndent())

        // Add file content to our fake file handler
        fileHandler.addFile("$projectPath/initial.kt", initialCode.code)
        fileHandler.addFile("$projectPath/added.kt", addedCode.code)

        // Mock file walker to return our initial file
        mockFileWalker(initialCode)

        // Mock VSCode responses for initial file
        val initialDocumentSymbols = listOf(
            VsCodeDocumentSymbol(
                "InitialClass",
                "",
                VsCodeSymbolKind.Class,
                VsCodeRange(2, 0, 4, 1),
                VsCodeRange(2, 6, 2, 18),
                listOf(
                    VsCodeDocumentSymbol(
                        "initialMethod",
                        "",
                        VsCodeSymbolKind.Method,
                        VsCodeRange(3, 4, 3, 23),
                        VsCodeRange(3, 8, 3, 21),
                        listOf()
                    )
                )
            )
        )

        val addedDocumentSymbols = listOf(
            VsCodeDocumentSymbol(
                "AddedClass",
                "",
                VsCodeSymbolKind.Class,
                VsCodeRange(2, 0, 4, 1),
                VsCodeRange(2, 6, 2, 16),
                listOf(
                    VsCodeDocumentSymbol(
                        "addedMethod",
                        "",
                        VsCodeSymbolKind.Method,
                        VsCodeRange(3, 4, 3, 21),
                        VsCodeRange(3, 8, 3, 19),
                        listOf()
                    )
                )
            )
        )

        mockVsCodeResponses(mapOf(
            "tmp/initial.kt" to initialDocumentSymbols,
            "tmp/added.kt" to addedDocumentSymbols
        ))

        // Build initial graph
        val initialGraph: Graphlike = ParsingTestHelper.runParseGraphBuilderOnCode(getParser(), initialCode) { graph, _ ->
            // Verify the initial graph contains just the InitialClass
            assert(graph.nodes.values.any { it.id.endsWith(".InitialClass") })
            assert(graph.nodes.values.any { it.id.endsWith(".initialMethod") })
            assert(graph.nodes.values.none { it.id.endsWith(".AddedClass") })
            graph.build()
        }

        // Now update the graph by adding the new file
        val graphUpdater = getGraphUpdater(getParser())
        val updatedGraph = graphUpdater.addFiles(listOf("added.kt"), initialGraph)

        // Create reference graph with both files for comparison
        mockFileWalker(initialCode, addedCode)
        val referenceGraph = ParsingTestHelper.runParseGraphBuilderOnCode(getParser(), initialCode, addedCode) { graph, _ ->
            graph
        }

        // Verify the updated graph contains both classes and matches the reference graph
        assert(updatedGraph.nodes.values.any { it.id.endsWith(".InitialClass") })
        assert(updatedGraph.nodes.values.any { it.id.endsWith(".initialMethod") })
        assert(updatedGraph.nodes.values.any { it.id.endsWith(".AddedClass") })
        assert(updatedGraph.nodes.values.any { it.id.endsWith(".addedMethod") })
        
        // Compare node counts with reference graph
        assertEquals(referenceGraph.nodes.size, updatedGraph.nodes.size, "Updated graph should have same number of nodes as reference graph")
    }

    @Test
    fun testDeleteFiles() {
        // Create two files for initial graph
        val fileToKeep = ParsingTestHelper.CodeFile("keep.kt", """
            package test
            
            class KeepClass {
                fun keepMethod() {}
            }
        """.trimIndent())

        val fileToDelete = ParsingTestHelper.CodeFile("delete.kt", """
            package test
            
            class DeleteClass {
                fun deleteMethod() {}
            }
        """.trimIndent())

        // Add file content to our fake file handler
        fileHandler.addFile("$projectPath/keep.kt", fileToKeep.code)
        fileHandler.addFile("$projectPath/delete.kt", fileToDelete.code)

        // Mock file walker to return both files
        mockFileWalker(fileToKeep, fileToDelete)

        // Mock VSCode responses
        val keepClassSymbols = listOf(
            VsCodeDocumentSymbol(
                "KeepClass",
                "",
                VsCodeSymbolKind.Class,
                VsCodeRange(2, 0, 4, 1),
                VsCodeRange(2, 6, 2, 15),
                listOf(
                    VsCodeDocumentSymbol(
                        "keepMethod",
                        "",
                        VsCodeSymbolKind.Method,
                        VsCodeRange(3, 4, 3, 20),
                        VsCodeRange(3, 8, 3, 18),
                        listOf()
                    )
                )
            )
        )

        val deleteClassSymbols = listOf(
            VsCodeDocumentSymbol(
                "DeleteClass",
                "",
                VsCodeSymbolKind.Class,
                VsCodeRange(2, 0, 4, 1),
                VsCodeRange(2, 6, 2, 17),
                listOf(
                    VsCodeDocumentSymbol(
                        "deleteMethod",
                        "",
                        VsCodeSymbolKind.Method,
                        VsCodeRange(3, 4, 3, 22),
                        VsCodeRange(3, 8, 3, 20),
                        listOf()
                    )
                )
            )
        )

        mockVsCodeResponses(mapOf(
            "tmp/keep.kt" to keepClassSymbols,
            "tmp/delete.kt" to deleteClassSymbols
        ))

        // Build initial graph with both files
        val initialGraph = ParsingTestHelper.runParseGraphBuilderOnCode(getParser(), fileToKeep, fileToDelete) { graph, _ ->
            // Verify the initial graph contains both classes
            assert(graph.nodes.values.any { it.id.endsWith(".KeepClass") })
            assert(graph.nodes.values.any { it.id.endsWith(".DeleteClass") })
            graph.build()
        }

        // Now update the graph by deleting one file
        val graphUpdater = getGraphUpdater(getParser())
        val updatedGraph = graphUpdater.deleteFiles(listOf("delete.kt"), initialGraph)

        // Create reference graph with just the kept file for comparison
        mockFileWalker(fileToKeep)
        val referenceGraph = ParsingTestHelper.runParseGraphBuilderOnCode(getParser(), fileToKeep) { graph, _ ->
            graph
        }

        // Verify the deleted class is no longer in the graph
        assert(updatedGraph.nodes.values.any { it.id.endsWith(".KeepClass") })
        assert(updatedGraph.nodes.values.none { it.id.endsWith(".DeleteClass") })
        
        // Compare node counts with reference graph
        assertEquals(referenceGraph.nodes.size, updatedGraph.nodes.size, "Updated graph should have same number of nodes as reference graph")
    }

    @Test
    fun testReparseFiles() {
        // Create a file with initial content
        val initialCode = ParsingTestHelper.CodeFile("file.kt", """
            package test
            
            class TestClass {
                fun methodA() {}
            }
        """.trimIndent())

        // Define the updated content
        val updatedCode = ParsingTestHelper.CodeFile("file.kt", """
            package test
            
            class TestClass {
                fun methodA() {}
                fun methodB() {}
            }
        """.trimIndent())

        // Add initial file content to our fake file handler
        fileHandler.addFile("$projectPath/file.kt", initialCode.code)

        // Mock file walker to return our file
        mockFileWalker(initialCode)

        // Mock VSCode responses for initial and updated file versions
        val initialDocumentSymbols = listOf(
            VsCodeDocumentSymbol(
                "TestClass",
                "",
                VsCodeSymbolKind.Class,
                VsCodeRange(2, 0, 4, 1),
                VsCodeRange(2, 6, 2, 15),
                listOf(
                    VsCodeDocumentSymbol(
                        "methodA",
                        "",
                        VsCodeSymbolKind.Method,
                        VsCodeRange(3, 4, 3, 18),
                        VsCodeRange(3, 8, 3, 15),
                        listOf()
                    )
                )
            )
        )

        val updatedDocumentSymbols = listOf(
            VsCodeDocumentSymbol(
                "TestClass",
                "",
                VsCodeSymbolKind.Class,
                VsCodeRange(2, 0, 5, 1),
                VsCodeRange(2, 6, 2, 15),
                listOf(
                    VsCodeDocumentSymbol(
                        "methodA",
                        "",
                        VsCodeSymbolKind.Method,
                        VsCodeRange(3, 4, 3, 18),
                        VsCodeRange(3, 8, 3, 15),
                        listOf()
                    ),
                    VsCodeDocumentSymbol(
                        "methodB",
                        "",
                        VsCodeSymbolKind.Method,
                        VsCodeRange(4, 4, 4, 20),
                        VsCodeRange(4, 8, 4, 17),
                        listOf()
                    )
                )
            )
        )

        mockVsCodeResponses(mapOf(
            "tmp/file.kt" to initialDocumentSymbols
        ))

        // Build initial graph
        val initialGraph = ParsingTestHelper.runParseGraphBuilderOnCode(getParser(), initialCode) { graph, _ ->
            // Verify the initial graph contains TestClass with methodA
            assert(graph.nodes.values.any { it.id.endsWith(".TestClass") })
            assert(graph.nodes.values.any { it.id.endsWith(".methodA") })
            assert(graph.nodes.values.none { it.id.endsWith(".methodB") })
            graph.build()
        }

        // Update the mocked response for the updated file
        mockVsCodeResponses(mapOf(
            "tmp/file.kt" to updatedDocumentSymbols
        ))
        
        // Switch to updated file content
        fileHandler.addFile("$projectPath/file.kt", updatedCode.code)
        
        val graphUpdater = getGraphUpdater(getParser())
        val updatedGraph = graphUpdater.reparseFiles(listOf("$projectPath/file.kt"), initialGraph)

        // Create reference graph with the updated file for comparison
        mockFileWalker(updatedCode)
        val referenceGraph = ParsingTestHelper.runParseGraphBuilderOnCode(getParser(), updatedCode) { graph, _ ->
            graph
        }

        // Verify the updated graph contains both methods
        assert(updatedGraph.nodes.values.any { it.id.endsWith(".TestClass") })
        assert(updatedGraph.nodes.values.any { it.id.endsWith(".methodA") })
        assert(updatedGraph.nodes.values.any { it.id.endsWith(".methodB") })
        
        // Compare node counts with reference graph
        assertEquals(referenceGraph.nodes.size, updatedGraph.nodes.size, "Updated graph should have same number of nodes as reference graph")
    }

    @Test
    fun testUpdateGraph() {
        // Create initial files for a graph
        val file1 = ParsingTestHelper.CodeFile("file1.kt", """
            package test
            
            class File1Class {
                fun method1() {}
            }
        """.trimIndent())

        val file2 = ParsingTestHelper.CodeFile("file2.kt", """
            package test
            
            class File2Class {
                fun method2() {}
            }
        """.trimIndent())

        // Setup for an updated file
        val updatedFile2 = ParsingTestHelper.CodeFile("file2.kt", """
            package test
            
            class File2Class {
                fun method2() {}
                fun newMethod() {}
            }
        """.trimIndent())

        // Setup for a new file to add
        val file3 = ParsingTestHelper.CodeFile("file3.kt", """
            package test
            
            class File3Class {
                fun method3() {}
            }
        """.trimIndent())
        
        // Add file content to our fake file handler
        fileHandler.addFile("$projectPath/file1.kt", file1.code)
        fileHandler.addFile("$projectPath/file2.kt", file2.code)
        fileHandler.addFile("$projectPath/file3.kt", file3.code)

        // Mock file walker to return our files
        mockFileWalker(file1, file2)

        // Mock VSCode responses
        val file1Symbols = listOf(
            VsCodeDocumentSymbol(
                "File1Class",
                "",
                VsCodeSymbolKind.Class,
                VsCodeRange(2, 0, 4, 1),
                VsCodeRange(2, 6, 2, 16),
                listOf(
                    VsCodeDocumentSymbol(
                        "method1",
                        "",
                        VsCodeSymbolKind.Method,
                        VsCodeRange(3, 4, 3, 18),
                        VsCodeRange(3, 8, 3, 15),
                        listOf()
                    )
                )
            )
        )

        val file2Symbols = listOf(
            VsCodeDocumentSymbol(
                "File2Class",
                "",
                VsCodeSymbolKind.Class,
                VsCodeRange(2, 0, 4, 1),
                VsCodeRange(2, 6, 2, 16),
                listOf(
                    VsCodeDocumentSymbol(
                        "method2",
                        "",
                        VsCodeSymbolKind.Method,
                        VsCodeRange(3, 4, 3, 18),
                        VsCodeRange(3, 8, 3, 15),
                        listOf()
                    )
                )
            )
        )

        val updatedFile2Symbols = listOf(
            VsCodeDocumentSymbol(
                "File2Class",
                "",
                VsCodeSymbolKind.Class,
                VsCodeRange(2, 0, 5, 1),
                VsCodeRange(2, 6, 2, 16),
                listOf(
                    VsCodeDocumentSymbol(
                        "method2",
                        "",
                        VsCodeSymbolKind.Method,
                        VsCodeRange(3, 4, 3, 18),
                        VsCodeRange(3, 8, 3, 15),
                        listOf()
                    ),
                    VsCodeDocumentSymbol(
                        "newMethod",
                        "",
                        VsCodeSymbolKind.Method,
                        VsCodeRange(4, 4, 4, 20),
                        VsCodeRange(4, 8, 4, 17),
                        listOf()
                    )
                )
            )
        )

        val file3Symbols = listOf(
            VsCodeDocumentSymbol(
                "File3Class",
                "",
                VsCodeSymbolKind.Class,
                VsCodeRange(2, 0, 4, 1),
                VsCodeRange(2, 6, 2, 16),
                listOf(
                    VsCodeDocumentSymbol(
                        "method3",
                        "",
                        VsCodeSymbolKind.Method,
                        VsCodeRange(3, 4, 3, 18),
                        VsCodeRange(3, 8, 3, 15),
                        listOf()
                    )
                )
            )
        )

        mockVsCodeResponses(mapOf(
            "tmp/file1.kt" to file1Symbols,
            "tmp/file2.kt" to file2Symbols
        ))

        // Build initial graph
        val initialGraph = ParsingTestHelper.runParseGraphBuilderOnCode(getParser(), file1, file2) { graph, _ ->
            graph.build()
        }

        // Test file update scenario
        mockVsCodeResponses(mapOf(
            "tmp/file1.kt" to file1Symbols,
            "tmp/file2.kt" to updatedFile2Symbols
        ))
        
        // Switch to updated file2 content
        fileHandler.addFile("$projectPath/file2.kt", updatedFile2.code)
        
        val graphUpdater = getGraphUpdater(getParser())
        val updatedGraph = graphUpdater.reparseFiles(listOf("$projectPath/file2.kt"), initialGraph)

        // Verify the updated file is reflected in the graph
        assert(updatedGraph.nodes.values.any { it.id.endsWith(".newMethod") })

        // Test file add scenario
        mockVsCodeResponses(mapOf(
            "tmp/file1.kt" to file1Symbols,
            "tmp/file2.kt" to updatedFile2Symbols,
            "tmp/file3.kt" to file3Symbols
        ))

        // Update mocks for file add scenario
        mockFileWalker(file1, updatedFile2, file3)

        val graphWithAddedFile = graphUpdater.addFiles(listOf("$projectPath/file3.kt"), updatedGraph)

        // Verify the new file is added to the graph
        assert(graphWithAddedFile.nodes.values.any { it.id.endsWith(".File3Class") })
        assert(graphWithAddedFile.nodes.values.any { it.id.endsWith(".method3") })

        // Test file delete scenario
        mockVsCodeResponses(mapOf(
            "tmp/file1.kt" to file1Symbols,
            "tmp/file3.kt" to file3Symbols
        ))

        val graphWithDeletedFile = graphUpdater.deleteFiles(listOf("$projectPath/file2.kt"), graphWithAddedFile)

        // Verify the deleted file is removed from the graph
        assert(graphWithDeletedFile.nodes.values.any { it.id.endsWith(".File1Class") })
        assert(graphWithDeletedFile.nodes.values.any { it.id.endsWith(".File3Class") })
        assert(graphWithDeletedFile.nodes.values.none { it.id.endsWith(".File2Class") })
    }

    @Test
    fun testUpdateGraphPreservesDescriptions() {
        // Create a file with overloaded methods to test description preservation
        val initialCode = ParsingTestHelper.CodeFile("file.kt", """
            package test
            
            class TestClass {
                /**
                 * This is methodA with no parameters
                 */
                fun methodA() {}
                
                /**
                 * This is methodA with an integer parameter
                 */
                fun methodA(number: Int) {}
                
                /**
                 * This is methodA with a string parameter
                 */
                fun methodA(text: String) {}
            }
        """.trimIndent())

        // Add file content to our fake file handler
        fileHandler.addFile("$projectPath/file.kt", initialCode.code)
        
        // Setup VSCode document symbols for the initial file with overloaded methods
        val initialDocumentSymbols = listOf(
            VsCodeDocumentSymbol(
                "TestClass",
                "",
                VsCodeSymbolKind.Class,
                VsCodeRange(2, 0, 17, 1),
                VsCodeRange(2, 6, 2, 15),
                listOf(
                    VsCodeDocumentSymbol(
                        "methodA",
                        "",
                        VsCodeSymbolKind.Method,
                        VsCodeRange(6, 4, 6, 18),
                        VsCodeRange(6, 8, 6, 15),
                        listOf()
                    ),
                    VsCodeDocumentSymbol(
                        "methodA",
                        "",
                        VsCodeSymbolKind.Method,
                        VsCodeRange(11, 4, 11, 29),
                        VsCodeRange(11, 8, 11, 15),
                        listOf()
                    ),
                    VsCodeDocumentSymbol(
                        "methodA",
                        "",
                        VsCodeSymbolKind.Method,
                        VsCodeRange(16, 4, 16, 29),
                        VsCodeRange(16, 8, 16, 15),
                        listOf()
                    )
                )
            )
        )

        // Mock file walker to return our file
        mockFileWalker(initialCode)
        
        // Setup VSCode responses
        mockVsCodeResponses(mapOf(
            "tmp/file.kt" to initialDocumentSymbols
        ))
        
        // Build initial graph
        val initialGraph = ParsingTestHelper.runParseGraphBuilderOnCode(getParser(), initialCode) { graph, _ ->
            // Verify graph contains all three overloaded methods
            assert(graph.nodes.values.any { it.id.endsWith(".TestClass") })
            
            val methodNodes = graph.nodes.values.filter { it.id.contains(".methodA") }
            assertEquals(3, methodNodes.size, "Should have three overloaded methodA functions")
            
            graph.build()
        }
        
        // Add descriptions to nodes - we need to find the specific node IDs for each overloaded method
        // To make it more realistic, find the actual node IDs from the graph
        val classNode = initialGraph.nodes.values.first { it.id.endsWith(".TestClass") }
        val methodNodes = initialGraph.nodes.values.filter { it.id.contains(".methodA") }
        
        // Create a modified graph with descriptions 
        val graphWithDescriptions = Graph(
            nodes = initialGraph.nodes.mapValues { (id, node) ->
                when {
                    id == classNode.id -> 
                        node.copy(description = "A test class with overloaded methods")
                    id == methodNodes[0].id -> 
                        node.copy(description = "No parameter version")
                    id == methodNodes[1].id -> 
                        node.copy(description = "Integer parameter version")
                    id == methodNodes[2].id -> 
                        node.copy(description = "String parameter version")
                    else -> node
                }
            },
            connections = initialGraph.connections
        )
        
        // Now, create an updated file that modifies one method and adds a new one
        val updatedCode = ParsingTestHelper.CodeFile("file.kt", """
            package test
            
            class TestClass {
            
                /**
                 * This is methodA with a string parameter
                 */
                fun methodA(text: String) {
                }
                
                /**
                 * This is methodA with no parameters
                 */
                fun methodA() {}
                
                /**
                 * This is methodA with an integer parameter
                 * Now with a modified comment
                 */
                fun methodA(number: Int) {
                 //Changed
                }
                
                
                /**
                 * This is a new method
                 */
                fun methodB() {}
            }
        """.trimIndent())
        
        // Updated document symbols including the new method
        val updatedDocumentSymbols = listOf(
            VsCodeDocumentSymbol(
                "TestClass",
                "",
                VsCodeSymbolKind.Class,
                VsCodeRange(2, 0, 14, 1),
                VsCodeRange(2, 6, 2, 15),
                listOf(
                    VsCodeDocumentSymbol(
                        "methodA",
                        "",
                        VsCodeSymbolKind.Method,
                        VsCodeRange(7, 4, 8, 5),
                        VsCodeRange(7, 8, 7, 15),
                        listOf()
                    ),
                    VsCodeDocumentSymbol(
                        "methodA",
                        "",
                        VsCodeSymbolKind.Method,
                        VsCodeRange(13, 4, 13, 18),
                        VsCodeRange(13, 8, 13, 15),
                        listOf()
                    ),
                    VsCodeDocumentSymbol(
                        "methodA",
                        "",
                        VsCodeSymbolKind.Method,
                        VsCodeRange(19, 4, 21, 5),
                        VsCodeRange(19, 8, 19, 15),
                        listOf()
                    ),
                    VsCodeDocumentSymbol(
                        "methodB",
                        "",
                        VsCodeSymbolKind.Method,
                        VsCodeRange(26, 4, 26, 18),
                        VsCodeRange(26, 8, 26, 15),
                        listOf()
                    )
                )
            )
        )
        
        // Update file in file handler
        fileHandler.addFile("$projectPath/file.kt", updatedCode.code)
        
        // Update VSCode response for the updated file
        mockVsCodeResponses(mapOf(
            "tmp/file.kt" to updatedDocumentSymbols
        ))
        
        // Now update the graph
        val graphUpdater = getGraphUpdater(getParser())
        val updatedGraph = graphUpdater.reparseFiles(listOf("$projectPath/file.kt"), graphWithDescriptions)
        
        // Verify the result:
        // 1. All three original methods should still be there with their descriptions intact
        // 2. The new method should be added
        // 3. The class node's description should be preserved
        
        // Find nodes in the updated graph
        val updatedClassNode = updatedGraph.nodes.values.first { it.id.endsWith(".TestClass") }
        val updatedMethodNodes = updatedGraph.nodes.values.filter { it.id.contains(".methodA") }
        val newMethodNode = updatedGraph.nodes.values.first { it.id.endsWith(".methodB") }
        
        // Verify class description is preserved
        assertEquals("[OUTDATED]\nA test class with overloaded methods", updatedClassNode.description,
                     "Class description should be preserved")
        
        // Verify all three methodA nodes still exist with preserved descriptions
        assertEquals(3, updatedMethodNodes.size, "Should still have three overloaded methodA functions")
        
        // Check that each overloaded method has its description preserved
        // Note: We need to match by signature since the order might change
        
        // No parameter version
        val noParamMethod = updatedMethodNodes.first {
            fileHandler.readString(it.filePath, it.codeLocation).contains("()")
        }
        assertEquals("No parameter version", noParamMethod.description, 
                     "No parameter method description should be preserved")
        
        // Integer parameter version
        val intParamMethod = updatedMethodNodes.first { fileHandler.readString(it.filePath, it.codeLocation).contains("Int") }
        assertEquals("[OUTDATED]\nInteger parameter version", intParamMethod.description,
                     "Integer parameter method description should be preserved")
        
        // String parameter version
        val stringParamMethod = updatedMethodNodes.first { fileHandler.readString(it.filePath, it.codeLocation).contains("String") }
        assertEquals("[OUTDATED]\nString parameter version", stringParamMethod.description,
                     "String parameter method description should be preserved")
        
        // New method should exist but have no description yet
        assertNotNull(newMethodNode, "New method should be in the graph")
        assertEquals("", newMethodNode.description, "New method should have empty description")
    }
}
