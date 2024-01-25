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
     * private constructor for singleton
     */
    public KafkaSources() {
        UserSource = KafkaSource.<User>builder()
                .setBootstrapServers("kafka:9092")
                .setTopics("users")
                .setGroupId("consumer-group")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setDeserializer(new GenericKafkaDeserialization<>(User.class))
                .build();

        PurchaseSource= KafkaSource.<Purchase>builder()
        .setBootstrapServers("kafka:9092")
        .setTopics("purchases")
        .setGroupId("my-group")
        .setStartingOffsets(OffsetsInitializer.earliest())
        .setDeserializer(new GenericKafkaDeserialization<>(Purchase.class))
        .build();
    }

    /***
     * Generic Kafka Deserialization class, only used inside KafkaSources
     * @param <T> type of object to deserialize
     */
    static private class GenericKafkaDeserialization<T extends Deserializable<T>> implements KafkaRecordDeserializationSchema<T> {

    private final Class<T> type;

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

    @Override
    public TypeInformation<T> getProducedType() {
        return TypeInformation.of(type);
    }
}
}
