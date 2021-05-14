package serverside.service;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class ClientHandler {
    private MyServer myServer;
    private Socket socket;
    private DataInputStream dis;
    private DataOutputStream dos;

    private String name;

    public ClientHandler(MyServer myServer, Socket socket) {
        try {
            this.myServer = myServer;
            this.socket = socket;
            this.dis = new DataInputStream(socket.getInputStream());
            this.dos = new DataOutputStream(socket.getOutputStream());
            this.name = "";

            new Thread(() -> {
                try {
                    authentication();
                    readMessage();
                } catch (IOException ignored) {

                } finally {
                    closeConnection();
                }
            }).start();
        } catch(IOException e) {
            closeConnection();
            throw new RuntimeException("Проблемы при создании ClientHandler");
        }
    }

    public void authentication() throws IOException {
        while(true) {
            String str = dis.readUTF();
            if(str.startsWith("/auth")) {  // /auth login password
                String[] arr = str.split("\\s");
                String nick = myServer
                        .getAuthService()
                        .getNickByLoginAndPassword(arr[1], arr[2]);
                if(nick != null) {
                    if(!myServer.isNickBusy(nick)) {
                        sendMessage("/authok " + nick);
                        name = nick;
                        myServer.broadcastMessage(name + " вошел в чат");
                        myServer.subscribe(this);
                        return;
                    } else {
                        sendMessage("Ник занят");
                    }
                } else {
                    sendMessage("Неправильный логин или пароль");
                }
            }
        }
    }

    public void readMessage() throws IOException {
        while(true) {
            String messageFromClient = dis.readUTF();
            System.out.println(name + " send message " + messageFromClient);
            if(messageFromClient.equals("/end")) {
                return;
            }
            if(messageFromClient.startsWith("/w")) {
                String[] arr = messageFromClient.split("\\s", 3);
                String message = messageFromClient.substring(arr[0].length() + arr[1].length() + 2);
                myServer.sendPrivateMessage(this, arr[1], message);
            } else {
                myServer.broadcastMessage("[" + name + "]: " + messageFromClient);
            }
        }
    }

    public void sendMessage(String message) {
        try {
            dos.writeUTF(message);
        } catch (IOException ignored) {

        }
    }

    private void closeConnection() {
        myServer.unsubscribe(this);
        myServer.broadcastMessage(name + " покинул чат");
        try {
            dis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            dos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getName() {
        return name;
    }
}
