/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.karavan.service;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.Startup;
import io.quarkus.runtime.StartupEvent;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.camel.karavan.docker.DockerAPI;
import org.apache.camel.karavan.kubernetes.KubernetesAPI;
import org.apache.camel.karavan.project.KaravanProjectsCache;
import org.apache.camel.karavan.project.ProjectService;
import org.apache.camel.karavan.status.ConfigService;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.jboss.logging.Logger;

import java.io.IOException;

@Startup
@Liveness
@Singleton
public class KaravanService implements HealthCheck {

    private static final Logger LOGGER = Logger.getLogger(KaravanService.class.getName());

    @Inject
    KubernetesAPI kubernetesAPI;

    @Inject
    DockerAPI dockerAPI;

    @Inject
    EventBus eventBus;

    @Inject
    ProjectService projectService;

    @Inject
    KaravanProjectsCache karavanProjectsCache;

    public static final String KARAVAN_STARTED = "KARAVAN_STARTED";

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.up("Karavan");
    }

    void onStart(@Observes StartupEvent ev) throws Exception {
        if (!ConfigService.inKubernetes() && !dockerAPI.checkDocker()){
            Quarkus.asyncExit();
        }
        LOGGER.info("Starting Karavan services");
        projectService.tryStart();
        eventBus.publish(KARAVAN_STARTED, null);
        if (!ConfigService.inKubernetes()) {
            dockerAPI.startListeners();
        }
    }

    void onStop(@Observes ShutdownEvent ev) throws IOException  {
        LOGGER.info("Stopping Listeners");
        if (!ConfigService.inKubernetes()) {
            dockerAPI.stopListeners();
        }
        LOGGER.info("Karavan stopped");
    }
}
