package io.edge.iot.dao;

import java.util.List;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public interface ThingRegistryDao {

	Future<List<JsonObject>> getAll(String registry);

	Future<JsonObject> findByName(String registry, String thingName);

	Future<Boolean> addOrUpdateThing(String registry, String thingName, JsonObject metadata);

}
