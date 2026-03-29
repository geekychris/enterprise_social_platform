FROM apache/spark:3.5.1-java17

USER root

# Install Python dependencies
RUN pip3 install pyspark==3.5.1

# Download required JARs for Iceberg + Kafka + S3 + Hive
ENV SPARK_JARS_DIR=/opt/spark/jars
RUN curl -sL -o $SPARK_JARS_DIR/iceberg-spark-runtime-3.5_2.12-1.5.2.jar \
      https://repo1.maven.org/maven2/org/apache/iceberg/iceberg-spark-runtime-3.5_2.12/1.5.2/iceberg-spark-runtime-3.5_2.12-1.5.2.jar && \
    curl -sL -o $SPARK_JARS_DIR/iceberg-aws-bundle-1.5.2.jar \
      https://repo1.maven.org/maven2/org/apache/iceberg/iceberg-aws-bundle/1.5.2/iceberg-aws-bundle-1.5.2.jar && \
    curl -sL -o $SPARK_JARS_DIR/spark-sql-kafka-0-10_2.12-3.5.1.jar \
      https://repo1.maven.org/maven2/org/apache/spark/spark-sql-kafka-0-10_2.12/3.5.1/spark-sql-kafka-0-10_2.12-3.5.1.jar && \
    curl -sL -o $SPARK_JARS_DIR/kafka-clients-3.6.1.jar \
      https://repo1.maven.org/maven2/org/apache/kafka/kafka-clients/3.6.1/kafka-clients-3.6.1.jar && \
    curl -sL -o $SPARK_JARS_DIR/spark-token-provider-kafka-0-10_2.12-3.5.1.jar \
      https://repo1.maven.org/maven2/org/apache/spark/spark-token-provider-kafka-0-10_2.12/3.5.1/spark-token-provider-kafka-0-10_2.12-3.5.1.jar && \
    curl -sL -o $SPARK_JARS_DIR/commons-pool2-2.12.0.jar \
      https://repo1.maven.org/maven2/org/apache/commons/commons-pool2/2.12.0/commons-pool2-2.12.0.jar && \
    curl -sL -o $SPARK_JARS_DIR/hadoop-aws-3.3.4.jar \
      https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-aws/3.3.4/hadoop-aws-3.3.4.jar && \
    curl -sL -o $SPARK_JARS_DIR/aws-java-sdk-bundle-1.12.262.jar \
      https://repo1.maven.org/maven2/com/amazonaws/aws-java-sdk-bundle/1.12.262/aws-java-sdk-bundle-1.12.262.jar

# Copy application
COPY consumer.py /opt/spark-apps/consumer.py

# Create directories
RUN mkdir -p /opt/spark-data/checkpoints

WORKDIR /opt/spark-apps

ENTRYPOINT ["/opt/spark/bin/spark-submit", \
    "--master", "local[2]", \
    "--driver-memory", "1g", \
    "--conf", "spark.sql.extensions=org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions", \
    "/opt/spark-apps/consumer.py"]
