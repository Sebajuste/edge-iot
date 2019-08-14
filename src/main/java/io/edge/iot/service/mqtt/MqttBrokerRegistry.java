package io.edge.iot.service.mqtt;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.edge.iot.service.identity.IdentityService;
import io.edge.iot.service.identity.impl.DefaultIdentityServiceImpl;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.reactivex.Emitter;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.mqtt.MqttEndpoint;

public class MqttBrokerRegistry implements Handler<MqttEndpoint> {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(MqttBrokerRegistry.class);

	private final Map<String, MqttBroker> brokerRegistry = new HashMap<>();

	private final Set<Emitter<MqttBroker>> brokerEmitterSet = new HashSet<>();

	private final Vertx vertx;

	private IdentityService identityService = new DefaultIdentityServiceImpl();

	private MqttBrokerRegistry(Vertx vertx) {
		super();
		this.vertx = vertx;
	}

	private MqttBroker getOrCreateBroker(String registry) {

		if (brokerRegistry.containsKey(registry)) {
			return brokerRegistry.get(registry);
		}

		JsonObject options = new JsonObject();
		options.put("registry", registry);

		MqttBroker broker = MqttBroker.create(vertx, options);
		brokerRegistry.put(registry, broker);

		brokerEmitterSet.forEach(emitter -> emitter.onNext(broker));

		return broker;
	}

	public static MqttBrokerRegistry create(Vertx vertx) {
		return new MqttBrokerRegistry(vertx);
	}

	public void close() {
		brokerEmitterSet.forEach(Emitter::onComplete);
		brokerEmitterSet.clear();
		brokerRegistry.values().forEach(MqttBroker::close);
		brokerRegistry.clear();
	}

	public Optional<MqttBroker> get(String registry) {
		return Optional.of(this.brokerRegistry.get(registry));
	}

	public void publish(String registry, String topic, byte[] data) {
		if (this.brokerRegistry.containsKey(registry)) {
			this.brokerRegistry.get(registry).publish(topic, data);
		}
	}

	public Disposable subscribeMessage(Handler<MqttMessage> messageHandler) {

		return Observable.<MqttBroker> create(emitter -> {

			this.brokerEmitterSet.add(emitter);
			emitter.setCancellable(() -> {
				this.brokerEmitterSet.remove(emitter);
			});
		}).flatMap(emitter -> {
			return emitter.publishObservable();
		}).subscribe(message -> {
			messageHandler.handle(message);
		});
	}

	@Override
	public void handle(MqttEndpoint endpoint) {
		
		LOGGER.info("New client connecting...");

		this.identityService.findIdentity(endpoint.clientIdentifier(), endpoint.auth(), identityResult -> {

			if (identityResult.succeeded()) {

				final JsonObject identity = identityResult.result();

				final String registry = identity.getString("registry");

				this.getOrCreateBroker(registry).handle(endpoint);

			} else {

				endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED);

			}

		});

	}

}
