package org.ole.planet.takeout;

import android.content.Intent;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

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
                imageButtonAction("Clicked myLibrary");
            }
        });

        myCourseImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imageButtonAction("Clicked myLibrary");
            }
        });

        myMeetUpsImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imageButtonAction("Clicked myLibrary");
            }
        });

        myTeamsImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imageButtonAction("Clicked myTeams");
            }
        });
        return view;
    }

    public void imageButtonAction(String btnmessage) {
        Log.e("DF: ", btnmessage);
        Intent intent = new Intent(getActivity(), PDFReaderActivity.class);
        startActivity(intent);
    }

    private void declareElements(View view) {
        // Imagebuttons
        myLibraryImage = (ImageButton) view.findViewById(R.id.myLibraryImageButton);
        myCourseImage = (ImageButton) view.findViewById(R.id.myCoursesImageButton);
        myMeetUpsImage = (ImageButton) view.findViewById(R.id.myMeetUpsImageButton);
        myTeamsImage = (ImageButton) view.findViewById(R.id.myTeamsImageButton);

        //TextViews
    }
}
