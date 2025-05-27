// Only set the root project name when this module is the actual root project
// (standalone mode), not when it's included as a subproject
if (gradle.parent == null) {
    rootProject.name = "code-to-knowledge-graph"
    
    // Include submodules when in standalone mode
    include("antlr")
    include("vscode")
    include("regex")
    include("providers")
}
