package irisscript.codegen;

import irisscript.ast.types.TypeNode;

final class JavaTypeMapper {

    public String map(TypeNode type) {
        if (!type.isArray()) {
            return switch (type.name()) {
                case "integer" -> "long";
                case "decimal" -> "double";
                case "text" -> "String";
                case "boolean" -> "boolean";
                case "void" -> "void";
                default -> throw new IllegalArgumentException("Unsupported IrisScript type: " + type.name());
            };
        }
        return switch (type.name()) {
            case "integer" -> "long[]";
            case "decimal" -> "double[]";
            case "text" -> "String[]";
            case "boolean" -> "boolean[]";
            default -> throw new IllegalArgumentException("Unsupported IrisScript type: " + type.name());
        };
    }

}