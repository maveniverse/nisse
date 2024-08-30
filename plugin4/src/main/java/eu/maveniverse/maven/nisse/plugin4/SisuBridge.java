package eu.maveniverse.maven.nisse.plugin4;

import com.google.inject.Guice;
import eu.maveniverse.maven.nisse.core.NisseManager;
import javax.inject.Inject;
import javax.inject.Named;
import org.eclipse.sisu.launch.Main;
import org.eclipse.sisu.space.BeanScanning;

/**
 * Sisu bridge.
 */
@Named
public class SisuBridge {
    @Inject
    private NisseManager nisseManager;

    public static NisseManager boot() {
        return Guice.createInjector(Main.wire(BeanScanning.INDEX)).getInstance(SisuBridge.class).nisseManager;
    }
}
