package io.edge.iot.verticle;

import java.util.List;

import io.edge.iot.certificates.CertificateService;
import io.edge.iot.service.mqtt.MqttBrokerRegistry;
import io.edge.iot.service.mqtt.MqttMessage;
import io.edge.iot.service.mqtt.MqttTopicFinder;
import io.edge.iot.service.mqtt.PathPatternMqtt;
import io.edge.iot.service.remote.ShadowService;
import io.edge.utils.exchange.Exchange;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;

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

	private void onReceiveMqttMessage(MqttMessage message) {

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

	}

	private Future<Void> startMqtt(MqttServerOptions options) {

		Promise<Void> promise = Promise.promise();
		
		MqttServer mqttServer = MqttServer.create(vertx, options);

		brokerRegistry = MqttBrokerRegistry.create(vertx);

		mqttServer.endpointHandler(brokerRegistry).listen(listenResult -> {

			if (listenResult.succeeded()) {

				/**
				 * Resend MQTT message to other services
				 */

				brokerRegistry.subscribeMessage(this::onReceiveMqttMessage);

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

				promise.complete();
				
			} else {
				promise.fail(listenResult.cause());
			}

		});
		
		return promise.future();
	}

	public Future<JsonObject> getCertificateCA(String certName, boolean autoCreate) {
		
		CertificateService certificateService = CertificateService.create(vertx);
		
		return certificateService.getServerCertificate("default", certName, false)//
		.flatMap(certificate -> {
			
			if (certificate == null && autoCreate) {
				return certificateService.createCertificate("default", certName, true);
			} else {
				return Future.succeededFuture(certificate);
			}
			
		});
		

		
	}
	
	@Deprecated
	public Future<JsonObject> getCertificate(String certName, String caCertName, boolean autoCreate) {

		
		return this.getCertificateCA(caCertName, autoCreate)//
				.flatMap( certificateCA -> {
			

				LOGGER.info("certificateCA: " + certificateCA);
				
				CertificateService certificateService = CertificateService.create(vertx);

				return certificateService.getServerCertificate("default", certName, true).flatMap(certificate -> {

						LOGGER.info(certificate);
						
						if (certificate == null && autoCreate) {
							return certificateService.createSignedCertificate("default", certName, caCertName);
						} else {
							return Future.succeededFuture(certificate);
						}


				});
				

		});

	}
	
	public Future<JsonObject> getChainCertificate(String certName, String caCertName, boolean autoCreate) {
		
		return this.getCertificateCA(caCertName, autoCreate)//
			.flatMap( certificateCA -> {
			
				
				CertificateService certificateService = CertificateService.create(vertx);

				return certificateService.getServerCertificate("default", certName, true)//
					.flatMap( certificate -> {

	
						if (certificate == null && autoCreate) {
							return certificateService.createSignedCertificate("default", certName, caCertName)//
								.map(serverCertificate -> {
								

									JsonObject chainCertificate = new JsonObject()//
											.put("private", serverCertificate.getJsonObject("keys").getString("private") )
											.put("certificate", serverCertificate.getString("certificate") )//
											.put("caCertificate", certificateCA.getString("certificate"));
									
									// future.complete(chainCertificate);
									
									return chainCertificate;

							});
						} else {

							JsonObject chainCertificate = new JsonObject()//
									.put("private", certificate.getJsonObject("keys").getString("private") )
									.put("certificate", certificate.getString("certificate") )//
									.put("caCertificate", certificateCA.getString("certificate"));
							
							// future.complete(chainCertificate);
							
							return Future.succeededFuture(chainCertificate);
						}

				});
				
			});
		
	}

	@Override
	public void start(Promise<Void> startPromise) {

		final MqttServerOptions options = new MqttServerOptions();
		
		if( config().getBoolean("mqtt.websocket", false)) {
			options.setUseWebSocket(true);
		}
		
		Handler<AsyncResult<Void>> resultHandler = ar -> {
			
			if( ar.succeeded()) {
				startPromise.complete();
			} else {
				LOGGER.error("Cannot get certificate : " + ar.cause().getMessage());
				startPromise.fail("Invalid certificate");
			}
			
		};
		
		if (config().getBoolean("mqtt.tls.enable", false)) {

			String certname = config().getString("mqtt.tls.cert_name", "mqtt-server");
			
			this.getChainCertificate(certname, "ca-mqtt-server", true)//
				.flatMap( chainCertificate -> {

					LOGGER.info("chain certificate : " + chainCertificate);
					
					Buffer keyValue = Buffer.buffer( chainCertificate.getString("private") );
					Buffer certValue = Buffer.buffer( chainCertificate.getString("certificate") );
					Buffer caCertValue = Buffer.buffer( chainCertificate.getString("caCertificate") );
					
					ClientAuth clientAuth = ClientAuth.valueOf( config().getString("mqtt.tls.auth", "NONE") );
					
					options.setPort(8883)//
						.setSsl(true)//
						.setClientAuth( clientAuth )//
						
						.setTrustOptions(new PemTrustOptions()
							.addCertValue(caCertValue)
						)//

						.setKeyCertOptions(new PemKeyCertOptions()//
							.setKeyValue(keyValue)//
							.setCertValue(certValue)//
						);
					
					return this.startMqtt(options);

				}).onComplete(resultHandler);


		} else {
			this.startMqtt(options).onComplete(resultHandler);
		}

	}

	@Override
	public void stop() throws Exception {
		super.stop();
		brokerRegistry.close();
	}

}
