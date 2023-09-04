package org.ole.planet.myplanet.ui.viewer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import org.ole.planet.myplanet.R;

import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.List;

public class CSVViewerActivity extends AppCompatActivity {

    private TextView mCSVNameTitle;
    private TextView mCSVContent;

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
    }

    private void renderCSVFile() {
        Intent csvFileOpenIntent = getIntent();
        String fileName = csvFileOpenIntent.getStringExtra("TOUCHED_FILE");

        if (fileName != null && !fileName.isEmpty()) {
            mCSVNameTitle.setText(fileName);
            mCSVNameTitle.setVisibility(View.VISIBLE);
        }


        try {
            File csvFile;
            if (fileName.startsWith("/")) {
                csvFile = new File(fileName);
            } else {
                File basePath = getExternalFilesDir(null);
                csvFile = new File(basePath, "ole/" + fileName);
            }

            CSVReader reader = new CSVReaderBuilder(new FileReader(csvFile))
                    .withCSVParser(new CSVParserBuilder()
                            .withSeparator(',')
                            .withQuoteChar('"')
                            .build())
                    .build();

            List<String[]> allRows = reader.readAll();

            for (String[] row : allRows) {
                mCSVContent.append(Arrays.toString(row));
                mCSVContent.append("\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}