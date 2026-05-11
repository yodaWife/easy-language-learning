---
description: Java coding guidelines following latest conventions and Java 26 features
applyTo: "**/*.java"
---

# Java Coding Guidelines

This project targets Java 26. Follow these guidelines for consistent, modern Java code.

## General Principles

- Use meaningful names for variables, methods, and classes.
- Prefer immutable objects where possible.
- Use exceptions appropriately; avoid checked exceptions for business logic.
- Write clean and straightforward code; avoid unnecessary complexity and over-engineering.
- Find root causes of issues rather than applying quick fixes.

## Language Features

- Use `var` for local variables when the type is obvious from the context.
- Use records for simple data classes that are immutable.
- Prefer sealed classes and interfaces for closed type hierarchies.
- Use pattern matching in `instanceof`, switch expressions, and record patterns.
- Use text blocks for multi-line strings.
- Leverage virtual threads (from Project Loom, available since Java 21) for concurrency.
- Use the latest APIs and features available in Java 26.
- Favour immutable objects. make classes immutable by declaring fields as `final` and providing no setters. Use collections from List.of(), Set.of(), and Map.of() for immutable collections.
- Use Stream API and lambdas for functional programming and to write more concise and readable code. Employ method references where appropriate for cleaner code.
- Use Lombok annotations to reduce boilerplate code for getters, setters, constructors, and other common methods. Use `@Data`, `@Value`, `@Builder`, and other relevant annotations to simplify your codebase.
- Use explicit imports to improve readability and avoid potential conflicts. Avoid using wildcard imports (e.g., `import java.util.*;`) to make it clear which classes are being used in the code.
- Use SLF4J for logging and avoid using `System.out.println` for logging purposes. Use appropriate log levels (e.g., DEBUG, INFO, WARN, ERROR) to categorize log messages effectively. Prefer parameterized logging.
- For classes with multiple fields, use constructor injection (e.g. Lombok's `@RequiredArgsConstructor`) or the builder pattern (e.g. with Lombok's `@Builder`)
- Use Javadoc comments for public APIs, describing the purpose of the class/method, its parameters, return value, and any exceptions thrown. Keep comments concise and focused on the "why" rather than the "what" of the code.


## Code Style

- Follow standard Java naming conventions: camelCase for methods and variables, PascalCase for classes and interfaces.
- Use 4 spaces for indentation (no tabs).
- Limit line length to 120 characters.
- Use Javadoc comments for public APIs.
- Organize imports: standard library first, then third-party, then project-specific.
- Use empty lines to separate logical blocks of code.

## Best Practices

- Favor composition over inheritance.
- Avoid raw types; use generics.
- Handle nulls carefully; consider using Optional for nullable return types.
- Write unit tests for all public methods.
- Always close streams and resources, preferably using try-with-resources.
- Compare objects using `equals()` rather than `==` for object equality.
- Avoid unnecessary casting; use generics and type inference to minimize the need for casts.
- Avoid conditional expressions that are always true or false, they indicate dead code and should be removed or refactored.
- Avoid using magic numbers; use constants or enums instead to improve readability and maintainability.

## Common Code Smells
- Parameter count - Avoid methods with too many parameters. Consider refactoring to use objects or builders to encapsulate related parameters.
- Method size - Keep methods small and focused on a single responsibility. If a method is too long, consider breaking it into smaller helper methods.
- Cognitive complexity - reduce nested conditionals and heavy branching by extracting logic into separate methods, using polymorphism or applying design patterns to simplify the code structure.
- Duplicated literals - extract repeated literals into constants to improve maintainability and readability.
- Dead code - remove unused variables and assignments. 

## Testing
- Test should have a descriptive name defined in `@DisplayName`
- Use `assertThat` from AssertJ for assertions, and prefer `assertThatThrownBy` for exception testing.
- Tests should take adventage of `var`
- Write tests on right level of abstraction, avoid testing implementation details and focus on behavior. Use mocks and stubs to isolate the unit under test when necessary.
- Use test doubles (mocks, stubs) to isolate the unit under test and avoid dependencies on external systems or complex setups. Use Mockito or similar libraries for mocking.
- Follow the Arrange-Act-Assert (AAA) pattern in your tests to improve readability and maintainability. Arrange the necessary preconditions and inputs, Act on the object or method under test, and Assert that the expected outcomes occur.
- Use parameterized tests to cover multiple input scenarios with a single test method, improving test coverage and reducing code duplication.
- Use test fixtures to set up common test data and state, reducing duplication and improving maintainability of your tests.
