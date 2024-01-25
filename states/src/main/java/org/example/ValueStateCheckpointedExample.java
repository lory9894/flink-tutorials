package org.example;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.connector.file.src.FileSource;
import org.apache.flink.connector.file.src.reader.TextLineInputFormat;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;

public class ValueStateCheckpointedExample {

     public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // start a checkpoint every 1000 ms
		env.enableCheckpointing(1000);

		// to set minimum progress time to happen between checkpoints.
		env.getCheckpointConfig().setMinPauseBetweenCheckpoints(500);
        // if the checkpoint takes too long we could starve the system of resources. if the checkpoint takes 900ms for example
        // and we set the checkpoint interval to 1000ms, then the system will only have 100ms to process the data.
         // if we set minPauseBetweenCheckpoints to 500ms no checkpoint will be triggered until 500ms have passed since the last checkpoint completed.

		// checkpoints have to complete within 10000 ms, or are discarded
		env.getCheckpointConfig().setCheckpointTimeout(10000);

		// set mode to exactly-once (this is the default)
		env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);  // AT_LEAST_ONCE

		// allow only one checkpoint to be in progress at the same time
		env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);

		// enable externalized checkpoints which are retained after job cancellation
		env.getCheckpointConfig().setExternalizedCheckpointCleanup(CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);  // DELETE_ON_CANCELLATION

		//StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
		env.setRestartStrategy(RestartStrategies.fixedDelayRestart(	3, 100 ));
			                                               // number of restart attempts , delay in each restart

        // Reading from a file
        DataStream<String> dataStream = null;

        final ParameterTool params = ParameterTool.fromArgs(args);

        env.getConfig().setGlobalJobParameters(params);

        if (params.has("input")) {

            FileSource<String> source =
                    FileSource.forRecordStreamFormat(
                            new TextLineInputFormat(), new Path(params.get("input"))).build();
            dataStream = env.fromSource(source, WatermarkStrategy.noWatermarks(), "File Source");
        } else {
            System.out.println("Use --input to specify file input");
            System.exit(1);
        }

        if (dataStream == null) {
            System.out.println("No data stream");
            System.exit(1);
        }

        SingleOutputStreamOperator<Tuple2<String, Integer>> filteredStream = dataStream.
                map(new MapFunction<String, Tuple2<String, Integer>>() { // Map to Tuple2, where f0 is the name and f1 is the count (number of occurrences)
                    @Override
                    public Tuple2<String, Integer> map(String value) throws Exception {
                        return new Tuple2<String, Integer>(value, 1); //number of occurrences is 1, we've seen that name only one time, always
                        // (could be condensed to the new flatMap, but in that case I would not have a key and I would not be able to use the stateValue)
                    }
                }).keyBy(value -> value.f0)
                .flatMap(new StatefullMap()); // flatMap to count the number of occurrences of each name, only collecting when a name is encountered the 5th time ( and multiples of 5)
        if (!params.has("output")) {
            System.out.println("Printing result to stdout. Use --output to specify output path.");
            filteredStream.print();
        } else {
            FileSink<String> sink = FileSink.forRowFormat(new Path(params.get("output")), new SimpleStringEncoder<String>("UTF-8")).build();
            filteredStream.map(value -> value.f0 + value.f1.toString()).sinkTo(sink);
        }
        env.execute("Read and Write");
    }

    static class StatefullMap extends RichFlatMapFunction<Tuple2<String, Integer>, Tuple2<String, Integer>> {

         private transient ValueState<Integer> count;
         private transient ValueState<Integer> sum;


        @Override
        public void flatMap(Tuple2<String, Integer> value, Collector<Tuple2<String, Integer>> out) throws Exception {

            Integer countValue = count.value() == null ? 0 : count.value();
            Integer sumValue = sum.value() == null ? 0 : sum.value();

            countValue++;
            sumValue += value.f1;

            count.update(countValue);
            sum.update(sumValue);

            if (countValue >= 5){ //
                out.collect(new Tuple2<>(value.f0, sumValue));
                count.clear();
            }
        }

        public void open(org.apache.flink.configuration.Configuration conf) {
            count = getRuntimeContext().getState(new ValueStateDescriptor<Integer>("count", Integer.class));
            sum = getRuntimeContext().getState(new ValueStateDescriptor<Integer>("sum", Integer.class));
        }
    }
}
