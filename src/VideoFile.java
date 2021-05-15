import org.apache.tika.metadata.Metadata;
import java.io.File;
import java.io.Serializable;

/**
 * VideoFile class implements Serializable
 * so that it can be shared on Threads
 *
 * VideoFile is used in requesting videos, pushing
 * and pulling
 */
public class VideoFile implements Serializable {

    //file of the Video
    File file;

    //byte array data: stores the read data from FileInputStream
    //for each chunk
    private byte[] data;

    //Metadata of file extracted by Apache Tika
    private Metadata metadata;

    //ID of this VideoFile chunk
    private int chunkID, data_bytes;

    /**
     * Constructor
     * @param file
     */
    public VideoFile(File file) {
        this.file = file;
    }

    /**
     * Constructor for chunk
     * @param data
     * @param metadata
     * @param chunkID
     * @param data_bytes
     */

    public VideoFile(byte[] data, Metadata metadata, int chunkID, int data_bytes) {
        this.data = data;
        this.metadata = metadata;
        this.chunkID = chunkID;
        this.data_bytes = data_bytes;
    }

    /**
     * GETTERS OF VIDEOFILE DATA
     * @return
     */
    public File getFile() {
        return file;
    }

    public byte[] getData() {
        return data;
    }

    public int getChunkID() {
        return chunkID;
    }
}
