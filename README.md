# CS 456 Assignment 2

The goal of this assignment was to implement the Go-Back-N protocol, which could be used to transfer a text file from one host to another across an unreliable network. The protocol should be able to handle network errors, such as packet loss and duplicate packets. For simplicity, the protocol is unidirectional, i.e., data will flow in one direction (from the sender to the receiver) and the acknowledgements (ACKs) in the opposite direction. To implement this protocol, I wrote two programs: a sender and a receiver, with the specifications given below. I tested my implementation using an emulated network link: When the sender needs to send packets to the receiver, it sends them to the network emulator instead of sending them directly to the receiver. The network emulator then forwards the received packets to the receiver. However, it may randomly discard and/or delay received packets. The same scenario happens when the receiver sends ACKs to the sender.

## How to run the program

Run the makefile on the sender, receiver and network emulator machines, and then give yourself permissions to run the programs:
```
make
chmod +x ./nEmulator-linux386
chmod +x ./src/receiver/receiver.class
chmod +x ./src/sender/sender.class
```
Start the emulator on your first machine:
```
./nEmulator-linux386 <PORT_1> <RECEIVER_ADDRESS> <PORT_2> <PORT_3> <SENDER_ADDRESS> <PORT_4> <DELAY> <LOSS> <VERBOSE>
```
where 
* <PORT_1> is the emulator's receiving UDP port number in the forward (sender) direction
* <RECEIVER_ADDRESS> is the receiver’s network address
* <PORT_2> is the receiver’s receiving UDP port number
* <PORT_3> is the emulator's receiving UDP port number in the backward (receiver) direction
* <SENDER_ADDRESS> is the sender’s network address
* <PORT_4> is the sender’s receiving UDP port number
* <DELAY> is the maximum delay of the link in units of millisecond (>= 0)
* <LOSS> is the packet discard probability (0 - 1)
* <VERBOSE> is the verbose-mode setting (0 or 1)

Then start the receiver on your second machine:
```
java -cp . src.receiver.receiver <EMULATOR_ADDRESS> <PORT_3> <PORT_2> <OUTPUT_FILE>
```
where 
* <EMULATOR_ADDRESS> is the hostname for the network emulator
* <PORT_3> is the UDP port number used by the link emulator to receive ACKs from the receiver
* <PORT_2> is the UDP port number used by the receiver to receive data from the emulator
* <OUTPUT_FILE> is the name of the file into which the received data is written

Then start the sender on your third machine:
```
java -cp . src.sender.sender <EMULATOR_ADDRESS> <PORT_1> <PORT_4> <INPUT_FILE>
```
where 
* <EMULATOR_ADDRESS> is the host address of the network emulator
* <PORT_1> is the UDP port number used by the emulator to receive data from the sender
* <PORT_4> is the UDP port number used by the sender to receive ACKs from the emulator
* <OUTPUT_FILE> is the name of the file to be transferred

The seqnum and ack logs from the transfer process can be found on the sender machine, while the arrival logs can be found on the receiver machine:

```
cd ./logs
```

## Machines program was tested on

ubuntu1604-002, ubuntu1604-004 and ubuntu1604-006 on the CS Student Computing Environment.

## Author

Kyra Wang Nian Yu, WATID 20809112