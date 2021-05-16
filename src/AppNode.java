import java.io.*;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * AppNode class extends Node
 *
 * AppNode represents a user of the system
 * Publisher/ Consumer or both
 */
public class AppNode extends Node {

    //userDirectory, the path where the already uploaded files of
    //a user are stored, might be blank forever
    private String userDirectory = "";
    private Address address;
    transient Scanner appNodeInput;
    private Channel channel;
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

    public synchronized HashMap<String, ArrayList<File>> getSubscribedTopics() {
        return subscribedTopics;
    }

    public InfoTable getInfoTable() {
        return infoTable;
    }

    public void setInfoTable(InfoTable infoTable) {
        this.infoTable = infoTable;
    }

    /**
     * compare method: used to check if 2 Address obj are the same (have the same port and ip)
     * @param appNode the AppNode obj that we are comparing this with
     * @return boolean true if channel/user names are the same or false if they are not
     */
    public boolean compare(AppNode appNode) {
        return this.getAddress().compare(appNode.getAddress());
    }

    /**
     * method uploadVideo creates a new File obj for the video and updates any data structures needed
     * @param directory String input of user of path of video to be uploaded
     * @param hashtags ArrayList<String> hashtags that user gave for this video
     */
    public synchronized void uploadVideo(String directory, ArrayList<String> hashtags) {
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

    /**
     * method deleteVideo removes the video File obj from any data structures needed
     *                    as well as removes topics that had only that video related to them
     * @param video File obj of video that user asked to be deleted
     */
    public synchronized void deleteVideo(File video) {
        getChannel().getAllVideosPublished().remove(video);
        ArrayList<String> hashtagsAssociated = getChannel().getUserHashtagsPerVideo().get(video);
        getChannel().getUserHashtagsPerVideo().remove(video);
        HashMap<String, ArrayList<File>> userVideosByHashtag = getChannel().getUserVideosByHashtag();
        for (String hashtag :hashtagsAssociated){
            if(userVideosByHashtag.containsKey(hashtag)) {
                ArrayList<File> hashtagsFile = userVideosByHashtag.get(hashtag);
                hashtagsFile.remove(video);
                if (hashtagsFile.isEmpty()){
                    userVideosByHashtag.remove(hashtag);
                }
            }
        }
        getChannel().getAllHashtagsPublished().clear();
        getChannel().getAllHashtagsPublished().addAll(userVideosByHashtag.keySet());
    }

    /**
     * method updateOnSubscriptions checks for any changes of the available videos related to
     *                              the subscribed topics, and if changes exist it returns the
     *                              list mentioned bellow
     * @return ArrayList<String> updatedTopics which is the list of the topics that have
     *         been updated (either video got deleted or uploaded on this subscribed topic)
     */
    public synchronized ArrayList<String> updateOnSubscriptions(){
        ArrayList<String> updatedTopics = new ArrayList<>();
        for (String topic: subscribedTopics.keySet()){
            ArrayList<File> availableVideos = new ArrayList<>(infoTable.getAllVideosByTopic().get(topic));
            if (getChannel().getAllHashtagsPublished().contains(topic)){
                availableVideos.removeAll(getChannel().getUserVideosByHashtag().get(topic));
            }
            if(!subscribedTopics.get(topic).equals(availableVideos)){
                updatedTopics.add(topic);
                subscribedTopics.replace(topic, availableVideos);
            }
        }
        return updatedTopics;
    }

    /**
     * method readDirectory lists already uploaded videofiles of user (at the mp4 folder of the userdirectory)
     *                      as well as their hashtags (at the hashtags folder of the userdirectory)
     *                      calls setChannelMaps to update the channel data structures
     */
    public void readDirectory() {
        File[] videoFiles = new File(userDirectory + "mp4").listFiles();
        File[] hashtags = new File(userDirectory + "hashtags").listFiles();
        setChannelMaps(videoFiles, hashtags);
    }

    /**
     * method setChannelMaps gets the 2 mentioned parameters and for each couple of files (one in videoFiles, one in hashtags)
     *                       updates the data structures of the Channel of the Publisher of this AppNode by storing the
     *                       videofiles and reading the files that contain the hashtags
     *                       (such as pex userHashtagsPerVideo)
     * @param videoFiles array of videoFiles already published by appNode
     * @param hashtags array of hashtags files associated with the videos
     */
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
        channel.setUserHashtagsPerVideo(userHashtagsPerVideo);
        channel.setAllHashtagsPublished(allHashtagsPublished);
        channel.setUserVideosByHashtag(userVideosByHashtag);
        channel.setAllVideosPublished(allVideosPublished);
    }//reads hashtags from txt file and returns them in list of String

    /**
     * method readHashtagsFile reads the file that has the hashtags, updates the channel data structure of allHashtagsPublished
     *                         and returns an ArrayList of Strings, which will be the hashtags read
     * @param hashtag the File obj to read from
     * @param allHashtagsPublished the data structure to be updated
     * @return
     */
    public ArrayList<String> readHashtagsFile(File hashtag, ArrayList<String> allHashtagsPublished) {
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
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return hashtagList;
    }

    /**
     * method init initializes the AppNode, asks user to assign themselves a username
     *             asks them if they have already published content, so that it is uploaded to the system and they can
     *             work as Publishers, in any case (if they do have content or if they don't have prepublished content)
     *             the AppNode will start working as a Consumer as well (search for/ subscribe to topics) and have the
     *             chance later on to become a Publisher
     */
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
        //If user has preuploaded content we ask for the directory of it (folder which contains mp4 folder and hashtags folder)
        if (response == 1) {
            isPublisher = true;
            System.out.println("Please specify the directory that contains the mp4 and hashtags folders for your existent videos.");
            while (this.userDirectory.isBlank())
                this.userDirectory = appNodeInput.nextLine();
            //and start a new thread to handle BROKER requests that need to pull video from this publisher
            //as well as start the AppNode server
            Thread appNodeServer = new Thread(new Runnable() {
                @Override
                public void run() {
                    readDirectory();
                    openAppNodeServer();
                }
            });
            appNodeServer.start();
        }
        //Creating a consumer actions handler in any use case
        Thread appNodeConsumer = new AppNodeActionsForConsumers(this);
        appNodeConsumer.start();
    }

    /**
     * method openAppNodeServer creates new ServerSocket and awaits for BROKER requests
     * which will be handled by the AppNodeActionsForBrokers handler class
     */
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

    /**
     * method hashTopic hashed the given String topic using SHA-1 encoding
     *        and based on the available brokers given by the hashmap in the parameters
     *        returns the Address obj of the broker this topic should be assigned to
     * @param topic String topic to be hashed (assigned to a BROKER)
     * @param hashIDAssociatedWithBrokers HashMap of the brokerAddresses associated with their ids
     * @return
     */
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

        for (BigInteger id : brokers) {
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
