package serverside.interfaces;

import java.sql.*;

public interface AuthService {
    void start();
    void stop();
    String getNickByLoginAndPassword(String login, String password) throws SQLException;
}
