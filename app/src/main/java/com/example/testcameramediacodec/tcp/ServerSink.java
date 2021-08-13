package com.example.testcameramediacodec.tcp;

import android.util.Log;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

public class ServerSink {
    private static final String TAG = "ServerSink";
    private ArrayList<ConnectionToClient> clientList;
    private LinkedBlockingQueue<Object> messages;
    private ServerSocket serverSocket;

    public ServerSink(int port) throws IOException {
        clientList = new ArrayList<ConnectionToClient>();
        messages = new LinkedBlockingQueue<Object>();
        serverSocket = new ServerSocket(port);

        Thread accept = new Thread(() -> {
            while(true){
                try{
                    Socket s = serverSocket.accept();
                    clientList.add(new ConnectionToClient(s));
                    Log.d(TAG, "ServerSink: new Client add!!");
                }
                catch(IOException e){ e.printStackTrace(); }
            }
        });

        accept.setDaemon(true);
        accept.start();

        Thread messageHandling = new Thread(() -> {
            while(true){
                try{
                    Object message = messages.take();
                    // Do some handling here...
                    Log.d(TAG, "ServerSink: Message Received: " + message);
                }
                catch(InterruptedException ignored){ }
            }
        });

        messageHandling.setDaemon(true);
        messageHandling.start();
    }

    private class ConnectionToClient {
        ObjectInputStream in;
        ObjectOutputStream out;
        Socket socket;

        ConnectionToClient(Socket socket) throws IOException {
            this.socket = socket;
            in = new ObjectInputStream(socket.getInputStream());
            out = new ObjectOutputStream(socket.getOutputStream());

            Thread read = new Thread(() -> {
                while(true){
                    try{
                        Object obj = in.readObject();
                        messages.put(obj);
                    }
                    catch(IOException | InterruptedException | ClassNotFoundException e){ e.printStackTrace(); }
                }
            });

            read.setDaemon(true); // terminate when main ends
            read.start();
        }

        public void write(Object obj) {
            try{
                out.writeObject(obj);
            }
            catch(IOException e){ e.printStackTrace(); }
        }
    }

    public void sendToOne(int index, Object message)throws IndexOutOfBoundsException {
        clientList.get(index).write(message);
    }

    public void sendToAll(Object message){
        for(ConnectionToClient client : clientList)
            client.write(message);
    }
}
