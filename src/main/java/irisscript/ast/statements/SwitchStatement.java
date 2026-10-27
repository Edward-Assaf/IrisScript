package irisscript.ast.statements;

import java.util.List;
import java.util.Objects;

import irisscript.ast.expressions.Expression;
import irisscript.source.SourceSpan;

public final record SwitchStatement(Expression target, List<Expression> caseValues, List<BlockStatement> caseStatements,
        BlockStatement defaultCase, SourceSpan span) implements Statement {

    public SwitchStatement {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(caseValues, "caseValues must not be null");
        Objects.requireNonNull(caseStatements, "caseStatements must not be null");
        Objects.requireNonNull(span, "span must not be null");
        if (caseValues.size() != caseStatements.size()) {
            throw new IllegalArgumentException("caseValues and caseStatements must have the same size");
        }
    }

}