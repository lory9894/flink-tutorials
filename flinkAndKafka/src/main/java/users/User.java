package users;

import example.Deserializable;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.List;

@JsonPropertyOrder({ "id", "first_name", "last_name", "email", "gender", "ip_address", "address" })
public class User implements Deserializable<User> {

    // id,first_name,last_name,email,gender,ip_address,address
    private int id;
    private String first_name;
    private String last_name;
    private String email;
    private String gender;
    private String ip_address;
    private String address;

    public User(int id, String first_name, String last_name, String email, String gender, String ip_address, String address) {
        this.id = id;
        this.first_name = first_name;
        this.last_name = last_name;
        this.email = email;
        this.gender = gender;
        this.ip_address = ip_address;
        this.address = address;
    }

    public User(){

    }

    @Override
    public User fromBytes(byte[] value) {
        String string = new String(value);
        String[] stringSplit = string.split(",");
        return new User(
Integer.parseInt(stringSplit[0]),
                stringSplit[1],
                stringSplit[2],
                stringSplit[3],
                stringSplit[4],
                stringSplit[5],
                stringSplit[6]
        );
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFirst_name() {
        return first_name;
    }

    public void setFirst_name(String first_name) {
        this.first_name = first_name;
    }

    public String getLast_name() {
        return last_name;
    }

    public void setLast_name(String last_name) {
        this.last_name = last_name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getIp_address() {
        return ip_address;
    }

    public void setIp_address(String ip_address) {
        this.ip_address = ip_address;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", first_name='" + first_name + '\'' +
                ", last_name='" + last_name + '\'' +
                ", email='" + email + '\'' +
                ", gender='" + gender + '\'' +
                ", ip_address='" + ip_address + '\'' +
                ", address='" + address + '\'' +
                '}';
    }

    @Override
    public byte[] serialize(){
        String  value = id + "," + first_name + "," + last_name + "," + email + "," + gender + "," + ip_address + "," + address;
        return value.getBytes();
    }
}
