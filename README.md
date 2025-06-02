<p align="center">
  <img src="https://i.imgur.com/4VnBgXz.png" alt="Code-to-Knowledge-Graph Logo" width="200"/>
  <h1 align="center">Code-to-Knowledge-Graph ✨</h1>
</p>

<p align="center">
  <strong>Unlock the hidden structure and insights within your codebase by transforming it into a powerful, queryable knowledge graph!</strong>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MPL%202.0-brightgreen.svg" alt="License"></a>
  <a href="https://search.maven.org/artifact/software.bevel/code-to-knowledge-graph"><img src="https://img.shields.io/maven-central/v/software.bevel/code-to-knowledge-graph.svg?label=Maven%20Central" alt="Maven Central"></a>
  <a href="https://github.com/YOUR_USERNAME/code-to-knowledge-graph/stargazers"><img src="https://img.shields.io/github/stars/YOUR_USERNAME/code-to-knowledge-graph.svg?style=social&label=Star&maxAge=2592000" alt="GitHub Stars"></a>
  <!-- Add build status badge if you have CI/CD: <a href="YOUR_CI_CD_LINK"><img src="YOUR_BUILD_STATUS_BADGE_URL" alt="Build Status"></a> -->
</p>

---

**Code-to-Knowledge-Graph** is a sophisticated Kotlin/JVM toolkit designed to parse complex source code and transform it into a rich, structured, and queryable knowledge graph. Its primary approach leverages **VS Code's Language Server Protocol (LSP)** capabilities to extract meaningful information about your code's entities (classes, functions, variables), their relationships (calls, inheritance, usage), and overall architecture.

This empowers developers and teams to:
*   🔍 **Deeply Understand Code**: Visualize and query intricate relationships between components.
*   📈 **Analyze Impact**: Understand the ripple effects of changes across the codebase.
*   🏛️ **Gain Architectural Insights**: Discover design patterns, dependencies, and potential refactoring opportunities.
*   🛠️ **Build Custom Tooling**: Create linters, documentation generators, or advanced refactoring tools on top of the graph.
*   🤖 **Augment AI & LLMs**: Provide rich, structured context about your code to Large Language Models for enhanced analysis and code generation.

## 🤔 Why Code-to-Knowledge-Graph?

Navigating and understanding large, evolving codebases is a monumental challenge. This project simplifies this by:
*   **Providing a structured, queryable representation of your code.**
*   **Enabling enhanced code comprehension** beyond simple text search.
*   **Laying the foundation for smarter development tools** and data-driven architectural decisions.
*   **Bridging your code with AI** by offering deep structural context to LLMs.

## ✨ Key Features

*   **Primary Approach - VS Code LSP Integration (`vscode/` module)**:
    *   Utilizes VS Code's language servers for robust parsing and symbol extraction.
    *   Aims for broad language support wherever a quality LSP implementation exists.
    *   Extracts symbols, definitions, references, and type hierarchies.
    *   Supports incremental graph updates based on file changes.
*   **Simplified Instantiation**: The `Factories.kt` file provides easy-to-use factory methods to get started quickly.
*   **Comprehensive Graph Model (`graph-domain` dependency)**:
    *   Leverages `software.bevel:graph-domain` for defining nodes (classes, functions, etc.) and connections (defines, inherits, calls, uses, etc.).
*   **File System Awareness (`file-system-domain` dependency & `providers/` module)**:
    *   Intelligent file tree walking, respecting `.gitignore` patterns.
    *   Abstracted file handling.
*   **Code Similarity Detection (`providers/` module)**:
    *   MinHashing (`MinHasher` using `hash4j`) for locality-sensitive hashing.
*   **Historical ANTLR-Powered Parsing (`antlr/` module)**:
    *   For users needing deep, grammar-specific analysis or working with languages where LSP support is unavailable/insufficient, this module offers an ANTLR-based pipeline with a custom AST Query Language (`bevel_ast_ql`). (See [Querying Guide](./docs/queries.md)). This is considered an advanced/historical feature.

## ⚙️ Core Modules & Architecture

*   **`vscode/` (Primary & Active)**: Handles all interaction with VS Code Language Servers for parsing and relationship discovery. This is the recommended module for most use cases.
*   **`providers/` (Shared Utilities)**: Contains common implementations like `GitignoreAwareFileWalker`, `MinHasher`, and file handlers.
*   **`antlr/` (Advanced/Historical)**: ANTLR-based parsing engine and custom AST query language.

**Key External Bevel Dependencies:**
This project integrates deeply with other Bevel libraries:
*   **`software.bevel:file-system-domain`**: Provides foundational interfaces for file system operations, path resolution, and text locations (e.g., `.bevel` project structure, `LCRange`).
*   **`software.bevel:graph-domain`**: Defines the core graph model (`Node`, `Connection`), graph construction tools (`GraphBuilder`), and parsing interfaces.
*   **`software.bevel:networking`**: Used by the `vscode` module (often via `Factories.kt`) to establish communication with the VS Code extension (e.g., `RestCommunicationInterface`).

## 📦 Installation

The library is available on Maven Central.

### Prerequisites
*   Java Development Kit (JDK) 17 or higher.
*   **A running VS Code instance with a compatible Bevel Language Server extension.** The `VsCodeParser` communicates with this extension to get code intelligence. The extension must expose an API (typically HTTP) that `LocalCommunicationInterface` (from `file-system-domain`, often implemented by `RestCommunicationInterface` from `networking`) can connect to. The default factory in `Factories.kt` will attempt to discover the port from Bevel's standard configuration files (e.g., in the `.bevel` directory of your project).

### Adding Dependencies

You'll need to add `code-to-knowledge-graph`:

**Gradle (Kotlin DSL - `build.gradle.kts`):**
```kotlin
dependencies {
    implementation("software.bevel:code-to-knowledge-graph:1.1.3") // Use the latest version
    // SLF4J implementation (e.g., Logback) for logging
    implementation("ch.qos.logback:logback-classic:1.4.14")
}
```

**Gradle (Groovy DSL - `build.gradle`):**
```groovy
dependencies {
    implementation 'software.bevel:code-to-knowledge-graph:1.1.3' // Use the latest version
    // SLF4J implementation (e.g., Logback) for logging
    implementation 'ch.qos.logback:logback-classic:1.4.14'
}
```

**Maven (`pom.xml`):**
```xml
<dependencies>
    <dependency>
        <groupId>software.bevel</groupId>
        <artifactId>code-to-knowledge-graph</artifactId>
        <version>1.1.3</version> <!-- Use the latest version -->
    </dependency>
    <!-- SLF4J implementation (e.g., Logback) for logging -->
    <dependency>
        <groupId>ch.qos.logback</groupId>
        <artifactId>logback-classic</artifactId>
        <version>1.4.14</version>
    </dependency>
</dependencies>
```
*(Always check Maven Central for the latest versions of all `software.bevel` artifacts.)*

## 🚀 Quick Start & Usage

Generating a knowledge graph is designed to be straightforward using the provided factory methods.

**The easiest way to get started is with `FactoriesKt.createVsCodeParser()` (from Java) or `createVsCodeParser()` (from Kotlin).**

**Example (Java):**
```java
import software.bevel.code_to_knowledge_graph.FactoriesKt;
import software.bevel.graph_domain.graph.Graphlike;
import software.bevel.graph_domain.parsing.Parser;

import java.util.ArrayList;
import java.util.List;

public class TestGraph {

    public static void main(String[] args) {
        // Ensure VS Code is running with the Bevel Language Server extension,
        // and it's configured for the project at this path.
        String projectPath = "C:\\Path\\To\\Your\\Project"; // Replace with your project path

        // The factory creates a parser configured to talk to your VS Code extension.
        Parser parser = FactoriesKt.createVsCodeParser(projectPath);

        List<String> projectsToParse = new ArrayList<>();
        projectsToParse.add(projectPath); // Add the root project path

        // This initiates parsing of symbols and then connections.
        Graphlike graph = parser.parse(projectsToParse);

        System.out.println("Graph generation complete!");
        System.out.println("Total Nodes: " + graph.getNodes().size());
        System.out.println("Total Connections: " + graph.getConnections().getAllConnections().size());
        // You can now explore the 'graph' object
        // For example, print details of each node:
        // graph.getNodes().values().forEach(node -> {
        //     System.out.println("Node: " + node.getId() + " (Type: " + node.getNodeType() + ")");
        // });
    }
}
```

**Explanation:**

1.  **`FactoriesKt.createVsCodeParser(projectPath)`**: This is the key. It instantiates a `VsCodeParser` with sensible defaults.
    *   It automatically sets up the `LocalCommunicationInterface` (usually a `RestCommunicationInterface`) to talk to your VS Code Bevel extension. It attempts to find the communication port from Bevel's project configuration files (typically within the `.bevel` directory of your `projectPath`).
    *   It also initializes other necessary components like `FileHandler`, `LanguageSpecification`, etc.
2.  **`parser.parse(projectsToParse)`**: This method performs a two-pass process:
    *   **Pass 1 (Symbol Extraction)**: Uses `VsCodeParser` to walk the file tree (respecting `.gitignore`), query the VS Code extension for document symbols in each relevant file, and build an initial graph of nodes.
    *   **Pass 2 (Connection Discovery)**: Uses `VsCodeConnectionParser` to query the VS Code extension for references, definitions, and implementations for the initially discovered symbols, thereby establishing connections (calls, inheritance, usage, etc.) between them.
3.  The `Graphlike` object returned contains all the nodes and connections representing your codebase.

**Important Considerations for VS Code Parser:**
*   **VS Code Extension**: You MUST have a companion Bevel VS Code extension running and configured for your target project. This extension provides the LSP services that `code-to-knowledge-graph` consumes.
*   **Project Indexing**: For best results, ensure VS Code has fully indexed your project. The quality of the graph depends on the accuracy of the LSP information.
*   **Performance**: Parsing large projects can take time, as it involves numerous requests to the VS Code extension.

### Advanced Customization

If you need more control over the parsing process (e.g., custom file handlers, specific language configurations, or a pre-configured communication channel), you can use the more detailed constructor of `VsCodeParser` or the other parameters in `createVsCodeParser` found in `src/main/kotlin/Factories.kt`.

## 🏗️ Building from Source (for Development/Contribution)

If you wish to contribute to `code-to-knowledge-graph` or build it locally:

### Prerequisites
*   JDK 17 or higher

### Build Steps
1.  **Clone the repository**:
    ```bash
    git clone https://github.com/YOUR_USERNAME/code-to-knowledge-graph.git
    cd code-to-knowledge-graph
    ```
2.  **Build using Gradle**:
    The project includes a Gradle wrapper (`gradlew`), which will download the correct Gradle version.
    ```bash
    ./gradlew build
    ```
    This command will compile all modules, run tests, and produce necessary artifacts.

## 🤝 Contributing

Contributions are welcome! Whether it's bug fixes, feature enhancements, or improvements to documentation, your help is appreciated.

1.  **Fork the repository.**
2.  **Create a new branch** for your feature or fix.
3.  **Make your changes.** Adhere to Kotlin coding conventions.
4.  **Add tests** for any new functionality or bug fixes. Ensure all tests pass: `./gradlew test`.
5.  **Commit your changes** with clear, descriptive messages.
6.  **Push to your branch.**
7.  **Open a Pull Request** against the `main` branch of the original repository.

Please ensure your PR description clearly explains the changes and their motivations.

## 📄 License

This project is licensed under the **Mozilla Public License Version 2.0**. See the [LICENSE](./LICENSE) file for full details.

The `NOTICE` file contains information about licenses of third-party dependencies.

---

We hope Code-to-Knowledge-Graph provides a powerful foundation for your code analysis endeavors!
