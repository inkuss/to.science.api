#!/bin/bash

if (( $EUID == 0 )); then
    echo "Don't run as root!"
    exit
fi

export TERM=xterm-color
deployingApp="to.science.api"
branch=$(git status | grep branch | cut -d ' ' -f3)
echo "git currently on branch: "$branch
remote=origin
if [ ! -z "$1" ]; then
    branch="$1"
fi
if [ ! -z "$2" ]; then
    branch="$2"
    remote=$1
fi


cd /opt/toscience/git/$deployingApp
git pull $remote $branch
/opt/toscience/activator/activator -java-home /opt/jdk clean
/opt/toscience/activator/activator -java-home /opt/jdk clean-files
/opt/toscience/activator/activator -java-home /opt/jdk dist
