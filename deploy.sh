#!/bin/bash

docker compose -f docker-compose.prod.yml build api bot

docker compose -f docker-compose.prod.yml up -d db redis

echo "Restarting Discord Bot..."
docker compose -f docker-compose.prod.yml up -d --force-recreate bot

OLD_API_CONTAINER=$(docker compose -f docker-compose.prod.yml ps -q api | head -n 1)

if [ -n "$OLD_API_CONTAINER" ]; then
  echo "Deploying the new API version (Zero-Downtime)..."

  docker compose -f docker-compose.prod.yml up -d --no-recreate --scale api=2 api

  echo "Waiting 40 seconds for the new API to start..."
  sleep 40

  docker rm -f $OLD_API_CONTAINER

  docker compose -f docker-compose.prod.yml up -d --no-recreate --scale api=1 api
else
  echo "Deploying API from scratch."
  docker compose -f docker-compose.prod.yml up -d api
fi

echo "Deploy complete."