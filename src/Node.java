import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;

public class Node /*extends Thread*/ implements Serializable {
    //Broker Addresses (ip, port)
    static protected final ArrayList<Address> BROKER_ADDRESSES = new ArrayList<>(Arrays.asList( new Address("192.168.88.189", 5000),
            new Address("192.168.88.188", 5250),
            new Address("192.168.88.185", 5500)));

    //AppNode Addresses (ip, port)
    static protected final ArrayList<Address> APPNODE_ADDRESSES = new ArrayList<>(Arrays.asList(new Address("192.168.88.185", 7000),
            new Address("192.168.88.186", 7250),
            new Address("192.168.88.189", 7500),
            new Address("192.168.88.188", 7750)));

    //Zookeeper Address (ip, port)
    static protected final Address ZOOKEEPER_ADDRESS = new Address("192.168.88.186", 10000);

    //backlog
    static protected final int BACKLOG = 50;
}
