package com.bonovo.radio;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.xmlpull.v1.XmlPullParser;
import com.radio.widget.RadioPlayerStatusStore;

import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.content.ComponentName;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager.NameNotFoundException;
import android.location.Address;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Xml;
import android.view.KeyEvent;
import android.widget.Toast;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
//import com.android.internal.car.can.CanRadio;
import com.google.gson.reflect.TypeToken;
import com.google.gson.Gson;
import java.lang.reflect.Type;
import com.bonovo.audiochannelservice.IAudioChannelService;
import android.content.ServiceConnection;
import android.os.RemoteException;

public class RadioService extends Service implements RadioInterface,
		AudioManager.OnAudioFocusChangeListener {
		
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
    	if(DEBUG)Log.d(TAG,"onStartCommand mIsRadioPlaying: "+mIsRadioPlaying+";  mAudioChannelService==null ="+( mAudioChannelService==null));		
		if(!mIsRadioPlaying){
			muteOrRecover(false);	
			setFreq(curFreq);//ensure switch to radio audio channel.
		}			
        return START_STICKY_COMPATIBILITY;
    }

	private static final boolean DEBUG = true;
	private static final String TAG = "RadioService";
	private static final int RADIO_OPEN_7415 = 1;
	private static final int RADIO_CLOSE_7415 = 0;
	private static final int RADIO_POWER_ON = 1;
	private static final int RADIO_POWER_OFF = 0;
	private static final int RADIO_SET_MUTE = 1;
	private static final int RADIO_SET_NOTMUTE = 0;
	private static final int RADIO_TURN_CHANNEL = 1;
	private static final int RADIO_DATA_READ = 1;
	public static final int RADIO_DATA_SAVE = 0;

	public static int FLAG_AUTO = 0;
	public static int STEP_OR_AUTO;			//step or auto flag
	public static int IS_AUTO_NOW = 1;
	public static int SEARCH_OVER = 0;
	
	public static final int CHINA_MODEL = 0;
	public static final int JAPAN_MODEL = 1;
	public static final int EUR_MODEL = 2;
	public static int RADIO_MODEL = 0;
	
	public static  int FM_LOW_FREQ = 8700;
	public static  int FM_HIGH_FREQ = 10800;
	public static  int AM_LOW_FREQ = 520;
	public static  int AM_HIGH_FREQ = 1710;

	/** radio status changed type */
	public static final int INFLATE_CHANNEL_LIST = 1;
	public static final int UPDATE_DETAIL_FREQ =2;
	public static final int ADD_CHANNEL_ITEM = 3;
	public static final int AUTO_SEARCH_COMPLETE = 4;
	public static final int TURN_FMAM_UI = 5;
	public static final int NEXT_CHANNEL = 6;
	public static final int LAST_CHANNEL = 7;

	private static final int RADIO_NO_ACTION = 0;
	private static final int RADIO_SEARCHING = 1;
	private static final int RADIO_START_SEARCH = 2;
		
	public static final String MSG_CLOSE = "com.bonovo.radioplayer.close";//RadioImportActivity
	private static  final String CHANGE_ACTION_BACKWARD = "android.bonovo.radio.FREQCHANGE_ACTION";
	private static  final String CHANGE_ACTION_FORWARD = "android.bonovo.radio.FREQCHANGE_ACTION_T";
	// show the FM back play notification on the title bar
	private static final int NOTIFICATION_ID = 1;
	PendingIntent contentIntent;
	Notification notification;

	private int functionId = 0; 
	private int curFreq;
	private int radioType = RADIO_FM;
	private int radioVolume;
	private int radioMute;
	private AudioManager mAudioManager;
	private static int mVolume = 100;
	private Context mContext;
	private static boolean mRemote;					

	private List<ChannelItem> mChannelList_fm ;
	private List<ChannelItem> mChannelList_am ;
	private List<ChannelItem> mChannelList_heart ;
	
	private ArrayList<String> m_province_list = null;
	private ArrayList<List<String>> m_city_list = null;
	private ArrayList<List<List<ChannelItem>>> m_channel_list = null;
	private RadioStatusChangeListener mStatusListener;
	private SharedPreferences settings;
	public boolean mIsSearchThreadRunning = false;
	
	private static boolean mDown = false;			//keyEvent flag
	public static boolean isLive = false;			//activity islive flag
	
	public boolean mIsRadioPlaying;
	private IAudioChannelService mAudioChannelService;


	static {
		System.loadLibrary("bonovoradio_jni");
	}

	// jni
	private native final int jniPowerOnoff(int OnOff)
			throws IllegalStateException;

	private native final int jniSetVolume(int volume)
			throws IllegalStateException;

	private native final int jniSetMute(int muteState)
			throws IllegalStateException;

	private native final int jniGetMute() throws IllegalStateException;

	private native final int jniFineLeft(int freq) throws IllegalStateException;

	private native final int jniFineRight(int freq)
			throws IllegalStateException;

	private native final int jniStepLeft(int freq) throws IllegalStateException;

	private native final int jniStepRight(int freq)
			throws IllegalStateException;

	private native final int jniAutoSeek(int freq) throws IllegalStateException;

	private native final int jniReadSeek(int[] freqs, int count)
			throws IllegalStateException;

	public native final int jniTurnFmAm(int type) throws IllegalStateException;

	private native final int jniSetModel(int type) throws IllegalStateException;

	private native final int jniSetFreq(int freq) throws IllegalStateException;
	
	private native final int jniSetRemote(int remote) throws IllegalStateException;

	static class ChannelItem {
		String freq;
		String name;
		String abridge;
	}

	public class ServiceBinder extends IRadioService.Stub {
		public RadioService getService() {

			return RadioService.this;
		}
		public char getRadioType(){
			if(radioType==RADIO_FM){
				return 'f';
			}else{
				return 'a';
			}
		}
		
		public void turnFM(){
			if(DEBUG)Log.d(TAG,"turnToFM ");
			if(isLive){
				if(radioType!=RADIO_FM){				
					setRadioType(RADIO_FM);
					turnFmAm(0);				
					Intent intent = new Intent("com.bonovo.radio.CHANGE");
					intent.putExtra("command","fm");
					sendBroadcast(intent);
				}
			}
		}
		public void turnAM(){
			if(DEBUG)Log.d(TAG,"turnAM");
			if(isLive){
				if(radioType!=RADIO_AM){
					setRadioType(RADIO_AM);
					turnFmAm(1);
					Intent intent = new Intent("com.bonovo.radio.CHANGE");
					intent.putExtra("command","am");
					sendBroadcast(intent);
				}
			}
		}
		public void nextFreq(){
			if(DEBUG)Log.d("Test","nextFreq");
			if(!isLive){
				Log.d("Test", "nextFreq: ");
				Intent intent = new Intent(RadioService.this,RadioActivity.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(intent);
			}
			if(functionId==0){//fine
				fineRight(curFreq);
			}else{//step
				stepRight(curFreq);
			}
		}
		public void preFreq(){
			if(DEBUG)Log.d("Test","preFreq");
			if(!isLive){
				Log.d("Test", "preFreq: ");
				Intent intent = new Intent(RadioService.this,RadioActivity.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(intent);
			}
			if(functionId==0){//fine
				fineLeft(curFreq);
			}else{//step
				stepLeft(curFreq);
			}
		}
		public void turnToFM(String freq){
			if(DEBUG)Log.d(TAG,"turnToFM freq= "+freq);		
			if(!mIsSearchThreadRunning){					
				if(!isLive){
					Intent intent = new Intent(RadioService.this,RadioActivity.class);
					intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					startActivity(intent);
				}
				if(radioType!=RADIO_FM){				
					turnFmAm(0);	
				}
				if(freq.contains(".")){
					curFreq =Integer.parseInt(freq.replaceAll("\\.", "")) * 10;
				}else if(!freq.equals("")){
					curFreq = Integer.parseInt(freq);
				}
				muteOrRecover(false);
				setFreq(curFreq);
				mRadioplayerHandler.sendEmptyMessage(TURN_FMAM_UI);
			}else {
				Toast.makeText(getApplicationContext(), R.string.searching, Toast.LENGTH_SHORT).show();
			}
		}
		public void turnToAM(String am){
			if(DEBUG)Log.d(TAG,"turnToAM am= "+am);			
			if(!mIsSearchThreadRunning){					
				if(!isLive){
					Intent intent = new Intent(RadioService.this,RadioActivity.class);
					intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					startActivity(intent);
				}
				Log.d(TAG,"before turnToAM radioType= "+radioType);
				if(radioType!=RADIO_AM){
					turnFmAm(1);
				}

				if(am.contains(".")){ 
					curFreq =Integer.parseInt(am.replaceAll("\\.", "")) * 10;
				}else if(!am.equals("")){
					curFreq = Integer.parseInt(am);
				}
				muteOrRecover(false);
				setFreq(curFreq);
				mRadioplayerHandler.sendEmptyMessage(TURN_FMAM_UI);
			}else {
				Toast.makeText(getApplicationContext(), R.string.searching, Toast.LENGTH_SHORT).show();
			}
		}
	}

	private ServiceBinder mServiceBinder;
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		if(DEBUG) Log.v(TAG, "------onBind()");
		mServiceBinder = new ServiceBinder();
		return mServiceBinder;
	}

	
	@Override
	public boolean onUnbind(Intent intent) {
		// TODO Auto-generated method stub
		if (DEBUG)
			Log.d(TAG, "------onUnbind()");
		mServiceBinder = null;
		return super.onUnbind(intent);
	}

	private RadioPlayerStatusStore mRaidoPlayerStatusStore;
	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		super.onCreate();
		mContext = this;
		
		mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE); 
		settings = getSharedPreferences("RadioPreferences", MODE_PRIVATE);
		mRaidoPlayerStatusStore = RadioPlayerStatusStore.getInstance();
        mRaidoPlayerStatusStore.setContext(this);
		
		readAndSetModelInfo();//set model,china,japan etc.	
		updatePreferences(RADIO_DATA_READ);//set current freq info

		PowerOnOff(true);

		Intent canbusIntent = new Intent("com.bonovo.can.REQUEST_DATA");
		if (RADIO_FM == radioType) {
			jniTurnFmAm(0); // open fm
			canbusIntent.putExtra("band",true);
		} else if (RADIO_AM == radioType) {
			jniTurnFmAm(1); // open am
			canbusIntent.putExtra("band",false);
		}

		canbusIntent.putExtra("mode","carRadioShow");
		canbusIntent.putExtra("fun","power");
		canbusIntent.putExtra("status",true);
		canbusIntent.putExtra("freq",curFreq);
		sendBroadcast(canbusIntent);
		Log.d(TAG, "onCreate: send com.bonovo.can.REQUEST_DATA");
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this); 
		boolean	remote = prefs.getBoolean("checkbox_remote_preference", true);
		if(remote){
			setRemote(1);
		}else {
			setRemote(0);
		}
					 
		//send a broadcast tell keyEdit radioService has been started
		Intent intent = new Intent("com.bonovo.radio.START");
		sendBroadcast(intent);

		//bind AudioChannelService.
		Intent startService = new Intent();
        ComponentName name = new ComponentName("com.bonovo.audiochannelservice","com.bonovo.audiochannelservice.AudioChannelService");
        startService.setComponent(name);
        startService(startService);
        bindService(startService,mAudioChannelServiceConn,0);
	
		this.registerReceiver(myReceiver, getIntentFilter());
		if(DEBUG) Log.d(TAG, "------onCreate()");
	}


	private ServiceConnection mAudioChannelServiceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
        	Log.d(TAG,"mAudioChannelServiceConn onServiceConnected");
			mAudioChannelService = IAudioChannelService.Stub.asInterface(service);
			if(!mIsRadioPlaying){
				setFreq(curFreq);
				muteOrRecover(false);
			}else{
				saveAudioChannel();
			}	
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
			mAudioChannelService = null;
        }
    };


	private void updatePlayStatus(){
		if(!mIsRadioPlaying){
			mAudioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN);
			mIsRadioPlaying = true;
		}
		saveAudioChannel();
		mRaidoPlayerStatusStore.put(RadioPlayerStatusStore.KEY_PLAY_STOP,RadioPlayerStatusStore.VALUE_PLAY);
	}

	
	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		mAudioManager.abandonAudioFocus(this);		
		mIsRadioPlaying = false;		
		mRaidoPlayerStatusStore.put(RadioPlayerStatusStore.KEY_PLAY_STOP,RadioPlayerStatusStore.VALUE_STOP);
		updateWidget();
		updatePreferences(RADIO_DATA_SAVE);
		this.unregisterReceiver(myReceiver);
		unbindService(mAudioChannelServiceConn);
		PowerOnOff(false);

		Intent canbusIntent = new Intent("com.bonovo.can.REQUEST_DATA");
		canbusIntent.putExtra("mode","carRadioShow");
		canbusIntent.putExtra("fun","power");
		canbusIntent.putExtra("status",false);
		sendBroadcast(canbusIntent);
		Log.d(TAG, "onDestroy: send com.bonovo.can.REQUEST_DATA ");

		stopForeground(false);
		if (DEBUG)Log.d(TAG, "onDestroy()  end curfreq is " + curFreq);
	}

	private Handler mRadioplayerHandler = new Handler() {
		public void handleMessage(Message msg) {
			if(mStatusListener!=null){
				if(msg.obj!=null){
					mStatusListener.onStatusChange(msg);
				}else{
					mStatusListener.onStatusChange(msg.what);
				}		
			}						
		}
	};

	@SuppressLint("HandlerLeak")
	Runnable runnable = new Runnable() {

		@Override
		public void run() {
			// TODO Auto-generated method stub
			int res, status = RADIO_START_SEARCH;
			if (DEBUG)Log.d(TAG, "START SEARCH +++++++++++++!!!!");
			jniSetVolume(0);
			mIsSearchThreadRunning = true;
			int[] freq = new int[3];

			do {
				res = jniReadSeek(freq, 3);
				Log.d(TAG,"search res= "+res);
				if (res < 0) {
					if (DEBUG)
						Log.e(TAG, "jniReadSeek error!");
					break;
				}
				status = freq[0];
				if (res == 0) {//auto search
					 Log.d(TAG, "++++++++++++++++ res:" + res +
					 " freq[0]:"+freq[0] + " freq[1]:" + freq[1] + " freq[2]:"
					 + freq[2]);
					if (FLAG_AUTO == 1) {
						/***************auto model************/
						curFreq = freq[2];
						//if(getRadioType() == RADIO_FM){
							//mCanRadio.sendRadioInfo(CanRadio.BAND_FM, curFreq);
						//}else if (getRadioType() == RADIO_AM) {
							//mCanRadio.sendRadioInfo(CanRadio.BAND_AM, curFreq);
					//	}
						mRadioplayerHandler.sendEmptyMessage(UPDATE_DETAIL_FREQ);
						if (freq[1] == 1) {
							curFreq = freq[2];
							Log.d(TAG,"@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@curFreq(FLAG Auto) ="+ curFreq);

						//	if(getRadioType() == RADIO_FM){
								//mCanRadio.sendRadioInfo(CanRadio.BAND_FM, curFreq);
					//		}else if (getRadioType() == RADIO_AM) {
								//mCanRadio.sendRadioInfo(CanRadio.BAND_AM, curFreq);
							//}
							Message msg = Message.obtain();
							ChannelItem newItem = new ChannelItem();
							if(radioType==RADIO_FM){
								newItem.freq = Double.toString(curFreq/100.0);
								mChannelList_fm.add(newItem);
							}else if(radioType==RADIO_AM){
								newItem.freq = Integer.toString(curFreq);
								mChannelList_am.add(newItem);
							}
							msg.obj = newItem;
							msg.what = ADD_CHANNEL_ITEM;
							mRadioplayerHandler.sendMessage(msg);
						}
					} else {
						/***************step model************/
						curFreq = freq[2];						
						//if(getRadioType() == RADIO_FM){
						//	mCanRadio.sendRadioInfo(CanRadio.BAND_FM, curFreq);
						//}else if (getRadioType() == RADIO_AM) {
						//	mCanRadio.sendRadioInfo(CanRadio.BAND_AM, curFreq);
						//}		
						Log.d(TAG, "step search: curFreq =" + curFreq);					
						mRadioplayerHandler.sendEmptyMessage(UPDATE_DETAIL_FREQ);
						/*******************************************************************************/
					}		
				}
				try {
					Thread.sleep(5);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} while (status != RADIO_NO_ACTION);
			if(STEP_OR_AUTO == IS_AUTO_NOW){
				FLAG_AUTO = 0;
				STEP_OR_AUTO = SEARCH_OVER;
				//when auto search is complete,set Channal0 with the first freq 
				if(radioType==RADIO_FM && mChannelList_fm.size()>0){
					curFreq = Integer.parseInt(mChannelList_fm.get(0).freq
						.replaceAll("\\.", "")) * 10;
				}else if(radioType==RADIO_AM && mChannelList_am.size()>0){
					curFreq = Integer.parseInt(mChannelList_am.get(0).freq);
				}
				Log.d(TAG, "auto search complete");
			}
			setFreq(curFreq);
			mIsSearchThreadRunning = false;
			updatePreferences(RADIO_DATA_SAVE);
			updatePlayStatus();
			mRadioplayerHandler.sendEmptyMessage(AUTO_SEARCH_COMPLETE);
			jniSetVolume(mVolume);
			Log.d(TAG, "+++++++++++++ STOP SEARCH!!!!");
		}
	};

	public void updatePlaybackTitle() {
		if (DEBUG)Log.v(TAG, "---Notification curFreq = " + curFreq);
		NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		CharSequence contentTitle = getResources().getString(R.string.app_name);
		CharSequence contentText = getResources().getString(R.string.playing)
				+ ": " + changeCurFreqToCS(curFreq);
		contentIntent = PendingIntent.getActivity(getApplicationContext(), 0,
		new Intent(getApplicationContext(),
		RadioActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
		notification = new Notification(R.drawable.import_channel,
				contentTitle, 0);
		notification.flags |= Notification.FLAG_ONGOING_EVENT;

		Notification.Builder builder = new Notification.Builder(getApplicationContext());
		builder.setContentTitle(contentTitle);
		builder.setContentText(contentText);
		builder.setContentIntent(contentIntent);
		notification = builder.build();
		manager.notify(NOTIFICATION_ID,notification);
		startForeground(NOTIFICATION_ID, notification); 

	}

	public void muteOrRecover(boolean mute){
		if(mute){
			if(DEBUG)Log.d(TAG,"mute audio");		
			jniSetVolume(0);			
			mIsRadioPlaying = false;
			mRaidoPlayerStatusStore.put(RadioPlayerStatusStore.KEY_PLAY_STOP,RadioPlayerStatusStore.VALUE_STOP);
			updateWidget();
		}else{
			jniSetVolume(mVolume);
			if(DEBUG)Log.d(TAG,"recover volumn="+mVolume);
			updatePlayStatus();
		}
	}

	@Override
	public void onAudioFocusChange(int focusChange) {
		// TODO Auto-generated method stub
		//if (DEBUG)
		Log.v(TAG, "----onAudioFocusChange----focusChange:" + focusChange);
		switch (focusChange) {
		case AudioManager.AUDIOFOCUS_GAIN:
			Log.v(TAG, "----onAudioFocusChange----AudioManager.AUDIOFOCUS_GAIN: ensure radio audio channel" + focusChange);
			jniSetFreq(curFreq);
			muteOrRecover(false);
			AudioManager audioManager = (AudioManager) this.getSystemService(AUDIO_SERVICE);
			ComponentName mRemoteControlClientReceiverComponent;
			mRemoteControlClientReceiverComponent = new ComponentName(
					getPackageName(), MediaButtonIntentReceiver.class.getName());
			audioManager.unregisterMediaButtonEventReceiver(mRemoteControlClientReceiverComponent);
			audioManager.registerMediaButtonEventReceiver(mRemoteControlClientReceiverComponent);
			break;
		case AudioManager.AUDIOFOCUS_LOSS:
			Log.v(TAG, "----onAudioFocusChange----AudioManager.AUDIOFOCUS_LOSS:" + focusChange+"; mVolume="+mVolume);
			if(mIsRadioPlaying){
				muteOrRecover(true);
			}	
			//************ removed by bonovo zbiao
			//stopService(new Intent("com.bonovo.RadioService"));
			//this.stopSelf();
										
			//break;
		case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
			Log.v(TAG, "----onAudioFocusChange----AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:" + focusChange);
			muteOrRecover(true);
			break;
		case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
			Log.v(TAG, "----onAudioFocusChange----AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:" + focusChange);
			break;

		}

	}

	public CharSequence changeCurFreqToCS(int freq) { /* �˺���δʹ�� */
		StringBuffer sb = new StringBuffer();
		if (freq >= 8700 && freq <= 10800) {
			sb.append(freq / 100).append('.').append(freq / 10 % 10)
					.append("MHz");
		} else {
			sb.append(freq).append("KHz");
		}
		return sb;
	}

    private void PowerOnOff(boolean onOff) {
		if(onOff) {
			jniPowerOnoff(RADIO_POWER_ON);
		}else{
			jniPowerOnoff(RADIO_POWER_OFF);
		}
    }
	//1:fm ,2:am
	public void turnFmAm(int type) {
		if (DEBUG)Log.v(TAG, "<myu>turnFmAm");
		radioType = type;
		if(type == RADIO_FM || type == RADIO_AM){
			jniTurnFmAm(type);
		}
	}

	public int getRadioType() {
		return radioType;
	}

	public void setRadioType(int type) {
		radioType = type;
	}

	public int setVolume(int volume) {
		mVolume = volume;
		if (DEBUG) Log.v(TAG, "(Service)mVolume = "+mVolume);
		jniSetVolume(mVolume);
		SharedPreferences.Editor editor = settings.edit();
		editor.putInt("mvolume", mVolume);
		editor.commit();
		return mVolume;
	}

	public int getVolume() {
		return mVolume;
	}


	public List<ChannelItem> getChannelList(){		
		if(radioType == RADIO_AM){
			return mChannelList_am;
		}else if(radioType == RADIO_COLLECT){
			return mChannelList_heart;
		}else {
			return mChannelList_fm;
		}
	}

	//type:fm am heart
	public List<ChannelItem> getChannelList(String type){	
		if("fm".equals(type)){
			return mChannelList_fm;
		}else if("am".equals(type)){
			return mChannelList_am;
		}else if("heart".equals(type)){
			return mChannelList_heart;
		}
		return null;

	}	
	
	public ChannelItem getChannelItem(int id) {
		if(mChannelList_am.size()>0 &&radioType == RADIO_AM){
			return mChannelList_am.get(id);
		}else if(mChannelList_heart.size()>0 && radioType == RADIO_COLLECT){
			return mChannelList_heart.get(id);
		}else if(mChannelList_fm.size()>0){
			return mChannelList_fm.get(id);
		}
		return null;
	}

	public boolean setChannelItem(int id, ChannelItem item) {
		if (DEBUG)
			Log.e(TAG, "============ setChannelItem!");
		if(RADIO_FM == radioType){
			mChannelList_fm.set(id, item);
		}else if(RADIO_AM== radioType){
			mChannelList_am.set(id, item);
		}else if(RADIO_COLLECT== radioType){
			mChannelList_heart.set(id, item);
		}

		return true;
	}

	@Override
	public void setFunctionId(int id) {
		// TODO Auto-generated method stub
		functionId = id;
	}

	@Override
	public int getFunctionId() {
		// TODO Auto-generated method stub
		return functionId;
	}

	@Override
	public void setCurrentFreq(int freq) {
		// TODO Auto-generated method stub
		curFreq = freq;
	}

	@Override
	public int getCurrentFreq() {
		// TODO Auto-generated method stub
		return curFreq;
	}

	@Override
	public int fineLeft(int freq) {
		// TODO Auto-generated method stub
		if (DEBUG)
			Log.v(TAG, "------ JNI fineLeft worked. Input freq:" + freq);
		curFreq = jniFineLeft(freq);
		//if(getRadioType() == RADIO_FM){
			//mCanRadio.sendRadioInfo(CanRadio.BAND_FM, curFreq);
	//	}else if (getRadioType() == RADIO_AM) {
	//		mCanRadio.sendRadioInfo(CanRadio.BAND_AM, curFreq);
	//	}
		if (DEBUG)
			Log.d(TAG, "------ curFreq:" + curFreq);
		updatePreferences(RADIO_DATA_SAVE);
		updatePlaybackTitle();
		if(mStatusListener!=null){
			mRadioplayerHandler.sendEmptyMessage(AUTO_SEARCH_COMPLETE);
		}		
		return curFreq;
	}

	@Override
	public int fineRight(int freq) {
		// TODO Auto-generated method stub
		if (DEBUG)
			Log.v(TAG, "++++++JNI fineRight worked.  Input freq:" + freq);
		curFreq = jniFineRight(freq);
//		if(getRadioType() == RADIO_FM){
//			mCanRadio.sendRadioInfo(CanRadio.BAND_FM, curFreq);
//		}else if (getRadioType() == RADIO_AM) {
//			mCanRadio.sendRadioInfo(CanRadio.BAND_AM, curFreq);
//		}
		if (DEBUG)
			Log.d(TAG, "+++++ curFreq:" + curFreq);
		updatePreferences(RADIO_DATA_SAVE);
		updatePlaybackTitle();
		if(mStatusListener!=null){
			mRadioplayerHandler.sendEmptyMessage(AUTO_SEARCH_COMPLETE);
		}
		return curFreq;
	}

	@Override
	public int stepLeft(int freq) {
		// TODO Auto-generated method stub
		if (DEBUG)
			Log.v(TAG, " JNI stepLeft worked ");
		if (mIsSearchThreadRunning) {
			Toast toast = Toast.makeText(getApplicationContext(),
					R.string.searchwait, Toast.LENGTH_SHORT);
			return freq;
		}
		jniSetFreq(freq);
		jniStepLeft(freq);
		new Thread(runnable).start();
		return curFreq;
	}

	@Override
	public int stepRight(int freq) {
		// TODO Auto-generated method stub
		if (DEBUG)
			Log.v(TAG, " JNI stepRight worked ");
		if (mIsSearchThreadRunning) {
			Toast toast = Toast.makeText(getApplicationContext(),
					R.string.searchwait, Toast.LENGTH_SHORT);
			return freq;
		}
		jniSetFreq(freq);
		jniStepRight(freq);
		new Thread(runnable).start();	
		return curFreq;
	}

	@Override
	public int getCurChannelId() {
		// TODO Auto-generated method stub
		return 0;
	}

	@SuppressWarnings("deprecation")
	@Override
	public void setCurChannelId(int id) {
		// TODO Auto-generated method stub
	}

	@Override
	public void registStatusListener(RadioStatusChangeListener listener) {
		// TODO Auto-generated method stub
		mStatusListener = listener;
	}

	@Override
	public void unRegistStatusListener() {
		// TODO Auto-generated method stub
		mStatusListener = null;
	}
	
	public void setRemote(int remote) {
		// TODO Auto-generated method stub
		jniSetRemote(remote);
	}

	@Override
	public void onAutoSearch() {
		// TODO Auto-generated method stub
		if (DEBUG)
			Log.d(TAG, "onAutoSearch");
		if (mIsSearchThreadRunning) {
			Toast toast = Toast.makeText(getApplicationContext(),
					R.string.searchwait, Toast.LENGTH_SHORT);
			return;
		}
		getChannelList().clear();
		FLAG_AUTO = 1;
		STEP_OR_AUTO = IS_AUTO_NOW;
		//jniSetFreq(curFreq);

		if(radioType == RADIO_FM){
			curFreq = FM_LOW_FREQ;
		}else if(radioType == RADIO_AM){
			curFreq = AM_LOW_FREQ;
		}
		jniAutoSeek(curFreq);
		new Thread(runnable).start();

	}

	@Override
	public void setFreq(int freq) {
		// TODO Auto-generated method stub
		if (DEBUG)Log.v(TAG, "JNI setfreq has worked ------freq is " + freq);
		curFreq = freq;
		jniSetFreq(freq);

		Intent canbusIntent = new Intent("com.bonovo.can.REQUEST_DATA");
		canbusIntent.putExtra("mode","carRadioShow");
		canbusIntent.putExtra("fun","set");
		if (radioType ==RadioService.RADIO_FM){
			canbusIntent.putExtra("band",true);
		}else if (radioType ==RadioService.RADIO_AM){
			canbusIntent.putExtra("band",false);
		}
		canbusIntent.putExtra("freq",curFreq);
		sendBroadcast(canbusIntent);

		updatePlaybackTitle();
		updateWidget();
	}

    private void saveAudioChannel() {
		Log.d(TAG,"saveAudioChannel mAudioChannelService==null "+(mAudioChannelService==null));
		if(mAudioChannelService!=null){
			try {
                mAudioChannelService.setAudioChannel(4);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
		}else{
			Intent startService = new Intent();
	        ComponentName name = new ComponentName("com.bonovo.audiochannelservice","com.bonovo.audiochannelservice.AudioChannelService");
	        startService.setComponent(name);
	        startService(startService);
	        bindService(startService,mAudioChannelServiceConn,0);
		}
    }
	
	public ArrayList<String> radioGetProvince() {
		return m_province_list;
	}

	public List<String> radioGetCity(int id) {
		return m_city_list.get(id);
	}

	public List<String> radioGetChannel(int provinceId, int cityId) {
		List<String> viewList = new ArrayList<String>();
		for (ChannelItem channel : m_channel_list.get(provinceId).get(cityId)) {
			String viewStr = new String(" " + channel.freq + "   "
					+ channel.name);
			viewList.add(viewStr);
		}
		return viewList;
	}

	private String getLocalLanguage() {
		// TODO Auto-generated method stub
		String language = Locale.getDefault().getLanguage();
//		SharedPreferences.Editor editor = settings.edit(); /* ��editor���ڱ���״̬ */
//		editor.putString("local_language", language);
//		editor.commit();
//		Log.v(TAG, "-->" + language);
		return language;
	}
	
	/* 解析XML */
	public boolean radioReadXML() {
		InputStream inputStream;
		String language = settings.getString("local_language", "zh");
		if(DEBUG)Log.v(TAG, "-->" + language + "|getLocalLanguage()== "+getLocalLanguage().equals(language));
		if (m_province_list != null && m_city_list != null
				&& m_channel_list != null ) {
			return true;
		}
		if (DEBUG)
			Log.v(TAG, "radioReadXML is into");
		XmlPullParser parser = Xml.newPullParser();
		/*
		 * Parse XML
		 */
		try {
			if(getLocalLanguage().equals("zh")){
				inputStream = getAssets().open("RadioDefChannel.xml"); 
			}else {
				inputStream = getAssets().open("RadioDefChannel_otherLanguage.xml"); 
			}

			parser.setInput(inputStream, "UTF-8");
			int eventType = parser.getEventType();

			ArrayList<String> curCityList = null;
			ChannelItem curChannelItem = null;
			List<ChannelItem> curItemList = null;
			List<List<ChannelItem>> curCityItemList = null;

			while (eventType != XmlPullParser.END_DOCUMENT) {
				switch (eventType) {
				case XmlPullParser.START_DOCUMENT:
					m_province_list = new ArrayList<String>();
					m_city_list = new ArrayList<List<String>>();
					m_channel_list = new ArrayList<List<List<ChannelItem>>>();
					break;
				case XmlPullParser.START_TAG:
					String name = parser.getName();
					if (name.equals("Province")) {
						m_province_list.add(parser.getAttributeValue(0));
						curCityList = new ArrayList<String>();
						curCityItemList = new ArrayList<List<ChannelItem>>();
					} else if (name.equals("City")) {
						curCityList.add(parser.getAttributeValue(0));
						curItemList = new ArrayList<ChannelItem>();
					} else if (name.equals("Channel")) {
						curChannelItem = new ChannelItem();
					} else if (name.equals("Abbr")) {
						curChannelItem.abridge = parser.getAttributeValue(0);
					} else if (name.equals("Name")) {
						curChannelItem.name = parser.getAttributeValue(0);
					} else if (name.equals("Freq")) {
						// String StrNoPoint =
						// parser.getAttributeValue(0).replaceAll("\\.", "");
						// curChannelItem.freq = Integer.parseInt(StrNoPoint);
						curChannelItem.freq = parser.getAttributeValue(0);
					}
					break;
				case XmlPullParser.END_TAG:
					if (parser.getName().equals("Province")) {
						if (curCityList != null) {
							m_city_list.add(curCityList);
							curCityList = null;
						}
						m_channel_list.add(curCityItemList);
						curCityItemList = null;
					} else if (parser.getName().equals("City")) {
						curCityItemList.add(curItemList);
						curItemList = null;
					} else if (parser.getName().equals("Channel")) {
						curItemList.add(curChannelItem);
						curChannelItem = null;
					}
					break;
				}
				eventType = parser.next();
			}
			inputStream.close();
			return true;
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		return false;
	}

	public void updatePreferences(int type) {
		synchronized(this){		
			if (type == RADIO_DATA_SAVE) {
												
				//save current channel info
				saveCurChanelInfo();
				//save mChannelList_fm mChannelList_am mChannelList_heart.		
				SharedPreferences.Editor editor = settings.edit(); 
				Gson gson = new Gson();
				editor.putString("fmList", gson.toJson(mChannelList_fm));
				editor.putString("amList", gson.toJson(mChannelList_am));
				editor.putString("collectiList", gson.toJson(mChannelList_heart));
				editor.commit(); // commit to save.
			
			} else if (type == RADIO_DATA_READ) {
				if (settings.contains("fmtype")) {
					//read current freq info.
					radioType = settings.getInt("fmtype", 0);
					functionId = settings.getInt("funId", 0);
					mVolume = settings.getInt("mvolume", mVolume);
					curFreq = settings.getInt("mfreq", 0);
					
					//read freq info to three channel list of three diffrent radio type.
					Gson gson = new Gson();
					Type objtype = new TypeToken<List<ChannelItem>>(){}.getType();
					mChannelList_fm = gson.fromJson(settings.getString("fmList",null),objtype);
					mChannelList_am = gson.fromJson(settings.getString("amList",null),objtype);
					mChannelList_heart = gson.fromJson(settings.getString("collectiList",null),objtype);
					
				} else { // read default data
					radioReadXML();
					importChannelList(0, 0, true);
					if(DEBUG)Log.d(TAG,"read channel info,no saved info ,import channel list.");
				}
			}
		}

	}

	public void saveCurChanelInfo(){
		Log.d(TAG,"saveCurChanelInfo curFreq= "+curFreq);
		SharedPreferences.Editor editor = settings.edit(); 
		editor.putInt("fmtype", radioType); 
		editor.putInt("funId", functionId);
		editor.putInt("mvolume", mVolume);
		editor.putInt("mfreq", curFreq);
		editor.commit(); // commit to save.
	}
	public boolean importChannelList(int provinceId, int cityId, boolean refresh) {

		//Log.v(TAG,".provinceId= "+provinceId+"cityId="+cityId);
		if (DEBUG)
			Log.v(TAG, "<myu>importChannelList has been into");
		if (m_channel_list == null || m_channel_list.size() <= provinceId
				|| m_channel_list.get(provinceId).size() <= cityId) {
			return false;
		}
		
		//allocate freq info to three channelList.
		if(mChannelList_fm == null){mChannelList_fm = new ArrayList<ChannelItem>();}else{mChannelList_fm.clear();}
		if(mChannelList_am == null){mChannelList_am = new ArrayList<ChannelItem>();}else{mChannelList_am.clear();}
		if(mChannelList_heart == null){mChannelList_heart = new ArrayList<ChannelItem>();}else{mChannelList_heart.clear();}
		if(m_channel_list!=null && m_channel_list.size()>0){
			for (ChannelItem item : m_channel_list.get(provinceId).get(cityId)) {
				if (item.freq.contains(".")) {
					mChannelList_fm.add(item);
				} else {
					mChannelList_am.add(item);
					if(DEBUG)Log.v(TAG,"3333333mChannelList.size() = "+mChannelList_am.size());
				}
			}
		}
		
		//set current freq.
		if (RADIO_FM == radioType) {
			if(mChannelList_fm.size()>0 && mChannelList_fm.get(0).freq!="")
			{
				curFreq = Integer.parseInt(mChannelList_fm.get(0).freq
						.replaceAll("\\.", "")) * 10;				
			}

		} else if (RADIO_AM == radioType) {
			if(mChannelList_am.size()>0 && mChannelList_am.get(0).freq!="")
			{
				curFreq = Integer
						.parseInt(mChannelList_am.get(0).freq);				
			}

		} else if(RADIO_COLLECT == radioType){

			if(mChannelList_heart.size()>0 && mChannelList_heart.get(0).freq!="")
			{
				if(mChannelList_heart.get(0).freq.contains(".")){
					curFreq = Integer.parseInt(mChannelList_heart.get(0).freq
							.replaceAll("\\.", "")) * 10;
				}else{
					if(mChannelList_heart.get(0).freq!="")
					{
						curFreq = Integer
								.parseInt(mChannelList_heart.get(0).freq);						
					}

				}				
			}			
		}
		updatePreferences(RADIO_DATA_SAVE);
		//notify to update ui.
		if (refresh) {
			if(mStatusListener!=null){
				mStatusListener.onStatusChange(TURN_FMAM_UI);
			}
		}
		return true;
	}
	
    private BroadcastReceiver myReceiver = new BroadcastReceiver() {

        @Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if(DEBUG)Log.d("Receiver","onReceive action: "+action);
			if(action.equals("CONNECT_SUCCESSFUL")){
				//RadioActivity connected to this service.this situation, mStatusListener will not be null.
				mStatusListener.onStatusChange(INFLATE_CHANNEL_LIST);
			}else if(action.equals("com.bonovo.RADIO_FM/AM")){
				if(DEBUG)Log.d(TAG,"com.bonovo.RADIO_FM/AM radioType="+radioType);
				Message msg = Message.obtain();
				msg.what = TURN_FMAM_UI;
				if(radioType!=RADIO_FM){
					setRadioType(RADIO_FM);
					msg.obj = "fm";
				}else if(radioType!=RADIO_AM){
					setRadioType(RADIO_AM);
					msg.obj = "am";
				}			
				mRadioplayerHandler.sendMessage(msg);
			}else if(action.equals("com.bonovo.RADIO_CHANEL_DECREASE")){
				if(DEBUG)Log.d(TAG,"com.bonovo.RADIO_CHANEL_DECREASE ");
				if(mServiceBinder!=null){
					mServiceBinder.preFreq();
				}

			}else if(action.equals("com.bonovo.RADIO_CHANEL_INCREASE")){
				if(DEBUG)Log.d(TAG,"com.bonovo.RADIO_CHANEL_INCREASE ");
				if(mServiceBinder!=null){
					mServiceBinder.nextFreq();
				}

			}else if (action.equals("android.intent.action.wakeup")){
				if(DEBUG) Log.v(TAG, "BonovoRadio is wakeup-->mIsRadioPlaying="+mIsRadioPlaying);
				if(!mIsRadioPlaying){
					muteOrRecover(false);
					setFreq(curFreq);
				}	
			}else if(action.equals("com.bonovo.radio.widget.REQUEST_UPDATE")){
				updateWidget();
			}else if(action.equals(CHANGE_ACTION_FORWARD)){
			   SwitchChannelListFreqForWard();
			}else if(action.equals(CHANGE_ACTION_BACKWARD)){
			   SwitchChannelListBackWard();
			}
		}
    };

    private void SwitchChannelListFreqForWard(){
		int  curFreq = getCurrentFreq();
		int curFreqId = isInCurChannelList(curFreq);
		String strFreq = String.valueOf(curFreq);
		if (curFreq<0||curFreqId == getChannelList().size()-1){
			strFreq = getChannelList().get(0).freq;
		}else if (curFreqId>=0&&curFreqId<getChannelList().size()-1){
			strFreq = getChannelList().get(curFreqId+1).freq;
		}
		if(strFreq.contains(".")){
			curFreq = Integer.parseInt(strFreq.replaceAll("\\.",""))*10;
		}else{
			curFreq = Integer.parseInt(strFreq);
		}
		setFreq(curFreq);
	}


	public void SwitchChannelListBackWard(){
		int  curFreq = getCurrentFreq();
		int curFreqId = isInCurChannelList(curFreq);
		String strFreq = String.valueOf(curFreq);
		if (curFreqId<0){
			strFreq = getChannelList().get(0).freq;
		}else if (curFreqId == 0){
			strFreq = getChannelList().get(getChannelList().size()-1).freq;
		}else if (curFreqId>0&&curFreqId<getChannelList().size()){
			strFreq = getChannelList().get(curFreqId-1).freq;
		}
		if(strFreq.contains(".")){
			curFreq = Integer.parseInt(strFreq.replaceAll("\\.",""))*10;
		}else{
			curFreq = Integer.parseInt(strFreq);
		}
		setFreq(curFreq);
	}

	private int isInCurChannelList(int freq){
		if(getChannelList().size()>0){
			for(int i=0;i<getChannelList().size();i++){
				ChannelItem item =  getChannelList().get(i);
				int itemFreq;
				if(item.freq.contains(".")){
					itemFreq = Integer.parseInt(item.freq.replaceAll("\\.",""))*10;
				}else{
					itemFreq = Integer.parseInt(item.freq);
				}
				if(freq == itemFreq){
					return i;
				}
			}
			return -1;
		}else{
			return -1;
		}
	}

	private void updateWidget(){
		Log.d(TAG,"---------------updateWidget");
		Intent updateWidget = new Intent("com.bonovo.radio.widget.UPDATE");
		updateWidget.putExtra("freq",curFreq);
		updateWidget.putExtra("playing",mIsRadioPlaying);
		sendBroadcast(updateWidget);
	}
    private IntentFilter getIntentFilter(){
        IntentFilter myIntentFilter = new IntentFilter("android.intent.action.BONOVO_SLEEP_KEY");
        //myIntentFilter.setPriority(Integer.MAX_VALUE);      	
		myIntentFilter.addAction("com.bonovo.radio.widget.REQUEST_UPDATE");//sended by widget to request update.
		myIntentFilter.addAction("CONNECT_SUCCESSFUL");//sended when RadioActivity has been connect to this service.
        myIntentFilter.addAction("android.intent.action.wakeup");//sended to ensure radio is playing when RadioActivity display.
		myIntentFilter.addAction("com.bonovo.RADIO_FM/AM");//key control,fm am.
		myIntentFilter.addAction("com.bonovo.RADIO_CHANEL_DECREASE");//key control,next freq.
		myIntentFilter.addAction("com.bonovo.RADIO_CHANEL_INCREASE");//key control,pre freq.
		myIntentFilter.addAction("Radio.Media_Broadcast_Next");//key contral to play next channel
		myIntentFilter.addAction("Radio.Media_Broadcast_Last");//key contral to play last channel
		myIntentFilter.addAction(CHANGE_ACTION_BACKWARD);//key control freq backward
		myIntentFilter.addAction(CHANGE_ACTION_FORWARD);//key control freq forward
		return myIntentFilter;
	}

	@Override
	public void clearAllContent() {
		// TODO Auto-generated method stub
		
	}
	
	/*
	 * Read And Set Radio Model -->china japan europe
	 */
	public void readAndSetModelInfo(){
		SharedPreferences modelpre = getSharedPreferences(
				"CHECKED", 0);
		RADIO_MODEL = modelpre.getInt("radioModel", 0);
		if(RADIO_MODEL == JAPAN_MODEL){
			jniSetModel(JAPAN_MODEL);
			FM_HIGH_FREQ = 9000;
			FM_LOW_FREQ = 7600;
			AM_HIGH_FREQ = 1620;
			AM_LOW_FREQ = 522;
		}else if (RADIO_MODEL == EUR_MODEL) {
			jniSetModel(EUR_MODEL);
			FM_HIGH_FREQ = 10800;
			FM_LOW_FREQ = 8700;
			AM_HIGH_FREQ = 1620;
			AM_LOW_FREQ = 522;
		}else {
			jniSetModel(CHINA_MODEL);
			FM_HIGH_FREQ = 10800;
			FM_LOW_FREQ = 8700;
			AM_HIGH_FREQ = 1602;
			AM_LOW_FREQ = 531;
		}
	}
}
