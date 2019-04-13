package io.edge.iot.service.remote;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@ProxyGen
@VertxGen
public interface ShadowService {

	static final String ADDRESS = "edge.iot.shadow-service";

	static final String EDGE_IOT_SHADOW_UPDATE_RESULT = "$edge.iot.things.shadow.update.result";

	static final String EDGE_IOT_SHADOW_UPDATE_DELTA = "$edge.iot.things.shadow.update.delta";
	
	static final String EDGE_IOT_SHADOW_GET_ACCEPTED = "$edge.iot.things.shadow.get.accepted";
	
	static final String EDGE_IOT_SHADOW_GET_REJECTED = "$edge.iot.things.shadow.get.rejected";

	void getShadow(String registry, String thingName, Handler<AsyncResult<JsonObject>> resultHandler);

	void updateDesired(String registry, String thingName, JsonObject shadow, Handler<AsyncResult<Boolean>> resultHandler);

	void removeDesired(String registry, String thingName, JsonObject desiredData, Handler<AsyncResult<Boolean>> resultHandler);

	void getReported(String registry, String thingName, Handler<AsyncResult<JsonObject>> resultHandler);

	void removeReported(String registry, String thingName, JsonArray keys, Handler<AsyncResult<Boolean>> resultHandler);

	void updateReported(String registry, String thingName, JsonObject shadow, Handler<AsyncResult<Boolean>> resultHandler);

	void updateShadow(String registry, String thingName, JsonObject shadow, Handler<AsyncResult<Boolean>> resultHandler);

}
