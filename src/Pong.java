import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;

public class Pong extends Descriptor {

    Gnutella g;

    public Pong(Gnutella g) {
        super(Main.generateRandomMessageID(), (byte) 0x01, (byte) Main.DEFAULT_TTL, (byte) 0, 10);
        this.g = g;
    }

    public Pong(Gnutella g, byte[] descriptor) {
        super(descriptor);
        this.g = g;
    }

    @Override
    public void run() {
    }

    public byte[] preparePong(String ip, int port) {
        ByteBuffer buf = ByteBuffer.allocate(10);

        //Port, IP, numfiles
        buf.putShort((short) port);
        buf.put(Main.ipToBytes(ip));
        buf.putInt(6, g.getNumFiles());

        return buf.array();
    }
    
}
