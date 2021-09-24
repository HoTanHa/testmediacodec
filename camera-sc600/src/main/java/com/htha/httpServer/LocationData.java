package com.htha.httpServer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Date;

public final class LocationData {
    private static final boolean TEST = true;

    public static String getThongTinThietBi() {
        if (TEST) {
            String data = "{\"TenCongTy\":\"ADSUN JSC\","
                    + "\"TenSanPham\":\"TMS-CAM-ND10\"," + "\"Serial\":638228604,"
                    + "\"BienSo\":\"N/A\"," + "\"ppDoTocDo\":1,"
                    + "\"ConfigXungKm\":8000,\"VanTocMax\":90,"
                    + "\"NgayLapDat\":\"2021/07/01\",\"NgayCapNhat\":\"2021/07/01\","
                    + "\"GsmStatus\":2,\"GpsStatus\":0,\"SdcardStatus\":1," + "\"DungLuong\":134217728,"
                    + "\"ThongTinTaiXe\":\"HO_VA_TEN_LAI_XE_1,GIAY_PHEP_LAI_XE_1\","
                    + "\"ThoiGianLienTuc\":0,\"GpsInfo\":\".0,.0\","
                    + "\"VanToc\":0,\"ThoiGianTb\":\"2021/08/23 04:03:57 PM\"}";
            return data;
        }
        JSONObject jsInfo = new JSONObject();
        return jsInfo.toString();
    }

    public static String getThoiGianLamViec(Date date) {
        if (TEST) {
            String data = "[{\"TenTaiXe\":\"Tran Trung Thao\",\"BangLai\":\"25647892145\",\"TimeBatDau\":\"16:20:36\","
                    + "\"KinhDoBatDau\":\"0\",\"ViDoBatDau\":\"0\",\"TimeKetThuc\":\"16:22:53\",\"KinhDoKetThuc\":\"0\",\"ViDoKetThuc\":\"0\"},"
                    + "{\"TenTaiXe\":\"NGUYEN VAN AB\",\"BangLai\":\"AD1234567890\",\"TimeBatDau\":\"16:24:38\","
                    + "\"KinhDoBatDau\":\"0\",\"ViDoBatDau\":\"0\",\"TimeKetThuc\":\"16:27:09\",\"KinhDoKetThuc\":\"106.605642\",\"ViDoKetThuc\":\"10.777903\"}]";
            return data;
        }
        JSONArray jsTgLamViec = new JSONArray();
        return jsTgLamViec.toString();
    }

    public static String getDuLieuDungDo(Date date) {
        if (TEST) {
            String data = "[{\"ThoiGian\":\"16:03:43\",\"KinhDo\":\".0\",\"ViDo\":\".0\",\"SoPhut\":17}, "
                    + "{\"ThoiGian\":\"16:20:50\",\"KinhDo\":\".0\",\"ViDo\":\".0\",\"SoPhut\":0},"
                    + "{\"ThoiGian\":\"16:22:53\",\"KinhDo\":\".0\",\"ViDo\":\".0\",\"SoPhut\":1},"
                    + "{\"ThoiGian\":\"16:27:09\",\"KinhDo\":\"106.605642\",\"ViDo\":\"10.777903\",\"SoPhut\":1}]";
            return data;
        }
        JSONArray jsDungDo = new JSONArray();
        return jsDungDo.toString();
    }

    public static String getDuLieuHanhTrinh(Date date) {
        if (TEST) {
            String data = "[{\"ThoiGian\":\"16:04:02\",\"KinhDo\":\".0\",\"ViDo\":\".0\",\"VanTocGps\":0,\"VanTocXung\":0},"
                    + "{\"ThoiGian\":\"16:14:02\",\"KinhDo\":\".0\",\"ViDo\":\".0\",\"VanTocGps\":0,\"VanTocXung\":0},"
                    + "{\"ThoiGian\":\"16:20:48\",\"KinhDo\":\".0\",\"ViDo\":\".0\",\"VanTocGps\":3,\"VanTocXung\":0},"
                    + "{\"ThoiGian\":\"16:21:08\",\"KinhDo\":\".0\",\"ViDo\":\".0\",\"VanTocGps\":0,\"VanTocXung\":0},"
                    + "{\"ThoiGian\":\"16:21:28\",\"KinhDo\":\".0\",\"ViDo\":\".0\",\"VanTocGps\":20,\"VanTocXung\":0},"
                    + "{\"ThoiGian\":\"16:21:48\",\"KinhDo\":\".0\",\"ViDo\":\".0\",\"VanTocGps\":16,\"VanTocXung\":0},"
                    + "{\"ThoiGian\":\"16:22:08\",\"KinhDo\":\".0\",\"ViDo\":\".0\",\"VanTocGps\":9,\"VanTocXung\":0},"
                    + "{\"ThoiGian\":\"16:22:28\",\"KinhDo\":\".0\",\"ViDo\":\".0\",\"VanTocGps\":10,\"VanTocXung\":0},"
                    + "{\"ThoiGian\":\"16:22:48\",\"KinhDo\":\".0\",\"ViDo\":\".0\",\"VanTocGps\":5,\"VanTocXung\":0},"
                    + "{\"ThoiGian\":\"16:23:08\",\"KinhDo\":\".0\",\"ViDo\":\".0\",\"VanTocGps\":0,\"VanTocXung\":0},"
                    + "{\"ThoiGian\":\"16:24:49\",\"KinhDo\":\".0\",\"ViDo\":\".0\",\"VanTocGps\":25,\"VanTocXung\":0},"
                    + "{\"ThoiGian\":\"16:25:09\",\"KinhDo\":\".0\",\"ViDo\":\".0\",\"VanTocGps\":32,\"VanTocXung\":0},"
                    + "{\"ThoiGian\":\"16:25:29\",\"KinhDo\":\".0\",\"ViDo\":\".0\",\"VanTocGps\":39,\"VanTocXung\":0},"
                    + "{\"ThoiGian\":\"16:25:49\",\"KinhDo\":\".0\",\"ViDo\":\".0\",\"VanTocGps\":35,\"VanTocXung\":0},"
                    + "{\"ThoiGian\":\"16:26:09\",\"KinhDo\":\".0\",\"ViDo\":\".0\",\"VanTocGps\":36,\"VanTocXung\":0},"
                    + "{\"ThoiGian\":\"16:26:29\",\"KinhDo\":\"106.605656\",\"ViDo\":\"10.777911\",\"VanTocGps\":39,\"VanTocXung\":0},"
                    + "{\"ThoiGian\":\"16:26:49\",\"KinhDo\":\".0\",\"ViDo\":\".0\",\"VanTocGps\":40,\"VanTocXung\":0},"
                    + "{\"ThoiGian\":\"16:27:09\",\"KinhDo\":\"106.605642\",\"ViDo\":\"10.777903\",\"VanTocGps\":0,\"VanTocXung\":0},"
                    + "{\"ThoiGian\":\"16:27:29\",\"KinhDo\":\"106.605625\",\"ViDo\":\"10.777917\",\"VanTocGps\":0,\"VanTocXung\":0},"
                    + "{\"ThoiGian\":\"16:28:10\",\"KinhDo\":\"106.605626\",\"ViDo\":\"10.777916\",\"VanTocGps\":19,\"VanTocXung\":0},"
                    + "{\"ThoiGian\":\"16:28:30\",\"KinhDo\":\"106.605626\",\"ViDo\":\"10.777916\",\"VanTocGps\":18,\"VanTocXung\":0},"
                    + "{\"ThoiGian\":\"16:28:50\",\"KinhDo\":\"106.605626\",\"ViDo\":\"10.777916\",\"VanTocGps\":17,\"VanTocXung\":0},"
                    + "{\"ThoiGian\":\"16:29:10\",\"KinhDo\":\"106.605625\",\"ViDo\":\"10.777916\",\"VanTocGps\":20,\"VanTocXung\":0},"
                    + "{\"ThoiGian\":\"16:29:30\",\"KinhDo\":\"106.605626\",\"ViDo\":\"10.777916\",\"VanTocGps\":23,\"VanTocXung\":0},"
                    + "{\"ThoiGian\":\"16:29:50\",\"KinhDo\":\"106.605625\",\"ViDo\":\"10.777916\",\"VanTocGps\":0,\"VanTocXung\":0},"
                    + "{\"ThoiGian\":\"16:32:20\",\"KinhDo\":\".0\",\"ViDo\":\".0\",\"VanTocGps\":0,\"VanTocXung\":0},"
                    + "{\"ThoiGian\":\"16:33:03\",\"KinhDo\":\".0\",\"ViDo\":\".0\",\"VanTocGps\":0,\"VanTocXung\":0},"
                    + "{\"ThoiGian\":\"16:42:36\",\"KinhDo\":\".0\",\"ViDo\":\".0\",\"VanTocGps\":0,\"VanTocXung\":0},"
                    + "{\"ThoiGian\":\"16:44:43\",\"KinhDo\":\".0\",\"ViDo\":\".0\",\"VanTocGps\":0,\"VanTocXung\":0},"
                    + "{\"ThoiGian\":\"16:54:43\",\"KinhDo\":\".0\",\"ViDo\":\".0\",\"VanTocGps\":0,\"VanTocXung\":0}]";
            return data;
        }
        JSONArray jsHanhTrinh = new JSONArray();
        return jsHanhTrinh.toString();
    }

    public static String getDuLieuTocDoTungGiay(Date date) {
        if (TEST) {
            String data = "[{\"ThoiGian\":\"16:03:42\",\"TocDoTungGiay\":\",0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0\"},"
                    + "{\"ThoiGian\":\"16:13:42\",\"TocDoTungGiay\":\",0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0\"},"
                    + "{\"ThoiGian\":\"16:20:28\",\"TocDoTungGiay\":\",5,4,4,4,3,3,0,0,3,4,3,5,4,3,4,4,4,3,3,4,3,3,0,0,0,0,0,0,0,0\"},"
                    + "{\"ThoiGian\":\"16:20:58\",\"TocDoTungGiay\":\",0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,19,20,20,21,21,21,21,23,22,23,22,21,21,20\"},"
                    + "{\"ThoiGian\":\"16:21:28\",\"TocDoTungGiay\":\",20,19,19,20,20,19,19,19,19,19,19,19,18,17,17,18,18,19,18,16,16,14,13,14,14,13,11,11,10,10\"},"
                    + "{\"ThoiGian\":\"16:21:58\",\"TocDoTungGiay\":\",11,12,11,11,11,11,10,10,10,10,9,9,7,8,7,8,7,8,10,10,10,11,11,10,10,11,10,9,10,10\"},"
                    + "{\"ThoiGian\":\"16:22:28\",\"TocDoTungGiay\":\",10,10,10,11,11,10,9,8,8,8,9,9,8,8,8,8,7,6,7,6,5,4,4,5,5,0,0,0,0,0\"},"
                    + "{\"ThoiGian\":\"16:22:58\",\"TocDoTungGiay\":\",0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0\"},"
                    + "{\"ThoiGian\":\"16:24:29\",\"TocDoTungGiay\":\",0,0,0,0,0,0,0,0,0,20,21,21,21,23,23,23,25,25,24,23,25,26,25,25,26,28,27,27,25,26\"},"
                    + "{\"ThoiGian\":\"16:24:59\",\"TocDoTungGiay\":\",26,25,26,27,28,29,31,29,30,31,32,33,33,34,35,33,34,34,35,35,35,36,36,36,35,36,36,38,38,38\"},"
                    + "{\"ThoiGian\":\"16:25:29\",\"TocDoTungGiay\":\",39,39,39,39,38,37,38,36,35,36,36,35,36,35,35,36,35,35,33,34,35,35,35,36,36,35,37,37,36,34\"},"
                    + "{\"ThoiGian\":\"16:25:59\",\"TocDoTungGiay\":\",33,33,34,34,33,33,34,34,35,36,36,36,36,36,37,35,37,36,37,38,38,38,37,36,36,36,37,38,39,38\"},"
                    + "{\"ThoiGian\":\"16:26:29\",\"TocDoTungGiay\":\",39,38,38,38,39,39,38,40,39,40,41,40,39,39,38,39,38,39,38,39,40,40,38,40,39,39,39,40,41,40\"},"
                    + "{\"ThoiGian\":\"16:26:59\",\"TocDoTungGiay\":\",40,39,39,38,38,37,37,37,36,37,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0\"},"
                    + "{\"ThoiGian\":\"16:27:50\",\"TocDoTungGiay\":\",0,0,0,0,0,0,0,0,0,19,18,17,18,20,19,19,19,18,18,20,19,18,20,18,17,17,18,17,19,18\"},"
                    + "{\"ThoiGian\":\"16:28:20\",\"TocDoTungGiay\":\",18,19,19,18,18,17,18,18,18,17,18,18,18,18,18,18,17,18,19,20,19,20,20,20,19,20,20,18,17,18\"},"
                    + "{\"ThoiGian\":\"16:28:50\",\"TocDoTungGiay\":\",17,17,18,18,18,18,18,19,18,20,20,19,21,20,18,19,18,18,18,19,20,19,19,18,19,20,20,19,20,19\"},"
                    + "{\"ThoiGian\":\"16:29:20\",\"TocDoTungGiay\":\",19,19,18,19,20,18,20,20,22,23,23,22,22,22,22,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0\"},"
                    + "{\"ThoiGian\":\"16:32:00\",\"TocDoTungGiay\":\",0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0\"},"
                    + "{\"ThoiGian\":\"16:32:43\",\"TocDoTungGiay\":\",0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0\"},"
                    + "{\"ThoiGian\":\"16:42:16\",\"TocDoTungGiay\":\",0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0\"},"
                    + "{\"ThoiGian\":\"16:44:23\",\"TocDoTungGiay\":\",0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0\"},"
                    + "{\"ThoiGian\":\"16:54:23\",\"TocDoTungGiay\":\",0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0\"},"
                    + "{\"ThoiGian\":\"16:55:24\",\"TocDoTungGiay\":\",0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0\"},"
                    + "{\"ThoiGian\":\"17:05:24\",\"TocDoTungGiay\":\",0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0\"}]";
            return data;
        }
        JSONArray jsSpeed = new JSONArray();
        return jsSpeed.toString();
    }

    public static String getQuaTocDo(Date date, int speed) {
        if (TEST) {
            String data = "[{\"TimeStart\":\"15:06:39\",\"TimeStop\":\"15:08:30\",\"VtMax\":94},"
                    + "{\"TimeStart\":\"15:08:37\",\"TimeStop\":\"15:08:38\",\"VtMax\":41},"
                    + "{\"TimeStart\":\"15:13:13\",\"TimeStop\":\"15:14:33\",\"VtMax\":70}]";
            return data;
        }
        JSONArray jsOverSpeed = new JSONArray();
        return jsOverSpeed.toString();
    }
}
