#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

# Define variables for easy updates
IMAGE_NAME="busapp"
DOCKER_USER="saip2005"
REPO_NAME="busapp"
TAG="1.9.5"
FULL_IMAGE_NAME="$DOCKER_USER/$REPO_NAME:$TAG"

echo " Starting build process for $IMAGE_NAME..."

# 1. Build the image
# The . refers to the current directory where the Dockerfile is located
docker build -t $IMAGE_NAME .

echo " Build successful. Tagging image as $FULL_IMAGE_NAME..."

# 2. Tag the image
docker tag $IMAGE_NAME $FULL_IMAGE_NAME

echo " Pushing to Docker Hub..."

# 3. Push to registry
docker push $FULL_IMAGE_NAME

echo " Done! Image $FULL_IMAGE_NAME is now live."