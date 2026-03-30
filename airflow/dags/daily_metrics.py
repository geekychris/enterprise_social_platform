"""
Daily Metrics DAG

Runs daily at 04:00 UTC (after entity rollup).
Computes DAU, WAU, MAU and other aggregate metrics.
"""
from datetime import datetime, timedelta
from airflow import DAG
from airflow.providers.trino.operators.trino import TrinoOperator
from airflow.sensors.python import PythonSensor

default_args = {
    'owner': 'worksphere',
    'depends_on_past': False,
    'retries': 2,
    'retry_delay': timedelta(minutes=5),
}


def check_daily_signals(ds, **kwargs):
    """Wait for entity rollup to complete."""
    from trino.dbapi import connect
    conn = connect(host='ws-trino', port=8080, user='airflow', catalog='iceberg', schema='worksphere')
    cursor = conn.cursor()
    # Check that at least users and interactions are done
    cursor.execute(f"""
        SELECT COUNT(DISTINCT table_name)
        FROM signals
        WHERE signal_date = DATE '{ds}'
          AND signal_type = 'DAILY_COMPLETE'
          AND table_name IN ('user_daily', 'post_daily', 'reaction_daily', 'message_daily')
    """)
    row = cursor.fetchone()
    count = row[0] if row else 0
    cursor.close()
    conn.close()
    return count >= 4


with DAG(
    'daily_metrics',
    default_args=default_args,
    description='Compute DAU, WAU, MAU and engagement metrics',
    schedule_interval='0 4 * * *',
    start_date=datetime(2026, 3, 29),
    catchup=False,
    tags=['warehouse', 'metrics', 'daily'],
) as dag:

    wait_for_rollup = PythonSensor(
        task_id='wait_for_entity_rollup',
        python_callable=check_daily_signals,
        timeout=7200,
        poke_interval=300,
        mode='poke',
    )

    ensure_metrics_table = TrinoOperator(
        task_id='ensure_metrics_table',
        trino_conn_id='trino_default',
        sql="""
            CREATE TABLE IF NOT EXISTS iceberg.worksphere.daily_metrics (
                metric_date DATE,
                metric_name VARCHAR,
                metric_value BIGINT,
                computed_at TIMESTAMP
            ) WITH (partitioning = ARRAY['metric_date'])
        """,
    )

    compute_dau = TrinoOperator(
        task_id='compute_dau',
        trino_conn_id='trino_default',
        sql="""
            INSERT INTO iceberg.worksphere.daily_metrics
            SELECT
                DATE '{{ ds }}' AS metric_date,
                'DAU' AS metric_name,
                COUNT(DISTINCT user_id) AS metric_value,
                CURRENT_TIMESTAMP AS computed_at
            FROM iceberg.worksphere.userinteraction
            WHERE event_date = '{{ ds }}'
        """,
    )

    compute_posts = TrinoOperator(
        task_id='compute_daily_posts',
        trino_conn_id='trino_default',
        sql="""
            INSERT INTO iceberg.worksphere.daily_metrics
            SELECT
                DATE '{{ ds }}',
                'DAILY_POSTS',
                COUNT(*),
                CURRENT_TIMESTAMP
            FROM iceberg.worksphere.entitypost
            WHERE event_date = DATE '{{ ds }}'
              AND event_type = 'CREATE'
        """,
    )

    compute_messages = TrinoOperator(
        task_id='compute_daily_messages',
        trino_conn_id='trino_default',
        sql="""
            INSERT INTO iceberg.worksphere.daily_metrics
            SELECT
                DATE '{{ ds }}',
                'DAILY_MESSAGES',
                COUNT(*),
                CURRENT_TIMESTAMP
            FROM iceberg.worksphere.entitymessage
            WHERE event_date = DATE '{{ ds }}'
              AND event_type = 'CREATE'
        """,
    )

    compute_reactions = TrinoOperator(
        task_id='compute_daily_reactions',
        trino_conn_id='trino_default',
        sql="""
            INSERT INTO iceberg.worksphere.daily_metrics
            SELECT
                DATE '{{ ds }}',
                'DAILY_REACTIONS',
                COUNT(*),
                CURRENT_TIMESTAMP
            FROM iceberg.worksphere.entityreaction
            WHERE event_date = DATE '{{ ds }}'
              AND event_type = 'CREATE'
        """,
    )

    compute_wau = TrinoOperator(
        task_id='compute_wau',
        trino_conn_id='trino_default',
        sql="""
            INSERT INTO iceberg.worksphere.daily_metrics
            SELECT
                DATE '{{ ds }}',
                'WAU',
                COUNT(DISTINCT user_id),
                CURRENT_TIMESTAMP
            FROM iceberg.worksphere.userinteraction
            WHERE event_date BETWEEN DATE '{{ ds }}' - INTERVAL '7' DAY AND DATE '{{ ds }}'
        """,
    )

    compute_mau = TrinoOperator(
        task_id='compute_mau',
        trino_conn_id='trino_default',
        sql="""
            INSERT INTO iceberg.worksphere.daily_metrics
            SELECT
                DATE '{{ ds }}',
                'MAU',
                COUNT(DISTINCT user_id),
                CURRENT_TIMESTAMP
            FROM iceberg.worksphere.userinteraction
            WHERE event_date BETWEEN DATE '{{ ds }}' - INTERVAL '30' DAY AND DATE '{{ ds }}'
        """,
    )

    signal = TrinoOperator(
        task_id='signal_metrics_complete',
        trino_conn_id='trino_default',
        sql="""
            INSERT INTO iceberg.worksphere.signals
            VALUES ('daily_metrics', '{{ ds }}', 'DAILY_COMPLETE', CURRENT_TIMESTAMP)
        """,
    )

    wait_for_rollup >> ensure_metrics_table
    ensure_metrics_table >> [compute_dau, compute_posts, compute_messages, compute_reactions, compute_wau, compute_mau]
    [compute_dau, compute_posts, compute_messages, compute_reactions, compute_wau, compute_mau] >> signal
