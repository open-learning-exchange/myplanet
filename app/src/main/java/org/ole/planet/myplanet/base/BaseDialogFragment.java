package org.ole.planet.myplanet.base;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;

public abstract class BaseDialogFragment extends DialogFragment {
  public  String id;
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Holo_Light_Dialog_NoActionBar_MinWidth);
        if (getArguments() != null) {
            id = getArguments().getString(getKey());
        }
    }

     protected abstract String getKey();

}
