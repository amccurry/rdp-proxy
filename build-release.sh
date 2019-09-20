#!/bin/bash
set -ex
#VERSION="${1}"

mvn clean install -DskipTests

VERSION=$(mvn org.apache.maven.plugins:maven-help-plugin:3.1.0:evaluate -Dexpression=project.version -q -DforceStdout)

# mvn help:effective-pom -Doutput=./target/effective.xml
#
# pushd ./rdp-proxy-core
# mvn help:effective-pom -Doutput=./target/effective.xml
# popd
#
# pushd ./rdp-proxy-file
# mvn help:effective-pom -Doutput=./target/effective.xml
# popd
#
# pushd ./rdp-proxy-service-spi
# mvn help:effective-pom -Doutput=./target/effective.xml
# popd
#
# pushd ./rdp-proxy-store-spi
# mvn help:effective-pom -Doutput=./target/effective.xml
# popd

DIR=$(pwd)
TMPDIR="/tmp/mvn-repo/$(uuidgen)"
mkdir -p $TMPDIR
cd $TMPDIR

git clone git@github.com:amccurry/rdp-proxy.git
cd $TMPDIR/rdp-proxy
git checkout repository
git pull

mvn install:install-file \
 -DgroupId=rdp-proxy \
 -DartifactId=rdp-proxy \
 -Dversion=${VERSION} \
 -Dpackaging=pom \
 -DgeneratePom=false \
 -Dfile=${DIR}/pom.xml \
 -DlocalRepositoryPath=. \
 -DcreateChecksum=true

mvn install:install-file \
 -DgroupId=rdp-proxy \
 -DartifactId=rdp-proxy-core \
 -Dversion=${VERSION} \
 -Dfile=${DIR}/rdp-proxy-core/target/rdp-proxy-core-${VERSION}.jar \
 -Dpackaging=jar \
 -DgeneratePom=false \
 -DpomFile=${DIR}/rdp-proxy-core/pom.xml \
 -DlocalRepositoryPath=. \
 -DcreateChecksum=true

mvn install:install-file \
 -DgroupId=rdp-proxy \
 -DartifactId=rdp-proxy-file \
 -Dversion=${VERSION} \
 -Dfile=${DIR}/rdp-proxy-file/target/rdp-proxy-file-${VERSION}.jar \
 -Dpackaging=jar \
 -DgeneratePom=false \
 -DpomFile=${DIR}/rdp-proxy-file/pom.xml \
 -DlocalRepositoryPath=. \
 -DcreateChecksum=true

mvn install:install-file \
 -DgroupId=rdp-proxy \
 -DartifactId=rdp-proxy-service-spi \
 -Dversion=${VERSION} \
 -Dfile=${DIR}/rdp-proxy-service-spi/target/rdp-proxy-service-spi-${VERSION}.jar \
 -Dpackaging=jar \
 -DgeneratePom=false \
 -DpomFile=${DIR}/rdp-proxy-service-spi/pom.xml \
 -DlocalRepositoryPath=. \
 -DcreateChecksum=true

mvn install:install-file \
 -DgroupId=rdp-proxy \
 -DartifactId=rdp-proxy-store-spi \
 -Dversion=${VERSION} \
 -Dfile=${DIR}/rdp-proxy-store-spi/target/rdp-proxy-store-spi-${VERSION}.jar \
 -Dpackaging=jar \
 -DgeneratePom=false \
 -DpomFile=${DIR}/rdp-proxy-store-spi/pom.xml \
 -DlocalRepositoryPath=. \
 -DcreateChecksum=true

git add rdp-proxy
git commit -a -m "Repo release."
git push

rm -rf $TMPDIR
