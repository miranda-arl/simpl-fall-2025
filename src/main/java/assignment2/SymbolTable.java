package assignment2;

import java.util.HashMap;
import java.util.Map;

public class SymbolTable {
	public Map<String, String[]> table;

	public String returnType;
	public String location; 
	public String value; 

	public SymbolTable() {
		table = new HashMap<>();
	}

	public void enter(String name, String[] attr) {
		table.put(name, attr);
	}

	public String[] lookup(String name) {
		return table.get(name);
	}

	public void exit(String name) {
		table.remove(name);
	}

	public String getReturnType(String name) {
		return table.get(name)[0];
	}

	public String getLocation(String name) {
		return table.get(name)[1];
	}

	public String getValue(String name) {
		return table.get(name)[2];
	}

	public boolean containsKey(String name) {
		return table.containsKey(name);
	}
}
