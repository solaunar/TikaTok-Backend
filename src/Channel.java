import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

public class Channel implements Serializable{
    private volatile String channelName;
    private volatile HashMap<File, ArrayList<String>> userHashtagsPerVideo = new HashMap<>();
    private volatile HashMap<String, ArrayList<File>> userVideosByHashtag = new HashMap<>();
    private volatile ArrayList<String> allHashtagsPublished = new ArrayList<>();
    private volatile ArrayList<File> allVideosPublished = new ArrayList<>();

    public Channel(String channelName) {
        this.channelName = channelName;
    }

    public Channel(Channel channel){
        this.channelName = channel.channelName;
        this.userHashtagsPerVideo = channel.getUserHashtagsPerVideo();
        this.allHashtagsPublished = channel.allHashtagsPublished;
        this.userVideosByHashtag = channel.userVideosByHashtag;
        this.allVideosPublished = channel.allVideosPublished;
    }

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
}