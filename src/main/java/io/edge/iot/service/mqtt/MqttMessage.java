package io.edge.iot.service.mqtt;

import java.io.Serializable;

import io.vertx.core.buffer.Buffer;

public class MqttMessage implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private final String registry;

	private final String topic;

	private final Buffer payload;

	public MqttMessage(String registry, String topic, Buffer payload) {
		super();
		this.registry = registry;
		this.topic = topic;
		this.payload = payload;
	}

	public String getRegistry() {
		return registry;
	}

	public String getTopic() {
		return topic;
	}

	public Buffer getPayload() {
		return payload;
	}

}
