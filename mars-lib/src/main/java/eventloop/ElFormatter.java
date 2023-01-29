package eventloop;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

public class ElFormatter extends Formatter {

	private final Formatter formatter = new SimpleFormatter();

	@Override
	public String format(LogRecord record) {
		if (El.inEl())
			record.setInstant(El.now());
		return formatter.format(record);
	}
}
