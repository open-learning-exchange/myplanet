package org.ole.planet.myplanet.ui.feedback;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmFeedback;
import org.ole.planet.myplanet.utilities.TimeUtils;

import io.realm.Realm;

public class FeedbackDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback_detail);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setTitle("Feedback");
        Realm realm = new DatabaseService(this).getRealmInstance();
        RealmFeedback feedback = realm.where(RealmFeedback.class).equalTo("id", getIntent().getStringExtra("id")).findFirst();
        TextView tvMessage = findViewById(R.id.tv_message);
        TextView tvDate = findViewById(R.id.tv_date);
        tvDate.setText(TimeUtils.getFormatedDateWithTime(Long.parseLong(feedback.getOpenTime())));
        tvMessage.setText(feedback.getMessage());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home)
            finish();
        return super.onOptionsItemSelected(item);
    }
}
