#!/bin/bash
set -ex
VERSION="${1}"
DIR=$(pwd)
TMPDIR="/tmp/mvn-repo/$(uuidgen)"
mkdir $TMPDIR
cd $TMPDIR

git clone git@github.com:amccurry/rdp-proxy.git
cd $TMPDIR/rdp-proxy
git checkout repository
git pull
mvn install:install-file \
 -DgroupId=rdp-proxy \
 -DartifactId=rdp-proxy-core \
 -Dversion=${VERSION} \
 -Dfile=${DIR}/rdp-proxy-core/target/rdp-proxy-core-${VERSION}.jar \
 -Dpackaging=jar \
 -DgeneratePom=true \
 -DlocalRepositoryPath=. \
 -DcreateChecksum=true

mvn install:install-file \
 -DgroupId=rdp-proxy \
 -DartifactId=rdp-proxy-file \
 -Dversion=${VERSION} \
 -Dfile=${DIR}/rdp-proxy-file/target/rdp-proxy-file-${VERSION}.jar \
 -Dpackaging=jar \
 -DgeneratePom=true \
 -DlocalRepositoryPath=. \
 -DcreateChecksum=true

mvn install:install-file \
 -DgroupId=rdp-proxy \
 -DartifactId=rdp-proxy-service-spi \
 -Dversion=${VERSION} \
 -Dfile=${DIR}/rdp-proxy-service-spi/target/rdp-proxy-service-spi-${VERSION}.jar \
 -Dpackaging=jar \
 -DgeneratePom=true \
 -DlocalRepositoryPath=. \
 -DcreateChecksum=true

mvn install:install-file \
 -DgroupId=rdp-proxy \
 -DartifactId=rdp-proxy-store-spi \
 -Dversion=${VERSION} \
 -Dfile=${DIR}/rdp-proxy-store-spi/target/rdp-proxy-store-spi-${VERSION}.jar \
 -Dpackaging=jar \
 -DgeneratePom=true \
 -DlocalRepositoryPath=. \
 -DcreateChecksum=true

git add rdp-proxy
git commit -a -m "Repo release."
git push

rm -rf $TMPDIR
