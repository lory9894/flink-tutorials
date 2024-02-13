package logger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
public class PerformanceLogger implements Serializable {

    private static final Logger LOGGER = LoggerFactory.getLogger("performanceLogger");

public void logPerformances(int id, String product ){
    LocalDate currentDate = LocalDate.now();
    int currentYear = currentDate.getYear();
    int currentMonth = currentDate.getMonthValue();
    int currentDay = currentDate.getDayOfMonth();

    String out = "{ \"logTimestamp\": " + "\"" + Instant.ofEpochSecond(System.currentTimeMillis()).toString() + "\", " +
            "\"environment\": " + "\" demo \", " +
            "\"applicationName\": " + "\" demo \", " +
            "\"domainKey\": " + "\" demo domain key\", " +
            "\"transactionId\": " + "\" demo transaction id\", " +
            "\"country\": " + "\" id \", " +
            "\"company\": 2, " +
            "\"branch\": " + "\" demo\", " +
            "\"year\": " + currentYear + ", " +
            "\"month\": " + currentMonth + ", " +
            "\"day\": " + currentDay + ", " +
            "\"uid\": " + id + ", " +
            "\"product\": " + product + ", " +
            "}";


    LOGGER.info(out);
    }
}
