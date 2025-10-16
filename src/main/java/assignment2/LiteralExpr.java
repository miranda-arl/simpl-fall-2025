package assignment2;

import java.lang.Error;

public class LiteralExpr implements Expr {
    public final Object value;

    public LiteralExpr(Object value) {
        this.value = value;
    }

    @Override
    public Type getType(SymbolTable symbolTable, SymbolTable globalSymbolTable) throws CompileException {
        if (value instanceof Integer) return Type.INT;
        if (value instanceof Boolean) return Type.BOOL;
        if (value instanceof String) return Type.STRING;
        throw new CompileException("Unknown literal type: " + value);
    }

    @Override
    public String generateCode(SymbolTable symbolTable, SymbolTable globalSymbolTable) throws CompileException {
        if (value instanceof Integer) return "PUSHIMM " + value + "\n";
        if (value instanceof Boolean) return "PUSHIMM " + ((boolean)value ? 1 : 0) + "\n";
        if (value instanceof String) return "PUSHIMMSTR \"" + value + "\"\n";
        throw new CompileException("Cannot generate code for literal: " + value);
    }
}