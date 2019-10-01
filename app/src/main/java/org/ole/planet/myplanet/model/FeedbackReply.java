package org.ole.planet.myplanet.model;

public class FeedbackReply {
    String message,user,date;

    public FeedbackReply(String message, String user, String date) {
        this.message = message;
        this.user = user;
        this.date = date;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }
}
