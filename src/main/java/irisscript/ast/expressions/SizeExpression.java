package irisscript.ast.expressions;

import java.util.Objects;

import irisscript.ast.types.TypeNode;
import irisscript.source.SourceSpan;

public final record SizeExpression(TypeNode targetType, SourceSpan span) implements Expression {

    public SizeExpression {
        Objects.requireNonNull(targetType, "targetType must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

}