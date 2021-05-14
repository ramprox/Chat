package serverside.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import serverside.interfaces.AuthService;
import serverside.model.User;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.sql.*;

/**
 * класс Сервер
 */
public class MyServer {

    private final int PORT = 8081;
    private List<ClientHandler> clients;
    private AuthService authService;

    private static final String ERR_SPM = "/errorSPM ";   // ошибка при отправке личного сообщения
    private static final String CLIENTS = "/clients ";    // список онлайн клиентов

    private static final Logger LOGGER = LogManager.getLogger(MyServer.class);

    public AuthService getAuthService() {
        return authService;
    }

    /**
     * Конструктор, запускающий сервер
     * Последовательность запуска сервера:
     * 1. Создание ServerSocket с портом PORT.
     * 2. Старт сервиса аутентификации
     * 3. Ожидание подключения от клиента
     * 4. После подключения клиента, создается ClientHandler.
     */
    public MyServer() {
        try (ServerSocket server = new ServerSocket(PORT)) {
            LOGGER.info("Сервер запущен");
            authService = new BaseAuthService();
            authService.start();
            clients = new ArrayList<>();
            while(true) {
                LOGGER.info("Сервер ожидает подключения");
                Socket socket = server.accept();
                LOGGER.info("Клиент подключился");
                new ClientHandler(this, socket);
            }
        } catch (IOException ex) {
            LOGGER.error("Сервер грохнулся: " + ex.getMessage());
        } catch(SQLException ex) {
            LOGGER.error("Проблемы с базой данных: " + ex.getMessage());
        } finally {
            if(authService != null) {
                authService.stop();
            }
            DBConnection.closeConnection();
            LOGGER.info("Работа сервера остановлена");
        }
    }

    /**
     * Отправляет сообщение в общий чат
     * @param message сообщение
     */
    public synchronized void broadcastMessage(String message) {
        for(ClientHandler c : clients) {
            c.sendMessage(message);
        }
    }

    /**
     * Отправляет личное сообщение
     * @param sender отправитель
     * @param recipient получатель
     * @param message сообщение
     */
    public synchronized void sendPrivateMessage(ClientHandler sender, String recipient, String message) {
        String senderNick = sender.getUser().getNick();
        for(ClientHandler c : clients) {
            if(c.getUser().getNick().equals(recipient)) {
                c.sendMessage("[Личное сообщение от " + senderNick + "]: " + message);
                sender.sendMessage("[Личное сообщение к " + recipient + "]: " + message);
                return;
            }
        }
        sender.sendMessage(ERR_SPM + "Пользователя " + recipient + " нет в чате");
    }

    public synchronized void getOnlineUsersList(ClientHandler clientHandler) {
        StringBuilder sb = new StringBuilder(CLIENTS);
        for(ClientHandler c : clients) {
            if(!c.equals(clientHandler)) {
                sb.append(c.getUser().getNick()).append(" ");
            }
        }
        clientHandler.sendMessage(sb.toString());
    }

    /**
     * Подписывает клиента на рассылку сообщений
     * @param client подписываемый клиент
     */
    public synchronized void subscribe(ClientHandler client) {
        clients.add(client);
    }

    /**
     * Отписывает клиента на рассылку сообщений
     * @param client отписываемый клиент
     */
    public synchronized void unsubscribe(ClientHandler client) {
        clients.remove(client);
    }


    public boolean isUserBusy(User user) {
        for(ClientHandler client : clients) {
            if(client.getUser().equals(user)) {
                return true;
            }
        }
        return false;
    }
}
