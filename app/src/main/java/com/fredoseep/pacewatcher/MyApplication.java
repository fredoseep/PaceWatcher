package com.fredoseep.pacewatcher;

import android.app.Application;

public class MyApplication extends Application {
    private AppRepository appRepository;

    @Override
    public void onCreate(){
        super.onCreate();
        appRepository = new AppRepository();
        appRepository.loadAsync();
    }

    public AppRepository getAppRepository(){
        return appRepository;
    }
}
