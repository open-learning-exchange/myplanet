package org.ole.planet.myplanet.ui.userprofile;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.xilingyuli.markdown.MarkDownController;
import com.xilingyuli.markdown.MarkDownEditorView;
import com.xilingyuli.markdown.OnPreInsertListener;
import com.xilingyuli.markdown.ToolsAdapter;

import org.ole.planet.myplanet.R;

import br.tiagohm.markdownview.MarkdownView;

/**
 * A simple {@link Fragment} subclass.
 */
public class EditAchievementFragment extends Fragment  {

    ToolsAdapter toolsAdapter;
    public EditAchievementFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v =  inflater.inflate(R.layout.fragment_edit_achievement, container, false);
        return v;
    }

}
