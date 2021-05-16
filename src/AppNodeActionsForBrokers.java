import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.mp4.MP4Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;

public class AppNodeActionsForBrokers extends Thread {
    ObjectInputStream in;
    ObjectOutputStream out;
    Socket connection;
    AppNode appNode;

    public AppNodeActionsForBrokers(Socket connection, AppNode appNode){
        this.appNode = appNode;
        this.connection = connection;
        System.out.println("[Publisher]: Connection with broker made. Port: " + connection.getLocalPort());
        try {
            out = new ObjectOutputStream(connection.getOutputStream());
            in = new ObjectInputStream(connection.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run(){
        try{
            //if broker makes a request of type VideoFile then chunk the requested video file and push the chunks one by one to the broker
            Object request = in.readObject();
            if (request instanceof VideoFile){
                System.out.println("Broker asked for a specific video file.");
                File video = ((VideoFile) request).getFile();
                ArrayList<VideoFile> chunks = chunkVideo(video);
                for (VideoFile chunk : chunks) {
                    push(chunk);
                    String response = (String) in.readObject();
                    System.out.println("Sent chunk #" + chunk.getChunkID());
                    if (response.equals("RECEIVED")) continue;
                }
                out.writeObject("NO MORE CHUNKS");
                out.flush();
            }
            this.interrupt();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * method chunkVideo gets the file as a parameter and reads that file chunk by chunk, storing it in an ArrayList
     * @param file File obj of video file requested
     * @return ArrayList<VideoFile> the list of the chunks as VideoFile objects
     */
    public ArrayList<VideoFile> chunkVideo(File file) {
        ArrayList<VideoFile> chunks = new ArrayList<>();
        File peepee = new File(file.getPath());
        int sizeOfChunk = 1024 * 512;// 0.5MB = 512KB
        byte[] buffer;
        try {
            BodyContentHandler handler = new BodyContentHandler();
            Metadata metadata = new Metadata();
            FileInputStream inputstream = new FileInputStream(peepee);
            ParseContext pcontext = new ParseContext();
            MP4Parser MP4Parser = new MP4Parser();
            MP4Parser.parse(inputstream, handler, metadata, pcontext);
            FileInputStream fis = new FileInputStream(file);
            int chunkID = 0;
            int data_bytes;
            for (int i = 0; i < file.length(); i += sizeOfChunk) {
                buffer = new byte[sizeOfChunk];
                data_bytes = fis.read(buffer);
                VideoFile chunk = new VideoFile(buffer, metadata, chunkID, data_bytes);
                chunks.add(chunk);
                chunkID++;
            }
            inputstream.close();
            fis.close();
            return chunks;
        } catch (TikaException | IOException | SAXException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * method push pushes (writes) one chunk on the broker that asked for it
     * @param chunk VideoFile obj that represents a chunk of the video requested
     * @throws IOException
     */
    public void push(VideoFile chunk) throws IOException {
        out.writeObject("SENDING CHUNK");
        out.flush();
        out.writeObject(chunk);
        out.flush();
    }
}
