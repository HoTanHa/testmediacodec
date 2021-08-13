package com.example.testcameramediacodec.tcp;

/**
 * Calculation class
 *
 * @author kokJuis
 */
public class CalculateUtil {

    /**
     * Note: INT to the conversion of the byte array!
     *
     * @param number
     * @return
     */
    public static byte[] intToByte(int number) {
        int temp = number;
        byte[] b = new byte[4];
        for (int i = 0; i < b.length; i++) {
            b [i] = new Integer (temp & 0xFF).byteValue(); // Save the lowest position at the lowest position
            temp = temp >> 8; // 8 digits to right
        }
        return b;
    }


    public static int byteToInt(byte b) {
        // Java always puts Byte as a manifest; we can use it and 0xFF to get its uncontrolled value
        return b & 0xFF;
    }


    // BYTE array with int
    public static int byteArrayToInt(byte[] b) {
        return b[3] & 0xFF |
                (b[2] & 0xFF) << 8 |
                (b[1] & 0xFF) << 16 |
                (b[0] & 0xFF) << 24;
    }

    public static byte[] intToByteArray(int a) {
        return new byte[] {
                (byte) ((a >> 24) & 0xFF),
                (byte) ((a >> 16) & 0xFF),
                (byte) ((a >> 8) & 0xFF),
                (byte) (a & 0xFF)
        };
    }


         // Empty the value of BUF
    public static void memset(byte[] buf, int value, int size) {
        for (int i = 0; i < size; i++) {
            buf[i] = (byte) value;
        }
    }

//    public static void dump(NALU_t n) {
//        System.out.println("len: " + n.len + " nal_unit_type:" + n.nal_unit_type);
//    }

         // Judgment is 0x000001, if it is returned 1
    public static int FindStartCode2(byte[] Buf, int off) {
        if (Buf[0 + off] != 0 || Buf[1 + off] != 0 || Buf[2 + off] != 1)
            return 0;
        else
            return 1;
    }

    // Judgment is 0x00000001, if it is returned 1
    public static int FindStartCode3(byte[] Buf, int off) {
        if (Buf[0 + off] != 0 || Buf[1 + off] != 0 || Buf[2 + off] != 0 || Buf[3 + off] != 1)
            return 0;
        else
            return 1;
    }

}