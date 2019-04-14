package io.edge.iot.verticle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.edge.iot.service.remote.ShadowService;
import io.edge.utils.exchange.Exchange;
import io.edge.utils.timeseries.BatchPoints;
import io.edge.utils.timeseries.Point;
import io.edge.utils.timeseries.influxdb.InfluxDB;
import io.edge.utils.timeseries.influxdb.InfluxDbOptions;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;

/**
 * Save changes of values in a time series detabase
 * 
 * @author smartinez
 *
 */
public class TimeSeriesVerticle extends AbstractVerticle {

	public static final String EDGE_IOT_TIMESERIES = "$edge.iot.timeseries";

	/**
	 * Transforme un objets de valeurs à l'aide de ses méta-données en un
	 * tableau de valeurs indépendantes
	 * 
	 * @param data
	 * @param metadata
	 * @param timestamp
	 * @return
	 */
	private static final List<JsonObject> createMeasures(String registry, String thingName, JsonObject data, JsonObject metadata, long timestamp) {

		List<JsonObject> measures = new ArrayList<>();

		for (String key : data.fieldNames()) {

			JsonObject entity = new JsonObject();
			entity.put("registry", registry);
			entity.put("thingName", thingName);
			entity.put("name", key);
			entity.put("value", data.getValue(key));
			entity.put("timestamp", timestamp);

			if (metadata != null && metadata.containsKey(key)) {
				JsonObject valueMetadata = metadata.getJsonObject(key);
				if (valueMetadata.containsKey("timestamp")) {
					entity.put("timestamp", metadata.getLong("timestamp"));
				}
			}

			measures.add(entity);
		}
		return measures;
	}
	
	
	private static final List<JsonObject> createMeasures(Message<JsonObject> message) {
		final String registry = message.headers().get("registry");

		final String thingName = message.headers().get("thingName");
		
		final JsonObject shadow = message.body();
		
		return TimeSeriesVerticle.createMeasures(registry, thingName, shadow.getJsonObject("state").getJsonObject("reported"), shadow.getJsonObject("metadata"), shadow.getLong("timestamp"));
	}

	@Override
	public void start() {

		HttpClientOptions influxDBOptions = new HttpClientOptions();
		influxDBOptions.setDefaultHost(config().getString("influxdb.host", "localhost"));
		influxDBOptions.setDefaultPort(config().getInteger("influxdb.port", 8086));

		HttpClient client = vertx.createHttpClient(influxDBOptions);

		InfluxDbOptions options = new InfluxDbOptions().credentials(config().getString("influxdb.user"), config().getString("influxdb.password"));

		InfluxDB influxDB = InfluxDB.connect(client, options);

		String databaseName = config().getString("influxdb.dbname", "edge_iot");
		
		int batchSize = config().getInteger("influxdb.batchSize", 5000);
		
		CircuitBreaker saveBatchCB = CircuitBreaker.create("edge.iot.measures-dao.saveBatch", vertx);
		
		Flowable.<JsonObject>create(emitter -> {
			
			MessageConsumer<JsonObject> consumer = Exchange.exchangeFanout(vertx, ShadowService.EDGE_IOT_SHADOW_UPDATE_RESULT).start().<JsonObject>consumer(EDGE_IOT_TIMESERIES, message -> {
				
				Iterable<JsonObject> values = TimeSeriesVerticle.createMeasures(message);
				values.forEach(emitter::onNext);
				
			});

			emitter.setCancellable(consumer::unregister);
			
		}, BackpressureStrategy.BUFFER)//
		
		.map(measure -> {
			
			return Point.measurement(measure.getString("name"))//
					.addTag("registry", measure.getString("registry"))//
					.addTag("thingName", measure.getString("thingName"))//
					.setValue( measure.getValue("value") )//
					.time(measure.getLong("timestamp"), TimeUnit.MILLISECONDS)//
					.build();

		})//
		
		
		.buffer(5L, TimeUnit.SECONDS, batchSize)//
		
		.<BatchPoints>map(points -> {
			return BatchPoints.database(databaseName).points(points).build();
		})
		.filter(batch -> !batch.isEmpty() )//
		
		.retry() //

		.subscribe(batch -> {
			
			saveBatchCB.execute(future -> {
				influxDB.write(batch, ar -> {
					if( ar.succeeded()) {
						future.complete();
					} else {
						future.fail(ar.cause());
					}
				});
			}).setHandler(ar -> {
				
			});
	
		});
	

	}

}
