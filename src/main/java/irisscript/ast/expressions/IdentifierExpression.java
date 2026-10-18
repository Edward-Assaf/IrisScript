package irisscript.ast.expressions;

import java.util.Objects;

import irisscript.source.SourceSpan;

public final record IdentifierExpression(String name, SourceSpan span) implements Expression {

    public IdentifierExpression {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

}