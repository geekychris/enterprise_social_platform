"""
Daily Entity Rollup DAG

Runs daily at 02:00 UTC. For each entity type:
1. Waits for hourly partition signals (sensors)
2. Deduplicates hourly data (latest event per entity PK)
3. Merges with prior daily ALL table via FULL OUTER JOIN
4. Writes new daily ALL partition
5. Writes a completion signal

The daily ALL table is a Type-1 SCD: one row per entity, latest state wins.
Deletes are excluded (entities with event_type='DELETE' are dropped from the ALL).
"""
from datetime import datetime, timedelta
from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.providers.trino.operators.trino import TrinoOperator
from airflow.sensors.python import PythonSensor

# Entity table configs: (hourly_table, daily_table, pk_column, all entity columns)
ENTITIES = {
    'entityuser': {
        'pk': 'id',
        'cols': ['tenant_id', 'id', 'username', 'display_name', 'email', 'avatar_url', 'bio',
                 'visibility', 'is_admin', 'job_title', 'department', 'manager_id',
                 'location', 'is_bot', 'created_at'],
    },
    'entitypost': {
        'pk': 'id',
        'cols': ['tenant_id', 'id', 'author_id', 'content', 'visibility', 'target_type', 'target_id', 'created_at'],
    },
    'entitycomment': {
        'pk': 'id',
        'cols': ['tenant_id', 'id', 'post_id', 'author_id', 'content', 'parent_id', 'depth', 'created_at'],
    },
    'entityreaction': {
        'pk': 'id',
        'cols': ['tenant_id', 'id', 'user_id', 'target_id', 'target_type', 'reaction_type', 'created_at'],
    },
    'entitymessage': {
        'pk': 'id',
        'cols': ['tenant_id', 'id', 'conversation_id', 'sender_id', 'content_length', 'has_attachment', 'created_at'],
    },
    'entitygroup': {
        'pk': 'id',
        'cols': ['tenant_id', 'id', 'name', 'description', 'visibility', 'owner_id', 'created_at'],
    },
    'entitypage': {
        'pk': 'id',
        'cols': ['tenant_id', 'id', 'name', 'description', 'visibility', 'owner_id', 'created_at'],
    },
    'entityfollow': {
        'pk': 'follower_id',  # composite: use follower_id + followed_id
        'pk2': 'followed_id',
        'cols': ['tenant_id', 'follower_id', 'followed_id', 'created_at'],
    },
    'entitymembership': {
        'pk': 'user_id',  # composite: use user_id + group_id
        'pk2': 'group_id',
        'cols': ['tenant_id', 'user_id', 'group_id', 'role', 'status', 'created_at'],
    },
}

default_args = {
    'owner': 'worksphere',
    'depends_on_past': False,
    'retries': 2,
    'retry_delay': timedelta(minutes=5),
}


def check_hourly_signals(entity_table: str, ds: str, **kwargs):
    """Check if hourly data signals exist for the given date."""
    from trino.dbapi import connect
    conn = connect(host='ws-trino', port=8080, user='airflow', catalog='iceberg', schema='worksphere')
    cursor = conn.cursor()
    cursor.execute(f"""
        SELECT COUNT(*)
        FROM signals
        WHERE table_name = '{entity_table}'
          AND signal_date = DATE '{ds}'
          AND signal_type = 'HOURLY_COMPLETE'
    """)
    row = cursor.fetchone()
    count = row[0] if row else 0
    cursor.close()
    conn.close()
    return count >= 1


def build_rollup_sql(entity_table: str, config: dict, ds_template: str) -> str:
    """Build the rollup SQL for a given entity."""
    daily_table = entity_table.replace('entity', '') + '_daily'
    cols = config['cols']
    pk = config['pk']
    pk2 = config.get('pk2')

    # Build join condition
    if pk2:
        join_cond = f"c.{pk} = d.{pk} AND c.{pk2} = d.{pk2}"
        dedup_partition = f"{pk}, {pk2}"
    else:
        join_cond = f"c.{pk} = d.{pk}"
        dedup_partition = pk

    # Build COALESCE column list for the merged output
    coalesce_cols = ', '.join([f"COALESCE(c.{col}, d.{col}) AS {col}" for col in cols])

    sql = f"""
        DELETE FROM iceberg.worksphere.{daily_table}
        WHERE event_date = DATE '{ds_template}'
    """

    sql2 = f"""
        INSERT INTO iceberg.worksphere.{daily_table}
        WITH hourly_deduped AS (
            SELECT *, ROW_NUMBER() OVER (
                PARTITION BY {dedup_partition} ORDER BY event_timestamp DESC
            ) AS rn
            FROM iceberg.worksphere.{entity_table}
            WHERE event_date = DATE '{ds_template}'
        ),
        latest_changes AS (
            SELECT event_type, event_timestamp, event_hour, event_date,
                   {', '.join(cols)}
            FROM hourly_deduped WHERE rn = 1
        ),
        prior_daily AS (
            SELECT event_type, event_timestamp, event_hour, event_date,
                   {', '.join(cols)}
            FROM iceberg.worksphere.{daily_table}
            WHERE event_date = (
                SELECT MAX(event_date) FROM iceberg.worksphere.{daily_table}
                WHERE event_date < DATE '{ds_template}'
            )
        ),
        merged AS (
            SELECT
                COALESCE(c.event_type, d.event_type) AS event_type,
                COALESCE(c.event_timestamp, d.event_timestamp) AS event_timestamp,
                COALESCE(c.event_hour, d.event_hour) AS event_hour,
                DATE '{ds_template}' AS event_date,
                {coalesce_cols}
            FROM latest_changes c
            FULL OUTER JOIN prior_daily d ON {join_cond}
        )
        SELECT * FROM merged
        WHERE event_type != 'DELETE' OR event_type IS NULL
    """

    return [sql, sql2]


def create_entity_tasks(dag, entity_table, config):
    """Create sensor + rollup tasks for one entity type."""
    daily_table = entity_table.replace('entity', '') + '_daily'
    cols = config['cols']
    all_cols = ['event_type', 'event_timestamp', 'event_hour', 'event_date'] + cols

    sensor = PythonSensor(
        task_id=f'wait_{entity_table}',
        python_callable=check_hourly_signals,
        op_kwargs={'entity_table': entity_table},
        timeout=7200,
        poke_interval=300,
        mode='poke',
        dag=dag,
    )

    # Build column DDL from the hourly table schema
    col_defs = ', '.join([f"{c} VARCHAR" for c in all_cols])

    create_table = TrinoOperator(
        task_id=f'ensure_{daily_table}',
        trino_conn_id='trino_default',
        sql=f"""
            CREATE TABLE IF NOT EXISTS iceberg.worksphere.{daily_table}
            AS SELECT {', '.join(all_cols)}
            FROM iceberg.worksphere.{entity_table}
            WHERE 1 = 0
        """,
        dag=dag,
    )

    # Delete existing partition for this date, then insert merged data
    ds = '{{ ds }}'

    # event_date is VARCHAR in Iceberg (Spark stores dates as strings)
    delete_existing = TrinoOperator(
        task_id=f'delete_{daily_table}',
        trino_conn_id='trino_default',
        sql=f"""
            DELETE FROM iceberg.worksphere.{daily_table}
            WHERE event_date = '{ds}'
        """,
        dag=dag,
    )

    pk = config['pk']
    pk2 = config.get('pk2')
    if pk2:
        join_cond = f"c.{pk} = d.{pk} AND c.{pk2} = d.{pk2}"
        dedup_partition = f"{pk}, {pk2}"
    else:
        join_cond = f"c.{pk} = d.{pk}"
        dedup_partition = pk

    coalesce_cols = ', '.join([f"COALESCE(c.{col}, d.{col}) AS {col}" for col in cols])

    rollup = TrinoOperator(
        task_id=f'rollup_{daily_table}',
        trino_conn_id='trino_default',
        sql=f"""
            INSERT INTO iceberg.worksphere.{daily_table}
            WITH hourly_deduped AS (
                SELECT *, ROW_NUMBER() OVER (
                    PARTITION BY {dedup_partition} ORDER BY event_timestamp DESC
                ) AS rn
                FROM iceberg.worksphere.{entity_table}
                WHERE event_date = '{ds}'
            ),
            latest_changes AS (
                SELECT event_type, event_timestamp, event_hour, event_date,
                       {', '.join(cols)}
                FROM hourly_deduped WHERE rn = 1
            ),
            prior_daily AS (
                SELECT event_type, event_timestamp, event_hour, event_date,
                       {', '.join(cols)}
                FROM iceberg.worksphere.{daily_table}
                WHERE event_date = (
                    SELECT MAX(event_date) FROM iceberg.worksphere.{daily_table}
                    WHERE event_date < '{ds}'
                )
            ),
            merged AS (
                SELECT
                    COALESCE(c.event_type, d.event_type) AS event_type,
                    COALESCE(c.event_timestamp, d.event_timestamp) AS event_timestamp,
                    COALESCE(c.event_hour, d.event_hour) AS event_hour,
                    '{ds}' AS event_date,
                    {coalesce_cols}
                FROM latest_changes c
                FULL OUTER JOIN prior_daily d ON {join_cond}
            )
            SELECT * FROM merged
            WHERE COALESCE(event_type, '') != 'DELETE'
        """,
        dag=dag,
    )

    signal = TrinoOperator(
        task_id=f'signal_{daily_table}',
        trino_conn_id='trino_default',
        sql=f"""
            INSERT INTO iceberg.worksphere.signals
            VALUES ('{daily_table}', DATE '{ds}', 'DAILY_COMPLETE', CURRENT_TIMESTAMP)
        """,
        dag=dag,
    )

    sensor >> create_table >> delete_existing >> rollup >> signal
    return sensor, signal


with DAG(
    'daily_entity_rollup',
    default_args=default_args,
    description='Daily rollup of hourly entity tables into ALL tables',
    schedule_interval='0 2 * * *',
    start_date=datetime(2026, 3, 29),
    catchup=False,
    tags=['warehouse', 'entity', 'daily'],
) as dag:

    for entity_name, entity_config in ENTITIES.items():
        create_entity_tasks(dag, entity_name, entity_config)
