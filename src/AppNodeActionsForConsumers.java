import org.apache.commons.math3.analysis.function.Add;

import java.io.*;
import java.net.Socket;
import java.util.*;

/**
 * AppNodeActionsForConsumers class extends Thread since it runs the Consumer side of the AppNode obj
 * all of the user interactions/ requests are handled here
 */
public class AppNodeActionsForConsumers extends Thread {
    //codes to handle the user requests by input
    private final int TOPIC_SEARCH = 1;
    private final int SUBSCRIBE_TOPIC = 2;
    private final int POST_VIDEO = 4;
    private final int DELETE_VIDEO = 5;
    private final int EXIT = 6;
    Socket appNodeRequestSocket = null;
    ObjectOutputStream out;
    ObjectInputStream in;
    AppNode appNode;
    boolean threadUpdateSub = false;
    public AppNodeActionsForConsumers(AppNode appNode) {
        this.appNode = appNode;
    }

    @Override
    public void run() {
        try {
            //Connecting the user to a random broker
            System.out.println("[Consumer]: Connecting to a random Broker.");
            Random random = new Random();
            int randomBrokerIndex = random.ints(0, Node.BROKER_ADDRESSES.size()).findFirst().getAsInt();
            Address randomBroker = Node.BROKER_ADDRESSES.get(randomBrokerIndex);
            appNodeRequestSocket = new Socket(randomBroker.getIp(), randomBroker.getPort());
            connection(appNodeRequestSocket);

            //initialize a thread to null, this thread will be used only if the user subscribes to any topics
            Thread updateSub = null;
            while (true) {
                if(appNode.isSubscribed() && !threadUpdateSub){
                    //since the user is subscribed to a topic we create the previously mentioned thread and
                    //ask for an updated InfoTable every 3 seconds (to check if there's any new content related to
                    //the subscribed topics
                    updateSub = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                while (true) {
                                    //ask for the updated info table
                                    updateInfoTable();
                                    //get the topics in which there has been an update
                                    ArrayList<String> topicsUpdated = appNode.updateOnSubscriptions();
                                    if (!topicsUpdated.isEmpty()) {
                                        //if there are indeed topics with updated content then for each one
                                        //print the list of videos
                                        HashMap<String, ArrayList<File>> updatedSubscriptions = appNode.getSubscribedTopics();
                                        System.out.println("Saving the list of videos of topics you are subscribed to...");
                                        for (String topic: topicsUpdated){
                                            printVideoList(topic, updatedSubscriptions.get(topic));
                                        }
                                        //then save all the videos of subscribed topics to the Consumer's/ subscriber's device
                                        saveAllSubscribedVideos(updatedSubscriptions);
                                    }
                                    sleep(3000);
                                }
                            } catch (IOException | ClassNotFoundException | InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    updateSub.start();
                    threadUpdateSub = true;
                }
                //ask user what do they want to do
                System.out.println("Please select what you'd like to do: ");
                System.out.println("1. Search for a topic (channel or hashtag) as a [Consumer].");
                System.out.println("2. Subscribe to a topic (channel or hashtag) as a [Consumer].");
                System.out.println("3. This does nothing, please chose one of the other options, thank you.");
                System.out.println("4. Post a video as a [Publisher].");
                System.out.println("5. Delete a video as a [Publisher].");
                System.out.println("6. Exit app as a [Consumer].");
                int option = appNode.getAppNodeInput().nextInt();
                String input = "";
                //user chose to check out videos of a certain topic without subscribing to it
                //the video list of the videos related will be printed
                //the user then will have to chose one video of the ones on the list to watch
                //the video will be then downloaded in the users device
                if (option == TOPIC_SEARCH) {
                    updateInfoTable();
                    System.out.println("Please type the topic (channel or hashtag) you want to look up...");
                    System.out.println("If you want to look up a hashtag, please add '#' in front of the word.");
                    while (input.isBlank()) {
                        input = appNode.getAppNodeInput().nextLine();
                    }
                    Address brokerAddress = find(input.toLowerCase());
                    if (!input.equals(appNode.getChannel().getChannelName())) {
                        System.out.println("[Consumer]: Searching for requested topic in the info table.");
                        while (brokerAddress == null) {
                            System.out.println("Topic does not exist. Please type in another topic or type 'EXIT0' to continue using the app.");
                            input = appNode.getAppNodeInput().nextLine();
                            if (input.equals(appNode.getChannel().getChannelName())) {
                                System.out.println("You can't request your own videos.");
                                continue;
                            }
                            if (input.equalsIgnoreCase("EXIT0"))
                                break;
                            brokerAddress = find(input.toLowerCase());
                        }
                    } else {
                        System.out.println("You can't request your own videos.");
                        continue;
                    }
                    if (input.equalsIgnoreCase("EXIT0")) {
                        continue;
                    }
                    System.out.println("[AppNode]: Connecting you to the proper broker.");
                    out.writeObject("RC");
                    out.flush();
                    out.writeObject(brokerAddress);
                    out.flush();
                    boolean redirect = in.readBoolean();
                    System.out.println("[Broker]: " + in.readObject());
                    if (redirect) {
                        out.writeObject("EXIT");
                        out.flush();
                        System.out.println("[Broker]: " + in.readObject());
                        in.close();
                        out.close();
                        appNodeRequestSocket.close();
                        appNodeRequestSocket = new Socket(brokerAddress.getIp(), brokerAddress.getPort());
                        connection(appNodeRequestSocket);
                    } else {
                        updateInfoTable();
                    }

                    ArrayList<File> videoList;
                    videoList = new ArrayList<>(appNode.getInfoTable().getAllVideosByTopic().get(input));
                    if (appNode.getChannel().getAllHashtagsPublished().contains(input))
                        videoList.removeAll(appNode.getChannel().getUserVideosByHashtag().get(input));
                    printVideoList(input, videoList);
                    if (videoList.isEmpty()) {
                        System.out.println("Hashtag existed but you are the only one that has posted a video with that tag.");
                        continue;
                    }
                    System.out.println("Please choose one of the videos in the list.");
                    String videoChosen = appNode.getAppNodeInput().nextLine();
                    while (videoChosen.isBlank()) {
                        videoChosen = appNode.getAppNodeInput().nextLine();
                    }
                    out.writeObject(getVideo(videoList, videoChosen.toLowerCase()));
                    out.flush();

                    out.writeObject(appNode);
                    out.flush();

                    System.out.println("[Broker]: " + in.readObject());
                    ArrayList<VideoFile> chunks = new ArrayList<>();
                    while (true) {
                        Object response = in.readObject();
                        if (response.equals("NO MORE CHUNKS")) break;
                        chunks.add((VideoFile) response);
                        System.out.println("Received chunk");
                        out.writeObject("RECEIVED");
                        out.flush();
                    }

                    System.out.println("Please type a path to save the videofile.");
                    String videoPath = appNode.getAppNodeInput().nextLine();
                    while (videoPath.isBlank()) {
                        videoPath = appNode.getAppNodeInput().nextLine();
                    }
                    FileOutputStream fos = new FileOutputStream(videoPath + videoChosen.toLowerCase() + ".mp4");
                    int i = 0;
                    for (VideoFile chunk : chunks) {
                        i++;
                        fos.write(chunk.getData());
                    }
                    fos.close();
                    continue;
                }
                //the user chose to subscribe to a topic,
                //if this is the first time they subscribe to this topic the list of ALL of the currently
                //available videos will be printed and same as in the search by topic option the user will have to pick
                //a video to watch/ download as well as update the broker (then infotable via the broker) that they are now
                //subscribed to the chosen topic
                //if they have been already subscribed to the chosen topic a message will pop up saying that they are already subscribed there
                //likewise they won't be able to subscribe to their own channels (topic usern/channelname)
                else if (option == SUBSCRIBE_TOPIC) {
                    updateInfoTable();
                    System.out.println("Please type the topic (channel or hashtag) you want to subscribe to...");
                    System.out.println("If you want to subscribe to a hashtag, please add '#' in front of the word.");
                    while (input.isBlank()) {
                        input = appNode.getAppNodeInput().nextLine();
                    }
                    Address brokerAddress = find(input.toLowerCase());
                    if (!input.equals(appNode.getChannel().getChannelName())) {
                        if(!appNode.getSubscribedTopics().containsKey(input)) {
                            System.out.println("[Consumer]: Searching for requested topic in the info table.");
                            while (brokerAddress == null) {
                                System.out.println("Topic does not exist. Please type in another topic or type 'EXIT0' to continue using the app.");
                                input = appNode.getAppNodeInput().nextLine();
                                if (input.equals(appNode.getChannel().getChannelName())) {
                                    System.out.println("You can't subscribe to your own videos.");
                                    continue;
                                }
                                if(appNode.getSubscribedTopics().containsKey(input)){
                                    System.out.println("You are already subscribed to this topic.");
                                    continue;
                                }
                                if (input.equalsIgnoreCase("EXIT0"))
                                    break;
                                brokerAddress = find(input.toLowerCase());
                            }
                        } else{
                            System.out.println("You are already subscribed to this topic.");
                            break;
                        }
                    } else {
                        System.out.println("You can't subscribe to your own videos.");
                        continue;
                    }
                    if (input.equalsIgnoreCase("EXIT0")) {
                        continue;
                    }
                    System.out.println("[AppNode]: Connecting you to the proper broker.");
                    out.writeObject("RC");
                    out.flush();
                    out.writeObject(brokerAddress);
                    out.flush();
                    boolean redirect = in.readBoolean();
                    System.out.println("[Broker]: " + in.readObject());
                    if (redirect) {
                        out.writeObject("EXIT");
                        out.flush();
                        System.out.println("[Broker]: " + in.readObject());
                        in.close();
                        out.close();
                        appNodeRequestSocket.close();
                        appNodeRequestSocket = new Socket(brokerAddress.getIp(), brokerAddress.getPort());
                        connection(appNodeRequestSocket);
                    } else {
                        out.writeObject("INFO");
                        out.flush();
                        System.out.println(in.readObject());
                        appNode.setInfoTable((InfoTable) in.readObject());
                    }

                    out.writeObject("REG");
                    out.flush();
                    out.writeObject(appNode);
                    out.flush();
                    out.writeObject(input);
                    out.flush();
                    ArrayList<File> subscribedVideos = new ArrayList<>(appNode.getInfoTable().getAllVideosByTopic().get(input));
                    if (appNode.getChannel().getAllHashtagsPublished().contains(input)){
                        subscribedVideos.removeAll(appNode.getChannel().getUserVideosByHashtag().get(input));
                    }
                    appNode.getSubscribedTopics().put(input, subscribedVideos);
                    appNode.setSubscribed(true);

                    ArrayList<File> videoList;
                    videoList = new ArrayList<>(appNode.getInfoTable().getAllVideosByTopic().get(input));
                    if (appNode.getChannel().getAllHashtagsPublished().contains(input))
                        videoList.removeAll(appNode.getChannel().getUserVideosByHashtag().get(input));
                    printVideoList(input, videoList);
                    if (videoList.isEmpty()) {
                        System.out.println("Hashtag existed but you are the only one that has posted a video with that tag.");
                        continue;
                    }
                    System.out.println("Please choose one of the videos in the list.");
                    String videoChosen = appNode.getAppNodeInput().nextLine();
                    while (videoChosen.isBlank()) {
                        videoChosen = appNode.getAppNodeInput().nextLine();
                    }
                    out.writeObject(getVideo(videoList, videoChosen.toLowerCase()));
                    out.flush();

                    out.writeObject(appNode);
                    out.flush();

                    System.out.println("[Broker]: " + in.readObject());
                    ArrayList<VideoFile> chunks = new ArrayList<>();
                    while (true) {
                        Object response = in.readObject();
                        if (response.equals("NO MORE CHUNKS")) break;
                        chunks.add((VideoFile) response);
                        System.out.println("Received chunk");
                        out.writeObject("RECEIVED");
                        out.flush();
                    }

                    System.out.println("Please type a path to save the videofile.");
                    String videoPath = appNode.getAppNodeInput().nextLine();
                    while (videoPath.isBlank()) {
                        videoPath = appNode.getAppNodeInput().nextLine();
                    }
                    FileOutputStream fos = new FileOutputStream(videoPath + videoChosen.toLowerCase() + ".mp4");
                    int i = 0;
                    for (VideoFile chunk : chunks) {
                        i++;
                        fos.write(chunk.getData());
                    }
                    fos.close();
                    continue;
                }
                //user has chosen to upload a video
                //if the video has already been uploaded there will be displayed a message about it
                //if the video is a new one then the Channels data structures are updated by using the updateVideoRequest() method
                //if the AppNode was already a Publisher then there won't be any need to reopen the AppNode publisher server
                //if he wasn't a Publisher before this upload then a new thread will start running so that the AppNode
                //can accept broker video pull requests as a server
                else if (option == POST_VIDEO) {
                    if (appNode.isPublisher()) {
                        System.out.println("[Publisher]: User already registered as publisher.");
                        uploadVideoRequest();
                        System.out.println("[Publisher]: Notifying brokers of new content.");
                        out.writeObject(appNode);
                        out.flush();
                        ArrayList<String> tempAllHashtagsPublished = new ArrayList<>();
                        tempAllHashtagsPublished.addAll(appNode.getChannel().getAllHashtagsPublished());
                        out.writeObject(tempAllHashtagsPublished);
                        out.flush();
                        ArrayList<File> tempAllVideosPublished = new ArrayList<>();
                        tempAllVideosPublished.addAll(appNode.getChannel().getAllVideosPublished());
                        out.writeObject(tempAllVideosPublished);
                        out.flush();
                        HashMap<String, ArrayList<File>> tempUserVideosByHashtag = updateUserVideosByHashtag();
                        out.writeObject(tempUserVideosByHashtag);
                        out.flush();
                        boolean isPublisher = appNode.isPublisher();
                        out.writeBoolean(isPublisher);
                        out.flush();
                        System.out.println(in.readObject());
                        System.out.println("[Consumer]: Sending info table request to Broker.");
                        out.writeObject("INFO");
                        out.flush();
                        in.readObject();
                        appNode.setInfoTable((InfoTable) in.readObject());
                    } else {
                        appNode.setPublisher(true);
                        uploadVideoRequest();
                        System.out.println("[Publisher]: Notifying brokers of new content.");
                        out.writeObject(appNode);
                        out.flush();
                        ArrayList<String> tempAllHashtagsPublished = new ArrayList<>();
                        tempAllHashtagsPublished.addAll(appNode.getChannel().getAllHashtagsPublished());
                        out.writeObject(tempAllHashtagsPublished);
                        out.flush();
                        ArrayList<File> tempAllVideosPublished = new ArrayList<>();
                        tempAllVideosPublished.addAll(appNode.getChannel().getAllVideosPublished());
                        out.writeObject(tempAllVideosPublished);
                        out.flush();
                        HashMap<String, ArrayList<File>> tempUserVideosByHashtag = updateUserVideosByHashtag();
                        out.writeObject(tempUserVideosByHashtag);
                        out.flush();
                        boolean isPublisher = appNode.isPublisher();
                        out.writeBoolean(isPublisher);
                        out.flush();
                        System.out.println(in.readObject());
                        System.out.println("[Consumer]: Sending info table request to Broker.");
                        out.writeObject("INFO");
                        out.flush();
                        in.readObject();
                        appNode.setInfoTable((InfoTable) in.readObject());
                        Thread appNodeServer = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                appNode.openAppNodeServer();
                            }
                        });
                        appNodeServer.start();
                    }
                }
                //user has chosen to delete a video
                //the list of his published videos is printed so that he can chose which one he wants to delete
                //then the broker is notified of this action so that he can update the infotable
                else if (option == DELETE_VIDEO) {
                    if (appNode.isPublisher()) {
                        File toBeDeleted = selectPublishedVideos();
                        out.writeObject("DELETE");
                        out.flush();
                        System.out.println("[Publisher]: Notifying brokers of updated content.");
                        out.writeObject(appNode);
                        out.flush();
                        out.writeObject(toBeDeleted);
                        out.flush();
                        ArrayList<String> tempAllHashtagsPublished = new ArrayList<>();
                        tempAllHashtagsPublished.addAll(appNode.getChannel().getAllHashtagsPublished());
                        out.writeObject(tempAllHashtagsPublished);
                        out.flush();
                        System.out.println(in.readObject());
                        System.out.println("[Consumer]: Sending info table request to Broker.");
                        out.writeObject("INFO");
                        out.flush();
                        in.readObject();
                        appNode.setInfoTable((InfoTable) in.readObject());
                    } else {
                        System.out.println("[Publisher]: User is not registered as publisher, which means he doesn't have videos to delete.");
                        continue;
                    }
                } else if (option == EXIT) {
                    out.writeObject("EXIT");
                    out.flush();
                    System.out.println("[Broker]: " + in.readObject());
                    if(updateSub!=null)
                        updateSub.interrupt();
                    in.close();
                    out.close();
                    appNodeRequestSocket.close();
                    break;
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                in.close();
                out.close();
                appNodeRequestSocket.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    /**
     * method updateInfoTable sends the broker a request for an updated version of the infoTable
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public void updateInfoTable() throws IOException, ClassNotFoundException {
        out.writeObject("INFO");
        out.flush();
        in.readObject();
        appNode.setInfoTable((InfoTable) in.readObject());
    }

    public File selectPublishedVideos() {
        System.out.println("LIST OF PUBLISHED VIDEOS.");
        HashMap<File, ArrayList<String>> userHashtagsPerVideo = appNode.getChannel().getUserHashtagsPerVideo();
        int index = 0;
        for (File videoPublished : userHashtagsPerVideo.keySet()) {
            String videoTitle = videoPublished.getPath();
            videoTitle = videoTitle.substring(videoTitle.lastIndexOf('\\') + 1, videoTitle.indexOf(".mp4"));
            System.out.println(index + ". " + videoTitle);
            System.out.println("\tHashtags of video: " + userHashtagsPerVideo.get(videoPublished));
            System.out.println("----------------------------------");
            index++;
        }
        System.out.println("Please type in the number of the video you want to delete.");
        int choice = -1;
        choice = appNode.getAppNodeInput().nextInt();
        while (choice == -1) {
            System.out.println("This is not a number off the list.");
            choice = appNode.getAppNodeInput().nextInt();
        }
        File toBeDeleted = appNode.getChannel().getAllVideosPublished().get(choice);
        appNode.deleteVideo(toBeDeleted);
        return toBeDeleted;
    }

    public void printVideoList(String topic, ArrayList<File> videoFiles){
        if (topic.startsWith("#")){
            System.out.println("VIDEOS PUBLISHED WITH HASHTAG: "+topic);
        } else{
            System.out.println("VIDEOS PUBLISHED BY CHANNEL: "+topic);
        }
        for (File videoFile :videoFiles){
            String videoTitle = videoFile.getPath();
            videoTitle = videoTitle.substring(videoTitle.lastIndexOf('\\') + 1, videoTitle.indexOf(".mp4"));
            System.out.println(videoTitle);
        }
        System.out.println("----------------------------------");
    }

    public void uploadVideoRequest() {
        System.out.println("Please type in the directory of the file you want to post.\n" +
                "Format: C:/.../video.mp4");
        String directory = "";
        directory = appNode.getAppNodeInput().nextLine();
        while (directory.isBlank()) {
            directory = appNode.getAppNodeInput().nextLine();
        }
        System.out.println("Please type in the hashtags you want to associate with this video.\n" +
                "Format: #hashtag1,#hashtag2,#hashtag3,... (all hashtags split by commas)");
        String hashtagsInline = "";
        hashtagsInline = appNode.getAppNodeInput().nextLine();
        while (hashtagsInline.isBlank()) {
            hashtagsInline = appNode.getAppNodeInput().nextLine();
        }
        ArrayList<String> hashtags = new ArrayList<>(Arrays.asList(hashtagsInline.toLowerCase().replace(" ", "").split(",")));
        appNode.uploadVideo(directory, hashtags);
    }

    /**
     * method connection created streams from the Socket streams
     *                   notifies broker of the AppNode existance and connection
     *                   and asks for InfoTable
     *                   method is used in every new connection made with broker (that includes redirections)
     * @param appNodeRequestSocket Socket of established connection with broker
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public void connection(Socket appNodeRequestSocket) throws IOException, ClassNotFoundException {
        out = new ObjectOutputStream(appNodeRequestSocket.getOutputStream());
        in = new ObjectInputStream(appNodeRequestSocket.getInputStream());
        System.out.println("[AppNode]: Notifying brokers of existence.");
        out.writeObject(appNode);
        out.flush();
        ArrayList<String> tempAllHashtagsPublished = new ArrayList<>();
        tempAllHashtagsPublished.addAll(appNode.getChannel().getAllHashtagsPublished());
        out.writeObject(tempAllHashtagsPublished);
        out.flush();
        ArrayList<File> tempAllVideosPublished = new ArrayList<>();
        tempAllVideosPublished.addAll(appNode.getChannel().getAllVideosPublished());
        out.writeObject(tempAllVideosPublished);
        out.flush();
        HashMap<String, ArrayList<File>> tempUserVideosByHashtag = new HashMap<>();
        tempUserVideosByHashtag.putAll(appNode.getChannel().getUserVideosByHashtag());
        out.writeObject(tempUserVideosByHashtag);
        out.flush();
        boolean isPublisher = appNode.isPublisher();
        out.writeBoolean(isPublisher);
        out.flush();
        System.out.println(in.readObject());
        System.out.println("[Consumer]: Sending info table request to Broker.");
        out.writeObject("INFO");
        out.flush();
        System.out.println(in.readObject());
        appNode.setInfoTable((InfoTable) in.readObject());
    }

    /**
     * method find iterates through the infotable data structure to check if topic exists and returns an Address obj
     * @param topic String topic that user asked to search or subscribe to
     * @return Address obj if the topic does exist it will be one of the broker addresses, if not then null is returned
     */
    public Address find(String topic) {
        HashMap<Address, ArrayList<String>> topicsAssociatedWithBrokers = appNode.getInfoTable().getTopicsAssociatedWithBrokers();
        Iterator it = topicsAssociatedWithBrokers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            ArrayList<String> brokerTopics = (ArrayList<String>) pair.getValue();
            for (String topicRegistered : brokerTopics) {
                if (topicRegistered.equals(topic)) {
                    return (Address) pair.getKey();
                }
            }
        }
        return null;
    }

    public VideoFile getVideo(ArrayList<File> videoList, String userVideoRequest) {
        for (File video : videoList) {
            if (video.getPath().toLowerCase().contains(userVideoRequest)) {
                VideoFile videoFile = new VideoFile(video);
                return videoFile;
            }
        }
        return null;
    }

    public HashMap<String, ArrayList<File>> updateUserVideosByHashtag(){
        HashMap<String, ArrayList<File>> objUserVideosByHashtag = appNode.getChannel().getUserVideosByHashtag();
        HashMap<String, ArrayList<File>> newUserVideosByHashtag = new HashMap<>();
        for (String hashtag: objUserVideosByHashtag.keySet()){
            ArrayList<File> newVideosAssociated = new ArrayList<>();
            newVideosAssociated.addAll(objUserVideosByHashtag.get(hashtag));
            newUserVideosByHashtag.put(hashtag, newVideosAssociated);
        }
        return newUserVideosByHashtag;
    }

    /**
     * method saveAllSubscribedVideos requests a videoFile from broker (which will pull from publisher) for each of the updated video files
     * @param updatedSubscriptions HashMap of the topics with their associated videos
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public void saveAllSubscribedVideos(HashMap<String, ArrayList<File>> updatedSubscriptions) throws IOException, ClassNotFoundException {

        for (String topic : updatedSubscriptions.keySet()){
            Address brokerAddress = find(topic);
            out.writeObject("RC");
            out.flush();
            out.writeObject(brokerAddress);
            out.flush();
            boolean redirect = in.readBoolean();
            System.out.println("[Broker]: " + in.readObject());
            if (redirect) {
                out.writeObject("EXIT");
                out.flush();
                System.out.println("[Broker]: " + in.readObject());
                in.close();
                out.close();
                appNodeRequestSocket.close();
                appNodeRequestSocket = new Socket(brokerAddress.getIp(), brokerAddress.getPort());
                connection(appNodeRequestSocket);
            } else {
                //System.out.println("[Consumer]: Sending info table request to Broker.");
                out.writeObject("INFO");
                out.flush();
                System.out.println(in.readObject());
                appNode.setInfoTable((InfoTable) in.readObject());
            }
            for (File videoFile : updatedSubscriptions.get(topic)){
                System.out.println(videoFile);
                if (appNode.getChannel().getAllVideosPublished().contains(videoFile)) {
                    System.out.println("Found my file");
                    continue;
                }
                VideoFile video = new VideoFile(videoFile);
                out.writeObject(video);
                out.flush();

                out.writeObject(appNode);
                out.flush();

                System.out.println("[Broker]: " + in.readObject());
                ArrayList<VideoFile> chunks = new ArrayList<>();
                while (true) {
                    Object response = in.readObject();
                    if (response.equals("NO MORE CHUNKS")) break;
                    chunks.add((VideoFile) response);
                    System.out.println("Received chunk");
                    out.writeObject("RECEIVED");
                    out.flush();
                }
                String videoTitle = videoFile.getPath();
                videoTitle = videoTitle.substring(videoTitle.lastIndexOf('\\') + 1, videoTitle.indexOf(".mp4"));
                String videoPath = "dataset/downloadedFromSub/" + appNode.getChannel().getChannelName() + "/";
                File theDir = new File(videoPath);
                if (!theDir.exists()){
                    theDir.mkdirs();
                }
                FileOutputStream fos = new FileOutputStream(videoPath + videoTitle+".mp4");
                int i = 0;
                for (VideoFile chunk : chunks) {
                    i++;
                    fos.write(chunk.getData());
                }
                fos.close();
            }
        }

    }
}
