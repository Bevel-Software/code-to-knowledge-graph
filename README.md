<p align="center">
  <img src="https://i.imgur.com/4VnBgXz.png" alt="Code-to-Knowledge-Graph Logo" width="200"/>
  <h1 align="center">Code-to-Knowledge-Graph ✨</h1>
</p>

<p align="center">
  <strong>Transform your codebase into a powerful, queryable knowledge graph!</strong>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MPL%202.0-brightgreen.svg" alt="License"></a>
  <a href="https://github.com/Bevel-Software/code-to-knowledge-graph/stargazers"><img src="https://img.shields.io/github/stars/Bevel-Software/code-to-knowledge-graph.svg?style=social&label=Star&maxAge=2592000" alt="GitHub Stars"></a>
</p>

---

**Code-to-Knowledge-Graph** is a Kotlin/JVM toolkit that parses source code and creates rich, queryable knowledge graphs using **VS Code's Language Server Protocol (LSP)**. Extract entities, relationships, and architecture insights from any codebase.

🔍 **Understand Code Structure** • 📈 **Analyze Impact** • 🏛️ **Gain Architectural Insights** • 🛠️ **Build Custom Tools** • 🤖 **Augment AI & LLMs**

## 🚀 Quick Start

### Option 1: Use Pre-built VS Code Extensions (Recommended)

1. **Install [Bevel Extension](https://marketplace.visualstudio.com/items?itemName=bevel-software.bevel)** from VS Code marketplace
2. **Open your codebase** and run `Bevel: Re-/Analyze Project` 
3. **Explore with additional tools:**
   - [Bevel Neo4j Visualization](https://github.com/Bevel-Software/bevel-neo4j-viz) for graph exploration
   - [Bevel Test Generator](https://marketplace.visualstudio.com/items?itemName=bevel-software.bevel-test-generator) for AI-assisted testing

### Option 2: Direct Integration

**Gradle:**
```kotlin
dependencies {
    implementation("software.bevel:code-to-knowledge-graph:1.1.3")
}
```

**Basic Usage:**
```java
import software.bevel.code_to_knowledge_graph.FactoriesKt;

String projectPath = "/path/to/your/project";
Parser parser = FactoriesKt.createVsCodeParser(projectPath);
Graphlike graph = parser.parse(List.of(projectPath));

System.out.println("Nodes: " + graph.getNodes().size());
System.out.println("Connections: " + graph.getConnections().getAllConnections().size());
```

## 🎯 Ecosystem & Use Cases

Transform your knowledge graph into powerful developer tools:

### 🔍 **Interactive Code Visualization**
![Neo4j Visualization](assets/neo4j-viz.gif)
*[Bevel Neo4j Visualization](https://github.com/Bevel-Software/bevel-neo4j-viz) - Explore your codebase visually*

### 🧪 **AI-Assisted Test Generation** 
![Testing Prompt Generator](assets/testing-prompt-generator.gif)
*[Bevel Test Generator](https://marketplace.visualstudio.com/items?itemName=bevel-software.bevel-test-generator) - Generate comprehensive test prompts*

### 📈 **Sequence Diagrams & Documentation**
![Bevel Sequence Diagram](assets/bevel-sequence-diagram.gif)
*[Bevel Extension](https://marketplace.visualstudio.com/items?itemName=bevel-software.bevel) - Generate interactive diagrams*

### 🚀 **Custom Tool Development**
![Bevel APIs](assets/bevel-apis.gif)
*Build your own analysis tools using the knowledge graph API*

## ✨ Key Features

- **🎯 VS Code LSP Integration**: Leverages VS Code's language servers for robust, multi-language parsing
- **📊 Rich Graph Model**: Comprehensive representation of code entities and relationships  
- **🔄 Incremental Updates**: Support for real-time graph updates based on file changes
- **📁 Smart File Discovery**: Respects `.gitignore` patterns and project structures
- **🔗 Similarity Detection**: MinHashing for code similarity analysis
- **🏗️ Extensible Architecture**: Build custom analysis tools on top of the graph

<!-- ## 📚 Documentation

- **[Advanced Usage & API](docs/advanced-usage.md)** - Detailed integration guide
- **[Architecture Overview](docs/architecture.md)** - Core modules and design
- **[Building from Source](docs/building.md)** - Development setup
- **[ANTLR Module](docs/antlr-guide.md)** - Legacy grammar-based parsing -->

## 🤝 Contributing

Contributions are welcome! Whether it's bug fixes, feature enhancements, or improvements to documentation, your help is appreciated.

1. **Fork the repository.**
2. **Create a new branch** for your feature or fix.
3. **Make your changes.** Adhere to Kotlin coding conventions.
4. **Add tests** for any new functionality or bug fixes. Ensure all tests pass: `./gradlew test`.
5. **Commit your changes** with clear, descriptive messages.
6. **Push to your branch.**
7. **Open a Pull Request** against the `main` branch of the original repository.

Please ensure your PR description clearly explains the changes and their motivations.

## 📄 License

Mozilla Public License Version 2.0 - see [LICENSE](LICENSE) for details.

---

<p align="center">
  <strong>Unlock the hidden structure within your codebase! 🚀</strong>
</p>
