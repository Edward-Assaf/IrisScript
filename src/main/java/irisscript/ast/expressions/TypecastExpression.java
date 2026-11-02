package irisscript.ast.expressions;

import java.util.Objects;

import irisscript.ast.types.TypeNode;
import irisscript.source.SourceSpan;

public final record TypecastExpression(TypeNode type, Expression castedExpression, SourceSpan span)
        implements Expression {

    public TypecastExpression {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(castedExpression, "castedExpression must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

}