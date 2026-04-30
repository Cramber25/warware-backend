#!/bin/bash

docker compose -f docker-compose.prod.yml build backend

OLD_CONTAINER=$(docker compose -f docker-compose.prod.yml ps -q backend | head -n 1)

if [ -n "$OLD_CONTAINER" ]; then
  echo "Deploying the new version."
  docker compose -f docker-compose.prod.yml up -d --no-recreate --scale backend=2 backend
  sleep 40
  docker rm -f $OLD_CONTAINER
  docker compose -f docker-compose.prod.yml up -d --no-recreate --scale backend=1 backend

else
  echo "Deploying from scratch."
  docker compose -f docker-compose.prod.yml up -d
fi

echo "Deploy complete."