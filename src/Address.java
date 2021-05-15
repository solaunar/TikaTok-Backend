import java.io.Serializable;

/**
 * Address class implements Serializable
 * so that it can be shared on Threads
 *
 * Address represents a device address (ip, port)
 */
public class Address implements Serializable {

    //port that the device with this address listens to
    private int port;

    //ip address of device
    private String ip;

    /**
     * Constructor
     * @param ip
     * @param port
     */
    public Address(String ip,int port) {
        this.port = port;
        this.ip = ip;
    }

    /**
     * GETTERS OF PORT AND IP
     */
    public int getPort() {
        return port;
    }

    public String getIp() {
        return ip;
    }

    /**
     * compare method: used to check if 2 Address obj are the same (have the same port and ip)
     * @param address the Address obj that we are comparing this with
     * @return boolean true if addresses are the same or false if they are not
     */
    public boolean compare(Address address){
        return (this.getIp().equals(address.getIp()) && this.getPort()==address.getPort()) || (this == address);
    }

    /**
     * @Override of the toString() method
     * @return Readable Address string
     */
    @Override
    public String toString() {
        return "Port: " + this.port + " IP: " + this.ip;
    }
}
