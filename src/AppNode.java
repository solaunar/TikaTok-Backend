import java.io.*;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class AppNode extends Node {
    private String userDirectory = "";
    private Address address;
    transient Scanner appNodeInput = null;
    private volatile Channel channel;
    transient ServerSocket appNodeServerSocket = null;
    transient Socket connection = null;
    private boolean isPublisher = false;
    private boolean isSubscribed = false;
    private InfoTable infoTable;
    private HashMap<String, ArrayList<File>> subscribedTopics = new HashMap<>();

    public AppNode(Address address) {
        this.address = address;
        appNodeInput = new Scanner(System.in);
    }

    public Address getAddress() {
        return address;
    }

    public Scanner getAppNodeInput() {
        return appNodeInput;
    }

    public boolean isPublisher() {
        return isPublisher;
    }

    public void setPublisher(boolean publisher) {
        isPublisher = publisher;
    }

    public boolean isSubscribed() {
        return isSubscribed;
    }

    public void setSubscribed(boolean subscribed) {
        isSubscribed = subscribed;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public HashMap<String, ArrayList<File>> getSubscribedTopics() {
        return subscribedTopics;
    }

    public InfoTable getInfoTable() {
        return infoTable;
    }

    public void setInfoTable(InfoTable infoTable) {
        this.infoTable = infoTable;
    }

    public boolean compare(AppNode appNode) {
        return this.getAddress().compare(appNode.getAddress());
    }

    public void uploadVideo(String directory, ArrayList<String> hashtags) {
        File videoFile = new File(directory);
        if (getChannel().getAllVideosPublished().contains(videoFile)){
            System.out.println("Video has been uploaded. Please chose upload again if you'd like to upload a NEW video.");
            return;
        }
        HashMap<String, ArrayList<File>> userVideosByHashtag = getChannel().getUserVideosByHashtag();
        for (String hashtag : hashtags) {
            if (!getChannel().getAllHashtagsPublished().contains(hashtag)) {
                getChannel().getAllHashtagsPublished().add(hashtag);
            }
            if (userVideosByHashtag.containsKey(hashtag)) {
                ArrayList<File> videosByHashtag = userVideosByHashtag.get(hashtag);
                videosByHashtag.add(videoFile);
            } else {
                ArrayList<File> videosByHashtag = new ArrayList<>();
                videosByHashtag.add(videoFile);
                userVideosByHashtag.put(hashtag, videosByHashtag);
            }
        }
        getChannel().getAllVideosPublished().add(videoFile);
        getChannel().getUserHashtagsPerVideo().put(videoFile, hashtags);
    }

    public void deleteVideo(File video) {
        getChannel().getAllVideosPublished().remove(video);
        ArrayList<String> hashtagsAssociated = getChannel().getUserHashtagsPerVideo().get(video);
        getChannel().getUserHashtagsPerVideo().remove(video);
        HashMap<String, ArrayList<File>> userVideosByHashtag = getChannel().getUserVideosByHashtag();
        for (String hashtag :hashtagsAssociated){
            if(userVideosByHashtag.containsKey(hashtag)) {
                System.out.println(hashtag + " " + userVideosByHashtag.get(hashtag));
                ArrayList<File> hashtagsFile = userVideosByHashtag.get(hashtag);
                hashtagsFile.remove(video);
                if (hashtagsFile.isEmpty()){
                    userVideosByHashtag.remove(hashtag);
                }
            }
        }
        getChannel().getAllHashtagsPublished().clear();
        getChannel().getAllHashtagsPublished().addAll(userVideosByHashtag.keySet());
        System.out.println(getChannel().getUserVideosByHashtag());
    }

    public boolean updateOnSubscriptions(){
        boolean shouldUpdate = false;
        for (String topic: subscribedTopics.keySet()){
            ArrayList<File> availableVideos = new ArrayList<>(infoTable.getAllVideosByTopic().get(topic));
            if (getChannel().getAllHashtagsPublished().contains(topic)){
                availableVideos.removeAll(getChannel().getUserVideosByHashtag().get(topic));
            }
            if(!subscribedTopics.get(topic).equals(availableVideos)){
                shouldUpdate = true;
                subscribedTopics.replace(topic, availableVideos);
            }
        }
        return shouldUpdate;
    }

    public void readDirectory() {
        File[] videoFiles = new File(userDirectory + "mp4").listFiles();
        File[] hashtags = new File(userDirectory + "hashtags").listFiles();
        System.out.println(videoFiles);
        System.out.println(hashtags);
        setChannelMaps(videoFiles, hashtags);
    }

    public void setChannelMaps(File[] videoFiles, File[] hashtags) {
        HashMap<File, ArrayList<String>> userHashtagsPerVideo = new HashMap<>();
        ArrayList<String> allHashtagsPublished = new ArrayList<>();
        ArrayList<File> allVideosPublished = new ArrayList<>(Arrays.asList(videoFiles));
        for (int i = videoFiles.length -1; i >= 0; i--) {
            File video = videoFiles[i];
            File hashtag = hashtags[i];
            ArrayList<String> hashtagList = readHashtagsFile(hashtag, allHashtagsPublished);
            userHashtagsPerVideo.put(video, hashtagList);
        }
        HashMap<String, ArrayList<File>> userVideosByHashtag = new HashMap<>();
        for (String hashtagPublished : allHashtagsPublished) {
            ArrayList<File> videosForThisHashtag = new ArrayList<>();
            for (Map.Entry videoElement : userHashtagsPerVideo.entrySet()) {
                File video = (File) videoElement.getKey();
                if (((ArrayList<String>) videoElement.getValue()).contains(hashtagPublished)) {
                    videosForThisHashtag.add(video);
                }
            }
            userVideosByHashtag.put(hashtagPublished, videosForThisHashtag);
        }
        //System.out.println(userHashtagsPerVideo);
        //System.out.println(allHashtagsPublished);
        //System.out.println(userVideosByHashtag);
        channel.setUserHashtagsPerVideo(userHashtagsPerVideo);
        channel.setAllHashtagsPublished(allHashtagsPublished);
        channel.setUserVideosByHashtag(userVideosByHashtag);
        channel.setAllVideosPublished(allVideosPublished);
    }//reads hashtags from txt file and returns them in list of String

    ArrayList<String> readHashtagsFile(File hashtag, ArrayList<String> allHashtagsPublished) {
        ArrayList<String> hashtagList = new ArrayList<>();
        Scanner hashtagReader = null;
        try {
            hashtagReader = new Scanner(hashtag);
            while (hashtagReader.hasNextLine()) {
                String hashtagRead = hashtagReader.nextLine();
                hashtagList.add(hashtagRead.toLowerCase());
                //add to total published hashtags list
                if (!allHashtagsPublished.contains(hashtagRead)) {
                    allHashtagsPublished.add(hashtagRead.toLowerCase());
                }
                //System.out.println(hashtag);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return hashtagList;
    }

    public void init() {
        System.out.println("[AppNode]: created.");
        System.out.println("[AppNode]: Please enter a username: ");
        String channelName = appNodeInput.nextLine().toLowerCase();
        setChannel(new Channel(channelName));
        System.out.println("Do you have any content to upload?");
        System.out.println("Please respond by typing 1 or 2:\n" +
                "1. Yes.\n" +
                "2. No.");
        int response = appNodeInput.nextInt();
        if (response == 1) {
            isPublisher = true;
            System.out.println("Please specify the directory that contains the mp4 and hashtags folders for your existent videos.");
            while (this.userDirectory.isBlank())
                this.userDirectory = appNodeInput.nextLine();
            Thread appNodeServer = new Thread(new Runnable() {
                @Override
                public void run() {
                    readDirectory();
                    openAppNodeServer();
                }
            });
            appNodeServer.start();
        }
        Thread appNodeConsumer = new AppNodeActionsForConsumers(this);
        appNodeConsumer.start();
    }

    public void openAppNodeServer() {
        try {
            appNodeServerSocket = new ServerSocket(address.getPort(), Node.BACKLOG);
            System.out.println("[Publisher]: " + this + " is ready to accept requests.");
            while (true) {
                connection = appNodeServerSocket.accept();
                Thread brokerThread = new AppNodeActionsForBrokers(connection, this);
                brokerThread.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Address hashTopic(String topic, HashMap<Address, BigInteger> hashIDAssociatedWithBrokers) {
        byte[] bytesOfMessage = null;
        MessageDigest md = null;
        try {
            bytesOfMessage = topic.getBytes("UTF-8");
            md = MessageDigest.getInstance("SHA-1");
        } catch (UnsupportedEncodingException ex) {
            System.out.println("Unsupported encoding");
        } catch (NoSuchAlgorithmException ex) {
            System.out.println("Unsupported hashing");
        }
        byte[] digest = md.digest(bytesOfMessage);
        BigInteger hashTopic = new BigInteger(digest);
        ArrayList<BigInteger> brokers = new ArrayList<>(hashIDAssociatedWithBrokers.values());
        Collections.sort(brokers);
        BigInteger maxID = brokers.get(brokers.size() - 1);
        hashTopic = hashTopic.mod(maxID);

        for (int i = 0; i < brokers.size(); i++) {
            BigInteger id = brokers.get(i);
            if (hashTopic.compareTo(id) < 0) {
                for (Map.Entry<Address, BigInteger> entry : hashIDAssociatedWithBrokers.entrySet()) {
                    if (entry.getValue().equals(id)) {
                        return entry.getKey();
                    }
                }
            }
        }
        Random random = new Random();
        return ((ArrayList<Address>) hashIDAssociatedWithBrokers.keySet()).get(random.ints(0, hashIDAssociatedWithBrokers.size()).findFirst().getAsInt());
    }

}
