package io.edge.iot.service.mqtt;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MqttTopicFinderTest {

	@Test
	public void test() {

		assertEquals(MqttTopicFinder.Result.MATCH, MqttTopicFinder.matchUri("toto/tata", "toto/tata"));

	}

	@Test
	public void iotTest() {

		assertEquals(MqttTopicFinder.Result.MATCH, MqttTopicFinder.matchUri("$edge/things/toto/shadow/update", "$edge/things/+/shadow/#"));
		
		assertEquals(MqttTopicFinder.Result.NO_MATCH, MqttTopicFinder.matchUri("$edge/things/toto/shadow/update/accepted", "$edge/things/+/shadow/update"));

	}

}
