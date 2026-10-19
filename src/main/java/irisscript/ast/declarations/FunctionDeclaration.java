package irisscript.ast.declarations;

import java.util.List;
import java.util.Objects;

import irisscript.ast.statements.BlockStatement;
import irisscript.ast.types.TypeNode;
import irisscript.source.SourceSpan;

public final record FunctionDeclaration(String name, List<ParameterDeclaration> parameters, TypeNode returnType,
        BlockStatement body, SourceSpan span, boolean isGlobal) implements Declaration {

    public FunctionDeclaration {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(parameters, "parameters must not be null");
        Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(span, "span must not be null");
        parameters = List.copyOf(parameters);
    }

}