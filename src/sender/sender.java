package src.sender;

import src.utility.LoggerFormatter;
import src.utility.packet;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

public class sender {

    // Loggers
    private static Logger logger = Logger.getLogger(sender.class.getName());
    private static Logger SEQNUM_LOGGER = Logger.getLogger("seqnum");
    private static Logger ACK_LOGGER = Logger.getLogger("ack");

    // Shared variables
    private static final int windowSize = 10;
    private static List<Integer> outstandingAcks = Collections.synchronizedList(new ArrayList<Integer>());
    private static AtomicInteger latestAck = new AtomicInteger();
    private static ArrayList<packet> packetList;
    private static final Object lock = new Object();
    private static int seqnumMultiplier = 0;

    // CLI Arguments
    private static InetAddress netAddress;
    private static Integer netPort;
    private static Integer senderPort;
    private static volatile Boolean eotReceived;

    // Main method
    public static void main(String[] args) throws InterruptedException, IOException {

        // Setting up seqnum and ack loggers
        FileHandler seqnumfh;
        LoggerFormatter seqnumformatter;
        FileHandler ackfh;
        LoggerFormatter ackformatter;

        seqnumfh = new FileHandler("./logs/seqnum.log");
        SEQNUM_LOGGER.addHandler(seqnumfh);
        seqnumformatter = new LoggerFormatter();
        seqnumfh.setFormatter(seqnumformatter);
        SEQNUM_LOGGER.setUseParentHandlers(false);

        ackfh = new FileHandler("./logs/ack.log");
        ACK_LOGGER.addHandler(ackfh);
        ackformatter = new LoggerFormatter();
        ackfh.setFormatter(ackformatter);
        ACK_LOGGER.setUseParentHandlers(false);

        // Parsing command line arguments
        try {
            netAddress = InetAddress.getByName(args[0]);
            netPort = Integer.parseInt(args[1]);
            senderPort = Integer.parseInt(args[2]);
            String filename = args[3];
            eotReceived = false;
            packetList = parseFile(filename);
            latestAck.set(-1);
        } catch (Exception e) {
            logger.warning("Wrong argument input, try again");
        }

        // Starting the packet sending code and ack listener code as separate threads
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Callable<Object>> callables = Arrays.asList(
                new SendCallable(),
                new ListenCallable()
        );
        logger.info("Executor invoking callables");
        executor.invokeAll(callables);

        // Shutdown the executor after threads are done running
        executor.shutdown();
        if (executor.awaitTermination(0, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
        logger.info("Executor shutting down");
        ackfh.close();
        seqnumfh.close();
    }

    // Threads

    // Thread to send packets to the network emulator
    static class SendCallable implements Callable<Object> {
        @Override
        public Object call() throws Exception {
            try (DatagramSocket sendSocket = new DatagramSocket()) {

                // Creating a new timer thread that will resend the outstanding packets when it times out
                Timer timer = new Timer("Timer");
                TimeoutTask timeoutTask = new TimeoutTask();
                timer.scheduleAtFixedRate(timeoutTask, 1000, 1000);

                // Loops while EOT has not been received from the receiver
                while (!eotReceived) {
                    int lastSent = latestAck.get();

                    // Sent the last packet, now sending the EOT
                    if (lastSent == packetList.size() - 1) {
                        logger.info("Sending EOT");
                        int eotSeqnum = packetList.size();
                        if (!outstandingAcks.contains(eotSeqnum)) {
                            packet eotPacket = packet.createEOT(eotSeqnum);
                            sendUtil(sendSocket, eotPacket);
                            outstandingAcks.add(eotSeqnum);
                            timeoutTask.cancel();
                            timeoutTask = new TimeoutTask();
                            timer.scheduleAtFixedRate(timeoutTask, 1000, 1000);
                        }
                    }
                    // If window is not full send packets until the window is full
                    else if (outstandingAcks.size() < windowSize) {
                        for (int i = lastSent; i < lastSent + windowSize; i++) {
                            if (((i + 1) < packetList.size()) && !outstandingAcks.contains(i + 1)) {
                                packet p = packetList.get(i + 1);
                                sendUtil(sendSocket, p);
                                outstandingAcks.add(i + 1);
                                logger.info(String.valueOf(outstandingAcks));
                                timeoutTask.cancel();
                                timeoutTask = new TimeoutTask();
                                timer.scheduleAtFixedRate(timeoutTask, 1000, 1000);
                            }
                        }
                    }
                    // Waits for the receiver to remove outstanding acks before running the loop again
                    synchronized (lock) {
                        lock.wait();
                    }
                }
                timer.cancel();
            }
            return null;
        }
    }

    // Thread to listen for acks from the network emulator
    static class ListenCallable implements Callable<Object> {
        @Override
        public Object call() throws Exception {
            try (DatagramSocket receiveSocket = new DatagramSocket(senderPort)) {

                int lastReceivedSeqnum = -1;

                // Loops while EOT has not been received from the receiver
                while (!eotReceived) {
                    byte[] buf = new byte[1000];
                    DatagramPacket rPacket = new DatagramPacket(buf, buf.length);
                    receiveSocket.receive(rPacket);
                    byte[] receivedData = rPacket.getData();
                    packet ackPacket = packet.parseUDPdata(receivedData);
                    int seqnum = ackPacket.getSeqNum();
                    ACK_LOGGER.info(String.valueOf(seqnum));
                    int type = ackPacket.getType();

                    if (seqnum == 0 && seqnum != lastReceivedSeqnum && lastReceivedSeqnum != -1) {
                        seqnumMultiplier ++;
                    }
                    int actualSeqnum = seqnum + (seqnumMultiplier * 32);

                    ArrayList<Integer> removeList = new ArrayList<>();
                    // EOT received
                    if (type == 2) {
                        eotReceived = true;
                        // Wakes the sending thread
                        synchronized (lock) {
                            lock.notifyAll();
                        }
                    }
                    // Removes the outstanding acks lower than the received ack
                    else if (actualSeqnum > latestAck.get() && seqnum != lastReceivedSeqnum) {
                        latestAck.set(actualSeqnum);
                        synchronized (outstandingAcks) {
                            Iterator i = outstandingAcks.iterator();
                            while (i.hasNext()) {
                                int ack = (int) i.next();
                                if (ack <= actualSeqnum) {
                                    removeList.add(ack);
                                }
                            }
                        }
                        for (Integer i: removeList) {
                            outstandingAcks.remove(i);
                            logger.info(String.valueOf(outstandingAcks));
                        }
                        // Wakes the sending thread
                        synchronized (lock) {
                            lock.notifyAll();
                        }
                        lastReceivedSeqnum = seqnum;
                    }
                }
            }
            return null;
        }
    }

    // Utility methods

    // Timer task that resends all outstanding packets in the window when time is up
    static class TimeoutTask extends TimerTask {
        public void run() {
            logger.info("Timer timed out, resending outstanding packets");
            synchronized (outstandingAcks) {
                Iterator i = outstandingAcks.iterator();
                while (i.hasNext()) {
                    int seqnum = (int) i.next();
                    if (seqnum < packetList.size()) {
                        try (DatagramSocket ds = new DatagramSocket()) {
                            sendUtil(ds, packetList.get(seqnum));
                        } catch (IOException e) {
                            logger.warning(e.toString());
                        }
                    } else {
                        try (DatagramSocket ds = new DatagramSocket()) {
                            sendUtil(ds, packet.createEOT(seqnum));
                        } catch (Exception e) {
                            logger.warning(e.toString());
                        }
                    }
                }
            }
            synchronized (lock) {
                lock.notifyAll();
            }
        }
    }

    // Parses the input file into an arraylist of packets
    private static ArrayList<packet> parseFile(String filename) {
        ArrayList<packet> packetList = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filename))){
            StringBuilder sb = new StringBuilder();
            int Seqnum = 0;
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
                while (sb.length() >= 500) {
                    String data = sb.substring(0, 500);
                    packetList.add(packet.createPacket(Seqnum, data));
                    Seqnum ++;
                    sb = new StringBuilder(sb.substring(500));
                }
            }
            String data = sb.toString();
            packetList.add(packet.createPacket(Seqnum, data));
        } catch (Exception e) {
            logger.warning(e.toString());
        }

        logger.info(String.valueOf(packetList));
        return packetList;
    }

    // Sends a packet via a datagram socket to the network emulator
    private static void sendUtil(DatagramSocket ds, packet p) throws IOException {
        SEQNUM_LOGGER.info(String.valueOf(p.getSeqNum()));
        byte[] UDPData = p.getUDPdata();
        DatagramPacket UDPp = new DatagramPacket(UDPData, UDPData.length, netAddress, netPort);
        ds.send(UDPp);
    }
}