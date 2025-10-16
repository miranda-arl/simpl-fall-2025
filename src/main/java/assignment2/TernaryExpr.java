package assignment2;

import java.util.UUID;
import java.lang.Error;

public class TernaryExpr implements Expr {
    public final Expr condition;
    public final Expr thenBranch;
    public final Expr elseBranch;

    public TernaryExpr(Expr condition, Expr thenBranch, Expr elseBranch) throws CompileException {
        this.condition = condition;
        this.thenBranch = thenBranch;
        this.elseBranch = elseBranch;

        if (condition == null) {
            throw new CompileException("Missing condition in ternary operator");
        }
        if (thenBranch == null) {
            throw new CompileException("Missing expression after '?' in ternary operator");
        }
        if (elseBranch == null) {
            throw new CompileException("Missing expression after ':' in ternary operator");
        }
    }

    @Override
    public String generateCode(SymbolTable symbolTable, SymbolTable globalSymbolTable) throws CompileException {
        String thenLabel = "then_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String endLabel = "end_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        String conditionCode = condition.generateCode(symbolTable, globalSymbolTable);
        String thenCode = thenBranch.generateCode(symbolTable, globalSymbolTable);
        String elseCode = elseBranch.generateCode(symbolTable, globalSymbolTable);

        return conditionCode +
               "JUMPC " + thenLabel + "\n" +
               elseCode +
               "JUMP " + endLabel + "\n" +
               thenLabel + ":\n" +
               thenCode +
               endLabel + ":\n";
    }
    
    @Override
    public Type getType(SymbolTable symbolTable, SymbolTable globalSymbolTable) throws CompileException {
        Type condType = condition.getType(symbolTable, globalSymbolTable);
        Type thenType = thenBranch.getType(symbolTable, globalSymbolTable);
        Type elseType = elseBranch.getType(symbolTable, globalSymbolTable);

        if (!condType.equals(Type.BOOL)) {
            throw new CompileException("Condition must be boolean");
        }
        if (!thenType.equals(elseType)) {
            throw new CompileException("Branches must have same type");
        }
        return thenType;
    }
}
