package src;

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

    private static Logger logger = Logger.getLogger(sender.class.getName());
    private static Logger SEQNUM_LOGGER = Logger.getLogger("seqnum");
    private static Logger ACK_LOGGER = Logger.getLogger("ack");

    // Shared variables
    private static final int windowSize = 10;
    private static List<Integer> outstandingAcks = Collections.synchronizedList(new ArrayList<Integer>());
    private static AtomicInteger latestAck = new AtomicInteger();
    private static ArrayList<packet> packetList;
    private static final Object lock = new Object();

    private static InetAddress netAddress;
    private static Integer netPort;
    private static Integer senderPort;
    private static volatile Boolean eotReceived;

    // Main method
    public static void main(String[] args) throws InterruptedException {

        try {
            FileHandler seqnumfh = new FileHandler("/logs/seqnum.log");
            SEQNUM_LOGGER.addHandler(seqnumfh);
            LoggerFormatter seqnumformatter = new LoggerFormatter();
            seqnumfh.setFormatter(seqnumformatter);
            SEQNUM_LOGGER.setUseParentHandlers(false);

            FileHandler ackfh = new FileHandler("/logs/ack.log");
            ACK_LOGGER.addHandler(ackfh);
            LoggerFormatter ackformatter = new LoggerFormatter();
            ackfh.setFormatter(ackformatter);
            ACK_LOGGER.setUseParentHandlers(false);
        } catch (IOException e) {
            logger.warning(e.toString());
        }

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

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Callable<Object>> callables = Arrays.asList(
                new SendCallable(),
                new ListenCallable()
        );
        logger.info("Executor invoking callables");
        executor.invokeAll(callables);
        executor.shutdown();
        if (executor.awaitTermination(0, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
        logger.info("Executor shutting down");
    }

    // Threads
    // Thread to send packets to the network emulator
    static class SendCallable implements Callable<Object> {
        @Override
        public Object call() throws Exception {
            try (DatagramSocket sendSocket = new DatagramSocket()) {

                TimerTask timeoutTask = new TimerTask() {
                    public void run() {
                        synchronized (outstandingAcks) {
                            Iterator i = outstandingAcks.iterator();
                            while (i.hasNext()) {
                                int seqnum = (int) i.next();
                                if (seqnum < packetList.size()) {
                                    try {
                                        sendUtil(sendSocket, packetList.get(seqnum));
                                    } catch (IOException e) {
                                        logger.warning(e.toString());
                                    }
                                } else {
                                    try {
                                        sendUtil(sendSocket, packet.createEOT(seqnum));
                                    } catch (Exception e) {
                                        logger.warning(e.toString());
                                    }
                                }
                            }
                        }
                    }
                };
                Timer timer = new Timer("Timer");
                timer.scheduleAtFixedRate(timeoutTask, 100, 100);

                while (!eotReceived) {
                    if (latestAck.get() == packetList.size() - 1) {
                        int eotSeqnum = packetList.size();
                        if (!outstandingAcks.contains(eotSeqnum)) {
                            packet eotPacket = packet.createEOT(eotSeqnum);
                            sendUtil(sendSocket, eotPacket);
                            outstandingAcks.add(eotSeqnum);
                            timer.cancel();
                            timer = new Timer("Timer");
                            timer.scheduleAtFixedRate(timeoutTask, 100, 100);
                        }
                    }
                    else if (outstandingAcks.size() < windowSize) {
                        for (int i = latestAck.get(); i < latestAck.get() + windowSize; i++) {
                            if (((i + 1) < packetList.size()) && !outstandingAcks.contains(i + 1)) {
                                packet p = packetList.get(i + 1);
                                byte[] UDPData = p.getUDPdata();
                                DatagramPacket UDPp = new DatagramPacket(UDPData, UDPData.length, netAddress, netPort);
                                sendSocket.send(UDPp);
                                outstandingAcks.add(p.getSeqNum());
                                timer.cancel();
                                timer = new Timer("Timer");
                                timer.scheduleAtFixedRate(timeoutTask, 100, 100);
                            }
                        }
                    }
                    synchronized (lock) {
                        lock.wait();
                    }
                }
            }
            return null;
        }
    }

    // Thread to listen for acks from the network emulator
    static class ListenCallable implements Callable<Object> {
        @Override
        public Object call() throws Exception {
            try (DatagramSocket receiveSocket = new DatagramSocket(senderPort)) {
                while (!eotReceived) {
                    byte[] buf = new byte[1000];
                    DatagramPacket rPacket = new DatagramPacket(buf, buf.length);
                    receiveSocket.receive(rPacket);
                    byte[] receivedData = rPacket.getData();
                    packet ackPacket = packet.parseUDPdata(receivedData);
                    int seqnum = ackPacket.getSeqNum();
                    ACK_LOGGER.info(String.valueOf(seqnum));
                    int type = ackPacket.getType();
                    ArrayList<Integer> removeList = new ArrayList<>();
                    if (type == 2) {
                        eotReceived = true;
                    }
                    else if (seqnum == latestAck.get() + 1) {
                        latestAck.set(seqnum);
                        synchronized (outstandingAcks) {
                            Iterator i = outstandingAcks.iterator();
                            while (i.hasNext()) {
                                int ack = (int) i.next();
                                if (ack <= seqnum) {
                                    removeList.add(ack);
                                }
                            }
                        }
                        for (Integer i: removeList) {
                            outstandingAcks.remove(i);
                        }
                        synchronized (lock) {
                            lock.notifyAll();
                        }
                    }
                }
            }
            return null;
        }
    }

    // Utility methods
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

        return packetList;
    }

    private static void sendUtil(DatagramSocket ds, packet p) throws IOException {
        byte[] UDPData = p.getUDPdata();
        DatagramPacket UDPp = new DatagramPacket(UDPData, UDPData.length, netAddress, netPort);
        ds.send(UDPp);
        SEQNUM_LOGGER.info(String.valueOf(p.getSeqNum()));
    }
}