package eu.maveniverse.maven.nisse.extension4.internal;

import eu.maveniverse.maven.nisse.core.NisseConfiguration;
import eu.maveniverse.maven.nisse.core.internal.SimpleNisseConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.maven.SessionScoped;
import org.apache.maven.api.Session;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.di.Typed;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.services.Source;
import org.apache.maven.api.services.xml.XmlReaderRequest;
import org.apache.maven.api.spi.ModelParser;
import org.apache.maven.api.spi.ModelParserException;
import org.apache.maven.internal.impl.DefaultModelXmlFactory;
import org.apache.maven.model.v4.MavenTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SessionScoped
@Named
@Typed
public class NisseModelParser implements ModelParser {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final NisseConfiguration configuration;

    @Inject
    public NisseModelParser(Session session) {
        this.configuration = SimpleNisseConfiguration.builder()
                .withSystemProperties(session.getSystemProperties())
                .withUserProperties(session.getUserProperties())
                .withCurrentWorkingDirectory(session.getTopDirectory())
                .build();
    }

    @Override
    public Optional<Source> locate(Path path) {
        Path pom = path.resolve("pom.xml");
        if (Files.exists(pom)) {
            return Optional.of(Source.fromPath(pom));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Model parse(Source source, Map<String, ?> map) throws ModelParserException {
        try {
            DefaultModelXmlFactory factory = new DefaultModelXmlFactory();
            XmlReaderRequest.XmlReaderRequestBuilder builder = XmlReaderRequest.builder();
            builder.location(source.getLocation());
            if (source.getPath() != null) {
                builder.path(source.getPath());
            } else {
                builder.inputStream(source.openStream());
            }
            Model model = factory.read(builder.build());
            String id = model.getId();
            return new MavenTransformer(v -> transform(id, v)).visit(model);
        } catch (IOException e) {
            throw new ModelParserException("Unable to parse source " + source, e);
        }
    }

    private String transform(String modelId, String value) {
        final AtomicBoolean logBarrier = new AtomicBoolean(false);
        if (value != null) {
            while (true) {
                int idxStart = value.indexOf("${" + NisseConfiguration.PROPERTY_PREFIX);
                if (idxStart >= 0) {
                    int idxEnd = value.indexOf("}", idxStart);
                    if (idxEnd >= 0) {
                        String key = value.substring(idxStart + 2, idxEnd);
                        String replacement = configuration.getConfiguration().get(key);
                        if (replacement != null) {
                            if (logBarrier.compareAndSet(false, true)) {
                                logger.info("Inlining {}", modelId);
                            }
                            logger.info(" * ${{}}={}", key, replacement);
                            value = value.substring(0, idxStart) + replacement + value.substring(idxEnd + 1);
                            continue;
                        } else {
                            throw new ModelParserException("Unable to find a value for property " + key);
                        }
                    }
                }
                break;
            }
        }
        return value;
    }
}
