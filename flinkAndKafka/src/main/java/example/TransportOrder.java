package example;

import users.User;

import java.time.Instant;

public class TransportOrder implements Deserializable<TransportOrder>{

    private int uid;

    private String address;

    private Instant timestamp;

    private String product;

    /***
     * Constructor for TransportOrder, timestamp is set to current time
     * @param uid user id
     * @param address user address
     * @param product product to be delivered
     */
    public TransportOrder(int uid, String address, String product) {
        this.uid = uid;
        this.address = address;
        this.product = product;
        this.timestamp = Instant.ofEpochSecond(System.currentTimeMillis());
    }

    /***
     * Constructor for TransportOrder, timestamp is set to specified time
     * @param uid user id
     * @param address user address
     * @param product product to be delivered
     * @param timestamp timestamp of the order
     */
    public TransportOrder(int uid, String address, String product, Instant timestamp) {
        this.uid = uid;
        this.address = address;
        this.product = product;
        this.timestamp = timestamp;
    }

    public int getUid() {
        return uid;
    }

    public void setUid(int uid) {
        this.uid = uid;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    @Override
    public String toString() {
        return "transportOrder{" +
                "uid=" + uid +
                ", address='" + address + '\'' +
                ", product='" + product + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }

    /***
     * Custom deserialization method the reverse of .toString().bytes()
     * @return byte array of the string representation of the object
     */
    @Override
    public TransportOrder fromBytes(byte[] bytes) {
        String string = new String(bytes);
        String[] stringSplit = string.split(",");
        return new TransportOrder(
                Integer.parseInt(stringSplit[0]),
                stringSplit[1],
                stringSplit[2],
                Instant.parse(stringSplit[3])); //TODO: questo prima era stringSplit[4], ha più senso così. magari adesso la TimeWindow va
    }

    @Override
    public byte[] serialize() {
        String  value = uid + "," + address + "," + product +  ","  + timestamp;
        return value.getBytes();
    }
}
