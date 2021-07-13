package com.example.mylibcommon;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class CommonFunction {
    public static String readFile(String path) {
        String a = "";
        try {
            FileReader fileProductReader = new FileReader(path);
            StringBuilder result = new StringBuilder();
            int i;
            while ((i = fileProductReader.read()) != -1)
                result.append((char) i);
            fileProductReader.close();
            return result.toString();
        }
        catch (IOException ignored) {
        }
        return a;
    }

    public static String readFirstLineInFile(String path) {
        String line = null;
        try {
            BufferedReader br = new BufferedReader(new FileReader(path));
            line = br.readLine();
            br.close();
        }
        catch (IOException ignored) {
        }
        if (line != null) {
            return line;
        }
        return "";
    }
}
