package assignment2;

import java.io.FileWriter;
import java.io.PrintWriter;
import edu.utexas.cs.sam.io.SamTokenizer;
import edu.utexas.cs.sam.io.Tokenizer;
import edu.utexas.cs.sam.io.Tokenizer.TokenType;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;

import java.io.IOException;
import java.lang.Error;

public class LiveOak2Compiler
{
	static SymbolTable globalSymbolTable = new SymbolTable();
	static String currentMethodName = "";
	static String currentMethodEndLabel = "";
	static Deque<String> breakLabelStack = new ArrayDeque<>();

	static List<String> formalsList = new java.util.ArrayList<>();
	static int formalsCount = 0; // number of formals in current method
	static int localVarCount = 0; // number of local variables in current method
	static int stackPointer = 0;
	static LabelGenerator labelGen = new LabelGenerator();

	public static void main(String[] args) throws IOException {
		if (args.length != 2) 
		{
			System.err.println("usage: java LiveOak2Compiler <source-file>");
			return;
		}

		String fileName = args[0];
		String pgm = compiler(fileName);

		// write program to a new file
		try (PrintWriter out = new PrintWriter(new FileWriter(args[1]))) {
			out.print(pgm);
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Error writing to output file");
			System.err.println("Error writing to output file");
		}
	}

	static String compiler(String fileName) 
	{
		//returns SaM code for program in file
		try 
		{
			SamTokenizer f1 = new SamTokenizer(fileName, SamTokenizer.TokenizerOptions.PROCESS_STRINGS);
			SamTokenizer f2 = new SamTokenizer(fileName, SamTokenizer.TokenizerOptions.PROCESS_STRINGS);
			String pgm = getProgram(f1, f2);
			return pgm;
		} 
		catch (IOException e) 
		{
			return "ERROR\n";
		}
		catch (Error e)
		{
			System.err.println("Failed to compile " + fileName);
			throw e;
		}
	}

	static String getProgram(SamTokenizer f1, SamTokenizer f2) throws IOException
	{
		try
		{
			globalSymbolTable = new SymbolTable();
			stackPointer = 0;

			// First pass: collect function declarations
			while (f1.peekAtKind() != TokenType.EOF) {
				if (!f1.peekAtKind().equals(TokenType.WORD)) {
					throw new CompileException("invalid function declaration");
				}
				parseMethodSignature(f1); // Collect function name and param count
				f1.check('{');
				// Skip to the end of the method body
				int braceCount = 1;
				while (braceCount > 0) {
					if (f1.peekAtKind() == TokenType.EOF) {
						throw new CompileException("unexpected end of file");
					}
					if (f1.check('{')) {
						braceCount++;
					} else if (f1.check('}')) {
						braceCount--;
					} else {
						f1.skipToken(); // consume other tokens
					}
				}

			}

			// Second pass: generate code and validate function calls
			String pgm="";

			pgm += "start:\nJUMP main\n";
			// Add code to jump to main
			while (f2.peekAtKind() != TokenType.EOF) {
				f2.check("(");
				pgm += getMethod(f2); // Now you can validate calls using the functionTable
				f2.check(")");
			}

			if (globalSymbolTable.containsKey("concatString")) {
				pgm += concatString();
			}
			if (globalSymbolTable.containsKey("repeatString")) {
				pgm += repeatString();
			}
			if (globalSymbolTable.containsKey("reverseString")) {
				pgm += reverseString();
			}
			if (globalSymbolTable.containsKey("equalString")) {
				pgm += equalString();
			}
			if (globalSymbolTable.containsKey("lessString")) {
				pgm += lessString();
			}
			if (globalSymbolTable.containsKey("greaterString")) {
				pgm += greaterString();
			}

			// System.out.println("pgm="+pgm);
			return pgm;
		}
		catch(Error e)
		{
			throw e;
		}		
	}

	static void parseMethodSignature(SamTokenizer f) throws Error { 
		formalsList = new java.util.ArrayList<>();
		String returnType = f.getWord();
		if (!returnType.equals("int") && !returnType.equals("String") && !returnType.equals("bool")) {
			throw new CompileException("invalid return type");
		}

		if (!f.peekAtKind().equals(TokenType.WORD)) {
			throw new CompileException("invalid method name");
		}

		String methodName = f.getWord();
		f.check('(');

		int paramCount = 0;

		while (!f.test(')')) {
			if (!f.peekAtKind().equals(TokenType.WORD)) {
				throw new CompileException("invalid parameter type");
			}
			String paramType = f.getWord(); // parameter type
			if (!paramType.equals("int") && !paramType.equals("String") && !paramType.equals("bool")) {
				throw new CompileException("invalid parameter type");
			}
			String paramName = f.getWord(); // parameter name
			formalsList.add(paramType);
			paramCount++;

			if (!f.check(',')) {
				break;
			}
		}

		f.check(')');

		if (methodName.equals("main") && paramCount != 0) {
			throw new CompileException("main method cannot have formals");
		}

		String[] attr = new String[paramCount + 1];
		attr[0] = returnType;
		for (int i = 1; i < paramCount + 1; i++) {
			attr[i] = formalsList.get(i - 1);
		}

		globalSymbolTable.enter(methodName, attr);
	}

	static String getMethod(SamTokenizer f) throws CompileException
	{
		SymbolTable methodSymbolTable = new SymbolTable();
		formalsCount = 0;
		localVarCount = 0;
		currentMethodName = "";

		//TODO: add code to convert a method declaration to SaM code.
		//TODO: add appropriate exception handlers to generate useful error msgs.
		try {
			String returnType = f.getWord();
			
			String methodName = f.getWord(); 
			currentMethodName = methodName;
			
			currentMethodEndLabel = newLabel("end_" + methodName);

			f.match('(');
			String formals = getFormals(f, methodSymbolTable);
			f.match(')');

			if (!f.check('{')) {
				throw new CompileException("missing opening brace for body");
			}

			String declarations = "";
			while (f.peekAtKind().equals(TokenType.WORD)) { // get all declarations
				declarations = getDeclarations(f, methodSymbolTable);
			}

			if (!f.check('{')) {
				throw new CompileException("missing opening brace for block");
			}

			String statements = "";
			while (!f.test('}')) {
				StatementResult statementResult = getStatements(f, methodSymbolTable);
				if (!statementResult.guaranteesReturn) {
					throw new CompileException("Method '" + methodName + "' is missing a return statement");
				}
				statements += statementResult.code;
			}
		
			if (!f.check('}')) {
				throw new CompileException("missing closing brace for statements");
			}

			if (!f.check('}')) { 
				throw new CompileException("missing closing brace for method");
			}

			String prologue = "ADDSP ";
			if (methodName.equals("main")) {
				prologue += (localVarCount + 1) + "\n";
			} else {
				prologue += (localVarCount) + "\n";
			}
			
			int rvIndex = 0; // index of return value
			if (currentMethodName.equals("main")) {
				rvIndex = 0; // sp
			} else {
				rvIndex = -(formalsCount+1);
			}

			String epilogue = 
			currentMethodEndLabel + ":\n" + 
			"STOREOFF " + rvIndex + "\n" + 
			"ADDSP -" + localVarCount + "\n";

			String result = methodName + ":\n" + prologue + formals + declarations + statements + epilogue;
			if (currentMethodName.equals("main")) {
				result += "STOP\n";
			} else {
				result += "JUMPIND\n"; // return ; sp = sp-1, pc = stack[sp]
			}

			return result;
		} catch (Error e) {
			throw e;
		}
	}

	static Expr parseExpr(SamTokenizer f, SymbolTable symbols) throws CompileException {
		return parseTernary(f, symbols);
	}

	static Expr parseTernary(SamTokenizer f, SymbolTable symbols) throws CompileException {
		Expr condition = parseOr(f, symbols);
		if (f.check('?')) {
			Expr thenExpr = parseExpr(f, symbols);
			f.match(':');
			Expr elseExpr = parseExpr(f, symbols);
			
			return new TernaryExpr(condition, thenExpr, elseExpr);
		}
		return condition;
	}

	static Expr parseOr(SamTokenizer f, SymbolTable symbols) throws CompileException {
		Expr left = parseAnd(f, symbols);
		while (f.check('|')) {
			Expr right = parseAnd(f, symbols);
			left = new BinaryExpr(left, "|", right);
			if (!f.test(')')) {
				throw new CompileException("BinaryExpr not enclosed in parenthesis");
			}
		}
		return left;
	}

	static Expr parseAnd(SamTokenizer f, SymbolTable symbols) throws CompileException {
		Expr left = parseEquality(f, symbols);
		while (f.check('&')) {
			Expr right = parseEquality(f, symbols);
			left = new BinaryExpr(left, "&", right);
			if (!f.test(')')) {
				throw new CompileException("BinaryExpr not enclosed in parenthesis");
			}
		}
		return left;
	}

	static Expr parseEquality(SamTokenizer f, SymbolTable symbols) throws CompileException {
		Expr left = parseAdd(f, symbols);
		while (f.test('=') || f.test('<') || f.test('>')) {
			String op = "" + f.getOp();
			Expr right = parseAdd(f, symbols);
			left = new BinaryExpr(left, op, right);
			if (!f.test(')')) {
				throw new CompileException("BinaryExpr not enclosed in parenthesis");
			}
		}
		return left;
	}

	static Expr parseAdd(SamTokenizer f, SymbolTable symbols) throws CompileException {
		Expr left = parseMul(f, symbols);
		while (f.test('+') || f.test('-')) {
			String op = "" + f.getOp();
			Expr right = parseMul(f, symbols);
			left = new BinaryExpr(left, op, right);
			if (!f.test(')')) {
				throw new CompileException("BinaryExpr not enclosed in parenthesis");
			}
		}
		return left;
	}

	static Expr parseMul(SamTokenizer f, SymbolTable symbols) throws CompileException {
		Expr left = parseUnary(f, symbols);
		while (f.test('*') || f.test('/') || f.test('%')) {
			String op = "" + f.getOp();
			Expr right = parseUnary(f, symbols);
			left = new BinaryExpr(left, op, right);
			if (!f.test(')')) {
				throw new CompileException("BinaryExpr not enclosed in parenthesis");
			}
		}
		return left;
	}

	static Expr parseUnary(SamTokenizer f, SymbolTable symbols) throws CompileException {		
		if (f.test('!') || f.test('~')) {
			String op = "" + f.getOp();
			Expr right = parseUnary(f, symbols);
			return new UnaryExpr(op, right);
		}
		return parsePrimary(f, symbols);
	}

	static Expr parsePrimary(SamTokenizer f, SymbolTable symbols) throws CompileException {
		switch (f.peekAtKind()) {
			case INTEGER:
				return new LiteralExpr(f.getInt());
			case STRING:
				return new LiteralExpr(f.getString());
			case WORD:
				String word = f.getWord();
				if (word.equals("true")) return new LiteralExpr(true);
				if (word.equals("false")) return new LiteralExpr(false);

				// Peek for method invocation
				if (f.check('(')) {
					List<Expr> actuals = new ArrayList<>();
					if (!f.test(')')) {
						actuals = parseActuals(f, symbols);
					}
					f.match(')');
					return new CallExpr(word, actuals);
				}

				return new IdentifierExpr(word);
			case OPERATOR:
				if (f.check('(')) {
					Expr parsePrimary = parseExpr(f, symbols);
					if (!f.check(')')) {
						throw new CompileException("Expected closing ')' after expression");
					}
					return parsePrimary;
				}
			default:
				return null;
		}
	}

	static String getFormals(SamTokenizer f, SymbolTable symbolTable) throws CompileException {
		StringBuilder code = new StringBuilder();

		while (true) {
			if (f.peekAtKind() == Tokenizer.TokenType.WORD) {
				String type = f.getWord();
				if (!type.equals("int") && !type.equals("bool") && !type.equals("String")) {
					throw new CompileException("Unknown type in formals: " + type);
				}

				if (f.peekAtKind() != Tokenizer.TokenType.WORD) {
					throw new CompileException("Expected identifier after type in formals");
				}

				String id = f.getWord();

				// Add to symbol table
				// fetch offset from sp: 1 (rv) + localVarCount + (formalsCount - i)
				int offset = -(formalsCount + 1);

				if (globalSymbolTable.containsKey(currentMethodName)){
					offset = -(globalSymbolTable.lookup(currentMethodName).length) + formalsCount + 1; // -1 for rv in arr, +1 for rv
				}

				symbolTable.enter(id, new String[]{type, Integer.toString(offset), ""}); // before or after
				formalsList.add(type); // or add both type+id if needed
				formalsCount++;

				// If comma, continue parsing formals
				if (f.check(',')) {
					continue;
				}

				// If closing paren, done
				if (f.test(')')) {
					break;
				}

				throw new CompileException("Expected ',' or ')' after formal parameter");
			} else if (f.test(')')) {
				break;
			} else {
				throw new CompileException("Unexpected token in formals: " + f.peekAtKind());
			}
		}

		return code.toString();
	}

	static String getDeclarations(SamTokenizer f, SymbolTable symbolTable) throws CompileException {
		StringBuilder code = new StringBuilder();

		while (f.peekAtKind() == Tokenizer.TokenType.WORD) {
			String type = f.getWord();
			if (!type.equals("int") && !type.equals("bool") && !type.equals("String")) {
				throw new CompileException("Unknown type in declaration: " + type);
			}

			do {
				if (f.peekAtKind() != Tokenizer.TokenType.WORD) {
					throw new CompileException("Expected identifier after type");
				}
				String varName = f.getWord();

				if (symbolTable.containsKey(varName)) {
					throw new CompileException("Variable '" + varName + "' already declared");
				}

				// Compute stack offset: 1 (rv) + formalsCount + localVarCount
				// one for link, one for fbr
				int offset = 2  + localVarCount;
				if (currentMethodName.equals("main")) {
					offset = 1 + localVarCount; // one for rv
				}
				
				symbolTable.enter(varName, new String[] { type, Integer.toString(offset), "" });
				localVarCount++;

			} while (f.check(',')); // handle multiple vars in one declaration

			f.match(';'); // ensures semicolon ends the declaration
		}

		return code.toString();
	}

	static StatementResult getStatements(SamTokenizer f, SymbolTable symbolTable) throws CompileException {
			StringBuilder code = new StringBuilder();
			boolean guaranteesReturn = false;

			while (true) {
				try {
					switch (f.peekAtKind()) {
						case WORD: {
							String word = f.getWord();

							// --- IF Statement ---
							if (word.equals("if")) {
								f.match('(');
								Expr condition = parseExpr(f, symbolTable);
								condition.getType(symbolTable, globalSymbolTable);
								String condCode = condition.generateCode(symbolTable, globalSymbolTable);
								f.match(')');
								f.match('{');
								StatementResult thenResult = getStatements(f, symbolTable);
								String thenCode = thenResult.code;
								f.match('}');
								f.match("else");
								f.match('{');

								StatementResult elseResult = getStatements(f, symbolTable);
								String elseCode = elseResult.code;
								f.match('}');

								String thenLabel = LabelGenerator.newLabel("then");
								String elseLabel = LabelGenerator.newLabel("else");
								String nextLabel = LabelGenerator.newLabel("next");

								code.append(condCode);
								code.append("JUMPC ").append(thenLabel).append("\n");
								code.append(elseLabel).append(":\n");
								code.append(elseCode);
								code.append("JUMP ").append(nextLabel).append("\n");
								code.append(thenLabel).append(":\n");
								code.append(thenCode);
								code.append(nextLabel).append(":\n");

								if (thenResult.guaranteesReturn && elseResult.guaranteesReturn) {
									guaranteesReturn = true;
								}
								continue;
							}

							// --- WHILE Statement ---
							if (word.equals("while")) {
								f.match('(');
								String condLabel = newLabel("cond");
								String bodyLabel = newLabel("body");
								String endLabel = newLabel("endwhile");

								// Push loop labels onto stacks
								breakLabelStack.push(endLabel);

								Expr condition = parseExpr(f, symbolTable);
								condition.getType(symbolTable, globalSymbolTable);
								String condCode = condition.generateCode(symbolTable, globalSymbolTable);
								f.match(')');

								f.match('{');
								StatementResult bodyResult = getStatements(f, symbolTable);
								String bodyCode = bodyResult.code;
								f.match('}');

								// Pop loop labels off after loop
								breakLabelStack.pop();

								code.append("JUMP ").append(condLabel).append("\n");
								code.append(bodyLabel).append(":\n");
								code.append(bodyCode);
								code.append(condLabel).append(":\n");
								code.append(condCode);
								code.append("JUMPC ").append(bodyLabel).append("\n");
								code.append(endLabel).append(":\n");
								continue;
								// "headLabel:\n" + exp + "ISNIL\n"+ 
								// "JUMPC whileNextLabel\n" + stmts + 
								// "JUMP headLabel\n";
							}

							// --- BREAK Statement ---
							if (word.equals("break")) {
								f.match(';');
								if (breakLabelStack.isEmpty()) {
									throw new CompileException("break used outside of loop");
								}
								code.append("JUMP ").append(breakLabelStack.peek()).append("\n");
								continue;
							}

							// --- RETURN Statement ---
							if (word.equals("return")) {
								Expr returnExpr = parseExpr(f, symbolTable);
								Type returnType = returnExpr.getType(symbolTable, globalSymbolTable);
								String returnCode = returnExpr.generateCode(symbolTable, globalSymbolTable);
								f.match(';');

								String declaredReturnType = globalSymbolTable.getReturnType(currentMethodName);
								Type t = Type.fromString(declaredReturnType);
								if (!Type.fromString(declaredReturnType).toString().equals(returnType.toString())) {
									throw new CompileException("Return type mismatch in method " + currentMethodName);
								}

								code.append(returnCode);
								code.append("JUMP ").append(currentMethodEndLabel).append("\n");

								guaranteesReturn = true; // return found
								continue;
							}

							// --- Assignment ---
							if (f.test('=')) {
								f.getOp(); // consume '='
								Expr expr = parseExpr(f, symbolTable);
								expr.getType(symbolTable, globalSymbolTable);
								String exprCode = expr.generateCode(symbolTable, globalSymbolTable);
								if (!f.check(';')) {
									throw new CompileException("Extra parenthesis");
								}

								String[] varAttr = symbolTable.lookup(word);
								if (varAttr == null) {
									throw new CompileException("Unknown variable: " + word);
								}

								code.append(exprCode);
								code.append("STOREOFF ").append(varAttr[1]).append("\n"); // varAttr[1] is offset
								continue;
							 }
							throw new CompileException("Unrecognized statement starting with word: " + word);
						}

						case OPERATOR: {
							if (f.check(';')) {
								// empty statement
								continue;
							} else if (f.test('}')) {
								// end of block
								return new StatementResult(code.toString(), guaranteesReturn);
							}
							throw new CompileException("Unexpected operator in statement.");
						}
						default:
							
							throw new CompileException("Unrecognized start of statement: " + f.peekAtKind());
					}
				} catch(CompileException e) {
					throw e;
				}
			}
			
		
	}

	static List<Expr> parseActuals(SamTokenizer f, SymbolTable symbols) throws CompileException {
		List<Expr> actuals = new ArrayList<>();
		actuals.add(parseExpr(f, symbols));
		while (f.check(',')) {
			actuals.add(parseExpr(f, symbols));
		}
		return actuals;
	}

	static String newLabel(String base) {
		return labelGen.newLabel(base);
	}

    static String reverseString() {
        String str_rev_loop = "str_rev_loop";
        String str_rev_middle = "str_rev_middle";
        String str_rev_ending = "str_rev_ending";
        String str_rev_loop2 = "str_rev_loop2";

		// rv = -2, 
		// (in func) 0 = (in stack) -1, 1 = 2, 2 = 3
		// in stack: 0 (link), 1 (fbr)
		// rv = -2, 
        return
			"reverseString:\n"+
			"PUSHOFF "+ (-1) +"\nPUSHIMM 0\nSWAP\n"+
			str_rev_loop + ":\n"+
				"DUP\nPUSHIND\nISNIL\n"+
				"JUMPC " + str_rev_middle + "\n"+
				"SWAP\nPUSHIMM 1\nADD\nSWAP\nPUSHIMM 1\nADD\n"+        
				"JUMP " + str_rev_loop + "\n"+
			str_rev_middle + ":\n"+
				"PUSHOFF "+ (3) +"\n"+
				"PUSHIMM -1\nADD\nSTOREOFF "+ (3) +"\n"+
				"PUSHOFF "+ (2) +"\n"+
				"PUSHIMM 1\nADD\nMALLOC\n"+
			str_rev_loop2 + ":\n"+
				"DUP\nPUSHOFF "+ (3) +"\n"+
				"PUSHIND\nSTOREIND\nPUSHIMM 1\nADD\n"+
				"PUSHOFF "+ (3) +"\n"+
				"PUSHIMM -1\nADD\n"+
				"STOREOFF "+ (3) +"\n"+
				"PUSHOFF "+ (3) +"\n"+
				"PUSHOFF "+ (-1) +"\n"+
				"LESS\n"+
				"JUMPC " + str_rev_ending + "\n"+
				"JUMP " + str_rev_loop2 + "\n"+
			str_rev_ending + ":\n"+
				"PUSHOFF "+ (2) +"\n"+
				"PUSHIMM -1\nTIMES\nADD\n"+
				"STOREOFF "+ (-2) +"\n"+
				"ADDSP -2\n"+
				"JUMPIND\n";
    }

	static String concatString() {
        String str_concat_beginning = "str_concat_beginning";
        String str_concat_loop1 = "str_concat_loop1";
        String str_concat_middle = "str_concat_middle";
        String str_concat_loop2 = "str_concat_loop2";
        String str_concat_concatPrep = "str_concat_concatPrep";
        String str_concat_concat1 = "str_concat_concat1";
        String str_concat_concat2Prep = "str_concat_concat2Prep";
        String str_concat_concat2 = "str_concat_concat2";
        String str_concat_ending = "str_concat_ending";
		// rv = -3, 
		// (in func) 0 = (in stack) -2, 1 = -1, 2 = 2 = rv, 3 = 3, 4 = 4
		// in stack: 0 (link), 1 (fbr)
        return
            "concatString:\n"+
            //"ADDSP 2\n"+ // local var
            str_concat_beginning + ":\n"+
                "PUSHOFF "+ (-2) +"\nPUSHIMM 0\nSWAP\n"+
            str_concat_loop1 + ":\n"+
                lenString(str_concat_middle, str_concat_loop1)+
            str_concat_middle + ":\n"+ 
                "PUSHOFF "+ (-1) +"\nPUSHIMM 0\nSWAP\n"+
                "JUMP " + str_concat_loop2 + "\n"+
            str_concat_loop2 + ":\n"+
                lenString(str_concat_concatPrep, str_concat_loop2)+
            str_concat_concatPrep + ":\n"+
                "SWAP\nDUP\nPUSHOFF 2\n"+
                "ADD\nPUSHIMM 1\nADD\n"+
                "MALLOC\nPUSHOFF "+ (-2) +"\n"+
            str_concat_concat1 + ":\n"+
                "DUP\nPUSHIND\nISNIL\n"+
                "JUMPC " + str_concat_concat2Prep + "\n"+
                "DUP\nPUSHIND\nPUSHOFF 6\nSWAP\n"+
                "STOREIND\nPUSHIMM 1\nADD\nPUSHOFF 6\n"+
                "PUSHIMM 1\nADD\nSTOREOFF 6\n"+
                "JUMP " + str_concat_concat1 + "\n"+
            str_concat_concat2Prep + ":\n"+
                "PUSHOFF "+ (-1) +"\n"+
            str_concat_concat2 + ":\n"+
                "DUP\nPUSHIND\nISNIL\n"+
                "JUMPC " + str_concat_ending + "\n"+
                "DUP\nPUSHIND\nPUSHOFF 6\n"+
                "SWAP\nSTOREIND\nPUSHIMM 1\nADD\n"+
                "PUSHOFF 6\nPUSHIMM 1\nADD\nSTOREOFF 6\n"+
                "JUMP " + str_concat_concat2 + "\n"+
            str_concat_ending + ":\n"+
                "PUSHOFF 6\nPUSHOFF 2\nPUSHOFF 5\n"+
                "ADD\nSUB\nSTOREOFF "+ (-3) +"\nADDSP -7\n"+
                "JUMPIND\n";
            //"POPFBR\n";//PUSHIMM 0\n
    }

	static String repeatString() {
        String str_repeat_beginning = "str_repeat_beginning";
        String str_repeat_prepare = "str_repeat_prepare";
        String str_repeat_allocate = "str_repeat_allocate";
        String str_repeat_pos_prologue = "str_repeat_pos_prologue";
        String str_repeat_neg_prologue = "str_repeat_neg_prologue";
        String str_repeat_ending = "str_repeat_ending";
        String str_repeat_setUpForCopy = "str_repeat_setUpForCopy";
        String str_repeat_copy = "str_repeat_copy";

		// rv = -3, 
		// (in func) 0 = (in stack) -2, 1 = -1, 2 = 2 = rv, 3 = 3, 4 = 4
		// in stack: 0 (link), 1 (fbr)
		// int rvIndex = 2; 
		// int offset = 3; // offset of first local var after rv
		
        return
		"repeatString:\n"+
            "PUSHIMM 0\nPUSHOFF "+(-2)+"\n"+
        str_repeat_beginning + ":\n"+ 
            lenString(str_repeat_prepare, str_repeat_beginning)+
        str_repeat_prepare + ":\n"+
            "PUSHOFF "+ (-1)+ "\nPUSHOFF "+ (2)+ "\nTIMES\nPUSHIMM 1\n"+
            "ADD\nDUP\nDUP\nISPOS\n"+
            "JUMPC " + str_repeat_allocate + "\n"+
            "ADDSP -1\nPUSHIMM 1\n"+
        str_repeat_allocate + ":\n"+
            "MALLOC\nPUSHIMM 1\nPUSHOFF "+ (4) + "\n"+
            "CMP\nPUSHIMM 1\nLESS\n"+
            "JUMPC " + str_repeat_neg_prologue + "\n"+
            "DUP\nPUSHOFF "+ (-2) + "\n"+
        str_repeat_copy + ":\n"+
            "DUP\nPUSHIND\nISNIL\n"+
            "JUMPC " + str_repeat_setUpForCopy + "\n"+
            "DUP\nPUSHIND\nPUSHOFF "+ (5) + "\nSWAP\n"+
            "STOREIND\nPUSHOFF "+ (5) + "\nPUSHIMM 1\n"+
            "ADD\nSTOREOFF "+ (5) + "\nPUSHIMM 1\nADD\n"+
            "JUMP " + str_repeat_copy + "\n"+
        str_repeat_setUpForCopy + ":\n"+
            "STOREOFF "+ (3) + "\nPUSHOFF "+ (6) + "\nPUSHOFF "+ (4) + "\nPUSHIMM -1\n"+
            "ADD\nADD\nPUSHOFF "+ (5) + "\nEQUAL\n"+
            "JUMPC " + str_repeat_pos_prologue + "\n"+
            "PUSHOFF "+ (-2) + "\n"+
            "JUMP " + str_repeat_copy + "\n"+
		str_repeat_neg_prologue + ":\n"+
            "STOREOFF "+ (-3) + "\nADDSP -3\n"+
			"JUMPIND\n"+
        str_repeat_pos_prologue + ":\n"+
            "STOREOFF "+ (-3) + "\nADDSP -4\n"+
			"JUMPIND\n";
    }

    static String compareString() {
        String str_cmp_loop = LabelGenerator.newLabel("str_cmp_loop");
        String str_cmp_secIsBigger = LabelGenerator.newLabel("str_cmp_secIsBigger");
        String str_cmp_firstIsBigger = LabelGenerator.newLabel("str_cmp_firstIsBigger");
        String str_cmp_prologue = LabelGenerator.newLabel("str_cmp_prologue");
        String str_cmp_test = LabelGenerator.newLabel("str_cmp_test");
        String str_cmp_equal = LabelGenerator.newLabel("str_cmp_equal");
        String str_cmp_end = LabelGenerator.newLabel("str_cmp_end");

		// rv = -3, 
		// (in func) 0 = (in stack) -2, 1 = -1, 2 = 2 = rv, 3 = 3, 4 = 4
		// in stack: 0 (link), 1 (fbr)
        return
			"PUSHIMM 0\nPUSHIMM 0\nPUSHIMM 0\n"+
			str_cmp_loop + ":\n"+
				"PUSHOFF "+ (-2) +"\nPUSHIND\nDUP\nISNIL\n"+
				"JUMPC " + str_cmp_secIsBigger + "\n"+
				"PUSHOFF "+ (-1) +"\nPUSHIND\nDUP\nISNIL\n"+
				"JUMPC " + str_cmp_firstIsBigger + "\n"+
				"CMP\nDUP\nISNEG\n"+
				"JUMPC " + str_cmp_test + "\n"+
				"DUP\nISPOS\n"+
				"JUMPC " + str_cmp_test + "\n"+
				"ADDSP -1\n"+
				"PUSHOFF "+ (-2) +"\nPUSHIMM 1\nADD\nSTOREOFF "+ (-2) +"\n"+
				"PUSHOFF "+ (-1) +"\nPUSHIMM 1\nADD\nSTOREOFF "+ (-1) +"\n"+
				"PUSHOFF 3\nPUSHIMM 1\nADD\nSTOREOFF 3\n"+
				"PUSHOFF 4\nPUSHIMM 1\nADD\nSTOREOFF 4\n"+
				"JUMP " + str_cmp_loop + "\n"+
			str_cmp_secIsBigger + ":\n"+
				"ADDSP -1\nPUSHOFF "+ (-1) +"\nPUSHIND\nISNIL\n"+
				"JUMPC " + str_cmp_equal + "\n"+
				"PUSHIMM 1\nSTOREOFF 2\n"+
				"JUMP " + str_cmp_prologue + "\n"+
			str_cmp_firstIsBigger + ":\n"+
				"ADDSP -1\nPUSHIMM -1\nSTOREOFF 2\n"+
				"JUMPC " + str_cmp_prologue + "\n"+
			str_cmp_prologue + ":\n"+
				"PUSHOFF "+ (-2) +"\nPUSHOFF 3\nSUB\nADDSP -1\n"+
				"PUSHOFF "+ (-1) +"\nPUSHOFF 4\nSUB\nADDSP -1\n"+
				"ADDSP -2\n"+
				"JUMP " + str_cmp_end + "\n"+
			str_cmp_test + ":\n"+
				"STOREOFF 2\n"+
				"JUMP " + str_cmp_prologue + "\n"+
			str_cmp_equal + ":\n"+
				"PUSHIMM 0\n"+
				"JUMP " + str_cmp_test + "\n"+
			str_cmp_end + ":\n";
				//"STOREOFF "+ (-3) +"\nADDSP -1\n";
    }

	static String compareStringOp(int expected) {
        return compareString() + 
           "PUSHIMM " + expected + "\n" +
           "EQUAL\nSTOREOFF" +(-3)+ "\nJUMPIND\n";
    }
	
	static String equalString() {
        return "equalString:\n" + compareStringOp(0);
    }
	
	static String lessString() {
        return "lessString:\n" + compareStringOp(1);
    }

    static String greaterString() {
        return "greaterString:\n" + compareStringOp(-1);
    }

	static String lenString(String conditionLabel, String jumpLabel) {
		return 
		"DUP\nPUSHIND\nISNIL\n"+
		"JUMPC "+conditionLabel+"\n"+
		"SWAP\nPUSHIMM 1\nADD\nSWAP\nPUSHIMM 1\nADD\n"+
		"JUMP "+jumpLabel+"\n";
    }

}


