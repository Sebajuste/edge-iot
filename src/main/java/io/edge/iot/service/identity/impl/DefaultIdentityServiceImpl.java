package io.edge.iot.service.identity.impl;

import io.edge.iot.service.identity.IdentityService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public class DefaultIdentityServiceImpl implements IdentityService {

	@Override
	public void findIdentity(String clientId, Handler<AsyncResult<JsonObject>> resultHandler) {

		resultHandler.handle(Future.succeededFuture(new JsonObject().put("registry", "default")));

	}

}
