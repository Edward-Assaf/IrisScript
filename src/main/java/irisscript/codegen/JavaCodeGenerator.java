package irisscript.codegen;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import irisscript.ast.AstNode;
import irisscript.ast.CompilationUnit;
import irisscript.ast.declarations.Declaration;
import irisscript.ast.declarations.FunctionDeclaration;
import irisscript.ast.declarations.ParameterDeclaration;
import irisscript.ast.expressions.*;
import irisscript.ast.statements.*;
import irisscript.ast.types.TypeNode;
import irisscript.semantic.IrisSemanticAnalyzer;
import irisscript.semantic.SemanticModel;
import irisscript.semantic.SemanticType;
import irisscript.semantic.TypeInfo;
import irisscript.source.SourceFile;
import irisscript.source.SourceManager;

public final class JavaCodeGenerator {

    private final SemanticModel semanticModel;

    private final JavaTypeMapper typeMapper = new JavaTypeMapper();

    private final JavaNameMangler nameMangler = new JavaNameMangler();

    private final Map<CompilationUnit, String> classNames = new IdentityHashMap<>();

    private StringBuilder out;

    private int indent;

    private String currentClassName;

    private FunctionDeclaration currentFunction;

    private static final String GLOBAL_CLASS = "IrisGlobals";

    private int internalSwitchVariableID = 0;

    public JavaCodeGenerator(SemanticModel semanticModel) {
        this.semanticModel = Objects.requireNonNull(semanticModel);
    }

    public List<GeneratedSource> generate(List<CompilationUnit> units, SourceManager sources) {
        classNames.clear();
        this.internalSwitchVariableID = 0;
        for (CompilationUnit unit : units) {
            SourceFile source = sources.get(unit.span().sourceID());
            String filename = Path.of(source.getName()).getFileName().toString();
            int dot = filename.lastIndexOf('.');
            String stem = dot > 0 ? filename.substring(0, dot) : filename;
            String className = toClassName(stem) + "Module" + unit.span().sourceID();
            classNames.put(unit, className);
        }
        boolean deferTopLevelExecution = !hasAnyMainDeclaration(units) && hasExecutableTopLevelStatements(units);
        List<GeneratedSource> result = new ArrayList<>();
        result.add(generateGlobals(units));
        for (CompilationUnit unit : units) {
            currentClassName = classNames.get(unit);
            out = new StringBuilder();
            indent = 0;
            emitLine("package irisscript.generated;");
            emitLine("");
            emitLine("public final class " + currentClassName + " {");
            indent++;
            emitTopLevelFields(unit);
            emitTopLevelInitializer(unit, deferTopLevelExecution);
            if (deferTopLevelExecution && hasExecutableTopLevelStatements(unit)) {
                emitTopLevelStatementsMethod(unit);
            }
            for (Declaration declaration : unit.declarations()) {
                if (declaration instanceof FunctionDeclaration function && !function.isGlobal()) {
                    emitFunction(function);
                } else if (!(declaration instanceof FunctionDeclaration)) {
                    throw new IllegalStateException("Cannot generate Java from error declaration");
                }
            }
            indent--;
            emitLine("}");
            result.add(new GeneratedSource("irisscript/generated/" + currentClassName + ".java", out.toString()));
        }
        GeneratedSource launcher = generateLauncher(units);
        if (launcher != null) {
            result.add(launcher);
        }
        return List.copyOf(result);
    }

    private GeneratedSource generateLauncher(List<CompilationUnit> units) {
        List<FunctionDeclaration> mains = new ArrayList<>();
        for (CompilationUnit unit : units) {
            for (Declaration declaration : unit.declarations()) {
                if (declaration instanceof FunctionDeclaration function && function.name().equals("main")) {
                    mains.add(function);
                }
            }
        }
        if (!mains.isEmpty() && mains.size() != 1) {
            return null;
        }
        boolean hasUserMain = !mains.isEmpty();
        FunctionDeclaration main = hasUserMain ? mains.get(0) : null;
        String invocation;
        if (hasUserMain) {
            if (!main.parameters().isEmpty() || main.returnType() == null || !"void".equals(main.returnType().name())) {
                return null;
            }
            if (main.isGlobal()) {
                invocation = "IrisGlobals." + nameMangler.mangle("main") + "();";
            } else {
                CompilationUnit owner = semanticModel.ownerOf(main).orElse(null);
                String ownerClass = owner == null ? null : classNames.get(owner);
                if (ownerClass == null) {
                    return null;
                }
                invocation = ownerClass + "." + nameMangler.mangle("main") + "();";
            }
        } else {
            if (!hasExecutableTopLevelStatements(units)) {
                return null;
            }
            invocation = null;
        }
        StringBuilder launcher = new StringBuilder();
        launcher.append("package irisscript.generated;\n\n")
                .append("public final class IrisLauncher {\n")
                .append("    private IrisLauncher() {}\n\n")
                .append("    public static void main(String[] args) throws Exception {\n")
                .append("        Class.forName(\"irisscript.generated.IrisGlobals\");\n");
        for (CompilationUnit unit : units) {
            String className = classNames.get(unit);
            launcher.append("        Class.forName(\"irisscript.generated.").append(className).append("\");\n");
        }
        if (hasUserMain) {
            launcher.append("        ").append(invocation).append("\n");
        } else {
            for (CompilationUnit unit : units) {
                if (hasExecutableTopLevelStatements(unit)) {
                    launcher.append("        ")
                            .append(classNames.get(unit))
                            .append(".__irisRunTopLevelStatements();\n");
                }
            }
        }
        launcher.append("    }\n").append("}\n");
        return new GeneratedSource("irisscript/generated/IrisLauncher.java", launcher.toString());
    }

    private boolean hasAnyMainDeclaration(List<CompilationUnit> units) {
        for (CompilationUnit unit : units) {
            for (Declaration declaration : unit.declarations()) {
                if (declaration instanceof FunctionDeclaration function && function.name().equals("main")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasExecutableTopLevelStatements(List<CompilationUnit> units) {
        for (CompilationUnit unit : units) {
            if (hasExecutableTopLevelStatements(unit)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasExecutableTopLevelStatements(CompilationUnit unit) {
        return unit.statements().stream().anyMatch(statement -> !(statement instanceof VariableDeclarationStatement));
    }

    private void emitTopLevelStatementsMethod(CompilationUnit unit) {
        emitLine("");
        emitLine("static void __irisRunTopLevelStatements() {");
        indent++;
        for (Statement statement : unit.statements()) {
            if (!(statement instanceof VariableDeclarationStatement)) {
                emitStatement(statement);
            }
        }
        indent--;
        emitLine("}");
    }

    private GeneratedSource generateGlobals(List<CompilationUnit> units) {
        currentClassName = GLOBAL_CLASS;
        out = new StringBuilder();
        indent = 0;
        emitLine("package irisscript.generated;");
        emitLine("");
        emitLine("public final class " + GLOBAL_CLASS + " {");
        indent++;
        for (CompilationUnit unit : units) {
            for (Statement statement : unit.statements()) {
                if (statement instanceof VariableDeclarationStatement variable && variable.isGlobal()) {
                    emitLine("public static " + (variable.isConstant() ? "final " : "")
                            + typeMapper.map(variable.type()) + " " + nameMangler.mangle(variable.name()) + ";");
                }
            }
        }
        boolean hasGlobalInitializers = units.stream().flatMap(unit -> unit.statements().stream())
                .anyMatch(statement -> statement instanceof VariableDeclarationStatement variable
                        && variable.isGlobal() && (variable.initializer() != null || isSizedArray(variable.type())));
        if (hasGlobalInitializers) {
            emitLine("");
            emitLine("static {");
            indent++;
            for (CompilationUnit unit : units) {
                for (Statement statement : unit.statements()) {
                    if (statement instanceof VariableDeclarationStatement variable && variable.isGlobal()) {
                        emitIndent();
                        out.append(nameMangler.mangle(variable.name())).append(" = ");
                        if (variable.initializer() != null) {
                            emitExpressionAs(toTypeInfo(variable.type()), variable.initializer());
                        } else if (isSizedArray(variable.type())) {
                            emitSizedArrayCreation(variable.type());
                        } else {
                            continue;
                        }
                        out.append(";\n");
                    }
                }
            }
            indent--;
            emitLine("}");
        }
        for (CompilationUnit unit : units) {
            for (Declaration declaration : unit.declarations()) {
                if (declaration instanceof FunctionDeclaration function && function.isGlobal()) {
                    emitFunction(function);
                }
            }
        }
        indent--;
        emitLine("}");
        return new GeneratedSource("irisscript/generated/" + GLOBAL_CLASS + ".java", out.toString());
    }

    private void emitTopLevelFields(CompilationUnit unit) {
        for (Statement statement : unit.statements()) {
            if (statement instanceof VariableDeclarationStatement variable && !variable.isGlobal()) {
                emitLine("static " + (variable.isConstant() ? "final " : "")
                        + typeMapper.map(variable.type()) + " " + nameMangler.mangle(variable.name()) + ";");
            }
        }
    }

    private void emitTopLevelInitializer(CompilationUnit unit, boolean deferExecutableStatements) {
        boolean hasInitializer = unit.statements().stream().anyMatch(statement -> {
            if (statement instanceof VariableDeclarationStatement variable) {
                return !variable.isGlobal() && (variable.initializer() != null || isSizedArray(variable.type()));
            }
            return !deferExecutableStatements;
        });
        if (!hasInitializer) {
            return;
        }
        emitLine("");
        emitLine("static {");
        indent++;
        for (Statement statement : unit.statements()) {
            if (statement instanceof VariableDeclarationStatement variable) {
                if (!variable.isGlobal()) {
                    emitIndent();
                    out.append(nameMangler.mangle(variable.name())).append(" = ");
                    if (variable.initializer() != null) {
                        emitExpressionAs(toTypeInfo(variable.type()), variable.initializer());
                    } else if (isSizedArray(variable.type())) {
                        emitSizedArrayCreation(variable.type());
                    } else {
                        continue;
                    }
                    out.append(";\n");
                }
            } else if (!deferExecutableStatements) {
                emitStatement(statement);
            }
        }
        indent--;
        emitLine("}");
    }

    private void emitFunction(FunctionDeclaration function) {
        FunctionDeclaration previousFunction = currentFunction;
        currentFunction = function;
        try {
            emitLine("");
            String visibility = function.isGlobal() ? "public " : "";
            StringBuilder signature = new StringBuilder(visibility + "static "
                    + typeMapper.map(function.returnType()) + " " + nameMangler.mangle(function.name()) + "(");
            for (int i = 0; i < function.parameters().size(); i++) {
                if (i > 0) {
                    signature.append(", ");
                }
                ParameterDeclaration parameter = function.parameters().get(i);
                signature.append(typeMapper.map(parameter.type())).append(' ')
                        .append(nameMangler.mangle(parameter.name()));
            }
            signature.append(") {");
            emitLine(signature.toString());
            indent++;
            for (Statement statement : function.body().statements()) {
                emitStatement(statement);
            }
            indent--;
            emitLine("}");
        } finally {
            currentFunction = previousFunction;
        }
    }

    @SuppressWarnings("unused")
    private void emitStatement(Statement statement) {
        switch (statement) {
            case BlockStatement block -> {
                emitLine("{");
                indent++;
                for (Statement child : block.statements()) {
                    emitStatement(child);
                }
                indent--;
                emitLine("}");
            }
            case VariableDeclarationStatement variable -> {
                emitIndent();
                if (variable.isConstant()) {
                    out.append("final ");
                }
                out.append(typeMapper.map(variable.type())).append(' ').append(nameMangler.mangle(variable.name()));
                if (variable.initializer() != null) {
                    out.append(" = ");
                    emitExpressionAs(toTypeInfo(variable.type()), variable.initializer());
                } else if (isSizedArray(variable.type())) {
                    out.append(" = ");
                    emitSizedArrayCreation(variable.type());
                }
                out.append(";\n");
            }
            case ExpressionStatement expression -> {
                emitIndent();
                emitExpression(expression.expression());
                out.append(";\n");
            }
            case ReturnStatement ret -> {
                emitIndent();
                out.append("return");
                if (ret.value() != null) {
                    out.append(' ');
                    if (currentFunction != null) {
                        emitExpressionAs(toTypeInfo(currentFunction.returnType()), ret.value());
                    } else {
                        emitExpression(ret.value());
                    }
                }
                out.append(";\n");
            }
            case IfStatement conditional -> {
                emitIndent();
                out.append("if (");
                emitExpression(conditional.condition());
                out.append(") ");
                emitBlockInline(conditional.thenBranch());
                if (conditional.elseBranch() != null) {
                    out.append(" else ");
                    if (conditional.elseBranch() instanceof BlockStatement block) {
                        emitBlockInline(block);
                    } else if (conditional.elseBranch() instanceof IfStatement nested) {
                        emitIfInline(nested);
                    }
                }
                out.append('\n');
            }
            case WhileStatement loop -> {
                emitIndent();
                out.append("while (");
                emitExpression(loop.condition());
                out.append(") ");
                emitBlockInline(loop.body());
                out.append('\n');
            }
            case BreakStatement ignored -> emitLine("break;");
            case SkipStatement ignored -> emitLine("continue;");
            case TerminalStatement terminal -> {
                emitIndent();
                out.append("System.out.print(");
                TypeInfo expressionType = semanticModel.expressionType(terminal.expression()).orElse(null);
                if (expressionType != null && expressionType.isArray()) {
                    if (expressionType.dimensions() > 1) {
                        out.append("java.util.Arrays.deepToString(");
                        emitExpression(terminal.expression());
                        out.append(")");
                    } else {
                        out.append("java.util.Arrays.toString(");
                        emitExpression(terminal.expression());
                        out.append(")");
                    }
                } else {
                    emitExpression(terminal.expression());
                }
                out.append(");\n");
            }
            case SwitchStatement switchStatement -> {
                emitIndent();
                if (switchStatement.caseValues().isEmpty()) {
                    if (switchStatement.defaultCase() != null) {
                        emitStatement(switchStatement.defaultCase());
                    }
                    break;
                }
                TypeInfo targetType = semanticModel.expressionType(switchStatement.target()).orElse(null);
                String internalVariableName = emitSwitchVariableName(switchStatement.target(), targetType);
                emitIndent();
                out.append("if");
                emitSwitchConditionPart(targetType, internalVariableName, switchStatement.caseValues().get(0));
                emitBlockInline(switchStatement.caseStatements().get(0));
                out.append('\n');
                for (int i = 1; i < switchStatement.caseValues().size(); i++) {
                    emitIndent();
                    out.append("else if");
                    emitSwitchConditionPart(targetType, internalVariableName, switchStatement.caseValues().get(i));
                    emitBlockInline(switchStatement.caseStatements().get(i));
                    out.append('\n');
                }
                if (switchStatement.defaultCase() != null) {
                    emitIndent();
                    out.append("else ");
                    emitBlockInline(switchStatement.defaultCase());
                    out.append('\n');
                }
                out.append('\n');
            }
            case ErrorStatement ignored -> throw new IllegalStateException("Cannot generate Java from ErrorStatement");
        }

    }

    private void emitIfInline(IfStatement statement) {
        out.append("if (");
        emitExpression(statement.condition());
        out.append(") ");
        emitBlockInline(statement.thenBranch());
        if (statement.elseBranch() != null) {
            out.append(" else ");
            if (statement.elseBranch() instanceof BlockStatement block) {
                emitBlockInline(block);
            } else if (statement.elseBranch() instanceof IfStatement nested) {
                emitIfInline(nested);
            }
        }
    }

    private void emitBlockInline(BlockStatement block) {
        out.append("{\n");
        indent++;
        for (Statement statement : block.statements()) {
            emitStatement(statement);
        }
        indent--;
        emitIndent();
        out.append('}');
    }

    @SuppressWarnings("unused")
    private void emitExpression(Expression expression) {
        switch (expression) {
            case LiteralExpression literal -> emitLiteral(literal.value());
            case IdentifierExpression identifier -> emitResolvedReference(identifier, identifier.name());
            case GroupingExpression grouping -> {
                out.append('(');
                emitExpression(grouping.expression());
                out.append(')');
            }
            case UnaryExpression unary -> {
                switch (unary.operator()) {
                    case POSITIVE -> {
                        out.append('+');
                        emitExpression(unary.operand());
                    }
                    case NEGATIVE -> {
                        out.append('-');
                        emitExpression(unary.operand());
                    }
                    case NOT -> {
                        out.append('!');
                        emitExpression(unary.operand());
                    }
                    case PRE_DECREMENT -> {
                        out.append("--");
                        emitExpression(unary.operand());
                    }
                    case PRE_INCREMENT -> {
                        out.append("++");
                        emitExpression(unary.operand());
                    }
                    case POST_DECREMENT -> {
                        emitExpression(unary.operand());
                        out.append("--");
                    }
                    case POST_INCREMENT -> {
                        emitExpression(unary.operand());
                        out.append("++");
                    }
                    default -> throw new IllegalStateException("Unsupported unary operator: " + unary.operator());
                }
            }
            case BinaryExpression binary -> {
                TypeInfo type = semanticModel.expressionType(binary.left()).orElse(null);
                boolean text = type != null && type.dimensions() == 0 && type.type() == SemanticType.TEXT;
                if ((binary.operator() == BinaryOperator.EQUAL || binary.operator() == BinaryOperator.NOT_EQUAL)
                        && text) {
                    if (binary.operator() == BinaryOperator.NOT_EQUAL) {
                        out.append('!');
                    }
                    out.append("java.util.Objects.equals(");
                    emitExpression(binary.left());
                    out.append(", ");
                    emitExpression(binary.right());
                    out.append(')');
                } else {
                    out.append('(');
                    emitExpression(binary.left());
                    out.append(' ').append(mapOperator(binary.operator())).append(' ');
                    emitExpression(binary.right());
                    out.append(')');
                }
            }
            case AssignmentExpression assignment -> {
                emitExpression(assignment.target());
                out.append(switch (assignment.operator()) {
                    case ASSIGN -> " = ";
                    case ADD_ASSIGN -> " += ";
                    case SUBTRACT_ASSIGN -> " -= ";
                    case MULTIPLY_ASSIGN -> " *= ";
                    case DIVIDE_ASSIGN -> " /= ";
                    case MODULO_ASSIGN -> " %= ";
                    case BITWISE_AND_ASSIGN -> " &= ";
                    case BITWISE_OR_ASSIGN -> " |= ";
                });
                TypeInfo targetType = semanticModel.expressionType(assignment.target()).orElse(null);
                if (targetType != null) {
                    emitExpressionAs(targetType, assignment.value());
                } else {
                    emitExpression(assignment.value());
                }
            }
            case CallExpression call -> {
                if (call.callee() instanceof IdentifierExpression identifier) {
                    AstNode declaration = semanticModel.resolvedDeclaration(call).orElse(null);
                    if (declaration != null) {
                        emitResolvedDeclaration(declaration, identifier.name());
                    } else {
                        emitExpression(call.callee());
                    }
                } else {
                    emitExpression(call.callee());
                }
                out.append('(');
                for (int i = 0; i < call.arguments().size(); i++) {
                    if (i > 0) {
                        out.append(", ");
                    }
                    Expression argument = call.arguments().get(i);
                    AstNode declaration = semanticModel.resolvedDeclaration(call).orElse(null);
                    if (declaration instanceof FunctionDeclaration function && i < function.parameters().size()) {
                        emitExpressionAs(toTypeInfo(function.parameters().get(i).type()), argument);
                    } else {
                        emitExpression(argument);
                    }
                }
                out.append(')');
            }
            case ArrayLiteralExpression array -> {
                TypeInfo type = semanticModel.expressionType(array).orElse(null);
                if (type == null) {
                    throw new IllegalStateException("Array literal missing semantic type");
                }
                String javaElementType = javaElementType(type.type());
                out.append("new ");
                out.append(javaElementType);
                for (int i = 0; i < type.dimensions(); i++) {
                    out.append("[]");
                }
                out.append('{');
                for (int i = 0; i < array.elements().size(); i++) {
                    if (i > 0) {
                        out.append(", ");
                    }
                    emitArrayElementAs(type.elementType(), array.elements().get(i));
                }
                out.append('}');
            }
            case IndexExpression index -> {
                TypeInfo targetType = semanticModel.expressionType(index.target()).orElse(null);
                if (targetType != null && targetType.type() == SemanticType.TEXT && targetType.dimensions() == 0) {
                    out.append("String.valueOf(");
                    emitExpression(index.target());
                    out.append(".charAt((int)(");
                    emitExpression(index.index());
                    out.append(")))");
                } else {
                    emitExpression(index.target());
                    out.append("[((int) (");
                    emitExpression(index.index());
                    out.append("))]");
                }
            }
            case SizeExpression size -> {
                TypeNode type = size.targetType();
                switch (type.name()) {
                    case "integer":
                        out.append("8L");
                        break;
                    case "decimal":
                        out.append("8L");
                        break;
                    case "boolean":
                        out.append("1L");
                        break;
                    case "text":
                        out.append("8L");
                        break;
                    default:
                        throw new IllegalStateException("Unsupported type for size(): " + type.name());
                }
            }
            case LengthExpression length -> {
                TypeInfo type = semanticModel.expressionType(length.expression()).orElse(null);
                out.append("((long) (");
                if (type == null) {
                    emitExpression(length.expression());
                    out.append("))");
                    return;
                }
                if (type.equals(IrisSemanticAnalyzer.TEXT_TYPE)) {
                    emitExpression(length.expression());
                    out.append(".length()))");
                    return;
                }
                emitExpression(length.expression());
                out.append(".length))");
            }
            case TypeExpression typeExpr -> {
                TypeInfo targetType = semanticModel.expressionType(typeExpr.expression()).orElse(null);
                if (targetType == null || targetType.equals(IrisSemanticAnalyzer.ERROR_TYPE)) {
                    throw new IllegalStateException("Missing or invalid type for type() expression");
                }
                String typeString = formatType(targetType);
                out.append('"').append(typeString).append('"');
            }
            case InterpolatedTextExpression interpolated -> {
                if (interpolated.parts().isEmpty()) {
                    out.append("\"\"");
                    break;
                }
                out.append("(\"\"");
                for (Expression part : interpolated.parts()) {
                    out.append(" + ");
                    TypeInfo partType = semanticModel.expressionType(part).orElse(null);
                    if (partType != null && partType.isArray()) {
                        if (partType.dimensions() > 1) {
                            out.append("java.util.Arrays.deepToString(");
                            emitExpression(part);
                            out.append(")");
                        } else {
                            out.append("java.util.Arrays.toString(");
                            emitExpression(part);
                            out.append(")");
                        }
                    } else {
                        emitExpression(part);
                    }
                }
                out.append(')');
            }
            case TernaryExpression ternary -> {
                out.append('(');
                emitExpression(ternary.condition());
                out.append("? ");
                emitExpression(ternary.ifTrue());
                out.append(" : ");
                emitExpression(ternary.ifFalse());
                out.append(')');
            }
            case SwitchExpression switchExpression -> {
                TypeInfo returnType = semanticModel.expressionType(switchExpression).orElse(null);
                TypeInfo targetType = semanticModel.expressionType(switchExpression.target()).orElse(null);
                out.append("((java.util.function.Supplier<");
                emitJavaElementTypeAndDimensionsBoxed(returnType);
                out.append(">) () -> { ");
                if (switchExpression.caseValues().isEmpty()) {
                    out.append("return ((");
                    emitJavaElementTypeAndDimensions(returnType);
                    out.append(") (");
                    emitExpression(switchExpression.defaultCase());
                    out.append(")); }).get()");
                    if (returnType.equals(IrisSemanticAnalyzer.INTEGER_TYPE)
                            || returnType.equals(IrisSemanticAnalyzer.DECIMAL_TYPE)) {
                        out.append('.');
                        emitJavaNumericBoxedCastToUnboxed(returnType);
                    }
                    break;
                }
                out.append('\n');
                indent++;
                emitIndent();
                String internalVariableName = emitSwitchVariableName(switchExpression.target(), targetType);
                emitIndent();
                out.append("if");
                emitSwitchConditionPart(targetType, internalVariableName, switchExpression.caseValues().get(0));
                out.append("return ((");
                emitJavaElementTypeAndDimensions(returnType);
                out.append(") (");
                emitExpression(switchExpression.caseExpressions().get(0));
                out.append("));\n");
                for (int i = 1; i < switchExpression.caseValues().size(); i++) {
                    emitIndent();
                    out.append("else if");
                    emitSwitchConditionPart(targetType, internalVariableName, switchExpression.caseValues().get(i));
                    out.append("return ((");
                    emitJavaElementTypeAndDimensions(returnType);
                    out.append(") (");
                    emitExpression(switchExpression.caseExpressions().get(i));
                    out.append("));\n");
                }
                emitIndent();
                out.append("else return ((");
                emitJavaElementTypeAndDimensions(returnType);
                out.append(") (");
                emitExpression(switchExpression.defaultCase());
                out.append("));\n");
                indent--;
                emitIndent();
                out.append("}).get()");
                if (returnType.equals(IrisSemanticAnalyzer.INTEGER_TYPE)
                        || returnType.equals(IrisSemanticAnalyzer.DECIMAL_TYPE)) {
                    out.append('.');
                    emitJavaNumericBoxedCastToUnboxed(returnType);
                }
            }
            case ErrorExpression ignored ->
                throw new IllegalStateException("Cannot generate Java from ErrorExpression");
        }
    }

    private void emitExpressionAs(TypeInfo targetType, Expression expression) {
        TypeInfo sourceType = semanticModel.expressionType(expression).orElse(null);
        if (sourceType == null) {
            emitExpression(expression);
            return;
        }
        if (targetType.equals(IrisSemanticAnalyzer.INTEGER_TYPE)
                && sourceType.equals(IrisSemanticAnalyzer.DECIMAL_TYPE)) {
            out.append("((long) (");
            emitExpression(expression);
            out.append("))");
        } else {
            emitExpression(expression);
        }
    }

    private String emitSwitchVariableName(Expression target, TypeInfo targetType) {
        String internalVariableName = "__irisSwitchVar" + (internalSwitchVariableID++);
        emitJavaElementTypeAndDimensions(targetType);
        out.append(" " + internalVariableName + " = ");
        emitExpression(target);
        out.append(";\n");
        return internalVariableName;
    }

    private void emitJavaElementTypeAndDimensionsBoxed(TypeInfo type) {
        if (type == null) {
            return;
        }
        if (type.isArray()) {
            emitJavaElementTypeAndDimensions(type);
            return;
        }
        switch (type.type()) {
            case INTEGER -> out.append("Long");
            case DECIMAL -> out.append("Double");
            case BOOLEAN -> out.append("Boolean");
            case TEXT -> out.append("String");
            default -> throw new IllegalStateException("Unsupported type for Java element type: " + type.type());
        }
    }

    private void emitJavaElementTypeAndDimensions(TypeInfo type) {
        if (type == null) {
            return;
        }
        out.append(javaElementType(type.type()));
        out.append("[]".repeat(type.dimensions()));
    }

    private void emitJavaNumericBoxedCastToUnboxed(TypeInfo type) {
        if (type == null) {
            return;
        }
        switch (type.type()) {
            case INTEGER -> out.append("longValue()");
            case DECIMAL -> out.append("doubleValue()");
            default -> throw new IllegalStateException("Unsupported type for Java element type: " + type.type());
        }
    }

    private void emitSwitchConditionPart(TypeInfo targetType, String internalVariableName,
            Expression caseValue) {
        out.append("( ");
        if (targetType != null && targetType.equals(IrisSemanticAnalyzer.TEXT_TYPE)) {
            out.append("java.util.Objects.equals(");
            out.append(internalVariableName);
            out.append(", ");
            emitExpression(caseValue);
            out.append(")) ");
        } else {
            out.append(internalVariableName);
            out.append(" == ");
            emitExpressionAs(targetType, caseValue);
            out.append(") ");
        }
    }

    private void emitArrayElementAs(TypeInfo targetType, Expression expression) {
        TypeInfo sourceType = semanticModel.expressionType(expression).orElse(null);
        if (sourceType == null || targetType.equals(sourceType)
                || targetType.type() != IrisSemanticAnalyzer.DECIMAL_TYPE.type()
                || sourceType.type() != IrisSemanticAnalyzer.INTEGER_TYPE.type()
                || targetType.dimensions() != sourceType.dimensions() || sourceType.dimensions() == 0) {
            emitExpression(expression);
            return;
        }
        int dimensions = sourceType.dimensions();
        // Capture the source expression as Java code.
        String sourceExpression;
        StringBuilder previousOut = out;
        try {
            out = new StringBuilder();
            emitExpression(expression);
            sourceExpression = out.toString();
        } finally {
            out = previousOut;
        }
        if (dimensions == 1) {
            out.append("java.util.Arrays.stream(")
                    .append(sourceExpression)
                    .append(").asDoubleStream().toArray()");
            return;
        }
        // Build the conversion from the innermost array outward.
        String convertedExpression = "java.util.Arrays.stream(__irisRow1)" + ".asDoubleStream().toArray()";
        for (int level = 2; level < dimensions; level++) {
            convertedExpression = "java.util.Arrays.stream(__irisRow" + level + ")" + ".map(__irisRow" + (level - 1)
                    + " -> " + convertedExpression + ")" + ".toArray(double" + "[]".repeat(level) + "::new)";
        }
        out.append("java.util.Arrays.stream(")
                .append(sourceExpression)
                .append(").map(__irisRow")
                .append(dimensions - 1)
                .append(" -> ")
                .append(convertedExpression)
                .append(").toArray(double")
                .append("[]".repeat(dimensions))
                .append("::new)");
    }

    private void emitResolvedReference(AstNode use, String sourceName) {
        AstNode declaration = semanticModel.resolvedDeclaration(use).orElse(null);
        if (declaration == null) {
            // This is normally unreachable after successful semantic analysis.
            out.append(nameMangler.mangle(sourceName));
            return;
        }
        emitResolvedDeclaration(declaration, sourceName);
    }

    private void emitResolvedDeclaration(AstNode declaration, String sourceName) {
        CompilationUnit owner = semanticModel.ownerOf(declaration).orElse(null);
        boolean topLevel = semanticModel.isTopLevelDeclaration(declaration);
        boolean global = topLevel && (declaration instanceof FunctionDeclaration function && function.isGlobal()
                || declaration instanceof VariableDeclarationStatement variable && variable.isGlobal());
        String targetClass = !topLevel ? currentClassName
                : (global ? GLOBAL_CLASS : (owner == null ? currentClassName : classNames.get(owner)));
        if (targetClass != null && !targetClass.equals(currentClassName)) {
            out.append(targetClass).append('.');
        }
        out.append(nameMangler.mangle(sourceName));
    }

    private void emitLiteral(Object value) {
        if (value == null) {
            out.append("null");
            return;
        }
        if (value instanceof String text) {
            out.append('"').append(escape(text, false)).append('"');
            return;
        }
        if (value instanceof Character character) {
            out.append('\'').append(escape(character.toString(), true)).append('\'');
            return;
        }
        if (value instanceof Long l) {
            out.append(l).append('L');
            return;
        }
        out.append(value);
    }

    private void emitSizedArrayCreation(TypeNode type) {
        String javaElementType = switch (type.name()) {
            case "integer" -> "long";
            case "decimal" -> "double";
            case "text" -> "String";
            case "boolean" -> "boolean";
            default -> "Object";
        };
        out.append("new ");
        out.append(javaElementType);
        for (Expression dimension : type.dimensions()) {
            out.append('[');
            if (dimension != null) {
                out.append("(int)(");
                emitExpression(dimension);
                out.append(')');
            }
            out.append(']');
        }
    }

    private String escape(String value, boolean character) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append(character ? "\"" : "\\\"");
                case '\'' -> escaped.append(character ? "\\\'" : "'");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                default -> {
                    if (c < 0x20 || c == 0x7f)
                        escaped.append(String.format("\\%03o", (int) c));
                    else
                        escaped.append(c);
                }
            }
        }
        return escaped.toString();
    }

    private String mapOperator(BinaryOperator operator) {
        return switch (operator) {
            case ADD -> "+";
            case SUBTRACT -> "-";
            case MULTIPLY -> "*";
            case DIVIDE -> "/";
            case MODULO -> "%";
            case EQUAL -> "==";
            case NOT_EQUAL -> "!=";
            case LESS -> "<";
            case GREATER -> ">";
            case LESS_EQ -> "<=";
            case GREATER_EQ -> ">=";
            case AND -> "&&";
            case OR -> "||";
            case BITWISE_AND -> "&";
            case BITWISE_OR -> "|";
        };
    }

    private String formatType(TypeInfo type) {
        StringBuilder builder = new StringBuilder(type.type().name().toLowerCase());
        for (int i = 0; i < type.dimensions(); i++) {
            builder.append("[]");
        }
        return builder.toString();
    }

    private void emitLine(String line) {
        emitIndent();
        out.append(line).append('\n');
    }

    private void emitIndent() {
        out.append("    ".repeat(Math.max(0, indent)));
    }

    private boolean isSizedArray(TypeNode type) {
        return type != null && type.isArray() && type.dimensions().stream().anyMatch(Objects::nonNull);
    }

    private String javaElementType(SemanticType type) {
        return switch (type) {
            case INTEGER -> "long";
            case DECIMAL -> "double";
            case TEXT -> "String";
            case BOOLEAN -> "boolean";
            default -> "Object";
        };
    }

    private TypeInfo toTypeInfo(TypeNode type) {
        int dimensions = (type.dimensions() == null) ? 0 : type.dimensions().size();
        return switch (type.name().toUpperCase()) {
            case "INTEGER" -> new TypeInfo(SemanticType.INTEGER, dimensions);
            case "DECIMAL" -> new TypeInfo(SemanticType.DECIMAL, dimensions);
            case "BOOLEAN" -> new TypeInfo(SemanticType.BOOLEAN, dimensions);
            case "TEXT" -> new TypeInfo(SemanticType.TEXT, dimensions);
            case "VOID" -> new TypeInfo(SemanticType.VOID, dimensions);
            default -> {
                throw new IllegalArgumentException("Unsupported type: " + type.name());
            }
        };
    }

    private String toClassName(String stem) {
        StringBuilder result = new StringBuilder();
        boolean capitalize = true;
        for (int i = 0; i < stem.length(); i++) {
            char c = stem.charAt(i);
            if (!Character.isJavaIdentifierPart(c)) {
                capitalize = true;
                continue;
            }
            if (result.isEmpty() && !Character.isJavaIdentifierStart(c)) {
                result.append('F');
            }
            result.append(capitalize ? Character.toUpperCase(c) : c);
            capitalize = false;
        }
        if (result.isEmpty()) {
            result.append("Source");
        }
        return result.toString();
    }

}