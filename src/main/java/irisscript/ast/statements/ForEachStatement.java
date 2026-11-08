package irisscript.ast.statements;

import java.util.Objects;

import irisscript.ast.expressions.Expression;
import irisscript.source.SourceSpan;

public final record ForEachStatement(VariableDeclarationStatement variable, Expression iterable, BlockStatement body,
        SourceSpan span) implements Statement {

    public ForEachStatement {
        Objects.requireNonNull(variable, "variable must not be null");
        Objects.requireNonNull(iterable, "iterable must not be null");
        Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

}