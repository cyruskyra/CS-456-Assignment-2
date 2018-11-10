package src.receiver;

import src.utility.LoggerFormatter;
import src.utility.packet;
import src.sender.sender;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

public class receiver {

    // Loggers
    private static Logger logger = Logger.getLogger(sender.class.getName());
    private static Logger ARRIVAL_LOGGER = Logger.getLogger("arrival");

    // CLI arguments
    private static InetAddress netAddress;
    private static Integer netPort;
    private static Integer receiverPort;
    private static String filename;

    // Shared variables
    private static Boolean eotReceived = false;
    private static ArrayList<packet> packetList = new ArrayList<>();
    private static Integer seqnum = 0;

    // Main method
    public static void main(String[] args) throws IOException {

        // Setting up the arrival logger
        FileHandler arrivalfh;
        LoggerFormatter arrivalformatter;

        arrivalfh = new FileHandler("./logs/arrival.log");
        ARRIVAL_LOGGER.addHandler(arrivalfh);
        arrivalformatter = new LoggerFormatter();
        arrivalfh.setFormatter(arrivalformatter);
        ARRIVAL_LOGGER.setUseParentHandlers(false);

        // Parsing the CLI arguments
        try {
            netAddress = InetAddress.getByName(args[0]);
            netPort = Integer.parseInt(args[1]);
            receiverPort = Integer.parseInt(args[2]);
            filename = args[3];
        } catch (Exception e) {
            logger.warning("Wrong argument input, try again");
        }

        try (DatagramSocket receiverSocket = new DatagramSocket(receiverPort);
             DatagramSocket sendSocket = new DatagramSocket()) {
            // Loops while EOT has not been received from the receiver
            while (!eotReceived) {
                byte[] buf = new byte[1000];
                DatagramPacket rPacket = new DatagramPacket(buf, buf.length);
                receiverSocket.receive(rPacket);
                byte[] receivedData = rPacket.getData();
                packet p = packet.parseUDPdata(receivedData);
                int pSeqnum = p.getSeqNum();
                logger.info(String.valueOf(pSeqnum));
                ARRIVAL_LOGGER.info(String.valueOf(pSeqnum));
                // EOT received, write all the packets received to the output file and send back an EOT
                if (p.getType() == 2) {
                    sendUtil(sendSocket, packet.createEOT(pSeqnum));
                    writeFile(packetList, filename);
                    eotReceived = true;
                }
                // Packet received is the packet expected, add to the packet list and send an ack
                else if (p.getType() == 1 && pSeqnum == seqnum) {
                    packetList.add(p);
                    sendUtil(sendSocket, packet.createACK(pSeqnum));
                    if (pSeqnum == 31) {
                        seqnum = 0;
                    } else {
                        seqnum++;
                    }
                }
                // Packet received is not the packet expected, send back last sent ack
                else {
                    sendUtil(sendSocket, packet.createACK(seqnum - 1));
                }
            }
        } catch (Exception e) {
            logger.warning(e.toString());
        }

        arrivalfh.close();
    }


    // Utility methods

    // Method to write a new file by combining all the received packets
    private static void writeFile(ArrayList<packet> packetList, String filename) throws IOException {
        try (PrintWriter output = new PrintWriter(filename, StandardCharsets.UTF_8)) {
            for (packet p : packetList) {
                String s = new String(p.getData());
                output.print(s);
            }
        }
    }

    // Sends a packet via a datagram socket to the network emulator
    private static void sendUtil(DatagramSocket ds, packet p) throws IOException {
        byte[] UDPData = p.getUDPdata();
        DatagramPacket UDPp = new DatagramPacket(UDPData, UDPData.length, netAddress, netPort);
        ds.send(UDPp);
    }
}
