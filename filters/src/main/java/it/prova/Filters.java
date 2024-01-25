package it.prova;

import org.apache.flink.api.common.functions.FilterFunction;

public class Filters {

    public static FilterFunction<String> charThreeFilter = new charThreeFilter();

    public static class charThreeFilter implements FilterFunction<String> {
        @Override
        public boolean filter(String s) throws Exception {

            try {
                Double.parseDouble(s.trim());
                return false;
            } catch (NumberFormatException e) {

            }
            return s.length() > 3;
        }
    }

}
