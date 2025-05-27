# Step-by-Step Guide for Common Programming Constructs

This guide provides detailed examples of how to create queries for common programming language constructs.

## 1. Functions and Methods

To capture functions:

1. Match the function declaration node
2. Capture:
   - Function name
   - Parameters (if any)
   - Return type (if any)
   - Receiver type (if methods are being created outside of parent class, through concepts like kotlins extensions or friend functions)
3. Create:
   - Function node
   - DEFINES connection from parent
   - IS_OF_TYPE connection to return type
   - PARENT_OF connection from receiver (for extension methods)

Example pattern:
```yaml
type: functionDeclaration
children:
  - type: identifier        # Function name
  - type: parameters       # Parameter list
  - type: returnType      # Return type
```

## 2. Classes and Interfaces

To capture classes:

1. Match class declaration node
2. Capture:
   - Class name
   - Constructor (if present)
   - Superclasses/interfaces
3. Create:
   - Class node
   - DEFINES connection from package
   - INHERITS_FROM connections to superclasses
   - Constructor nodes and connections

## 3. Variables and Properties

To capture variables and properties:

1. Match variable/property declaration nodes
2. Capture:
   - Variable/property name
   - Type (if explicit)
   - Initial value (if present)
   - Modifiers and annotations
3. Create:
   - Property node with unique name (including parent context)
   - DEFINES connection from parent scope
   - IS_OF_TYPE connection to type node
   - Handle special cases:
     - Class-level properties vs local variables
     - Properties with backing fields
     - Computed properties (getters/setters)

Example pattern:
```yaml
type: propertyDeclaration
children:
  - type: identifier      # Property name
  - type: type           # Property type (optional)
    optional: true
  - type: initializer    # Initial value (optional)
    optional: true
```

## 4. Function Calls

To capture function invocations:

1. Match call expression node
2. Capture:
   - Called function name
   - Arguments
3. Create:
   - INVOKED_BY connection from caller to callee
   - CALLED_WITH connections for arguments

## 5. Type References

To capture type usage:

1. Match type reference nodes
2. Create:
   - USES or IS_OF_TYPE connections
   - Create ALIAS nodes for imported types

## 6. Imports and Module References

To capture imports:

1. Match import declaration nodes
2. Capture:
   - Full import path
   - Alias (if present)
   - Handle wildcards separately
3. Create:
   - Import nodes
   - USES connections to imported symbols
   - Handle both specific imports and wildcard imports differently

Example pattern:
```yaml
type: importDeclaration
children:
  - type: identifier     # Import path
  - type: alias         # Optional alias
    optional: true
```

## 7. Package Structure

To capture package organization:

1. Match package declaration nodes
2. Create:
   - Package nodes for each level
   - Nested DEFINES connections
   - Handle default packages

Example:
```yaml
type: packageDeclaration
children:
  - type: identifier    # Package path
```

## 8. Generic Types and Type Parameters

To capture generic type usage:

1. Match type parameter nodes
2. Capture:
   - Parameter name
   - Bounds/constraints
   - Variance annotations (in/out)
3. Create:
   - ALIAS nodes for type parameters
   - INHERITS_FROM connections for bounds
   - Track type parameter usage in:
     - Classes
     - Functions
     - Properties
     - Type aliases

Example patterns:
```yaml
# Basic type parameter
type: typeParameter
children:
  - type: identifier    # Parameter name
  - type: typeBound    # Optional bounds
    optional: true

# Type constraints
type: typeConstraints
children:
  - type: typeConstraint
    children:
      - type: type          # Constraint type
      - type: identifier    # Type parameter
```

Type System Features:
1. **Type Bounds**
   - Upper bounds (extends/: in Kotlin)
   - Lower bounds (super/in Kotlin)
   - Multiple bounds

2. **Variance**
   - Covariance (out/+)
   - Contravariance (in/-)
   - Invariance (default)

3. **Type Projections**
   - Use-site variance
   - Star projections
   - Type wildcards

4. **Type Inference**
   - Handle explicit vs implicit types
   - Track type resolution context
   - Support type aliases and shortcuts

## 9. Constructors

To capture constructors:

1. Match constructor declarations
2. Capture:
   - Parameters
   - Initialization blocks
   - Property declarations in primary constructor
3. Create:
   - Constructor nodes as special FUNCTION types
   - DEFINES connections from class
   - Property nodes for constructor parameters

## 10. Inheritance and Implementation

To capture inheritance relationships:

1. Match class/interface declaration with inheritance clauses
2. Capture:
   - Base classes/interfaces
   - Implementation details
   - Virtual/abstract members
3. Create:
   - INHERITS_FROM connections for class inheritance
   - IMPLEMENTS connections for interface implementation
   - Track method overrides with OVERRIDES connections
   - Handle multiple inheritance where applicable

Example pattern:
```yaml
type: classDeclaration
children:
  - type: identifier           # Class name
  - type: inheritanceClause
    children:
      - type: supertype       # Base class/interface
```

Special considerations:
- Abstract classes and methods
- Interface default implementations
- Diamond problem in multiple inheritance
- Sealed/final classes
- Delegation patterns

## 11. Function Overloading

To capture function overloading:

1. Match function declarations with same name
2. Capture:
   - Function signatures
   - Parameter types
   - Return types
3. Create:
   - Unique nodes for each overload containing the parameters
   - Add a node for the function that does not have any parameters, that will be referenced when any of the overloads are called
   - Add a DEFINES connection from the base, parameterless function node to each overloaded function node

Example pattern:
```yaml
type: functionDeclaration
children:
  - type: identifier          # Function name
  - type: parameterList       # Parameter types for disambiguation
  - type: returnType
    optional: true
```

## 12. Annotations and Decorators

To capture metadata annotations:

1. Match annotation/decorator nodes
2. Capture:
   - Annotation name
   - Parameters/arguments
   - Target element
3. Create:
   - Annotation nodes
   - ANNOTATES connections
   - Track annotation processing rules

Example pattern:
```yaml
type: annotation
children:
  - type: identifier          # Annotation name
  - type: argumentList        # Annotation parameters
    optional: true
```

## Best Practices

1. **Unique Node Names**
   - Use fully qualified names
   - Include parameter types for overloaded functions
   - Use parent context in node names

2. **Connection Accuracy**
   - Use appropriate connection types
   - Include position information for debugging
   - Handle optional elements properly

3. **AST Pattern Matching**
   - Start with concrete patterns
   - Add optional elements progressively
   - Test with various code styles

4. **Error Handling**
   - Use fallback types for unresolved references
   - Add predicates for conditional conversions
   - Validate node existence before creating connections

## Query Writing Tips

1. **Pattern Structure**
   - Start with the most specific rule at the top
   - Include optional elements with `optional: true`
   - Use captures for elements you need to reference
   - Group related patterns together

2. **Node Naming Conventions**
   - Use consistent prefixes for different types (e.g., cls_, fn_, var_)
   - Include context in node names (e.g., package.class.method)
   - Handle name collisions with unique identifiers

3. **Connection Management**
   - Create DEFINES connections for ownership relationships
   - Use IS_OF_TYPE for type relationships
   - Add USES for references
   - Include INHERITS_FROM for type hierarchies

4. **Advanced Features**
   - Use predicates for conditional conversions
   - Handle special cases with multiple patterns
   - Include position information for debugging
   - Support language-specific features through specialized patterns

5. **Testing Strategies**
   - Test with minimal examples first
   - Gradually add complexity
   - Include edge cases
   - Verify bidirectional relationships

## Language-Specific Considerations

When adapting queries for different languages:

1. **AST Structure**
   - Study the language's ANTLR grammar
   - Identify equivalent constructs
   - Handle language-specific features

2. **Naming Conventions**
   - Adapt to language-specific naming
   - Handle special characters
   - Consider case sensitivity

3. **Special Features**
   - Handle language-specific modifiers
   - Account for visibility rules
   - Support special type systems

4. **Scope Rules**
   - Implement proper namespace handling
   - Consider block-level scoping
   - Handle forward declarations

## Testing and Validation

1. Create test cases covering:
   - Basic language constructs
   - Complex relationships
   - Edge cases and special syntax

2. Verify graph correctness:
   - Node creation
   - Connection accuracy
   - Type resolution

3. Test with real-world code:
   - Large codebases
   - Different coding styles
   - Various language features

## Common Patterns Across Languages

While AST structures vary between languages, these common patterns emerge:

1. **Hierarchical Structures**
   - Package/namespace containment
   - Class/interface hierarchies
   - Nested function definitions

2. **Type Systems**
   - Basic types
   - Generic parameters
   - Type constraints
   - Union/intersection types

3. **Symbol References**
   - Variable references
   - Function calls
   - Type usage
   - Import references

4. **Modifiers and Annotations**
   - Access modifiers
   - Type modifiers
   - Custom annotations/decorators

Remember to adapt these patterns based on:
- Language-specific AST structure
- Naming conventions
- Special language features
- Type system peculiarities
