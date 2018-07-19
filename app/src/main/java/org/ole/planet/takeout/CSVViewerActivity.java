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

    private void declareElements() {
        mCSVNameTitle = (TextView) findViewById(R.id.csvFileName);
        mCSVContent = (TextView) findViewById(R.id.csvFileContent);
        filePath = Environment.getExternalStorageDirectory() + File.separator + "ole" + File.separator;
    }

    private void renderCSVFile() {
        // File name to be viewed

        Intent imageOpenIntent = getIntent();
        fileName = imageOpenIntent.getStringExtra("TOUCHED_FILE");
        Log.i("THE_FILE", filePath);

        if (fileName != null || fileName != "") {
            mCSVNameTitle.setText(fileName);
            mCSVNameTitle.setVisibility(View.VISIBLE);
        } else {
            mCSVNameTitle.setVisibility(View.INVISIBLE);
        }

        try{

            CSVReader reader = new CSVReader(new FileReader(new File(filePath, fileName)), ',' , '"');

            //Read all rows at once
            List<String[]> allRows = reader.readAll();



            //Read CSV line by line and use the string array as you want
            for(String[] row : allRows){
                Log.i("CSV_CONTENT", Arrays.toString(row));
                mCSVContent.append(Arrays.toString(row));
                mCSVContent.append("\n");
            }

            /*
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                if (nextLine != null) {
                    //Verifying the read data here
                    mCSVContent.setText(Arrays.toString(nextLine));
                    Log.i("CSV_CONTENT", Arrays.toString(nextLine));
                }
            }
            */

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
