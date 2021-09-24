package com.htha.mylibcommon;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

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

    public static String getDateTime64(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);

        char[] s_date = new char[10];
        s_date[0] = (char) (calendar.get(Calendar.SECOND) + 64);//    (date.getSeconds() + 64);
        s_date[1] = (char) (calendar.get(Calendar.MINUTE) + 64);//    (date.getMinutes() + 64);
        s_date[2] = (char) (calendar.get(Calendar.HOUR_OF_DAY) + 64);//  (date.getHours() + 64);
        s_date[3] = (char) (calendar.get(Calendar.DATE) + 64);//  (date.getDate() + 64);
        s_date[4] = (char) (calendar.get(Calendar.MONTH) + 1 + 64);// (date.getMonth() + 64 + 1);
        s_date[5] = (char) (calendar.get(Calendar.YEAR) - 2000 + 64);//(date.getYear() - 36); //(+1900-2000+64)
        s_date[6] = 0;                 //null
        String sDate = new String(s_date, 0, 6);
        return sDate;
    }
}
