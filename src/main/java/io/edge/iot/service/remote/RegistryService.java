package io.edge.iot.service.remote;

import java.util.List;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

@ProxyGen
@VertxGen
public interface RegistryService {

	public static String ADDRESS = "edge.iot.registry-service";

	void createThing(String registry, String thingName, JsonObject metadata, Handler<AsyncResult<Boolean>> resultHandler);

	void getAll(String registry, Handler<AsyncResult<List<JsonObject>>> resultHandler);

	void findMetadata(String registry, String thingName, Handler<AsyncResult<JsonObject>> resultHandler);
}
