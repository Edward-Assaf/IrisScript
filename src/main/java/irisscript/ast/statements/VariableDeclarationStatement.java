package irisscript.ast.statements;

import java.util.Objects;

import irisscript.ast.expressions.Expression;
import irisscript.ast.types.TypeNode;
import irisscript.source.SourceSpan;

public final record VariableDeclarationStatement(String name, TypeNode type, Expression initializer, boolean isConstant,
        SourceSpan span, boolean isGlobal) implements Statement {

    public VariableDeclarationStatement {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

}