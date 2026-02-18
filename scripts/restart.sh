#!/bin/bash

# ==============================
# Restart Docker Environment
# ==============================

echo -e "\n\033[1;34m[1/3] Stopping containers...\033[0m"
docker compose down -v >/dev/null 2>&1

echo -e "\n\033[1;34m[1/3] Stopping containers...\033[0m"
docker compose up -d >/dev/null 2>&1

#echo -e "\n\033[1;34m[3/3] Removing images...\033[0m"
#docker rmi 

echo -e "\n\033[1;32m Restart completed. Docker Compose stack is now running.\033[0m\n"
