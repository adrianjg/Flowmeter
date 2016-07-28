package com.adrianjg.flowmeter.history;

import com.adrianjg.dlab.R;
import com.adrianjg.flowmeter.image.ImageTargets;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.app.Activity;
import android.content.Intent;

/**
 * Displays LED reading results and the will eventually display the final
 * hg-level result after the regression is implemented. These results are
 * displayed when a given patient history file is selected in the ViewHistory
 * activity. The results displayed are those of the selected file.
 *
 */
public class ShowHistory extends Activity {

    private TextView results;
    private int patientId;
    private String patientName;
    private TextView show_patient;
    private TextView show_id;
    private TextView show_result;
    private double hemoResult;
    int[] i;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.history_show);
        // Show the Up button in the action bar.
        setupActionBar();

        i = new int[6];
        Intent intent = getIntent();
        patientId = intent.getIntExtra(ImageTargets.EXTRA_PATIENT_ID, 0);
        patientName = intent.getStringExtra(ImageTargets.EXTRA_PATIENT_NAME);
        if (i == null)
            Log.d("i null2", "i null2");
        else
            Log.d("i received2", "i received2");
        hemoResult = 10.2;

        results = (TextView) findViewById(R.id.raw_data);
        results.setText(String.format(" %x \n %x \n %x \n %x \n %x \n %x",
                i[0], i[1], i[2], i[3], i[4], i[5]));
        show_patient = (TextView) findViewById(R.id.patient_name);
        show_id = (TextView) findViewById(R.id.patient_id);
        show_result = (TextView) findViewById(R.id.result_value);

        show_patient.setText("Name: " + patientName);
        show_id.setText("ID: " + Integer.toString(patientId));
        show_result
                .setText("Result: " + Double.toString(hemoResult) + " mg/dL");

    }

    @Override
    protected void onStop() {
        super.onStop();
        finish();
    }

    /**
     * Set up the {@link android.app.ActionBar}.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void setupActionBar() {

        getActionBar().setDisplayHomeAsUpEnabled(true);

    }
}