package io.edge.iot.verticle;

import java.util.ArrayList;
import java.util.List;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

public class LauncherVerticle extends AbstractVerticle {

	private Future<Void> deployVerticles(JsonObject config) {

		@SuppressWarnings("rawtypes")
		List<Future> deployFutureList = new ArrayList<>();

		/**
		 * Measures Deployment
		 */

		if (config.getBoolean("measures.enable", true)) {
			DeploymentOptions measuresDeployOptions = new DeploymentOptions();
			measuresDeployOptions.setConfig(config);

			Promise<String> measuresDeployPromise = Promise.promise();
			
			this.vertx.deployVerticle(TimeSeriesVerticle.class.getName(), measuresDeployOptions, measuresDeployPromise);

			deployFutureList.add(measuresDeployPromise.future());
		}

		/**
		 * Shadow Deployment
		 */

		if (config.getBoolean("shadow.enable", true)) {
			DeploymentOptions shadowDeployOptions = new DeploymentOptions();
			shadowDeployOptions.setConfig(config);

			Promise<String> shadowDeployPromise = Promise.promise();
			this.vertx.deployVerticle(ShadowVerticle.class.getName(), shadowDeployOptions, shadowDeployPromise);

			deployFutureList.add(shadowDeployPromise.future());
		}

		/**
		 * MQTT Deployment
		 */

		if (config.getBoolean("mqtt.enable", true)) {

			DeploymentOptions mqttOptions = new DeploymentOptions();

			// mqttOptions.setInstances(Runtime.getRuntime().availableProcessors());
			mqttOptions.setConfig(config);

			Promise<String> mqttDeployPromise = Promise.promise();
			this.vertx.deployVerticle(MqttVerticle.class.getName(), mqttOptions, mqttDeployPromise);

			deployFutureList.add(mqttDeployPromise.future());
		}

		/**
		 * Deployment Result
		 */

		return CompositeFuture.all(deployFutureList).map(compositeFuture -> null );

	}

	@Override
	public void start(Promise<Void> startPromise) {

		ConfigRetriever.create(vertx).getConfig(ar -> {

			if (ar.succeeded()) {

				this.deployVerticles(ar.result()).onComplete(startPromise);

			} else {
				startPromise.fail(ar.cause());

			}

		});

	}

}
