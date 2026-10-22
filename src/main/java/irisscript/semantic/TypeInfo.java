package irisscript.semantic;

import java.util.Objects;

public final record TypeInfo(SemanticType type, int dimensions) {

    public TypeInfo {
        Objects.requireNonNull(type, "type must not be null");
        if (dimensions < 0) {
            throw new IllegalArgumentException("Dimensions cannot be negative");
        }
    }

    public boolean isArray() {
        return dimensions > 0;
    }

    public TypeInfo elementType() {
        if (dimensions == 0) {
            throw new IllegalStateException("Scalar types have no element type");
        }
        return new TypeInfo(type, dimensions - 1);
    }

}