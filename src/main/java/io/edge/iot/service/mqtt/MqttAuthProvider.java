package io.edge.iot.service.mqtt;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.ext.auth.User;

public interface MqttAuthProvider {
	
	void authenticate(JsonObject authInfo, Handler<AsyncResult<User>> resultHandler);

}
