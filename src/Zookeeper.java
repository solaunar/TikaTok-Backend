import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Zookeeper class
 * handles updates of InfoTable
 *
 */
public class Zookeeper extends Node{

    //Zookeeper Address got from Node
    Address zookeeperAddress = Node.ZOOKEEPER_ADDRESS;
    //Zookeeper ServerSocket to accept requests
    ServerSocket zookeeperServerSocket = null;
    //InfoTable used in the system (shared and updated by Zookeeper)
    transient InfoTable infoTable;

    /**
     * Constructor
     */
    public Zookeeper(){
        //create new InfoTable obj for the system
        infoTable = new InfoTable();
        System.out.println("[Zookeeper]: Initialized. " + zookeeperAddress.toString());
    }

    /**
     * openZookeeperServer(): creates new ServerSocket for Zookeeper
     *                        waits for BROKER requests and once accepted
     *                        runs a new thread (ZookeeperActionsForBrokers)
     *                        to handle this BROKER's request
     */
    public void openZookeeperServer(){
        try {
            zookeeperServerSocket = new ServerSocket(zookeeperAddress.getPort(), Node.BACKLOG);
            System.out.println("[Zookeeper]: Ready to accept requests.");
            Socket brockerSocket;
            while (true) {
                brockerSocket = zookeeperServerSocket.accept();
                Thread brokerThread = new ZookeeperActionsForBrokers(brockerSocket, this);
                brokerThread.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                zookeeperServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * getInfoTable()
     * @return InfoTable object (the one used in this system)
     */
    public synchronized InfoTable getInfoTable() {
        return infoTable;
    }

    /**
     * main: creates new Zookeeper obj and calls openZookeeperServer()
     *       so that the Zookeeper starts functioning as it should
     * @param args not used
     */
    public static void main(String[] args) {
        new Zookeeper().openZookeeperServer();
    }
}
