package assignment2;

public class LabelGenerator {
    private static int labelCounter = 0;

    public static String newLabel(String base) {
        return base + "_" + (labelCounter++);
    }
}