package eu.maveniverse.maven.nisse.extension.internal;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.interpolation.ModelInterpolator;
import org.apache.maven.model.interpolation.StringVisitorModelInterpolator;
import org.codehaus.plexus.interpolation.ValueSource;

public class NisseModelInterpolator implements ModelInterpolator {
    private final StringVisitorModelInterpolator interpolator = new StringVisitorModelInterpolator() {
        @Override
        protected List<ValueSource> createValueSources(
                Model model, File projectDir, ModelBuildingRequest config, ModelProblemCollector problems) {
            ArrayList<ValueSource> result =
                    new ArrayList<>(super.createValueSources(model, projectDir, config, problems));
            return result;
        }
    };

    @Override
    public Model interpolateModel(
            Model model, File projectDir, ModelBuildingRequest request, ModelProblemCollector problems) {
        return interpolator.interpolateModel(model, projectDir, request, problems);
    }
}
