import com.uwyn.jhighlight.fastutil.Hash;
import org.apache.commons.math3.analysis.function.Add;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

public class ZookeeperActionsForBrokers extends Thread {
    private static final int UPDATE_INFOTABLE = 0;
    private static final int GET_INFOTABLE = 1;
    ObjectOutputStream out = null;
    ObjectInputStream in = null;
    Socket connection;
    Zookeeper zookeeper;

    public ZookeeperActionsForBrokers(Socket connection, Zookeeper zookeeper){
        this.connection = connection;
        this.zookeeper = zookeeper;
        try{
            out = new ObjectOutputStream(connection.getOutputStream());
            in = new ObjectInputStream(connection.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run(){
        System.out.println("[Zookeeper]: Connection is made with broker at port: " + connection.getPort());
        try {
            int requestCode = in.readInt();
            //System.out.println(requestCode);
            if (requestCode == GET_INFOTABLE){
                System.out.println("[Zookeeper]: Received request for InfoTable.");
                out.writeObject(zookeeper.getInfoTable());
                out.flush();
            } else if (requestCode == UPDATE_INFOTABLE){
                System.out.println("[Zookeeper]: Received request for InfoTable update.");
                AppNode appNode = (AppNode) in.readObject();
                Address broker = (Address) in.readObject();
                BigInteger brokerID = (BigInteger) in.readObject();
                boolean updateID = in.readBoolean();
                updateInfoTable(appNode, broker, brokerID, updateID);
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                connection.close();
                out.close();
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void updateInfoTable(AppNode appNode, Address broker, BigInteger brokerID, boolean updateID) throws IOException {
        HashMap<Address, BigInteger> hashingIDAssociatedWithBrokers = zookeeper.getInfoTable().getHashingIDAssociatedWithBrokers();
        HashMap<Address, ArrayList<String>> topicsAssociatedWithBrokers = zookeeper.getInfoTable().getTopicsAssociatedWithBrokers();
        HashMap<String, ArrayList<File>> allVideosByTopic = zookeeper.getInfoTable().getAllVideosByTopic();
        //update - add brokerID if the parameter is not zero and if the boolean updateID is true
        if (brokerID.compareTo(BigInteger.valueOf(0))!=0 && updateID){
            boolean existsHashingID = checkBrokerExistence(broker, null, hashingIDAssociatedWithBrokers);
            if (hashingIDAssociatedWithBrokers.containsKey(broker) || existsHashingID){
                System.out.println("[Zookeeper]: Updating brokerID associated with broker: " + broker.toString());
                hashingIDAssociatedWithBrokers.replace(broker, brokerID);
                topicsAssociatedWithBrokers.replace(broker, new ArrayList<String>());
                out.writeObject("[Zookeeper]: Updated brokerID associated with broker: " + broker.toString());
                out.flush();
            } else {
                System.out.println("[Zookeeper]: Added brokerID associated with broker: " + broker.toString());
                hashingIDAssociatedWithBrokers.put(broker, brokerID);
                topicsAssociatedWithBrokers.put(broker, new ArrayList<String>());
                out.writeObject("[Zookeeper]: Added brokerID associated with broker: " + broker.toString());
                out.flush();
            }
        }
        //if the boolean updateID is false then we got a new pub
        else {
            out.writeObject("[Zookeeper]: Updating info table...");
            out.flush();
            if (appNode != null && appNode.isPublisher()) {
                if (!checkPublisherExistence(appNode) && zookeeper.getInfoTable().getAvailablePublishers() != null) {
                    zookeeper.getInfoTable().getAvailablePublishers().put(appNode, appNode.getChannel().getAllHashtagsPublished());
                } else {
                    zookeeper.getInfoTable().getAvailablePublishers().replace(appNode, appNode.getChannel().getAllHashtagsPublished());
                }
            }
            ArrayList<String> allAvailableTopics = new ArrayList<>();
            for (AppNode publisher : zookeeper.getInfoTable().getAvailablePublishers().keySet()) {
                ArrayList<String> publisherTopics = zookeeper.getInfoTable().getAvailablePublishers().get(publisher);
                publisherTopics.add(publisher.getChannel().getChannelName());
                allAvailableTopics.addAll(publisherTopics);
                ArrayList<Address> topicsHashed = new ArrayList<>();
                for (String topic : publisherTopics) {
                    topicsHashed.add(publisher.hashTopic(topic, hashingIDAssociatedWithBrokers));
                }
                //System.out.println(topicsHashed);
                for (Address brokerAdd : hashingIDAssociatedWithBrokers.keySet()) {
                    ArrayList<String> topicAssociated = topicsAssociatedWithBrokers.get(brokerAdd);
                    for (int i = 0; i < topicsHashed.size(); i++) {
                        if (brokerAdd.compare(topicsHashed.get(i))) {
                            if(!topicAssociated.contains(publisherTopics.get(i))) {
                                topicAssociated.add(publisherTopics.get(i));
                            }
                        }
                    }
                    topicsAssociatedWithBrokers.replace(brokerAdd, topicAssociated);
                }
            }
            for (String availableTopic: allAvailableTopics){
                System.out.println("MPHKA STH FOR");
                ArrayList<File> filesAssociated = new ArrayList<>();
                for (AppNode availablePublisher : zookeeper.getInfoTable().getAvailablePublishers().keySet()){
                    if (availableTopic.equals(availablePublisher.getChannel().getChannelName())){
                        if(allVideosByTopic.containsKey(availableTopic)){
                            allVideosByTopic.replace(availableTopic, availablePublisher.getChannel().getAllVideosPublished());
                        } else {
                            allVideosByTopic.put(availableTopic, availablePublisher.getChannel().getAllVideosPublished());
                        }
                        break;
                    }
                    if (availableTopic.startsWith("#"))
                        if(availablePublisher.getChannel().getUserVideosByHashtag().containsKey(availableTopic))
                            filesAssociated.addAll(availablePublisher.getChannel().getUserVideosByHashtag().get(availableTopic));
                }
                if(availableTopic.startsWith("#")){
                    if (allVideosByTopic.containsKey(availableTopic)){
                        allVideosByTopic.replace(availableTopic, filesAssociated);
                    } else {
                        allVideosByTopic.put(availableTopic, filesAssociated);
                    }
                }
            }
        }
        System.out.println(allVideosByTopic);
        System.out.println("[Zookeeper]: Updated InfoTable.");
        //System.out.println(zookeeper.getInfoTable());
        out.writeObject(zookeeper.getInfoTable());
        out.flush();
        out.writeObject("[Zookeeper]: Sent updated info table." );
        out.flush();
        System.out.println("[Zookeeper]: Sent updated InfoTable to broker."+ broker);
    }

    public boolean checkPublisherExistence(AppNode publisher){
        for (AppNode availablePublisher : zookeeper.getInfoTable().getAvailablePublishers().keySet()){
            if (availablePublisher.compare(publisher))
                return true;
        }
        return false;
    }
    public boolean checkBrokerExistence(Address broker, HashMap<Address, ArrayList<String>> topicsAssociatedWithBrokers, HashMap<Address, BigInteger> hashingIDAssociatedWithBrokers){
        if (topicsAssociatedWithBrokers!=null){
            for (Address address: topicsAssociatedWithBrokers.keySet())
                if (broker.compare(address))
                    return true;
        }
        if (hashingIDAssociatedWithBrokers!=null){
            for (Address address: hashingIDAssociatedWithBrokers.keySet())
                if (broker.compare(address))
                    return true;
        }
        return false;
    }
}
