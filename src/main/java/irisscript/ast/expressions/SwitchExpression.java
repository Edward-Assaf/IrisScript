package irisscript.ast.expressions;

import java.util.List;
import java.util.Objects;

import irisscript.source.SourceSpan;

public final record SwitchExpression(Expression target, List<Expression> caseValues, List<Expression> caseExpressions,
        Expression defaultCase, SourceSpan span) implements Expression {

    public SwitchExpression {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(caseValues, "caseValues must not be null");
        Objects.requireNonNull(caseExpressions, "caseExpressions must not be null");
        Objects.requireNonNull(span, "span must not be null");
        if (caseValues.size() != caseExpressions.size()) {
            throw new IllegalArgumentException("caseValues and caseExpressions must have the same size");
        }
    }

}