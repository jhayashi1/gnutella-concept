import java.nio.ByteBuffer;

public abstract class Descriptor implements Runnable {
    
    protected byte[] messageID;
    protected byte payloadDesc, TTL, hops;
    protected int payloadLength;

    public Descriptor(byte[] messageID, byte payloadDesc, byte TTL, byte hops, int payloadLength) {
        this.messageID = messageID;
        //System.arraycopy(messageID, 0, this.messageID, 0, messageID.length);

        this.payloadDesc = payloadDesc;
        this.TTL = TTL;
        this.hops = hops;
        this.payloadLength = payloadLength;
    }

    public Descriptor(byte[] descriptor) {
        ByteBuffer buf = ByteBuffer.wrap(descriptor);
        messageID = new byte[16];

        buf.get(messageID, 0, 16);
        payloadDesc = buf.get();
        TTL = buf.get();
        hops = buf.get();
        payloadLength = buf.getInt();
    }

    public byte[] prepareHeader() {
        ByteBuffer buf = ByteBuffer.allocate(23);
        buf.put(messageID);
        buf.put(payloadDesc);
        buf.put(TTL);
        buf.put(hops);
        buf.putInt(payloadLength);

        return buf.array();
    }

    public byte[] getMessageID() {
        return messageID.clone();
    }

    public String getMessageIDAsString() {
        return new String(messageID);
    }

    public byte getPayloadDesc() {
        return payloadDesc;
    }

    public byte getTTL() {
        return TTL;
    }

    public byte getHops() {
        return hops;
    }

    public int getPayloadLength() {
        return payloadLength;
    }

    public void setMessageID(byte[] messageID) {
        this.messageID = messageID;
    }

    public void setTTL(byte TTL) {
        this.TTL = TTL;
    }

    public void setHops(byte hops) {
        this.hops = hops;
    }

}
