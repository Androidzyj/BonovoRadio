package com.bonovo.radio;
import android.os.Message;


public interface RadioInterface {
	public static final int RADIO_FM = 0;
	public static final int RADIO_AM = 1;
	public static final int RADIO_COLLECT = 2;
	
	public int fineLeft(int freq);
	public int fineRight(int freq);
	public int stepLeft(int freq);
	public int stepRight(int freq);
	public void setFreq(int freq);
    public void onAutoSearch();
    
	int getCurChannelId();
    void setCurChannelId(int id);
	void setFunctionId(int id);
	int getFunctionId();
	void setCurrentFreq(int freq);
	int getCurrentFreq();
	void clearAllContent();
	
	interface RadioStatusChangeListener {
	      public void onStatusChange(int type);
		  public void onStatusChange(Message msg);
	}
	void registStatusListener(final RadioStatusChangeListener listener);

	void unRegistStatusListener();

}
