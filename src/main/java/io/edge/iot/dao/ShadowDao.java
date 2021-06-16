package io.edge.iot.dao;

import java.util.List;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public interface ShadowDao {

	Future<JsonObject> get(String registry, String thingName);

	Future<JsonObject> getReported(String registry, String thingName);

	Future<JsonObject> getDesired(String registry, String thingName);

	Future<Boolean> saveReported(String registry, String thingName, JsonObject shadow);

	Future<Boolean> saveDesired(String registry, String thingName, JsonObject shadow);

	Future<Boolean> deleteReported(String registry, String thingName, Iterable<String> keys);

	Future<Boolean> deleteDesired(String registry, String thingName, Iterable<String> desired);

	Future<Boolean> remove(String registry, String thingName, List<String> valueNameList);

	Future<Boolean> delete(String registry, String thingName);

}
