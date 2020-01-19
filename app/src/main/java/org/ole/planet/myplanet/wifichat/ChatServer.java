package org.ole.planet.myplanet.wifichat;

import java.net.InetAddress;
import java.util.ArrayList;

/**
 * Created by rajeev on 16/3/17.
 */

public class ChatServer {

    private String SERVER_NAME;
    private int CAPACITY;
    private int CLIENTS;

    private ArrayList<InetAddress> clients;

    public void ChatServer() {
        SERVER_NAME = "CHAT_SERVER";
        CAPACITY = 50;
        CLIENTS = 0;
        clients = new ArrayList<>();
    }

    public void ChatServer(String name, int capacity) {
        SERVER_NAME = name;
        CAPACITY = capacity;
        CLIENTS = 0;
        clients = new ArrayList<>();
    }

    public void setServername(String name) {
        SERVER_NAME = name;
    }

    public void setCAPACITY(int n) {
        CAPACITY = n;
    }

    public boolean isFull() {
        if(CLIENTS >= CAPACITY)
            return true;

        return false;
    }

    public void addClient(InetAddress ip) {
        if(!isFull() && !clients.contains(ip)) {
            clients.add(ip);
            CLIENTS++;
        }
    }

    public void removeClient(InetAddress ip) {
        if(clients.contains(ip)) {
            clients.remove(ip);
            CLIENTS--;
        }
    }

    public String getSERVER_NAME() {
        return SERVER_NAME;
    }
}