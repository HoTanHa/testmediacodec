package com.example.httpServer;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

public class ImageData {
    private long timeMs;
    private int idIndex;
    private int camChn;// 1, 2, 3,...
    private final String path;
    private String sTime;
    private String sLocation;

    public ImageData(String pathImage){
        this.path = pathImage;
        getImageParam();
    }

    private void getImageParam(){
        String name = path.substring(path.lastIndexOf('/')+1);
        String[] strParams = name.split("_");
        camChn = Integer.parseInt(strParams[1]);
        long latitude = Long.parseLong(strParams[2]);
        long longitude = Long.parseLong(strParams[3]);
        timeMs = Long.parseLong(strParams[4].substring(0, strParams[4].indexOf('.')));
        Date date = new Date(timeMs);
        sTime =  new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(date);
        double dLat = latitude /1000000.0f;
        double dLon = longitude /1000000.0f;
        sLocation = String.format(Locale.ROOT, "%.6f - %.6f",dLon,dLat);
    }

    public long getImageTimeMs(){
        return timeMs;
    }
    public void setId(int id){
        idIndex = id;
    }

    /******************************
    * @return 1, 2, ..
     *********************************/
    public int getCamChn(){
        return camChn;
    }

    public String getImagePath(){
        return path;
    }

    public static Comparator<ImageData> imageDataComparator = new Comparator<ImageData>() {
        @Override
        public int compare(ImageData imageData1, ImageData imageData2) {
            long time1 = imageData1.getImageTimeMs();
            long time2 = imageData2.getImageTimeMs();
            return (int) (time1-time2);
        }
    };

    public JSONObject getStringJSON(){
        JSONObject imgJson = new JSONObject();
        try {
            imgJson.put("ID", idIndex);
            imgJson.put("time", sTime);
            imgJson.put("camChn", camChn);
            imgJson.put("location", sLocation);
//            imgJson.put("Link", <);
        }catch (Exception e){
            return null;
        }
        return imgJson;
    }
}
