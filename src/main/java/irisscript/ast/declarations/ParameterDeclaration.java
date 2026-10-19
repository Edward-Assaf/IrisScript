package irisscript.ast.declarations;

import java.util.Objects;

import irisscript.ast.types.TypeNode;
import irisscript.ast.AstNode;
import irisscript.source.SourceSpan;

public final record ParameterDeclaration(String name, TypeNode type, boolean isConstant, SourceSpan span)
        implements AstNode {

    public ParameterDeclaration {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

}