import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Channel class implements Serializable
 * so that it can be shared on Threads
 *
 * Channel represents the users (AppNode) channel
 * contains all the data structures related to the
 * publisher and the user's name (channelname)
 */
public class Channel implements Serializable{

    //username / channelName
    private String channelName;

    //Data related to publisher of this Channel

    //HashMap userHashtagsPerVideo has the video files File objects
    //as keys and values of ArrayList<String>, each value is the list
    //of topics that are related to the File (hashtags posted with this video)
    private HashMap<File, ArrayList<String>> userHashtagsPerVideo = new HashMap<>();

    //HashMap userVideosByHashtag has the String topics
    //as keys and values of ArrayList<File>, each value is the list
    //of videoFiles File that have been tagged with each topic (videos posted with this hashtag)
    private HashMap<String, ArrayList<File>> userVideosByHashtag = new HashMap<>();

    //ArrayList allHashtagsPublished contains all the hashtags that have been published by this Channel
    private ArrayList<String> allHashtagsPublished = new ArrayList<>();

    //ArrayList allVideosPublished contains all the Videos that have been published by this Channel
    private ArrayList<File> allVideosPublished = new ArrayList<>();

    /**
     * Constructor
     * @param channelName the username/channelName to be assigned to this channel
     */
    public Channel(String channelName) {
        this.channelName = channelName;
    }

    /**
     * GETTERS AND SETTERS OF DATA IN CHANNEL
     *
     */
    public synchronized String getChannelName() {
        return channelName;
    }

    public synchronized HashMap<File, ArrayList<String>> getUserHashtagsPerVideo() {
        return userHashtagsPerVideo;
    }

    public synchronized void setUserHashtagsPerVideo(HashMap<File, ArrayList<String>> userHashtagsPerVideo) {
        this.userHashtagsPerVideo = userHashtagsPerVideo;
    }

    public synchronized HashMap<String, ArrayList<File>> getUserVideosByHashtag() {
        return userVideosByHashtag;
    }

    public synchronized void setUserVideosByHashtag(HashMap<String, ArrayList<File>> userVideosByHashtag) {
        this.userVideosByHashtag = userVideosByHashtag;
    }

    public synchronized ArrayList<String> getAllHashtagsPublished() {
        return allHashtagsPublished;
    }

    public synchronized void setAllHashtagsPublished(ArrayList<String> allHashtagsPublished) {
        this.allHashtagsPublished = allHashtagsPublished;
    }

    public synchronized ArrayList<File> getAllVideosPublished() {
        return allVideosPublished;
    }

    public synchronized void setAllVideosPublished(ArrayList<File> allVideosPublished) {
        this.allVideosPublished = allVideosPublished;
    }

    /**
     * @Override of the toString() method
     * @return Readable Channel string
     */
    @Override
    public String toString() {
        return "Channel{" +
                "channelName='" + channelName + '\'' +
                '}';
    }
}