package io.edge.iot.service.mqtt;

public class MqttTopicFinder {

	public static enum Result {
		INTERMEDIATE, MATCH, NO_MATCH,
	}

	public static MqttTopicFinder.Result matchUri(String splitTopic[], String topicPattern) {
		String splitPattern[] = topicPattern.split("/");

		int minSize = splitTopic.length < splitPattern.length ? splitTopic.length : splitPattern.length;

		for (int i = 0; i < minSize; ++i) {
			if (splitPattern[i].equals("#")) {
				return MqttTopicFinder.Result.MATCH;
			}
			if (!splitTopic[i].equals(splitPattern[i]) && !splitPattern[i].equals("+")) {
				return MqttTopicFinder.Result.NO_MATCH;
			}
		}

		if (splitTopic.length == splitPattern.length) {
			return MqttTopicFinder.Result.MATCH;
		} else if (splitTopic.length < splitPattern.length) {
			return MqttTopicFinder.Result.INTERMEDIATE;
		} else {
			return MqttTopicFinder.Result.NO_MATCH;
		}

	}

	/**
	 * Check if a topic matching on a topic pattern.
	 * 
	 * <p>
	 * Exemples
	 * <ul>
	 * <li>topic: <b>users/john</b>, topicPattern: <b>users/john</b> will return
	 * MATCH</li>
	 * <li>topic: <b>users/john</b>, topicPattern: <b>users/+</b> will return
	 * MATCH</li>
	 * <li>topic: <b>users/john</b>, topicPattern: <b>#</b> will return
	 * MATCH</li>
	 * <li>topic: <b>users/john</b>, topicPattern: <b>users/jane</b> will return
	 * NO_MATCH</li>
	 * </ul>
	 * </p>
	 * 
	 * @param topic
	 * @param topicPattern
	 * @return
	 */
	public static MqttTopicFinder.Result matchUri(String topic, String topicPattern) {
		return MqttTopicFinder.matchUri(topic.split("/"), topicPattern);
	}

}