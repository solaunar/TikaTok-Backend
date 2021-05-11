import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

public class Zookeeper extends Node{
    Address zookeeperAddress = Node.ZOOKEEPER_ADDRESS;
    ServerSocket zookeeperServerSocket = null;
    transient InfoTable infoTable;

    public Zookeeper(){
        infoTable = new InfoTable();
        System.out.println("[Zookeeper]: Initialized. " + zookeeperAddress.toString());
    }

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

    public synchronized InfoTable getInfoTable() {
        return infoTable;
    }

    public static void main(String[] args) {
        new Zookeeper().openZookeeperServer();
    }
}
