package io.edge.iot.verticle;

import java.util.List;

import io.edge.iot.service.mqtt.MqttBrokerRegistry;
import io.edge.iot.service.mqtt.MqttTopicFinder;
import io.edge.iot.service.mqtt.PathPatternMqtt;
import io.edge.iot.service.remote.ShadowService;
import io.edge.utils.exchange.Exchange;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.mqtt.MqttServer;

/**
 * Mqtt Server & Broker embedded
 * 
 * @author smartinez
 *
 */
public class MqttVerticle extends AbstractVerticle {

	private static final Logger LOGGER = LoggerFactory.getLogger(MqttVerticle.class);

	public static final String EDGE_IOT_MQTT = "$edge.iot.mqtt";

	private MqttBrokerRegistry brokerRegistry;

	@Override
	public void start() {

		MqttServer mqttServer = MqttServer.create(vertx);

		brokerRegistry = MqttBrokerRegistry.create(vertx);

		mqttServer.endpointHandler(brokerRegistry);

		mqttServer.listen(listenResult -> {

			if (listenResult.succeeded()) {

				/**
				 * Resend MQTT message to other services
				 */

				brokerRegistry.subscribeMessage(message -> {

					try {

						String topic = message.getTopic();

						if (MqttTopicFinder.Result.MATCH.equals(MqttTopicFinder.matchUri(topic, "$edge/things/+/shadow/update"))) {

							List<String> params = PathPatternMqtt.create("$edge/things/+/shadow/update").matcherList(topic);

							String thingName = params.get(0);

							DeliveryOptions options = new DeliveryOptions();
							options.addHeader("registry", message.getRegistry());
							options.addHeader("topic", topic);
							options.addHeader("thingName", thingName);

							Exchange.exchangeFanout(vertx, EDGE_IOT_MQTT).publish(message.getPayload(), options);
						}

					} catch (Exception e) {
						LOGGER.error(e);
					}

				});

				/**
				 * Bridge Shadow to MQTT
				 */

				Exchange.exchangeFanout(vertx, ShadowService.EDGE_IOT_SHADOW_UPDATE_RESULT).start().<JsonObject> consumer("$edge.iot.mqtt.shadow-update.result", message -> {

					String registry = message.headers().get("registry");

					String thingName = message.headers().get("thingName");

					String action = message.headers().get("action");

					if ("accepted".equalsIgnoreCase(action)) {
						brokerRegistry.publish(registry, "$edge/things/" + thingName + "/shadow/update/accepted", message.body().encode().getBytes());
					}

					if ("rejected".equalsIgnoreCase(action)) {
						brokerRegistry.publish(registry, "$edge/things/" + thingName + "/shadow/update/rejected", message.body().encode().getBytes());
					}

				});

				LOGGER.info("Mqtt Server started on port " + listenResult.result().actualPort());

			}

		});

	}

	@Override
	public void stop() throws Exception {
		super.stop();
		brokerRegistry.close();
	}

}
