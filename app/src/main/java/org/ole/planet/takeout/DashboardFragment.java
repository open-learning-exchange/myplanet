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
    private ImageButton myLibraryImage;
    private ImageButton myCoursesImage;
    private ImageButton myMeetUpsImage;
    private ImageButton myTeamsImage;

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

        myCoursesImage.setOnClickListener(new View.OnClickListener() {
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
        return view;
    }

    private void declareElements(View view) {
        // Imagebuttons
        myLibraryImage = (ImageButton) view.findViewById(R.id.myLibrary);
        myCoursesImage = (ImageButton) view.findViewById(R.id.myCourses);
        myMeetUpsImage = (ImageButton) view.findViewById(R.id.myMeetUps);
        myTeamsImage = (ImageButton) view.findViewById(R.id.myTeams);
    }
}
