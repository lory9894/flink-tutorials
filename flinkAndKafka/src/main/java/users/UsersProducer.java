package users;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.file.src.FileSource;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.core.fs.Path;
import org.apache.flink.formats.csv.CsvReaderFormat;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema;
import org.apache.kafka.clients.producer.ProducerRecord;

import javax.annotation.Nullable;

public class UsersProducer {
    public static void main(String[] args) throws Exception {

        // set up the streaming execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        final ParameterTool params = ParameterTool.fromArgs(args);

        env.getConfig().setGlobalJobParameters(params);

        DataStream<User> dataStream = null;

        //read from a CSV file, if not provided trow an error and exit
        if (params.has("input")) {

            CsvReaderFormat<User> csvFormat = CsvReaderFormat.forPojo(User.class); //csv schema definition with jackson

            FileSource<User> source =
                    FileSource.forRecordStreamFormat(csvFormat, Path.fromLocalFile(new java.io.File(params.get("input"))))
                            .build(); //read from the CSV file

            dataStream = env.fromSource(source, WatermarkStrategy.noWatermarks(), "File Source");
            //use the CSV file as a source, transform it to a DataStream

        } else {
            System.out.println("Use --input to specify file input");
            System.exit(1);
        }

        if (dataStream == null) {
            System.out.println("No data stream");
            System.exit(1);
        }

        dataStream.print(); //debug print

        //generate a Kafka sink
        KafkaSink<User> kafkaSink = KafkaSink.<User>builder()
                .setBootstrapServers("kafka:9092")
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic("users")
                        .setValueSerializationSchema(new UserKafkaSerializationSchema()) //custom serializer needed
                        .build()
                )
                .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                .build();

        dataStream.
                sinkTo(kafkaSink); //send the data to Kafka (topic: users)
        //run the pipeline
        env.execute();
    }

    private static class UserKafkaSerializationSchema implements SerializationSchema<User> {

        @Override
        public byte[] serialize(User element) {
            return element.serialize(); //convert the data to a string, simple serializer
        }
    }
}
