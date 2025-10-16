package assignment2;

import java.lang.Error;
import java.util.List;

public class UnaryExpr implements Expr {
    public final String operator;
    public final Expr operand;

    public UnaryExpr(String operator, Expr operand) {
        this.operator = operator;
        this.operand = operand;
    }

    @Override
    public String generateCode(SymbolTable symbolTable, SymbolTable globalSymbolTable) throws CompileException {
        String code = operand.generateCode(symbolTable, globalSymbolTable);
        String opCode;

        switch (operator) {
            case "!":
            case "~": // for string, reverse. identifier, bool
                Type operandType = operand.getType(symbolTable, globalSymbolTable);
                
                if (operandType == Type.STRING) {
                    if (!globalSymbolTable.containsKey("reverseString")) {
                        globalSymbolTable.enter("reverseString", new String[] {"String", "String"});
                    }
                    Expr reverse = new CallExpr("reverseString", List.of(operand));
                    return reverse.generateCode(symbolTable, globalSymbolTable);
                } else if (operandType == Type.INT) {
                    return code + "PUSHIMM -1\nTIMES\n";
                } else {
                    opCode = "NOT\n";
                }
                break;
            default:
                throw new CompileException("Unknown unary operator: " + operator);
        }

        return code + opCode;
    }

    @Override
    public Type getType(SymbolTable symbolTable, SymbolTable globalSymbolTable) throws CompileException {
        Type operandType = operand.getType(symbolTable, globalSymbolTable);
        switch (operator) {
            case "!":
                if (operandType != Type.BOOL) {
                    throw new CompileException("Type error: '!' operator requires a boolean operand.");
                }
                return Type.BOOL;
            case "~":
                if (operandType != Type.INT && operandType != Type.STRING) {
                    throw new CompileException("Type error: '~' operator requires an integer or string operand.");
                }
                return operandType;
            default:
                throw new CompileException("Unknown unary operator: " + operator);
        }
    }
}
