import java.io.*;
import java.math.BigInteger;
import java.net.Socket;
import java.util.*;

public class BrokerActionsForAppNodes extends Thread {
    ObjectOutputStream out = null;
    ObjectInputStream in = null;
    Socket connection;
    Broker broker;

    public BrokerActionsForAppNodes(Socket connection, Broker broker) {
        this.connection = connection;
        this.broker = broker;
        try {
            out = new ObjectOutputStream(connection.getOutputStream());
            in = new ObjectInputStream(connection.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        System.out.println("[Broker]: Connection is made with appNode at port: " + connection.getPort());
        try {
            while (true) {
                Object message = in.readObject();
                if (message instanceof AppNode) {
                    AppNode user = (AppNode) message;
                    ArrayList<String> allHashtagsPublished = (ArrayList<String>) in.readObject();
                    ArrayList<File> allVideosPublished = (ArrayList<File>) in.readObject();
                    HashMap<String, ArrayList<File>> userVideosByHashtag = (HashMap<String, ArrayList<File>>) in.readObject();
                    boolean isPublisher = in.readBoolean();
                    broker.updateInfoTable(user, allHashtagsPublished, allVideosPublished, userVideosByHashtag, isPublisher);
                    System.out.println("[Broker]: AppNode: " + user.getChannel().getChannelName() + " data retrieved.");
                    out.writeObject("[Broker(" + broker.getAddress() + " )]: AppNode data retrieved.");
                    out.flush();
                } else if (message instanceof VideoFile){
                    VideoFile requestedVideo = (VideoFile) message;
                    System.out.println("[Broker]: Got video file from publisher.");
                    out.writeObject("Received video file request.");
                    out.flush();
                    AppNode consumer = (AppNode) in.readObject();
                    pull(requestedVideo, consumer);
                } else{
                    String command = (String) message;
                    if (command.equals("INFO")){
                        System.out.println("[Broker]: Received request for INFO table...");
                        out.writeObject("[Broker]: Getting info table for brokers...");
                        out.flush();
                        broker.updateInfoTable(null, null, null, null, false);
                        out.writeObject(broker.getInfoTable());
                        out.flush();
                    } else if (command.equals("PUBLISHER")){
                        ArrayList<String> topicsPub = (ArrayList<String>) in.readObject();
                        AppNode publisher = (AppNode) in.readObject();
                        ArrayList<String> allHashtagsPublished = (ArrayList<String>) in.readObject();
                        ArrayList<File> allVideosPublished = (ArrayList<File>) in.readObject();
                        HashMap<String, ArrayList<File>> userVideosByHashtag = (HashMap<String, ArrayList<File>>) in.readObject();
                        boolean isPublisher = in.readBoolean();
                        broker.updateInfoTable(publisher, allHashtagsPublished, allVideosPublished, userVideosByHashtag, isPublisher);
                    } else if (command.equals("RC")){
                        System.out.println("[Broker]: Received request for redirection of connection.");
                        Address rcAdress = (Address)in.readObject();
                        if(rcAdress.compare(broker.getAddress())){
                            out.writeBoolean(false);
                            out.flush();
                            out.writeObject("Already at correct Broker.");
                            out.flush();
                            continue;
                        }
                        //has to redirect
                        out.writeBoolean(true);
                        out.flush();
                        out.writeObject("[Broker]: Redirected successfully to the proper broker.");
                        out.flush();
                        this.interrupt();
                    } else if(command.equals("EXIT")){
                        System.out.println("[Broker]: A consumer logged out from broker.");
                        out.writeObject("Disconnected successfully.");
                        out.flush();
                        out.close();
                        in.close();
                        connection.close();
                        break;
                    } else if (command.equals("LIST_CHANNEL")) {
                        String channelName = (String) in.readObject();
                        for (AppNode publisher : broker.getRegisteredPublishers()){
                            if (publisher.getChannel().getChannelName().equals(channelName.toLowerCase())){
                                out.writeObject(publisher.getChannel().getUserHashtagsPerVideo());
                                out.flush();
                            }
                        }
                    } else if(command.equals("LIST_HASHTAG")){
                        String hashtag = (String) in.readObject();
                        AppNode userConsumer = (AppNode) in.readObject();
                        HashMap<String, ArrayList<File>> allVideosByHashtag = new HashMap<>();
                        ArrayList<File> publisherVidsByHashtag;
                        for (AppNode publisher: broker.getRegisteredPublishers()){
                            if (userConsumer.compare(publisher)) continue;
                            publisherVidsByHashtag = publisher.getChannel().getUserVideosByHashtag().get(hashtag);//getAllHashtagVideos(hashtag, publisher.getAddress().getIp(), publisher.getAddress().getPort());
                            allVideosByHashtag.put(publisher.getChannel().getChannelName(), publisherVidsByHashtag);
                        }
                        out.writeObject(allVideosByHashtag);
                        out.flush();
                    } else if(command.equals("REG")){
                        AppNode userRegister = (AppNode) in.readObject();
                        String topic = (String) in.readObject();
                        registerConsumer(userRegister, topic);
                    } else if(command.equals("LIST_TOPIC")){
                        String topic = (String) in.readObject();
                        AppNode consumer = (AppNode) in.readObject();
                        ArrayList<File> videosToReturn = new ArrayList<>(broker.getInfoTable().getAllVideosByTopic().get(topic));
                        out.writeObject(videosToReturn.removeAll(consumer.getChannel().getUserVideosByHashtag().get(topic)));
                        out.flush();
                    } else if(command.equals("DELETE")){
                        AppNode publisher = (AppNode) in.readObject();
                        File toBeDeleted = (File) in.readObject();
                        ArrayList<String> allHashtagsPublished = (ArrayList<String>) in.readObject();
                        broker.updateOnDelete(publisher, toBeDeleted, allHashtagsPublished);
                        out.writeObject("[Broker(" + broker.getAddress() + " )]: AppNode data retrieved.");
                        out.flush();
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                in.close();
                out.close();
                connection.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    public boolean registerConsumer(AppNode user, String topic) {
        ArrayList<String> topicsRegistered = new ArrayList<>();
        for (AppNode registeredConsumer : broker.getRegisteredConsumers().keySet()) {
            if (user.compare(registeredConsumer)) {
                topicsRegistered = broker.getRegisteredConsumers().get(registeredConsumer);
                if (topicsRegistered.contains(topic)){
                    System.out.println("[Broker]: AppNode user: " + user + " already registered as consumer for topic: " + topic + ".");
                }
                else{
                    topicsRegistered.add(topic);
                }
                return false;
            }
        }
        topicsRegistered.add(topic);
        broker.getRegisteredConsumers().put(user, topicsRegistered);
        return true;
    }

    public void pull(VideoFile videoFile, AppNode consumer){
        int publisherServer = 0;
        String publisherIP ="";
        Socket brokerSocket;
        ObjectOutputStream brokerSocketOut;
        ObjectInputStream brokerSocketIn;
        /**ITERATE HASHMAP NOT ARRAYLIST**/
        for (AppNode user: broker.getRegisteredPublishers()){
            if (consumer.compare(user)) continue;
            ArrayList<File> allVideosPublished = broker.getInfoTable().getAllVideosByTopic().get(user.getChannel().getChannelName());
            for (File video : allVideosPublished){
                if (video.equals(videoFile.getFile())){
                    publisherServer = user.getAddress().getPort();
                    publisherIP = user.getAddress().getIp();
                    break;
                }
            }
        }
        if(publisherServer==0 || publisherIP.equals("")){
            System.out.println("This video does not exist.");
            try {
                out.writeObject("NO MORE CHUNKS");
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }
        try {
            brokerSocket = new Socket(publisherIP, publisherServer);
            brokerSocketOut = new ObjectOutputStream(brokerSocket.getOutputStream());
            brokerSocketIn = new ObjectInputStream(brokerSocket.getInputStream());
            brokerSocketOut.writeObject(videoFile);
            brokerSocketOut.flush();
            System.out.println("Request sent to publisher.");
            ArrayList<VideoFile> chunks = new ArrayList<>();
            VideoFile chunk;
            String response;
            while (true){
                response = (String) brokerSocketIn.readObject();
                System.out.println(">Publisher: "+response);
                if (response.equals("NO MORE CHUNKS")){
                    out.writeObject("NO MORE CHUNKS");
                    out.flush();
                    break;
                }
                chunk = (VideoFile) brokerSocketIn.readObject();
                chunks.add(chunk);
                brokerSocketOut.writeObject("RECEIVED");
                out.writeObject(chunk);
                out.flush();
                response = (String) in.readObject();
                if (response.equals("RECEIVED")) continue;
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}