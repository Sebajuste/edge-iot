package io.edge.iot.dao;

import java.util.List;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public interface ThingRegistryDao {

	void getAll(String registry, Handler<AsyncResult<List<JsonObject>>> resultHandler);

	void findByName(String registry, String thingName, Handler<AsyncResult<JsonObject>> resultHandler);

	void addOrUpdateThing(String registry, String thingName, JsonObject metadata, Handler<AsyncResult<Boolean>> resultHandler);

}
