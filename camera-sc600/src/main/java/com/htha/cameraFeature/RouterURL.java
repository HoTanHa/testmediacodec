package com.htha.cameraFeature;

public class RouterURL {
    private static final boolean USE_ROUTER = false;
    private static final String URL_ROUTER_1 = "http://route1.adsun.vn/DeviceHttp/Camera?Serial=";
    private static final String URL_ROUTER_2 = "http://route2.adsun.pro.vn/DeviceHttp/Camera?Serial=";
    private static final String URL_ROUTER_3 = "http://route3.adsun.net.vn/DeviceHttp/Camera?Serial=";

    private static final boolean DEV_TEST = true;
    private static final String URL_DOMAIN_1 = "http://camera1.adsun.vn/DeviceHttp/Camera?Serial=";
    private static final String URL_DOMAIN_2 = "http://camera2.adsun.net.vn/DeviceHttp/Camera?Serial=";
    private static final String URL_DOMAIN_3 = "http://camera3.adsun.pro.vn/DeviceHttp/Camera?Serial=";
    private static final String HOST_PROXY = "livedev.adsun.vn";
    private static final int PORT_PROXY = 8090;
    private static final String URL_DOMAIN_1_DEV = "http://namaroute.adsun.vn/DeviceHttp/Camera?Serial=";
    private static final String URL_DOMAIN_2_DEV = "http://namaroute.adsun.vn/DeviceHttp/Camera?Serial=";
    private static final String URL_DOMAIN_3_DEV = "http://namaroute.adsun.vn/DeviceHttp/Camera?Serial=";
    private static final String HOST_PROXY_DEV = "livedev.adsun.vn";
    private static final int PORT_PROXY_DEV = 8091;

    private static final boolean USE_BINHMINH = false;
    private static final String URL_BINHMINH = "http://routecamera.gpsbinhminh.vn/DeviceHttp/Camera?Serial=";


    private static final String URL_DEVICE_AD = "http://live1.adsun.vn:8100/api/manage/deviceInfo";
    //    "http://125.212.211.209:8100/api/manage/deviceInfo"
    private static final String URL_DEVICE_TEST = "http://live1.adsun.vn:8200/api/manage/deviceInfo";
    //    "http://125.212.211.209:8200/api/manage/deviceInfo";
    private static final String URL_DEVICE_BM = "http://live1.gpsbinhminh.vn:8102/api/manage/deviceInfo";
    //    "http://125.212.211.209:8102/api/manage/deviceInfo"

    public static boolean isSendServerTest() {
        return DEV_TEST && (!USE_ROUTER);
    }

    public static boolean isSendBinhMinh() {
        return USE_BINHMINH;
    }

    private static int countFail = 0;

    public static void setCountFail(boolean result) {
        if (result) {
            int tmp = countFail;
            countFail = tmp - (tmp % 10);
        }
        else {
            countFail++;
            if (countFail >= 30) {
                countFail = 0;
            }
        }
    }

    public static String getUrlDomainToSendImage() {
        if (USE_BINHMINH) {
            return URL_BINHMINH;
        }

        if (countFail < 10) {
            return USE_ROUTER ? URL_ROUTER_1 : (DEV_TEST ? URL_DOMAIN_1_DEV : URL_DOMAIN_1);
        }

        if (countFail < 20) {
            return USE_ROUTER ? URL_ROUTER_2 : (DEV_TEST ? URL_DOMAIN_2_DEV : URL_DOMAIN_2);
        }

        return USE_ROUTER ? URL_ROUTER_3 : (DEV_TEST ? URL_DOMAIN_3_DEV : URL_DOMAIN_3);
    }

    public static String getUrlSendInfoDevice() {
        if (isSendBinhMinh()) {
            return URL_DEVICE_BM;
        }
        else if (isSendServerTest()) {
            return URL_DEVICE_TEST;
        }
        else {
            return URL_DEVICE_BM;
        }
    }
}
