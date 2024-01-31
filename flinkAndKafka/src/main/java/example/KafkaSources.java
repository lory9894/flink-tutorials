package example;

import events.Purchase;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import users.User;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

public class KafkaSources {

    //singleton instance
    private static KafkaSources instance = null;

    //kafka source for users topic
    private final KafkaSource<User> UserSource;

    //kafka source for purchases topic
    private final KafkaSource<Purchase> PurchaseSource;


    public KafkaSource<User> getUserSource() {
        return UserSource;
    }

    public KafkaSource<Purchase> getPurchaseSource() {
        return PurchaseSource;
    }

    /***
     * @return singleton instance of KafkaSources
     */
    public static KafkaSources getInstance() {
        if (instance == null) {
            instance = new KafkaSources();
        }
        return instance;
    }


    /***
     * private constructor for singleton, defines 2 kafka sources, for users and purchases
     */
    public KafkaSources() {
        UserSource = KafkaSource.<User>builder()
                .setBootstrapServers("kafka:9092")
                .setTopics("users")
                .setGroupId("consumer-group")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setDeserializer(new GenericKafkaDeserialization<User>(User.class))
                .build();

        PurchaseSource= KafkaSource.<Purchase>builder()
        .setBootstrapServers("kafka:9092")
        .setTopics("purchases")
        .setGroupId("my-group")
        .setStartingOffsets(OffsetsInitializer.earliest())
        .setDeserializer(new GenericKafkaDeserialization<Purchase>(Purchase.class))
        .build();
    }

    /***
     * Custom Kafka Deserialization class, only used inside KafkaSources
     * deserializes a byte array into an object of type T, the serialization schema is custom
     * (it's only .toString() and then .bytes())
     * @param <T> type of object to deserialize
     */
    static private class GenericKafkaDeserialization<T extends Deserializable<T>> implements KafkaRecordDeserializationSchema<T> {

    private final Class<T> type;

    /***
     * constructor, sets type of object to deserialize, needed because java doesn't allow constructors of generic classes
     * @param type type of object to deserialize
     */
    public GenericKafkaDeserialization(Class<T> type) {
        this.type = type;
    }

    @Override
    public void deserialize(ConsumerRecord<byte[], byte[]> record, Collector<T> out) throws IOException {
        try {
            T instance = type.getDeclaredConstructor().newInstance();
            out.collect(instance.fromBytes(record.value()));
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IOException("Cannot create instance of " + type.getName(), e);
        } catch (InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
    /***
     * @return type of object to deserialize (equivalent to T.class, but java doesn't allow it)
     */
    @Override
    public TypeInformation<T> getProducedType() {
        return TypeInformation.of(type);
    }
}
}
