package eu.maveniverse.maven.nisse.source.jgit;

import org.junit.jupiter.api.Test;

public class JGitPropertyKeySourceTest {
    @Test
    void smoke() {
        new JGitPropertyKeySource()
                .providedKeys()
                .forEach(k -> System.out.println(k + " = " + k.getValue().orElse("")));
    }
}
