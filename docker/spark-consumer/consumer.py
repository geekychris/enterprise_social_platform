#!/usr/bin/env python3
"""
WorkSphere Kafka → Iceberg Consumer
Reads structured log events from Kafka and writes to Iceberg tables via Spark.
"""
import json
import os
import sys
import time
from pyspark.sql import SparkSession
from pyspark.sql.functions import col, from_json, to_timestamp
from pyspark.sql.types import (
    StructType, StructField, StringType, IntegerType, LongType,
    DoubleType, BooleanType, TimestampType, DateType, MapType
)

KAFKA_SERVERS = os.environ.get("KAFKA_BOOTSTRAP_SERVERS", "host.docker.internal:9092")
S3_ENDPOINT = os.environ.get("S3_ENDPOINT", "http://ws-minio:9000")
S3_ACCESS = os.environ.get("S3_ACCESS_KEY", "admin")
S3_SECRET = os.environ.get("S3_SECRET_KEY", "password123")
METASTORE_URI = os.environ.get("HIVE_METASTORE_URI", "thrift://ws-hive-metastore:9083")
CONFIG_DIR = os.environ.get("CONFIG_DIR", "/opt/spark-apps/log-configs")
CHECKPOINT_DIR = os.environ.get("CHECKPOINT_DIR", "/opt/spark-data/checkpoints")

TYPE_MAP = {
    "string": StringType(),
    "int": IntegerType(),
    "integer": IntegerType(),
    "long": LongType(),
    "float": DoubleType(),
    "double": DoubleType(),
    "boolean": BooleanType(),
    "timestamp": StringType(),  # Parse as string, convert later
    "date": StringType(),
    "map<string,string>": MapType(StringType(), StringType()),
}


def build_schema(config):
    fields = []
    for f in config["fields"]:
        spark_type = TYPE_MAP.get(f["type"], StringType())
        fields.append(StructField(f["name"], spark_type, not f.get("required", False)))
    return StructType(fields)


def create_table(spark, table_name, config):
    schema = build_schema(config)
    fields_ddl = ", ".join([f"`{f.name}` {f.dataType.simpleString()}" for f in schema.fields])
    partition_by = config.get("warehouse", {}).get("partition_by", [])
    partition_clause = f"PARTITIONED BY ({', '.join(partition_by)})" if partition_by else ""

    sql = f"""
    CREATE TABLE IF NOT EXISTS iceberg.worksphere.{table_name} (
        {fields_ddl}
    )
    USING iceberg
    {partition_clause}
    TBLPROPERTIES ('format-version' = '2', 'write.parquet.compression-codec' = 'snappy')
    """
    try:
        spark.sql(sql)
        print(f"✓ Table iceberg.worksphere.{table_name} ready")
    except Exception as e:
        print(f"✗ Table creation error: {e}")


def start_stream(spark, config):
    topic = config["kafka"]["topic"]
    table_name = config["name"].lower()
    schema = build_schema(config)

    print(f"\nStarting stream: {topic} → iceberg.worksphere.{table_name}")

    # Envelope schema wrapping the data
    envelope_schema = StructType([
        StructField("_log_type", StringType(), True),
        StructField("_log_class", StringType(), True),
        StructField("_version", StringType(), True),
        StructField("data", schema, True),
    ])

    df = (spark.readStream
          .format("kafka")
          .option("kafka.bootstrap.servers", KAFKA_SERVERS)
          .option("subscribe", topic)
          .option("startingOffsets", "earliest")
          .option("failOnDataLoss", "false")
          .load())

    parsed = (df.select(
        from_json(col("value").cast("string"), envelope_schema).alias("env"))
        .select("env.data.*"))

    table_ref = f"iceberg.worksphere.{table_name}"
    query = (parsed.writeStream
             .outputMode("append")
             .option("checkpointLocation", f"{CHECKPOINT_DIR}/{table_name}")
             .option("fanout-enabled", "true")
             .trigger(processingTime="30 seconds")
             .toTable(table_ref))

    print(f"  ✓ Streaming started for {topic}")
    return query


def main():
    print("=" * 60)
    print("WorkSphere Kafka → Iceberg Consumer")
    print("=" * 60)
    print(f"Kafka: {KAFKA_SERVERS}")
    print(f"S3: {S3_ENDPOINT}")
    print(f"Metastore: {METASTORE_URI}")
    print(f"Configs: {CONFIG_DIR}")

    spark = (SparkSession.builder
             .appName("WorkSphereConsumer")
             .config("spark.sql.catalog.iceberg", "org.apache.iceberg.spark.SparkCatalog")
             .config("spark.sql.catalog.iceberg.type", "hive")
             .config("spark.sql.catalog.iceberg.uri", METASTORE_URI)
             .config("spark.sql.catalog.iceberg.warehouse", "s3a://warehouse/")
             .config("spark.sql.catalog.iceberg.io-impl", "org.apache.iceberg.aws.s3.S3FileIO")
             .config("spark.sql.catalog.iceberg.s3.endpoint", S3_ENDPOINT)
             .config("spark.sql.catalog.iceberg.s3.access-key-id", S3_ACCESS)
             .config("spark.sql.catalog.iceberg.s3.secret-access-key", S3_SECRET)
             .config("spark.sql.catalog.iceberg.s3.path-style-access", "true")
             .config("spark.sql.catalog.iceberg.s3.region", "us-east-1")
             .config("spark.hadoop.fs.s3a.endpoint", S3_ENDPOINT)
             .config("spark.hadoop.fs.s3a.access.key", S3_ACCESS)
             .config("spark.hadoop.fs.s3a.secret.key", S3_SECRET)
             .config("spark.hadoop.fs.s3a.path.style.access", "true")
             .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
             .config("spark.sql.warehouse.dir", "s3a://warehouse/")
             .getOrCreate())

    # Create namespace with S3 location
    try:
        spark.sql("DROP NAMESPACE IF EXISTS iceberg.worksphere CASCADE")
    except Exception:
        pass
    try:
        spark.sql("CREATE NAMESPACE IF NOT EXISTS iceberg.worksphere LOCATION 's3a://warehouse/worksphere'")
        print("✓ Namespace iceberg.worksphere ready (s3a://warehouse/worksphere)")
    except Exception as e:
        print(f"Namespace note: {e}")

    # Load configs and start streams
    queries = []
    if not os.path.isdir(CONFIG_DIR):
        print(f"Config dir {CONFIG_DIR} not found!")
        sys.exit(1)

    for fname in sorted(os.listdir(CONFIG_DIR)):
        if not fname.endswith(".json"):
            continue
        path = os.path.join(CONFIG_DIR, fname)
        print(f"\nLoading: {fname}")
        with open(path) as f:
            config = json.load(f)
        try:
            create_table(spark, config["name"].lower(), config)
            q = start_stream(spark, config)
            queries.append(q)
        except Exception as e:
            print(f"  ✗ Error: {e}")
            import traceback; traceback.print_exc()

    if not queries:
        print("\nNo streams started!")
        sys.exit(1)

    print(f"\n{'=' * 60}")
    print(f"✓ {len(queries)} streams running. Waiting for data...")
    print(f"{'=' * 60}")

    for q in queries:
        q.awaitTermination()


if __name__ == "__main__":
    main()
