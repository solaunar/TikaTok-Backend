import java.io.File;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class InfoTable implements Serializable {
    private HashMap<Address, ArrayList<String>> topicsAssociatedWithBrokers = new HashMap<>();
    private HashMap<Address, BigInteger> hashingIDAssociatedWithBrokers = new HashMap<>();
    private HashMap<AppNode, ArrayList<String>> availablePublishers = new HashMap<>();
    private HashMap<String, ArrayList<File>> allVideosByTopic = new HashMap<>();
    private ArrayList<String> availableTopics = new ArrayList<>();
    public InfoTable() {
    }

    public synchronized HashMap<Address, ArrayList<String>> getTopicsAssociatedWithBrokers() {
        return topicsAssociatedWithBrokers;
    }

    public void setTopicsAssociatedWithBrokers(HashMap<Address, ArrayList<String>> topicsAssociatedWithBrokers) {
        this.topicsAssociatedWithBrokers = topicsAssociatedWithBrokers;
    }

    public synchronized ArrayList<String> getAvailableTopics() {
        return availableTopics;
    }

    public void setAvailableTopics(ArrayList<String> availableTopics) {
        this.availableTopics = availableTopics;
    }

    public synchronized HashMap<Address, BigInteger> getHashingIDAssociatedWithBrokers() {
        return hashingIDAssociatedWithBrokers;
    }

    public void setHashingIDAssociatedWithBrokers(HashMap<Address, BigInteger> hashingIDAssociatedWithBrokers) {
        this.hashingIDAssociatedWithBrokers = hashingIDAssociatedWithBrokers;
    }

    public synchronized HashMap<AppNode, ArrayList<String>> getAvailablePublishers() {
        return availablePublishers;
    }

    public void setAvailablePublishers(HashMap<AppNode, ArrayList<String>> availablePublishers) {
        this.availablePublishers = availablePublishers;
    }

    public synchronized HashMap<String, ArrayList<File>> getAllVideosByTopic() {
        return allVideosByTopic;
    }

    public void setAllVideosByTopic(HashMap<String, ArrayList<File>> allVideosByTopic) {
        this.allVideosByTopic = allVideosByTopic;
    }

    @Override
    public String toString() {
        String infoTable = "";
        for (Address broker : hashingIDAssociatedWithBrokers.keySet()){
            String line = "Broker "+ broker.toString() + " ID: " + hashingIDAssociatedWithBrokers.get(broker) + " ";
            if (!topicsAssociatedWithBrokers.isEmpty()) {
                for(Address brokerAd : topicsAssociatedWithBrokers.keySet()){
                    if (brokerAd.compare(broker)){
                        for (String topic : topicsAssociatedWithBrokers.get(brokerAd)) {
                            infoTable += line + topic + "\n";
                        }
                    }
                }
            }
        }
        return "------------------------------------------------------------InfoTable------------------------------------------------------------\n" + infoTable;
    }
}
