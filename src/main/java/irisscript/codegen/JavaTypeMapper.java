package irisscript.codegen;

import irisscript.ast.types.TypeNode;
import irisscript.semantic.SemanticType;
import irisscript.semantic.TypeInfo;

final class JavaTypeMapper {

    public String map(TypeNode type) {
        if (!type.isArray()) {
            return mapElementType(type.name());
        }
        int dimensions = type.dimensions() == null ? 0 : type.dimensions().size();
        return mapElementType(type.name()) + "[]".repeat(dimensions);
    }

    public String map(TypeInfo type) {
        if (type == null) {
            return "Object";
        }
        if (!type.isArray()) {
            return mapElementType(type.type());
        }
        return mapElementType(type.type()) + "[]".repeat(type.dimensions());
    }

    public String mapElementType(SemanticType type) {
        if (type == null) {
            return "Object";
        }
        return switch (type) {
            case INTEGER -> "long";
            case DECIMAL -> "double";
            case TEXT -> "String";
            case BOOLEAN -> "boolean";
            case VOID -> "void";
            default -> "Object";
        };
    }

    public String mapElementType(String name) {
        if (name == null) {
            return "Object";
        }
        return switch (name.toLowerCase()) {
            case "integer" -> "long";
            case "decimal" -> "double";
            case "text" -> "String";
            case "boolean" -> "boolean";
            case "void" -> "void";
            default -> throw new IllegalArgumentException("Unsupported IrisScript type: " + name);
        };
    }

    public String mapBoxed(TypeInfo type) {
        if (type == null) {
            return "Object";
        }
        if (type.isArray()) {
            return map(type);
        }
        return switch (type.type()) {
            case INTEGER -> "Long";
            case DECIMAL -> "Double";
            case BOOLEAN -> "Boolean";
            case TEXT -> "String";
            default -> throw new IllegalStateException("Unsupported type for Java boxed type: " + type.type());
        };
    }

    public String mapBoxedUnwrapMethod(TypeInfo type) {
        if (type == null) {
            return "";
        }
        return switch (type.type()) {
            case INTEGER -> "longValue()";
            case DECIMAL -> "doubleValue()";
            case BOOLEAN -> "booleanValue()";
            default -> throw new IllegalStateException("Unsupported type for Java unboxing: " + type.type());
        };
    }

}