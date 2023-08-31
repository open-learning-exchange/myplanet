package org.ole.planet.myplanet.ui.helpwanted;

import static org.ole.planet.myplanet.ui.sync.SyncActivity.PREFS_NAME;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.FragmentHelpWantedBinding;
import org.ole.planet.myplanet.utilities.JsonUtils;

public class HelpWantedFragment extends Fragment {
    private FragmentHelpWantedBinding fragmentHelpWantedBinding;
    SharedPreferences settings;
    JsonObject manager;

    public HelpWantedFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentHelpWantedBinding = FragmentHelpWantedBinding.inflate(inflater, container, false);
        settings = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (settings.contains("user_admin"))
            manager = new JsonParser().parse(settings.getString("user_admin", "")).getAsJsonObject();
        return fragmentHelpWantedBinding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        String boldName = "<b>" + getString(R.string.name_colon) + "</b>";
        String boldEmail = "<b>" + getString(R.string.email_colon) + "</b>";
        String boldPhone = "<b>" + getString(R.string.phone_number_colon) + "</b>";
        if (manager != null) {
            fragmentHelpWantedBinding.llData.setVisibility(View.VISIBLE);
            fragmentHelpWantedBinding.tvName.setText(Html.fromHtml(boldName + JsonUtils.getString("name", manager)));
            fragmentHelpWantedBinding.tvEmail.setText(Html.fromHtml(boldEmail + JsonUtils.getString("name", manager)));
            fragmentHelpWantedBinding.tvPhone.setText(Html.fromHtml(boldPhone + JsonUtils.getString("phoneNumber", manager)));
        } else {
            fragmentHelpWantedBinding.llData.setVisibility(View.GONE);
            fragmentHelpWantedBinding.tvNodata.setText(R.string.no_data_available);
            fragmentHelpWantedBinding.tvNodata.setVisibility(View.VISIBLE);
        }
    }
}
