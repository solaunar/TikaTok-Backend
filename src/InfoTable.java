import java.io.File;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * InfoTable class implements Serializable
 * so that it can be shared on Threads
 *
 * InfoTable basically contains all the data
 * needed for the System to work
 */
public class InfoTable implements Serializable {

    //Data contained in InfoTable

    //HashMap topicsAssociatedWithBrokers has the BROKER Addresses
    //as keys and values of ArrayList<String>, each value is the list
    //of topics that are assigned to the Broker by the hashing func
    private HashMap<Address, ArrayList<String>> topicsAssociatedWithBrokers = new HashMap<>();

    //HashMap hashingIDAssociatedWithBrokers has the BROKER Addresses
    //as keys and values of BigInteger, each BigInteger is the
    //respective BrokerID assigned by the hashing func
    private HashMap<Address, BigInteger> hashingIDAssociatedWithBrokers = new HashMap<>();

    //HashMap availablePublishers has the AppNode objects
    //as keys and values of ArrayList<String>, each value is the
    //list of topics that the related Publisher AppNode is responsible for
    private HashMap<AppNode, ArrayList<String>> availablePublishers = new HashMap<>();

    //HashMap allVideosByTopic has String representing the TOPICS
    //as keys and values of ArrayList<File>, each value is the
    //list of VIDEOS associated to the certain topic
    private HashMap<String, ArrayList<File>> allVideosByTopic = new HashMap<>();

    //ArrayList availableTopics is a list that contains all the available topics
    private ArrayList<String> availableTopics = new ArrayList<>();

    /**
     * Constructor (empty since the data structures are initialized above)
     */
    public InfoTable() {
    }

    /**
     * GETTERS SYNCHRONIZED FOR DATA STRUCTURES STORED IN INFOTABLE
     */
    public synchronized HashMap<Address, ArrayList<String>> getTopicsAssociatedWithBrokers() {
        return topicsAssociatedWithBrokers;
    }

    public synchronized ArrayList<String> getAvailableTopics() {
        return availableTopics;
    }

    public synchronized HashMap<Address, BigInteger> getHashingIDAssociatedWithBrokers() {
        return hashingIDAssociatedWithBrokers;
    }

    public synchronized HashMap<AppNode, ArrayList<String>> getAvailablePublishers() {
        return availablePublishers;
    }

    public synchronized HashMap<String, ArrayList<File>> getAllVideosByTopic() {
        return allVideosByTopic;
    }

    /**
     * @Override of the toString() method
     * @return Readable InfoTable string
     */
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
