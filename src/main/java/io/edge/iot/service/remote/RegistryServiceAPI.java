package io.edge.iot.service.remote;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.ext.web.api.OperationRequest;
import io.vertx.ext.web.api.OperationResponse;
import io.vertx.ext.web.api.generator.WebApiServiceGen;

@WebApiServiceGen
public interface RegistryServiceAPI {
	
	static String ADDRESS = "edge.iot.registry-service-api";

	void getAll(String registry, OperationRequest context, Handler<AsyncResult<OperationResponse>> resultHandler);

	void findMetadata(String registry, String thingName, OperationRequest context, Handler<AsyncResult<OperationResponse>> resultHandler);

}
