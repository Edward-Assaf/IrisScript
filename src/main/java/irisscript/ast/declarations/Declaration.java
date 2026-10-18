package irisscript.ast.declarations;

import irisscript.ast.AstNode;

public sealed interface Declaration extends AstNode permits FunctionDeclaration, ErrorDeclaration {

}