import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class Gnutella implements Runnable {

    private ArrayList<Servent> servents;
    private ArrayList<String> processedQueries;
    private String ip;
    private String address;
    private int connectPort;
    private int listenPort;
    private boolean attemptConnect;
    private boolean queryFile;
    private int numFiles;
    private Ping ping;
    private String queryString;

    public Gnutella(int listenPort) {
        this.listenPort = listenPort;
        this.ip = Main.LOCAL_IP;

        servents = new ArrayList<Servent>();
        processedQueries = new ArrayList<String>();
        attemptConnect = false;
        queryFile = false;
        ping = new Ping(this);
    }

    public void run() {
        if (attemptConnect) {
            connect();
        }

        //Start sending pings to the network
        Thread pingSender = new Thread(ping);
        pingSender.start();

        //Query for file if specified
        if (queryFile) {
            Thread queryThread = new Thread(new Query(this, queryString));
            queryThread.start();
        }
        
        //Start listening for incoming messages
        listen();
    }

    private void listen() {
        try {
            //Setup socket
            DatagramSocket socket = new DatagramSocket(listenPort);
            System.out.println("Listening on port " + listenPort);

            while (true) {
                //Allocate buffer
                byte[] buf = new byte[23];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);

                //Wait for packets
                socket.receive(packet);

                //Get info from packet
                String packetString = new String(buf, 0, buf.length - 1);
                InetAddress socketAddress = packet.getAddress();
                int socketPort = packet.getPort();

                //If message is GNUTELLA CONNECT/0.4\n\n accept the request
                if (packetString.equals(Main.CONNECT_REQUEST)) {
                    System.out.println("Received packet " + packetString + "from " + socketAddress.toString());
                    DatagramPacket response = new DatagramPacket(Main.CONNECT_RESPONSE.getBytes(), Main.CONNECT_RESPONSE.getBytes().length, socketAddress, socketPort);
                    socket.send(response);
                } else {
                    //Process the descriptor
                    byte[] messageID = new byte[16];
                    System.arraycopy(buf, 0, messageID, 0, messageID.length);
                    byte payloadDesc = buf[16];
                    byte TTL = buf[17];
                    byte hops = buf[18];
                    byte[] payloadLength = new byte[4];
                    System.arraycopy(buf, 19, payloadLength, 0, 4);

                    if (TTL <= 0) {
                        //System.out.println("Message TTL is expired, dropping it...");
                        continue;
                    }

                    //Decrement TTL and increment hops
                    buf[17] -= 1;
                    buf[18] += 1;
                                
                    switch (payloadDesc) {
                        //Ping
                        case 0x00: {
                            int size = ByteBuffer.wrap(payloadLength).getInt();
                            //System.out.println("Received ping header from " + socketAddress.toString());

                            //Receive the ping info
                            byte[] content = new byte[size];
                            packet = new DatagramPacket(content, content.length);
                            socket.receive(packet);

                            //Process the ping and forward it
                            //System.out.println("Received ping content from " + socketAddress.toString());
                            Servent sender = processPing(content);
                            Ping ping = new Ping(this);
                            forwardPing(sender, ping);

                            //Send the pong header
                            String ip = Main.bytesToIP(Arrays.copyOfRange(content, 2, 5 + 1));
                            int port = (int) ByteBuffer.wrap(content).getShort();

                            System.out.println("Sending pong to " + ip + ":" + port);

                            Pong pong = new Pong(this);
                            pong.setMessageID(messageID);
                            byte[] pongHeader = pong.prepareHeader();
                            DatagramSocket pongSocket = new DatagramSocket();
                            DatagramPacket response = new DatagramPacket(pongHeader, pongHeader.length, InetAddress.getByName(ip), port);
                            pongSocket.send(response);

                            //Send the pong content
                            byte[] pongContent = new Pong(this).preparePong(ip, listenPort);
                            response = new DatagramPacket(pongContent, pongContent.length, InetAddress.getByName(ip), port);
                            pongSocket.send(response);

                            pongSocket.close();

                            break;
                        }
                        //Pong
                        case 0x01: {
                            int size = ByteBuffer.wrap(payloadLength).getInt();
                            //System.out.println("Received pong header from " + socketAddress.toString());

                            //Receive the pong info
                            byte[] content = new byte[size];
                            packet = new DatagramPacket(content, content.length);
                            socket.receive(packet);

                            //Process the pong
                            System.out.println("Processing Pong...");
                            processPing(content);

                            break;
                        }
                        //Query
                        case (byte) 0x80:
                            int size = ByteBuffer.wrap(payloadLength).getInt();

                            byte[] content = new byte[size];
                            packet = new DatagramPacket(content, content.length);
                            socket.receive(packet);

                            if (processedQueries.contains(new String(messageID))) {
                                continue;
                            }

                            String qs = new String(content, 6, content.length - 6);

                            //Process the query
                            System.out.println("Processing query for " + qs + "...");
                            File requested = Main.searchFiles(qs, listenPort);

                            if (requested != null) {
                                ByteBuffer bb = ByteBuffer.wrap(content);
                                int port = (int) bb.getShort();
                                String ip = Main.bytesToIP(Arrays.copyOfRange(bb.array(), 2, 5 + 1));
                                sendFile(port, ip, requested);
                                break;
                            }

                            System.out.println("Could not find file " + qs + " forwarding query...");
                            processedQueries.add(new String(messageID));
                            forwardQuery(buf, content);

                            break;
                        default:
                            System.out.println("Unrecognized payload descriptor... dropping packet");
                            break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void connect() {
        //Allocate buffer
        System.out.println("Attempting to connect to " + address + " on port " + connectPort);
        byte[] buf = Main.CONNECT_REQUEST.getBytes();

        try {
            //Setup socket
            DatagramSocket socket = new DatagramSocket();
            DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getByName(address), connectPort);
            socket.send(packet);

            //Prepare for response
            buf = new byte[Main.CONNECT_RESPONSE.getBytes().length];
            packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);

            //Get info from response
            String response = new String(packet.getData());
            System.out.println("Received response: " + response);
            
            if (!response.equals(Main.CONNECT_RESPONSE)) {
                //If an invalid response is received, exit the program
                System.out.println("Invalid response, not connecting to network. Exiting...");
                socket.close();
                System.exit(1);
            } 

            //Close socket
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Servent processPing(byte[] content) {
        ByteBuffer bb = ByteBuffer.wrap(content);
        int port = (int) bb.getShort();
        String ip = Main.bytesToIP(Arrays.copyOfRange(bb.array(), 2, 5 + 1));
        int numFiles = bb.getInt(6);

        //Search for servent in registered servents
        for (Servent servent : servents) {
            if (servent.getIP().equals(ip) && servent.getPort() == port) {
                System.out.println("Servent at port " + port + " already registered, updating time");
                servent.setLastResponse(System.currentTimeMillis());
                return servent;
            }
        }

        //Register servent if not found
        System.out.println("Adding servent to registered servents");
        System.out.println("Content:\n" +
            "Port: " + port + "\n" +
            "IP address: " + ip + "\n" +
            "numFiles: " + numFiles);

        Servent s = new Servent(ip, port, System.currentTimeMillis());
        servents.add(s);

        return s;
    }

    private void forwardPing(Servent sender, Ping ping) {
        int count = 0;
        for (int i = 0; i < servents.size(); i++) {
            Servent servent = servents.get(i);

            //If servent is sender, continue
            if (servent.getIP().equals(sender.getIP()) && servent.getPort() == sender.getPort()) {
                continue;
            }

            //Get ip and port
            String ip = servent.getIP();
            int port = servent.getPort();

            System.out.println("Forwarding ping to " + ip + ":" + port);

            try {
                //Send descriptor header
                byte[] buf = ping.prepareHeader();
                DatagramSocket socket = new DatagramSocket();
                DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getByName(ip), port);
                socket.send(packet);

                //Send content
                byte[] content = ping.preparePing(ip, listenPort);
                packet = new DatagramPacket(content, content.length, InetAddress.getByName(ip), port);
                socket.send(packet);

                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            count++;
        }

        System.out.println("Forwarded ping to " + count + " servents");
    }

    private void forwardQuery(byte[] buf, byte[] content) {
        int count = 0;
        for (int i = 0; i < servents.size(); i++) {
            Servent servent = servents.get(i);

            //Get ip and port
            String ip = servent.getIP();
            int port = servent.getPort();

            System.out.println("Forwarding ping to " + ip + ":" + port);

            try {
                //Send descriptor header
                DatagramSocket socket = new DatagramSocket();
                DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getByName(ip), port);
                socket.send(packet);

                //Send content
                packet = new DatagramPacket(content, content.length, InetAddress.getByName(ip), port);
                socket.send(packet);

                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            count++;
        }

        System.out.println("Forwarded query to " + count + " servents");
    }

    private void sendFile(int port, String ip, File requested) {
        try { 
            Socket s = new Socket(ip, port);
            DataOutputStream outStream = new DataOutputStream(s.getOutputStream());
            byte[] fileBytes = Files.readAllBytes(requested.toPath());
            
            outStream.writeInt(fileBytes.length);
            outStream.write(fileBytes);
            outStream.flush();

            outStream.close();
            s.close();

            System.out.println("Successfully sent file " + requested.getName());
        } catch (Exception e) {

        }
    }

    public void setListenPort(int port) {
        listenPort = port;
    }

    public void setIP(String ip) {
        String[] s = ip.split(":");
        this.address = s[0];
        this.connectPort = Integer.parseInt(s[1]);
        servents.add(new Servent(address, connectPort, -1));
        attemptConnect = true;
    }

    public ArrayList<Servent> getServents() {
        return this.servents;
    }

    public int getListenPort() {
        return listenPort;
    }

    public int getNumFiles() {
        return numFiles;
    }

    public String getIP() {
        return ip;
    }

    public void setNumFiles(int numFiles) {
        this.numFiles = numFiles;
    }

    public void setQueryString(String queryString) {
        this.queryString = queryString;
        queryFile = true;
    }

    public void addToProcessedQueries(String messageID) {
        processedQueries.add(messageID);
    }
}
