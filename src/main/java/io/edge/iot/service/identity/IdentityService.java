package io.edge.iot.service.identity;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.mqtt.MqttAuth;

public interface IdentityService {

	void findIdentity(String clientId, MqttAuth auth, Handler<AsyncResult<JsonObject>> resultHandler);

}
