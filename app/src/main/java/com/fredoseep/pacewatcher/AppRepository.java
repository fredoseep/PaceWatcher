package com.fredoseep.pacewatcher;

import android.app.Application;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class AppRepository {
    private int currentSeason = -1;

    private static final String TAG = "AppRepository";
    private static final String RECENT_MATCH_URL = "https://api.mcsrranked.com/matches";
    public AppRepository(){
    }

    public void setCurrentSeason(int currentSeason) {
        this.currentSeason = currentSeason;
    }

    public int getCurrentSeason() {
        return currentSeason;
    }

    public void loadAsync() {
        new Thread(() -> {
            HttpURLConnection httpURLConnection = null;

            try {
                URL url = new URL(RECENT_MATCH_URL);
                httpURLConnection = (HttpURLConnection) url.openConnection();
                httpURLConnection.setConnectTimeout(3000);
                httpURLConnection.setReadTimeout(3000);
                httpURLConnection.setRequestMethod("GET");
                int responseCode = httpURLConnection.getResponseCode();
                if(responseCode!=200){
                    Log.e(TAG, "loadAsync: connection error, responseCode: "+ responseCode);
                }
                else {
                    StringBuilder sb = new StringBuilder();
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream()));
                    String inputLine;
                    while ((inputLine = bufferedReader.readLine()) != null) {
                        sb.append(inputLine);
                    }
                    bufferedReader.close();
                    httpURLConnection.disconnect();
                    JSONObject root = new JSONObject(sb.toString());
                    if(root.has("data")){
                        JSONArray matchData = root.getJSONArray("data");
                        if(matchData.length()>0){
                            JSONObject matchObject = matchData.getJSONObject(0);
                            if(matchObject.has("season")){
                                int currentSeason = matchObject.getInt("season");
                                this.setCurrentSeason(currentSeason);
                                Log.d(TAG, "loadAsync: current season got: "+currentSeason);
                            }
                            else Log.e(TAG,"match object doesn't contain season key");
                        }
                        else Log.e(TAG,"matchData array is empty");
                    }
                    else Log.e(TAG, "loadAsync: response doesn't contain data key");
                }
            } catch (Exception e) {
                Log.e(TAG, "loadAsync: "+e.getMessage());
            }
        }).start();
    }
}
