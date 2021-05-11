import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;

public class Node /*extends Thread*/ implements Serializable {
    //Broker Addresses (ip, port)
    static protected final ArrayList<Address> BROKER_ADDRESSES = new ArrayList<>(Arrays.asList(
            new Address("192.168.1.36", 5000),
            new Address("192.168.1.36", 5250),
            new Address("192.168.1.36", 5500)));

    //AppNode Addresses (ip, port)
    static protected final ArrayList<Address> APPNODE_ADDRESSES = new ArrayList<>(Arrays.asList(
            new Address("192.168.1.26", 7000),
            new Address("192.168.88.186", 7250),
            new Address("192.168.1.32", 7500),
            new Address("192.168.1.37", 7750)));

    //Zookeeper Address (ip, port)
    static protected final Address ZOOKEEPER_ADDRESS = new Address("192.168.1.36", 10000);

    //backlog
    static protected final int BACKLOG = 250;
}
