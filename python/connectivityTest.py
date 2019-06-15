#!/usr/bin/python3

import yaml
import sys
import socket
import struct

def closeSocket(s):
  s.setsockopt(socket.SOL_SOCKET, socket.SO_LINGER, struct.pack('ii', 1, 0))
  s.close()

def checkConnectivity( key, host, port):
  try:
    s = socket.create_connection( ( host, port), 3000)
    closeSocket(s)
    print( "OK: "+key)
  except:
    print( "BAD: "+key +" "+ str(sys.exc_info()[0]) )
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
