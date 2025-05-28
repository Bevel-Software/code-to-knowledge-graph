import org.slf4j.Logger
import org.slf4j.LoggerFactory
import software.bevel.code_to_knowledge_graph.providers.GitignoreAwareFileWalker
import software.bevel.code_to_knowledge_graph.providers.MinHasher
import software.bevel.code_to_knowledge_graph.vscode.VsCodeConnectionParser
import software.bevel.code_to_knowledge_graph.vscode.VsCodeGraphUpdater
import software.bevel.code_to_knowledge_graph.vscode.VsCodeParser
import software.bevel.code_to_knowledge_graph.vscode.data.VsCodeNodeBuilder
import software.bevel.code_to_knowledge_graph.vscode.languageSpecs.GeneralLanguageSpecification
import software.bevel.code_to_knowledge_graph.vscode.languageSpecs.VsCodeLanguageSpecification
import software.bevel.file_system_domain.BevelFilesPathResolver
import software.bevel.file_system_domain.FileWalker
import software.bevel.code_to_knowledge_graph.providers.CachedIoFileHandler
import software.bevel.file_system_domain.services.FileHandler
import software.bevel.file_system_domain.web.LocalCommunicationInterface
import software.bevel.graph_domain.GraphMergingService
import software.bevel.code_to_knowledge_graph.providers.GraphMergingServiceImpl
import software.bevel.graph_domain.parsing.IntermediateFileParser
import software.bevel.code_to_knowledge_graph.providers.RegexIdentifierTokenizer
import software.bevel.networking.RestCommunicationInterfaceCreator
import software.bevel.networking.web_client.JavaNetReactorMonoWebClient
import kotlin.io.path.pathString

/**
 * Creates a [VsCodeParser] instance for parsing VSCode project files.
 *
 * This factory function sets up a VsCodeParser with the specified parameters or uses default implementations
 * where not provided. It handles setting up communication with the VSCode extension via a REST interface
 * by either using a provided communication channel or creating one using the port from the Bevel port file.
 *
 * @param projectPath The path to the VSCode project to be parsed
 * @param commsChannel Optional communication interface for interacting with VSCode extension; if null, one will be created
 * @param fileHandler File handling service for reading and writing files; defaults to [CachedIoFileHandler]
 * @param languageSpecification Language-specific parsing rules; defaults to [GeneralLanguageSpecification]
 * @param fileWalker Service for traversing the file system; defaults to [GitignoreAwareFileWalker]
 * @param logger Logger for capturing parse events and errors; defaults to a logger for VsCodeParser class
 * @param connectionVersion The version string for the VSCode connection protocol; defaults to "1.0.0"
 * @return A configured VsCodeParser instance ready to parse the specified project
 */
fun createVsCodeParser(
    projectPath: String,
    commsChannel: LocalCommunicationInterface? = null,
    fileHandler: FileHandler = CachedIoFileHandler(),
    languageSpecification: VsCodeLanguageSpecification = GeneralLanguageSpecification(
        fileHandler,
        VsCodeNodeBuilder(
            fileHandler, MinHasher()
        ),
        RegexIdentifierTokenizer(),
        null,
    ),
    fileWalker: FileWalker = GitignoreAwareFileWalker(languageSpecification, fileHandler),
    logger: Logger = LoggerFactory.getLogger(VsCodeParser::class.java),
    connectionVersion: String = "1.0.0"
): VsCodeParser {
    val communicationChannel = if(commsChannel != null) {
        commsChannel
    }
    else {
        val port = fileHandler.readString(BevelFilesPathResolver.bevelPortFilePath(projectPath).pathString).trim()
        RestCommunicationInterfaceCreator(JavaNetReactorMonoWebClient(), port).create()
    }
    return VsCodeParser(
        projectPath, communicationChannel, fileHandler, languageSpecification, fileWalker, logger, connectionVersion
    )
}

/**
 * Creates a [VsCodeGraphUpdater] instance for updating knowledge graphs from VSCode projects.
 *
 * This factory function configures a VsCodeGraphUpdater that integrates VSCode parsing with 
 * graph merging capabilities, allowing for incremental updates to knowledge graphs based on
 * code changes in a VSCode project.
 *
 * @param projectPath The path to the VSCode project to be processed
 * @param vsCodeParser Parser for processing VSCode project files; defaults to a new instance created with [createVsCodeParser]
 * @param graphMergingService Service for merging parsed data into a knowledge graph; defaults to [GraphMergingServiceImpl]
 * @return A configured VsCodeGraphUpdater instance ready to update knowledge graphs
 */
fun createVsCodeGraphUpdater(
    projectPath: String,
    vsCodeParser: IntermediateFileParser = createVsCodeParser(projectPath),
    graphMergingService: GraphMergingService = GraphMergingServiceImpl(MinHasher(), CachedIoFileHandler()),
): VsCodeGraphUpdater {
    return VsCodeGraphUpdater(projectPath, vsCodeParser, graphMergingService)
}

/**
 * Creates a [VsCodeConnectionParser] instance for parsing VSCode connections and dependencies.
 *
 * This factory function sets up a VsCodeConnectionParser which specializes in analyzing connections
 * between code elements in a VSCode project. It establishes communication with the VSCode extension
 * either through a provided channel or by creating one using the port from the Bevel port file.
 *
 * @param projectPath The path to the VSCode project to be analyzed
 * @param commsChannel Optional communication interface for interacting with VSCode extension; if null, one will be created
 * @param fileHandler File handling service for reading and writing files; defaults to [CachedIoFileHandler]
 * @param languageSpecification Language-specific parsing rules; defaults to [GeneralLanguageSpecification]
 * @param logger Logger for capturing parse events and errors; defaults to a logger for VsCodeConnectionParser class
 * @return A configured VsCodeConnectionParser instance ready to parse connections in the specified project
 */
fun createVsCodeConnectionParser(
    projectPath: String,
    commsChannel: LocalCommunicationInterface? = null,
    fileHandler: FileHandler = CachedIoFileHandler(),
    languageSpecification: VsCodeLanguageSpecification = GeneralLanguageSpecification(
        fileHandler,
        VsCodeNodeBuilder(
            fileHandler, MinHasher()
        ),
        RegexIdentifierTokenizer(),
        null,
    ),
    logger: Logger = LoggerFactory.getLogger(VsCodeConnectionParser::class.java)
): VsCodeConnectionParser {
    val communicationChannel = if(commsChannel != null) {
        commsChannel
    }
    else {
        val port = fileHandler.readString(BevelFilesPathResolver.bevelPortFilePath(projectPath).pathString).trim()
        RestCommunicationInterfaceCreator(JavaNetReactorMonoWebClient(), port).create()
    }
    return VsCodeConnectionParser(projectPath, communicationChannel, fileHandler, languageSpecification, logger)
}