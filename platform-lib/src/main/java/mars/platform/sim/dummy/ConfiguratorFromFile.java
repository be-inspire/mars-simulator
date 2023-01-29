package mars.platform.sim.dummy;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import mars.platform.logics.Payload;
import mars.platform.sim.SimulativeBay;

public class ConfiguratorFromFile {

	private final static Logger LOGGER = LoggerFactory.getLogger(ConfiguratorFromFile.class);

	public void configure(DummyPlatformInternals pi, DummyAutomationSystem automationSystem, File file)
			throws IOException {
		LOGGER.info("Loading form configuration file: {}", file);
		final Configuration configuration = loadConfigueration(file);
		if (configuration.geoCoord == null)
			throw new IllegalArgumentException("Missing geoCoord");
		if (configuration.bays == null)
			throw new IllegalArgumentException("Missing bays");
		if (configuration.serviceMillis == null)
			throw new IllegalArgumentException("Missing serviceMillis");
		pi.setGeoCoord(configuration.geoCoord);
		automationSystem.setServiceMillis(configuration.serviceMillis);
		final List<SimulativeBay> bays = automationSystem.getPayloadBays();
		bays.clear();
		automationSystem.getPayloadBays();
		int id = 1;
		for (final var bayConf : configuration.bays) {
			if (bayConf.payloadType == null)
				throw new IllegalArgumentException("Missing payloadType in: " + bayConf);
			if (bayConf.chargePerSecond == null)
				throw new IllegalArgumentException("Missing chargePerSecond in: " + bayConf);
			if (bayConf.prepareMillis == null)
				throw new IllegalArgumentException("Missing prepareMillis in: " + bayConf);
			if (bayConf.number == null)
				throw new IllegalArgumentException("Missing number in: " + bayConf);
			LOGGER.info("Creating: {} bays of type: {} charging rate: {}/s", bayConf.number, bayConf.payloadType,
					bayConf.chargePerSecond);
			for (int i = 0; i < bayConf.number; i++) {
				final var bay = new SimulativeBay(id++,
						Optional.of(new Payload(bayConf.payloadType, UUID.randomUUID().toString())),
						bayConf.chargePerSecond, bayConf.prepareMillis);
				bays.add(bay);
			}
		}
	}

	private Configuration loadConfigueration(File file) throws IOException {
		var mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
		return mapper.readValue(new FileReader(file), Configuration.class);
	}
}