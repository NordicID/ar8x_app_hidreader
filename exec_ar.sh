#!/bin/sh

echo "Starting..."
~/java/bin/java -cp "./bin:./lib/*" -Djava.library.path="./lib" hidreader.hidreader
