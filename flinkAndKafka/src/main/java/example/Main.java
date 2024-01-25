package example;

import events.Purchase;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.GlobalWindow;
import org.apache.flink.streaming.api.windowing.windows.Window;
import org.apache.flink.util.Collector;
import users.User;

import java.io.IOException;

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


        purchaseDataStream
                .keyBy(Purchase::getUid)
                .countWindow(10).process(new GenericPurchaseAveragedCount<GlobalWindow>()).print("Count Window");

        /*
        purchaseDataStream.
                keyBy(Purchase::getUid)
                .window(TumblingEventTimeWindows.of(Time.minutes(2))).process(new PurchaseAveragedWindow<TimeWindow>()).print("Time Window");
                TODO: fix this, input data wrong
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

}



