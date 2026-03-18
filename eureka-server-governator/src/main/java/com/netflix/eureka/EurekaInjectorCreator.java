package com.netflix.eureka;

import com.google.inject.servlet.ServletModule;
import com.netflix.discovery.guice.EurekaModule;
import com.netflix.eureka.guice.Ec2EurekaServerModule;
import com.netflix.governator.InjectorBuilder;
import com.netflix.governator.LifecycleInjector;
import com.netflix.governator.ProvisionDebugModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author David Liu
 */
public class EurekaInjectorCreator {
    private static final Logger logger = LoggerFactory.getLogger(EurekaInjectorCreator.class);

    public static LifecycleInjector createInjector() {
        try {
            return InjectorBuilder
                    .fromModules(
                            new EurekaModule(),
                            new Ec2EurekaServerModule(),
                            new ProvisionDebugModule(),
                            new ServletModule() {
                                @Override
                                protected void configureServlets() {
                                    filter("/*").through(StatusFilter.class);
                                    filter("/*").through(ServerRequestAuthFilter.class);
                                    filter("/v2/apps", "/v2/apps/*").through(GzipEncodingEnforcingFilter.class);
                                    //filter("/*").through(RateLimitingFilter.class);  // enable if needed
                                }
                            }
                    )
                    .createInjector();
        } catch (Exception e) {
            logger.error("Failed to create the injector", e);
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
