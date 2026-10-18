package irisscript.ast.statements;

import java.util.Objects;

import irisscript.ast.expressions.Expression;
import irisscript.source.SourceSpan;

public final record ReturnStatement(Expression value, SourceSpan span) implements Statement {

    public ReturnStatement {
        Objects.requireNonNull(span, "span must not be null");
    }

}