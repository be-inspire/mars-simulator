#set -x
TAG=${1:-$(date "+T%Y%m%d%H%M")}

NAME=mars-$TAG.zip
TARGET=~/Downloads/$NAME

FOLDERS="."
BUMP_FOLDERS=$FOLDERS
FILES="mc-comm/target/mc-comm-$TAG.jar
mc-sample/target/mc-sample-$TAG.jar
platform-comm/target/platform-comm-$TAG.jar
platform-sim/target/platform-sim-$TAG.jar
tower/target/tower-$TAG.jar"

echo Files: $FILES

echo Genrating $TARGET
echo "Press ENTER to continue..."
read


for d in $FOLDERS; do
  pushd $d >/dev/null
  if [ -n "$(git status --porcelain)" ]; then echo "***** dirty repo: $d"; exit ;fi
  popd >/dev/null
done

for d in $BUMP_FOLDERS; do
  pushd $d >/dev/null
  xmlstarlet ed -P -L -u "/_:project/_:properties/_:revision" -v $TAG pom.xml
  mvn clean package
  git ci -am "Version bump: $TAG"
  popd >/dev/null
done

rm -f $TARGET

for d in $BUMP_FOLDERS; do
  pushd $d >/dev/null
  echo Adding $d
  git archive --format zip --output $TARGET HEAD
  popd >/dev/null
done

for d in $FOLDERS; do
  pushd $d >/dev/null
  echo "Tagging $d -> $TAG"
  git tag -f $TAG || { echo "***** cannot tag $d"; exit; }
done

for f in $FILES; do
  echo Adding file $f
  zip $TARGET -g --junk-paths $f
done

echo Done: $TARGET
