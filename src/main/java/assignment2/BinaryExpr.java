package assignment2;

import java.util.List;
import java.lang.Error;

public class BinaryExpr implements Expr {
    public final String operator;
    public final Expr left, right;

    public BinaryExpr(Expr left, String operator, Expr right) {
        this.left = left;
        this.operator = operator;
        this.right = right;

        if (left == null) {
            throw new CompileException("Left hand side is null");
        }
        if (right == null) {
            throw new CompileException("Right hand side is null");
        }
        if (!List.of("+", "-", "*", "/", "%", "<", ">", "=", "&", "|").contains(operator)) {
            throw new CompileException("Unsupported binary operator: " + operator);
        }
    }

    @Override
    public Type getType(SymbolTable symbolTable, SymbolTable globalSymbolTable) throws CompileException {
        Type l = left.getType(symbolTable, globalSymbolTable);
        Type r = right.getType(symbolTable, globalSymbolTable);

        if (l == Type.STRING && r == Type.INT) {
            if (!operator.equals("*")) {
                throw new CompileException("Operator " + operator + " not supported for types " + l + " " + r);
            }
            return Type.STRING;
        }

        if (!l.equals(r)) {
            throw new CompileException("Type mismatch: " + l + " vs " + r + " for operator " + operator);
        }
        if (operator.equals("+")) {
            if (l == Type.INT || l == Type.STRING) return l;
        } else if(operator.equals("<") || operator.equals(">") || operator.equals("=")) {
            if (l == Type.INT || l == Type.STRING) return Type.BOOL;
        } else if (List.of("-", "*", "/", "%").contains(operator)) {
            if (l != Type.INT) throw new CompileException("Operator " + operator + " not supported for type " + l);
            return Type.INT;
        } else if (List.of("&", "|").contains(operator)) {
            if (l != Type.BOOL) throw new CompileException("Operator " + operator + " not supported for type " + l);
            return Type.BOOL;
        }
        throw new CompileException("Unknown binary operator: " + operator);
    }

    @Override
    public String generateCode(SymbolTable symbolTable, SymbolTable globalSymbolTable) throws CompileException {
        Type l = left.getType(symbolTable, globalSymbolTable);
        Type r = right.getType(symbolTable, globalSymbolTable);

        String lCode = left.generateCode(symbolTable, globalSymbolTable);
        String rCode = right.generateCode(symbolTable, globalSymbolTable);

        if (l == Type.STRING && operator.equals("*") && r == Type.INT) {
            if (!globalSymbolTable.containsKey("repeatString")) {
                globalSymbolTable.enter("repeatString", new String[] {"String", "String", "int"});
            }
            Expr concat = new CallExpr("repeatString", List.of(left, right));
            return concat.generateCode(symbolTable, globalSymbolTable);
        } else if (l == Type.STRING && operator.equals("+") && r == Type.STRING) {
            if (!globalSymbolTable.containsKey("concatString")) {
                globalSymbolTable.enter("concatString", new String[] {"String", "String", "String"});
            }
            Expr concat = new CallExpr("concatString", List.of(left, right));
            return concat.generateCode(symbolTable, globalSymbolTable);
        } else if (l == Type.STRING && operator.equals("=") && r == Type.STRING) {
            if (!globalSymbolTable.containsKey("equalString")) {
                globalSymbolTable.enter("equalString", new String[] {"String", "String", "String"});
            }
            Expr concat = new CallExpr("equalString", List.of(left, right));
            return concat.generateCode(symbolTable, globalSymbolTable);
        }  else if (l == Type.STRING && operator.equals("<") && r == Type.STRING) {
            if (!globalSymbolTable.containsKey("lessString")) {
                globalSymbolTable.enter("lessString", new String[] {"String", "String", "String"});
            }
            Expr concat = new CallExpr("lessString", List.of(left, right));
            return concat.generateCode(symbolTable, globalSymbolTable);
        }  else if (l == Type.STRING && operator.equals(">") && r == Type.STRING) {
            if (!globalSymbolTable.containsKey("greaterString")) {
                globalSymbolTable.enter("greaterString", new String[] {"String", "String", "String"});
            }
            Expr concat = new CallExpr("greaterString", List.of(left, right));
            return concat.generateCode(symbolTable, globalSymbolTable);
        }
        return lCode + rCode + opcode();
    }

    private String opcode() {
        switch (operator) {
            case "+": return "ADD\n";
            case "-": return "SUB\n";
            case "*": return "TIMES\n";
            case "/": return "DIV\n";
            case "%": return "MOD\n";
            case "<": return "LESS\n";
            case ">": return "GREATER\n";
            case "=": return "EQUAL\n";
            case "&": return "AND\n";
            case "|": return "OR\n";
            default: throw new CompileException("Unknown op: " + operator);
        }
    }

    public String lenString(String conditionLabel, String jumpLabel) {
        return 
            "DUP\nPUSHIND\nISNIL\n"+
            "JUMPC "+conditionLabel+"\n"+
            "SWAP\nPUSHIMM 1\nADD\nSWAP\nPUSHIMM 1\nADD\n"+
            "JUMP "+jumpLabel+"\n";
    }
}

