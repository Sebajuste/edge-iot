package io.edge.iot.verticle;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public class LauncherVerticle extends AbstractVerticle {

	private void readConfig(Handler<AsyncResult<JsonObject>> resultHandler) {

		InputStream inputStream = this.getClass().getResourceAsStream("/default-config.json");

		JsonObject defaultConfig = new JsonObject(new BufferedReader(new InputStreamReader(inputStream)).lines().collect(Collectors.joining("\n")));

		ConfigStoreOptions defaultStore = new ConfigStoreOptions().setType("json").setConfig(defaultConfig);

		ConfigStoreOptions envStore = new ConfigStoreOptions().setType("env");

		ConfigStoreOptions sysStore = new ConfigStoreOptions().setType("sys");

		ConfigRetrieverOptions options = new ConfigRetrieverOptions().addStore(defaultStore).addStore(envStore).addStore(sysStore);

		ConfigRetriever retriever = ConfigRetriever.create(vertx, options);

		retriever.getConfig(resultHandler);

	}

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

		this.readConfig(configResult -> {

			if (configResult.succeeded()) {

				this.deployVerticles(configResult.result(), deployResult -> {

					if (deployResult.succeeded()) {
						startFuture.complete();
					} else {
						startFuture.fail(deployResult.cause());
					}

				});

			} else {
				startFuture.fail(configResult.cause());

			}

		});

	}

}
