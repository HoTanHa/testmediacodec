package com.example.testcameramediacodec.tcp;

public class RtspPacketEncode {
    private static final String TAG = "RtspPacketEncode";


    // ------------ Video conversion data listening -----------
    public interface H264ToRtpLinsener {
        void h264ToRtpResponse(byte[] out, int len);
    }

    private H264ToRtpLinsener h264ToRtpLinsener;

    // Perform a callback
    private void exceuteH264ToRtpLinsener(byte[] out, int len) {
        if (this.h264ToRtpLinsener != null) {
            h264ToRtpLinsener.h264ToRtpResponse(out, len);
        }
    }


    // ------- Video --------
    private int framerate = 10;
    private byte[] sendbuf = new byte[1500];
    private int packageSize = 1400;
    private int seq_num = 0;
    private int timestamp_increse = (int) (90000.0 / framerate); // Framerate is a frame rate
    private int ts_current = 0;
    private int bytes = 0;

    // ------- Video End --------

    public RtspPacketEncode(H264ToRtpLinsener h264ToRtpLinsener) {
        this.h264ToRtpLinsener = h264ToRtpLinsener;
    }


    /**
     * One frame one frame RTP package
     *
     * @param r
     * @return
     */
    public void h264ToRtp(byte[] r, int h264len) throws Exception {

        CalculateUtil.memset(sendbuf, 0, 1500);
        sendbuf[1] = (byte) (sendbuf[1] | 96); // load type 96, its value is: 01100000
        sendbuf[0] = (byte) (sendbuf[0] | 0x80); // version number, this version is fixed to 2
        sendbuf[1] = (byte) (sendbuf[1] & 254); // flag, specified by the specific protocol, its value is: 01100000
        sendbuf[11] = 10; // Specify 10, and in this RTP reply, the Java default uses network byte serial number without conversion (the last byte of the same identifier)
        if (h264len <= packageSize) {
            sendbuf[1] = (byte) (sendbuf[1] | 0x80); // Setting the RTP m bit is 1, its value is: 11100000, the last piece of the sub-package, the M bit (first) is 0, then 7 Bit is a decimal 96, indicating load type
            sendbuf[3] = (byte) seq_num++;
            System.arraycopy(CalculateUtil.intToByte(seq_num++), 0, sendbuf, 2, 2); // send [2] and Send [3] are serial numbers, two
            {
                byte temp = 0;
                temp = sendbuf[3];
                sendbuf[3] = sendbuf[2];
                sendbuf[2] = temp;
            }
            // fu-a header, and fill this header in Sendbuf [12]
            sendbuf[12] = (byte) (sendbuf[12] | ((byte) (r[0] & 0x80)) << 7);
            sendbuf[12] = (byte) (sendbuf[12] | ((byte) ((r[0] & 0x60) >> 5)) << 5);
            sendbuf[12] = (byte) (sendbuf[12] | ((byte) (r[0] & 0x1f)));
            // Triendally assign SendBuf [13] to NALU_PAYLOAD
            // NALU header is already written in SendBuf [12], and the data after the first byte of NAL is stored. So start copying from the second byte of R
            System.arraycopy(r, 4, sendbuf, 13, h264len - 4);
            ts_current = ts_current + timestamp_increse;
            System.arraycopy(CalculateUtil.intToByte(ts_current), 0, sendbuf, 4, 4); // The serial number is next to the timestamp, 4 bytes, and it needs to be reversed after storage.
            {
                byte temp = 0;
                temp = sendbuf[4];
                sendbuf[4] = sendbuf[7];
                sendbuf[7] = temp;
                temp = sendbuf[5];
                sendbuf[5] = sendbuf[6];
                sendbuf[6] = temp;
            }
            bytes = h264len + 12; // Received the length of SendBuf, the length of NALU (including the NALU head but takes the starting prefix, plus the RTP_HEADER fixed length 12 bytes)
            //client.send(new DatagramPacket(sendbuf, bytes, addr, port/*9200*/));
            //send(sendbuf,bytes);
            exceuteH264ToRtpLinsener(sendbuf, bytes);

        }
        else {
            int k = 0, l = 0;
            k = (h264len-4) / packageSize;
            l = (h264len-4) % packageSize;
            int t = 0;
            ts_current = ts_current + timestamp_increse;
            System.arraycopy(CalculateUtil.intToByte(ts_current), 0, sendbuf, 4, 4); // Timestamp, and reverse
            {
                byte temp = 0;
                temp = sendbuf[4];
                sendbuf[4] = sendbuf[7];
                sendbuf[7] = temp;
                temp = sendbuf[5];
                sendbuf[5] = sendbuf[6];
                sendbuf[6] = temp;
            }
            while (t <= k) {
                System.arraycopy(CalculateUtil.intToByte(seq_num++), 0, sendbuf, 2, 2); // serial number, and reverse
                {
                    byte temp = 0;
                    temp = sendbuf[3];
                    sendbuf[3] = sendbuf[2];
                    sendbuf[2] = temp;
                }
                if (t == 0) {// Sub-packaged first piece
                    sendbuf[1] = (byte) (sendbuf[1] & 0x7f); // The value is: 01100000, not the last piece, M bits (first) set to 0
                    // FU INDICATOR, one byte, after the RTP header, including F, NRI, Header
                    sendbuf[12] = (byte) ((byte) (sendbuf[12] | (byte) (r[0] & 0x80)) << 7); // Forbidden position, 0
                    sendbuf[12] = (byte) ((byte) ((byte) (((r[0] & 0x60) >> 5)) << 5)); // NRI, indicating the importance of the package
                    sendbuf[12] = (byte) (sendbuf[12] | (28)); // Type, indicating why this FU-A package is type, generally 28
                    // fu header, one byte, s, e, r, type
                    sendbuf[13] = (byte) (sendbuf[13] & 0xBF); // E = 0, indicating whether it is the last package, is 1
                    sendbuf[13] = (byte) (sendbuf[13] & 0xDF); // r = 0, reserved bit, must be set to 0
                    sendbuf[13] = (byte) (sendbuf[13] | 0x80); // s = 1, indicating whether it is the first package, is 1
                    sendbuf[13] = (byte) (((byte) (r[0] & 0x1f))); // Type, Type corresponding to NALU head
                    // After the NALU data remains in the NALU header is written to the 14th byte of Sendbuf. The first 14 bytes include: 12-byte RTP header, Fu Indicator, Fu PreferenceActivity.Header
                    System.arraycopy(r, 4, sendbuf, 14, packageSize);
                    //client.send(new DatagramPacket(sendbuf, packageSize + 14, addr, port/*9200*/));
                    exceuteH264ToRtpLinsener(sendbuf, packageSize + 14);
                    t++;
                }
                else if (t == k) {// Splitting last piece
                    sendbuf[1] = (byte) (sendbuf[1] | 0x80);

                    sendbuf[12] = (byte) (sendbuf[12] | ((byte) (r[0] & 0x80)) << 7);
                    sendbuf[12] = (byte) (sendbuf[12] | ((byte) ((r[0] & 0x60) >> 5)) << 5);
                    sendbuf[12] = (byte) (sendbuf[12] | (byte) (28));

                    sendbuf[13] = (byte) (sendbuf[13] & 0xDF); // r = 0, the reserved bit must be set to 0
                    sendbuf[13] = (byte) (sendbuf[13] & 0x7f); // s = 0, not the first package
                    sendbuf[13] = (byte) (sendbuf[13] | 0x40); // E = 1, is the last package
                    sendbuf[13] = (byte) ((byte) (r[0] & 0x1F)); // NALU head corresponding to Type

                    if (0 != l) {// If you can't remove it, there is a left, and this code is executed. If the package is just a multiple of 1400, this code is not executed.
                        System.arraycopy(r, t * packageSize + 4, sendbuf, 14, l - 1); // L-1, does not include NALU headers
                        bytes = l - 1 + 14; //bytes=l-1+14;
                        //client.send(new DatagramPacket(sendbuf, bytes, addr, port/*9200*/));
                        //send(sendbuf,bytes);
                        exceuteH264ToRtpLinsener(sendbuf, bytes);
                    }//pl
                    t++;
                }
                else if (t < k) {// is neither the first piece, not the last package
                    sendbuf[1] = (byte) (sendbuf[1] & 0x7F); // m = 0, its value is: 01100000, not the last piece, the M bit (first) is set to 0.
                    sendbuf[12] = (byte) (sendbuf[12] | ((byte) (r[0] & 0x80)) << 7);
                    sendbuf[12] = (byte) (sendbuf[12] | ((byte) ((r[0] & 0x60) >> 5)) << 5);
                    sendbuf[12] = (byte) (sendbuf[12] | (byte) (28));

                    sendbuf[13] = (byte) (sendbuf[13] & 0xDF); // r = 0, the reserved bit must be set to 0
                    sendbuf[13] = (byte) (sendbuf[13] & 0x7f); // s = 0, not the first package
                    sendbuf[13] = (byte) (sendbuf[13] & 0xBF); // E = 0, not the last package
                    sendbuf[13] = (byte) ((byte) (r[0] & 0x1F)); // NALU head corresponding to Type
                    System.arraycopy(r, t * packageSize + 4, sendbuf, 14, packageSize); // does not include NALU header
                    //client.send(new DatagramPacket(sendbuf, packageSize + 14, addr, port/*9200*/));
                    //send(sendbuf,1414);
                    exceuteH264ToRtpLinsener(sendbuf, packageSize + 14);

                    t++;
                }
            }
        }
    }

}
