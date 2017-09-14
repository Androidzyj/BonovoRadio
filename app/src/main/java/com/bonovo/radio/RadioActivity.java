package com.bonovo.radio;

import com.bonovo.radio.RadioInterface.RadioStatusChangeListener;
import com.bonovo.radio.RadioService.ChannelItem;
import java.util.List;

import android.media.AudioManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.GridLayout;
import android.view.LayoutInflater;
import android.text.TextUtils;

@SuppressLint("HandlerLeak")
public class RadioActivity extends Activity implements 
		ServiceConnection, OnClickListener, OnSeekBarChangeListener ,OnLongClickListener{

	/**when switch fm and am,record last state*/
	private int mLastFreq = -1;
	private int mLastFreqChannelId = 0;
	private int mLastAm = -1;
	private int mLastAmChannelId = 0;
	private int mLastCollectId = 0;
	private int ROW_NUM = 4;
	private LayoutInflater mInflater;

	// private static final int AUTOSEARCH_COMPLETE = 2;
	// private static final int DISMISS_VOLUME_DIALOG = 3;
	private static final int DISMISS_INPUT_DIALOG = 4;
	private static final int VOLUME_DELAY_TIME = 3000;
	
	private static int FOCUS_BUTTON_ID = 0;
	private static int HEART_STATIC_FLAG = 0;

	private static final int DIALOG_EDIT = 3;
	private static final int DIALOG_INPUTFREQ_FM = 4;
	private static final int DIALOG_INPUTFREQ_AM = 5;

	private static boolean mDown = false;			//keyEvent flag

	private AlertDialog mVolCtrlDialog;
	private AlertDialog mInputDialog;
	private SeekBar mVolumeSeekBar;
	private AlertDialog warnDialog;
	private CheckBox cBox;
	private AlertDialog di;
	private View checkbox;

	private static final boolean DEBUG = true;
	private static int  flag = 0;				
	private static int IS_AUTO_NOW = 1;
	private static int IS_STEP_NOW = 2;
	private final static String TAG = "RadioActivity";
	private RadioService radioService = null;

	private GridLayout mChannelLayout;

	private ImageButton mMiddleButtonForward;
	private ImageButton mMiddleButtonBackward;
	private ImageButton mTitleButtonClose;
	private ImageButton mTitleButtonHome;

	private Button mMiddleButtonFine;
	private Button mMiddleButtonStep;
	private Button mMiddleButtonAuto;
	private Button mBottomButtonImport;
	private Button mBottomButtonVolume;
	private Button mButtonButtonFm1;
	private Button mButtonButtonFm2;
	private Button mButtonButtonAm;
	private Button mButtonButtonSetting;
	private Button mButtonButtoncollect;
	private Button mButtonButtonHeart;
	private Button mButtonClear;
	private Button mButtonAddHeart;

	private TextView mRadioType;
	private TextView mRadiohz;
	private TextView mChannelDetails;
	private ChannelItem mCanAddToCollectItem;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		mInflater = getLayoutInflater();
		
		setupview();
		Intent serviceIntent = new Intent();
		serviceIntent.setClassName("com.bonovo.radio", "com.bonovo.radio.RadioService");
		this.startService(serviceIntent);
		bindService(serviceIntent, this, 0);
		registerReceiver(myBroadcastReveiver, getIntentFilter());

		if(DEBUG) Log.d(TAG, "++++++onCreate()  ");
	}
	
	@SuppressWarnings("deprecation")
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		if(radioService!=null)radioService.isLive = false;
		this.unregisterReceiver(myBroadcastReveiver);
		unbindService(this);
		AudioManager audioManager = (AudioManager) this.getSystemService(AUDIO_SERVICE);
		ComponentName mRemoteControlClientReceiverComponent;
		mRemoteControlClientReceiverComponent = new ComponentName(
		                getPackageName(), MediaButtonIntentReceiver.class.getName());
		audioManager.unregisterMediaButtonEventReceiver(mRemoteControlClientReceiverComponent);
		android.os.Process.killProcess(android.os.Process.myPid());
		if(DEBUG) Log.d(TAG, "++++++onDestroy()");
		super.onDestroy();
	}


	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		AudioManager audioManager = (AudioManager) this.getSystemService(AUDIO_SERVICE);
		ComponentName mRemoteControlClientReceiverComponent;
		mRemoteControlClientReceiverComponent = new ComponentName(
				getPackageName(), MediaButtonIntentReceiver.class.getName());
		audioManager.unregisterMediaButtonEventReceiver(mRemoteControlClientReceiverComponent);
		audioManager.registerMediaButtonEventReceiver(mRemoteControlClientReceiverComponent);
		if(DEBUG) Log.d(TAG, "++++++onResume unregister and then register");

		if(DEBUG) Log.v("RadioService","send broadcast in RadioActivity to tell service request auto focus");
        Intent broadCastIntent = new Intent("android.intent.action.wakeup");
	    sendBroadcast(broadCastIntent);
			
	}

	private int editChannelId = -1;

	public boolean onLongClick(View v) {
		int viewId = v.getId();
		switch (viewId) {
		case R.id.btnclear:
			/*******init the checkbox layout******/
			LayoutInflater lauoutInflater = LayoutInflater.from(RadioActivity.this);
			checkbox = lauoutInflater.inflate(R.layout.checkbox, null);
			cBox = (CheckBox) checkbox.findViewById(R.id.check);
			
			SharedPreferences pre = getSharedPreferences("checkvalue", MODE_PRIVATE);
			String value = pre.getString("ischeck", "");
			/*****if check the NO_Remind,the Dialog no show next time******/
			if (value.endsWith("1")) {
				createDialog().dismiss();				
				mChannelLayout.removeAllViews();
				radioService.getChannelList().clear();
				//save current channel info.
				radioService.updatePreferences(RadioService.RADIO_DATA_SAVE);
			} else {
				createDialog().show();
			}
			break;
		default:
			break;
		}
		return true;
	}

	private void setupview() {
		mChannelLayout = (GridLayout)findViewById(R.id.channel_layout);
		Log.d("mChannelLayout","mChannelLayout "+(mChannelLayout == null));
		mMiddleButtonBackward = (ImageButton) findViewById(R.id.btnbackward);
		Log.d("mChannelLayout","mMiddleButtonBackward "+(mMiddleButtonBackward == null));
		mMiddleButtonBackward.setOnClickListener(this);
		mMiddleButtonForward = (ImageButton) findViewById(R.id.btnforward);
		mMiddleButtonForward.setOnClickListener(this);
		mTitleButtonClose = (ImageButton) findViewById(R.id.btnclose);
		mTitleButtonClose.setOnClickListener(this);
		//mTitleButtonHome = (ImageButton) findViewById(R.id.btnhome);
		//mTitleButtonHome.setOnClickListener(this);

		mButtonButtonFm1 = (Button) findViewById(R.id.btnfm1);
		mButtonButtonFm1.setOnClickListener(this);
		//mButtonButtonFm2 = (Button) findViewById(R.id.btnfm2); 
		//mButtonButtonFm2.setOnClickListener(this);
		mButtonButtonAm = (Button) findViewById(R.id.btnam);
		mButtonButtonAm.setOnClickListener(this);
		//mBottomButtonVolume = (Button) findViewById(R.id.btnvolume);
		//mBottomButtonVolume.setOnClickListener(this);
		//mBottomButtonImport = (Button) findViewById(R.id.btnimport);
		//mBottomButtonImport.setOnClickListener(this);
		mButtonButtonSetting = (Button)findViewById(R.id.btnsetting);
		mButtonButtonSetting.setOnClickListener(this);
		mMiddleButtonFine = (Button) findViewById(R.id.btnfine);
		mMiddleButtonFine.setOnClickListener(this);
		mMiddleButtonStep = (Button) findViewById(R.id.btnstep);
		mMiddleButtonStep.setOnClickListener(this);
		mMiddleButtonAuto = (Button) findViewById(R.id.btnauto);
		mMiddleButtonAuto.setOnClickListener(this);		
		mButtonButtoncollect = (Button)findViewById(R.id.collect);
		mButtonButtoncollect.setOnClickListener(this);
		mButtonButtonHeart = (Button) findViewById(R.id.btnsave);
		mButtonButtonHeart.setOnClickListener(this);
		mButtonClear = (Button) findViewById(R.id.btnclear);
		mButtonClear.setOnClickListener(this);
		mButtonClear.setOnLongClickListener(this);
		mButtonAddHeart = (Button) findViewById(R.id.btncollect);
		mButtonAddHeart.setOnClickListener(this);

		mRadioType = ((TextView) findViewById(R.id.radiotype)); 
		mRadiohz = ((TextView) findViewById(R.id.radiohz));
		mChannelDetails = ((TextView) findViewById(R.id.channeldetails));
	}

	private RadioStatusChangeListener mRadioStatusListener = new RadioStatusChangeListener() {

		@Override
		public void onStatusChange(int type) {
			// TODO Auto-generated method stub
			switch(type){
				case RadioService.INFLATE_CHANNEL_LIST:
					if(DEBUG)Log.d(TAG,"INFLATE_CHANNEL_LIST ");	
					inflateChannelList(radioService.getChannelList());	
					Log.d(TAG,"setChannelItemChecked 22");
					setChannelItemChecked();
					break;
				case RadioService.UPDATE_DETAIL_FREQ:
					updateFreqView();
					break;
				case RadioService.AUTO_SEARCH_COMPLETE://fine succeed or auto search complete
					//checked item 0
					updateFreqView();
					setChannelItemChecked();
					break;
				case RadioService.TURN_FMAM_UI://cur
					//update ui,include freq view ,title detail,fm am,checked channel item.
					Log.d(TAG,"case turnFmAMUI getRadioType "+radioService.getRadioType());
					turnFmAMUI(radioService.getRadioType());
					break;

				case RadioService.NEXT_CHANNEL:
					int childCount = mChannelLayout.getChildCount();
					if(childCount>0){
						int curFreq = radioService.getCurrentFreq();
						int curChannelId = isInCurChannelList(curFreq);
						String strFreq = String.valueOf(curFreq);
						if(curChannelId<0 || curChannelId==radioService.getChannelList().size()-1){
							strFreq =  radioService.getChannelList().get(0).freq;			
						}else if(curChannelId >=0 && curChannelId<childCount){
							strFreq = radioService.getChannelList().get(curChannelId+1).freq;
						}
						if(strFreq.contains(".")){
							curFreq = Integer.parseInt(strFreq.replaceAll("\\.",""))*10;
						}else{
							curFreq = Integer.parseInt(strFreq);
						}
						radioService.setFreq(curFreq);
						if(DEBUG)Log.d(TAG,"NEXT_CHANNEL childCount= "+childCount+"; curChannelId= "+curChannelId+"; next strFreq= "+strFreq);
						updateFreqView();
						setChannelItemChecked();
					}					
					break;			
				case RadioService.LAST_CHANNEL:
					int childCount1 = mChannelLayout.getChildCount();
					if(childCount1>0){
						int curFreq = radioService.getCurrentFreq();
						int curChannelId = isInCurChannelList(curFreq);
						String strFreq = String.valueOf(curFreq);
						if(curChannelId<0){
							strFreq = radioService.getChannelList().get(0).freq;			
						}else if(curChannelId==0){
							strFreq = radioService.getChannelList().get(radioService.getChannelList().size()-1).freq;
						}else if(curChannelId >0 && curChannelId<childCount1){
							strFreq = radioService.getChannelList().get(curChannelId-1).freq;
						}
						if(strFreq.contains(".")){
							curFreq = Integer.parseInt(strFreq.replaceAll("\\.",""))*10;
						}else{
							curFreq = Integer.parseInt(strFreq);
						}
						radioService.setFreq(curFreq);
						if(DEBUG)Log.d(TAG,"LAST_CHANNEL childCount1= "+childCount1+"; curChannelId= "+curChannelId+"; next strFreq= "+strFreq);
						updateFreqView();
						setChannelItemChecked();
					}					
					break;
			}
		}

		@Override
		public void onStatusChange(Message msg) {
			switch(msg.what){
				case  RadioService.ADD_CHANNEL_ITEM:
					if(msg.obj!=null)
						addItemView(mChannelLayout,(ChannelItem)msg.obj);
					break;
				case RadioService.TURN_FMAM_UI://key contral
					if(!radioService.mIsSearchThreadRunning){
						if("fm".equals((String)msg.obj)){
							radioService.turnFmAm(RadioService.RADIO_FM);
							if(mLastFreq<0){
								if(radioService.getChannelList().size()>0){
									String freq = radioService.getChannelList().get(0).freq;
									if(freq.contains(".")){
										freq = freq.replaceAll("\\.","");
										Log.d(TAG,"after replace freq = "+freq);
										mLastFreq = Integer.parseInt(freq)*10;
									}else{
										mLastFreq = Integer.parseInt(freq); 					
									}
								}else{
									mLastFreq = RadioService.FM_LOW_FREQ;				
								}
							}				
							radioService.setFreq(mLastFreq);						
							turnFmAMUI(RadioService.RADIO_FM);
						}else{
							radioService.turnFmAm(RadioService.RADIO_AM);
							if(mLastAm<0){
								if(radioService.getChannelList().size()>0){
									String freq = radioService.getChannelList().get(0).freq;
									mLastAm = Integer.parseInt(freq);	
								}else{
									mLastAm = RadioService.AM_LOW_FREQ;
								}
								
							}
							radioService.setFreq(mLastAm);	
							turnFmAMUI(RadioService.RADIO_AM);					
						}
					}else{
						Toast.makeText(getApplicationContext(), R.string.searching, Toast.LENGTH_SHORT).show();
					}
					
					break;
			}
		}
	};

		
	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		// TODO Auto-generated method stub
		if (DEBUG)
			Log.v(TAG, "RadioService is connected");
		radioService = ((RadioService.ServiceBinder) service).getService();
		radioService.isLive = true;
		radioService.registStatusListener(mRadioStatusListener);

		//send a broadcast to service,tell it connection is successful and then can inflate channelList.
		Intent inflateIntent = new Intent("CONNECT_SUCCESSFUL");
		sendBroadcast(inflateIntent);
		
		//button back and pre
		if (radioService.getFunctionId() == 0) {
			mMiddleButtonBackward
					.setBackgroundResource(R.drawable.btnfinebackward);
			mMiddleButtonForward
					.setBackgroundResource(R.drawable.btnfineforward);
		} else if (radioService.getFunctionId() == 1) {
			mMiddleButtonBackward
					.setBackgroundResource(R.drawable.btnstepbackward);
			mMiddleButtonForward
					.setBackgroundResource(R.drawable.btnstepforward);
		}
		
		//freq number 
        updateFreqView();

		// radip type
		if (RadioService.RADIO_FM == radioService.getRadioType()) {
			((TextView) findViewById(R.id.radiotype)).setText("FM"); 
			((TextView) findViewById(R.id.radiohz)).setText("MHz");
			mButtonButtonFm1.setSelected(true);
			//mButtonButtonFm2.setSelected(false);
			mButtonButtonAm.setSelected(false);
			mButtonButtoncollect.setSelected(false);
		} else if (RadioService.RADIO_COLLECT == radioService.getRadioType()) {
			((TextView) findViewById(R.id.radiotype)).setText(getResources().getString(R.string.collect)); 
			((TextView) findViewById(R.id.radiohz)).setText(" ");
			mButtonButtonFm1.setSelected(false);
			//mButtonButtonFm2.setSelected(true);
			mButtonButtonAm.setSelected(false);
			mButtonButtoncollect.setSelected(true);
		} else if (RadioService.RADIO_AM == radioService.getRadioType()) {
			((TextView) findViewById(R.id.radiotype)).setText("AM");
			((TextView) findViewById(R.id.radiohz)).setText("KHz");
			mButtonButtonFm1.setSelected(false);
			//mButtonButtonFm2.setSelected(false);
			mButtonButtonAm.setSelected(true);
			mButtonButtoncollect.setSelected(false);
		}

	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		// TODO Auto-generated method stub
		if(DEBUG) Log.v(TAG, "RadioService is disconnected");
		radioService.unRegistStatusListener();
		finish();
	}

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		int viewId = v.getId();
		switch (viewId) {
			case R.id.btnclose:
				if (DEBUG)Log.d(TAG, "btnclose has worked");
				Intent radioServiceIntent = new Intent(RadioActivity.this, RadioService.class);
				radioService.stopService(radioServiceIntent);
				finish();
				break;
			case R.id.btncollect://horizontal heart button,add current freq to heart_list
				addToCollect();
				if(radioService.getRadioType()==RadioService.RADIO_COLLECT && mCanAddToCollectItem!=null){
					addItemView(mChannelLayout,mCanAddToCollectItem);
				}
				break;
			case R.id.btnfine:
				if (DEBUG)Log.d(TAG, "btnfine has worked");
				radioService.setFunctionId(0);
				radioService.saveCurChanelInfo();
				mMiddleButtonBackward.setBackgroundResource(R.drawable.btnfinebackward);
				mMiddleButtonForward.setBackgroundResource(R.drawable.btnfineforward);
				break;
			case R.id.btnstep:
				if (DEBUG)Log.d(TAG, "btnstep has worked");
				radioService.setFunctionId(1);
				radioService.saveCurChanelInfo();
				mMiddleButtonBackward.setBackgroundResource(R.drawable.btnstepbackward);
				mMiddleButtonForward.setBackgroundResource(R.drawable.btnstepforward);
				break;
			case R.id.btnauto:
						if (DEBUG)
							Log.d(TAG, "btnauto has worked");
						radioService.STEP_OR_AUTO = IS_AUTO_NOW;
						new AlertDialog.Builder(RadioActivity.this)
								.setTitle(R.string.remind)
								.setCancelable(true)
								.setMessage(R.string.describe)
								.setPositiveButton(R.string.ok,
										new DialogInterface.OnClickListener() {
		
											@Override
											public void onClick(DialogInterface dialog,
													int which) {
												// TODO Auto-generated method stub
												if(radioService.getRadioType()==RadioService.RADIO_FM){
													mLastFreqChannelId = 0;
													mChannelLayout.removeAllViews();
													radioService.onAutoSearch();
												}else if(radioService.getRadioType()==RadioService.RADIO_AM){
													mLastAmChannelId = 0;
													mChannelLayout.removeAllViews();
													radioService.onAutoSearch();
												}else{
													Toast.makeText(getApplicationContext(), R.string.switch_to_fmam_first, Toast.LENGTH_SHORT).show();
												}	
											}
										})
								.setNegativeButton(R.string.cancel,
										new DialogInterface.OnClickListener() {
		
											@Override
											public void onClick(DialogInterface dialog,
													int which) {
												// TODO Auto-generated method stub
		
											}
										}).create().show();
						break;
			case R.id.btnsave:
				//if channel list has contained the freq,do nothing.
				boolean saved = false;
				String curfreq = "";
				if(radioService.getRadioType() == RadioService.RADIO_FM){
					// make the current freq type int to String
					curfreq = Double.toString(radioService.getCurrentFreq()/100.0);
				}else if(radioService.getRadioType() == RadioService.RADIO_AM){
					// make the current freq type int to String
					curfreq = Integer.toString(radioService.getCurrentFreq());
				}else if(radioService.getRadioType() == RadioService.RADIO_COLLECT){
					//when the type is RADIO_COLLECT ,the curfreq can be FM also can be AM too
					if(radioService.getCurrentFreq() < RadioService.FM_LOW_FREQ  || radioService.getCurrentFreq() > RadioService.FM_HIGH_FREQ){
						// make the current freq type int to String
						curfreq = Integer.toString(radioService.getCurrentFreq());
					}else if(radioService.getCurrentFreq() < RadioService.AM_LOW_FREQ || radioService.getCurrentFreq() > RadioService.AM_HIGH_FREQ){
						curfreq = Double.toString(radioService.getCurrentFreq()/100.0);
					}
				}
				for(ChannelItem item: radioService.getChannelList()){
					if(curfreq.equals(item.freq)){
						saved = true;
						break ;
					}
				}
				if(saved){
					Toast.makeText(getApplicationContext(), R.string.samefreq, Toast.LENGTH_SHORT).show();
				}else{
					ChannelItem newItem = new ChannelItem();
					newItem.freq = curfreq;
					radioService.getChannelList().add(newItem);		
					radioService.updatePreferences(RadioService.RADIO_DATA_SAVE);
					inflateChannelList(radioService.getChannelList());
					setChannelItemChecked();
				}
						
				break;
			case R.id.btnclear:
				new AlertDialog.Builder(RadioActivity.this)
				.setTitle(R.string.remind)
				.setCancelable(true)
				.setMessage(R.string.clear_single_channel)
				.setPositiveButton(R.string.ok,
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								// TODO Auto-generated method stub
								int removeId = isInCurChannelList(radioService.getCurrentFreq());
								if(removeId>=0){
									radioService.getChannelList().remove(removeId);
									inflateChannelList(radioService.getChannelList());						
									radioService.updatePreferences(RadioService.RADIO_DATA_SAVE);
								}
							}
						})
				.setNegativeButton(R.string.cancel,
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								// TODO Auto-generated method stub

							}
						}).create().show();
				break;
			case R.id.btnfm1:
				if (DEBUG)Log.d(TAG, "btnfm1 has worked");
				if(!radioService.mIsSearchThreadRunning){
					radioService.turnFmAm(RadioService.RADIO_FM);
					if(mLastFreq<0){
						if(radioService.getChannelList().size()>0){
							String freq = radioService.getChannelList().get(0).freq;
							if(freq.contains(".")){
								freq = freq.replaceAll("\\.","");
								Log.d(TAG,"after replace freq = "+freq);
								mLastFreq = Integer.parseInt(freq)*10;
							}else{
								mLastFreq = Integer.parseInt(freq); 					
							}
						}else{
							mLastFreq = RadioService.FM_LOW_FREQ;				
						}
					}				
					radioService.setFreq(mLastFreq);					
					turnFmAMUI(RadioService.RADIO_FM);
					//save current channel info.
					radioService.saveCurChanelInfo();
				}else{
					Toast.makeText(getApplicationContext(), R.string.searching, Toast.LENGTH_SHORT).show();
				}
				break;


			case R.id.btnam:
				if (DEBUG)Log.d(TAG, "btnam has worked");
				if(!radioService.mIsSearchThreadRunning){
					radioService.turnFmAm(RadioService.RADIO_AM);
					if(mLastAm<0){
						if(radioService.getChannelList().size()>0){
							String freq = radioService.getChannelList().get(0).freq;
							mLastAm = Integer.parseInt(freq);	
						}else{
							mLastAm = RadioService.AM_LOW_FREQ;
						}
						
					}
					radioService.setFreq(mLastAm);						
					turnFmAMUI(RadioService.RADIO_AM);	
					//save current channel info.
					radioService.saveCurChanelInfo();
				}else {
					Toast.makeText(getApplicationContext(), R.string.searching, Toast.LENGTH_SHORT).show();
				}
				break;
			case R.id.collect:
				if (DEBUG)Log.d(TAG, "btncollect has worked");
				if(!radioService.mIsSearchThreadRunning){

					radioService.turnFmAm(RadioService.RADIO_COLLECT);
					
					mRadioType.setText(RadioActivity.this.getResources().getString(R.string.collect)); 
					mRadiohz.setText(" ");
					mButtonButtonFm1.setSelected(false);
					mButtonButtonAm.setSelected(false);
					mButtonButtoncollect.setSelected(true);
					inflateChannelList(radioService.getChannelList());//inflate collect channel list.
					setChannelItemChecked();
					/*//update freq number view
					if(radioService.getChannelList().size()>0){
						
						String freq = radioService.getChannelList().get(0).freq;
						if(freq.contains(".")){
							freq = freq.replaceAll("\\.","");
							Log.d(TAG,"after replace freq = "+freq);
							radioService.setFreq(Integer.parseInt(freq)*10);
						}else{
							radioService.setFreq(Integer.parseInt(freq));						
						}
						updateFreqView();
						radioService.setCurChannelId(0);
						Log.d(TAG,"setChannelItemChecked 33");
						setChannelItemChecked();
					}*/
				}else {
					Toast.makeText(getApplicationContext(), R.string.searching, Toast.LENGTH_SHORT).show();
				}
				
				break;

			case R.id.btnsetting:
				if (DEBUG)Log.d(TAG, "btnsrtting has worked");
				Intent setting = new Intent(RadioActivity.this, IntentActivity.class);
				RadioActivity.this.startActivity(setting);
				break;
			case R.id.btnforward:
				if(radioService.getFunctionId()==0){//fine
					radioService.fineRight(radioService.getCurrentFreq());
				}else if(radioService.getFunctionId()==1){//step
					radioService.stepRight(radioService.getCurrentFreq());
				}
				break;
			case R.id.btnbackward:
				if(radioService.getFunctionId()==0){//fine
					radioService.fineLeft(radioService.getCurrentFreq());
				}else if(radioService.getFunctionId()==1){//step
					radioService.stepLeft(radioService.getCurrentFreq());
				}				
				break;	
			}
	}


	/**
	 * horizontal heart button,add or delete current freq to heart_list
	 */
	private void addToCollect(){
		mCanAddToCollectItem = null;
		boolean alreadyCollected = isInCollectList(radioService.getCurrentFreq());
		if(!alreadyCollected){
			if(!(radioService.getRadioType()==RadioService.RADIO_COLLECT)){
				int inCurrentListId = isInCurChannelList(radioService.getCurrentFreq());
				if(inCurrentListId<0){
					mCanAddToCollectItem = new ChannelItem();
					mCanAddToCollectItem.freq = freqToString(radioService.getCurrentFreq());
					mCanAddToCollectItem.name = "";
					mCanAddToCollectItem.abridge = "";
				}else{
					mCanAddToCollectItem = radioService.getChannelItem(inCurrentListId);
				}
			}else{
				mCanAddToCollectItem = new ChannelItem();
				mCanAddToCollectItem.freq = freqToString(radioService.getCurrentFreq());
				mCanAddToCollectItem.name = "";
				mCanAddToCollectItem.abridge = "";
			}
			if(mCanAddToCollectItem!=null){
				radioService.getChannelList("heart").add(mCanAddToCollectItem);
				mButtonAddHeart.setBackground(getResources().getDrawable(R.drawable.collect_d));
				radioService.updatePreferences(RadioService.RADIO_DATA_SAVE);
				Toast.makeText(getApplicationContext(), R.string.is_addHeart_toast, Toast.LENGTH_SHORT).show();
			}else{
				Toast.makeText(getApplicationContext(), R.string.add_to_collect_failed, Toast.LENGTH_SHORT).show();
			}		
		}else {
			Toast.makeText(getApplicationContext(), R.string.same_addHeart_toast, Toast.LENGTH_SHORT).show();
		}
	}

	private String freqToString(int freq){
		if(DEBUG)Log.d(TAG,"freqToString before freq= "+freq);
		if (freq >= RadioService.FM_LOW_FREQ && freq <= RadioService.FM_HIGH_FREQ) {
			String strFreq = String.valueOf((freq/ 100));
			strFreq = strFreq+"."+String.valueOf((freq  / 10 % 10));
			if(DEBUG)Log.d(TAG,"after freq= "+strFreq);
			return  strFreq;
		}else{
			return String.valueOf(freq);
		}
	}

	private boolean isInCollectList(int freq){
		if(radioService.getChannelList("heart").size()>0){
			for(ChannelItem item:radioService.getChannelList("heart")){
				int itemFreq;
				if(item.freq.contains(".")){
					itemFreq = Integer.parseInt(item.freq.replaceAll("\\.",""))*10;
				}else{
					itemFreq = Integer.parseInt(item.freq);
				}
				if(freq == itemFreq){
					return true;
				}
			}
			return false;
		}else{
			return false;
		}
		
	}

	/*check whether current freq is in current channel list
		return :  -1:is not in channel list.
	*/
	private int isInCurChannelList(int freq){
		if(radioService.getChannelList().size()>0){
			for(int i=0;i<radioService.getChannelList().size();i++){
				ChannelItem item =  radioService.getChannelList().get(i);
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
	
	private void addItemView(GridLayout container,ChannelItem newItem){
		
		 GridLayout.Spec c_row,c_column;
		 GridLayout.LayoutParams params;
		 int i = 0 ;
		 int glCount = container.getChildCount();
		 for (;i<glCount;i++){
			 Button btn = (Button)container.getChildAt(i).findViewById(R.id.channelItem);
			 if (TextUtils.isEmpty(btn.getText())){
				 btn.setText(newItem.freq);
				 return;
			 }
		 }
		 if(i>=(glCount-1)){
			 int row  = glCount%ROW_NUM;
			 int col = glCount/ROW_NUM;
	
			 c_row = GridLayout.spec(row);
			 c_column = GridLayout.spec(col);
			 params = new GridLayout.LayoutParams(c_row,c_column);
			 container.addView(getItemView(newItem),params);
		 }
	 }


	private void turnFmAMUI(int type){
		updateFreqView();
		Log.d(TAG,"turnFmAMUI type= "+type);
		if(RadioService.RADIO_FM==type){
			mRadioType.setText("FM"); 
			mRadiohz.setText("MHz");
			mButtonButtonFm1.setSelected(true);
			mButtonButtonAm.setSelected(false);
			mButtonButtoncollect.setSelected(false);

		}else if(RadioService.RADIO_AM==type){
			mRadioType.setText("AM"); 
			mRadiohz.setText("KHz");
			mButtonButtonFm1.setSelected(false);
			mButtonButtonAm.setSelected(true);
			mButtonButtoncollect.setSelected(false);
		}
		inflateChannelList(radioService.getChannelList());		
		Log.d(TAG,"setChannelItemChecked 55");
		setChannelItemChecked(); 
	}
	
	private void  updateFreqView() {
		int length, temp;
		ImageView channelView;
		temp = radioService.getCurrentFreq();
		if(DEBUG)Log.d(TAG,"updateFreqView temp= "+temp);
		if(radioService.getRadioType()==RadioService.RADIO_FM){
			mLastFreq = temp;
		}else if(radioService.getRadioType()==RadioService.RADIO_AM){
			mLastAm = temp;
		}
		
		if (temp >= RadioService.FM_LOW_FREQ && temp < 10000) {
			length = 2;
			((ImageView) findViewById(R.id.number1)).setVisibility(View.GONE);
			((ImageView) findViewById(R.id.number4))
					.setVisibility(View.VISIBLE);
		} else if (temp >= 10000 && temp <= RadioService.FM_HIGH_FREQ) {
			length = 3;
			((ImageView) findViewById(R.id.number1))
					.setVisibility(View.VISIBLE);
			((ImageView) findViewById(R.id.number4))
					.setVisibility(View.VISIBLE);
		} else if (temp >= RadioService.AM_LOW_FREQ && temp < 1000) {
			length = 2;
			temp *= 10;
			((ImageView) findViewById(R.id.number1)).setVisibility(View.GONE);
			((ImageView) findViewById(R.id.number4)).setVisibility(View.GONE);
		} else if (temp > 1000 && temp <= RadioService.AM_HIGH_FREQ) {
			length = 3;
			temp *= 10;
			((ImageView) findViewById(R.id.number1))
					.setVisibility(View.VISIBLE);
			((ImageView) findViewById(R.id.number4)).setVisibility(View.GONE);
		} else {
			return;
		}
		for (int i = length; i >= 0; i--) {
			if (i == length) {
				channelView = (ImageView) findViewById(R.id.number5);
			} else if (i == length - 1) {
				channelView = (ImageView) findViewById(R.id.number3);
			} else if (i == length - 2) {
				channelView = (ImageView) findViewById(R.id.number2);
			} else if (i == length - 3) {
				channelView = (ImageView) findViewById(R.id.number1);
			} else {
				channelView = (ImageView) findViewById(R.id.number5);
			}
			temp = temp / 10;
			switch (temp % 10) {
			case 0:
				channelView.setImageResource(R.drawable.number0);
				break;
			case 1:
				channelView.setImageResource(R.drawable.number1);
				break;
			case 2:
				channelView.setImageResource(R.drawable.number2);
				break;
			case 3:
				channelView.setImageResource(R.drawable.number3);
				break;
			case 4:
				channelView.setImageResource(R.drawable.number4);
				break;
			case 5:
				channelView.setImageResource(R.drawable.number5);
				break;
			case 6:
				channelView.setImageResource(R.drawable.number6);
				break;
			case 7:
				channelView.setImageResource(R.drawable.number7);
				break;
			case 8:
				channelView.setImageResource(R.drawable.number8);
				break;
			case 9:
				channelView.setImageResource(R.drawable.number9);
				break;
			}
		}

	}

	/*update the background of Checked channel and last checked channel,freq detail text,heart background.
	*/
	void setChannelItemChecked() { 		
		if(mChannelLayout.getChildCount()>0 && radioService.getChannelList().size()>0 && radioService.getChannelList().get(0)!=null){//only when channel list is not null,we can set item checked.

			//heart background.
			if(isInCollectList(radioService.getCurrentFreq())){
				mButtonAddHeart.setBackground(getResources().getDrawable(R.drawable.collect_d));		
			}else{
				mButtonAddHeart.setBackground(getResources().getDrawable(R.drawable.btncollect_heart));		
			}

			//recover last checked item background.
			if(radioService.getRadioType()==RadioService.RADIO_FM){
				if(DEBUG)Log.d(TAG,"last fm channel id= "+mLastFreqChannelId);
				if(((Button)mChannelLayout.getChildAt(mLastFreqChannelId)!=null))
					((Button)mChannelLayout.getChildAt(mLastFreqChannelId)).setSelected(false);
			}else if(radioService.getRadioType()==RadioService.RADIO_AM){
				if(DEBUG)Log.d(TAG,"last am channel id= "+mLastAmChannelId);
				if(((Button)mChannelLayout.getChildAt(mLastAmChannelId)!=null))
					((Button)mChannelLayout.getChildAt(mLastAmChannelId)).setSelected(false);
			}else{
				if(((Button)mChannelLayout.getChildAt(mLastCollectId)!=null))
					((Button)mChannelLayout.getChildAt(mLastCollectId)).setSelected(false);
			}

			int checkedId = isInCurChannelList(radioService.getCurrentFreq());
			if(checkedId>=0){
				
				//update freq detail.
				String freqDetail = radioService.getChannelList().get(checkedId).name;
				if(freqDetail==null || "null".equals(freqDetail) || TextUtils.isEmpty(freqDetail)){
					mChannelDetails.setText("");			
				}else{
					mChannelDetails.setText(radioService.getChannelList().get(checkedId).name);			
				}
				
				//checked item background.
				((Button)mChannelLayout.getChildAt(checkedId)).setSelected(true);

				//record checked channel id.
				if(radioService.getRadioType()==RadioService.RADIO_FM){
					mLastFreqChannelId = checkedId;
				}else if(radioService.getRadioType()==RadioService.RADIO_AM){
					mLastAmChannelId = checkedId;
				}else{
					mLastCollectId = checkedId;
				}
				//save current channel info.
				radioService.saveCurChanelInfo();
			}else{
				mChannelDetails.setText("");
			}
		}	
	}

	private void inflateChannelList(List<ChannelItem> channelList){
		if(channelList!=null){
			mChannelLayout.removeAllViews();		
			GridLayout.Spec c_row,c_column;
			GridLayout.LayoutParams params;
			int column = channelList.size()/ROW_NUM;
			int k = 0;
			if((channelList.size()%ROW_NUM)>0){
				column +=1;
			}
			for(int i=0;i<column;i++){
				for (int j=0;j<ROW_NUM;j++){
					c_row = GridLayout.spec(j);
					c_column = GridLayout.spec(i);
					params = new GridLayout.LayoutParams(c_row,c_column);
					if (k<channelList.size()){
					    mChannelLayout.addView(getItemView(channelList.get(k++)),params);
					}else {
						mChannelLayout.addView(getItemView(new ChannelItem()),params);
					}
				}
			}

		}
	}

    private View getItemView(ChannelItem channel){
        Button itemView = (Button)mInflater.inflate(R.layout.item_channel,null);
		//Log.d(TAG,"getItemView channel.abridge= "+channel.abridge);
        if(channel.abridge==null || ("null").equals(channel.abridge) || TextUtils.isEmpty(channel.abridge.trim())){
            itemView.setText(channel.freq);
        }else{
			itemView.setText(channel.abridge);
        }
		if(!TextUtils.isEmpty(channel.freq)){
			itemView.setOnClickListener(mChannelItemClickListener);
			itemView.setOnLongClickListener(mChannelItemLongClick);
		}
        return itemView;
    }

	private View.OnClickListener mChannelItemClickListener = new View.OnClickListener() {
	   @Override
	   public void onClick(View v) {
			int channelId = mChannelLayout.indexOfChild(v);
			if(DEBUG)Log.d(TAG,"onClick channel id= "+channelId);
			String freq = radioService.getChannelList().get(channelId).freq ;
			if(freq.contains(".")){
				freq = freq.replaceAll("\\.","");
				radioService.jniTurnFmAm(RadioService.RADIO_FM);
				radioService.setFreq(Integer.parseInt(freq)*10);
				Intent updateWidget = new Intent("com.bonovo.radio.widget.UPDATE");
				updateWidget.putExtra("freq",Integer.parseInt(freq)*10);
				updateWidget.putExtra("playing",true);
				sendBroadcast(updateWidget);
			}else{
			    radioService.jniTurnFmAm(RadioService.RADIO_AM);
				radioService.setFreq(Integer.parseInt(freq));
				Intent updateWidget = new Intent("com.bonovo.radio.widget.UPDATE");
				updateWidget.putExtra("freq",Integer.parseInt(freq));
				updateWidget.putExtra("playing",true);
				sendBroadcast(updateWidget);
			}
			updateFreqView();	
			Log.d(TAG,"setChannelItemChecked 11");
			setChannelItemChecked();
	   }
   };


	private View.OnLongClickListener mChannelItemLongClick = new View.OnLongClickListener() {
		EditText freqEdit,shortNameEdit,fullNameEdit;
		Context context;
		int channelId;
		ChannelItem item;
        @Override
        public boolean onLongClick(View v) {
        	context = v.getContext();
        	//init dialog view.
            LayoutInflater factory = LayoutInflater.from(context);
			View channelEditView = factory.inflate(R.layout.radio_dialog_edit,null);
			freqEdit = (EditText) channelEditView.findViewById(R.id.freq_edit);
			shortNameEdit = (EditText) channelEditView.findViewById(R.id.short_name_edit);
			fullNameEdit = (EditText) channelEditView.findViewById(R.id.full_name_edit);

			//set edit channel info.
			channelId = mChannelLayout.indexOfChild(v);
			item = radioService.getChannelList().get(channelId);
			freqEdit.setText(item.freq);
			freqEdit.setSelection(freqEdit.length());
			shortNameEdit.setText(item.abridge);
			shortNameEdit.setSelection(shortNameEdit.length());
			fullNameEdit.setText(item.name);
			fullNameEdit.setSelection(fullNameEdit.length());
			if (DEBUG)
				Log.v(TAG, "freqEdit is " + item.freq + " shortNameEdit is "
						+ item.abridge + " fullNameEdit is " + item.name);
			
			new AlertDialog.Builder(RadioActivity.this)
					.setTitle(R.string.edit_channel)
					.setCancelable(false)
					.setView(channelEditView)
					.setPositiveButton(R.string.ok,okBtnClick)
					.setNegativeButton(R.string.cancel,
							new DialogInterface.OnClickListener() {
			
								@Override
								public void onClick(DialogInterface dialog,int which) {
									// TODO Auto-generated method stub
									dialog.dismiss();
								}
							}).create().show();
            return false;
		}

		DialogInterface.OnClickListener okBtnClick = new DialogInterface.OnClickListener() {
				
			@Override
			public void onClick(DialogInterface dialog,int which) {
				// TODO Auto-generated method stub
				int freq = 0;
				String freqStr, shortNameStr, fullNameStr;
				freqStr = freqEdit.getText().toString();
				shortNameStr = shortNameEdit.getText().toString();
				fullNameStr = fullNameEdit.getText().toString();
		
				//check input info. freq should not be same as the freq of one channel item which is already in the list.
				//and freq should not be null.
				//and freq should in validate range.
				float lowFM = RadioService.FM_LOW_FREQ/100;
				float hightFM = RadioService.FM_HIGH_FREQ/100;
				if(TextUtils.isEmpty(freqStr)){
					Toast.makeText(getApplicationContext(), R.string.please_input_freq, Toast.LENGTH_SHORT).show();
				}else {
					//invalidate freq.
					if(radioService.getRadioType() == RadioService.RADIO_FM){
						if (freqStr.length() < 4|| freqStr.length() > 5 || freqStr.charAt(freqStr.length() - 2) != '.'){
							Toast.makeText(getApplicationContext(), context.getResources().getString(R.string.indicate_validate_freq)+lowFM+" -- "+hightFM , Toast.LENGTH_LONG).show();
							return;
						}else{
							freq = Integer.parseInt(freqStr.replaceAll("\\.", "")) * 10;
						}
						if (freq < RadioService.FM_LOW_FREQ|| freq > RadioService.FM_HIGH_FREQ) {
							Toast.makeText(getApplicationContext(), context.getResources().getString(R.string.indicate_validate_freq)+lowFM+" -- "+hightFM , Toast.LENGTH_LONG).show();
							return;
						}
					}else if(radioService.getRadioType() == RadioService.RADIO_AM){
						if (freqStr.contains(".")) {
							Toast.makeText(getApplicationContext(),  context.getResources().getString(R.string.indicate_validate_freq)+RadioService.AM_LOW_FREQ+" -- "+ RadioService.AM_HIGH_FREQ, Toast.LENGTH_LONG).show();
							return ;
						}
						freq = Integer.parseInt(freqStr);
						if (freq < RadioService.AM_LOW_FREQ|| freq > RadioService.AM_HIGH_FREQ) {
							Toast.makeText(getApplicationContext(),  context.getResources().getString(R.string.indicate_validate_freq)+RadioService.AM_LOW_FREQ+" -- "+ RadioService.AM_HIGH_FREQ, Toast.LENGTH_LONG).show();
							return ;
						}
					}else{
						if(freqStr.contains(".")){
							if(freqStr.length() < 4|| freqStr.length() > 5 || freqStr.charAt(freqStr.length() - 2) != '.'){
								Toast.makeText(getApplicationContext(),  context.getResources().getString(R.string.indicate_validate_freq)+lowFM+" -- "+hightFM 
									+context.getResources().getString(R.string.indicate_or)+RadioService.AM_LOW_FREQ+" -- "+RadioService.AM_HIGH_FREQ, Toast.LENGTH_LONG).show();
								return;
							}
							if (freq < RadioService.FM_LOW_FREQ|| freq > RadioService.FM_HIGH_FREQ) {
								Toast.makeText(getApplicationContext(),  context.getResources().getString(R.string.indicate_validate_freq)+lowFM+" -- "+hightFM 
									+context.getResources().getString(R.string.indicate_or)+RadioService.AM_LOW_FREQ+" -- "+RadioService.AM_HIGH_FREQ, Toast.LENGTH_LONG).show();
								return;
							}
						}else{
							freq = Integer.parseInt(freqStr);
							if (freq < RadioService.AM_LOW_FREQ|| freq > RadioService.AM_HIGH_FREQ) {
								Toast.makeText(getApplicationContext(),  context.getResources().getString(R.string.indicate_validate_freq)+RadioService.FM_LOW_FREQ+" -- "+ RadioService.FM_HIGH_FREQ
									+context.getResources().getString(R.string.indicate_or)+RadioService.AM_LOW_FREQ+" -- "+RadioService.AM_HIGH_FREQ, Toast.LENGTH_LONG).show();
								return ;
							}
						}
					}	
					//same freq.
					int sameId = isInCurChannelList(freq);
					if(sameId>=0 &&(sameId!=channelId)){	
						String abridge = radioService.getChannelList().get(sameId).abridge;
						if(TextUtils.isEmpty(abridge)){
							Toast.makeText(getApplicationContext(), R.string.already_in_channellist, Toast.LENGTH_LONG).show();
						}else{
							Toast.makeText(getApplicationContext(),  context.getResources().getString(R.string.indicate_same_freq)+abridge, Toast.LENGTH_LONG).show();
						}
						return;
					}
				}
				item.freq = freqStr;
				item.abridge = shortNameStr;
				item.name = fullNameStr;
				radioService.setChannelItem(channelId,item);
				inflateChannelList(radioService.getChannelList());
				setChannelItemChecked();
				radioService.updatePreferences(RadioService.RADIO_DATA_SAVE);
			}
		};
    };
		
	@Override
	public void onProgressChanged(SeekBar seekBar, int progress,
			boolean fromUser) {
		radioService.setVolume(progress);
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {

	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// TODO Auto-generated method stub
		if (keyCode == KeyEvent.KEYCODE_BACK) {  
            moveTaskToBack(false);  
            return true;  
        }  
		return super.onKeyDown(keyCode, event);
	}
	
	/*************The No_Remind Dialog*******************/
	private AlertDialog createDialog() {
		di = new AlertDialog.Builder(this).setTitle("温馨提示")
				.setMessage(R.string.clear_content).setView(checkbox)
				.setPositiveButton(R.string.ok ,new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						SharedPreferences pre = getSharedPreferences(
								"checkvalue", MODE_PRIVATE);
						Editor editor = pre.edit();
						if (cBox.isChecked()) {
							editor.putString("ischeck", "1");
						} else {
							editor.putString("ischeck", "0");
						}
						//clear all content and refresh view
						mChannelLayout.removeAllViews();
						radioService.getChannelList().clear();
						//save current channel info.
						radioService.updatePreferences(RadioService.RADIO_DATA_SAVE);
						editor.commit();

					}
				}).setNegativeButton(R.string.cancel,
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								// TODO Auto-generated method stub
							}
						})
				
				.create();
		return di;
	}
			
	private IntentFilter getIntentFilter(){		
		IntentFilter myIntentFilter = new IntentFilter();
		myIntentFilter.addAction("MediaStatus.BonovoRadio.Media_Broadcast_Close");
		myIntentFilter.addAction("com.bonovo.radio.CHANGE");
		return myIntentFilter;		
	};
	
	private BroadcastReceiver myBroadcastReveiver = new BroadcastReceiver() {
		
		@Override
		public void onReceive(Context context, Intent intent) {
			// TODO Auto-generated method stub
			String action = intent.getAction();
			if(DEBUG)Log.d(TAG,"onReceive action= "+action);
			if(!TextUtils.isEmpty(action)){
				if(action.equals("com.bonovo.radio.CHANGE")){
					String command = intent.getStringExtra("command");
					if(DEBUG)Log.d(TAG,"onReceive  com.bonovo.radio.CHANGE command= "+command);
					if("fm".equals(command)){
						if(!radioService.mIsSearchThreadRunning){
							Log.d(TAG,"mLastFreq "+mLastFreq+" ; mLastFreqChannelId "+mLastFreqChannelId);
							radioService.setFreq(mLastFreq);
							radioService.muteOrRecover(false);
							turnFmAMUI(RadioService.RADIO_FM);
						}else {
							Toast.makeText(getApplicationContext(), R.string.searching, Toast.LENGTH_SHORT).show();
						}
				
					}else if("am".equals(command)){
						if(!radioService.mIsSearchThreadRunning){
							Log.d(TAG,"mLastAm "+mLastAm+" ; mLastAmChannelId "+mLastAmChannelId);
							radioService.setFreq(mLastAm);
							radioService.muteOrRecover(false);
							turnFmAMUI(RadioService.RADIO_AM);
						}else {
							Toast.makeText(getApplicationContext(), R.string.searching, Toast.LENGTH_SHORT).show();
						}					
					}
				}
			}
		}
	};
}
