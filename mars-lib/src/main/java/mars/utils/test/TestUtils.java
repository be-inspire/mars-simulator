package mars.utils.test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.logging.LogManager;

public class TestUtils {

	public static void loadLog4jStandard() {
		try {
			final InputStream i = new FileInputStream("jdk14logger.properties");
			LogManager.getLogManager().readConfiguration(i);
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

}
