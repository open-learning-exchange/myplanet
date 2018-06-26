package org.ole.planet.takeout;

import android.content.Intent;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

/**
 * A placeholder fragment containing a simple view.
 */
public class DashboardFragment extends Fragment {

    //ImageButtons
    private ImageButton myLibraryImage;
    private ImageButton myCourseImage;
    private ImageButton myMeetUpsImage;
    private ImageButton myTeamsImage;

    //TextViews
    private TextView myLibraryTextView;
    private TextView myCoursesTextView;
    private TextView myMeetUpsTextView;
    private TextView myTeamsTextView;


    public DashboardFragment() {
        //init dashboard
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        declareElements(view);

        myLibraryImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.e("DF: ", "Clicked myLibrary");
                Intent intent = new Intent(getActivity() , PDFReaderActivity.class);
                startActivity(intent);
            }
        });

        myCourseImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.e("DF: ", "Clicked myLibrary");
                Intent intent = new Intent(getActivity() , PDFReaderActivity.class);
                startActivity(intent);
            }
        });

        myMeetUpsImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.e("DF: ", "Clicked myLibrary");
                Intent intent = new Intent(getActivity() , PDFReaderActivity.class);
                startActivity(intent);
            }
        });

        myTeamsImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.e("DF: ", "Clicked myLibrary");
                Intent intent = new Intent(getActivity() , PDFReaderActivity.class);
                startActivity(intent);
            }
        });

        //TextView Clickable
        myLibraryTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.e("DF: ", "Clicked myLibrary");
                Intent intent = new Intent(getActivity() , PDFReaderActivity.class);
                startActivity(intent);
            }
        });

        myCoursesTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.e("DF: ", "Clicked myLibrary");
                Intent intent = new Intent(getActivity() , PDFReaderActivity.class);
                startActivity(intent);
            }
        });

        myMeetUpsTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.e("DF: ", "Clicked myLibrary");
                Intent intent = new Intent(getActivity() , PDFReaderActivity.class);
                startActivity(intent);
            }
        });

        myTeamsTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.e("DF: ", "Clicked myLibrary");
                Intent intent = new Intent(getActivity() , PDFReaderActivity.class);
                startActivity(intent);
            }
        });

        return view;
    }

    private void declareElements(View view) {

        // Imagebuttons
        myLibraryImage = (ImageButton) view.findViewById(R.id.myLibraryImageButton);
        myCourseImage = (ImageButton) view.findViewById(R.id.myCoursesImageButton);
        myMeetUpsImage = (ImageButton) view.findViewById(R.id.myMeetUpsImageButton);
        myTeamsImage = (ImageButton) view.findViewById(R.id.myTeamsImageButton);

        //TextViews
        myLibraryTextView = (TextView) view.findViewById(R.id.myLibraryTextView);
        myCoursesTextView = (TextView) view.findViewById(R.id.myCoursesTextView);
        myMeetUpsTextView = (TextView) view.findViewById(R.id.myMeetUpsTextView);
        myTeamsTextView = (TextView) view.findViewById(R.id.myTeamsTextView);

    }
}
