package irisscript.ast.statements;

import java.util.Objects;

import irisscript.ast.expressions.Expression;
import irisscript.source.SourceSpan;

public final record IfStatement(Expression condition, BlockStatement thenBranch, Statement elseBranch, SourceSpan span)
        implements Statement {

    public IfStatement {
        Objects.requireNonNull(condition, "condition must not be null");
        Objects.requireNonNull(thenBranch, "thenBranch must not be null");
        Objects.requireNonNull(span, "span must not be null");
        if (elseBranch != null && !(elseBranch instanceof BlockStatement || elseBranch instanceof IfStatement)) {
            throw new IllegalArgumentException("elseBranch must be either a BlockStatement or an IfStatement");
        }
    }

}