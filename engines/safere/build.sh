#!/bin/sh
set -eu

# Use a numbered release so Rebar measurements can identify the library used.
mvn -q org.apache.maven.plugins:maven-dependency-plugin:3.11.0:copy \
  -Dartifact=org.safere:safere:0.11.0 \
  -DoutputDirectory=. \
  -Dmdep.stripVersion=true
javac -cp safere.jar Main.java
