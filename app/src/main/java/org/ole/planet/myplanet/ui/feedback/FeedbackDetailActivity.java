package org.ole.planet.myplanet.ui.feedback;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.FeedbackReply;
import org.ole.planet.myplanet.model.RealmFeedback;
import org.ole.planet.myplanet.utilities.TimeUtils;

import java.util.Date;
import java.util.List;

import io.realm.Realm;

public class FeedbackDetailActivity extends AppCompatActivity {

    private RecyclerView rv_feedback_reply;
    private RecyclerView.Adapter mAdapter;
    private RecyclerView.LayoutManager layoutManager;
    Button closeButton,replyButton;
    EditText editText;
    RealmFeedback feedback;
    Realm realm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback_detail);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setTitle("Feedback");
        realm = new DatabaseService(this).getRealmInstance();
        feedback = realm.where(RealmFeedback.class).equalTo("id", getIntent().getStringExtra("id")).findFirst();
        TextView tvMessage = findViewById(R.id.tv_message);
        TextView tvDate = findViewById(R.id.tv_date);
        if (!TextUtils.isEmpty(feedback.getOpenTime()))
            tvDate.setText(TimeUtils.getFormatedDateWithTime(Long.parseLong(feedback.getOpenTime())));
        else
            tvDate.setText("Date : N/A");
        tvMessage.setText(TextUtils.isEmpty(feedback.getMessage()) ? "N/A" : feedback.getMessage());
        closeButton = findViewById(R.id.close_feedback);
        replyButton = findViewById(R.id.reply_feedback);
        editText = findViewById(R.id.feedback_reply_edit_text);
        setUpReplies();
    }

    public void setUpReplies(){
        rv_feedback_reply = (RecyclerView) findViewById(R.id.rv_feedback_reply);
        rv_feedback_reply.setHasFixedSize(true);
        layoutManager = new LinearLayoutManager(this);
        rv_feedback_reply.setLayoutManager(layoutManager);
        mAdapter = new RvFeedbackAdapter(feedback.getMessageList(), getApplicationContext());
        rv_feedback_reply.setAdapter(mAdapter);
        updateForClosed();
        closeButton.setOnClickListener(view -> {
            realm.executeTransaction(realm1 -> {
                RealmFeedback feedback1 = realm1.where(RealmFeedback.class).equalTo("id", getIntent().getStringExtra("id")).findFirst();
                feedback1.setStatus("Closed");
                updateForClosed();
            });
        });
        replyButton.setOnClickListener(r -> {
            String message = editText.getText().toString().trim();
            JsonObject object = new JsonObject();
            object.addProperty("message", message);
            object.addProperty("time", new Date().getTime() +"");
            object.addProperty("user", feedback.getOwner() +"");
            String id = feedback.getId();
            addReply(realm, object,id);
            mAdapter = new RvFeedbackAdapter(feedback.getMessageList(), getApplicationContext());
            rv_feedback_reply.setAdapter(mAdapter);
        });
    }

    public void updateForClosed(){
        if(feedback.getStatus().equalsIgnoreCase("Closed")){
            closeButton.setEnabled(false);
            replyButton.setEnabled(false);
            editText.setVisibility(View.INVISIBLE);
        }
    }

    public void addReply(Realm mRealm, JsonObject obj, String id) {
        RealmFeedback feedback = mRealm.where(RealmFeedback.class).equalTo("id", id).findFirst();
        if(feedback != null){
            mRealm.executeTransaction(realm -> {
                Gson con = new Gson();
                JsonArray msgArray = con.fromJson(feedback.getMessages(),JsonArray.class);
                Log.e("Msg", new Gson().toJson(msgArray));
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

    public class RvFeedbackAdapter extends RecyclerView.Adapter<RvFeedbackAdapter.ReplyViewHolder> {
        private List<FeedbackReply> replyList;
        Context context;

        public class ReplyViewHolder extends RecyclerView.ViewHolder {
            public TextView tv_message, tv_date,tv_user;
            public ReplyViewHolder(View v) {
                super(v);
                tv_message = v.findViewById(R.id.tv_message);
                tv_user = v.findViewById(R.id.tv_user);
                tv_date = v.findViewById(R.id.tv_date);
            }
        }

        public RvFeedbackAdapter(List<FeedbackReply> replyList, Context context) {
            this.replyList = replyList;
            this.context = context;
        }

        @Override
        public RvFeedbackAdapter.ReplyViewHolder onCreateViewHolder(ViewGroup parent,
                                                         int viewType) {
            View v = LayoutInflater.from(context).inflate(R.layout.row_feedback_reply, parent, false);
            return new RvFeedbackAdapter.ReplyViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ReplyViewHolder holder, int position) {
           holder.tv_date.setText(TimeUtils.getFormatedDateWithTime(Long.parseLong(replyList.get(position).getDate())));
           holder.tv_user.setText(replyList.get(position).getUser());
           holder.tv_message.setText(replyList.get(position).getMessage());
        }

        @Override
        public int getItemCount() {
            return replyList.size();
        }
    }


}

