package src;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.logging.Logger;

public class receiver {

    private static Logger logger = Logger.getLogger(sender.class.getName());

    private static InetAddress netAddress;
    private static Integer netPort;
    private static Integer receiverPort;
    private static String filename;

    private static Boolean eotReceived = false;
    private static ArrayList<packet> packetList = new ArrayList<>();
    private static Integer seqnum = 0;

    public static void main(String[] args) {

        try {
            Loggers.setup();
        } catch (IOException e) {
            logger.warning(e.toString());
        }

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
            while (!eotReceived) {
                byte[] buf = new byte[1000];
                DatagramPacket rPacket = new DatagramPacket(buf, buf.length);
                receiverSocket.receive(rPacket);
                byte[] receivedData = rPacket.getData();
                packet p = packet.parseUDPdata(receivedData);
                Loggers.ARRIVAL_LOGGER.info(String.valueOf(p.getSeqNum()));
                if (p.getType() == 2 && p.getSeqNum() == seqnum) {
                    eotReceived = true;
                    sendUtil(sendSocket, packet.createEOT(seqnum));
                    writeFile(packetList, filename);
                }
                else if (p.getType() == 1 && p.getSeqNum() == seqnum) {
                    packetList.add(p);
                    sendUtil(sendSocket, packet.createACK(seqnum));
                    seqnum ++;
                }
                else {
                    sendUtil(sendSocket, packet.createACK(seqnum));
                }
            }
        } catch (Exception e) {
            logger.warning(e.toString());
        }

    }

    private static void writeFile(ArrayList<packet> packetList, String filename) throws IOException {
        File file = new File(filename);
        FileWriter writer = new FileWriter(file, true);
        try (PrintWriter output = new PrintWriter(writer)) {
            for (packet p : packetList) {
                String s = new String(p.getData());
                output.print(s);
            }
        }
    }

    private static void sendUtil(DatagramSocket ds, packet p) throws IOException {
        byte[] UDPData = p.getUDPdata();
        DatagramPacket UDPp = new DatagramPacket(UDPData, UDPData.length, netAddress, netPort);
        ds.send(UDPp);
    }
}
