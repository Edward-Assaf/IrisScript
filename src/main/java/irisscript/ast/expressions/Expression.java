package irisscript.ast.expressions;

import irisscript.ast.AstNode;

public sealed interface Expression extends AstNode permits LiteralExpression, IdentifierExpression, BinaryExpression,
        UnaryExpression, CallExpression, AssignmentExpression, GroupingExpression, ArrayLiteralExpression,
        IndexExpression, SizeExpression, LengthExpression, TernaryExpression, SwitchExpression, ErrorExpression {

}