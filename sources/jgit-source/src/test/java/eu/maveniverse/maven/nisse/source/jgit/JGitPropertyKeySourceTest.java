package eu.maveniverse.maven.nisse.source.jgit;

import java.util.Collections;
import org.junit.jupiter.api.Test;

public class JGitPropertyKeySourceTest {
    @Test
    void smoke() {
        new JGitPropertyKeySource()
                .providedKeys(Collections.emptyMap())
                .forEach(k -> System.out.println(k + " = " + k.getValue().orElse("")));
    }
}
