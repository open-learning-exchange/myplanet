package org.ole.planet.takeout.base;

import android.content.Context;
import android.support.v4.app.Fragment;

import org.ole.planet.takeout.callback.OnHomeItemClickListener;

public abstract class BaseContainerFragment extends Fragment {
  public   OnHomeItemClickListener homeItemClickListener;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnHomeItemClickListener) {
            homeItemClickListener = (OnHomeItemClickListener) context;
        }
    }
}
