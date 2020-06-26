package org.ole.planet.myplanet.ui.helpwanted;


import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import static org.ole.planet.myplanet.ui.sync.SyncActivity.PREFS_NAME;

/**
 * A simple {@link Fragment} subclass.
 */
public class HelpWantedFragment extends Fragment {

    SharedPreferences settings;
    JsonObject manager;
    LinearLayout llData;
    TextView name, email, phone, nodata;

    public HelpWantedFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_help_wanted, container, false);
        settings = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        name = v.findViewById(R.id.tv_name);
        email = v.findViewById(R.id.tv_email);
        phone = v.findViewById(R.id.tv_phone);
        nodata = v.findViewById(R.id.tv_nodata);
        llData = v.findViewById(R.id.ll_data);
        if (settings.contains("user_admin"))
            manager = new JsonParser().parse(settings.getString("user_admin", "")).getAsJsonObject();
        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        String boldName = "<b>" + "Name : " + "</b>";
        String boldEmail = "<b>" + "Email : " + "</b>";
        String boldPhone = "<b>" + "Phone Number : " + "</b>";
        if (manager != null) {
            llData.setVisibility(View.VISIBLE);
            name.setText(Html.fromHtml(boldName + JsonUtils.getString("name", manager)));
            email.setText(Html.fromHtml(boldEmail + JsonUtils.getString("name", manager)));
            phone.setText(Html.fromHtml(boldPhone + JsonUtils.getString("phoneNumber", manager)));
        }else{
            llData.setVisibility(View.GONE);
            nodata.setText("No data available");
            nodata.setVisibility(View.VISIBLE);
        }
    }
}
