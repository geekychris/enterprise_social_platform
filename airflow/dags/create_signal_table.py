"""
Create Signal Table DAG (run once)

Creates the signals table used for dependency tracking between pipeline stages.
"""
from datetime import datetime
from airflow import DAG
from airflow.providers.trino.operators.trino import TrinoOperator

with DAG(
    'create_signal_table',
    description='One-time: create the signals table',
    schedule_interval='@once',
    start_date=datetime(2026, 3, 29),
    catchup=False,
    tags=['warehouse', 'setup'],
) as dag:

    TrinoOperator(
        task_id='create_signals',
        trino_conn_id='trino_default',
        sql="""
            CREATE TABLE IF NOT EXISTS iceberg.worksphere.signals (
                table_name VARCHAR,
                signal_date DATE,
                signal_type VARCHAR,
                completed_at TIMESTAMP
            ) WITH (partitioning = ARRAY['signal_date'])
        """,
    )
