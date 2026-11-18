FROM python:3.12-alpine

WORKDIR /app
COPY server.py ./server.py

ENV CINEMA_SINK_HOST=0.0.0.0 \
    CINEMA_SINK_PORT=13000 \
    CINEMA_SINK_DIR=/data

VOLUME ["/data"]
EXPOSE 13000

CMD ["python3", "server.py"]
