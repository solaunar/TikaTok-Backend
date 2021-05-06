import org.apache.tika.metadata.Metadata;

import java.io.File;
import java.io.Serializable;

public class VideoFile implements Serializable {
    File file;
    private byte[] data;
    private Metadata metadata;
    private int chunkID, data_bytes;

    public VideoFile(File file) {
        this.file = file;
    }

    public VideoFile(byte[] data, Metadata metadata, int chunkID, int data_bytes) {
        this.data = data;
        this.metadata = metadata;
        this.chunkID = chunkID;
        this.data_bytes = data_bytes;
    }

    public File getFile() {
        return file;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public int getChunkID() {
        return chunkID;
    }
}
