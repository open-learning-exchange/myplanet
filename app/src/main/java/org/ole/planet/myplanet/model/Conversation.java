package org.ole.planet.myplanet.model;

import io.realm.RealmObject;

public class Conversation extends RealmObject {
    private String query;
    private String response;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }
}
