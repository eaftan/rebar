#!/bin/sh
set -eu

exec mvn -q -U clean verify
