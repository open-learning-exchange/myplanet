package org.ole.planet.myplanet.ui.viewer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.opencsv.CSVReader;

import org.ole.planet.myplanet.databinding.ActivityCsvviewerBinding;

import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.List;

public class CSVViewerActivity extends AppCompatActivity {
    private ActivityCsvviewerBinding activityCsvviewerBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityCsvviewerBinding = ActivityCsvviewerBinding.inflate(getLayoutInflater());
        setContentView(activityCsvviewerBinding.getRoot());
        renderCSVFile();
    }

    private void renderCSVFile() {
        Intent csvFileOpenIntent = getIntent();
        String fileName = csvFileOpenIntent.getStringExtra("TOUCHED_FILE");

        if (fileName != null && !fileName.isEmpty()) {
            activityCsvviewerBinding.csvFileName.setText(fileName);
            activityCsvviewerBinding.csvFileName.setVisibility(View.VISIBLE);
        }

        try {
            File csvFile;
            if (fileName.startsWith("/")) {
                csvFile = new File(fileName);
            } else {
                File basePath = getExternalFilesDir(null);
                csvFile = new File(basePath, "ole/" + fileName);
            }

            CSVReader reader = new CSVReader(new FileReader(csvFile), ',', '"');

            List<String[]> allRows = reader.readAll();

            for (String[] row : allRows) {
                activityCsvviewerBinding.csvFileContent.append(Arrays.toString(row));
                activityCsvviewerBinding.csvFileContent.append("\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}