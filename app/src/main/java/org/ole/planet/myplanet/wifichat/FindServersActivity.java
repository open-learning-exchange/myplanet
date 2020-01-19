package org.ole.planet.myplanet.wifichat;

import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.*;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import org.ole.planet.myplanet.R;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class FindServersActivity extends AppCompatActivity {

    ListView serverList;
    List<String> servers;
    WifiManager wifi;
    String u_name;
    WifiManager.MulticastLock multicastLock;
    ArrayAdapter<String> arrayAdapter;
    DatagramSocket ds;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find_servers);
        serverList = (ListView) findViewById(R.id.server_list);

        servers = new ArrayList<String>();
        arrayAdapter = new ArrayAdapter<String>(this, R.layout.custom_server_row, servers);
        serverList.setAdapter(arrayAdapter);

        Bundle extras = getIntent().getExtras();
        if(extras != null) {
            u_name = extras.getString("Name");
        }

        serverList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent i = new Intent(getApplicationContext(), ClientActivity.class);
                String ip = String.valueOf(parent.getItemAtPosition(position));
                i.putExtra("IP", ip);
                i.putExtra("Name", u_name);
                startActivity(i);
            }
        });

        try {
            ds = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    public InetAddress getIpAddress() {
        InetAddress inetAddress = null;
        InetAddress myAddr = null;

        try {
            for (Enumeration < NetworkInterface > networkInterface = NetworkInterface
                    .getNetworkInterfaces(); networkInterface.hasMoreElements();) {

                NetworkInterface singleInterface = networkInterface.nextElement();

                for (Enumeration< InetAddress > IpAddresses = singleInterface.getInetAddresses(); IpAddresses
                        .hasMoreElements();) {
                    inetAddress = IpAddresses.nextElement();

                    if (!inetAddress.isLoopbackAddress() && (singleInterface.getDisplayName()
                            .contains("wlan0") ||
                            singleInterface.getDisplayName().contains("eth0") ||
                            singleInterface.getDisplayName().contains("ap0"))) {

                        myAddr = inetAddress;
                    }
                }
            }

        } catch (SocketException ex) {
            Log.e("Reply", ex.toString());
        }
        return myAddr;
    }

    public InetAddress getBroadcast(InetAddress inetAddr) {

        NetworkInterface temp;
        InetAddress iAddr = null;
        try {
            temp = NetworkInterface.getByInetAddress(inetAddr);
            List < InterfaceAddress > addresses = temp.getInterfaceAddresses();

            for (InterfaceAddress inetAddress: addresses)

                iAddr = inetAddress.getBroadcast();
            Log.d("Reply", "iAddr=" + iAddr);
            return iAddr;

        } catch (SocketException e) {

            e.printStackTrace();
            Log.d("Reply", "getBroadcast" + e.getMessage());
        }
        return null;
    }

    public void scanForServers(View view) {
        wifi = (WifiManager) this.getApplicationContext().getSystemService(WIFI_SERVICE);
        multicastLock = wifi.createMulticastLock("multicastLock");
        multicastLock.setReferenceCounted(true);
        multicastLock.acquire();

        Thread scan = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (true) {
                            byte[] data = "SERVER_DISCOVERY_REQUEST".getBytes();
                            DatagramPacket dp = new DatagramPacket(data, data.length,
                                    getBroadcast(getIpAddress()), 9001);
                            Log.d("Reply", getBroadcast(getIpAddress()).toString());
                            ds.send(dp);

                            byte[] rdata = new byte[15000];
                            DatagramPacket rdp = new DatagramPacket(rdata, rdata.length);
                            ds.receive(rdp);
                            String recvmsg = (new String(rdp.getData())).trim();

                            Log.d("Reply", "Message from: " + rdp.getAddress() + "\nMessage: "+recvmsg);

                            if(recvmsg.equals("SERVER_DISCOVERY_RESPONSE")) {

                                byte[] connetionMsg = "CONNECTION_REQUEST".getBytes();
                                DatagramPacket conn_packet = new DatagramPacket(connetionMsg, connetionMsg.length,
                                        rdp.getAddress(), rdp.getPort());
                                ds.send(conn_packet);

                                byte[] connRecvMsg = new  byte[15000];
                                DatagramPacket conn_recv_packet = new DatagramPacket(connRecvMsg, connetionMsg.length);
                                ds.receive(conn_recv_packet);

                                String conn_reply_msg = (new String(conn_recv_packet.getData())).trim();
                                Log.d("Reply", "Message from: " + conn_recv_packet.getAddress() +
                                        "\nMessage: "+conn_reply_msg);

                                if(conn_reply_msg.startsWith("CONNECTION_RESPONS")) {
                                    String server_name = conn_recv_packet.getAddress().toString();
                                    Message new_message = Message.obtain();
                                    new_message.obj = server_name;
                                    handler.sendMessage(new_message);
                                }
                            }

                            synchronized (this) {
                                wait(1000);
                            }
                        }
                    }
                    catch(Exception e){
                        Log.d("Error", e.toString());
                    }
                }
            });
        scan.start();
    }

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            String ip = msg.obj.toString().substring(1);
            if (!servers.contains(ip)) {
                servers.add(0, ip);
                arrayAdapter.notifyDataSetChanged();
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ds.close();
        if (multicastLock != null) {
            multicastLock.release();
            multicastLock = null;
        }
    }
}
