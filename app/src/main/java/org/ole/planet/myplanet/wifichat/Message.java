package org.ole.planet.myplanet.wifichat;

/**
 * Created by rajeev on 13/3/17.
 */

public class Message {

    private String message_text;
    public boolean sender = true;
    private String time;

    public Message(String msg, boolean s, String t) {
        message_text = msg;
        sender = s;
        time = t;
    }

    public String getMessage() {
        return message_text;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String arrival_time) {
        time = arrival_time;
    }
}
