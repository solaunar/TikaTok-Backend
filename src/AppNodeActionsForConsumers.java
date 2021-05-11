import org.apache.commons.math3.analysis.function.Add;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class AppNodeActionsForConsumers extends Thread {
    private final int TOPIC_SEARCH = 1;
    private final int SUBSCRIBE_TOPIC = 2;
    private final int REFRESH_SUBSCRIPTIONS = 3;
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
            System.out.println("[Consumer]: Connecting to a random Broker.");
            Random random = new Random();
            int randomBrokerIndex = random.ints(0, Node.BROKER_ADDRESSES.size()).findFirst().getAsInt();
            Address randomBroker = Node.BROKER_ADDRESSES.get(randomBrokerIndex);
            //Address randomBroker = Node.BROKER_ADDRESSES.get(0);
            appNodeRequestSocket = new Socket(randomBroker.getIp(), randomBroker.getPort());
            connection(appNodeRequestSocket);
            Thread updateSub = null;
            while (true) {
                if(appNode.isSubscribed() && !threadUpdateSub){
                    updateSub = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                while (true) {
                                    updateInfoTable();
                                    if (appNode.updateOnSubscriptions()) {
                                        System.out.println(appNode.getSubscribedTopics());
                                    }
                                    sleep(10000);
                                }
                            } catch (IOException | ClassNotFoundException | InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    updateSub.start();
                    threadUpdateSub = true;
                }
                System.out.println("Please select what you'd like to do: ");
                System.out.println("1. Search for a topic (channel or hashtag) as a [Consumer].");
                System.out.println("2. Subscribe to a topic (channel or hashtag) as a [Consumer].");
                System.out.println("3. Refresh for updated content as a [Consumer].");
                System.out.println("4. Post a video as a [Publisher].");
                System.out.println("5. Delete a video as a [Publisher].");
                System.out.println("6. Exit app as a [Consumer].");
                int option = appNode.getAppNodeInput().nextInt();
                String input = "";
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
                    //System.out.println(brokerAddress);
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

                    ArrayList<File> videoList = null;
                    /*out.writeObject("LIST_TOPIC");
                    out.flush();
                    out.writeObject(input);
                    out.flush();
                    out.writeObject(appNode);
                    out.flush();
                    videoList = (ArrayList<File>) in.readObject();*/
                    videoList = new ArrayList<>(appNode.getInfoTable().getAllVideosByTopic().get(input));
                    System.out.println(videoList);
                    if (appNode.getChannel().getAllHashtagsPublished().contains(input))
                        videoList.removeAll(appNode.getChannel().getUserVideosByHashtag().get(input));
                    System.out.println(videoList);
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
                    /*
                    while (!videoList.contains(videoChosen.toLowerCase())){
                        System.out.println("Video does not exist in the list. Please type video name again or type 'EXIT0' to continue using the app.");
                        videoChosen = appNode.getAppNodeInput().nextLine();
                        if(input.toUpperCase().equals("EXIT0"))
                            break;
                    }
                    if (input.toUpperCase().equals("EXIT0")){
                        continue;
                    }
                    */
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
                } else if (option == SUBSCRIBE_TOPIC) {
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
                                if(!appNode.getSubscribedTopics().containsKey(input)){
                                    System.out.println("You are already subscribed to this topic.");
                                    continue;
                                }
                                if (input.equalsIgnoreCase("EXIT0"))
                                    break;
                                brokerAddress = find(input.toLowerCase());
                            }
                        } else{
                            System.out.println("You are already subscribed to this topic.");
                            continue;
                        }
                    } else {
                        System.out.println("You can't subscribe to your own videos.");
                        continue;
                    }
                    if (input.equalsIgnoreCase("EXIT0")) {
                        continue;
                    }
                    System.out.println("[AppNode]: Connecting you to the proper broker.");
                    //System.out.println(brokerAddress);
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
                        System.out.println("[Consumer]: Sending info table request to Broker.");
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
                    //System.out.println(appNode.getSubscribedTopics());
                    appNode.setSubscribed(true);
                    continue;
                } else if (option == REFRESH_SUBSCRIPTIONS) {
                    System.out.println("[Consumer]: Got available video list of subscriptions.");
                } else if (option == POST_VIDEO) {
                    if (appNode.isPublisher()) {
                        System.out.println("[Publisher]: User already registered as publisher.");
                        uploadVideoRequest();
                        System.out.println("[Publisher]: Notifying brokers of new content.");
                        out.writeObject(appNode);
                        out.flush();
                        System.out.println(in.readObject());
                        System.out.println("[Consumer]: Sending info table request to Broker.");
                        out.writeObject("INFO");
                        out.flush();
                        in.readObject();
                        //System.out.println();
                        appNode.setInfoTable((InfoTable) in.readObject());
                        /*System.out.println(appNode.getChannel().getAllHashtagsPublished());
                        System.out.println(appNode.getChannel().getAllVideosPublished());
                        System.out.println(appNode.getChannel().getUserHashtagsPerVideo());
                        System.out.println(appNode.getChannel().getUserVideosByHashtag());*/
                    } else {
                        appNode.setPublisher(true);
                        uploadVideoRequest();
                        System.out.println("[Publisher]: Notifying brokers of new content.");
                        out.writeObject(appNode);
                        out.flush();
                        System.out.println(in.readObject());
                        System.out.println("[Consumer]: Sending info table request to Broker.");
                        out.writeObject("INFO");
                        out.flush();
                        in.readObject();
                        //System.out.println();
                        appNode.setInfoTable((InfoTable) in.readObject());
                        /*System.out.println(appNode.getChannel().getAllHashtagsPublished());
                        System.out.println(appNode.getChannel().getAllVideosPublished());
                        System.out.println(appNode.getChannel().getUserHashtagsPerVideo());
                        System.out.println(appNode.getChannel().getUserVideosByHashtag());*/
                        Thread appNodeServer = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                appNode.openAppNodeServer();
                            }
                        });
                        appNodeServer.start();
                    }
                } else if (option == DELETE_VIDEO) {
                    if (appNode.isPublisher()) {
                        selectPublishedVideos();
                        System.out.println("[Publisher]: Notifying brokers of new content.");
                        out.writeObject(appNode);
                        out.flush();
                        System.out.println(in.readObject());
                        System.out.println("[Consumer]: Sending info table request to Broker.");
                        out.writeObject("INFO");
                        out.flush();
                        in.readObject();
                        //System.out.println();
                        appNode.setInfoTable((InfoTable) in.readObject());
                        /*System.out.println(appNode.getChannel().getAllHashtagsPublished());
                        System.out.println(appNode.getChannel().getAllVideosPublished());
                        System.out.println(appNode.getChannel().getUserHashtagsPerVideo());
                        System.out.println(appNode.getChannel().getUserVideosByHashtag());*/
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
            //System.out.println(appNode.getInfoTable());
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

    public void updateInfoTable() throws IOException, ClassNotFoundException {
        out.writeObject("INFO");
        out.flush();
        in.readObject();
        //System.out.println();
        appNode.setInfoTable((InfoTable) in.readObject());
        //System.out.println(appNode.getInfoTable());
    }
    public void selectPublishedVideos() {
        System.out.println("LIST OF PUBLISHED VIDEOS.");
        HashMap<File, ArrayList<String>> userHashtagsPerVideo = appNode.getChannel().getUserHashtagsPerVideo();
        int index = 0;//userHashtagsPerVideo.size() - 1;
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
        for (int i = 0; i < appNode.getChannel().getAllVideosPublished().size(); i++)
            System.out.println(i + " " + appNode.getChannel().getAllVideosPublished().get(i).getPath());
        System.out.println(appNode.getChannel().getAllVideosPublished().get(choice));
        File toBeDeleted = appNode.getChannel().getAllVideosPublished().get(choice);
        appNode.deleteVideo(toBeDeleted);
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

    public void connection(Socket appNodeRequestSocket) throws IOException, ClassNotFoundException {
        out = new ObjectOutputStream(appNodeRequestSocket.getOutputStream());
        in = new ObjectInputStream(appNodeRequestSocket.getInputStream());
        System.out.println("[AppNode]: Notifying brokers of existence.");
        out.writeObject(appNode);
        out.flush();
        System.out.println(in.readObject());
        System.out.println("[Consumer]: Sending info table request to Broker.");
        out.writeObject("INFO");
        out.flush();
        System.out.println(in.readObject());
        appNode.setInfoTable((InfoTable) in.readObject());
    }

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
}
