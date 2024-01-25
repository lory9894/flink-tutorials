package org.example;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.connector.file.src.FileSource;
import org.apache.flink.connector.file.src.reader.TextLineInputFormat;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.EventTimeSessionWindows;
import org.apache.flink.streaming.api.windowing.assigners.GlobalWindows;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;


public class WordCount {
    public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

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

        SingleOutputStreamOperator<Tuple2<String, Integer>> filteredStream = dataStream.map(new Tokenizer()).
                keyBy(value -> value.f0).reduce((value1, value2) -> new Tuple2<>(value1.f0, value1.f1 + value2.f1));
        if (!params.has("output")) {
            System.out.println("Printing result to stdout. Use --output to specify output path.");
            filteredStream.print();
        } else {
            FileSink<String> sink = FileSink.forRowFormat(new Path(params.get("output")), new SimpleStringEncoder<String>("UTF-8")).build();
            filteredStream.map(value -> value.f0 + value.f1.toString()).sinkTo(sink);
        }
        env.execute("Read and Write");
    }

    static class Tokenizer implements MapFunction<String, Tuple2<String,Integer>> {
        @Override
        public Tuple2<String, Integer> map(String value) throws Exception {
            return new Tuple2<>(value,1);
        }
    }
}