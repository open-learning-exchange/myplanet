package org.ole.planet.myplanet.ui.feedback;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmFeedback;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Date;

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
        if (!TextUtils.isEmpty(feedback.getOpenTime()))
            tvDate.setText(TimeUtils.getFormatedDateWithTime(Long.parseLong(feedback.getOpenTime())));
        else
            tvDate.setText("Date : N/A");
        tvMessage.setText(TextUtils.isEmpty(feedback.getMessage()) ? "N/A" : feedback.getMessage());
        Button closeButton = findViewById(R.id.close_feedback);
        Button replyButton = findViewById(R.id.reply_feedback);
        EditText editText = findViewById(R.id.feedback_reply_edit_text);
        replyButton.setOnClickListener(r -> {
            String message = editText.getText().toString();
            JsonObject object = new JsonObject();
            object.addProperty("message", message);
            object.addProperty("time", new Date().getTime() +"");
            object.addProperty("user", feedback.getOwner() +"");
            String id = feedback.getId();
            addReply(realm, object,id);
        });

    }

    public void addReply(Realm mRealm, JsonObject obj, String id) {
        RealmFeedback feedback = mRealm.where(RealmFeedback.class).equalTo("id", id).findFirst();
        if(feedback != null){
            mRealm.executeTransaction(realm -> {
                Gson con = new Gson();
                JsonArray msgArray = con.fromJson(feedback.getMessages(),JsonArray.class);
                Log.e("hi", new Gson().toJson(msgArray));
                msgArray.add(obj);
                feedback.setMessages(msgArray);
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home)
            finish();
        return super.onOptionsItemSelected(item);
    }
}
