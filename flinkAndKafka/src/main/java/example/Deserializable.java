package example;

public interface Deserializable<T> {

    public T fromBytes(byte[] bytes);

    public byte[] serialize();
}
