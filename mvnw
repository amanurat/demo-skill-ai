#!/bin/sh
# Minimal Maven wrapper — delegates to the system Maven installation.
# In CI/CD environments with a full Maven wrapper setup, replace this file
# with the output of: mvn wrapper:wrapper -Dmaven=3.9.6
exec mvn "$@"
