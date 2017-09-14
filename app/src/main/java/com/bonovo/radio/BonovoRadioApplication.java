package com.bonovo.radio;

import android.app.Application;


public class BonovoRadioApplication extends Application {

	@Override
    public void onCreate() {
        super.onCreate();
		
		CrashHandler crashHandler = CrashHandler.getInstance();
        crashHandler.init(getApplicationContext());
    }

}
