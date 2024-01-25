package basic;

//this is a basic example of reading from kafka and writing back to kafka

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

public class ReadAndRewrite {
    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env =  StreamExecutionEnvironment.getExecutionEnvironment();

	KafkaSource<String> source = KafkaSource.<String>builder()
    .setBootstrapServers("kafka:9092")
    .setTopics("test")
    .setGroupId("my-group")
    .setStartingOffsets(OffsetsInitializer.earliest())
    .setValueOnlyDeserializer(new SimpleStringSchema())
    .build();

    KafkaSink<String> sink = KafkaSink.<String>builder()
        .setBootstrapServers("kafka:9092")
        .setRecordSerializer(KafkaRecordSerializationSchema.builder()
            .setTopic("test-out")
            .setValueSerializationSchema(new SimpleStringSchema())
            .build()
        )
        .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
        .build();

	DataStream<String> kafkaDataStream =  env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source").map(x -> "processed:" + x); //gets the data from kafka (topic: test) adds "proof of processing" and sends it back to kafka (topic: test-out)
    kafkaDataStream.sinkTo(sink);
    kafkaDataStream.print();


	env.execute("Kafka Example");
    }
}