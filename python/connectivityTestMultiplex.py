#!/usr/bin/python3

import yaml
import sys

import socket
import select
import time
import errno
import struct

pending = []

def closeSocket(s):
  s.setblocking(1)
  s.setsockopt(socket.SOL_SOCKET, socket.SO_LINGER, struct.pack('ii', 1, 0))
  s.close()

def closeCtx(ctx):
  global pending
  s = ctx["socket"]
  closeSocket(s)
  pending.remove(ctx)
  
def checkPending():
  global pending
  array = []
  contexts = {}
  now = time.time()
  for ctx in pending:
    s = ctx["socket"]
    contexts[s] = ctx
    array.append(s)
  ready_to_read, ready_to_write, in_error = select.select([], array, [])
  #print("  ready_to_read %d ready_to_write %d inerror %d" % (len(ready_to_read),len( ready_to_write), len(in_error)))
  for sock in ready_to_write:
    ctx = contexts[sock]
    err = sock.getsockopt(socket.SOL_SOCKET, socket.SO_ERROR) 
    if err == 0:
      print("OK: "+ctx["key"])
      closeCtx(ctx)
    elif errno.errorcode[err] != "EINPROGRESS":
      #print( "BAD: "+ctx["key"] + " " +errno.errorcode[err])
      print( "BAD: "+ctx["key"]   )
      closeCtx(ctx)
      
def checkConnectivity( key, host, port):
  global pending
  context = {
      "key" : key,
      "socket" : None
  }
  while ( len(pending) >= 200):
      checkPending()
      time.sleep(.5)
      
  s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
  context['socket'] = s 
  s.settimeout(30)
  s.setblocking(0) 
  try:     
    err = s.connect_ex( ( host, port))
    if err == 0:
      print("OK: "+key)
      closeSocket(s)
    elif errno.errorcode[err] == "EINPROGRESS":
      pending.append( context)
    else:
      #print( "BAD: "+key + " "+errno.errorcode[err])
      print( "BAD: "+key  )
      closeSocket(s)
  except:
      #print( "BAD: "+key +" "+ str(sys.exc_info()[0]) )
      print( "BAD: "+key  )
      closeSocket(s)
      
def processInputFile(file):
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
  while (len(pending) > 0):
    checkPending()
    time.sleep(.5)
