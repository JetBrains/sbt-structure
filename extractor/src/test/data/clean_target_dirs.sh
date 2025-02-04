#!/bin/bash

# Find and remove all "target" directories recursively from the current directory
find . -type d -name "target" -exec rm -rf {} +

echo "All 'target' directories have been removed."