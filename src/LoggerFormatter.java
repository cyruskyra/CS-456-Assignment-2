package src;

import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

public class LoggerFormatter extends SimpleFormatter {
    @Override
    public String format(LogRecord record) {
        return record.getMessage() + "\r\n";
    }
}
