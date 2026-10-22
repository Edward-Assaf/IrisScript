package irisscript.semantic;

import java.util.List;
import java.util.Objects;

import irisscript.ast.AstNode;
import irisscript.ast.CompilationUnit;
import irisscript.source.SourceSpan;

final record Symbol(String name, SymbolKind kind, TypeInfo type, boolean isConstant,
        List<TypeInfo> parameterTypes, SourceSpan declarationSpan, AstNode declaration,
        CompilationUnit ownerUnit) {

    public Symbol {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(parameterTypes, "parameterTypes must not be null");
        Objects.requireNonNull(declarationSpan, "declarationSpan must not be null");
        Objects.requireNonNull(declaration, "declaration must not be null");
        if (kind != SymbolKind.FUNCTION && !parameterTypes.isEmpty()) {
            throw new IllegalArgumentException("Only function symbols can have parameter types");
        }
        parameterTypes = List.copyOf(parameterTypes);
    }

}