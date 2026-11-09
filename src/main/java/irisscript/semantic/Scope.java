package irisscript.semantic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class Scope {

    private final Scope parent;

    private final Map<String, Symbol> variables = new LinkedHashMap<>();

    private final Map<String, List<Symbol>> functions = new LinkedHashMap<>();

    public Scope(Scope parent) {
        this.parent = parent;
    }

    public Scope getParent() {
        return parent;
    }

    public boolean define(Symbol symbol) {
        Objects.requireNonNull(symbol, "symbol must not be null");
        if (symbol.kind() == SymbolKind.FUNCTION) {
            return defineFunction(symbol);
        }
        if (variables.containsKey(symbol.name()) || functions.containsKey(symbol.name())) {
            return false;
        }
        variables.put(symbol.name(), symbol);
        return true;
    }

    public Symbol getDuplicateSymbol(Symbol symbol) {
        Objects.requireNonNull(symbol, "symbol must not be null");
        if (symbol.kind() != SymbolKind.FUNCTION) {
            return variables.get(symbol.name());
        }
        if (!functions.containsKey(symbol.name())) {
            return null;
        }
        for (Symbol existing : functions.get(symbol.name())) {
            if (sameSignature(existing, symbol)) {
                return existing;
            }
        }
        return null;
    }

    public Optional<Symbol> resolveVariable(String name) {
        Objects.requireNonNull(name, "name must not be null");
        Symbol symbol = variables.get(name);
        if (symbol != null) {
            return Optional.of(symbol);
        }
        if (parent != null) {
            return parent.resolveVariable(name);
        }
        return Optional.empty();
    }

    public Optional<Symbol> resolveVariableLocal(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return Optional.ofNullable(variables.get(name));
    }

    public List<Symbol> resolveFunctions(String name) {
        Objects.requireNonNull(name, "name must not be null");
        if (functions.containsKey(name)) {
            return functions.get(name);
        }
        if (parent != null) {
            return parent.resolveFunctions(name);
        }
        return List.of();
    }

    public List<Symbol> resolveFunctionLocal(String name) {
        Objects.requireNonNull(name, "name must not be null");
        if (!functions.containsKey(name)) {
            return List.of();
        }
        return functions.get(name);
    }

    private boolean defineFunction(Symbol symbol) {
        if (variables.containsKey(symbol.name())) {
            return false;
        }
        if (!functions.containsKey(symbol.name())) {
            functions.put(symbol.name(), new ArrayList<>(List.of(symbol)));
            return true;
        }
        for (Symbol existing : functions.get(symbol.name())) {
            if (sameSignature(existing, symbol)) {
                return false;
            }
        }
        functions.get(symbol.name()).add(symbol);
        return true;
    }

    private boolean sameSignature(Symbol first, Symbol second) {
        if (first.parameterTypes().size() != second.parameterTypes().size()) {
            return false;
        }
        int numberOfParameters = first.parameterTypes().size();
        for (int i = 0; i < numberOfParameters; i++) {
            TypeInfo firstType = first.parameterTypes().get(i);
            TypeInfo secondType = second.parameterTypes().get(i);
            if (!firstType.equals(secondType)) {
                return false;
            }
        }
        return true;
    }

}