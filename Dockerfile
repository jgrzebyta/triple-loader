
FROM debian:jessie-backports

ENV LOADER_VERSION 0.1.3-SNAPSHOT
ENV LOADER_NAME sesame-loader
ENV LOADER_URL https://github.com/jgrzebyta/sesame-loader/releases/download/0.1.3.1/triple-loader-"$LOADER_VERSION"-standalone.jar
ENV LOADER_DIR /opt/"$LOADER_NAME"

ENV PATH "$LOADER_DIR":$PATH
ENV BOOT_AS_ROOT yes


RUN apt-get update \
    && apt-get -y dist-upgrade \
    && apt-get -y install curl openjdk-8-jre

# install sesame-loader
RUN curl --create-dirs -Lfo "${LOADER_DIR}"/sesame-loader.jar "${LOADER_URL}"

CMD ["/bin/bash"]
