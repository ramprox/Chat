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

    private static final int timeForAuthenticationInSecond = 120;
    private static final int timeForReadMessageFromClientInSeconds = 180;
    private volatile boolean isAuthorized = false;
    private volatile long timeLastReadedMessage;

    public ClientHandler(MyServer myServer, Socket socket) {
        try {
            this.myServer = myServer;
            this.socket = socket;
            this.dis = new DataInputStream(socket.getInputStream());
            this.dos = new DataOutputStream(socket.getOutputStream());
            this.name = "";

            new Thread(() -> {
                try {
                    Thread.sleep(timeForAuthenticationInSecond * 1000);
                    if(!isAuthorized) {
                        closeConnection();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();

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
                        isAuthorized = true;
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
        long timeInMillis = timeForReadMessageFromClientInSeconds * 1000;
        timeLastReadedMessage = System.currentTimeMillis();
        new Thread(() -> {
            try {
                while(true) {
                    Thread.sleep(1);
                    if(System.currentTimeMillis() - timeLastReadedMessage >= timeInMillis) {
                        closeConnection();
                        break;
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
        while(true) {
            String messageFromClient = dis.readUTF();
            timeLastReadedMessage = System.currentTimeMillis();
            System.out.println(name + " send message " + messageFromClient);
            if(messageFromClient.trim().startsWith("/")) {
                if(messageFromClient.trim().startsWith("/end")) {
                    return;
                }
                if(messageFromClient.startsWith("/w")) {
                    String[] arr = messageFromClient.split("\\s", 3);
                    if(this.name.equals(arr[1])) {
                        continue;
                    }
                    myServer.sendPrivateMessage(this, arr[1], arr[2]);
                    continue;
                }
                if(messageFromClient.trim().startsWith("/list")) {
                    myServer.getOnlineUsersList(this);
                    continue;
                }
            }
            myServer.broadcastMessage("[" + name + "]: " + messageFromClient);
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
