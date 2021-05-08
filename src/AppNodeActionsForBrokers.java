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
            Object request = in.readObject();
            if (request instanceof VideoFile){
                System.out.println("Broker asked for a specific video file.");
                File video = ((VideoFile) request).getFile();
                ArrayList<VideoFile> chunks = chunkVideo(video);
                for (VideoFile chunk : chunks) {
                    push(chunk);
                    sleep(10000);
                    String response = (String) in.readObject();
                    System.out.println("Sent chunk #" + chunk.getChunkID());
                    if (response.equals("RECEIVED")) continue;
                }
                out.writeObject("NO MORE CHUNKS");
                out.flush();
            }
            this.interrupt();
        } catch (IOException | ClassNotFoundException | InterruptedException e) {
            e.printStackTrace();
        }
    }


    public ArrayList<VideoFile> chunkVideo(File file) {
        ArrayList<VideoFile> chunks = new ArrayList<>();
        int sizeOfChunk = 1024 * 512;// 0.5MB = 512KB
        byte[] buffer;
        try {
            BodyContentHandler handler = new BodyContentHandler();
            Metadata metadata = new Metadata();
            FileInputStream inputstream = new FileInputStream(file);
            ParseContext pcontext = new ParseContext();
            MP4Parser MP4Parser = new MP4Parser();
            MP4Parser.parse(inputstream, handler, metadata, pcontext);
            FileInputStream fis = new FileInputStream(file);
            int chunkID = 0;
            int data_bytes;
            for (int i = 0; i < file.length(); i += sizeOfChunk) {
                buffer = new byte[sizeOfChunk];
                data_bytes = fis.read(buffer);
                //System.out.println(data_bytes);
                //System.out.println(buffer);
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

    public void push(VideoFile chunk) throws IOException {
        out.writeObject("SENDING CHUNK");
        out.flush();
        out.writeObject(chunk);
        out.flush();
    }
}
