package example;

import events.Purchase;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.CoGroupFunction;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.GlobalWindows;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.triggers.CountTrigger;
import org.apache.flink.streaming.api.windowing.windows.GlobalWindow;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.streaming.api.windowing.windows.Window;
import org.apache.flink.util.Collector;
import users.User;

import java.util.NoSuchElementException;

public class Main {
    public static void main(String[] args) throws Exception {
        //ENVIRONMENT SETUP
        final StreamExecutionEnvironment env =  StreamExecutionEnvironment.getExecutionEnvironment();
        final ParameterTool params = ParameterTool.fromArgs(args);
        env.getConfig().setGlobalJobParameters(params);

        KafkaSources kafkaSources = KafkaSources.getInstance();

        DataStream<User> userDataStream =  env.fromSource(kafkaSources.getUserSource(), WatermarkStrategy.noWatermarks(), "Kafka User Source");
        DataStream<Purchase> purchaseDataStream =  env.fromSource(kafkaSources.getPurchaseSource(), WatermarkStrategy.forMonotonousTimestamps(), "Kafka Purchases Source")
        .assignTimestampsAndWatermarks(WatermarkStrategy.<Purchase>forMonotonousTimestamps().withTimestampAssigner((event, timestamp) -> event.getEventTimeMillis()));

        DataStream<TransportOrder> orderStream = userDataStream.coGroup(purchaseDataStream)
                .where(User::getId)
                .equalTo(Purchase::getUid)
                .window(GlobalWindows.create()).trigger(CountTrigger.of(1))
                .apply(new CoGroupFunction<User, Purchase, TransportOrder>() {
                    @Override
                    public void coGroup(Iterable<User> first, Iterable<Purchase> second, Collector<TransportOrder> out) throws Exception {
                        if (first.iterator().hasNext() && second.iterator().hasNext()){//TODO: this is bad. I made this because not all users have purchases, but this is not the way to do it
                            User user = first.iterator().next();
                            Purchase purchase = second.iterator().next();
                            out.collect(new TransportOrder(user.getId(),user.getAddress(),purchase.getProduct()));
                        }

                    }
                });

        KafkaSink<TransportOrder> transportOrderSink = KafkaSink.<TransportOrder>builder()
        .setBootstrapServers("kafka:9092")
        .setRecordSerializer(KafkaRecordSerializationSchema.builder()
            .setTopic("transportOrders")
            .setValueSerializationSchema(new SerializationSchema<TransportOrder>() {
                @Override
                public byte[] serialize(TransportOrder element) {
                    return element.serialize();
                }
            })
            .build()
        )
        .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
        .build();

        //orderStream.print("Order Stream");

        orderStream.sinkTo(transportOrderSink);

        purchaseDataStream
                .keyBy(Purchase::getUid)
                .countWindow(10).process(new GenericPurchaseAveragedCount<GlobalWindow>()).print("Count Window");

        /*
        purchaseDataStream.
                keyBy(Purchase::getUid)
                .window(TumblingEventTimeWindows.of(Time.seconds(2))).process(new Printer<TimeWindow>()).print("Time Window");
         */
        env.execute("Kafka Example");
    }


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



