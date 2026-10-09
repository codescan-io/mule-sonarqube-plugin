package com.mulesoft.services.tools.sonarqube.sensor;

import java.util.Locale;

import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.MessageException;

import com.mulesoft.services.tools.sonarqube.language.MuleLanguage;

/**
 * Fails the scan when ".xml" is configured both here and in the AutoRABIT Mule plugin. Only one of the two
 * plugins may own the Mule configuration files, whichever quality profile is selected.
 *
 * Deliberately not restricted to a language or rule repository so that it runs on every scan.
 */
public class MuleFileSuffixesSensor implements Sensor {

	public static final String AUTORABIT_FILE_SUFFIXES_KEY = "sonar.armule.file.suffixes";

	private static final String XML_SUFFIX = ".xml";

	@Override
	public void describe(SensorDescriptor descriptor) {
		descriptor.name("Check Mule file suffixes do not conflict");
	}

	@Override
	public void execute(SensorContext context) {
		validateSuffixesDoNotConflict(context.config());
	}

	static void validateSuffixesDoNotConflict(Configuration config) {
		// Same suffixes the language indexes, including its fallback to .xml when the setting is empty
		String[] legacySuffixes = new MuleLanguage(config).getFileSuffixes();

		if (containsXmlSuffix(legacySuffixes) && containsXmlSuffix(config.getStringArray(AUTORABIT_FILE_SUFFIXES_KEY))) {
			throw MessageException.of("Conflicting Mule file suffixes: '" + XML_SUFFIX + "' is configured in both "
					+ MuleLanguage.FILE_SUFFIXES_KEY + " (Legacy File Suffixes) and " + AUTORABIT_FILE_SUFFIXES_KEY
					+ " (AutoRABIT Mule File Suffixes). Keep '" + XML_SUFFIX
					+ "' in only one of them, matching the selected quality profile: " + MuleLanguage.FILE_SUFFIXES_KEY
					+ " for the MuleSoft Rules profiles, or " + AUTORABIT_FILE_SUFFIXES_KEY
					+ " for the AutoRABIT Mule profile. To stop the legacy plugin from claiming '" + XML_SUFFIX + "', set "
					+ MuleLanguage.FILE_SUFFIXES_KEY + " to another suffix, as an empty value defaults to '"
					+ MuleLanguage.FILE_SUFFIXES_DEFAULT_VALUE + "'.");
		}
	}

	// Match ".xml" ignoring case and a missing leading dot, since MuleFilePredicate matches suffixes with endsWith
	private static boolean containsXmlSuffix(String[] suffixes) {
		for (String suffix : suffixes) {
			String normalised = suffix.trim().toLowerCase(Locale.ROOT);
			if (!normalised.startsWith(".")) {
				normalised = "." + normalised;
			}
			if (XML_SUFFIX.equals(normalised)) {
				return true;
			}
		}
		return false;
	}
}
