package io.edge.iot.verticle;

import java.util.ArrayList;
import java.util.List;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public class LauncherVerticle extends AbstractVerticle {

	private void deployVerticles(JsonObject config, Handler<AsyncResult<Void>> handlerResult) {

		@SuppressWarnings("rawtypes")
		List<Future> deployFutureList = new ArrayList<>();

		/**
		 * Measures Deployment
		 */

		if (config.getBoolean("measures.enable", true)) {
			DeploymentOptions measuresDeployOptions = new DeploymentOptions();
			measuresDeployOptions.setConfig(config);

			Future<String> measuresDeployFuture = Future.future();
			this.vertx.deployVerticle(TimeSeriesVerticle.class.getName(), measuresDeployOptions, measuresDeployFuture);

			deployFutureList.add(measuresDeployFuture);
		}

		/**
		 * Shadow Deployment
		 */

		if (config.getBoolean("shadow.enable", true)) {
			DeploymentOptions shadowDeployOptions = new DeploymentOptions();
			shadowDeployOptions.setConfig(config);

			Future<String> shadowDeployFuture = Future.future();
			this.vertx.deployVerticle(ShadowVerticle.class.getName(), shadowDeployOptions, shadowDeployFuture);

			deployFutureList.add(shadowDeployFuture);
		}

		/**
		 * MQTT Deployment
		 */

		if (config.getBoolean("mqtt.enable", true)) {

			DeploymentOptions mqttOptions = new DeploymentOptions();

			// mqttOptions.setInstances(Runtime.getRuntime().availableProcessors());
			mqttOptions.setConfig(config);

			Future<String> mqttDeployFuture = Future.future();
			this.vertx.deployVerticle(MqttVerticle.class.getName(), mqttOptions, mqttDeployFuture);

			deployFutureList.add(mqttDeployFuture);
		}

		/**
		 * Deployment Result
		 */

		CompositeFuture.all(deployFutureList).setHandler(deployResult -> {
			if (deployResult.succeeded()) {
				handlerResult.handle(Future.succeededFuture());
			} else {
				handlerResult.handle(Future.failedFuture(deployResult.cause()));
			}
		});

	}

	@Override
	public void start(Future<Void> startFuture) {

		ConfigRetriever.create(vertx).getConfig(ar -> {

			if (ar.succeeded()) {

				this.deployVerticles(ar.result(), deployResult -> {

					if (deployResult.succeeded()) {
						startFuture.complete();
					} else {
						startFuture.fail(deployResult.cause());
					}

				});

			} else {
				startFuture.fail(ar.cause());

			}

		});

	}

}
