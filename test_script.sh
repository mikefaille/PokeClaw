#!/bin/bash
find . -type f -name "*.kt" -exec grep -l "class Unified" {} +
