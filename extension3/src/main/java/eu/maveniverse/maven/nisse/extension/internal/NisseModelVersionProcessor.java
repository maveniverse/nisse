/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.extension.internal;

import java.util.Properties;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.interpolation.ModelVersionProcessor;
import org.eclipse.sisu.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Named
@Priority(200)
class NisseModelVersionProcessor implements ModelVersionProcessor {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public boolean isValidProperty(String property) {
        // return true if property is coming from any source
        // put them into "to be rewritten" properties
        logger.info("isValidProperty");
        return false;
    }

    @Override
    public void overwriteModelProperties(Properties modelProperties, ModelBuildingRequest request) {
        logger.info("overwriteModelProperties");
    }
}
