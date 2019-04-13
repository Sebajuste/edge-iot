package io.edge.iot.service.mqtt;

import java.util.List;

import io.edge.iot.util.PathPattern;

public final class PathPatternMqtt extends PathPattern {

	private PathPatternMqtt(String regex, List<String> keyWords) {
		super(regex, keyWords);
	}

	public static PathPattern create(String topic) {
		return new PathPatternMqtt("^" + topic.replaceAll("\\$", "\\\\\\$").replaceAll("\\+", "([\\\\\\w:_-]{1,})").replaceFirst("\\#", "([\\\\\\w\\\\\\/:_-]{1,})") + "$", null);
	}
	
}
