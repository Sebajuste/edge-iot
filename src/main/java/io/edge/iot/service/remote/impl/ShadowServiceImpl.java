package io.edge.iot.service.remote.impl;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.edge.iot.dao.ShadowDao;
import io.edge.iot.service.remote.ShadowService;
import io.edge.utils.exchange.Exchange;
import io.reactivex.Single;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ShadowServiceImpl implements ShadowService {

	/**
	 * Prend en paramètre un état de valeur sous forme de map, et l'état
	 * souhaité sous forme de Document MongoDB, et retourne une map de toutes
	 * les valeurs décohérentes
	 */
	private static final BiFunction<JsonObject, JsonObject, JsonObject> CHECK_DELTA = (reported, desired) -> {

		if (desired != null) {

			JsonObject delta = new JsonObject();

			for (String key : desired.fieldNames()) {

				Object desiredValue = desired.getValue(key);

				if (reported.containsKey(key)) {

					if (!desiredValue.equals(reported.getValue(key))) {
						delta.put(key, desiredValue);
					}
				} else {
					delta.put(key, desiredValue);
				}
			}
			return delta;
		}

		return null;
	};

	private final Vertx vertx;

	private final ShadowDao shadowDao;

	public ShadowServiceImpl(Vertx vertx, ShadowDao shadowDao) {
		this.vertx = vertx;
		this.shadowDao = shadowDao;
	}

	private static final Function<CompositeFuture, Boolean> COMPOSITE_UPDATE = r -> {

		if (r.succeeded()) {

			boolean updated = false;

			CompositeFuture composite = r.result();

			for (int i = 0; i < composite.size(); ++i) {

				if (composite.<Boolean> resultAt(i) == true) {
					updated = true;
					break;
				}

			}

			return updated;

		} else {
			return false;
		}

	};
	
	@Override
	public void getShadow(String registry, String thingName, Handler<AsyncResult<JsonObject>> resultHandler) {
		
		this.shadowDao.get(registry, thingName, ar -> {

			DeliveryOptions options = new DeliveryOptions().addHeader("registry", registry).addHeader("thingName", thingName);

			if (ar.succeeded()) {
				JsonObject reported = ar.result();

				if (reported.containsKey("state")) {
					reported = reported.getJsonObject("state");
				}

				if (reported.containsKey("reported")) {
					reported = reported.getJsonObject("reported");
				}

				JsonObject payload = new JsonObject().put("state", new JsonObject().put("reported", reported));

				// this.vertx.eventBus().publish(EDGE_IOT_SHADOW_GET_ACCEPTED, payload, options);
				
				Exchange.exchangeFanout(this.vertx, EDGE_IOT_SHADOW_GET_ACCEPTED).publish(payload, options);
				
				resultHandler.handle(Future.succeededFuture(payload));

				// message.reply(new JsonObject());

			} else {
				
				JsonObject payload = new JsonObject().put("message", ar.cause().getMessage());
				
				Exchange.exchangeFanout(this.vertx, EDGE_IOT_SHADOW_GET_REJECTED).publish(payload, options);
				
				resultHandler.handle(Future.failedFuture(ar.cause()));
				
				// JsonObject payload = new JsonObject().put("message", r.cause().getMessage());
				// this.vertx.eventBus().publish(EDGE_IOT_SHADOW_GET_REJECTED, payload, options);
				// message.reply(new JsonObject().put("error", r.cause().getMessage()));
			}

		});
	}

	@Override
	public void updateDesired(String registry, String thingName, JsonObject shadow, Handler<AsyncResult<Boolean>> resultHandler) {

		// JsonArray updateDesired = new JsonArray();

		List<String> deleteDesired = new ArrayList<>();

		JsonObject desired = shadow.getJsonObject("state").getJsonObject("reported");

		for (String name : desired.fieldNames()) {

			if (desired.getValue(name) == null) {
				deleteDesired.add(name);
			}

		}

		/*
		 * for (int i = 0; i < desiredData.size(); ++i) { JsonObject measure =
		 * desiredData.getJsonObject(i); if (measure.containsKey("value") &&
		 * measure.getValue("value") != null) { updateDesired.add(measure); }
		 * else { deleteDesired.add(measure.getString("name")); } }
		 */

		Future<Boolean> saveFuture = Future.future();
		shadowDao.saveDesired(registry, thingName, shadow, saveFuture);

		Future<Boolean> deleteFuture = Future.future();
		shadowDao.deleteDesired(registry, thingName, deleteDesired, deleteFuture);

		CompositeFuture.all(saveFuture, deleteFuture).map(COMPOSITE_UPDATE).setHandler(resultHandler);

	}

	@Override
	public void removeDesired(String registry, String thingName, JsonObject desiredData, Handler<AsyncResult<Boolean>> resultHandler) {
		this.shadowDao.deleteDesired(registry, thingName, desiredData.fieldNames(), resultHandler);
	}

	@Override
	public void getReported(String registry, String thingName, Handler<AsyncResult<JsonObject>> resultHandler) {
		this.shadowDao.getReported(registry, thingName, resultHandler);
	}

	@Override
	public void removeReported(String registry, String thingName, JsonArray keys, Handler<AsyncResult<Boolean>> resultHandler) {

		List<String> keyList = keys.stream().map(v -> (String) v).collect(Collectors.toList());

		this.shadowDao.deleteReported(registry, thingName, keyList, resultHandler);
	}

	@Override
	public void updateReported(String registry, String thingName, JsonObject shadow, Handler<AsyncResult<Boolean>> resultHandler) {
		// JsonArray updateReported = new JsonArray();

		List<String> deleteReported = new ArrayList<>();

		/*
		 * for (int i = 0; i < measures.size(); ++i) {
		 * 
		 * JsonObject measure = measures.getJsonObject(i);
		 * 
		 * if (measure.getValue("value") != null) { updateReported.add(measure);
		 * } else { deleteReported.add(measure.getString("name")); }
		 * 
		 * }
		 */

		JsonObject reported = shadow.getJsonObject("state", new JsonObject() ).getJsonObject("reported");

		for (String key : reported.fieldNames()) {
			Object value = reported.getValue(key);
			if (value == null) {
				deleteReported.add(key);
			}
		}

		Future<Boolean> saveFuture = Future.future();
		shadowDao.saveReported(registry, thingName, shadow, saveFuture);

		Future<Boolean> deleteFuture = Future.future();
		shadowDao.deleteReported(registry, thingName, deleteReported, deleteFuture);

		CompositeFuture.all(saveFuture, deleteFuture).map(COMPOSITE_UPDATE).setHandler(resultHandler);
	}

	@Override
	public void updateShadow(String registry, String thingName, JsonObject shadow, Handler<AsyncResult<Boolean>> resultHandler) {

		final long timestamp = System.currentTimeMillis();

		if (shadow.containsKey("state")) {

			JsonObject state = shadow.getJsonObject("state");

			this.updateShadow(state, shadow.getJsonObject("metadata", new JsonObject()), registry, thingName, shadow.getLong("timestamp", timestamp), resultHandler);

		} else {

			this.updateShadow(shadow, new JsonObject(), registry, thingName, timestamp, resultHandler);

		}

	}

	private void updateShadow(JsonObject state, JsonObject metadata, String registry, String thingName, Long timestamp, Handler<AsyncResult<Boolean>> resultHandler) {

		Objects.requireNonNull(registry);
		Objects.requireNonNull(thingName);

		/*
		 * this.thingRegistryDao.addOrUpdateThing(registry, thingName, new
		 * JsonObject(), ar -> { if (ar.failed()) {
		 * LOGGER.error("Cannot update Thing Registry : " +
		 * ar.cause().getMessage()); } });
		 */

		if (state.containsKey("reported")) {
			this.updateReported(registry, thingName, state.getJsonObject("reported"), metadata, timestamp, resultHandler);
		} else if (state.containsKey("desired")) {
			this.updateDesired(registry, thingName, state.getJsonObject("desired"), metadata, timestamp, resultHandler);
		} else {
			resultHandler.handle(Future.failedFuture("Invalid state for " + registry + "@" + thingName + ": " + state));
			// endHandler.handle(Future.failedFuture("Invalid state for " +
			// registry + "@" + thingName + ": " + state));
		}

	}

	private void updateDesired(String registry, String thingName, JsonObject desired, JsonObject metadata, Long timestamp, Handler<AsyncResult<Boolean>> resultHandler) {

		Objects.requireNonNull(registry);
		Objects.requireNonNull(thingName);

		JsonObject shadow = new JsonObject().put("state", new JsonObject().put("desired", desired)).put("metadata", metadata).put("timestamp", timestamp);

		final DeliveryOptions options = new DeliveryOptions().addHeader("registry", registry).addHeader("thingName", thingName);

		this.updateDesired(registry, thingName, shadow, r -> {

			if (r.succeeded() && r.result()) {
				options.addHeader("action", "accepted");
				resultHandler.handle(Future.succeededFuture(true));
			} else {
				options.addHeader("action", "rejected");
				resultHandler.handle(Future.succeededFuture(false));
			}

			Exchange.exchangeFanout(this.vertx, EDGE_IOT_SHADOW_UPDATE_RESULT).publish(shadow, options);

		});

		Single.<JsonObject> create(emitter -> {

			this.getReported(registry, thingName, reportedResult -> {
				if (reportedResult.succeeded()) {
					emitter.onSuccess(reportedResult.result());
				} else {
					emitter.onError(reportedResult.cause());
				}
			});

		})//
				.map(reported -> CHECK_DELTA.apply(reported, desired))//

				.subscribe(delta -> {

					if (delta != null && !delta.isEmpty()) {
						// JsonObject deltaMessage = new JsonObject().put("state", delta).put("metadata", metadata).put("timestamp", timestamp);

						// this.vertx.eventBus().publish(EDGE_IOT_SHADOW_UPDATE_DELTA,
						// deltaMessage, options);
						Exchange.exchangeFanout(this.vertx, EDGE_IOT_SHADOW_UPDATE_DELTA).publish(shadow, options);
					}

				});

	}

	private void updateReported(String registry, String thingName, JsonObject reported, JsonObject metadata, Long timestamp, Handler<AsyncResult<Boolean>> resultHandler) {

		final DeliveryOptions options = new DeliveryOptions().addHeader("registry", registry).addHeader("thingName", thingName);

		// final JsonArray measures = ShadowVerticle.createMeasures(reported,
		// metadata,
		// timestamp);

		final JsonObject shadow = new JsonObject().put("state", new JsonObject().put("reported", reported)).put("metadata", metadata).put("timestamp", timestamp);

		shadow.put("datetime", ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT));

		/**
		 * Save into TimeStamp Database
		 */
		// vertx.eventBus().send(TimeSeriesVerticle.EDGE_IOT_TIMESERIES,
		// measures,
		// options);

		/**
		 * Update Shadow
		 */

		Future<Boolean> removeDesiredFuture = Future.future();
		this.removeDesired(registry, thingName, reported, removeDesiredFuture);

		Future<Boolean> updateReportedFuture = Future.future();
		this.updateReported(registry, thingName, shadow, updateReportedFuture);

		CompositeFuture.all(removeDesiredFuture, updateReportedFuture).setHandler(compositeFuture -> {

			if (compositeFuture.succeeded()) {
				options.addHeader("action", "accepted");
				resultHandler.handle(Future.succeededFuture(true));
			} else {
				options.addHeader("action", "rejected");
				resultHandler.handle(Future.succeededFuture(false));
			}

			Exchange.exchangeFanout(this.vertx, EDGE_IOT_SHADOW_UPDATE_RESULT).publish(shadow, options);

		});

	}

}
