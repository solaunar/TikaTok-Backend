
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

    /**
     * method setRegisteredPublishers updates the registeredPublishers list of the broker
     *        based on the topics assigned to the broker and the availablePublishers topics lists.
     *        If there is a match of at least one topic in the previously mentioned lists then
     *        the related publisher will be registered to this broker.
     */
    public synchronized void setRegisteredPublishers() {
        boolean nextPub = false;
        boolean pub_exists = false;
        for(AppNode publisher : availablePublishers.keySet()){
            for (String topicPublisher : availablePublishers.get(publisher)){
                System.out.println("This is my publisher topic:" + topicPublisher);
                for (String associatedTopic : topicsAssociated){
                    System.out.println("Broker topic: " + associatedTopic);
                    if (topicPublisher.equals(associatedTopic)) {
                        for (AppNode registeredPublisher: registeredPublishers){
                            if(registeredPublisher.compare(publisher)){
                                pub_exists = true;
                            }
                        }
                        if(!pub_exists){
                            registeredPublishers.add(publisher);
                            pub_exists = false;
                        }
                        nextPub = true;
                        break;
                    }
                }
                if(nextPub){
                    nextPub = false;
                    break;
                }
            }
        }
    }

    /**
     * method init connects the broker to the ZOOKEEPER class to notify the zookeeper of its existence
     *             and update his brokerID and Address on the InfoTable using updateID
     *             and opens the BrokerServer to accept AppNode requests
     */
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

    /**
     * method openBrokerServer creates new ServerSocket for the Broker to accept AppNode requests
     *                         which will be handled by the BrokerActionsForAppNodes
     */
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

    /**
     * method calculateBrokerID uses SHA-1 encoding to assign a BigInteger ID to this broker
     */
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


    /**
     * method updateID() makes a connection with the Zookeeper, in which case the broker requests to update its ID
     *                   and address at the InfoTable
     */
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

    /**
     * method updateInfoTable makes a connection with the Zookeeper, in which case the broker requests to update the
     *                        InfoTable with content read from an AppNode (such as the hashTagsPublished of this
     *                        AppNode - Publisher etc).
     *                        Then it receives the UPDATED InfoTable obj from the Zookeeper.
     * @param appNode AppNode obj
     * @param allHashtagsPublished the hashtags published by this AppNode publisher
     * @param allVideosPublished the videos published by this AppNode publisher
     * @param userVideosByHashtag the videos by hashtag published by this AppNode publisher
     * @param isPublisher boolean variable to check if the AppNode is a Publisher or not
     */
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

    /**
     * method updateOnDelete used each time there is a delete request from the Publisher AppNode
     *                       BROKER makes a connection with the Zookeeper, in this case the broker requests to update the
     *                       InfoTable (delete every instance of the video to be deleted, as well as hashtags that were
     *                       only associated with this video)
     * @param appNode the Publisher AppNode obj that made the request
     * @param toBeDeleted the video File obj that the AppNode requested deletion of
     * @param allHashtagsPublished the list of the hashtagsPublished from the Publisher that made the request
     */
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
