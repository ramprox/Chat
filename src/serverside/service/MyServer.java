package serverside.service;

import serverside.interfaces.AuthService;
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
            authService = new BaseAuthService();
            authService.start();
            clients = new ArrayList<>();
            while(true) {
                System.out.println("Сервер ожидает подключения");
                Socket socket = server.accept();
                System.out.println("Клиент подключился");
                new ClientHandler(this, socket);
            }
        } catch (IOException e) {
            System.out.println("Сервер грохнулся");
        } catch(SQLException ex) {
            for(Throwable t : ex) {
                t.printStackTrace();
            }
            System.out.println("Соединение с базой данных отсутствует");
        } finally {
            if(authService != null) {
                authService.stop();
            }
            DBConnection.closeConnection();
        }
    }

    public synchronized void broadcastMessage(String message) {
        for(ClientHandler c : clients) {
            c.sendMessage(message);
        }
    }

    public synchronized void sendPrivateMessage(ClientHandler sender, String nick, String message) {
        for(ClientHandler c : clients) {
            if(c.getName().equals(nick)) {
                c.sendMessage("[Личное сообщение от " + sender.getName() + "]: " + message);
                sender.sendMessage("[Личное сообщение к " + nick + "]: " + message);
                return;
            }
        }
        sender.sendMessage(ERR_SPM + "Пользователя " + nick + " нет в чате");
    }

    public synchronized void getOnlineUsersList(ClientHandler clientHandler) {
        StringBuilder sb = new StringBuilder(CLIENTS);
        for(ClientHandler c : clients) {
            if(!c.equals(clientHandler)) {
                sb.append(c.getName()).append(" ");
            }
        }
        clientHandler.sendMessage(sb.toString());
    }


    public synchronized void subscribe(ClientHandler client) {
        clients.add(client);
    }

    public synchronized void unsubscribe(ClientHandler client) {
        clients.remove(client);
    }

    public boolean isNickBusy(String nick) {
        for(ClientHandler client : clients) {
            if(client.getName().equals(nick)) {
                return true;
            }
        }
        return false;
    }
}
