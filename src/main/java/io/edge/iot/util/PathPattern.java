package io.edge.iot.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PathPattern {

	public static final Pattern URL_PARAM_PATTERN = Pattern.compile("\\{([\\w\\s]+)\\}");

	public static final String URL_VAR_PATTERN = "([\\$\\^./*-+\\S]+)";

	private final Pattern pattern;

	private final List<String> keyWords;

	protected PathPattern(String regex, List<String> keyWords) {
		super();
		this.pattern = Pattern.compile(regex);
		this.keyWords = keyWords != null ? Collections.unmodifiableList(keyWords) : null;
	}

	public static PathPattern compile(String path, Pattern paramPattern, String varPattern) {

		String result = "^" + path + "$";

		Matcher m = paramPattern.matcher(path);

		List<String> keyWords = new LinkedList<>();

		while (m.find()) {
			keyWords.add(m.group(1));
			result = result.replace(m.group(0), varPattern);
		}

		return new PathPattern(result, keyWords);
	}

	public static PathPattern compile(String path) {
		return PathPattern.compile(path, URL_PARAM_PATTERN, URL_VAR_PATTERN);
	}

	public static PathPattern compile(String path, String pattern, String varPattern) {
		return PathPattern.compile(path, Pattern.compile(pattern), varPattern);
	}

	public Map<String, String> matcherKey(String str) {

		Map<String, String> result = null;
		if (keyWords != null) {
			Matcher matcher = pattern.matcher(str);
			if (matcher.matches()) {
				result = new HashMap<>();
				for (int i = 1; i <= matcher.groupCount(); ++i) {
					result.put(keyWords.get(i - 1), matcher.group(i));
				}
			}
		}
		return result;
	}

	public List<String> matcherList(String str) {
		List<String> result = null;
		Matcher matcher = pattern.matcher(str);
		if (matcher.matches()) {
			result = new LinkedList<>();
			for (int i = 1; i <= matcher.groupCount(); ++i) {
				result.add(matcher.group(i));
			}
		}
		return result;
	}
	
}
