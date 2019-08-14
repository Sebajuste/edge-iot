package io.edge.iot.service.mqtt;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.reactivex.Emitter;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.disposables.Disposable;
import io.reactivex.observables.GroupedObservable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttTopicSubscription;
import io.vertx.reactivex.ext.auth.User;

public class MqttBroker implements Handler<MqttEndpoint> {

	private static final String EDGE_IOT_MQTT = "$edge.iot.mqtt";

	private static final Logger LOGGER = LoggerFactory.getLogger(MqttBroker.class);

	private final Vertx vertx;

	private final String registry;

	private final String brokerBusAddress;

	/**
	 * Topic map, contains a submap with clientIdentifier as key
	 */
	private final Map<String, Map<String, Emitter<MqttMessage>>> emitterSetMap = new HashMap<>();

	private final Set<Emitter<MqttMessage>> publishEmitterSet = new HashSet<>();

	private MqttAuthProvider authProvider = null;

	private MqttBroker(Vertx vertx, JsonObject options) {
		super();
		this.vertx = Objects.requireNonNull(vertx);
		this.registry = Objects.requireNonNull(options).getString("registry", "default");
		this.brokerBusAddress = EDGE_IOT_MQTT + "." + this.registry;
	}

	private MqttBroker(Vertx vertx) {
		this(vertx, new JsonObject());
	}

	private MqttBroker init() {

		this.vertx.eventBus().<Buffer> consumer(this.brokerBusAddress, bufferMessage -> {
			LOGGER.info("Message receive from MQTT broker bus : " + bufferMessage.headers());

			String topic = bufferMessage.headers().get("topic");

			this.publish(topic, bufferMessage.body().getBytes());
		});

		return this;
	}

	private Map<String, Emitter<MqttMessage>> getOrCreateEmitterMap(String topic) {

		if (emitterSetMap.containsKey(topic)) {
			return emitterSetMap.get(topic);
		}

		Map<String, Emitter<MqttMessage>> map = new HashMap<>();

		LOGGER.info("Topic created " + topic);
		emitterSetMap.put(topic, map);
		return map;
	}

	private void clearTopicIfEmpty(String topic) {
		if (emitterSetMap.containsKey(topic) && emitterSetMap.get(topic).isEmpty()) {
			emitterSetMap.remove(topic);
			LOGGER.info("Topic removed " + topic);
		}
	}

	private void addEmitter(String clientIdentifier, String topic, ObservableEmitter<MqttMessage> emitter) {
		this.getOrCreateEmitterMap(topic).put(clientIdentifier, emitter);
	}

	private void removeEmitter(String clientIdentifier, String topic) {
		if (this.emitterSetMap.containsKey(topic)) {
			this.emitterSetMap.get(topic).remove(clientIdentifier);
			this.clearTopicIfEmpty(topic);
		}
	}

	/**
	 * Return an Observable where all publish packet are emitted.
	 * 
	 * @return
	 */
	public Observable<MqttMessage> publishObservable() {
		return Observable.create(emitter -> {
			publishEmitterSet.add(emitter);
			emitter.setCancellable(() -> publishEmitterSet.remove(emitter));
		});
	}

	private void auth(MqttEndpoint endpoint, Handler<AsyncResult<User>> resultHandler) {

		Future<User> future = Future.future();

		future.setHandler(resultHandler);

		if (authProvider != null) {

			JsonObject authInfo = new JsonObject() //
					.put("clientIdentifier", endpoint.clientIdentifier()); //

			if (endpoint.auth() != null) {
				authInfo.put("auth", new JsonObject().put("username", endpoint.auth().getUsername()).put("password", endpoint.auth().getPassword()));
			}

			LOGGER.info("Auth request : " + authInfo);

			authProvider.authenticate(authInfo, ar -> {

				if (ar.succeeded()) {

					User user = ar.result();

					// resultHandler.handle(Future.succeededFuture(user));
					future.complete(user);

				} else {
					future.fail("Bad username or password");
					// resultHandler.handle(Future.failedFuture("Bad username or
					// password"));
				}

			});

		} else {
			LOGGER.info("No auth Provider found");
			future.complete();
			// resultHandler.handle(Future.succeededFuture(null));
		}

	}

	private void acceptConnection(MqttEndpoint endpoint, User user) {

		final Map<String, Disposable> subscribeMap = new HashMap<>();

		endpoint.closeHandler(v -> {
			LOGGER.info("Connection closed");
		});

		/**
		 * Disconnect
		 */
		endpoint.disconnectHandler(v -> {

			LOGGER.info("Received disconnect from client");
			subscribeMap.forEach((topic, disposable) -> disposable.dispose());
			subscribeMap.clear();

		});

		/**
		 * Subscribe / Unsubscribe
		 */

		endpoint.subscribeHandler(subscribe -> {

			List<MqttQoS> grantedQosLevels = new ArrayList<>();
			for (MqttTopicSubscription s : subscribe.topicSubscriptions()) {
				LOGGER.info("Subscription for " + s.topicName() + " with QoS " + s.qualityOfService());
				grantedQosLevels.add(s.qualityOfService());

				if (!subscribeMap.containsKey(s.topicName())) {
					Disposable disposable = this.subscribe(endpoint.clientIdentifier(), s.topicName())//
							.subscribe(message -> {
								LOGGER.info("Send data to endpoint [topic: " + message.getTopic() + "]");
								endpoint.publish(message.getTopic(), message.getPayload(), MqttQoS.AT_MOST_ONCE, false, false);
							});
					subscribeMap.put(s.topicName(), disposable);
				}

			}
			// ack the subscriptions request
			endpoint.subscribeAcknowledge(subscribe.messageId(), grantedQosLevels);

		});

		endpoint.unsubscribeHandler(unsubscribe -> {

			for (String topic : unsubscribe.topics()) {
				LOGGER.info("Unsubscription for " + topic);

				if (subscribeMap.containsKey(topic)) {
					subscribeMap.get(topic).dispose();
					subscribeMap.remove(topic);
				}

			}
			// ack the subscriptions request
			endpoint.unsubscribeAcknowledge(unsubscribe.messageId());
		});

		/**
		 * Publish
		 */

		endpoint.publishHandler(message -> {

			LOGGER.info("Just received message [" + message.payload().toString(Charset.defaultCharset()) + "] on topic [" + message.topicName() + "] with QoS [" + message.qosLevel() + "]");

			try {

				if (message.qosLevel() == MqttQoS.AT_LEAST_ONCE) {
					endpoint.publishAcknowledge(message.messageId());
				} else if (message.qosLevel() == MqttQoS.EXACTLY_ONCE) {
					endpoint.publishRelease(message.messageId());
				}

				DeliveryOptions options = new DeliveryOptions();
				options.addHeader("registry", this.registry);
				options.addHeader("topic", message.topicName());

				this.vertx.eventBus().publish(this.brokerBusAddress, message.payload(), options);

				MqttMessage mqttMessage = new MqttMessage(this.registry, message.topicName(), message.payload());

				this.publishEmitterSet.forEach(emitter -> emitter.onNext(mqttMessage));

				LOGGER.info("Message published on mqtt brokers");

			} catch (Exception e) {
				LOGGER.error("ERROR", e);
			}

		}).publishReleaseHandler(messageId -> {

			endpoint.publishComplete(messageId);
		});

		LOGGER.info("Connection accepted");

		endpoint.accept(false);
	}

	public static MqttBroker create(Vertx vertx) {
		return new MqttBroker(vertx).init();
	}

	public static MqttBroker create(Vertx vertx, JsonObject options) {
		return new MqttBroker(vertx, options).init();
	}

	public MqttAuthProvider getAuthProvider() {
		return authProvider;
	}

	public void setAuthProvider(MqttAuthProvider authProvider) {
		this.authProvider = authProvider;
	}

	/**
	 * Close all resources
	 */
	public void close() {

		publishEmitterSet.forEach(Emitter::onComplete);
		publishEmitterSet.clear();

		emitterSetMap.values().forEach(map -> map.values().forEach(Emitter::onComplete));
		emitterSetMap.clear();

	}

	/**
	 * Return an Observable that subscribe to a topic.
	 * 
	 * @param topic
	 * @return
	 */
	public Observable<MqttMessage> subscribe(String clientIdentifier, String topic) {

		return Observable.create(emitter -> {
			this.addEmitter(clientIdentifier, topic, emitter);
			emitter.setCancellable(() -> this.removeEmitter(clientIdentifier, topic));
		});

	}

	/**
	 * Publish a payload on a topic. All Observables eligible will receive the
	 * packet.
	 * 
	 * @param topic
	 * @param data
	 */
	public void publish(String topic, byte[] data) {

		LOGGER.info("Publish to topic [" + topic + "]");

		MqttMessage message = new MqttMessage(this.registry, topic, Buffer.buffer(data));

		Observable.fromIterable(this.emitterSetMap.entrySet())// Take all
																// subscriber on
																// this local
																// node

				.filter(entry -> {
					MqttTopicFinder.Result matchResult = MqttTopicFinder.matchUri(topic, entry.getKey());
					return MqttTopicFinder.Result.MATCH.equals(matchResult) || MqttTopicFinder.Result.INTERMEDIATE.equals(matchResult);
				})// Keep only eligible topics

				.flatMap(entry -> Observable.fromIterable(entry.getValue().entrySet())) // Flat
																						// all
																						// emitters
																						// for
																						// the
																						// eligible
																						// topics

				.groupBy(Map.Entry::getKey)// Regroup for each Client (to avoid
											// send same data for the same
											// client)

				.flatMapSingle(GroupedObservable::toList)// Make a list for each
															// Client

				.filter(list -> !list.isEmpty())// Remove the Client when his
												// list is empty

				.map(list -> list.get(0)).map(Map.Entry::getValue)// Keep one
																	// emitter
																	// for each
																	// client

				.subscribe(emitter -> emitter.onNext(message)); // Send the
																// message for
																// each valid
																// emitter

	}

	@Override
	public void handle(MqttEndpoint endpoint) {

		LOGGER.info("MQTT client [identifier=" + endpoint.clientIdentifier() + "] request to connect, clean session = " + endpoint.isCleanSession());

		if (endpoint.auth() != null) {
			LOGGER.info("[username = " + endpoint.auth().getUsername() + ", password = " + endpoint.auth().getPassword() + "]");
		}
		if (endpoint.will() != null) {
			LOGGER.info("[Will = " + endpoint.will().toJson() + "]");
		}

		LOGGER.info("[keep alive timeout = " + endpoint.keepAliveTimeSeconds() + "]");

		this.auth(endpoint, ar -> {

			if (ar.succeeded()) {

				LOGGER.info("Auth result : " + ar.result());

				this.acceptConnection(endpoint, ar.result());

			} else {
				LOGGER.info("Reject connection");
				endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
			}

		});

	}

}
