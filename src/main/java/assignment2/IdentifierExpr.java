package assignment2;

import java.lang.Error;

public class IdentifierExpr implements Expr {
    public final String name;

    public IdentifierExpr(String name) {
        this.name = name;
    }

    @Override
    public Type getType(SymbolTable symbolTable, SymbolTable globalSymbolTable)  throws CompileException {
        if (!symbolTable.containsKey(name)) {
            throw new CompileException("Undeclared variable: " + name);
        }
        String type = symbolTable.getReturnType(name);
        return Type.fromString(type);
    }

    @Override
    public String generateCode(SymbolTable symbolTable, SymbolTable globalSymbolTable) throws CompileException {
        if (!symbolTable.containsKey(name)) {
            throw new CompileException("Undeclared variable: " + name);
        }
        int offset = Integer.parseInt(symbolTable.getLocation(name));
        return "PUSHOFF " + offset + "\n";
    }

    public String getName() {
        return name;
    }
}