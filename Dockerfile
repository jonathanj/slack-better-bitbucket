FROM debian:jessie
MAINTAINER "Jonathan Jacobs <jonathan@jsphere.com>"
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -qy openjdk-7-jre-headless
COPY ["target/uberjar/slack-better-bitbucket-0.1.0-SNAPSHOT-standalone.jar", "/srv/slack_better_bitbucket/slack-better-bitbucket.jar"]
WORKDIR /srv/slack_better_bitbucket
ENTRYPOINT ["/usr/bin/java", "-jar", "/srv/slack_better_bitbucket/slack-better-bitbucket.jar"]
EXPOSE 8880
EXPOSE 8881
