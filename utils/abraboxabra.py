# Abraboxabra 1.0
# Author: Pietro Di Vita(scognito@gmail.com)

import time
import RPi.GPIO as GPIO
import os
from bluetooth import *
from thread import *

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

print "Waiting for connection on RFCOMM channel %d" % port

#Function for handling connections. This will be used to create threads
def clientthread(conn):
     
    global first_time
    #infinite loop so that function do not terminate and thread do not end.
    while True:
        try: 
		#Receiving from client
		data = conn.recv(1024)
		print "received command [%s]" % data

		client_sock.send('received message ' + data);
		if data == 'openclose':
			if first_time:
				GPIO.setup(7, GPIO.OUT)
				first_time = False

			GPIO.output(7, False)
			time.sleep(2)
			GPIO.output(7,True)
			client_sock.send("OK")
		elif data == 'shutdown':
			os.system('sudo shutdown now -h')
	except IOError:
		print "Disconnected from ", client_info
		pass
     
    #came out of loop
    conn.close()

#
# MAIN
#
while True:
	client_sock, client_info = server_sock.accept()
	print "Accepted Bluetooth connection from ", client_info

	start_new_thread(clientthread ,(client_sock,))

print "disconnected"

time.sleep(1)
GPIO.output(7,True)

server_sock.close()
print "all done"
