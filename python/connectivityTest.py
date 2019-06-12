#!/usr/bin/python3

import yaml
import sys
import socket

def checkConnectivity( key, host, port):
  try:
    socket.create_connection( ( host, port), 3000)
    print( "OK: "+key)
  except:
    print( "BAD: "+key)
    
def processInputFile(file):
  print (file)
  with open(file, 'r') as stream:
    try:
        config = yaml.safe_load(stream)
    except yaml.YAMLError as exc:
        print(exc)
        raise exc
  checks = config["checks"]
  ping = checks["ping"]
  for key, value in ping.items():
    ( host, port ) = value.split(':')
    port = int(port)
    #print ("host %s port %d" % ( host, port ))
    checkConnectivity( key, host, port)

if len(sys.argv)<=1:
  raise Exception('please provide input file argument')

for arg in sys.argv[1:]:
  processInputFile(arg)
