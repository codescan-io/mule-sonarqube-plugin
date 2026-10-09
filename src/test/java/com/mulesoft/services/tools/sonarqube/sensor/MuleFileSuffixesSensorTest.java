package com.mulesoft.services.tools.sonarqube.sensor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;

import org.junit.Test;
import org.sonar.api.batch.rule.internal.ActiveRulesBuilder;
import org.sonar.api.batch.rule.internal.NewActiveRule;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.MessageException;

import com.mulesoft.services.tools.sonarqube.language.MuleLanguage;

public class MuleFileSuffixesSensorTest {

	/**
	 * ".xml" may belong to only one of the two Mule plugins. With both claiming it the scan must fail even
	 * when the selected profile is a MuleSoft one, i.e. when there would be no clash yet.
	 */
	@Test
	public void testExecuteFailsWhenXmlSuffixIsConfiguredForBothPlugins() {
		MapSettings settings = new MapSettings();
		settings.setProperty(MuleLanguage.FILE_SUFFIXES_KEY, ".xml");
		settings.setProperty(MuleFileSuffixesSensor.AUTORABIT_FILE_SUFFIXES_KEY, ".xml,.properties");

		SensorContextTester context = SensorContextTester.create(new File("."));
		context.setSettings(settings);
		context.setActiveRules(new ActiveRulesBuilder()
				.addRule(new NewActiveRule.Builder().setRuleKey(RuleKey.of("mule4-repository", "rule-1")).build())
				.build());

		try {
			new MuleFileSuffixesSensor().execute(context);
			fail("Expected the scan to fail on conflicting Mule file suffixes");
		} catch (MessageException e) {
			assertTrue(e.getMessage(), e.getMessage().startsWith("Conflicting Mule file suffixes"));
		}
	}

	@Test
	public void testSuffixConflictDetection() {
		// .xml with the legacy plugin only: the out-of-the-box defaults.
		assertSuffixConflict(".xml", ".properties,.arxml,.arjson", false);
		// AutoRABIT plugin not installed, so its key has no value.
		assertSuffixConflict(".xml", null, false);
		// .xml with the AutoRABIT plugin only.
		assertSuffixConflict(".txml", ".xml,.properties", false);
		// .xml in both, including spellings MuleFilePredicate's endsWith match accepts.
		assertSuffixConflict(".xml", ".xml,.properties", true);
		assertSuffixConflict("xml", ".XML", true);
		// An empty value falls back to .xml in MuleLanguage.
		assertSuffixConflict("", ".xml", true);
		assertSuffixConflict("", ".properties", false);
	}

	private static void assertSuffixConflict(String legacySuffixes, String autoRabitSuffixes, boolean expectConflict) {
		MapSettings settings = new MapSettings();
		settings.setProperty(MuleLanguage.FILE_SUFFIXES_KEY, legacySuffixes);
		if (autoRabitSuffixes != null) {
			settings.setProperty(MuleFileSuffixesSensor.AUTORABIT_FILE_SUFFIXES_KEY, autoRabitSuffixes);
		}

		boolean conflict = false;
		try {
			MuleFileSuffixesSensor.validateSuffixesDoNotConflict(settings.asConfig());
		} catch (MessageException e) {
			conflict = true;
		}
		assertEquals("legacy=" + legacySuffixes + " autorabit=" + autoRabitSuffixes, expectConflict, conflict);
	}
}
