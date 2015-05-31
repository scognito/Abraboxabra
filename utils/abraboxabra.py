# Abraboxabra 1.0
# Author: Pietro Di Vita(scognito@gmail.com)

import time
import RPi.GPIO as GPIO
import os
from bluetooth import *

GPIO.setmode(GPIO.BOARD)

#Commented as GPIO.setup close the circuit. Will use a workaround
#GPIO.setup(7, GPIO.OUT)

#workaround
first_time = True

server_sock=BluetoothSocket( RFCOMM )
server_sock.bind(("",PORT_ANY))
server_sock.listen(1)

port = server_sock.getsockname()[1]

uuid = "94f39d29-7d6d-437d-973b-fba39e49d4ee"

advertise_service( server_sock, "SampleServer",
                   service_id = uuid,
                   service_classes = [ uuid, SERIAL_PORT_CLASS ],
                   profiles = [ SERIAL_PORT_PROFILE ],
#                   protocols = [ OBEX_UUID ]
                  )

print "Waiting for connection on RFCOMM channel %d" % port

client_sock, client_info = server_sock.accept()
print "Accepted Bluetooth connection from ", client_info

while True:
   try:
        data = client_sock.recv(1024)
#       if len(data) == 0: break
        print "received command [%s]" % data
#       client_sock.send('received message: ' + data);
        if data == 'openclose':
                if first_time:
                        GPIO.setup(7, GPIO.OUT)
                        first_time = False

                # simulate pressing button for 1 second
                GPIO.output(7,False)
                time.sleep(1)
                GPIO.output(7,True)
                client_sock.send("OK")
        elif data == 'shutdown':
                os.system("sudo shutdown -h now")
                client_sock.send("OK")

   except IOError:
      client_sock, client_info = server_sock.accept()
      print "Accepted Bluetooth connection from ", client_info
      pass

print "disconnected"

time.sleep(1)
print "DEBUG: fine, 7 true"
GPIO.output(7,True)

client_sock.close()
server_sock.close()
print "All done, have a nice day :)"
