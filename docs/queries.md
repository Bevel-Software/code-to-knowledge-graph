# Creating Language Queries for Knowledge Graph Generation

This guide explains how to create queries that parse different programming languages into knowledge graphs using our AST-based query system.

For detailed examples of how to handle specific programming constructs, best practices, and language-specific considerations, see [Programming Constructs Guide](programming_constructs.md).

## Core Concepts

### Knowledge Graph Structure

Our knowledge graph consists of two main components:

1. **Nodes** (`NodeType`):
   - `FUNCTION`: Represents functions, methods, and constructors
   - `CLASS`: Represents classes and interfaces
   - `PACKAGE`: Represents package/namespace declarations
   - `PROPERTY`: Represents fields, properties, and variables
   - `ALIAS`: Represents type aliases and imports
   - `OBJECT`: Represents singleton objects or static contexts

2. **Connections** (`ConnectionType`):
   - `DEFINES`: Parent-child relationship (e.g., class defines method)
   - `PARENT_OF`: Indicates ownership (e.g., receiver type owns method)
   - `INHERITS_FROM`: Inheritance relationships
   - `USES`: Usage relationships (e.g., variable uses type)
   - `IS_OF_TYPE`: Type relationships (e.g., return type, parameter type)
   - `INVOKED_BY`: Method invocation relationships
   - `CALLED_WITH`: Parameter passing relationships

## Query Structure

Each query file is written in YAML and follows a specific structure designed to match AST patterns and generate graph nodes and connections. Here's a detailed breakdown:

### Pattern Definition

```yaml
patterns:
  - pattern:      # A single pattern to match in the AST
      rule: string   # The AST rule to match
      captures:      # List of names to reference matched rules
        - captureName
      children:      # Child patterns that must be present in this exact order
        - rule: string
          captures:
            - childCaptureName
          optional: boolean   # Whether this child pattern is optional
      notChildren:   # Set of rules types that must NOT be present
        - excludedType
    predicates:    # Conditions that must be satisfied
      - function: exists/equals/...
        capture: captureName
    converters:    # Actions to generate graph elements
      - function: node/connection/import
        # Converter-specific parameters
```

### Pattern Types

1. **Node Pattern**
   ```yaml
   rule: functionDeclaration    # Matches specific AST node type
   captures:                    # Optional List of names to reference matched rules
     - funcNode                 # Reference this node as "funcNode"
   optional: true              # Optional property, that specifies if matching this rule and its children is optional
   notchildren:                # Set of rules types that must NOT be present
   children:                    # Required child patterns
     - rule: identifier
       captures: 
         - funcName
     - alternatives:
         - rule: classMethod
         - rule: functionDeclaration
   ```

2. **Alternative Pattern**
   ```yaml
   alternatives:               # Match any of these patterns
     - rule: classMethod
     - rule: functionDeclaration
   optional: true             # Entire alternative is optional
   ```

### Captures

Captures are references to AST nodes that can be used in predicates and converters. There are several ways to reference nodes:

1. **Named Captures**
   ```yaml
   rule: functionDeclaration
   captures:
     - funcNode    # Reference this node as "@funcNode"
   ```
   If no captures are specified, the node's rule/type will be used as the capture name.

2. **Special Captures**
   - `@parentNode`: References the parent node (first query match when going up the AST that has the node converter) and returns the AST rule
   - `@current`: References the starting AST rule of the associated pattern
   - `@parentClassNode.node`: References the closest parent class node and returns the Node
   - `@parentSuperClassNode.node`: References the closes parent class's super class if it exists and returns the Node

3. **Capture Usage**
   ```yaml
   nodeName: "@parentNode.node + '.' + @funcName"  # Concatenate captures
   ```
   - Use `@` prefix to reference captures
   - A capture's properties can be accessed using the `.` operator
   - Add `?` suffix when referencing captures if they're optional (e.g., `@returnType?`)

4. **Dot Notation Access**
   - **Parse Tree Node Access**
     ```yaml
     "@capture" = "@capture.label"      # Get node's label = source code it represents
     "@capture.rule"       # Get node's rule/type name
     "@capture.hashCode"   # Get node code's hash code, usefull for uniquely identifying items with no name, like lambdas
     ```
   - **Node Builder Access**
     ```yaml
     "@capture.node" = "@capture.node.name"  # Get node's name
     ```
   - **String Operations**
     ```yaml
     "@class.node + '.' + @method.label + \"1234\""  # Concatenate values
     "'prefix.' + @capture.node"          # Mix literals and captures
     ```

5. **Capture Resolution**
   - **Parse Tree Resolution**
     - Resolves to AST nodes (ParseTreeNode)
     - Accesses node properties (label, rule)
     - Throws error if required capture missing

   - **Node Builder Resolution**
     - Resolves to graph nodes (NodeBuilder)
     - Accesses node properties (name)
     - Falls back to parse tree if needed and possible

### Predicates

Predicates are conditions that must be satisfied for a match to be valid:

```yaml
predicates:
  - function: exists          # Check if capture exists
    capture: "@returnType"
  - function: equals          # Check if values of captures are equal
    capture1: "@type1"
    capture2: "@type2"
```

### Converters

Converters transform matched AST patterns into graph elements. There are three main types of converters:

1. **Node Converter**
   Creates nodes in the knowledge graph from matched AST patterns.
   ```yaml
   function: node
   nodeName: "@parentNode.node + '.' + @funcName"    # Fully qualified name
   nodeType: FUNCTION                                # Must match NodeType enum
   representingParseTreeNode: "@capturedNode"        # Optional, save the node as related to specific AST node, stead of @currentNode, which matches to the root node that is matched by the pattern
   predicates:                                       # Optional conditions
     - function: exists
       capture: "@someCapture"
   ```

2. **Connection Converter**
   Creates edges between nodes in the knowledge graph.
   ```yaml
   function: connection
   fromNode: "@source"                     # Source node name
   fromFallbackType: CLASS                 # Optional, fallback type if node not found
   toNode: "@target"                       # Target node name
   toFallbackType: FUNCTION               # Optional, fallback type if node not found
   connectionType: DEFINES                 # Must match ConnectionType enum
   positionAstRule: "@nodeForPosition"     # AST node for saving position where code starts and ends
   predicates:                            # Optional conditions
     - function: exists
       capture: "@someCapture"
   ```

3. **Import Converter**
   Tracks import statements and module dependencies.
   ```yaml
   function: import
   importData:                           # Map<String, String> of import data, can differ from programming language to programming language
     alias: "@aliasCapture"             # Example data: Import alias
     path: "@pathCapture"               # Example data: Import path
   predicates:                          # Optional conditions
     - function: exists
       capture: "@someCapture"
   ```

4. **Macro Converters**
   Pre-built converters that combine multiple basic converters for common patterns:

   a. **Argument Connection Converter**
   Creates connections for function arguments and their callers.
   ```yaml
   function: argument-connection
   callerCapture: "@methodCall"        # The node calling the method
   argumentCapture: "@argument"        # The argument being passed
   positionAstRule: "@methodCall"      # Optional, AST node for position info
   ```
   This converter automatically:
   - Creates a CALLED_WITH connection between the caller and argument
   - Uses splitDotAndGetLast on the caller capture for the from node
   - Adds existence predicate for the caller capture

   b. **Node With Parent Converter**
   Creates a node and automatically connects it to its parent.
   ```yaml
   function: node-with-parent
   nodeName: "@childNode"              # Name for the new node
   nodeType: FUNCTION                  # Type of the node (NodeType enum)
   parent: "@parentNode"               # Optional, defaults to "@parentNode.node"
   representingAndPositionAstRule: "@nodeForPosition"  # Optional, AST node for position
   predicates:                         # Optional conditions
     - function: exists
       capture: "@someCapture"
   ```
   This converter automatically:
   - Creates the specified node
   - Creates a DEFINES connection from the parent to the new node
   - Uses the same AST node for both node position and connection position

These macro converters simplify common patterns and help maintain consistency across queries.

The macro converters can be expressed using base converters as follows:

a. **Argument Connection Converter Equivalent**
```yaml
# This macro:
function: argument-connection
callerCapture: "@methodCall"
argumentCapture: "@argument"
positionAstRule: "@methodCall"

# Is equivalent to:
function: connection
fromNode: "@methodCall.splitDotAndGetLast"
toNode: "@argument"
connectionType: CALLED_WITH
positionAstRule: "@methodCall"
predicates:
  - function: exists
    capture: "@methodCall"
```

b. **Node With Parent Converter Equivalent**
```yaml
# This macro:
function: node-with-parent
nodeName: "@childNode"
nodeType: FUNCTION
parent: "@parentNode"
representingAndPositionAstRule: "@nodeForPosition"

# Is equivalent to:
- function: node
  nodeName: "@childNode"
  nodeType: FUNCTION
  representingParseTreeNode: "@nodeForPosition"

- function: connection
  fromNode: "@parentNode"
  toNode: "@childNode"
  connectionType: DEFINES
  positionAstRule: "@nodeForPosition"
```

Using the base converters gives you more flexibility to customize the behavior, while the macro converters provide convenience for common patterns.

### Converter Features

1. **Node Creation**
   - Nodes are created with fully qualified names
   - Node types must match the `NodeType` enum
   - Optional AST node association for debugging
   - Automatic parent-child relationship tracking

2. **Connection Management**
   - Supports all relationship types defined in `ConnectionType`
   - Fallback types for unresolved nodes
   - Position where code of this connection starts and ends
   - Automatic connection validation

3. **Import Handling**
   - Tracks file-level imports
   - Supports aliased imports
   - Maintains import relationships

4. **Common Features**
   - Conditional execution through predicates
   - Argument resolution with capture references
   - Error handling for missing captures
   - AST position tracking for debugging