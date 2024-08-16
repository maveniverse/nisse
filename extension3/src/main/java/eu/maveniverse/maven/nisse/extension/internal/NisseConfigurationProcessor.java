package eu.maveniverse.maven.nisse.extension.internal;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.cli.CliRequest;
import org.apache.maven.cli.configuration.ConfigurationProcessor;
import org.apache.maven.cli.configuration.SettingsXmlConfigurationProcessor;

@Singleton
@Named
public class NisseConfigurationProcessor implements ConfigurationProcessor {
    private final SettingsXmlConfigurationProcessor settingsXmlConfigurationProcessor;

    @Inject
    public NisseConfigurationProcessor(SettingsXmlConfigurationProcessor settingsXmlConfigurationProcessor) {
        this.settingsXmlConfigurationProcessor = settingsXmlConfigurationProcessor;
    }

    @Override
    public void process(CliRequest request) throws Exception {
        settingsXmlConfigurationProcessor.process(request);

        // push what is needed
    }
}
