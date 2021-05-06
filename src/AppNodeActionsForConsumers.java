import org.apache.commons.math3.analysis.function.Add;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class AppNodeActionsForConsumers extends Thread{
    private final int TOPIC_SEARCH = 1;
    Socket appNodeRequestSocket = null;
    ObjectOutputStream out;
    ObjectInputStream in;
    AppNode appNode;

    public AppNodeActionsForConsumers(AppNode appNode){
        this.appNode = appNode;
    }

    @Override
    public void run(){
        try{
            System.out.println("[Consumer]: Connecting to a random Broker.");
            Random random = new Random();
            int randomBrokerIndex = random.ints(0, Node.BROKER_ADDRESSES.size()).findFirst().getAsInt();
            Address randomBroker = Node.BROKER_ADDRESSES.get(randomBrokerIndex);
            //Address randomBroker = Node.BROKER_ADDRESSES.get(0);
            appNodeRequestSocket = new Socket(randomBroker.getIp(), randomBroker.getPort());
            connection(appNodeRequestSocket);
            while (true){
                System.out.println("Please select what you'd like to do: ");
                System.out.println("1. Search for a topic (channel or hashtag) as a [Consumer].");
                System.out.println("2. Subscribe to a topic (channel or hashtag) as a [Consumer].");
                System.out.println("3. Unsubscribe from a topic (channel or hashtag) as a [Consumer].");
                System.out.println("4. Refresh for updated content as a [Consumer].");
                System.out.println("5. Post a video as a [Publisher].");
                System.out.println("6. Delete a video as a [Publisher].");
                System.out.println("7. Exit app as a [Consumer].");
                int option = appNode.getAppNodeInput().nextInt();
                String input = "";
                if (TOPIC_SEARCH == 1){
                    System.out.println("Please type the topic (channel or hashtag) you want to look up...");
                    System.out.println("If you want to look up a hashtag, please add '#' in front of the word.");
                    while (input.isBlank()){
                        input = appNode.getAppNodeInput().nextLine();
                    }
                    System.out.println("[Consumer]: Searching for requested topic in the info table.");
                    Address brokerAddress = find(input.toLowerCase());
                    while (brokerAddress == null){
                        System.out.println("Topic does not exist. Please type in another topic or type 'EXIT0' to continue using the app.");
                        input = appNode.getAppNodeInput().nextLine();
                        if(input.toUpperCase().equals("EXIT0"))
                            break;
                        brokerAddress = find(input.toLowerCase());
                    }
                    if (input.toUpperCase().equals("EXIT0")){
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
                    if (redirect){
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

                    ArrayList<File> videoList = null;
                    if (input.startsWith("#")){
                        out.writeObject("LIST_HASHTAG");
                        out.flush();
                        out.writeObject(input);
                        out.flush();
                        videoList = printHashtagVideoList((HashMap<String, ArrayList<File>>) in.readObject());
                    } else {
                        out.writeObject("LIST_CHANNEL");
                        out.flush();
                        out.writeObject(input);
                        out.flush();
                        videoList = printChannelVideoList((HashMap<File, ArrayList<String>>) in.readObject());
                    }
                    System.out.println("Please choose one of the videos in the list.");
                    String videoChosen = appNode.getAppNodeInput().nextLine();
                    while (videoChosen.isBlank()){
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

                    System.out.println("[Broker]: "+in.readObject());
                    ArrayList<VideoFile> chunks = new ArrayList<>();
                    while (true){
                        Object response = in.readObject();
                        if (response.equals("NO MORE CHUNKS")) break;
                        chunks.add((VideoFile) response);
                        System.out.println("Received chunk");
                        out.writeObject("RECEIVED");
                        out.flush();
                    }

                    System.out.println("Please type a path to save the videofile.");
                    String videoPath = appNode.getAppNodeInput().nextLine();
                    while (videoPath.isBlank()){
                        videoPath = appNode.getAppNodeInput().nextLine();
                    }
                    FileOutputStream fos = new FileOutputStream(videoPath+videoChosen.toLowerCase()+".mp4");
                    int i =0;
                    for (VideoFile chunk: chunks){
                        i++;
                        //System.out.println(chunk.getData());
                        //FileOutputStream foschunk = new FileOutputStream(videoPath+videoChosen.toLowerCase()+"chunk"+i+".mp4");
                        fos.write(chunk.getData());
                        //foschunk.write(chunk.getData());
                        //foschunk.close();
                    }
                    fos.close();
                    /*System.out.println("Do you want to play the video NOW?" +
                            "1. Yes.\n" +
                            "2. No.");
                    option = appNode.getAppNodeInput().nextInt();
                    Process process;
                    if (option == 1) {
                        process = Runtime.getRuntime().exec("cd " + videoPath + videoChosen.toLowerCase() + ".mp4");
                    }*/
                    continue;
                }
            }
            //System.out.println(appNode.getInfoTable());
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public ArrayList<File> printChannelVideoList(HashMap<File, ArrayList<String>> userHashtagsPerVideo) {
        System.out.println("LIST OF VIDEOS OF REQUESTED CHANNEL.");
        ArrayList<File> videoList = new ArrayList<>();
        Iterator it = userHashtagsPerVideo.entrySet().iterator();
        while (it.hasNext()){
            Map.Entry pair = (Map.Entry) it.next();
            File videoFile = (File) pair.getKey();
            videoList.add(videoFile);
            String videoTitle = videoFile.getPath();
            videoTitle = videoTitle.substring(videoTitle.lastIndexOf('\\') + 1, videoTitle.indexOf(".mp4"));
            System.out.println(videoTitle);
            System.out.println("\tHashtags of video: " + pair.getValue());
            System.out.println("----------------------------------");
        }
        return videoList;
    }

    public void connection(Socket appNodeRequestSocket) throws IOException, ClassNotFoundException {;
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
    public Address find(String topic){
        HashMap<Address, ArrayList<String>> topicsAssociatedWithBrokers = appNode.getInfoTable().getTopicsAssociatedWithBrokers();
        Iterator it = topicsAssociatedWithBrokers.entrySet().iterator();
        while (it.hasNext()){
            Map.Entry pair = (Map.Entry) it.next();
            ArrayList<String> brokerTopics = (ArrayList<String>) pair.getValue();
            for (String topicRegistered : brokerTopics){
                if (topicRegistered.equals(topic)){
                    return (Address) pair.getKey();
                }
            }
        }
        return null;
    }
    public ArrayList<File> printHashtagVideoList(HashMap<String, ArrayList<File>> hashtagVideoList){
        System.out.println("LIST OF VIDEOS OF REQUESTED HASHTAG.");
        ArrayList<File> files = new ArrayList<>();
        Iterator it = hashtagVideoList.entrySet().iterator();
        while (it.hasNext()){
            Map.Entry pair = (Map.Entry) it.next();
            String channelName = (String) pair.getKey();
            System.out.println("Channel: "+ channelName+ " posted: ");
            ArrayList<File> publisherVideoByHashtag = (ArrayList<File>) pair.getValue();
            for (File videoFile : publisherVideoByHashtag){
                files.add(videoFile);
                String videoTitle = videoFile.getPath();
                videoTitle = videoTitle.substring(videoTitle.lastIndexOf('\\') + 1, videoTitle.indexOf(".mp4"));
                System.out.println("\t"+videoTitle.toUpperCase());
            }
            System.out.println("----------------------------------");
        }
        return files;
    }
    public VideoFile getVideo(ArrayList<File> videoList, String userVideoRequest) {
        for (File video: videoList){
            if (video.getPath().contains(userVideoRequest)) {
                VideoFile videoFile = new VideoFile(video);
                return videoFile;
            }
        }
        return null;
    }
}
