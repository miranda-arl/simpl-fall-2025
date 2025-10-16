package assignment2;

public enum Type {
    INT, BOOL, STRING;

    public static Type fromString(String s) {
        switch (s) {
            case "int": return INT;
            case "bool": return BOOL;
            case "String": return STRING;
            default: throw new RuntimeException("Unknown type: " + s);
        }
    }
}