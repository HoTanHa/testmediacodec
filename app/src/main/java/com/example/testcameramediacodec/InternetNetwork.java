package com.example.testcameramediacodec;

import android.content.Context;
import android.net.ConnectivityManager;
public class InternetNetwork {
    private Context context;

    public InternetNetwork(Context context){
        this.context = context;
    }

    public boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
    }

}
