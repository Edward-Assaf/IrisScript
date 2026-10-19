package irisscript.ast.statements;

import irisscript.ast.AstNode;

public sealed interface Statement extends AstNode permits BlockStatement, VariableDeclarationStatement, IfStatement,
                WhileStatement, BreakStatement, SkipStatement, ReturnStatement, ExpressionStatement, TerminalStatement,
                ErrorStatement {

}