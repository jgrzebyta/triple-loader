
FROM debian:jessie-backports

ENV REPOSITORY_URL https://github.com/jgrzebyta/sesame-loader.git
ENV LOADER_VERSION 0.1.1
ENV LOADER_NAME sesame-loader-"$LOADER_VERSION"
ENV LOADER_DIR /opt/"$LOADER_NAME"

ENV PATH "$LOADER_DIR":$PATH
ENV BOOT_AS_ROOT yes


RUN apt-get update \
    && apt-get -y dist-upgrade \
    && apt-get -y install make curl openjdk-8-jre git bzip2 zip xz-utils

# install sesame-loader
RUN cd /opt/ \
    && git clone $REPOSITORY_URL $LOADER_NAME \
    && cd $LOADER_NAME \
    make
