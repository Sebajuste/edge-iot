package io.edge.iot.verticle;

import io.edge.iot.dao.ShadowDao;
import io.edge.iot.dao.ThingRegistryDao;
import io.edge.iot.dao.mongodb.ShadowDaoMongo;
import io.edge.iot.dao.mongodb.ThingRegistryDaoMongo;
import io.edge.iot.service.remote.RegistryService;
import io.edge.iot.service.remote.RegistryServiceAPI;
import io.edge.iot.service.remote.ShadowService;
import io.edge.iot.service.remote.ShadowServiceAPI;
import io.edge.iot.service.remote.impl.RegistryServiceAPIImpl;
import io.edge.iot.service.remote.impl.RegistryServiceImpl;
import io.edge.iot.service.remote.impl.ShadowServiceAPIImpl;
import io.edge.iot.service.remote.impl.ShadowServiceImpl;
import io.edge.utils.exchange.Exchange;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.serviceproxy.ServiceBinder;

/**
 * Manage the shadow of each object and persist it into a database
 * 
 * @author smartinez
 *
 */
public class ShadowVerticle extends AbstractVerticle {

	private static final Logger LOGGER = LoggerFactory.getLogger(ShadowVerticle.class);

	/**
	 * Nom du canal de sortie des écart entre les valeurs lues et attendues
	 */
	public static final String EDGE_IOT_SHADOW_UPDATE_DELTA = "$edge.iot.things.shadow.update.delta";

	private ShadowDao shadowDao = null;

	private ThingRegistryDao thingRegistryDao = null;

	private ShadowService shadowService = null;

	private Record iotWebApiRecord = null;

	private void onMqttPublishReceived(Message<Buffer> message) {

		String registry = message.headers().get("registry");

		String thingName = message.headers().get("thingName");
		
		Buffer payload = message.body();

		JsonObject shadow = new JsonObject(payload.toString());
		
		this.shadowService.updateShadow(registry, thingName, shadow, ar -> {
			if( ar.succeeded()) {
				message.reply(null);
			} else {
				message.fail(-1, ar.cause().getMessage());
			}
		});

	}

	@Override
	public void start() {

		JsonObject mongoConfig = new JsonObject();

		mongoConfig.put("host", config().getString("mongodb.host", "127.0.0.1"));
		mongoConfig.put("port", config().getInteger("mongodb.port", 27017));

		mongoConfig.put("db_name", config().getString("mongodb.dbname", "edge-iot"));

		if (config().containsKey("mongodb.user")) {
			mongoConfig.put("username", config().getString("mongodb.user"));
		}

		if (config().containsKey("mongodb.password")) {
			mongoConfig.put("password", config().getString("mongodb.password"));
		}

		mongoConfig.put("serverSelectionTimeoutMS", 5000L);

		MongoClient mongoClient = MongoClient.createShared(vertx, mongoConfig);

		this.shadowDao = new ShadowDaoMongo(vertx, mongoClient);

		this.thingRegistryDao = new ThingRegistryDaoMongo(vertx, mongoClient);

		this.shadowService = new ShadowServiceImpl(vertx, shadowDao);

		Exchange.exchangeFanout(vertx, "$edge.iot.mqtt").start().consumer("$edge.iot.mqtt-shadow-bridge", this::onMqttPublishReceived);

		/**
		 * Register services
		 */

		ServiceBinder serviceBinder = new ServiceBinder(vertx);

		ServiceDiscovery discovery = ServiceDiscovery.create(vertx);

		//
		// Bus Service
		//

		serviceBinder.setAddress(ShadowService.ADDRESS).register(ShadowService.class, this.shadowService);
		
		RegistryService registryService = new RegistryServiceImpl(thingRegistryDao);
		serviceBinder.setAddress(RegistryService.ADDRESS).register(RegistryService.class, registryService);
		

		//
		// API service
		//

		ShadowServiceAPI shadowServiceAPI = new ShadowServiceAPIImpl(shadowDao);
		serviceBinder.setAddress(ShadowServiceAPI.ADDRESS).register(ShadowServiceAPI.class, shadowServiceAPI);
		
		RegistryServiceAPI registryServiceAPI = new RegistryServiceAPIImpl(thingRegistryDao);
		serviceBinder.setAddress(RegistryServiceAPI.ADDRESS).register(RegistryServiceAPI.class, registryServiceAPI);
		

		// Registry Service

		vertx.eventBus().consumer("edge.iot.webapi-service.yaml", message -> {

			String action = message.headers().get("action");

			if ("getOpenAPI".equals(action)) {

				vertx.fileSystem().readFile("src/main/resources/config.yaml", readResult -> {

					if (readResult.succeeded()) {

						message.reply(readResult.result());

					} else {
						message.fail(0, readResult.cause().getMessage());
					}

				});

			} else {
				message.fail(0, "Invalid action");
			}

		});

		iotWebApiRecord = new Record()//
				// .setName("edge-iot")//
				.setType("eventbus-webapi-service-proxy")//
				.setLocation(new JsonObject().put("endpoint", "edge.iot.webapi-service.yaml"))//
				.setMetadata(new JsonObject().put("supath", "/iot"))//
				.setName("EdgeIoT");

		discovery.publish(iotWebApiRecord, recordResult -> {

		});

	}

	@Override
	public void stop() {

		ServiceDiscovery discovery = ServiceDiscovery.create(vertx);

		if (iotWebApiRecord != null) {
			discovery.unpublish(iotWebApiRecord.getRegistration(), ar -> {

			});
		}

	}

}
