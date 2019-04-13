package io.edge.iot.dao;

import java.util.List;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public interface MeasureDao {

	void addMeasures(String registry, String thingName, JsonObject measure, Handler<AsyncResult<Boolean>> resultHandler);

	void addMeasures(String registry, String thingName, List<JsonObject> measures, Handler<AsyncResult<Boolean>> resultHandler);

}
