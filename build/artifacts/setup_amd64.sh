#!/usr/bin/env bash

#
# Plugin installation script
#


# Parameter: installation target path (i.e. /opt/simplifier inside the simplifier container)
INSTALL_TARGET=$1


# get path of installation source files (location of setup script)
INSTALL_SRC="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"

# copy plugin jar and config files
cp ${INSTALL_SRC}/assets/* ${INSTALL_TARGET}/appserver/plugins

# create data dirs
mkdir -p ${INSTALL_TARGET}/data/plugins/pdf/tmp
mkdir -p ${INSTALL_TARGET}/data/plugins/pdf/templates

# install Webkit HTML->PDF Tool (wkhtmltox)
WKTOHTML_TARGET=/opt/wkhtmltox
mv ${INSTALL_SRC}/wkhtmltox ${WKTOHTML_TARGET}

# carry over some deprecated libs from debian:bullseye, which are no longer installable on debian:bookworm target platform
ln -s /opt/wkhtmltox/libssl.so.1.1 /lib/x86_64-linux-gnu/
ln -s /opt/wkhtmltox/libcrypto.so.1.1 /lib/x86_64-linux-gnu/


# Legacy: provide reroute to wkhtmltopdf with patched qt
ln -s $WKTOHTML_TARGET/bin/wkhtmltopdf /usr/local/bin/wkhtmltopdf-xvfb
cd /opt/wkhtmltox/lib; cp -a * /lib




