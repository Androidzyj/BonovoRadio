package com.bonovo.radio;
interface IRadioService{
	char getRadioType();//f:FM  a:AM 
	void turnFM();
	void turnAM();
	void nextFreq();
	void preFreq();
	void turnToFM(String freq);
	void turnToAM(String am);
}

