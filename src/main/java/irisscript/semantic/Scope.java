package irisscript.semantic;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class Scope {

    private final Scope parent;

    private final Map<String, Symbol> symbols = new LinkedHashMap<>();

    public Scope(Scope parent) {
        this.parent = parent;
    }

    public Scope getParent() {
        return parent;
    }

    public boolean define(Symbol symbol) {
        Objects.requireNonNull(symbol, "symbol must not be null");
        if (symbols.containsKey(symbol.name())) {
            return false;
        }
        symbols.put(symbol.name(), symbol);
        return true;
    }

    public Symbol getExistingSymbol(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return symbols.get(name);
    }

    public Optional<Symbol> resolve(String name) {
        Objects.requireNonNull(name, "name must not be null");
        Symbol symbol = symbols.get(name);
        if (symbol != null) {
            return Optional.of(symbol);
        }
        if (parent != null) {
            return parent.resolve(name);
        }
        return Optional.empty();
    }

    public Optional<Symbol> resolveLocal(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return Optional.ofNullable(symbols.get(name));
    }

}