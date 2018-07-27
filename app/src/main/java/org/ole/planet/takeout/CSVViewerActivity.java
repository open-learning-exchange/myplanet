package org.ole.planet.takeout;

import android.content.Intent;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.opencsv.CSVReader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Arrays;
import java.util.List;


public class CSVViewerActivity extends AppCompatActivity {

    private TextView mCSVNameTitle;
    private TextView mCSVContent;
    private String filePath;
    private String fileName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_csvviewer);
        declareElements();
        renderCSVFile();
    }

    public CSVViewerActivity()
    {
        filePath = new DashboardFragment().globalFilePath;
    }

    private void declareElements() {
        mCSVNameTitle = (TextView) findViewById(R.id.csvFileName);
        mCSVContent = (TextView) findViewById(R.id.csvFileContent);
    }

    private void renderCSVFile() {
        // File name to be viewed

        Intent imageOpenIntent = getIntent();
        fileName = imageOpenIntent.getStringExtra("TOUCHED_FILE");

        if (fileName != null && !fileName.isEmpty()) {
            mCSVNameTitle.setText(fileName);
            mCSVNameTitle.setVisibility(View.VISIBLE);
        }

        try{
            CSVReader reader = new CSVReader(new FileReader(new File(filePath, fileName)), ',' , '"');

            //Get all lines from CSV file
            List<String[]> allRows = reader.readAll();

            //Read List "allRows" into textview line by line
            for(String[] row : allRows){
                Log.i("CSV_CONTENT", Arrays.toString(row));
                mCSVContent.append(Arrays.toString(row));
                mCSVContent.append("\n");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
