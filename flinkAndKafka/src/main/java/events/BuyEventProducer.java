package events;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.kafka.common.utils.Utils.sleep;

public class BuyEventProducer {
    public static void main(String[] args) throws Exception {

        final Logger logger = LoggerFactory.getLogger(BuyEventProducer.class);

         // set up the streaming execution environment
         StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
         final ParameterTool params = ParameterTool.fromArgs(args);

         env.getConfig().setGlobalJobParameters(params);

         DataStream<Purchase> dataStream = null;

         //read from a CSV file, if not provided trow an error and exit
         if (params.has("input")) {

             CsvReaderFormat<Purchase> csvFormat = CsvReaderFormat.forPojo(Purchase.class);
             FileSource<Purchase> source =
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

         //dataStream.print(); //debug print

        //generate a Kafka sink
        KafkaSink<Purchase> kafkaSink = KafkaSink.<Purchase>builder()
        .setBootstrapServers("kafka:9092")
        .setRecordSerializer(KafkaRecordSerializationSchema.builder()
            .setTopic("purchases")
            .setValueSerializationSchema(new PurchaseKafkaSerializationSchema())
            .build()
        )
        .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
        .build();

        if (params.has("delay")) {
            dataStream.map(new MapFunction<Purchase, Purchase>() {
                @Override
                public Purchase map(Purchase value) throws Exception {
                    sleep((long) (Math.random()*200)); //simulate a random delay
                    logger.info("Sending purchase, uid = " + value.getUid());
                    return value;
                }
            }).sinkTo(kafkaSink); //send the data to Kafka (topic: Orders)
        } else {
            dataStream.sinkTo(kafkaSink); //send the data to Kafka (topic: Orders)
        }

        //run the pipeline
         env.execute();
     }

     private static class PurchaseKafkaSerializationSchema implements SerializationSchema<Purchase> {

         @Override
         public byte[] serialize(Purchase element) {
             return element.serialize(); //convert the data to a string, simple serializer
         }
     }

}
