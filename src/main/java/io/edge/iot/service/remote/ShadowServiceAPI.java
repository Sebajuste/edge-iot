package io.edge.iot.service.remote;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.ext.web.api.service.ServiceRequest;
import io.vertx.ext.web.api.service.ServiceResponse;
import io.vertx.ext.web.api.service.WebApiServiceGen;

@WebApiServiceGen
public interface ShadowServiceAPI {

	static String ADDRESS = "edge.iot.shadow-service-api";

	void getShadow(String registry, String thingName, ServiceRequest context, Handler<AsyncResult<ServiceResponse>> resultHandler);

}
