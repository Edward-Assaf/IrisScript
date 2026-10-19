package irisscript.semantic;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import irisscript.ast.AstNode;
import irisscript.ast.CompilationUnit;
import irisscript.ast.expressions.Expression;

public final class SemanticModel {

    private final Map<AstNode, AstNode> resolvedDeclarations = new IdentityHashMap<>();

    private final Map<AstNode, CompilationUnit> declarationOwners = new IdentityHashMap<>();

    private final java.util.Set<AstNode> topLevelDeclarations = Collections.newSetFromMap(new IdentityHashMap<>());

    private final Map<Expression, String> expressionTypes = new IdentityHashMap<>();

    void bindResolution(AstNode use, AstNode declaration) {
        resolvedDeclarations.put(Objects.requireNonNull(use), Objects.requireNonNull(declaration));
    }

    void bindOwner(AstNode declaration, CompilationUnit unit) {
        declarationOwners.put(Objects.requireNonNull(declaration), Objects.requireNonNull(unit));
    }

    void bindTopLevelOwner(AstNode declaration, CompilationUnit unit) {
        bindOwner(declaration, unit);
        topLevelDeclarations.add(declaration);
    }

    public boolean isTopLevelDeclaration(AstNode declaration) {
        return topLevelDeclarations.contains(Objects.requireNonNull(declaration));
    }

    void bindExpressionType(Expression expression, String typeName) {
        expressionTypes.put(Objects.requireNonNull(expression), Objects.requireNonNull(typeName));
    }

    public Optional<String> expressionTypeName(Expression expression) {
        return Optional.ofNullable(expressionTypes.get(Objects.requireNonNull(expression)));
    }

    public Optional<AstNode> resolvedDeclaration(AstNode use) {
        return Optional.ofNullable(resolvedDeclarations.get(Objects.requireNonNull(use)));
    }

    public Optional<CompilationUnit> ownerOf(AstNode declaration) {
        return Optional.ofNullable(declarationOwners.get(Objects.requireNonNull(declaration)));
    }

    public boolean isResolved(AstNode use) {
        return resolvedDeclarations.containsKey(use);
    }

}