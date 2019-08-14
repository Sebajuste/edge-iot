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

	private void startMqtt(Future<Void> startFuture, MqttServerOptions options) {

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

				startFuture.complete();
				
			} else {
				startFuture.fail(listenResult.cause());
			}

		});
	}

	public void getCertificateCA(String certName, boolean autoCreate, Handler<AsyncResult<JsonObject>> resultHandler) {
		
		Future<JsonObject> resultFuture = Future.future();

		CertificateService certificateService = CertificateService.create(vertx);

		certificateService.getServerCertificate("default", certName, false, ar -> {

			if (ar.succeeded()) {

				JsonObject certificate = ar.result();

				LOGGER.info(certificate);
				
				if (certificate == null && autoCreate) {
					certificateService.createCertificate("default", certName, true, resultFuture);
				} else {
					resultFuture.complete(certificate);
				}

			} else {
				LOGGER.error("Cannot find CA certificate", ar.cause());
				resultFuture.fail(ar.cause());
			}

		});

		resultFuture.setHandler(resultHandler);
		
	}
	
	@Deprecated
	public void getCertificate(String certName, String caCertName, boolean autoCreate, Handler<AsyncResult<JsonObject>> resultHandler) {

		Future<JsonObject> future = Future.future();
		
		future.setHandler(resultHandler);
		
		this.getCertificateCA(caCertName, autoCreate, arCA -> {
			
			if( arCA.succeeded()) {
				
				JsonObject certificateCA = arCA.result();
				
				LOGGER.info("certificateCA: " + certificateCA);
				

				CertificateService certificateService = CertificateService.create(vertx);

				certificateService.getServerCertificate("default", certName, true, ar -> {

					if (ar.succeeded()) {

						JsonObject certificate = ar.result();

						LOGGER.info(certificate);
						
						if (certificate == null && autoCreate) {
							certificateService.createSignedCertificate("default", certName, caCertName, future);
						} else {
							future.complete(certificate);
						}

					} else {
						LOGGER.error("Error to get certificate " + certName, ar.cause());
						future.fail(ar.cause());
					}

				});
				
			} else {
				LOGGER.error("Cannot find CA certificate", arCA.cause());
				future.fail(arCA.cause());
			}
			
		});

	}
	
	public void getChainCertificate(String certName, String caCertName, boolean autoCreate, Handler<AsyncResult<JsonObject>> resultHandler) {
		
		Future<JsonObject> future = Future.future();
		
		future.setHandler(resultHandler);
		
		this.getCertificateCA(caCertName, autoCreate, arCA -> {
			
			if( arCA.succeeded()) {
				
				JsonObject certificateCA = arCA.result();
				
				CertificateService certificateService = CertificateService.create(vertx);

				certificateService.getServerCertificate("default", certName, true, ar -> {

					if (ar.succeeded()) {

						JsonObject certificate = ar.result();
	
						if (certificate == null && autoCreate) {
							certificateService.createSignedCertificate("default", certName, caCertName, createCertAr -> {
								
								if( createCertAr.succeeded() ) {
									
									JsonObject serverCertificate = createCertAr.result();
									
									JsonObject chainCertificate = new JsonObject()//
											.put("private", serverCertificate.getJsonObject("keys").getString("private") )
											.put("certificate", serverCertificate.getString("certificate") )//
											.put("caCertificate", certificateCA.getString("certificate"));
									
									future.complete(chainCertificate);
									
								} else {
									future.fail(createCertAr.cause());
								}
								
							});
						} else {

							JsonObject chainCertificate = new JsonObject()//
									.put("private", certificate.getJsonObject("keys").getString("private") )
									.put("certificate", certificate.getString("certificate") )//
									.put("caCertificate", certificateCA.getString("certificate"));
							
							future.complete(chainCertificate);
						}

					} else {
						LOGGER.error("Error to get certificate " + certName, ar.cause());
						future.fail(ar.cause());
					}

				});
				
			} else {
				LOGGER.error("Cannot find CA certificate", arCA.cause());
				future.fail(arCA.cause());
			}
			
		});
		
	}

	@Override
	public void start(Future<Void> startFuture) {

		if (config().getBoolean("mqtt.tls.enable", false)) {

			String certname = config().getString("mqtt.tls.cert_name", "mqtt-server");
			
			this.getChainCertificate(certname, "ca-mqtt-server", true, ar -> {

				if (ar.succeeded()) {
					JsonObject chainCertificate = ar.result();
					
					LOGGER.info("chain certificate : " + chainCertificate);
					
					Buffer keyValue = Buffer.buffer( chainCertificate.getString("private") );
					Buffer certValue = Buffer.buffer( chainCertificate.getString("certificate") );
					Buffer caCertValue = Buffer.buffer( chainCertificate.getString("caCertificate") );
					
					MqttServerOptions options = new MqttServerOptions();
					
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
					
					this.startMqtt(startFuture, options);
					
				} else {
					LOGGER.error("Cannot get certificate : " + ar.cause().getMessage());
					startFuture.fail("Invalid certificate");
				}

			});

			
			;
			/*
			 * .setClientAuth(ClientAuth.REQUIRED)// .setTrustStoreOptions(new
			 * JksOptions().setv)
			 */

			new JksOptions();

		} else {
			MqttServerOptions options = new MqttServerOptions();
			this.startMqtt(startFuture, options);
		}

	}

	@Override
	public void stop() throws Exception {
		super.stop();
		brokerRegistry.close();
	}

}
