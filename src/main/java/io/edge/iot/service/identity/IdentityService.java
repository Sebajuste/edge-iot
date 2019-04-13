package io.edge.iot.service.identity;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public interface IdentityService {

	void findIdentity(String clientId, Handler<AsyncResult<JsonObject>> resultHandler);

}
