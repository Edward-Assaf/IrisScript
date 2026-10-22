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
        String brackets = "[]".repeat(type.dimensions().size());
        return switch (type.name()) {
            case "integer" -> "long" + brackets;
            case "decimal" -> "double" + brackets;
            case "text" -> "String" + brackets;
            case "boolean" -> "boolean" + brackets;
            default -> throw new IllegalArgumentException("Unsupported IrisScript type: " + type.name());
        };
    }

}