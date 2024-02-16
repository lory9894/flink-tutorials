FROM flink:1.17.2-java11

COPY ../src/main/resources/log4j-console.properties /opt/flink/conf/log4j-console.properties
COPY ../src/main/resources/logback-console.xml /opt/flink/conf/logback-console.xml
RUN rm -f /opt/flink/lib/log4j-slf4j-impl-2.17.1.jar