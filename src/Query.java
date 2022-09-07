import java.io.DataInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;

public class Query extends Descriptor {

    private String queryString;
    private Gnutella g;

    public Query(Gnutella g, String queryString) {
        super(Main.generateRandomMessageID(), (byte) 0x80, (byte) Main.DEFAULT_TTL, (byte) 0, queryString.length() + 6);
        this.g = g;
        this.queryString = queryString;
    }

    public byte[] prepareQuery() {
        ByteBuffer buf = ByteBuffer.allocate(queryString.length() * 2 + 6);

        buf.putShort((short) g.getListenPort());
        buf.put(Main.ipToBytes(g.getIP()));
        buf.put(queryString.getBytes());

        return buf.array();
    }

    @Override
    public void run() {
        System.out.println("Sending query to network...");
        g.addToProcessedQueries(this.getMessageIDAsString());

        for (Servent servent : g.getServents()) {
            //Get ip and port
            String ip = servent.getIP();
            int port = servent.getPort();
            
            try {
                //Send query header
                byte[] buf = this.prepareHeader();
                DatagramSocket socket = new DatagramSocket();
                DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getByName(ip), port);
                socket.send(packet);

                //Send query containing port, ip, and query string
                buf = prepareQuery();
                packet = new DatagramPacket(buf, buf.length, InetAddress.getByName(ip), port);
                socket.send(packet);

                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        System.out.println("Waiting for tcp connections for query...");

        try {
            //Setup tcp socket for receiving files
            ServerSocket serverSocket = new ServerSocket(g.getListenPort());
            Socket s = serverSocket.accept();
            DataInputStream inStream  = new DataInputStream(s.getInputStream());

            System.out.println("Receiving file...");

            //Get size of file and read it
            int length = inStream.readInt();
            byte[] file = new byte[length];
            inStream.read(file);

            //Write file to directory
            FileOutputStream outStream = new FileOutputStream(Main.DIRECTORY + g.getListenPort() + "/" + queryString);
            outStream.write(file);

            System.out.println("Sucessfully received file " + queryString);

            inStream.close();
            outStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
