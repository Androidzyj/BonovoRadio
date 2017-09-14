package com.radio.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.RemoteViews;
import com.bonovo.radio.RadioService;

import com.bonovo.radio.R;
import com.bonovo.radio.RadioActivity;
import com.bonovo.radio.RadioService;
import java.io.Serializable;
import android.app.ActivityManager;
import java.util.List;
import android.content.SharedPreferences;


public class RadioPlayerAppWidget extends AppWidgetProvider{
    private static final String TAG = "RadioPlayerAppWidget";
    private static final boolean D = true;
	private static final String WIDGET_ACTION = "android.bonovo.radio.APPWIDGET_ACTION";
    private ComponentName mServiceComponent;
    private Intent mPlayStopIntent;
    //private Intent mNextIntent;
	private RemoteViews remoteViews;
    public RadioPlayerAppWidget() {
        mServiceComponent = new ComponentName("com.bonovo.radio",
                "com.bonovo.radio.RadioService");
        mPlayStopIntent = new Intent(WIDGET_ACTION);
        //mNextIntent = new Intent(RadioService.ACTION_NEXT);
        //mNextIntent.setComponent(mServiceComponent);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
		final Bundle bundle = intent.getExtras();
        if (D)Log.d(TAG, "onReceive action : " + action);
		if("com.bonovo.radio.widget.UPDATE".equals(action)){
			int freq = intent.getIntExtra("freq",-1);
			boolean play = intent.getBooleanExtra("playing",false);
			if(D)Log.d(TAG,"UPDATE playing: "+play+" ; freq: "+freq);
			updateWidget(context,freq,play);
		}else if(WIDGET_ACTION.equals(action)){
			boolean play = bundle.getBoolean("action_play");
			if(D)Log.d(TAG,"action_play= "+play);
			if(play){
				final Intent serviceIntent = new Intent();
				serviceIntent.setComponent(mServiceComponent);
				context.startService(serviceIntent);
			}else{
				//when RadioService is not started by this widget,this method can not stop RadioService.
				final Intent serviceIntent = new Intent();
				serviceIntent.setComponent(mServiceComponent);
				context.stopService(serviceIntent);

				//Intent stopIntent = new Intent("com.bonovo.radio.widget.STOP");
				//mContext.sendBroadcast(stopIntent);
			}
			updateWidget(context,-1,play);
		}		
        super.onReceive(context, intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		if(D)Log.d(TAG,"onUpdate");		
		// read sharedPreference about freq info.then update.
		//and then send a broadcast tell RadioService to update this widget.
		SharedPreferences preferences = context.getSharedPreferences("RadioPreferences", Context.MODE_PRIVATE);;
		int freq = preferences.getInt("mfreq",-1);
		if (D) Log.d(TAG, "freq : " + freq);	
		Intent requestIntent = new Intent("com.bonovo.radio.widget.REQUEST_UPDATE");
		context.sendBroadcast(requestIntent);
		updateWidget(context,freq,false);				
	}
	
    private void updateWidget(Context context,int freq,boolean play) { 
        if(D)Log.d(TAG,"updateWidget");	
		if(remoteViews==null){
			remoteViews = new RemoteViews("com.bonovo.radio",R.layout.radio_player_app_widget_layout);
		}
		AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
		int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(
						"com.bonovo.radio", "com.radio.widget.RadioPlayerAppWidget"));

		for (int appWidgetId : appWidgetIds) {
			if(D)Log.d(TAG,"appWidgetId= "+appWidgetId);		
			Intent intent = new Intent();
			intent.setComponent(new ComponentName(context, RadioActivity.class));
			remoteViews.setOnClickPendingIntent(R.id.widget_view_container,PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT));
			if(freq>0){//freq has been changed,update freq view.
				updateFreq(remoteViews,freq);
			}
			updatePlayButton(remoteViews,context,play); 
			appWidgetManager.updateAppWidget(appWidgetId, remoteViews);				
		}
    }


	private void updateFreq(RemoteViews remoteViews,int hz){
		String freq = "";	
		//freq
		if (!TextUtils.isEmpty(String.valueOf(hz))) {
			freq = freqToString(hz);
		}
		remoteViews.setTextViewText(R.id.widget_text_number,freq);
		//fmam
		remoteViews.setTextViewText(R.id.widget_text_fm_am, fmOrAm(hz));
	}

	private void updatePlayButton(RemoteViews remoteViews,Context context,boolean play){
		//play or stop
		if(play){	
			//show play view,and register click event
			remoteViews.setImageViewResource(R.id.widget_image_play_stop,
					R.drawable.radio_player_app_widget_stop_background);
			mPlayStopIntent.putExtra("action_play",false);
			remoteViews.setOnClickPendingIntent(R.id.widget_image_play_stop, PendingIntent
					.getBroadcast(context, 0, mPlayStopIntent, PendingIntent.FLAG_UPDATE_CURRENT));
			
		}else{
			//show stop view,and register click event
			remoteViews.setImageViewResource(R.id.widget_image_play_stop,
					R.drawable.radio_player_app_widget_play_background);
			mPlayStopIntent.putExtra("action_play",true);
			remoteViews.setOnClickPendingIntent(R.id.widget_image_play_stop, PendingIntent
					.getBroadcast(context, 0, mPlayStopIntent, PendingIntent.FLAG_UPDATE_CURRENT));		
		}
	}
	
	private String freqToString(int freq){
		if(D)Log.d(TAG,"freqToString before freq= "+freq);
		if (freq >= RadioService.FM_LOW_FREQ && freq <= RadioService.FM_HIGH_FREQ) {
			String strFreq = String.valueOf((freq/ 100));
			strFreq = strFreq+"."+String.valueOf((freq  / 10 % 10));
			if(D)Log.d(TAG,"after freq= "+strFreq);
			return  strFreq;
		}else{
			return String.valueOf(freq);
		}
	}

	/*return : fm, am, (unkown)*/
	private String fmOrAm(int freq){
		//if(D)Log.d(TAG,"FM_LOW_FREQ "+RadioService.FM_LOW_FREQ+"; FM_HIGH_FREQ "+
		//	RadioService.FM_HIGH_FREQ+"; AM_LOW_FREQ "+RadioService.AM_LOW_FREQ+";AM_HIGH_FREQ "+RadioService.AM_HIGH_FREQ);
		if (freq >= RadioService.FM_LOW_FREQ && freq <= RadioService.FM_HIGH_FREQ) {
			return "FM";
		}else if(freq >= RadioService.AM_LOW_FREQ && freq <= RadioService.AM_HIGH_FREQ){
			return "AM";
		}else{
			return "";
		}
	}
}
