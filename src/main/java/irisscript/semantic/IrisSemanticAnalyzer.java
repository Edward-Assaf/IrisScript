package irisscript.semantic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import irisscript.ast.CompilationUnit;
import irisscript.ast.declarations.Declaration;
import irisscript.ast.declarations.FunctionDeclaration;
import irisscript.ast.declarations.ParameterDeclaration;
import irisscript.ast.expressions.*;
import irisscript.ast.statements.*;
import irisscript.ast.types.TypeNode;
import irisscript.diagnostics.DiagnosticCode;
import irisscript.diagnostics.DiagnosticLabel;
import irisscript.diagnostics.DiagnosticSeverity;
import irisscript.diagnostics.DiagnosticsCollector;
import irisscript.source.SourceSpan;

public final class IrisSemanticAnalyzer {

    public final DiagnosticsCollector diagnosticsCollector;

    private final Scope globalScope = new Scope(null);

    private final Map<CompilationUnit, Scope> unitScopes = new HashMap<>();

    private SemanticType currentFunctionReturnType = null;

    private int loopDepth = 0;

    private final SemanticModel semanticModel = new SemanticModel();

    private CompilationUnit currentUnit;

    public IrisSemanticAnalyzer(DiagnosticsCollector diagnosticsCollector) {
        this.diagnosticsCollector = Objects.requireNonNull(diagnosticsCollector,
                "diagnosticsCollector must not be null");
    }

    public SemanticModel analyze(List<CompilationUnit> units) {
        Objects.requireNonNull(units, "units must not be null");
        for (CompilationUnit unit : units) {
            unitScopes.put(unit, new Scope(globalScope));
            collectGlobalDeclarations(unit);
        }
        for (CompilationUnit unit : units) {
            this.currentUnit = unit;
            this.loopDepth = 0;
            this.currentFunctionReturnType = null;
            analyzeBodies(unit);
        }
        return semanticModel;
    }

    private void collectGlobalDeclarations(CompilationUnit unit) {
        for (Declaration declatation : unit.declarations()) {
            if (declatation instanceof FunctionDeclaration functionDeclaration) {
                Scope scope = functionDeclaration.isGlobal() ? globalScope : unitScopes.get(unit);
                semanticModel.bindTopLevelOwner(functionDeclaration, unit);
                collectFunctionSignature(functionDeclaration, scope, unit);
            }
        }
        for (Statement statement : unit.statements()) {
            if (statement instanceof VariableDeclarationStatement variableStatement) {
                Scope scope = variableStatement.isGlobal() ? globalScope : unitScopes.get(unit);
                semanticModel.bindTopLevelOwner(variableStatement, unit);
                collectGlobalVariable(variableStatement, scope, unit);
            }
        }
    }

    private void analyzeBodies(CompilationUnit unit) {
        for (Declaration declatation : unit.declarations()) {
            if (declatation instanceof FunctionDeclaration functionDeclaration) {
                analyzeFunction(functionDeclaration, unitScopes.get(unit), unitScopes.get(unit));
            }
        }
        for (Statement statement : unit.statements()) {
            analyzeStatement(statement, unitScopes.get(unit), unitScopes.get(unit));
        }
    }

    private void collectFunctionSignature(FunctionDeclaration functionDeclaration, Scope scope, CompilationUnit unit) {
        SemanticType returnType = resolveType(functionDeclaration.returnType(), true);
        List<SemanticType> parameterTypes = new ArrayList<>();
        for (ParameterDeclaration parameterDeclaration : functionDeclaration.parameters()) {
            SemanticType parameterType = resolveType(parameterDeclaration.type(), false);
            parameterTypes.add(parameterType);
        }
        Symbol symbol = new Symbol(functionDeclaration.name(), SymbolKind.FUNCTION, returnType, false,
                parameterTypes, functionDeclaration.span(), functionDeclaration, unit);
        if (!scope.define(symbol)) {
            reportDuplicateSymbol(functionDeclaration.name(), functionDeclaration.span(), scope);
        }
    }

    private void collectGlobalVariable(VariableDeclarationStatement variableStatement, Scope scope,
            CompilationUnit unit) {
        SemanticType variableType = resolveType(variableStatement.type(), false);
        Symbol symbol = new Symbol(variableStatement.name(), SymbolKind.VARIABLE, variableType,
                variableStatement.isConstant(), List.of(), variableStatement.span(), variableStatement, unit);
        if (!scope.define(symbol)) {
            reportDuplicateSymbol(variableStatement.name(), variableStatement.span(), scope);
        }
    }

    private void analyzeFunction(FunctionDeclaration functionDeclaration, Scope scope, Scope unitScope) {
        Scope functionScope = new Scope(scope);
        SemanticType previousReturnType = currentFunctionReturnType;
        try {
            currentFunctionReturnType = resolveType(functionDeclaration.returnType(), true);
            for (ParameterDeclaration parameter : functionDeclaration.parameters()) {
                SemanticType parameterType = resolveType(parameter.type(), false);
                Symbol parameterSymbol = new Symbol(parameter.name(), SymbolKind.PARAMETER, parameterType,
                        parameter.isConstant(), List.of(), parameter.span(), parameter, currentUnit);
                semanticModel.bindOwner(parameter, currentUnit);
                if (!functionScope.define(parameterSymbol)) {
                    reportDuplicateSymbol(parameter.name(), parameter.span(), functionScope);
                }
            }
            analyzeStatement(functionDeclaration.body(), functionScope, unitScope);
            if (resolveType(functionDeclaration.returnType(), true) != SemanticType.VOID
                    && !returnsOnEveryPath(functionDeclaration.body())) {
                reportError("E2024", "Not all execution paths return a value", functionDeclaration.span());
            }
        } finally {
            currentFunctionReturnType = previousReturnType;
        }
    }

    private void analyzeStatement(Statement statement, Scope scope, Scope unitScope) {
        switch (statement) {
            case BlockStatement blockStatement -> analyzeBlockStatement(blockStatement, scope, unitScope);
            case BreakStatement breakStatement -> analyzeBreakStatement(breakStatement, scope);
            case ExpressionStatement expressionStatement -> analyzeExpressionStatement(expressionStatement, scope);
            case IfStatement ifStatement -> analyzeIfStatement(ifStatement, scope, unitScope);
            case ReturnStatement returnStatement -> analyzeReturnStatement(returnStatement, scope);
            case SkipStatement skipStatement -> analyzeSkipStatement(skipStatement, scope);
            case VariableDeclarationStatement variableStatement ->
                analyzeVariableStatement(variableStatement, scope, unitScope);
            case WhileStatement whileStatement -> analyzeWhileStatement(whileStatement, scope, unitScope);
            case ErrorStatement errorStatement -> analyzeErrorStatement(errorStatement);
        }
    }

    private void analyzeBlockStatement(BlockStatement blockStatement, Scope scope, Scope unitScope) {
        Scope blockScope = new Scope(scope);
        for (Statement innerStatement : blockStatement.statements()) {
            analyzeStatement(innerStatement, blockScope, unitScope);
        }
    }

    private void analyzeBreakStatement(BreakStatement breakStatement, Scope scope) {
        if (loopDepth == 0) {
            reportError("E2004", "'break' statement is not allowed outside a loop's scope", breakStatement.span());
        }
    }

    private void analyzeExpressionStatement(ExpressionStatement expressionStatement, Scope scope) {
        analyzeExpression(expressionStatement.expression(), scope);
    }

    private void analyzeIfStatement(IfStatement ifStatement, Scope scope, Scope unitScope) {
        SemanticType conditionType = analyzeExpression(ifStatement.condition(), scope);
        validateType(SemanticType.BOOLEAN, conditionType, ifStatement.condition().span());
        analyzeStatement(ifStatement.thenBranch(), scope, unitScope);
        if (ifStatement.elseBranch() != null) {
            analyzeStatement(ifStatement.elseBranch(), scope, unitScope);
        }
    }

    private void analyzeReturnStatement(ReturnStatement returnStatement, Scope scope) {
        if (currentFunctionReturnType == null) {
            reportError("E2006", "'return' statement is not allowed outside a function's scope",
                    returnStatement.span());
            if (returnStatement.value() != null) {
                analyzeExpression(returnStatement.value(), scope);
            }
            return;
        }
        Expression returnValue = returnStatement.value();
        if (currentFunctionReturnType == SemanticType.VOID) {
            if (returnValue != null) {
                reportError("E2007", "Functions with type name 'void' cannot return a value", returnStatement.span());
                analyzeExpression(returnValue, scope);
            }
            return;
        }
        if (returnValue == null) {
            reportError("E2008", "A non-void function must return a value", returnStatement.span());
            return;
        }
        SemanticType returnType = analyzeExpression(returnValue, scope);
        validateType(currentFunctionReturnType, returnType, returnValue.span());
    }

    private void analyzeSkipStatement(SkipStatement skipStatement, Scope scope) {
        if (loopDepth == 0) {
            reportError("E2009", "'skip' statement is not allowed outside a loop's scope", skipStatement.span());
        }
    }

    private void analyzeVariableStatement(VariableDeclarationStatement variableStatement, Scope scope,
            Scope unitScope) {
        SemanticType declaredType = resolveType(variableStatement.type(), false);
        Expression initializer = variableStatement.initializer();
        if (initializer != null) {
            SemanticType initializerType = analyzeExpression(initializer, scope);
            validateType(declaredType, initializerType, initializer.span());
        }
        if (variableStatement.isGlobal() || scope == unitScope) {
            Scope declarationScope = variableStatement.isGlobal() ? globalScope : unitScope;
            if (declarationScope.resolveLocal(variableStatement.name()).isEmpty()) {
                throw new IllegalStateException("Top-level variable '" + variableStatement.name()
                        + "' was not registered during declaration collection");
            }
            return;
        }
        Symbol symbol = new Symbol(variableStatement.name(), SymbolKind.VARIABLE, declaredType,
                variableStatement.isConstant(), List.of(), variableStatement.span(), variableStatement, currentUnit);
        semanticModel.bindOwner(variableStatement, currentUnit);
        if (!scope.define(symbol)) {
            reportDuplicateSymbol(variableStatement.name(), variableStatement.span(), scope);
        }
    }

    private void analyzeWhileStatement(WhileStatement whileStatement, Scope scope, Scope unitScope) {
        SemanticType conditionType = analyzeExpression(whileStatement.condition(), scope);
        validateType(SemanticType.BOOLEAN, conditionType, whileStatement.condition().span());
        try {
            loopDepth++;
            analyzeStatement(whileStatement.body(), scope, unitScope);
        } finally {
            loopDepth--;
        }
    }

    private void analyzeErrorStatement(ErrorStatement errorStatement) {
        // Do nothing, parser already handles this
    }

    private boolean returnsOnEveryPath(Statement statement) {
        return switch (statement) {
            case ReturnStatement returnStatement -> returnStatement.value() != null;
            case BlockStatement blockStatement -> blockStatement.statements().stream()
                    .anyMatch(this::returnsOnEveryPath);
            case IfStatement ifStatement -> returnsOnEveryPath(ifStatement.thenBranch())
                    && (ifStatement.elseBranch() == null || returnsOnEveryPath(ifStatement.elseBranch()));
            default -> false;
        };
    }

    private SemanticType analyzeExpression(Expression expression, Scope scope) {
        SemanticType result = switch (expression) {
            case AssignmentExpression assignmentExpression -> analyzeAssignmentExpression(assignmentExpression, scope);
            case BinaryExpression binaryExpression -> analyzeBinaryExpression(binaryExpression, scope);
            case CallExpression callExpression -> analyzeCallExpression(callExpression, scope);
            case GroupingExpression groupingExpression -> analyzeGroupingExpression(groupingExpression, scope);
            case IdentifierExpression identifierExpression -> analyzeIdentifierExpression(identifierExpression, scope);
            case LiteralExpression literalExpression -> analyzeLiteralExpression(literalExpression);
            case UnaryExpression unaryExpression -> analyzeUnaryExpression(unaryExpression, scope);
            case ErrorExpression errorExpression -> analyzeErrorExpression(errorExpression);
        };
        semanticModel.bindExpressionType(expression, result.name());
        return result;
    }

    private SemanticType analyzeAssignmentExpression(AssignmentExpression assignmentExpression, Scope scope) {
        Optional<Symbol> symbol = scope.resolve(assignmentExpression.name());
        if (!symbol.isPresent()) {
            reportError("E2010", "Name '" + assignmentExpression.name() + "' does not exist",
                    assignmentExpression.span());
            return SemanticType.ERROR;
        }
        semanticModel.bindResolution(assignmentExpression, symbol.get().declaration());
        if (symbol.get().kind() == SymbolKind.FUNCTION) {
            reportError("E2011", "Cannot assign value to a function", assignmentExpression.span());
            return SemanticType.ERROR;
        }
        if (symbol.get().isConstant()) {
            reportError("E2012", "Cannot reassign a variable declared as 'constant'", assignmentExpression.span());
            return SemanticType.ERROR;
        }
        SemanticType valueType = analyzeExpression(assignmentExpression.value(), scope);
        if (valueType == SemanticType.ERROR) {
            return valueType;
        }
        validateType(symbol.get().type(), valueType, assignmentExpression.value().span());
        return symbol.get().type();
    }

    private SemanticType analyzeBinaryExpression(BinaryExpression binaryExpression, Scope scope) {
        SemanticType leftType = analyzeExpression(binaryExpression.left(), scope);
        SemanticType rightType = analyzeExpression(binaryExpression.right(), scope);
        if (leftType == SemanticType.ERROR || rightType == SemanticType.ERROR) {
            return SemanticType.ERROR;
        }
        return switch (binaryExpression.operator()) {
            case ADD, SUBTRACT, MULTIPLY, DIVIDE -> analyzeNumericBinary(binaryExpression, leftType, rightType);
            case MODULO -> analyzeModuloBinary(binaryExpression, leftType, rightType);
            case LESS, LESS_EQ, GREATER, GREATER_EQ -> analyzeRelationalBinary(binaryExpression, leftType, rightType);
            case EQUAL, NOT_EQUAL -> analyzeEqualityBinary(binaryExpression, leftType, rightType);
            case AND, OR -> analyzeLogicalBinary(binaryExpression, leftType, rightType);
            case BITWISE_AND, BITWISE_OR -> analyzeBitwiseBinary(binaryExpression, leftType, rightType);
        };
    }

    private SemanticType analyzeCallExpression(CallExpression callExpression, Scope scope) {
        if (!(callExpression.callee() instanceof IdentifierExpression identifier)) {
            reportError("E2017", "Function calls must target a named function", callExpression.callee().span());
            for (Expression argument : callExpression.arguments()) {
                analyzeExpression(argument, scope);
            }
            return SemanticType.ERROR;
        }
        Optional<Symbol> symbol = scope.resolve(identifier.name());
        if (!symbol.isPresent()) {
            reportError("E2010", "Function name '" + identifier.name() + "' does not exist", identifier.span());
            for (Expression argument : callExpression.arguments()) {
                analyzeExpression(argument, scope);
            }
            return SemanticType.ERROR;
        }
        semanticModel.bindResolution(callExpression, symbol.get().declaration());
        semanticModel.bindResolution(identifier, symbol.get().declaration());
        if (symbol.get().kind() != SymbolKind.FUNCTION) {
            reportError("E2018", "'" + identifier.name() + "' is not a function", identifier.span());
            for (Expression argument : callExpression.arguments()) {
                analyzeExpression(argument, scope);
            }
            return SemanticType.ERROR;
        }
        List<SemanticType> parameterTypes = symbol.get().parameterTypes();
        List<Expression> arguments = callExpression.arguments();
        int parameterCount = parameterTypes.size(), argumentCount = arguments.size();
        if (parameterCount != argumentCount) {
            reportError("E2019", "Function '" + identifier.name() + "' expectes " + parameterCount
                    + " argument(s), but received " + argumentCount, callExpression.span());
        }
        for (int i = 0; i < argumentCount; i++) {
            SemanticType argumentType = analyzeExpression(arguments.get(i), scope);
            if (i < parameterCount) {
                validateType(parameterTypes.get(i), argumentType, arguments.get(i).span());
            }
        }
        return symbol.get().type();
    }

    private SemanticType analyzeGroupingExpression(GroupingExpression groupingExpression, Scope scope) {
        return analyzeExpression(groupingExpression.expression(), scope);
    }

    private SemanticType analyzeIdentifierExpression(IdentifierExpression identifierExpression, Scope scope) {
        Optional<Symbol> symbol = scope.resolve(identifierExpression.name());
        if (!symbol.isPresent()) {
            reportError("E2010", "Name '" + identifierExpression.name() + "' does not exist",
                    identifierExpression.span());
            return SemanticType.ERROR;
        }
        semanticModel.bindResolution(identifierExpression, symbol.get().declaration());
        if (symbol.get().kind() == SymbolKind.FUNCTION) {
            reportError("E2020", "Functions cannot be used as a variable value", identifierExpression.span());
            return SemanticType.ERROR;
        }
        return symbol.get().type();
    }

    private SemanticType analyzeLiteralExpression(LiteralExpression literalExpression) {
        Object value = literalExpression.value();
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return SemanticType.INTEGER;
        }
        if (value instanceof Double || value instanceof Float) {
            return SemanticType.DECIMAL;
        }
        if (value instanceof String || value instanceof Character) {
            return SemanticType.TEXT;
        }
        if (value instanceof Boolean) {
            return SemanticType.BOOLEAN;
        }
        reportError("E2021", "Unsupported literal value: " + ((value == null) ? "null" : value.getClass().getName()),
                literalExpression.span());
        return SemanticType.ERROR;
    }

    private SemanticType analyzeUnaryExpression(UnaryExpression unaryExpression, Scope scope) {
        SemanticType operandType = analyzeExpression(unaryExpression.operand(), scope);
        if (operandType == SemanticType.ERROR) {
            return SemanticType.ERROR;
        }
        return switch (unaryExpression.operator()) {
            case POSITIVE, NEGATIVE -> {
                if (!isNumeric(operandType)) {
                    reportError("E2022", "Unary operator " + unaryExpression.operator() + " requires a numeric operand",
                            unaryExpression.span());
                    yield SemanticType.ERROR;
                }
                yield operandType;
            }
            case NOT -> {
                if (operandType != SemanticType.BOOLEAN) {
                    reportError("E2023", "Unary operator " + unaryExpression.operator() + " requires a boolean operand",
                            unaryExpression.span());
                    yield SemanticType.ERROR;
                }
                yield SemanticType.BOOLEAN;
            }
        };
    }

    private SemanticType analyzeErrorExpression(ErrorExpression errorExpression) {
        return SemanticType.ERROR;
    }

    private SemanticType analyzeNumericBinary(BinaryExpression expression, SemanticType leftType,
            SemanticType rightType) {
        if (!isNumeric(leftType) || !isNumeric(rightType)) {
            reportError("E2013", "Operator " + expression.operator() + " requires numeric operands", expression.span());
            return SemanticType.ERROR;
        }
        return (leftType == SemanticType.DECIMAL || rightType == SemanticType.DECIMAL) ? SemanticType.DECIMAL
                : SemanticType.INTEGER;
    }

    private SemanticType analyzeModuloBinary(BinaryExpression expression, SemanticType leftType,
            SemanticType rightType) {
        if (leftType != SemanticType.INTEGER || rightType != SemanticType.INTEGER) {
            reportError("E2014", "Operator " + expression.operator() + " requires integer operands", expression.span());
            return SemanticType.ERROR;
        }
        return SemanticType.INTEGER;
    }

    private SemanticType analyzeRelationalBinary(BinaryExpression expression, SemanticType leftType,
            SemanticType rightType) {
        if (!isNumeric(leftType) || !isNumeric(rightType)) {
            reportError("E2013", "Operator " + expression.operator() + " requires numeric operands", expression.span());
            return SemanticType.ERROR;
        }
        return SemanticType.BOOLEAN;
    }

    private SemanticType analyzeEqualityBinary(BinaryExpression expression, SemanticType leftType,
            SemanticType rightType) {
        if (leftType != rightType) {
            if (isNumeric(rightType) && isNumeric(leftType)) {
                return SemanticType.BOOLEAN;
            }
            reportError("E2015", "Operator " + expression.operator() + " is not compatible with types " + leftType
                    + " and " + rightType, expression.span());
            return SemanticType.ERROR;
        }
        return SemanticType.BOOLEAN;
    }

    private SemanticType analyzeLogicalBinary(BinaryExpression expression, SemanticType leftType,
            SemanticType rightType) {
        if (leftType != SemanticType.BOOLEAN || rightType != SemanticType.BOOLEAN) {
            reportError("E2016", "Operator " + expression.operator() + " requires boolean operands", expression.span());
            return SemanticType.ERROR;
        }
        return SemanticType.BOOLEAN;
    }

    private SemanticType analyzeBitwiseBinary(BinaryExpression expression, SemanticType leftType,
            SemanticType rightType) {
        if (leftType != SemanticType.INTEGER || rightType != SemanticType.INTEGER) {
            reportError("E2014", "Operator " + expression.operator() + " requires integer operands", expression.span());
            return SemanticType.ERROR;
        }
        return SemanticType.INTEGER;
    }

    private boolean isNumeric(SemanticType type) {
        return type == SemanticType.INTEGER || type == SemanticType.DECIMAL;
    }

    private SemanticType resolveType(TypeNode type, boolean voidAllowed) {
        if (type == null) {
            return SemanticType.ERROR;
        }
        SemanticType semanticType = switch (type.name()) {
            case "integer" -> SemanticType.INTEGER;
            case "text" -> SemanticType.TEXT;
            case "decimal" -> SemanticType.DECIMAL;
            case "boolean" -> SemanticType.BOOLEAN;
            case "void" -> SemanticType.VOID;
            default -> SemanticType.ERROR;
        };
        if (semanticType == SemanticType.ERROR) {
            reportError("E2001", "Unknown type name: " + type.name(), type.span());
            return SemanticType.ERROR;
        }
        if (semanticType == SemanticType.VOID && !voidAllowed) {
            reportError("E2002", "Void type name is not allowed here", type.span());
            return SemanticType.ERROR;
        }
        return semanticType;
    }

    private void validateType(SemanticType required, SemanticType provided, SourceSpan span) {
        if (provided == SemanticType.ERROR || required == SemanticType.ERROR) {
            return;
        }
        if (provided == required) {
            return;
        }
        if (provided == SemanticType.INTEGER && required == SemanticType.DECIMAL) {
            return;
        }
        if (provided == SemanticType.DECIMAL && required == SemanticType.INTEGER) {
            reportWarning("W2001", "Potential loss of precision converting a decimal to an integer", span);
            return;
        }
        reportError("E2005", "Type mismatch: expected " + required + ", got " + provided, span);
    }

    private void reportDuplicateSymbol(String name, SourceSpan span, Scope scope) {
        Symbol alreadyExisting = scope.getExistingSymbol(name);
        if (alreadyExisting == null) {
            reportError("E2003", "Duplicate declaration of '" + name + "'", span);
        }
        reportError("E2003", "Duplicate declaration of '" + name + "'", span, "Previous declaration found here",
                alreadyExisting.declarationSpan());
    }

    private void reportError(String code, String message, SourceSpan span) {
        diagnosticsCollector.report(DiagnosticSeverity.ERROR, new DiagnosticCode(code), message, span);
    }

    private void reportError(String code, String message, SourceSpan span, String note, SourceSpan secondarySpan) {
        DiagnosticLabel label = new DiagnosticLabel(secondarySpan, note);
        List<DiagnosticLabel> labels = new ArrayList<>();
        labels.add(label);
        diagnosticsCollector.report(DiagnosticSeverity.ERROR, new DiagnosticCode(code), message, span, labels);
    }

    private void reportWarning(String code, String message, SourceSpan span) {
        diagnosticsCollector.report(DiagnosticSeverity.WARNING, new DiagnosticCode(code), message, span);
    }

}