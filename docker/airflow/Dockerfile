FROM apache/airflow:2.9.0-python3.11

USER airflow

# Install providers compatible with Airflow 2.9.0
RUN pip install --no-cache-dir \
    "apache-airflow-providers-trino>=5.0,<6.0" \
    "apache-airflow-providers-amazon>=8.0,<9.0" \
    "trino>=0.320,<1.0" \
    "apache-airflow==2.9.0"
