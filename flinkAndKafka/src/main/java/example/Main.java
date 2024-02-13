package example;

import events.BuyEventProducer;
import events.Purchase;
import logger.PerformanceLogger;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.CoGroupFunction;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.GlobalWindows;
import org.apache.flink.streaming.api.windowing.triggers.CountTrigger;
import org.apache.flink.streaming.api.windowing.windows.GlobalWindow;
import org.apache.flink.streaming.api.windowing.windows.Window;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import users.User;

public class Main {
    public static void main(String[] args) throws Exception {
        final PerformanceLogger logger = new PerformanceLogger();

        //ENVIRONMENT SETUP
        final StreamExecutionEnvironment env =  StreamExecutionEnvironment.getExecutionEnvironment();
        //env.setMaxParallelism(3);
        final ParameterTool params = ParameterTool.fromArgs(args);
        env.getConfig().setGlobalJobParameters(params);

        //KAFKA SOURCES, creation of singleton
        KafkaSources kafkaSources = KafkaSources.getInstance();

        //data streams, getting sources from singleton
        DataStream<User> userDataStream =  env.fromSource(kafkaSources.getUserSource(), WatermarkStrategy.noWatermarks(), "Kafka User Source");
        DataStream<Purchase> purchaseDataStream =  env.fromSource(kafkaSources.getPurchaseSource(), WatermarkStrategy.forMonotonousTimestamps(), "Kafka Purchases Source")
        .assignTimestampsAndWatermarks(WatermarkStrategy.<Purchase>forMonotonousTimestamps().withTimestampAssigner((event, timestamp) -> event.getEventTimeMillis())); //setting watermarks as monotonous, timestamps are written on object

        //TRANSPORT ORDERS PROCESSING
        //merge user and purchase streams
        DataStream<TransportOrder> orderStream = userDataStream.coGroup(purchaseDataStream)
                .where(User::getId)
                .equalTo(Purchase::getUid) //merge on uid
                .window(GlobalWindows.create()).trigger(CountTrigger.of(1))//new transport order will be created for each combination of user and purchase (with same uid), so trigger on each element
                .apply(new CoGroupFunction<User, Purchase, TransportOrder>() { //merge function, creates a transport order with user id, address and product
                    @Override
                    public void coGroup(Iterable<User> first, Iterable<Purchase> second, Collector<TransportOrder> out) throws Exception {
                        if (first.iterator().hasNext() && second.iterator().hasNext()){//TODO: this is bad. I made this because not all users have purchases, but this is not the way to do it
                            User user = first.iterator().next();
                            Purchase purchase = second.iterator().next();
                            logger.logPerformances(user.getId(), purchase.getProduct());
                            out.collect(new TransportOrder(user.getId(),user.getAddress(),purchase.getProduct()));
                        }

                    }
                });

        //kafka sink for transport orders
        KafkaSink<TransportOrder> transportOrderSink = KafkaSink.<TransportOrder>builder()
        .setBootstrapServers("kafka:9092")
        .setRecordSerializer(KafkaRecordSerializationSchema.builder()
            .setTopic("transportOrders")
            .setValueSerializationSchema(new SerializationSchema<TransportOrder>() { //custom serialization schema, simply .toString() and then .bytes()

                /*
                TODO
                  Citare il fatto che avrei potuto usare direttamente Tuple o stringhe (c'è anche la TableAPI che è perfetta per i CSV)
                    ma ho voluto fare una cosa più generica, più simile a quello che si farebbe con un serialization schema (Avro, json, ecc)
                    Non ho usato direttamente AVRO perché non lo considero rilevante ai fini della demo.
                 */
                @Override
                public byte[] serialize(TransportOrder element) {
                    return element.serialize();
                }
            })
            .build()
        )
        .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
        .build();

        //send transport orders to kafka
        orderStream.sinkTo(transportOrderSink);

        //AVERAGE PURCHASES PROCESSING

        SinkFunction<Tuple2<Integer,Double>> sqlSink = JdbcSink.sink( //tutta sta roba è una "insert on duplicate key update"
                "MERGE averages AS target\n" +
                        "USING (VALUES (?, ?)) AS source (uid, average)\n" +
                        "ON (target.uid = source.uid)\n" +
                        "WHEN MATCHED THEN \n" +
                        "    UPDATE SET average = source.average\n" +
                        "WHEN NOT MATCHED THEN \n" +
                        "    INSERT (uid, average) \n" +
                        "    VALUES (source.uid, source.average);", (ps, t) -> {
                ps.setInt(1, t.f0);
                ps.setDouble(2, t.f1);
                },
                JdbcExecutionOptions.builder() //when one of this condition is met, the batch is executed
                                .withBatchSize(1000)
                                .withBatchIntervalMs(200)
                                .withMaxRetries(5)
                                .build(),
                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder() //connection options for sqlserver
                                .withUrl("jdbc:sqlserver://sqlserver:1433;databaseName=FlinkDB;" +
                                        "integratedSecurity=false;encrypt=false;trustServerCertificate=false") //la security fa casino con docker
                                .withDriverName("com.microsoft.sqlserver.jdbc.SQLServerDriver")
                                .withUsername("sa")
                                .withPassword("Pass@Word")
                                .build()

        );

        purchaseDataStream
                .keyBy(Purchase::getUid) //key by uid, partition the stream to one uid per partition
                .countWindow(10) //triggers when 10 elements are in the window
                .process(new GenericPurchaseAveragedCount<GlobalWindow>()).addSink(sqlSink);

        /*
        purchaseDataStream.
                keyBy(Purchase::getUid)
                .window(TumblingEventTimeWindows.of(Time.seconds(2))).process(new Printer<TimeWindow>()).print("Time Window");
         */
        env.execute("Kafka Example");
    }


    /***
     * Generic process window function that calculates the average of a stream of purchases.
     * returns a stream of tuples (uid, average)
     * @param <T> type of window (GlobalWindow or TimeWindow)
     */
    public static class GenericPurchaseAveragedCount<T extends Window> extends ProcessWindowFunction<Purchase, Tuple2<Integer, Double>, Integer, T> {
        @Override
        public void process(Integer key, Context context, Iterable<Purchase> purchases, Collector<Tuple2<Integer, Double>> out) {
            int count = 0;
            double sum = 0;
            for (Purchase p : purchases) {
                count++;
                sum += p.getPriceDouble();
            }
            out.collect(Tuple2.of(key, sum / count));
        }
    }

    /***
     * Debug, prints the elements of a stream present in a window.
     * @param <T> type of window (GlobalWindow or TimeWindow)
     */
    public static class Printer<T extends Window> extends ProcessWindowFunction<Purchase, Purchase, Integer, T> {

        @Override
        public void process(Integer integer, ProcessWindowFunction<Purchase, Purchase, Integer, T>.Context context, Iterable<Purchase> elements, Collector<Purchase> out) throws Exception {
            for (Purchase p : elements) {
                System.out.println(p);
                out.collect(p);
            }
        }
    }


}



