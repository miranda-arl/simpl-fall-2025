package assignment2;

public interface Expr { 
    Type getType(SymbolTable symbolTable, SymbolTable globalSymbolTable) throws CompileException;
    String generateCode(SymbolTable symbolTable, SymbolTable globalSymbolTable) throws CompileException;
}
