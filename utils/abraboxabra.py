#!/usr/bin/python

# Abraboxabra 2.0
# Author: Pietro Di Vita (scognito@gmail.com)

# 2.0 - 2015-08-06 - Added server commands for add/remove remotes, added config.ini file
#                    Requires abraboxabra-agent.py
# 1.0 - 2015-07-10 - Initial Release

import thread
import time
import RPi.GPIO as GPIO
import os
from bluetooth import *
from thread import *
import subprocess
import ConfigParser
import signal
import psutil

#SETTINGS
pairing_running = 0
agent_path='/root/abraboxabra-agent.py'
test_device_path='/usr/bin/bluez-test-device'

#INI SECTION
config_file='/root/config.ini'
default_pass='0000'

#Function for handling connections. This will be used to create threads
def clientthread(conn):
     
    global first_time
    #infinite loop so that function do not terminate and thread do not end.
    while True:
        try: 
		#Receiving from client
		data = conn.recv(1024)
		print "received command [%s]" % data

		#client_sock.send('received message ' + data);
		if data == 'openclose':
			if first_time:
				GPIO.setup(7, GPIO.OUT)
				first_time = False

			GPIO.output(7, False)
			time.sleep(1.4)
			GPIO.output(7,True)
		elif data == 'shutdown':
			os.system('shutdown now -h')
		elif data == 'device_list':
			client_sock.send(get_paired_devices())
		elif data.startswith ('remove_device'):
			remove_device(data[14:])
		elif data == 'enable_pairing':
			if pairing_running == 0:
				print "Enabling pairing"
				try:
				   thread.start_new_thread( thread_pairing, ("Thread-1", 60, ) )
				except:
				   print "Error: unable to start thread"
			else:
				print "Pairing in progress. Not starting new thread"
				#thread.start_new_thread( thread_pairing, ("Thread-1", 10, ) )
			
	except IOError:
		print "Disconnected from ", client_info
		pass
     
    #came out of loop
    conn.close()

def thread_pairing( threadName, delay):
	print "Thread " + threadName + "started"
	global pairing_running 
   
	pairing_running = 1
	
	os.system("hciconfig hci0 piscan");
	process = subprocess.Popen([agent_path, "-p" + default_pass]);
	print "Process pid: " + str(process.pid)
	time.sleep(delay)
	process.kill()
   
	print "-> " + threadName + ": pairing disabled"
	
	os.system("hciconfig hci0 pscan");
	pairing_running = 0

def get_paired_devices():                                                                    
        print "Executing " + test_device_path +  " list"                                        
        device_list_output = subprocess.check_output(test_device_path + " list", shell=True) 
                                                                                             
        device_list = device_list_output.split('\n')                                         
        list_len = len(device_list)-1                                                        
        result = ""                                                                          
        i=0                                                                                  
        for device in device_list:                                                           
                if i == list_len:                                                            
                        break                                                                
                if i == list_len-1:                                                          
                        result = result + device                                             
                else:                                                                        
                        result = result +  device + "<-->"                                   
                i=i+1                                                                        
                                                                                             
        if i==0:                                                                
                result="none"

	print result
	return result

def remove_device(address):
	print "Eseguo " + test_device_path + " remove " + address
	process = subprocess.Popen([test_device_path, "remove", address])


#
# MAIN
#

config = ConfigParser.ConfigParser()

if len(config.read(config_file)) != 1:
   print "not found"   
   default_config = open(config_file, "w")
   default_config.write('[MAIN]\n')
   default_config.write('PASSWORD='+default_pass+'\n')
   default_config.close
else:
   print "found!"
   default_pass = config.get('MAIN', 'PASSWORD')

print "inizio! password = " + default_pass

GPIO.setmode(GPIO.BOARD)

#Commented as GPIO.setup close the circuit. Will use a workaround
#GPIO.setup(7, GPIO.OUT)

#workaround
first_time = True

server_sock=BluetoothSocket( RFCOMM )
server_sock.bind(("",PORT_ANY))
server_sock.listen(10)

port = server_sock.getsockname()[1]

uuid = "94f39d29-7d6d-437d-973b-fba39e49d4ee"

advertise_service( server_sock, "Abraboxabra server",
                   service_id = uuid,
                   service_classes = [ uuid, SERIAL_PORT_CLASS ],
                   profiles = [ SERIAL_PORT_PROFILE ], 
#                   protocols = [ OBEX_UUID ]
                    )

#GPIO.output(7,True)


# Create two threads as follows
#try:
#   thread.start_new_thread( thread_pairing, ("Thread-1", 60, ) )
#
#except:
#   print "Error: unable to start thread"
#
print "Waiting for connection on RFCOMM channel %d" % port

while True:
	client_sock, client_info = server_sock.accept()
	print "Accepted Bluetooth connection from ", client_info

	start_new_thread(clientthread ,(client_sock,))

print "disconnected"

time.sleep(1)
GPIO.output(7,True)

server_sock.close()
print "all done"

