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

    private TypeInfo currentFunctionReturnType = null;

    private int loopDepth = 0;

    private final SemanticModel semanticModel = new SemanticModel();

    private CompilationUnit currentUnit;

    public static final TypeInfo ERROR_TYPE = new TypeInfo(SemanticType.ERROR, 0);

    public static final TypeInfo INTEGER_TYPE = new TypeInfo(SemanticType.INTEGER, 0);

    public static final TypeInfo DECIMAL_TYPE = new TypeInfo(SemanticType.DECIMAL, 0);

    public static final TypeInfo TEXT_TYPE = new TypeInfo(SemanticType.TEXT, 0);

    public static final TypeInfo BOOLEAN_TYPE = new TypeInfo(SemanticType.BOOLEAN, 0);

    public static final TypeInfo VOID_TYPE = new TypeInfo(SemanticType.VOID, 0);

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
        TypeInfo returnType = resolveType(functionDeclaration.returnType(), true);
        List<TypeInfo> parameterTypes = new ArrayList<>();
        for (ParameterDeclaration parameterDeclaration : functionDeclaration.parameters()) {
            TypeInfo parameterType = resolveType(parameterDeclaration.type(), false);
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
        TypeInfo variableType = resolveType(variableStatement.type(), false);
        Symbol symbol = new Symbol(variableStatement.name(), SymbolKind.VARIABLE, variableType,
                variableStatement.isConstant(), List.of(), variableStatement.span(), variableStatement, unit);
        if (!scope.define(symbol)) {
            reportDuplicateSymbol(variableStatement.name(), variableStatement.span(), scope);
        }
    }

    private void analyzeFunction(FunctionDeclaration functionDeclaration, Scope scope, Scope unitScope) {
        Scope functionScope = new Scope(scope);
        TypeInfo previousReturnType = currentFunctionReturnType;
        try {
            currentFunctionReturnType = resolveType(functionDeclaration.returnType(), true);
            boolean returnTypeHasExplicitSize = functionDeclaration.returnType().isArray()
                    && functionDeclaration.returnType().dimensions().stream().anyMatch(Objects::nonNull);
            if (returnTypeHasExplicitSize) {
                reportError("E2032", "Size-declaration for arrays is only permitted in declaration statements",
                        functionDeclaration.returnType().span());
            }
            for (ParameterDeclaration parameter : functionDeclaration.parameters()) {
                TypeInfo parameterType = resolveType(parameter.type(), false);
                boolean parameterHasExplicitSize = parameter.type().isArray()
                        && parameter.type().dimensions().stream().anyMatch(Objects::nonNull);
                if (parameterHasExplicitSize) {
                    reportError("E2032", "Size-declaration for arrays is only permitted in declaration statements",
                            parameter.type().span());
                }
                Symbol parameterSymbol = new Symbol(parameter.name(), SymbolKind.PARAMETER, parameterType,
                        parameter.isConstant(), List.of(), parameter.span(), parameter, currentUnit);
                semanticModel.bindOwner(parameter, currentUnit);
                if (!functionScope.define(parameterSymbol)) {
                    reportDuplicateSymbol(parameter.name(), parameter.span(), functionScope);
                }
            }
            analyzeStatement(functionDeclaration.body(), functionScope, unitScope);
            if (!resolveType(functionDeclaration.returnType(), true).equals(VOID_TYPE)
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
            case TerminalStatement terminalStatement -> analyzeTerminalStatement(terminalStatement, scope);
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
        TypeInfo conditionType = analyzeExpression(ifStatement.condition(), scope);
        validateType(BOOLEAN_TYPE, conditionType, ifStatement.condition().span());
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
        if (currentFunctionReturnType.equals(VOID_TYPE)) {
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
        TypeInfo returnType = analyzeExpression(returnValue, scope);
        validateType(currentFunctionReturnType, returnType, returnValue.span());
    }

    private void analyzeSkipStatement(SkipStatement skipStatement, Scope scope) {
        if (loopDepth == 0) {
            reportError("E2009", "'skip' statement is not allowed outside a loop's scope", skipStatement.span());
        }
    }

    private void analyzeVariableStatement(VariableDeclarationStatement variableStatement, Scope scope,
            Scope unitScope) {
        TypeInfo declaredType = resolveType(variableStatement.type(), false);
        validateArraySize(variableStatement.type(), scope);
        Expression initializer = variableStatement.initializer();
        boolean hasExplicitSize = variableStatement.type().isArray()
                && variableStatement.type().dimensions().stream().anyMatch(Objects::nonNull);
        if (hasExplicitSize) {
            if (initializer != null) {
                reportError("E2031", "Arrays with an explicit size cannot have an initializer",
                        variableStatement.initializer().span());
            }
        } else if (initializer != null) {
            TypeInfo initializerType = analyzeExpression(initializer, scope);
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
        TypeInfo conditionType = analyzeExpression(whileStatement.condition(), scope);
        validateType(BOOLEAN_TYPE, conditionType, whileStatement.condition().span());
        try {
            loopDepth++;
            analyzeStatement(whileStatement.body(), scope, unitScope);
        } finally {
            loopDepth--;
        }
    }

    private void analyzeTerminalStatement(TerminalStatement terminalStatement, Scope scope) {
        TypeInfo expressionType = analyzeExpression(terminalStatement.expression(), scope);
        if (expressionType.equals(VOID_TYPE)) {
            reportError("E2025", "'terminal' statement requires a non-void expression",
                    terminalStatement.expression().span());
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

    private TypeInfo analyzeExpression(Expression expression, Scope scope) {
        TypeInfo result = switch (expression) {
            case AssignmentExpression assignmentExpression -> analyzeAssignmentExpression(assignmentExpression, scope);
            case BinaryExpression binaryExpression -> analyzeBinaryExpression(binaryExpression, scope);
            case CallExpression callExpression -> analyzeCallExpression(callExpression, scope);
            case GroupingExpression groupingExpression -> analyzeGroupingExpression(groupingExpression, scope);
            case IdentifierExpression identifierExpression -> analyzeIdentifierExpression(identifierExpression, scope);
            case LiteralExpression literalExpression -> analyzeLiteralExpression(literalExpression);
            case UnaryExpression unaryExpression -> analyzeUnaryExpression(unaryExpression, scope);
            case ArrayLiteralExpression arrayExpression -> analyzeArrayExpression(arrayExpression, scope);
            case IndexExpression indexExpression -> analyzeIndexExpression(indexExpression, scope);
            case ErrorExpression errorExpression -> analyzeErrorExpression(errorExpression);
        };
        semanticModel.bindExpressionType(expression, result);
        return result;
    }

    private TypeInfo analyzeAssignmentExpression(AssignmentExpression assignmentExpression, Scope scope) {
        TypeInfo variableType = null;
        if (assignmentExpression.target() instanceof IdentifierExpression identifier) {
            Optional<Symbol> symbol = scope.resolve(identifier.name());
            if (!symbol.isPresent()) {
                reportError("E2010", "Name '" + identifier.name() + "' does not exist", identifier.span());
                return ERROR_TYPE;
            }
            semanticModel.bindResolution(assignmentExpression, symbol.get().declaration());
            if (symbol.get().kind() == SymbolKind.FUNCTION) {
                reportError("E2011", "Cannot assign value to a function", assignmentExpression.span());
                return ERROR_TYPE;
            }
            if (symbol.get().isConstant()) {
                reportError("E2012", "Cannot reassign a variable declared as 'constant'", assignmentExpression.span());
                return ERROR_TYPE;
            }
            variableType = symbol.get().type();
        } else if (assignmentExpression.target() instanceof IndexExpression indexExpression) {
            IdentifierExpression root = rootIdentifier(indexExpression);
            if (root != null) {
                Optional<Symbol> symbol = scope.resolve(root.name());
                if (symbol.isPresent()) {
                    semanticModel.bindResolution(assignmentExpression, symbol.get().declaration());
                }
            }
            TypeInfo indexType = analyzeExpression(indexExpression, scope);
            if (indexType.equals(ERROR_TYPE)) {
                return ERROR_TYPE;
            }
            variableType = indexType;
        } else {
            return ERROR_TYPE;
        }
        TypeInfo rhsType = analyzeExpression(assignmentExpression.value(), scope);
        if (rhsType.equals(ERROR_TYPE)) {
            return ERROR_TYPE;
        }
        TypeInfo valueType = null;
        switch (assignmentExpression.operator()) {
            case ASSIGN:
                valueType = rhsType;
                break;
            case ADD_ASSIGN:
                valueType = getNumericType(variableType, rhsType, BinaryOperator.ADD,
                        assignmentExpression.value().span());
                break;
            case SUBTRACT_ASSIGN:
                valueType = getNumericType(variableType, rhsType, BinaryOperator.SUBTRACT,
                        assignmentExpression.value().span());
                break;
            case MULTIPLY_ASSIGN:
                valueType = getNumericType(variableType, rhsType, BinaryOperator.MULTIPLY,
                        assignmentExpression.value().span());
                break;
            case DIVIDE_ASSIGN:
                valueType = getNumericType(variableType, rhsType, BinaryOperator.DIVIDE,
                        assignmentExpression.value().span());
                break;
            case MODULO_ASSIGN:
                valueType = getModuloType(variableType, rhsType, assignmentExpression.value().span());
                break;
            case BITWISE_AND_ASSIGN:
                valueType = getBitwiseType(variableType, rhsType, BinaryOperator.BITWISE_AND,
                        assignmentExpression.value().span());
                break;
            case BITWISE_OR_ASSIGN:
                valueType = getBitwiseType(variableType, rhsType, BinaryOperator.BITWISE_OR,
                        assignmentExpression.value().span());
                break;
        }
        if (valueType.equals(ERROR_TYPE)) {
            return ERROR_TYPE;
        }
        validateType(variableType, valueType, assignmentExpression.value().span());
        return variableType;
    }

    private TypeInfo analyzeBinaryExpression(BinaryExpression binaryExpression, Scope scope) {
        TypeInfo leftType = analyzeExpression(binaryExpression.left(), scope);
        TypeInfo rightType = analyzeExpression(binaryExpression.right(), scope);
        if (leftType.equals(ERROR_TYPE) || rightType.equals(ERROR_TYPE)) {
            return ERROR_TYPE;
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

    private TypeInfo analyzeCallExpression(CallExpression callExpression, Scope scope) {
        if (!(callExpression.callee() instanceof IdentifierExpression identifier)) {
            reportError("E2017", "Function calls must target a named function", callExpression.callee().span());
            for (Expression argument : callExpression.arguments()) {
                analyzeExpression(argument, scope);
            }
            return ERROR_TYPE;
        }
        Optional<Symbol> symbol = scope.resolve(identifier.name());
        if (!symbol.isPresent()) {
            reportError("E2010", "Function name '" + identifier.name() + "' does not exist", identifier.span());
            for (Expression argument : callExpression.arguments()) {
                analyzeExpression(argument, scope);
            }
            return ERROR_TYPE;
        }
        semanticModel.bindResolution(callExpression, symbol.get().declaration());
        semanticModel.bindResolution(identifier, symbol.get().declaration());
        if (symbol.get().kind() != SymbolKind.FUNCTION) {
            reportError("E2018", "'" + identifier.name() + "' is not a function", identifier.span());
            for (Expression argument : callExpression.arguments()) {
                analyzeExpression(argument, scope);
            }
            return ERROR_TYPE;
        }
        List<TypeInfo> parameterTypes = symbol.get().parameterTypes();
        List<Expression> arguments = callExpression.arguments();
        int parameterCount = parameterTypes.size(), argumentCount = arguments.size();
        if (parameterCount != argumentCount) {
            reportError("E2019", "Function '" + identifier.name() + "' expects " + parameterCount
                    + " argument(s), but received " + argumentCount, callExpression.span());
        }
        for (int i = 0; i < argumentCount; i++) {
            TypeInfo argumentType = analyzeExpression(arguments.get(i), scope);
            if (i < parameterCount) {
                validateType(parameterTypes.get(i), argumentType, arguments.get(i).span());
            }
        }
        return symbol.get().type();
    }

    private TypeInfo analyzeGroupingExpression(GroupingExpression groupingExpression, Scope scope) {
        return analyzeExpression(groupingExpression.expression(), scope);
    }

    private TypeInfo analyzeIdentifierExpression(IdentifierExpression identifierExpression, Scope scope) {
        Optional<Symbol> symbol = scope.resolve(identifierExpression.name());
        if (!symbol.isPresent()) {
            reportError("E2010", "Name '" + identifierExpression.name() + "' does not exist",
                    identifierExpression.span());
            return ERROR_TYPE;
        }
        semanticModel.bindResolution(identifierExpression, symbol.get().declaration());
        if (symbol.get().kind() == SymbolKind.FUNCTION) {
            reportError("E2020", "Functions cannot be used as a variable value", identifierExpression.span());
            return ERROR_TYPE;
        }
        return symbol.get().type();
    }

    private TypeInfo analyzeLiteralExpression(LiteralExpression literalExpression) {
        Object value = literalExpression.value();
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return INTEGER_TYPE;
        }
        if (value instanceof Double || value instanceof Float) {
            return DECIMAL_TYPE;
        }
        if (value instanceof String || value instanceof Character) {
            return TEXT_TYPE;
        }
        if (value instanceof Boolean) {
            return BOOLEAN_TYPE;
        }
        reportError("E2021", "Unsupported literal value: " + ((value == null) ? "null" : value.getClass().getName()),
                literalExpression.span());
        return ERROR_TYPE;
    }

    private TypeInfo analyzeUnaryExpression(UnaryExpression unaryExpression, Scope scope) {
        TypeInfo operandType = analyzeExpression(unaryExpression.operand(), scope);
        if (operandType.equals(ERROR_TYPE)) {
            return ERROR_TYPE;
        }
        return switch (unaryExpression.operator()) {
            case POSITIVE, NEGATIVE -> {
                if (!isNumeric(operandType)) {
                    reportError("E2022", "Unary operator " + unaryExpression.operator() + " requires a numeric operand",
                            unaryExpression.span());
                    yield ERROR_TYPE;
                }
                yield operandType;
            }
            case NOT -> {
                if (!operandType.equals(BOOLEAN_TYPE)) {
                    reportError("E2023", "Unary operator " + unaryExpression.operator() + " requires a boolean operand",
                            unaryExpression.span());
                    yield ERROR_TYPE;
                }
                yield BOOLEAN_TYPE;
            }
            case PRE_INCREMENT, PRE_DECREMENT, POST_INCREMENT, POST_DECREMENT -> {
                if (!(unaryExpression.operand() instanceof IdentifierExpression
                        || unaryExpression.operand() instanceof IndexExpression)) {
                    reportError("E2029", "Increment and decrement operators require an identifier or index operand",
                            unaryExpression.span());
                    yield ERROR_TYPE;
                }
                if (unaryExpression.operand() instanceof IdentifierExpression identifier) {
                    Optional<Symbol> symbol = scope.resolve(identifier.name());
                    if (!symbol.isPresent()) {
                        yield ERROR_TYPE;
                    }
                    if (symbol.get().isConstant()) {
                        reportError("E2012", "Cannot reassign a variable declared as 'constant'",
                                unaryExpression.span());
                        yield ERROR_TYPE;
                    }
                }
                if (!isNumeric(operandType)) {
                    reportError("E2030", "Increment and decrement operators require a numeric operand",
                            unaryExpression.span());
                    yield ERROR_TYPE;
                }
                yield operandType;
            }
        };
    }

    private TypeInfo analyzeArrayExpression(ArrayLiteralExpression arrayExpression, Scope scope) {
        if (arrayExpression.elements().isEmpty()) {
            reportError("E2026", "Cannot infer the type of an empty array literal", arrayExpression.span());
            return ERROR_TYPE;
        }
        TypeInfo elementType = null;
        for (Expression element : arrayExpression.elements()) {
            TypeInfo currentType = analyzeExpression(element, scope);
            if (currentType.equals(ERROR_TYPE)) {
                return ERROR_TYPE;
            }
            if (elementType == null) {
                elementType = currentType;
                continue;
            }
            if (elementType.equals(currentType)) {
                continue;
            }
            if (elementType.dimensions() == currentType.dimensions() && isNumericFamily(elementType)
                    && isNumericFamily(currentType)) {
                elementType = new TypeInfo(SemanticType.DECIMAL, elementType.dimensions());
                continue;
            }
            reportError("E2027", "Array literal elements must have compatible types", element.span());
            return ERROR_TYPE;
        }
        return new TypeInfo(elementType.type(), elementType.dimensions() + 1);
    }

    private TypeInfo analyzeIndexExpression(IndexExpression indexExpression, Scope scope) {
        TypeInfo targetType = analyzeExpression(indexExpression.target(), scope);
        TypeInfo indexType = analyzeExpression(indexExpression.index(), scope);
        if (targetType.equals(ERROR_TYPE) || indexType.equals(ERROR_TYPE)) {
            return ERROR_TYPE;
        }
        if (!indexType.equals(INTEGER_TYPE)) {
            reportError("E2034", "Indices must be integer expressions", indexExpression.index().span());
            return ERROR_TYPE;
        }
        if (targetType.type() == SemanticType.TEXT && targetType.dimensions() == 0) {
            return TEXT_TYPE;
        }
        if (targetType.isArray()) {
            return targetType.elementType();
        }
        reportError("E2035", "Index target must be an array or a text", indexExpression.target().span());
        return ERROR_TYPE;
    }

    private TypeInfo analyzeErrorExpression(ErrorExpression errorExpression) {
        return ERROR_TYPE;
    }

    private TypeInfo analyzeNumericBinary(BinaryExpression expression, TypeInfo leftType, TypeInfo rightType) {
        if (rightType.equals(TEXT_TYPE) && leftType.equals(TEXT_TYPE) && expression.operator() == BinaryOperator.ADD) {
            return TEXT_TYPE;
        }
        if (!isNumeric(leftType) || !isNumeric(rightType)) {
            reportError("E2013", "Operator " + expression.operator() + " requires numeric operands", expression.span());
            return ERROR_TYPE;
        }
        return (leftType.equals(DECIMAL_TYPE) || rightType.equals(DECIMAL_TYPE)) ? DECIMAL_TYPE : INTEGER_TYPE;
    }

    private TypeInfo analyzeModuloBinary(BinaryExpression expression, TypeInfo leftType, TypeInfo rightType) {
        if (!leftType.equals(INTEGER_TYPE) || !rightType.equals(INTEGER_TYPE)) {
            reportError("E2014", "Operator " + expression.operator() + " requires integer operands", expression.span());
            return ERROR_TYPE;
        }
        return INTEGER_TYPE;
    }

    private TypeInfo analyzeRelationalBinary(BinaryExpression expression, TypeInfo leftType, TypeInfo rightType) {
        if (!isNumeric(leftType) || !isNumeric(rightType)) {
            reportError("E2013", "Operator " + expression.operator() + " requires numeric operands", expression.span());
            return ERROR_TYPE;
        }
        return BOOLEAN_TYPE;
    }

    private TypeInfo analyzeEqualityBinary(BinaryExpression expression, TypeInfo leftType, TypeInfo rightType) {
        if (!leftType.equals(rightType)) {
            if (isNumeric(rightType) && isNumeric(leftType)) {
                return BOOLEAN_TYPE;
            }
            reportError("E2015", "Operator " + expression.operator() + " is not compatible with types "
                    + typeName(leftType) + " and " + typeName(rightType), expression.span());
            return ERROR_TYPE;
        }
        return BOOLEAN_TYPE;
    }

    private TypeInfo analyzeLogicalBinary(BinaryExpression expression, TypeInfo leftType, TypeInfo rightType) {
        if (!leftType.equals(BOOLEAN_TYPE) || !rightType.equals(BOOLEAN_TYPE)) {
            reportError("E2016", "Operator " + expression.operator() + " requires boolean operands", expression.span());
            return ERROR_TYPE;
        }
        return BOOLEAN_TYPE;
    }

    private TypeInfo analyzeBitwiseBinary(BinaryExpression expression, TypeInfo leftType, TypeInfo rightType) {
        if (!leftType.equals(INTEGER_TYPE) || !rightType.equals(INTEGER_TYPE)) {
            reportError("E2014", "Operator " + expression.operator() + " requires integer operands", expression.span());
            return ERROR_TYPE;
        }
        return INTEGER_TYPE;
    }

    private TypeInfo getNumericType(TypeInfo leftType, TypeInfo rightType, BinaryOperator operator,
            SourceSpan span) {
        if (rightType.equals(TEXT_TYPE) && leftType.equals(TEXT_TYPE) && operator == BinaryOperator.ADD) {
            return TEXT_TYPE;
        }
        if (!isNumeric(leftType) || !isNumeric(rightType)) {
            reportError("E2013", "Operator " + operator + " requires numeric operands", span);
            return ERROR_TYPE;
        }
        return (leftType.equals(DECIMAL_TYPE) || rightType.equals(DECIMAL_TYPE)) ? DECIMAL_TYPE : INTEGER_TYPE;
    }

    private TypeInfo getModuloType(TypeInfo leftType, TypeInfo rightType, SourceSpan span) {
        if (!leftType.equals(INTEGER_TYPE) || !rightType.equals(INTEGER_TYPE)) {
            reportError("E2014", "Operator " + BinaryOperator.MODULO + " requires integer operands", span);
            return ERROR_TYPE;
        }
        return INTEGER_TYPE;
    }

    private TypeInfo getBitwiseType(TypeInfo leftType, TypeInfo rightType, BinaryOperator operator,
            SourceSpan span) {
        if (!leftType.equals(INTEGER_TYPE) || !rightType.equals(INTEGER_TYPE)) {
            reportError("E2014", "Operator " + operator + " requires integer operands", span);
            return ERROR_TYPE;
        }
        return INTEGER_TYPE;
    }

    private boolean isNumericFamily(TypeInfo type) {
        return type.type() == SemanticType.INTEGER || type.type() == SemanticType.DECIMAL;
    }

    private boolean isNumeric(TypeInfo type) {
        return type.equals(INTEGER_TYPE) || type.equals(DECIMAL_TYPE);
    }

    private TypeInfo resolveType(TypeNode type, boolean voidAllowed) {
        if (type == null) {
            return ERROR_TYPE;
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
            return ERROR_TYPE;
        }
        if (semanticType == SemanticType.VOID && !voidAllowed) {
            reportError("E2002", "Void type name is not allowed here", type.span());
            return ERROR_TYPE;
        }
        int dimensions = (type.dimensions() == null) ? 0 : type.dimensions().size();
        return new TypeInfo(semanticType, dimensions);
    }

    private void validateArraySize(TypeNode type, Scope scope) {
        if (!type.isArray()) {
            return;
        }
        int sizesFound = 0;
        boolean atLeastOneNonNull = false;
        for (Expression size : type.dimensions()) {
            if (size == null) {
                continue;
            }
            atLeastOneNonNull = true;
            sizesFound++;
            TypeInfo sizeType = analyzeExpression(size, scope);
            if (!sizeType.equals(INTEGER_TYPE)) {
                reportError("E2028", "Array size must be an integer expression", size.span());
            }
            if (size instanceof UnaryExpression unary && unary.operator() == UnaryOperator.NEGATIVE
                    && unary.operand() instanceof LiteralExpression literal && literal.value() instanceof Number number
                    && number.longValue() > 0) {
                reportError("E2033", "Array size must be non-negative", size.span());
            }
        }
        if (sizesFound != type.dimensions().size() && atLeastOneNonNull) {
            reportError("E2036", "Size-declarations for multidimensional arrays cannot be partial", type.span());
        }
    }

    private void validateType(TypeInfo required, TypeInfo provided, SourceSpan span) {
        if (provided.equals(ERROR_TYPE) || required.equals(ERROR_TYPE)) {
            return;
        }
        if (provided.equals(required)) {
            return;
        }
        if (provided.equals(INTEGER_TYPE) && required.equals(DECIMAL_TYPE)) {
            return;
        }
        if (provided.equals(DECIMAL_TYPE) && required.equals(INTEGER_TYPE)) {
            reportWarning("W2001", "Potential loss of precision converting a decimal to an integer", span);
            return;
        }
        reportError("E2005", "Type mismatch: expected " + typeName(required) + ", got " + typeName(provided), span);
    }

    private String typeName(TypeInfo type) {
        StringBuilder builder = new StringBuilder(type.type().name().toLowerCase());
        for (int i = 0; i < type.dimensions(); i++) {
            builder.append("[]");
        }
        return builder.toString();
    }

    private IdentifierExpression rootIdentifier(Expression expression) {
        if (expression instanceof IdentifierExpression identifier) {
            return identifier;
        }
        if (expression instanceof IndexExpression indexExpression) {
            return rootIdentifier(indexExpression.target());
        }
        return null;
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