package serverside.model;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

public class User {
    private String nick;

    public String getNick() {
        return nick;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    public static User userBuilder(ResultSet result) throws SQLException {
        User user = null;
        if (result.next()) {
            user = new User();
            user.nick = result.getString("nick");
        }
        return user;
    }

    @Override
    public String toString() {
        return nick;
    }

    @Override
    public boolean equals(Object otherObject) {
        if(this == otherObject) {
            return true;
        }
        if(otherObject == null || getClass() != otherObject.getClass()) {
            return false;
        }
        User other = (User)otherObject;
        return this.nick.equals(other.nick);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(nick);
    }
}
