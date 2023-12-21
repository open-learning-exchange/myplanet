package org.ole.planet.myplanet.ui.feedback;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.ActivityFeedbackDetailBinding;
import org.ole.planet.myplanet.databinding.RowFeedbackReplyBinding;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.FeedbackReply;
import org.ole.planet.myplanet.model.RealmFeedback;
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity;
import org.ole.planet.myplanet.utilities.TimeUtils;

import java.util.Date;
import java.util.List;

import io.realm.Realm;

public class FeedbackDetailActivity extends AppCompatActivity {
    private ActivityFeedbackDetailBinding activityFeedbackDetailBinding;
    private RecyclerView.Adapter mAdapter;
    private RecyclerView.LayoutManager layoutManager;
    RealmFeedback feedback;
    Realm realm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityFeedbackDetailBinding = ActivityFeedbackDetailBinding.inflate(getLayoutInflater());
        setContentView(activityFeedbackDetailBinding.getRoot());
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setTitle(R.string.feedback);
        realm = new DatabaseService(this).getRealmInstance();
        feedback = realm.where(RealmFeedback.class).equalTo("id", getIntent().getStringExtra("id")).findFirst();
        if (!TextUtils.isEmpty(feedback.openTime))
            activityFeedbackDetailBinding.tvDate.setText(TimeUtils.getFormatedDateWithTime(Long.parseLong(feedback.openTime)));
        else activityFeedbackDetailBinding.tvDate.setText(R.string.date_n_a);
        activityFeedbackDetailBinding.tvMessage.setText(TextUtils.isEmpty(feedback.getMessage()) ? "N/A" : feedback.getMessage());
        setUpReplies();
    }

    public void setUpReplies() {
        activityFeedbackDetailBinding.rvFeedbackReply.setHasFixedSize(true);
        layoutManager = new LinearLayoutManager(this);
        activityFeedbackDetailBinding.rvFeedbackReply.setLayoutManager(layoutManager);
        mAdapter = new RvFeedbackAdapter(feedback.getMessageList(), getApplicationContext());
        activityFeedbackDetailBinding.rvFeedbackReply.setAdapter(mAdapter);
        activityFeedbackDetailBinding.closeFeedback.setOnClickListener(view -> {
            realm.executeTransactionAsync(realm1 -> {
                RealmFeedback feedback1 = realm1.where(RealmFeedback.class).equalTo("id", getIntent().getStringExtra("id")).findFirst();
                feedback1.status = "Closed";
            }, () -> {
                updateForClosed();
            });
        });

        activityFeedbackDetailBinding.replyFeedback.setOnClickListener(r -> {
            if (TextUtils.isEmpty(activityFeedbackDetailBinding.feedbackReplyEditText.getText().toString().trim())) {
                activityFeedbackDetailBinding.feedbackReplyEditText.setError("Kindly enter reply message");
            } else {
                String message = activityFeedbackDetailBinding.feedbackReplyEditText.getText().toString().trim();
                JsonObject object = new JsonObject();
                object.addProperty("message", message);
                object.addProperty("time", new Date().getTime() + "");
                object.addProperty("user", feedback.owner + "");
                String id = feedback.id;
                addReply(object, id);
                mAdapter = new RvFeedbackAdapter(feedback.getMessageList(), getApplicationContext());
                activityFeedbackDetailBinding.rvFeedbackReply.setAdapter(mAdapter);
                activityFeedbackDetailBinding.feedbackReplyEditText.setText("");
                activityFeedbackDetailBinding.feedbackReplyEditText.clearFocus();
            }
        });
    }

    public void updateForClosed() {
        if (feedback.status.equalsIgnoreCase("Closed")) {
            activityFeedbackDetailBinding.closeFeedback.setEnabled(false);
            activityFeedbackDetailBinding.replyFeedback.setEnabled(false);
            activityFeedbackDetailBinding.feedbackReplyEditText.setVisibility(View.INVISIBLE);
            navigateToFeedbackListFragment();
        }
    }

    private void navigateToFeedbackListFragment() {
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.putExtra("fragmentToOpen", "feedbackList");
        startActivity(intent);
        finish();
    }

    public void addReply(JsonObject obj, String id) {
        realm.executeTransactionAsync(realm -> {
            RealmFeedback feedback = realm.where(RealmFeedback.class).equalTo("id", id).findFirst();
            if (feedback != null) {
                Gson con = new Gson();
                JsonArray msgArray = con.fromJson(feedback.getMessages(), JsonArray.class);
                Log.e("Msg", new Gson().toJson(msgArray));
                msgArray.add(obj);
                feedback.setMessages(msgArray);
            }
        }, () -> {
            updateForClosed();
            mAdapter = new RvFeedbackAdapter(feedback.getMessageList(), getApplicationContext());
            activityFeedbackDetailBinding.rvFeedbackReply.setAdapter(mAdapter);
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) finish();
        return super.onOptionsItemSelected(item);
    }

    public class RvFeedbackAdapter extends RecyclerView.Adapter<RvFeedbackAdapter.ReplyViewHolder> {
        private RowFeedbackReplyBinding rowFeedbackReplyBinding;
        private List<FeedbackReply> replyList;
        Context context;

        public RvFeedbackAdapter(List<FeedbackReply> replyList, Context context) {
            this.replyList = replyList;
            this.context = context;
        }

        @NonNull
        @Override
        public ReplyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            rowFeedbackReplyBinding = RowFeedbackReplyBinding.inflate(LayoutInflater.from(context), parent, false);
            return new ReplyViewHolder(rowFeedbackReplyBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull ReplyViewHolder holder, int position) {
            rowFeedbackReplyBinding.tvDate.setText(TimeUtils.getFormatedDateWithTime(Long.parseLong(replyList.get(position).date)));
            rowFeedbackReplyBinding.tvUser.setText(replyList.get(position).user);
            rowFeedbackReplyBinding.tvMessage.setText(replyList.get(position).message);
        }

        @Override
        public int getItemCount() {
            return replyList.size();
        }

        public class ReplyViewHolder extends RecyclerView.ViewHolder {
            RowFeedbackReplyBinding rowFeedbackReplyBinding;

            public ReplyViewHolder(RowFeedbackReplyBinding rowFeedbackReplyBinding) {
                super(rowFeedbackReplyBinding.getRoot());
                this.rowFeedbackReplyBinding = rowFeedbackReplyBinding;
            }
        }
    }
}

