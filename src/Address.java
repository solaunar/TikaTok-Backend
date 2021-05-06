import java.io.Serializable;

public class Address implements Serializable {
    private int port;
    private String ip;

    public Address(){}
    public Address(String ip,int port) {
        this.port = port;
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public boolean compare(Address address){
        return (this.getIp().equals(address.getIp()) && this.getPort()==address.getPort()) || (this == address);
    }

    @Override
    public String toString() {
        return "Port: " + this.port + " IP: " + this.ip;
    }
}
