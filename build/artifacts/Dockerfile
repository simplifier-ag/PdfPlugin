FROM simplifierag/simplifier-assembly:graal25_2.0.10 AS build
WORKDIR /build
COPY . /build/
RUN sbt assembly

FROM debian:bookworm-slim AS pdfplugin_builder
RUN apt-get update && \
    apt-get upgrade -f -y && \
    apt-get install -y curl xfonts-base xfonts-75dpi fontconfig fontconfig-config fonts-dejavu-core libbsd0 libexpat1 \
      libfontconfig1 libjpeg62-turbo libmd0 libx11-6 libx11-data libxau6 libxcb1 libxdmcp6 libxext6 libxrender1 sensible-utils ucf

RUN mkdir -p /opt/plugin

#  download and install wkhtmltox tool
RUN curl "https://github.com/wkhtmltopdf/packaging/releases/download/0.12.6.1-3/wkhtmltox_0.12.6.1-3.bookworm_amd64.deb" -L -o "/tmp/wkhtmltox_0.12.6.1-3.bookworm_amd64.deb"
RUN dpkg -i /tmp/wkhtmltox_0.12.6.1-3.bookworm_amd64.deb

#  provide plugin jar & config files
COPY --from=this assets /opt/plugin/assets
COPY --from=this setup_amd64.sh /opt/plugin/setup.sh
COPY --from=build /build/target/pdfPlugin.jar /opt/plugin/assets

FROM scratch
COPY --from=pdfplugin_builder /opt/plugin /opt/plugin
COPY --from=pdfplugin_builder /usr/local/bin/. /opt/plugin/wkhtmltox
COPY --from=pdfplugin_builder /usr/local/lib/. /opt/plugin/wkhtmltox

