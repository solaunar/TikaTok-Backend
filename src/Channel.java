import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

public class Channel implements Serializable{
    private String channelName;
    private HashMap<File, ArrayList<String>> userHashtagsPerVideo = new HashMap<>();
    private HashMap<String, ArrayList<File>> userVideosByHashtag = new HashMap<>();
    private ArrayList<String> allHashtagsPublished = new ArrayList<>();
    private ArrayList<File> allVideosPublished = new ArrayList<>();

    public Channel(String channelName) {
        this.channelName = channelName;
    }

    public String getChannelName() {
        return channelName;
    }

    public HashMap<File, ArrayList<String>> getUserHashtagsPerVideo() {
        return userHashtagsPerVideo;
    }

    public void setUserHashtagsPerVideo(HashMap<File, ArrayList<String>> userHashtagsPerVideo) {
        this.userHashtagsPerVideo = userHashtagsPerVideo;
    }

    public HashMap<String, ArrayList<File>> getUserVideosByHashtag() {
        return userVideosByHashtag;
    }

    public void setUserVideosByHashtag(HashMap<String, ArrayList<File>> userVideosByHashtag) {
        this.userVideosByHashtag = userVideosByHashtag;
    }

    public ArrayList<String> getAllHashtagsPublished() {
        return allHashtagsPublished;
    }

    public void setAllHashtagsPublished(ArrayList<String> allHashtagsPublished) {
        this.allHashtagsPublished = allHashtagsPublished;
    }

    public ArrayList<File> getAllVideosPublished() {
        return allVideosPublished;
    }

    public void setAllVideosPublished(ArrayList<File> allVideosPublished) {
        this.allVideosPublished = allVideosPublished;
    }
}