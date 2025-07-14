#!/bin/bash

echo "COMPOSE_PROJECT_NAME=group-chat-using-db" > .env
echo "HOST_IP=$(route get default | grep 'interface' | awk '{print $2}' | xargs ipconfig getifaddr)" >> .env
