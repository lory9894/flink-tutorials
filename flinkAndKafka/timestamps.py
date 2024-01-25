import csv
timestamp_start = 1706176974
timestamps = list(range(timestamp_start, timestamp_start + 1000))

# print to csv
with open('timestamps.csv', 'w') as f:
    writer = csv.writer(f)
    for timestamp in timestamps:
        writer.writerow(timestamp)