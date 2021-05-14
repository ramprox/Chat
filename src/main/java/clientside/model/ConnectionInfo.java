package clientside.model;

public class ConnectionInfo {
    private boolean isConnected;
    private boolean isAuthorized;

    public ConnectionInfo() {
        isConnected = false;
        isAuthorized = false;
    }

    public void setConnected(boolean connected) {
        isConnected = connected;
        if(!connected) {
            isAuthorized = false;
        }
    }

    public void setAuthorized(boolean authorized) {
        if(isConnected) {
            isAuthorized = authorized;
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    public boolean isAuthorized() {
        return isAuthorized;
    }
}
