package irisscript.codegen;

public final class JavaNameMangler {

    public String mangle(String sourceName) {
        StringBuilder result = new StringBuilder("iris$");
        for (int i = 0; i < sourceName.length(); i++) {
            char c = sourceName.charAt(i);
            if ((i == 0 ? Character.isJavaIdentifierStart(c) : Character.isJavaIdentifierPart(c))) {
                result.append(c);
            } else {
                result.append("_u").append(String.format("%04x", (int) c));
            }
        }
        return result.toString();
    }

}