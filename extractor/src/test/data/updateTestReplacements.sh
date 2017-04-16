#! /bin/bash
# update a locally generated xml file with the replacements used to make the tests machine-independent
# use with find:
# find -name 'structure-0.13.*-actual.xml' -execdir ../../updateTestReplacement.sh "{}" \;

FILE=$1
BASE=`pwd`
SBT_VERSION=`echo "$FILE" | egrep -o "\d+\.\d+\.\d+"`
URI_BASE=`scala -e "println(new java.io.File(\"$BASE\").toURI.toString)"`
URI_ANDROID_HOME=`scala -e "println(new java.io.File(\"$ANDROID_HOME\").toURI.toString + \"/\")"`
SBT_GLOBAL_ROOT="$HOME/.sbt-structure-global"
SBT_GLOBAL_BASE="$SBT_GLOBAL_ROOT/$SBT_VERSION"
SBT_BOOT_DIR="$SBT_GLOBAL_ROOT/boot/"
SBT_IVY_HOME="$SBT_GLOBAL_ROOT/ivy2/"

TARGET_FILE="structure-$SBT_VERSION.xml"

echo "replacing $FILE, BASE: $BASE"

sed -e \
"s|$URI_BASE|\$URI_BASE| ; \
s|$BASE|\$BASE| ; \
s|$URI_ANDROID_HOME|\$URI_ANDROID_HOME| ; \
s|$ANDROID_HOME|\$ANDROID_HOME| ; \
s|$SBT_IVY_HOME|\$IVY2/| ; \
s|$SBT_BOOT_DIR|\$SBT_BOOT/| ; \
s|$HOME|\$HOME|" "$FILE" > ${TARGET_FILE}
