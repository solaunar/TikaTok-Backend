
import java.io.*;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;

public class Broker extends Node{
    private static final int UPDATE_NODES = 0;
    private static final int UPDATE_ID = 2;
    private static final int UPDATE_ON_DELETE = 1;
    //private static final int UPDATE_PUBLISHERS = 3;
    private boolean updateID = true;
    private Address address;
    private BigInteger brokerID = BigInteger.valueOf(0);
    ServerSocket brokerServerSocket = null;
    private ArrayList<String> topicsAssociated = new ArrayList<>();
    private InfoTable infoTable;
    private HashMap<AppNode, ArrayList<String>> registeredConsumers = new HashMap<>();
    private ArrayList<AppNode> registeredPublishers = new ArrayList<>();
    private HashMap<AppNode, ArrayList<String>> availablePublishers;

    public Broker(Address address) {
        this.address = address;
        System.out.println("[Broker]: Broker initialized. " + address.toString());
    }

    public synchronized Address getAddress() {
        return address;
    }

    public BigInteger getBrokerID() {
        return brokerID;
    }

    public ArrayList<String> getTopicsAssociated() {
        return topicsAssociated;
    }

    public synchronized HashMap<AppNode, ArrayList<String>> getRegisteredConsumers() {
        return registeredConsumers;
    }

    public synchronized ArrayList<AppNode> getRegisteredPublishers() {
        return registeredPublishers;
    }

    public synchronized InfoTable getInfoTable() {
        return infoTable;
    }

    public void setTopicsAssociated(ArrayList<String> topicsAssociated) {
        this.topicsAssociated = topicsAssociated;
    }

    public void setAvailablePublishers(HashMap<AppNode, ArrayList<String>> availablePublishers) {
        this.availablePublishers = availablePublishers;
    }

    public synchronized void setRegisteredPublishers() {
        boolean nextPub = false;
        for(AppNode publisher : availablePublishers.keySet()){
            for (String topicPublisher : availablePublishers.get(publisher)){
                for (String associatedTopic : topicsAssociated){
                    if (topicPublisher.equals(associatedTopic)) {
                        if (!registeredPublishers.contains(publisher))
                            registeredPublishers.add(publisher);
                        nextPub = true;
                        break;
                    }
                }
                if(nextPub) break;
            }
        }
    }

    public void init(){
        calculateBrokerID();
        Thread zookeeperThread = new Thread(new Runnable() {
            @Override
            public void run() {
                updateID();
                updateID = false;
            }
        });
        zookeeperThread.start();
        openBrokerServer();
    }

    public void openBrokerServer(){
        try{
            brokerServerSocket = new ServerSocket(address.getPort(), Node.BACKLOG);
            System.out.println("[Broker]: Ready to accept requests.");
            Socket appNodeSocket;
            while (true){
                appNodeSocket = brokerServerSocket.accept();
                Thread appNodeThread = new BrokerActionsForAppNodes(appNodeSocket, this);
                appNodeThread.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                brokerServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void calculateBrokerID(){
        System.out.println("[Broker]: Calculating brokerID for self.");
        //Start of hashing of ip+port
        String hash = address.getIp()+ address.getPort();
        byte[] bytesOfMessage=null;
        MessageDigest md=null;
        try {
            bytesOfMessage = hash.getBytes("UTF-8");
            md = MessageDigest.getInstance("SHA-1");
        } catch (UnsupportedEncodingException ex){
            System.out.println("Unsupported encoding");
        } catch (NoSuchAlgorithmException ex){
            System.out.println("Unsupported hashing");
        }
        byte[] digest = md.digest(bytesOfMessage);
        brokerID = new BigInteger(digest);
    }

    public void updateID(){
        Socket brokerSocket = null;
        ObjectOutputStream brokerSocketOut = null;
        ObjectInputStream brokerSocketIn = null;
        try{
            brokerSocket = new Socket(Node.ZOOKEEPER_ADDRESS.getIp(), Node.ZOOKEEPER_ADDRESS.getPort());
            brokerSocketOut = new ObjectOutputStream(brokerSocket.getOutputStream());
            brokerSocketIn = new ObjectInputStream(brokerSocket.getInputStream());
            brokerSocketOut.writeInt(UPDATE_ID);
            brokerSocketOut.flush();
            brokerSocketOut.writeObject(address);
            brokerSocketOut.flush();
            brokerSocketOut.writeObject(brokerID);
            brokerSocketOut.flush();
            System.out.println(brokerSocketIn.readObject());
            brokerSocketIn.close();
            brokerSocketOut.close();
            brokerSocket.close();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                brokerSocketIn.close();
                brokerSocketOut.close();
                brokerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void updateInfoTable(AppNode appNode, ArrayList<String> allHashtagsPublished, ArrayList<File> allVideosPublished, HashMap<String, ArrayList<File>> userVideosByHashtag, boolean isPublisher){
        Socket brokerSocket = null;
        ObjectOutputStream brokerSocketOut = null;
        ObjectInputStream brokerSocketIn = null;
        try{
            brokerSocket = new Socket(Node.ZOOKEEPER_ADDRESS.getIp(), Node.ZOOKEEPER_ADDRESS.getPort());
            brokerSocketOut = new ObjectOutputStream(brokerSocket.getOutputStream());
            brokerSocketIn = new ObjectInputStream(brokerSocket.getInputStream());
            brokerSocketOut.writeInt(UPDATE_NODES);
            brokerSocketOut.flush();
            brokerSocketOut.writeObject(appNode);
            brokerSocketOut.flush();
            brokerSocketOut.writeObject(address);
            brokerSocketOut.flush();
            brokerSocketOut.writeObject(allHashtagsPublished);
            brokerSocketOut.flush();
            brokerSocketOut.writeObject(allVideosPublished);
            brokerSocketOut.flush();
            brokerSocketOut.writeObject(userVideosByHashtag);
            brokerSocketOut.flush();
            brokerSocketOut.writeBoolean(isPublisher);
            brokerSocketOut.flush();
            System.out.println(brokerSocketIn.readObject());
            infoTable = (InfoTable) brokerSocketIn.readObject();
            for (Address broker :infoTable.getTopicsAssociatedWithBrokers().keySet()){
                if (broker.compare(address))
                    setTopicsAssociated(infoTable.getTopicsAssociatedWithBrokers().get(broker));
            }
            setAvailablePublishers(infoTable.getAvailablePublishers());
            setRegisteredPublishers();
            System.out.println(brokerSocketIn.readObject());
            brokerSocketIn.close();
            brokerSocketOut.close();
            brokerSocket.close();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                brokerSocketIn.close();
                brokerSocketOut.close();
                brokerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void updateOnDelete(AppNode appNode, File toBeDeleted, ArrayList<String> allHashtagsPublished){
        Socket brokerSocket = null;
        ObjectOutputStream brokerSocketOut = null;
        ObjectInputStream brokerSocketIn = null;
        try{
            brokerSocket = new Socket(Node.ZOOKEEPER_ADDRESS.getIp(), Node.ZOOKEEPER_ADDRESS.getPort());
            brokerSocketOut = new ObjectOutputStream(brokerSocket.getOutputStream());
            brokerSocketIn = new ObjectInputStream(brokerSocket.getInputStream());
            brokerSocketOut.writeInt(UPDATE_ON_DELETE);
            brokerSocketOut.flush();
            brokerSocketOut.writeObject(appNode);
            brokerSocketOut.flush();
            brokerSocketOut.writeObject(toBeDeleted);
            brokerSocketOut.flush();
            brokerSocketOut.writeObject(allHashtagsPublished);
            brokerSocketOut.flush();
            System.out.println(brokerSocketIn.readObject());
            infoTable = (InfoTable) brokerSocketIn.readObject();
            for (Address broker :infoTable.getTopicsAssociatedWithBrokers().keySet()){
                if (broker.compare(address))
                    setTopicsAssociated(infoTable.getTopicsAssociatedWithBrokers().get(broker));
            }
            setAvailablePublishers(infoTable.getAvailablePublishers());
            setRegisteredPublishers();
            System.out.println(brokerSocketIn.readObject());
            brokerSocketIn.close();
            brokerSocketOut.close();
            brokerSocket.close();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                brokerSocketIn.close();
                brokerSocketOut.close();
                brokerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
}
