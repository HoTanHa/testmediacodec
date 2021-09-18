package com.example.httpServer;

import org.json.JSONException;
import org.json.JSONObject;

public class VideoInterval {
    private long timeBegin = 0;
    private long timeEnd = 0;
    private int ID = 0;
    private int idGroup = 0;

    public VideoInterval(int id, int group, long timeBegin, long timeEnd) {
        this.timeBegin = timeBegin;
        this.timeEnd = timeEnd;
        this.ID = id;
        this.idGroup = group;
    }

    public JSONObject toJson() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("id", ID);
            jsonObject.put("group", idGroup);
            jsonObject.put("start", timeBegin * 1000);
            jsonObject.put("end", (timeEnd - 59) * 1000);
        }
        catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
        return jsonObject;
    }
}
