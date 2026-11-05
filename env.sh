#!/usr/bin/env bash
# Local toolchain (user-local installs, no root required).
# Source this before building: `source env.sh`
export JAVA_HOME="$HOME/.local/opt/jdk-25.0.5+8"
export PATH="$JAVA_HOME/bin:$HOME/.local/opt/apache-maven-3.9.16/bin:$PATH"
