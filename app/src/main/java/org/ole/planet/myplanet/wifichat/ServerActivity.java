package org.ole.planet.myplanet.wifichat;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.ole.planet.myplanet.R;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;


public class ServerActivity extends AppCompatActivity {
    String u_name = "User";
    EditText message;
    ListView listView;
    Thread m_objThread;
    ServerSocket m_server;
    Socket connectedSocket;
    ArrayList<Message> messages = new ArrayList<>();
    CustomAdapter arrayAdapter;
    DatagramSocket ds;
    WifiManager wifi;
    WifiManager.MulticastLock multicastLock;

    ChatServer chatServer;

    // Used to load the 'native-lib' library on application startup.
//    static {
//        System.loadLibrary("native-lib");
//    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.server);
        message = (EditText) findViewById(R.id.enter_message);
        listView = (ListView) findViewById(R.id.message_list);
        arrayAdapter = new CustomAdapter(this, messages);
        listView.setAdapter(arrayAdapter);

        try {
            ds = new DatagramSocket(9001);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        chatServer = new ChatServer();
        Bundle extras = getIntent().getExtras();
        if(extras != null) {
            u_name = extras.getString("Name");
            chatServer.setServername(extras.getString("ServerName"));
        }

        wifi = (WifiManager) this.getApplicationContext().getSystemService(WIFI_SERVICE);
        multicastLock = wifi.createMulticastLock("multicastLock");
        multicastLock.setReferenceCounted(true);
        multicastLock.acquire();

        startListening();
        addMessage();
        listenForDiscoveryRequests();
        listenForConnectionRequests();
    }

    private void listenForConnectionRequests() {
        Thread connectionThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        byte[] conn_data = new byte[15000];
                        DatagramPacket dp = new DatagramPacket(conn_data, conn_data.length);
                        ds.receive(dp);

                        String msg = new String(dp.getData()).trim();
                        if(msg.equals("CONNECTION_REQUEST")) {
                            String replyMsg = ("CONNECTION_RESPONSE_ALLOW_"+
                                    chatServer.getSERVER_NAME());
                            Log.d("Reply", replyMsg);
                            byte[] send_data = replyMsg.getBytes();
                            DatagramPacket sendPacket = new DatagramPacket(send_data, send_data.length,
                            dp.getAddress(), dp.getPort());
                            ds.send(sendPacket);
                        }
                        synchronized (this) {
                            wait(1000);
                        }
                    }
                }
                catch (Exception e) {
                    Log.d("Eexception: ", e.toString());
                }
            }
        });

        connectionThread.start();
    }

    private void listenForDiscoveryRequests() {
        Thread requestThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        byte[] data = new byte[15000];
                        DatagramPacket dp = new DatagramPacket(data, data.length);
                        ds.receive(dp);
                        String msg = new String(dp.getData()).trim();
                        Log.d("Reply", "Message from: " + dp.getAddress() + "\nMessage: " + msg);

                        if(msg.equals("SERVER_DISCOVERY_REQUEST")) {
                            byte[] sendData = "SERVER_DISCOVERY_RESPONSE".getBytes();
                            DatagramPacket sdp = new DatagramPacket(sendData, sendData.length, dp.getAddress(), dp.getPort());
                            ds.send(sdp);
                            Log.d("Reply", "Message send to : " + dp.getAddress() + "\nMessage: " + msg);
                        }

                        synchronized (this) {
                            wait(500);
                        }
                    }
                }
                catch (Exception e) { }
            }
        });
        requestThread.start();
    }

    public void startListening() {
        m_objThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    m_server = new ServerSocket(8080);
                    connectedSocket = m_server.accept();
                }
                catch (Exception e) {
                  android.os.Message msg3 =android.os.Message.obtain();
                    msg3.obj = e.getMessage();

                }
            }
        });

        m_objThread.start();
    }

    public void addMessage() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    String msg = null;
                    try {
                        ObjectInputStream ois = new ObjectInputStream(connectedSocket.getInputStream());
                        msg = (String) ois.readObject();
                    } catch (Exception e) { }

                    if(msg != null) {
                      android.os. Message new_msg =android.os.Message.obtain();
                        new_msg.obj = msg;
                        mHandler.sendMessage(new_msg);
                        scrollMyListViewToBottom();
                    }
                    try {
                        synchronized (this) {
                            wait(100);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        thread.start();
    }

    public void sendServerMessage(View view) {
        String msg = message.getText().toString().trim();
        if(msg.length() > 0) {
            String timeStamp = new SimpleDateFormat("HH:mm").format(Calendar.getInstance().getTime());
            Message serverMessage = new Message(msg, true, timeStamp);

            try {
                ObjectOutputStream oos = new ObjectOutputStream(connectedSocket.getOutputStream());
                oos.writeObject(msg);

                messages.add(serverMessage);
                arrayAdapter.notifyDataSetChanged();
                scrollMyListViewToBottom();

            } catch (Exception e) {
                Toast.makeText(this, e.toString(), Toast.LENGTH_LONG);
            }
            message.setText("");
        }
    }

    public void clearText(View view) {
        message.setText("");
    }

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(android.os.Message msg) {
            String new_msg = msg.obj.toString();
            String timeStamp = new SimpleDateFormat("HH:mm").format(Calendar.getInstance().getTime());
            Message recvMessage = new Message(new_msg, false, timeStamp);

            messages.add(recvMessage);
            arrayAdapter.notifyDataSetChanged();
            scrollMyListViewToBottom();
        }
    };

    private void scrollMyListViewToBottom() {
        listView.post(new Runnable() {
            @Override
            public void run() {
                // Select the last row so it will scroll into view...
                listView.setSelection(arrayAdapter.getCount() - 1);
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ds.close();
        try {
            connectedSocket.close();
            m_server.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (multicastLock != null) {
            multicastLock.release();
            multicastLock = null;
        }
    }
}