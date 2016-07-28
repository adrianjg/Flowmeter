package com.adrianjg.flowmeter.history;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.adrianjg.dlab.R;
import com.adrianjg.flowmeter.image.ImageTargets;

public class NewPatient extends Activity {
	
	public final static String EXTRA_PATIENT_NAME = "com.adrianjg.flowmeter.image.ImageTargets.PATIENT_NAME";
	public final static String EXTRA_PATIENT_ID = "com.adrianjg.flowmeter.image.ImageTargets.PATIENT_ID";
	
	@Override
	public void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.history_new_patient);
	}
	
	private void writeToFile(File file, String data)
	{
	    try {
	    	PrintWriter pw = new PrintWriter(new FileWriter(file));
	    	pw.print(data);
	    	pw.close();
	    }
	    catch (IOException e) {
	        android.util.Log.e("Exception", "File write failed: " + e.toString());
	    } 
	}
	
	public void goHistory(View v) {
		Intent intent = new Intent();
		intent.setClassName(getPackageName(), "com.adrianjg.flowmeter" + ".history.ShowHistory");

		startActivity(intent);

	}
	
	public void goForth(View v) {
		EditText patientText = (EditText) findViewById(R.id.patient_name);
    	EditText IdText = (EditText) findViewById(R.id.patient_id);
    	EditText age = (EditText) findViewById(R.id.patient_age);
    	EditText height = (EditText) findViewById(R.id.patient_height);
    	Spinner gender = (Spinner) findViewById(R.id.patient_gender);
    	
    	String patientName;
    	String patientId;
    	int patientHeight;
    	String patientGender;
    	int patientAge;

    	if ( patientText.getText().toString().matches("") || 
    		IdText.getText().toString().matches("") ||
    		height.getText().toString().matches("") ||
    		age.getText().toString().matches("")
    		) {
    		patientName = "John Doe";
    		patientId = "31415";
    		patientAge = 30;
    		patientGender = "Male";
    		patientHeight = 170;
		} else {

	    	//TODO: make sure values check out (age below 200, int, etc)
	    	//TODO: check gender within values taken by ImageTargets
			patientName = patientText.getText().toString();
	    	patientId = IdText.getText().toString();	    	
	    	patientHeight = Integer.parseInt(height.getText().toString());
	    	patientGender = gender.getSelectedItem().toString();
	    	patientAge = Integer.parseInt(age.getText().toString());
		}
		
		
		String filepath = Environment.getExternalStorageDirectory().getPath();
		File file = new File(filepath, ImageTargets.getAR_Folder());
		if (!file.exists()) file.mkdirs();
		file = new File(file, patientId);
		if(!file.exists()) file.mkdir();
		
		// write the info file
		file = new File(file, "info.txt");
		String info = "Date: "+new Date()+"\nName: "+patientName+"\nID: "+patientId+"\n";
		writeToFile(file, info);
				
		
    	Intent intent = new Intent();
		/*
		intent.setClassName(getPackageName(), "com.adrianjg.flowmeter" + ".history.Instructions");
		*/		
		intent.putExtra("PATIENT_NAME", patientName);
		intent.putExtra("PATIENT_ID", patientId);		
		intent.putExtra("PATIENT_HEIGHT", patientHeight);
		intent.putExtra("PATIENT_GENDER", patientGender);
		intent.putExtra("PATIENT_AGE", patientAge);
		intent.setClassName(getPackageName(), "com.adrianjg.flowmeter" + ".image.ImageTargets");
    	intent.putExtra(EXTRA_PATIENT_NAME, patientName);
		intent.putExtra(EXTRA_PATIENT_ID, patientId);	
		startActivity(intent);
	}
	
}
