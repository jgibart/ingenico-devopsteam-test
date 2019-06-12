#!/bin/bash

# by courtesy of https://stackoverflow.com/questions/5014632/how-can-i-parse-a-yaml-file-from-a-linux-shell-script
function parse_yaml {
   local prefix="$2"
   local s='[[:space:]]*' w='[a-zA-Z0-9_]*' fs=$(echo @|tr @ '\034')
   sed -ne "s|^\($s\):|\1|" \
        -e "s|^\($s\)\($w\)$s:$s[\"']\(.*\)[\"']$s\$|\1$fs\2$fs\3|p" \
        -e "s|^\($s\)\($w\)$s:$s\(.*\)$s\$|\1$fs\2$fs\3|p"  "$1" |
   awk -F$fs '{
      indent = length($1)/2;
      vname[indent] = $2;
      for (i in vname) {if (i > indent) {delete vname[i]}}
      if (length($3) > 0) {
         vn=""; for (i=0; i<indent; i++) {vn=(vn)(vname[i])("_")}
         printf("%s%s%s=\"%s\"\n", "'$prefix'",vn, $2, $3);
      }
   }'
}
function testConnectivity() {
  KEY="$1"
  HOST="$2"
  PORT="$3"
  if nc -d -w 3 $HOST $PORT 2>/dev/null
  then 
    echo "OK: $key"
  else
    echo "BAD: $key"
  fi
}

function processInputFile() {
  INPUTFILE="$1"
  parse_yaml "$INPUTFILE" | grep "checks_ping_" | while read a
  do
    keyvalue="${a##checks_ping_}"
    key="${keyvalue%=*}"
    site="${keyvalue##*=}"
    site="${site%\"}"
    site="${site#\"}"
    host="${site%:*}"
    port="${site#*:}"
    #echo " $key = $site   host = $host port = $port"
    testConnectivity "$key" "$host"  "$port"
  done
}
if [[ $# -eq 0 ]]
then
  expected input file argument
  exit 1
fi

for arg in "$@"
do
  processInputFile "$arg"
done
