package eu.maveniverse.maven.nisse.extension4.internal;

import static java.util.Objects.requireNonNull;

import eu.maveniverse.maven.nisse.core.NisseConfiguration;
import eu.maveniverse.maven.nisse.core.internal.SimpleNisseConfiguration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.maven.SessionScoped;
import org.apache.maven.api.Event;
import org.apache.maven.api.Listener;
import org.apache.maven.api.Session;
import org.apache.maven.api.SessionData;
import org.apache.maven.api.di.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SessionScoped
@Named
class NisseListener implements Listener {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    static final SessionData.Key<InlinedKeys> INLINED_KEYS = SessionData.key(InlinedKeys.class);

    static class InlinedKeys {
        private final Set<String> keys = ConcurrentHashMap.newKeySet();

        boolean addKey(String key) {
            requireNonNull(key, "key");
            return keys.add(key);
        }

        Set<String> getKeys() {
            return keys;
        }
    }

    @Override
    public void onEvent(Event event) {
        switch (event.getType()) {
            case SESSION_STARTED: {
                Session session = event.getSession();
                NisseConfiguration configuration = SimpleNisseConfiguration.builder()
                        .withSystemProperties(session.getSystemProperties())
                        .withUserProperties(session.getUserProperties())
                        .withCurrentWorkingDirectory(session.getTopDirectory())
                        .build();
                InlinedKeys inlinedKeys = session.getData().computeIfAbsent(INLINED_KEYS, InlinedKeys::new);
                for (String inlinedKey : configuration.getInlinedPropertyKeys()) {
                    if (inlinedKeys.addKey(inlinedKey)) {
                        logger.info("Nisse property {} configured for inlining", inlinedKey);
                    }
                }
            }
            case SESSION_ENDED: {
            }
        }
    }
}
