package src;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public interface Loggers {
    Logger SEQNUM_LOGGER = Logger.getLogger("seqnum");
    Logger ACK_LOGGER = Logger.getLogger("ack");
    Logger ARRIVAL_LOGGER = Logger.getLogger("arrival");

    static void setup() throws IOException {
        FileHandler seqnumfh = new FileHandler("/logs/seqnum.log", true);
        SEQNUM_LOGGER.addHandler(seqnumfh);
        EmptyFormatter seqnumformatter = new EmptyFormatter();
        seqnumfh.setFormatter(seqnumformatter);
        SEQNUM_LOGGER.setUseParentHandlers(false);

        FileHandler ackfh = new FileHandler("/logs/ack.log", true);
        ACK_LOGGER.addHandler(ackfh);
        EmptyFormatter ackformatter = new EmptyFormatter();
        ackfh.setFormatter(ackformatter);
        ACK_LOGGER.setUseParentHandlers(false);

        FileHandler arrivalfh = new FileHandler("/logs/arrival.log", true);
        ARRIVAL_LOGGER.addHandler(arrivalfh);
        EmptyFormatter arrivalformatter = new EmptyFormatter();
        seqnumfh.setFormatter(arrivalformatter);
        ARRIVAL_LOGGER.setUseParentHandlers(false);
    }

    class EmptyFormatter extends SimpleFormatter {
        @Override
        public String format(LogRecord record) {
            return record.getMessage() + "\r\n";
        }
    }
}
