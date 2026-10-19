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
import irisscript.semantic.SemanticModel;
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

    private static final String GLOBAL_CLASS = "IrisGlobals";

    public JavaCodeGenerator(SemanticModel semanticModel) {
        this.semanticModel = Objects.requireNonNull(semanticModel);
    }

    public List<GeneratedSource> generate(List<CompilationUnit> units, SourceManager sources) {
        classNames.clear();
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
                        && variable.isGlobal() && variable.initializer() != null);
        if (hasGlobalInitializers) {
            emitLine("");
            emitLine("static {");
            indent++;
            for (CompilationUnit unit : units) {
                for (Statement statement : unit.statements()) {
                    if (statement instanceof VariableDeclarationStatement variable
                            && variable.isGlobal() && variable.initializer() != null) {
                        emitIndent();
                        out.append(nameMangler.mangle(variable.name())).append(" = ");
                        emitExpression(variable.initializer());
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
                return !variable.isGlobal() && variable.initializer() != null;
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
                if (!variable.isGlobal() && variable.initializer() != null) {
                    emitIndent();
                    out.append(nameMangler.mangle(variable.name())).append(" = ");
                    emitExpression(variable.initializer());
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
        emitLine("");
        String visibility = function.isGlobal() ? "public " : "";
        StringBuilder signature = new StringBuilder(visibility + "static "
                + typeMapper.map(function.returnType()) + " " + nameMangler.mangle(function.name()) + "(");
        for (int i = 0; i < function.parameters().size(); i++) {
            if (i > 0) {
                signature.append(", ");
            }
            ParameterDeclaration parameter = function.parameters().get(i);
            signature.append(typeMapper.map(parameter.type())).append(' ').append(nameMangler.mangle(parameter.name()));
        }
        signature.append(") {");
        emitLine(signature.toString());
        indent++;
        for (Statement statement : function.body().statements()) {
            emitStatement(statement);
        }
        indent--;
        emitLine("}");
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
                    emitExpression(variable.initializer());
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
                    emitExpression(ret.value());
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
                emitExpression(terminal.expression());
                out.append(");\n");
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
                out.append(switch (unary.operator()) {
                    case POSITIVE -> "+";
                    case NEGATIVE -> "-";
                    case NOT -> "!";
                });
                emitExpression(unary.operand());
            }
            case BinaryExpression binary -> {
                String type = semanticModel.expressionTypeName(binary.left()).orElse("");
                if ((binary.operator() == BinaryOperator.EQUAL || binary.operator() == BinaryOperator.NOT_EQUAL)
                        && type.equals("TEXT")) {
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
                emitResolvedReference(assignment, assignment.name());
                out.append(" = ");
                emitExpression(assignment.value());
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
                    emitExpression(call.arguments().get(i));
                }
                out.append(')');
            }
            case ErrorExpression ignored ->
                throw new IllegalStateException("Cannot generate Java from ErrorExpression");
        }
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

    private void emitLine(String line) {
        emitIndent();
        out.append(line).append('\n');
    }

    private void emitIndent() {
        out.append("    ".repeat(Math.max(0, indent)));
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