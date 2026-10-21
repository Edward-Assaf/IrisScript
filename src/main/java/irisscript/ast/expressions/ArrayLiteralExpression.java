package irisscript.ast.expressions;

import java.util.List;
import java.util.Objects;

import irisscript.source.SourceSpan;

public final record ArrayLiteralExpression(List<Expression> elements, SourceSpan span) implements Expression {

    public ArrayLiteralExpression {
        Objects.requireNonNull(elements, "elements must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

}