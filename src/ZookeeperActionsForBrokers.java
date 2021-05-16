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
    private static final int UPDATE_ON_DELETE = 1;
    private static final int UPDATE_ID = 2;
    ObjectOutputStream out = null;
    ObjectInputStream in = null;
    Socket connection;
    Zookeeper zookeeper;

    public ZookeeperActionsForBrokers(Socket connection, Zookeeper zookeeper) {
        this.connection = connection;
        this.zookeeper = zookeeper;
        try {
            out = new ObjectOutputStream(connection.getOutputStream());
            in = new ObjectInputStream(connection.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        System.out.println("[Zookeeper]: Connection is made with broker at port: " + connection.getPort());
        try {
            int requestCode = in.readInt();
            if (requestCode == UPDATE_ON_DELETE) {
                System.out.println("[Zookeeper]: Received request for video deletion.");
                AppNode appNode = (AppNode) in.readObject();
                File toBeDeleted = (File) in.readObject();
                ArrayList<String> allHashtagsPublished = (ArrayList<String>) in.readObject();
                updateOnDelete(appNode, toBeDeleted, allHashtagsPublished);
            } else if (requestCode == UPDATE_INFOTABLE) {
                System.out.println("[Zookeeper]: Received request for InfoTable update.");
                AppNode appNode = (AppNode) in.readObject();
                Address broker = (Address) in.readObject();
                ArrayList<String> allHashtagsPublished = (ArrayList<String>) in.readObject();
                ArrayList<File> allVideosPublished = (ArrayList<File>) in.readObject();
                HashMap<String, ArrayList<File>> userVideosByHashtag = (HashMap<String, ArrayList<File>>) in.readObject();
                boolean isPublisher = in.readBoolean();
                updateInfoTable(appNode, allHashtagsPublished, allVideosPublished, userVideosByHashtag, isPublisher, broker);
            } else if (requestCode == UPDATE_ID) {
                System.out.println("[Zookeeper]: Received request for InfoTable update of brokerID.");
                Address broker = (Address) in.readObject();
                BigInteger brokerID = (BigInteger) in.readObject();
                updateID(broker, brokerID);
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

    public synchronized void updateOnDelete(AppNode appNode, File toBeDeleted, ArrayList<String> allHashtagsPublished) throws IOException {
        out.writeObject("[Zookeeper]: Updating info table...");
        out.flush();
        HashMap<Address, ArrayList<String>> topicsAssociatedWithBrokers = zookeeper.getInfoTable().getTopicsAssociatedWithBrokers();
        HashMap<String, ArrayList<File>> allVideosByTopic = zookeeper.getInfoTable().getAllVideosByTopic();
        HashMap<AppNode, ArrayList<String>> availablePublishers = zookeeper.getInfoTable().getAvailablePublishers();
        availablePublishers.replace(appNode, allHashtagsPublished);
        ArrayList<String> topicsThatDoNotExist = new ArrayList<>();
        for (String availableTopic : allVideosByTopic.keySet()){
            ArrayList<File> filesAssociated = allVideosByTopic.get(availableTopic);
            if (filesAssociated.contains(toBeDeleted)){
                filesAssociated.remove(toBeDeleted);
                if(filesAssociated.isEmpty()){
                    topicsThatDoNotExist.add(availableTopic);
                }
            }
        }
        for (String topicToBeDeleted : topicsThatDoNotExist){
            for (Address broker: topicsAssociatedWithBrokers.keySet()){
                ArrayList<String> updatedAssociatedTopics = topicsAssociatedWithBrokers.get(broker);
                if (updatedAssociatedTopics.contains(topicToBeDeleted)){
                    updatedAssociatedTopics.remove(topicToBeDeleted);
                    topicsAssociatedWithBrokers.replace(broker, updatedAssociatedTopics);
                }
            }
            allVideosByTopic.remove(topicToBeDeleted);
        }
        System.out.println("[Zookeeper]: Updated InfoTable.");
        System.out.println(zookeeper.getInfoTable());
        out.writeObject(zookeeper.getInfoTable());
        out.flush();
        out.writeObject("[Zookeeper]: Sent updated info table.");
        out.flush();
    }

    public synchronized void updateID(Address broker, BigInteger brokerID) throws IOException {
        HashMap<Address, BigInteger> hashingIDAssociatedWithBrokers = zookeeper.getInfoTable().getHashingIDAssociatedWithBrokers();
        HashMap<Address, ArrayList<String>> topicsAssociatedWithBrokers = zookeeper.getInfoTable().getTopicsAssociatedWithBrokers();
        if (brokerID.compareTo(BigInteger.valueOf(0)) != 0) {
            boolean existsHashingID = checkBrokerExistence(broker, null, hashingIDAssociatedWithBrokers);
            if (hashingIDAssociatedWithBrokers.containsKey(broker) || existsHashingID) {
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
    }

    public synchronized void updateInfoTable(AppNode appNode, ArrayList<String> allHashtagsPublished, ArrayList<File> allVideosPublished, HashMap<String, ArrayList<File>> userVideosByHashtag, boolean isPublisher, Address broker) throws IOException {
        //update - add brokerID if the parameter is not zero and if the boolean updateID is true
        //if the boolean updateID is false then we got a new pub
        HashMap<Address, BigInteger> hashingIDAssociatedWithBrokers = zookeeper.getInfoTable().getHashingIDAssociatedWithBrokers();
        HashMap<Address, ArrayList<String>> topicsAssociatedWithBrokers = zookeeper.getInfoTable().getTopicsAssociatedWithBrokers();
        HashMap<String, ArrayList<File>> allVideosByTopic = zookeeper.getInfoTable().getAllVideosByTopic();
        out.writeObject("[Zookeeper]: Updating info table...");
        out.flush();
        if (appNode != null && isPublisher) {
            allHashtagsPublished.add(appNode.getChannel().getChannelName());
            if (!checkPublisherExistence(appNode) && zookeeper.getInfoTable().getAvailablePublishers() != null) {
                zookeeper.getInfoTable().getAvailablePublishers().put(appNode, allHashtagsPublished);
            } else {
                zookeeper.getInfoTable().getAvailablePublishers().replace(appNode, allHashtagsPublished);
            }

            ArrayList<String> allAvailableTopics = zookeeper.getInfoTable().getAvailableTopics();
            for (String newTopic : allHashtagsPublished) {
                if (!allAvailableTopics.contains(newTopic))
                    allAvailableTopics.add(newTopic);
                Address brokerAdd = appNode.hashTopic(newTopic, hashingIDAssociatedWithBrokers);
                ArrayList<String> topicAssociated = topicsAssociatedWithBrokers.get(brokerAdd);
                if (!topicAssociated.contains(newTopic)) {
                    topicAssociated.add(newTopic);
                    topicsAssociatedWithBrokers.replace(brokerAdd, topicAssociated);
                }
                if (!allVideosByTopic.containsKey(newTopic)) {
                    ArrayList<File> filesAssociated = new ArrayList<>();
                    if (newTopic.startsWith("#")) {
                        filesAssociated.addAll(userVideosByHashtag.get(newTopic));
                    } else {
                        filesAssociated.addAll(allVideosPublished);
                    }
                    allVideosByTopic.put(newTopic, filesAssociated);
                } else {
                    ArrayList<File> filesAssociated = allVideosByTopic.get(newTopic);
                    if (newTopic.startsWith("#")) {
                        ArrayList<File> filesOfPublisherHashtag = userVideosByHashtag.get(newTopic);
                        for (File filePub : filesOfPublisherHashtag) {
                            if (!filesAssociated.contains(filePub))
                                filesAssociated.add(filePub);
                        }
                    } else {
                        for (File filePub : allVideosPublished) {
                            if (!filesAssociated.contains(filePub))
                                filesAssociated.add(filePub);
                        }
                    }
                    allVideosByTopic.replace(newTopic, filesAssociated);
                }
            }
        }
        System.out.println("[Zookeeper]: Updated InfoTable.");
        System.out.println(zookeeper.getInfoTable());
        out.writeObject(zookeeper.getInfoTable());
        out.flush();
        out.writeObject("[Zookeeper]: Sent updated info table.");
        out.flush();
        System.out.println("[Zookeeper]: Sent updated InfoTable to broker." + broker);
    }

    public boolean checkPublisherExistence(AppNode publisher) {
        for (AppNode availablePublisher : zookeeper.getInfoTable().getAvailablePublishers().keySet()) {
            if (availablePublisher.compare(publisher))
                return true;
        }
        return false;
    }

    public boolean checkBrokerExistence(Address broker, HashMap<Address, ArrayList<String>> topicsAssociatedWithBrokers, HashMap<Address, BigInteger> hashingIDAssociatedWithBrokers) {
        if (topicsAssociatedWithBrokers != null) {
            for (Address address : topicsAssociatedWithBrokers.keySet())
                if (broker.compare(address))
                    return true;
        }
        if (hashingIDAssociatedWithBrokers != null) {
            for (Address address : hashingIDAssociatedWithBrokers.keySet())
                if (broker.compare(address))
                    return true;
        }
        return false;
    }
}
