import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;

public class Node /*extends Thread*/ implements Serializable {
    //Broker Addresses (ip, port)
    static protected final ArrayList<Address> BROKER_ADDRESSES = new ArrayList<>(Arrays.asList( new Address("127.0.0.1", 5000),
            new Address("127.0.0.1", 5250),
            new Address("127.0.0.1", 5500)));

    //AppNode Addresses (ip, port)
    static protected final ArrayList<Address> APPNODE_ADDRESSES = new ArrayList<>(Arrays.asList(new Address("127.0.0.1", 7000),
            new Address("127.0.0.1", 7250),
            new Address("127.0.0.1", 7500)));

    //Zookeeper Address (ip, port)
    static protected final Address ZOOKEEPER_ADDRESS = new Address("127.0.0.1", 10000);

    //backlog
    static protected final int BACKLOG = 50;
}
