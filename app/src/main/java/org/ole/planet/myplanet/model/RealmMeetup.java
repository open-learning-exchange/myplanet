package org.ole.planet.myplanet.model;

import android.text.TextUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.json.JSONArray;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.annotations.PrimaryKey;

public class RealmMeetup extends RealmObject {
    @PrimaryKey
    private String id;
    private String userId;
    private String meetupId;
    private String meetupId_rev;
    private String title;
    private String description;
    private long startDate;
    private long endDate;
    private String recurring;
    private String Day;
    private String startTime;
    private String endTime;
    private String category;
    private String meetupLocation;
    private String creator;
    private String links;
    private String teamId;

    public static void insert(Realm mRealm, JsonObject meetupDoc) {
        insert("", meetupDoc, mRealm);
    }

    public static void insert(String userId, JsonObject meetupDoc, Realm mRealm) {
        Utilities.log("INSERT MEETUP " + meetupDoc);
        RealmMeetup myMeetupsDB = mRealm.where(RealmMeetup.class).equalTo("id", JsonUtils.getString("_id", meetupDoc)).findFirst();
        if (myMeetupsDB == null) {
            myMeetupsDB = mRealm.createObject(RealmMeetup.class, JsonUtils.getString("_id", meetupDoc));
        }
        myMeetupsDB.setMeetupId(JsonUtils.getString("_id", meetupDoc));
        myMeetupsDB.setUserId(userId);
        myMeetupsDB.setMeetupId_rev(JsonUtils.getString("_rev", meetupDoc));
        myMeetupsDB.setTitle(JsonUtils.getString("title", meetupDoc));
        myMeetupsDB.setDescription(JsonUtils.getString("description", meetupDoc));
        myMeetupsDB.setStartDate(JsonUtils.getLong("startDate", meetupDoc));
        myMeetupsDB.setEndDate(JsonUtils.getLong("endDate", meetupDoc));
        myMeetupsDB.setRecurring(JsonUtils.getString("recurring", meetupDoc));
        myMeetupsDB.setStartTime(JsonUtils.getString("startTime", meetupDoc));
        myMeetupsDB.setEndTime(JsonUtils.getString("endTime", meetupDoc));
        myMeetupsDB.setCategory(JsonUtils.getString("category", meetupDoc));
        myMeetupsDB.setMeetupLocation(JsonUtils.getString("meetupLocation", meetupDoc));
        myMeetupsDB.setCreator(JsonUtils.getString("creator", meetupDoc));
        myMeetupsDB.setDay(JsonUtils.getJsonArray("day", meetupDoc).toString());
        myMeetupsDB.setLinks(JsonUtils.getJsonObject("links", meetupDoc).toString());
        myMeetupsDB.setTeamId(JsonUtils.getString("teams", JsonUtils.getJsonObject("links", meetupDoc)));
    }

    public static void insertMyMeetups(String userId, JsonObject resourceDoc, Realm mRealm) {

    }


    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getLinks() {
        return links;
    }

    public void setLinks(String links) {
        this.links = links;
    }

    public static JsonArray getMyMeetUpIds(Realm realm, String userId) {
        RealmResults<RealmMeetup> meetups = realm.where(RealmMeetup.class).isNotEmpty("userId")
                .equalTo("userId", userId, Case.INSENSITIVE).findAll();

        JsonArray ids = new JsonArray();
        for (RealmMeetup lib : meetups
        ) {
            ids.add(lib.getMeetupId());
        }
        return ids;
    }


    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getMeetupId() {
        return meetupId;
    }

    public void setMeetupId(String meetupId) {
        this.meetupId = meetupId;
    }

    public String getMeetupId_rev() {
        return meetupId_rev;
    }

    public void setMeetupId_rev(String meetupId_rev) {
        this.meetupId_rev = meetupId_rev;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getStartDate() {
        return startDate;
    }

    public void setStartDate(long startDate) {
        this.startDate = startDate;
    }

    public long getEndDate() {
        return endDate;
    }

    public void setEndDate(long endDate) {
        this.endDate = endDate;
    }

    public String getRecurring() {
        return recurring;
    }

    public void setRecurring(String recurring) {
        this.recurring = recurring;
    }

    public String getDay() {
        return Day;
    }

    public void setDay(String day) {
        Day = day;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getMeetupLocation() {
        return meetupLocation;
    }

    public void setMeetupLocation(String meetupLocation) {
        this.meetupLocation = meetupLocation;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public static HashMap<String, String> getHashMap(RealmMeetup meetups) {
        HashMap<String, String> map = new HashMap<>();
        map.put("Meetup Title", checkNull(meetups.getTitle()));
        map.put("Created By", checkNull(meetups.getCreator()));
        map.put("Category", checkNull(meetups.getCategory()));
        try {
            map.put("Meetup Date", TimeUtils.getFormatedDate((meetups.getStartDate())) + " - " + TimeUtils.getFormatedDate((meetups.getEndDate())));
        } catch (Exception e) {
        }
        map.put("Meetup Time", checkNull(meetups.getStartTime()) + " - " + checkNull(meetups.getEndTime()));
        map.put("Recurring", checkNull(meetups.getRecurring()));

        String recurringDays = "";
        try {
            JSONArray ar = new JSONArray(meetups.getDay());
            for (int i = 0; i < ar.length(); i++) {
                recurringDays += ar.get(i).toString() + ", ";
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        map.put("Recurring Days", checkNull(recurringDays));
        map.put("Location", checkNull(meetups.getMeetupLocation()));
        map.put("Description", checkNull(meetups.getDescription()));
        return map;
    }

    public static String[] getJoinedUserIds(Realm mRealm) {
        List<RealmMeetup> list = mRealm.where(RealmMeetup.class).isNotEmpty("userId").findAll();
        String[] myIds = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            myIds[i] = list.get(i).getUserId();
        }
        return myIds;
    }


    public static String checkNull(String s) {
        return TextUtils.isEmpty(s) ? "" : s;
    }
}
