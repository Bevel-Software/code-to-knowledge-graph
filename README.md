<p align="center">
  <!-- TODO: INSERT COOL LOGO HERE -->
  <img src="https://i.imgur.com/4VnBgXz.png" alt="Code-to-Knowledge-Graph Logo" width="200"/>
  <h1 align="center">Code-to-Knowledge-Graph ✨</h1>
</p>

<p align="center">
  <strong>Unlock the hidden structure and insights within your codebase by transforming it into a powerful, queryable knowledge graph!</strong>
</p>

<p align="center">
  <!-- Badges: Replace placeholders with actual URLs if you set them up -->
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MPL%202.0-brightgreen.svg" alt="License"></a>
  <a href="https://github.com/YOUR_USERNAME/code-to-knowledge-graph/stargazers"><img src="https://img.shields.io/github/stars/YOUR_USERNAME/code-to-knowledge-graph.svg?style=social&label=Star&maxAge=2592000" alt="GitHub Stars"></a>
  <!-- Add build status badge if you have CI/CD -->
  <!-- <a href="YOUR_CI_CD_LINK"><img src="YOUR_BUILD_STATUS_BADGE_URL" alt="Build Status"></a> -->
</p>

---

This project is your gateway to transforming complex source code into structured, queryable knowledge graphs. By leveraging semantic analysis (primarily via VS Code Language Server capabilities and historically through ANTLR), we extract meaningful information about your code's entities, relationships, and architecture. Dive deep into your codebase like never before!

## 🚀 Key Features

*   **Knowledge Graph Generation:** Converts source code from various languages into a rich graph structure.
*   **VS Code Integration:** Primarily utilizes VS Code's powerful language servers for parsing and symbol extraction, providing broad language support out-of-the-box.
*   **Language Agnostic (via VS Code):** The VS Code module aims to support any language for which a good Language Server Protocol (LSP) implementation exists.
*   **Detailed Symbol & Relationship Extraction:** Captures classes, functions, variables, calls, inheritance, implementations, and more.
*   **File System Awareness:** Includes tools for intelligently walking file trees, respecting `.gitignore` patterns.
*   **MinHashing for Similarity:** Implements MinHash for locality-sensitive hashing, useful for detecting near-duplicate code snippets or tracking semantic drift.
*   **(Historical) ANTLR-based Parsing & Querying:** Features a sophisticated, though now **deprecated**, ANTLR-based parsing pipeline with a custom AST Query Language (`bevel_ast_ql`) for fine-grained code analysis.
*   **Codebase Combiner Utility:** Handy scripts to bundle your entire project (or a summary) into a single text file for easy sharing or LLM analysis.

## 🤔 Why Code-to-Knowledge-Graph?

Understanding large, evolving codebases is a monumental task. This project aims to alleviate that by:

*   **Deep Code Understanding:** Visualize and query relationships between components.
*   **Impact Analysis:** Understand the ripple effects of changes.
*   **Architectural Insights:** Discover design patterns, dependencies, and potential issues.
*   **Foundation for Custom Tooling:** Build linters, documentation generators, or advanced refactoring tools on top of the graph.
*   **AI & LLM Augmentation:** Provide rich, structured context about code to Large Language Models.

## 🛠️ Core Modules

The project is organized into several key modules:

1.  **`vscode/` (Primary & Active):**
    *   Interfaces with VS Code's language services to extract symbols, references, and definitions.
    *   `VsCodeParser.kt`: Orchestrates symbol extraction from files.
    *   `VsCodeConnectionParser.kt`: Infers connections (calls, inheritance, etc.) from symbols.
    *   `languageSpecs/`: Contains language-specific configurations and heuristics to refine VS Code's output.
    *   This is the **recommended and actively developed** approach for parsing.

2.  **`antlr/` (⚠️ Largely Deprecated & Non-Functional):**
    *   Contains ANTLR grammars for various languages (Kotlin, C#, JavaScript).
    *   Includes a custom AST Query Language (`bevel_ast_ql/`) for defining patterns to extract nodes and relationships from ANTLR parse trees.
    *   `QueryBasedAntlrParser.kt` & `ConverterBasedAntlrParser.kt`: Historical parsers using this system.
    *   **Important:** This module underwent a large-scale refactor and is **no longer functional in its current state.** It's preserved for its valuable query language design and potential future reintegration if specific deep-parsing needs arise that VS Code LSP cannot fulfill. The documentation in `docs/` primarily refers to this deprecated system.

3.  **`providers/`:**
    *   `GitignoreAwareFileWalker.kt`: Efficiently traverses project directories, respecting `.gitignore`.
    *   `MinHasher.kt`: Implements MinHash for code similarity analysis.

4.  **`regex/` (⚠️ Deprecated):**
    *   Contains older, regex-based parsers for specific frameworks (e.g., AngularJS). Not actively maintained.

5.  **Root-level Scripts (`combine_*.sh`, `combine_files.py`):**
    *   Utilities to package the codebase itself into a single text file. Useful for providing context to LLMs or for archiving.

## ✨ How It Works (High-Level - VS Code Path)

1.  **File Discovery:** The `GitignoreAwareFileWalker` scans the target project for relevant source files.
2.  **Symbol Extraction (via VS Code):** For each file, the `VsCodeParser` communicates with VS Code (or a compatible LSP client) to request document symbols, workspace symbols, definitions, and references.
3.  **Graph Node Creation:** Extracted symbols (classes, functions, variables, etc.) are transformed into nodes in the knowledge graph.
4.  **Relationship Inference:** The `VsCodeConnectionParser` analyzes the symbol information (e.g., call hierarchies, type definitions, inheritance) to create connections (edges) between nodes.
5.  **Graph Augmentation & Refinement:** Language-specific logic in `vscode/languageSpecs/` can further refine the graph, adding more detailed connections or node properties.
6.  **(Optional) Hashing:** `MinHasher` can be used to generate semantic fingerprints of code blocks or files.

<!-- TODO: INSERT GIF SHOWING CODE BEING PARSED AND GRAPH VISUALIZATION -->
<!-- <p align="center">
  <img src="docs/assets/how-it-works-vscode.gif" alt="Code to Knowledge Graph in Action (VS Code Path)"/>
</p> -->

## ⚙️ Getting Started

### Prerequisites

*   Java Development Kit (JDK) 17 or higher
*   Gradle (the project uses the Gradle wrapper, so it will be downloaded automatically)
*   (For `vscode` module functionality) A running instance of VS Code or a compatible LSP server setup that the tool can communicate with (details depend on the specific runner implementation).
*   Python 3 for the `combine_*.sh` scripts (`pip3 install -r requirements.txt`).

### Installation & Build

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/YOUR_USERNAME/code-to-knowledge-graph.git
    cd code-to-knowledge-graph
    ```

2.  **Build the project using Gradle:**
    ```bash
    ./gradlew build
    ```
    This will compile the Kotlin/Java code, run tests, and produce necessary artifacts.

## 🚀 Usage

The primary way to use the knowledge graph generation capabilities is programmatically by integrating the `vscode` module's parsers into your own applications or analysis scripts.

```kotlin
// Example (Conceptual - actual API may vary)
// import software.bevel.code_to_knowledge_graph.vscode.VsCodeParser
// import software.bevel.code_to_knowledge_graph.vscode.VsCodeConnectionParser
// ...

// val projectPath = "/path/to/your/codebase"
// val vsCodeParser = VsCodeParser(...)
// val connectionParser = VsCodeConnectionParser(...)

// val initialGraph = vsCodeParser.parseProject(projectPath)
// val finalGraph = connectionParser.enhanceGraph(initialGraph)

// Now you have 'finalGraph' to work with!
```

For development and testing the `vscode` module, you'll typically need a way to simulate or directly interact with VS Code's LSP. The `BatchProcessor` and related classes in `vscode/src/main/kotlin/` hint at a mechanism for this.

### ⚠️ Important Note on the ANTLR Module

As mentioned, the `antlr/` module and its associated `bevel_ast_ql` query language are **currently deprecated and non-functional** due to a major refactor. The project's primary focus for parsing is now the `vscode/` module.

The documentation found in:
*   `docs/queries.md`
*   `docs/programming_constructs.md`

...describes how to use the `bevel_ast_ql` system. While this system is not active, the concepts and query examples might still provide valuable insights into thinking about code-to-graph transformations, should you wish to explore or revive parts of that system.

<!-- TODO: INSERT GIF SHOWING A QUERY ON THE GENERATED GRAPH -->
![Querying the Knowledge Graph](https://i.imgur.com/XhPzIGP.gif)


### Codebase Combiner Utility

This project also includes a handy set of scripts to bundle your (or any) codebase into a single text file, respecting `.gitignore`. This is excellent for feeding context to LLMs.

*   **Full Combine (respects .gitignore):**
    ```bash
    ./combine_codebase.sh path/to/your/project output_combined.txt
    ```
*   **Compact Combine (first 50 lines per file, respects .gitignore):**
    ```bash
    ./combine_compact.sh path/to/your/project output_compact.txt
    ```
*   **Summary View (file structure + first 10 lines per file, respects .gitignore):**
    ```bash
    ./combine_summary.sh path/to/your/project output_summary.txt
    ```
    (Requires Python dependencies: `pip3 install -r requirements.txt`)

## 🤝 Contributing

Contributions are welcome! Whether it's improving the VS Code integration, adding new language-specific handlers, enhancing the graph model, or fixing bugs, your help is appreciated.

1.  Fork the repository.
2.  Create your feature branch (`git checkout -b feature/AmazingFeature`).
3.  Commit your changes (`git commit -m 'Add some AmazingFeature'`).
4.  Push to the branch (`git push origin feature/AmazingFeature`).
5.  Open a Pull Request.

Please make sure your code adheres to the existing style and that all tests pass.

## 📜 License

This project is licensed under the **Mozilla Public License Version 2.0**. See the `LICENSE` file for details.
The `NOTICE` file contains information about licenses of third-party dependencies.

## 🙏 Acknowledgements

*   The **ANTLR** project for their powerful parser generator (though our ANTLR module is currently deprecated).
*   The **Dynatrace hash4j** library for MinHash implementation.
*   The broader **Language Server Protocol (LSP)** community for enabling robust cross-editor language intelligence.
*   All contributors and users of this project.

---

<p align="center">
  Happy Coding & Graphing! 🧑‍💻➡️📊
</p>
