# /bin/bash

INTEGRATION_PATH=$1
CLIENT_PATH=$2
COMMIT_MSG=$3
TAG_NAME=$4

cd $INTEGRATION_PATH
/usr/bin/git checkout master
/usr/bin/git stash save before_release
/usr/bin/git pull origin master
/usr/bin/git stash pop
/usr/bin/git add version.properties
/usr/bin/git commit -m "$COMMIT_MSG"

/usr/bin/git tag -a $TAG_NAME -m $TAG_NAME

/usr/bin/git push origin master
/usr/bin/git push --tags


cd $CLIENT_PATH
/usr/bin/git checkout master
/usr/bin/git tag -a $TAG_NAME -m $TAG_NAME
/usr/bin/git push --tags