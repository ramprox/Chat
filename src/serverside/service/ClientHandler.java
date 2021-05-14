package serverside.service;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.sql.*;

public class ClientHandler {
    private final MyServer myServer;
    private final Socket socket;
    private final DataInputStream dis;
    private final DataOutputStream dos;

    private String name;

    private static final int timeForAuthenticationInSecond = 120;
    private static final int timeForReadMessageFromClientInSeconds = 180;
    private volatile boolean isAuthorized = false;
    private volatile long timeLastReadedMessage;

    // команды от клиента
    private static final String AUTH = "/auth";              // аутентификация /auth login password
    private static final String CHANGE_NICK = "/chnick";     // сменить ник
    private static final String END = "/end";                // отключить соединение
    private static final String SEND_PRIVATE_MESSAGE = "/w"; // отправить личное сообщение /w nick message
    private static final String LIST = "/list";              // получить список онлайн пользователей

    // результаты выполнения команд от клиента
    private static final String AUTH_OK = "/authok ";               // успешная авторизация
    private static final String CHANGE_NICK_OK = "/chnickok ";      // успешная смена ника
    private static final String ERROR_CHANGE_NICK = "/errchnick ";  // ошибка при смене ника
    private static final String ERROR_DB_CONNECTION = "/errdbcon "; // соединение с базой данных отсутствует

    // запросы в базу данных
    private static final String CHANGE_NICK_QUERY = "UPDATE users SET nick=? WHERE nick=?";         // запрос на смену ника

    public ClientHandler(MyServer myServer, Socket socket) {
        try {
            this.myServer = myServer;
            this.socket = socket;
            this.dis = new DataInputStream(socket.getInputStream());
            this.dos = new DataOutputStream(socket.getOutputStream());
            this.name = "";
            startAuthenticationTimer();
            startReadMessagesFromClient();
        } catch(IOException e) {
            closeConnection();
            throw new RuntimeException("Проблемы при создании ClientHandler");
        }
    }
    
    private void startAuthenticationTimer() {
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
    }
    
    private void startReadMessageFromClientTimer(long timeInMillis) {
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
    }

    private void startReadMessagesFromClient() {
        new Thread(() -> {
            try {
                authentication();
                readMessage();
            } catch (IOException ignored) {

            } finally {
                closeConnection();
            }
        }).start();
    }

    /**
     * Цикл аутентификации
     * @throws IOException, если какие то неполадки во время чтения сообщения от клиента
     * @throws SQLException, если какие то неполадки по время обращения к базе данных
     */
    public void authentication() throws IOException {
        try {
            while (true) {
                String str = dis.readUTF();
                if (str.startsWith(AUTH)) {
                    String[] arr = str.split("\\s");
                    String nick = myServer
                            .getAuthService()
                            .getNickByLoginAndPassword(arr[1], arr[2]);
                    if (nick != null) {
                        if (!myServer.isNickBusy(nick)) {
                            isAuthorized = true;
                            sendMessage(AUTH_OK + nick);
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
        } catch (SQLException ex) {
            sendMessage(ERROR_DB_CONNECTION + "Соединение с базой данных отсутствует");
            closeConnection();
        }
    }

    /**
     * Цикл чтения сообщений от клиента после успешной аутентификации
     * @throws IOException, если какие то неполадки во время чтения сообщения от клиента
     */
    public void readMessage() throws IOException {
        long timeInMillis = timeForReadMessageFromClientInSeconds * 1000;
        timeLastReadedMessage = System.currentTimeMillis();
        startReadMessageFromClientTimer(timeInMillis);
        while(true) {
            String messageFromClient = dis.readUTF();
            timeLastReadedMessage = System.currentTimeMillis();
            System.out.println(name + " send message " + messageFromClient);
            if(isServiceMessage(messageFromClient)) {
                String trimedMessage = messageFromClient.trim();
                if(isEndSessionCommand(trimedMessage)) {
                    return;
                }
                handleServiceMessage(trimedMessage);
                continue;
            }
            myServer.broadcastMessage("[" + name + "]: " + messageFromClient);
        }
    }

    /**
     * Метод, определяющий является ли сообщение от клиента служебным сообщением
     * @param message - сообщение от клиента
     * @return true - если сообщение от клиента является служебным, false - в противном случае
     */
    private boolean isServiceMessage(String message) {
        return message.trim().startsWith("/");
    }

    /**
     * Метод, определяющий является ли служебное сообщение от клиента командой завершения сессии
     * @param message - служебное сообщение от клиента
     * @return true - если сообщение от клиента является командой завершения сессии, false - в противном случае
     */
    private boolean isEndSessionCommand(String message) {
        return message.startsWith(END);
    }

    /**
     * Метод обработки служебных сообщений (команд) от клиента
     * @param message - служебное сообщение (команда) от клиента
     */
    private void handleServiceMessage(String message) {
        if(message.startsWith(SEND_PRIVATE_MESSAGE)) {
            String[] arr = message.split("\\s", 3);
            if(!this.name.equals(arr[1])) {
                myServer.sendPrivateMessage(this, arr[1], arr[2]);
            }
        }
        if(message.startsWith(LIST)) {
            myServer.getOnlineUsersList(this);
        }
        if(message.startsWith(CHANGE_NICK)) {
            String oldNick = name;
            String newNick = message.substring(CHANGE_NICK.length() + 1);
            try (PreparedStatement statement = DBConnection.getConnection().prepareStatement(CHANGE_NICK_QUERY)) {
                statement.setString(1, newNick);
                statement.setString(2, name);
                if (statement.executeUpdate() > 0) {
                    name = newNick;
                    sendMessage(CHANGE_NICK_OK + newNick);
                    myServer.broadcastMessage("[" + oldNick + " сменил ник на " + newNick + "]");
                }
            } catch (SQLIntegrityConstraintViolationException ex) {
                sendMessage(ERROR_CHANGE_NICK + "Пользователь с данным ником уже существует");
            } catch (SQLException ex) {
                sendMessage(ERROR_DB_CONNECTION + "Соединение с базой данных отсутствует");
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
