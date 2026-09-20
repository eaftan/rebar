#!/bin/sh
set -eu

exec mvn -q clean verify
