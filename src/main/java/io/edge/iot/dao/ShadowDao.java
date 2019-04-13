package io.edge.iot.dao;

import java.util.List;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public interface ShadowDao {

	void get(String registry, String thingName, Handler<AsyncResult<JsonObject>> resultHandler);

	void getReported(String registry, String thingName, Handler<AsyncResult<JsonObject>> resultHandler);

	void getDesired(String registry, String thingName, Handler<AsyncResult<JsonObject>> resultHandler);

	void saveReported(String registry, String thingName, JsonObject shadow, Handler<AsyncResult<Boolean>> resultHandler);

	void saveDesired(String registry, String thingName, JsonObject shadow, Handler<AsyncResult<Boolean>> resultHandler);

	void deleteReported(String registry, String thingName, Iterable<String> keys, Handler<AsyncResult<Boolean>> resultHandler);

	void deleteDesired(String registry, String thingName, Iterable<String> desired, Handler<AsyncResult<Boolean>> resultHandler);

	void remove(String registry, String thingName, List<String> valueNameList, Handler<AsyncResult<Boolean>> resultHandler);

	void delete(String registry, String thingName, Handler<AsyncResult<Boolean>> resultHandler);

}
