import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class Ping extends Descriptor {
    
    Gnutella g;

    public Ping(Gnutella g) {
        super(Main.generateRandomMessageID(), (byte) 0x00, (byte) Main.DEFAULT_TTL, (byte) 0, 10);
        this.g = g;
    }

    public Ping(Gnutella g, byte[] descriptor) {
        super(descriptor);
        this.g = g;
    }

    @Override
    public void run() {
        while (true) {
            //Generate new id for message
            this.messageID = Main.generateRandomMessageID();

            //Remove inactive servents before sending ping
            ArrayList<Servent> servents = g.getServents();
            removeInactiveServents(servents);

            System.out.println("Sending ping to network...");
            byte[] buf = this.prepareHeader();

            //Loop through servents and ping them
            for (Servent servent : servents) {
                //Get ip and port
                String ip = servent.getIP();
                int port = servent.getPort();
                
                try {
                    //Send ping header
                    DatagramSocket socket = new DatagramSocket();
                    DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getByName(ip), port);
                    socket.send(packet);

                    //Send ping containing port, ip, and number of files shared
                    buf = preparePing(g.getIP(), g.getListenPort());
                    packet = new DatagramPacket(buf, buf.length, InetAddress.getByName(ip), port);
                    socket.send(packet);

                    socket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            //Sleep
            try {
                TimeUnit.SECONDS.sleep((long) Main.TIME_TO_SLEEP);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public byte[] preparePing(String ip, int port) {
        ByteBuffer buf = ByteBuffer.allocate(10);

        //Port, IP, numfiles
        buf.putShort((short) port);
        buf.put(Main.ipToBytes(ip));
        buf.putInt(6, g.getNumFiles());

        return buf.array();
    }

    private void removeInactiveServents(ArrayList<Servent> servents) {
        ArrayList<Servent> inactiveServents = new ArrayList<Servent>();
        for (Servent servent : servents) {
            long lastResponse = servent.getLastResponse();
            
            //If they haven't pinged yet, skip
            if (lastResponse == -1) {
                continue;
            }

            //If time since last ping exceeds 2 * sleep time, remove them
            if (System.currentTimeMillis() - lastResponse > Main.TIME_TO_SLEEP * 2 * 1000) {
                inactiveServents.add(servent);
            }
        }

        //Remove all servents that are inactive
        if (inactiveServents.size() > 0) {
            for (Servent inactive : inactiveServents) {
                System.out.println("Removing inactive servent: " + inactive.getIP() + ":" + inactive.getPort());
            }

            servents.removeAll(inactiveServents);
        }
    }
}
