package assignment2;

import java.util.List;
import java.lang.Error;

public class CallExpr implements Expr {
    public final String name;
    public final List<Expr> arguments;

    public CallExpr(String name, List<Expr> arguments) {
        this.name = name;
        this.arguments = arguments;
    }

    @Override
    public String generateCode(SymbolTable symbolTable, SymbolTable globalSymbolTable) {
        StringBuilder code = new StringBuilder();

        code.append("PUSHIMM 0\n"); // Push a dummy return address
        for (Expr arg : this.arguments) {
            code.append(arg.generateCode(symbolTable, globalSymbolTable));
        }

        code.append("LINK\n"); // (fbr added to top of stack) stack[sp] = fbr, fbr = sp, sp = sp + 1,
        code.append("JSR ").append(name).append("\n"); // saves pc + 1 to stack and jumps to label
        code.append("POPFBR\n"); // sp = sp-1, sp = stack[fbr]; // ???sets pc+1 to be new 0-index, sp = pc+1
        code.append("ADDSP -").append(arguments.size()).append("\n");

        return code.toString();
    }

    @Override
    public Type getType(SymbolTable symbolTable, SymbolTable globalSymbolTable) throws CompileException {
        String[] methodAttr = globalSymbolTable.lookup(name);
        if (methodAttr == null) {
            throw new CompileException("Unknown method: " + name);
        }

        if (arguments.size() != methodAttr.length - 1) {
            throw new CompileException("Wrong number of arguments for method: " + name);
        }
        
        for (int i = 0; i < arguments.size(); i++) {
            Type expectedType = Type.fromString(methodAttr[i + 1]);
            Type actualType = arguments.get(i).getType(symbolTable, globalSymbolTable);

            if (expectedType != actualType) {
                throw new CompileException("Argument type mismatch in method '" + name + 
                    "': expected " + expectedType + " but got " + actualType);
            }
        }
        String returnType = globalSymbolTable.getReturnType(name);
        return Type.fromString(returnType);
    }
}