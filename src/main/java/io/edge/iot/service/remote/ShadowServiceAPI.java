package io.edge.iot.service.remote;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.ext.web.api.OperationRequest;
import io.vertx.ext.web.api.OperationResponse;
import io.vertx.ext.web.api.generator.WebApiServiceGen;

@WebApiServiceGen
public interface ShadowServiceAPI {

	static String ADDRESS = "edge.iot.shadow-service-api";

	void getShadow(String registry, String thingName, OperationRequest context, Handler<AsyncResult<OperationResponse>> resultHandler);

}
