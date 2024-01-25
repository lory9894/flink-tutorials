package events;


import example.Deserializable;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;

@JsonPropertyOrder({ "uid", "credit_card", "invoice_address", "price", "product", "timestamp" })
public class Purchase implements Deserializable<Purchase> {
    //uid,credit card,invoice address,price,product
    private int uid;
    private Long credit_card;
    private String invoice_address;
    private String price;
    private String product;
    private Instant timestamp;

    public Purchase(){

    }

    public Purchase(int uid, Long credit_card, String invoice_address, String price, String product, Instant timestamp){
        this.uid = uid;
        this.credit_card = credit_card;
        this.invoice_address = invoice_address;
        this.price = price;
        this.product = product;
        this.timestamp = timestamp;
    }

    public long getEventTimeMillis() {
        return timestamp.toEpochMilli();
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public int getUid() {
        return uid;
    }

    public void setUid(int uid) {
        this.uid = uid;
    }

    public Long getCredit_card() {
        return credit_card;
    }

    public void setCredit_card(Long credit_card) {
        this.credit_card = credit_card;
    }

    public String getInvoice_address() {
        return invoice_address;
    }

    public void setInvoice_address(String invoice_address) {
        this.invoice_address = invoice_address;
    }

    public String getPrice() {
        return price;
    }

    public Double getPriceDouble() {
        return Double.parseDouble(price.replace("$",""));
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    @Override
    public Purchase fromBytes(byte[] value){
        String string = new String(value);
        String[] stringSplit = string.split(",");
        return new Purchase(
                Integer.parseInt(stringSplit[0]),
                Long.parseLong(stringSplit[1]),
                stringSplit[2],
                stringSplit[3],
                stringSplit[4],
                Instant.parse(stringSplit[5])
        );
    }


    @Override
    public String toString() {
        return "Order{" +
                "uid=" + uid +
                ", credit_card='" + credit_card + '\'' +
                ", invoice_address='" + invoice_address + '\'' +
                ", price='" + price + '\'' +
                ", product='" + product + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }

    @Override
    public byte[] serialize(){
        String value = uid + ","+ credit_card + "," + invoice_address + "," + price + "," + product + "," + timestamp.toString();
        return value.getBytes();
    }
}
