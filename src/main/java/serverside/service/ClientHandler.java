package serverside.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import serverside.model.User;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class ClientHandler {
    private final MyServer myServer;
    private final Socket socket;
    private final DataInputStream dis;
    private final DataOutputStream dos;
    private volatile boolean isConnected;

    private User user;

    private static final int timeForAuthenticationInSecond = 120;
    private static final int timeForReadMessageFromClientInSeconds = 180;
    private volatile long timeLastReadedMessage;

    // команды от клиента
    private static final String AUTH = "/auth";              // аутентификация /auth login password
    private static final String CHANGE_NICK = "/chnick";     // сменить ник
    private static final String END = "/end";                // отключить соединение
    private static final String SEND_PRIVATE_MESSAGE = "/w"; // отправить личное сообщение /w nick message
    private static final String LIST = "/list";              // получить список онлайн пользователей
    private static final String NOTIFY = "/notify ";      // уведомление

    // результаты выполнения команд от клиента
    private static final String AUTH_OK = "/authok ";                  // успешная авторизация
    private static final String CHANGE_NICK_OK = "/chnickok ";         // успешная смена ника
    private static final String TIMEOUT_AUTH = "/timeoutauth";         // время для авторизации истекло
    private static final String TIMEOUT_ACTIVITY = "/timeoutactivity"; // длительный простой
    private static final String ERROR_CHANGE_NICK = "/errchnick ";     // ошибка при смене ника
    private static final String ERROR_DB_CONNECTION = "/errdbcon ";    // соединение с базой данных отсутствует

    // запросы в базу данных
    private static final String CHANGE_NICK_QUERY = "UPDATE users SET nick=? WHERE nick=?"; // запрос на смену ника

    private static final Logger LOGGER = LogManager.getLogger(ClientHandler.class);

    public ClientHandler(MyServer myServer, Socket socket) {
        try {
            this.myServer = myServer;
            this.socket = socket;
            this.dis = new DataInputStream(socket.getInputStream());
            this.dos = new DataOutputStream(socket.getOutputStream());
            isConnected = true;
            this.user = null;
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            executorService.execute(clientHandlerThread());
            executorService.shutdown();
        } catch(IOException ex) {
            closeConnection();
            LOGGER.error("Проблемы при создании ClientHandler: " + ex.getMessage());
            throw new RuntimeException("Проблемы при создании ClientHandler");
        }
    }

    /**
     * Метод обработчика клиента для выполнения в отдельном потоке.
     * Порядок выполнения метода:
     * 1. Создается executor c 2 потоками в пуле. В одном потоке запускается таймер, в течение которого должен
     *    авторизоваться клиент. Во втором потоке запускается цикл чтения данных авторизации от клиента.
     *    Результатом выполнения двух потоков является значение типа boolean - клиент авторизовался или нет.
     *    Чтобы это отследить этот результат вызывается invokeAny, т.к. какой то из потоков должен завершиться
     *    первым. Как только один из потоков вернет значение типа Boolean (авторизовался или нет), executor
     *    прерывает выполнение второго потока. Если клиент не авторизовался, соединение закрывается и происходит
     *    возврат из метода.
     * 2. Если клиент авторизовался запускается на выполнение тот же executor с двумя потоками в пуле. В одном
     *    потоке запускается таймер на отслеживание активности от клиента, во втором потоке запускается цикл
     *    чтения сообщений от клиента. Результат выполнения этих потоков не отслеживается, поэтому эти две
     *    задачи запускаются на выполнение вызовом execute, вызывается shutdown (сообщаем, что других задач не будет) и
     *    базовый поток завершает выполнение.
     * @return объект, реулизующий Runnable для запуска в отдельном потоке
     */
    private Runnable clientHandlerThread() {
        return () -> {
            ExecutorService executor = Executors.newFixedThreadPool(2);
            List<Callable<Boolean>> authTasks = new ArrayList<>();
            authTasks.add(AuthenticationTimer());
            authTasks.add(authentication());
            Boolean isAuthorized = null;
            try {
                isAuthorized = executor.invokeAny(authTasks);
            } catch (InterruptedException | ExecutionException ignored) {

            }
            if(isAuthorized == null || !isAuthorized) {
                executor.shutdown();
                closeConnection();
                return;
            }
            myServer.broadcastMessage(NOTIFY + user.getNick() + " вошел в чат");
            myServer.subscribe(this);
            long timeInMillis = timeForReadMessageFromClientInSeconds * 1000;
            timeLastReadedMessage = System.currentTimeMillis();
            executor.execute(readMessagesTimer(timeInMillis));
            executor.execute(readMessagesFromClient());
            executor.shutdown();
        };
    }

    /**
     * Таймер аутентификации
     * @return - объект, реализующий Callable<Boolean> для запуска в отдельном потоке
     */
    private Callable<Boolean> AuthenticationTimer() {
        return () -> {
            try {
                Thread.sleep(timeForAuthenticationInSecond * 1000);
            } catch (InterruptedException ignored) {
                return false;
            }
            sendMessage(TIMEOUT_AUTH);
            return false;
        };
    }

    /**
     * Цикл чтения данных аутентификации
     * @return - объект, реализующий Callable<Boolean> для запуска в отдельном потоке
     */
    private Callable<Boolean> authentication() {
        return () -> {
            try {
                while (true) {
                    String str = dis.readUTF();
                    if (str.startsWith(AUTH)) {
                        String[] arr = str.split("\\s");
                        String login = arr[1];
                        User user = myServer
                                .getAuthService()
                                .getUserByLoginAndPassword(arr[1], arr[2]);
                        if (user != null) {
                            if (!myServer.isUserBusy(user)) {
                                sendMessage(AUTH_OK + user.getNick() + " " + login);
                                LOGGER.info("Пользователь с логином " + login + " и ником " + user.getNick() + " вошел в чат");
                                this.user = user;
                                return true;
                            } else {
                                sendMessage("Пользователь с данным логином и паролем уже в чате");
                            }
                        } else {
                            sendMessage("Неправильный логин или пароль");
                        }
                    }
                }
            } catch (SQLException ex) {
                LOGGER.error("Ошибка при авторизации: " + ex.getMessage());
                sendMessage(ERROR_DB_CONNECTION + "Соединение с базой данных отсутствует");
            }
            return false;
        };
    }
    
    private Runnable readMessagesTimer(long timeInMillis) {
        return () -> {
            try {
                while(true) {
                    Thread.sleep(1);
                    if(System.currentTimeMillis() - timeLastReadedMessage >= timeInMillis) {
                        LOGGER.info("Пользователь с ником " + user.getNick() + " в течение "
                                + timeForReadMessageFromClientInSeconds + " секунд не проявил активность");
                        sendMessage(TIMEOUT_ACTIVITY);
                        closeConnection();
                        break;
                    }
                }
            } catch (InterruptedException ignored) {

            }
        };
    }

    private Runnable readMessagesFromClient() {
        return () -> {
            try {
                readMessages();
            } catch (IOException ex) {
                LOGGER.error("Ошибка при чтении сообщения от клиента: " + ex.getMessage());
            } finally {
                closeConnection();
            }
        };
    }

    /**
     * Цикл чтения сообщений от клиента после успешной аутентификации
     * @throws IOException, если какие то неполадки во время чтения сообщения от клиента
     */
    public void readMessages() throws IOException {
        while(true) {
            String messageFromClient = dis.readUTF();
            timeLastReadedMessage = System.currentTimeMillis();
            if(isServiceMessage(messageFromClient)) {
                String trimedMessage = messageFromClient.trim();
                if(isEndSessionCommand(trimedMessage)) {
                    return;
                }
                handleServiceMessage(trimedMessage);
                continue;
            }
            LOGGER.info("Пользователь с ником " + user.getNick() + " прислал сообщение в общий чат: " + messageFromClient);
            myServer.broadcastMessage("[" + user.getNick() + "]: " + messageFromClient);
        }
    }

    /**
     * Метод, определяющий является ли сообщение от клиента служебным сообщением
     * @param message - сообщение от клиента
     * @return true - если сообщение от клиента является служебным сообщением, false - в противном случае
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
            if(!this.user.getNick().equals(arr[1])) {
                LOGGER.info("Пользователь с ником " + user.getNick() +
                        " прислал личное сообщение пользователю с ником " + arr[1] +
                        ": " + arr[2]);
                myServer.sendPrivateMessage(this, arr[1], arr[2]);
            }
        }
        if(message.startsWith(LIST)) {
            LOGGER.info("Пользователь с ником " + user.getNick() + " запросил список онлайн-клиентов");
            myServer.getOnlineUsersList(this);
        }
        if(message.startsWith(CHANGE_NICK)) {
            String oldNick = user.getNick();
            String newNick = message.substring(CHANGE_NICK.length() + 1);
            LOGGER.info("Пользователь с ником " + user.getNick() + " прислал запрос на смену ника на " + newNick);
            try (PreparedStatement statement = DBConnection.getConnection().prepareStatement(CHANGE_NICK_QUERY)) {
                statement.setString(1, newNick);
                statement.setString(2, user.getNick());
                if (statement.executeUpdate() > 0) {
                    user.setNick(newNick);
                    LOGGER.info("Пользователь с ником " + user.getNick() + " поменял ник на " + newNick);
                    sendMessage(CHANGE_NICK_OK + newNick);
                    myServer.broadcastMessage(NOTIFY + "[" + oldNick + " сменил ник на " + newNick + "]");
                }
            } catch (SQLIntegrityConstraintViolationException ex) {
                sendMessage(ERROR_CHANGE_NICK + "Пользователь с данным ником уже существует");
            } catch (SQLException ex) {
                LOGGER.error("Проблемы с базой данных при попытке смены ника c " + oldNick + " на " + newNick + ": " + ex.getMessage());
                sendMessage(ERROR_DB_CONNECTION + "Проблемы с базой данных при попытке смены ника");
            }
        }
    }

    public void sendMessage(String message) {
        try {
            dos.writeUTF(message);
        } catch (IOException ex) {
            LOGGER.error("Ошибка при отправке пользователю с ником " + user.getNick() +
                    " сообщения: " + message + ": " + ex.getMessage());
            closeConnection();
        }
    }

    private void closeConnection() {
        if(isConnected) {
            isConnected = false;
            if(user != null) {
                LOGGER.info("Пользователь с ником " + user.getNick() + " покинул чат");
                myServer.unsubscribe(this);
                myServer.broadcastMessage(NOTIFY + user.getNick() + " покинул чат");
            }
            try {
                dis.close();
            } catch (IOException ex) {
                LOGGER.error("Ошибка при закрытии DataInputStream: " + ex.getMessage());
            }
            try {
                dos.close();
            } catch (IOException ex) {
                LOGGER.error("Ошибка при закрытии DataOutputStream: " + ex.getMessage());
            }
        }
    }

    public User getUser() {
        return user;
    }
}
