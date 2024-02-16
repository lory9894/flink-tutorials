#!/bin/bash
/opt/flink/bin/start-cluster.sh
/opt/flink/bin/taskmanager.sh start
flink run -c events.BuyEventProducer program.jar --input csvs/orders.csv &
flink run -c users.UsersProducer program.jar --input csvs/users.csv
