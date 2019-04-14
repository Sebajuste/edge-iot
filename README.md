Edge IoT
------

Provide services for IoT to:
- Communicate with gateway / remote objects
- Create replication data between cloud and objects
- Save data as metrics values
- Send new configuration to objects
- Create different namespaces for multi-tenancy
- [Horizontal Scaling(]Support horizontal scaling)

# Communication

Edge IoT use MQTT broker to etablish communication, and manage some topics. These are pretty the same than [AWS IoT-Core](https://docs.aws.amazon.com/iot/latest/developerguide/topics.html)
Excpect than all topics don't start with *$aws* prefix, but *$edge* prefics

### Authentication

Edge IoT doen't support authentication yet.

However, credentials are used for multi-tenancy. Use _clientID_ ou _Username_ to indicate which namespace must be used, like the format: `<namespace>@<object-credential>`

### Topics

- To send new data to the cloud, use topic: `$edge/things/<thingName>/shadow/update`
- To listen order and command from th cloud, subscribe topic: `$edge/things/<thingName>/shadow/update/delta`

- To make a request, send empty payload to `$edge/things/<thingName>/shadow/get`
- To listen get request answer, subscribe topic `$edge/things/<thingName>/shadow/get/accepted` and `$edge/things/<thingName>/shadow/get/rejected`

### Payload

The format of the payload is a json format, using same format than [AWS IoT-Core](https://docs.aws.amazon.com/iot/latest/developerguide/device-shadow-document.html).

### Custom

You are totally free to use other custom topics as you want.


# Interaction

Third party software can interact with Edge IoT using :
- an MQTT Client
- the API (see src/main/resources/iot-api.yaml).
- vert.x programms who can listen on event bus

### API

See *src/main/resources/iot-api.yaml* to knowledge about API specifications.

You must use [Edge API](https://github.com/Sebajuste/edge-api) as web proxy to use the API.

### MQTT

- All MQTT events are sent to `$edge.iot.mqtt` queue address.
- All new shadow update events are sent to `$edge.iot.things.shadow.update.result` queue address.
- All new shadow update events are sent to `$edge.iot.things.shadow.update.result` queue address.
- All delta data between desired and reported values are sent to `$edge.iot.things.shadow.update.delta` queue address.
- All request answer for a shadow are sent to `$edge.iot.things.shadow.get.accepted` queue address.

**To avoid to stole message, please use Exchange object from [Edge Utils](https://github.com/Sebajuste/edge-utils) **

# Database

### Shadow storage

Edge IoT use [MongoDB](https://www.mongodb.com) for the storage of the *shadows*, the cloud replication of local information.

You can configure the database with these environement variables:

- **mongodb.host** Host name of the database. *Default 127.0.0.1*
- **mongodb.port** Port of the database. *Default 27017*
- **mongodb.dbname** Name of the database. *Default edge-iot*
- **mongodb.user** Username of the database. *Not required. Not defined by default*
- **mongodb.password** Password of the database. *Not required. Not defined by default*

- All shadows are stored in _shadows_ collection
- All objects registry are stores in _registry_ collection

### Metrics values

Edge IoT use [InfluxDB](https://www.influxdata.com/time-series-platform/influxdb/) for the storage of all time series data.

You can configure the database with these environement variables:

- **influxdb.host** Host name of the database. *Default 127.0.0.1*
- **influxdb.port** Port of the database. *Default 8086*
- **influxdb.dbname** Name of the database. *Default edge_iot*
- **influxdb.batchSize** Maximum batch values to save metrics. *Default 5000*
- **influxdb.user** Username of the database. *Required. Not defined by default*
- **influxdb.password** Password of the database. *Required. Not defined by default*

Tags used to store metrics are:

- **registry** : The name of the namespace for multi-tenancy
- **thingName** : The name of the object

# Horizontal Scaling

Edge IoT is designed to be run on different nodes / servers together. Round Robin is use to distribute automatically the different event in the cluster.
It using Hazelcast to share informations, and vert.x event bus to send events.

Start the jar file file *--cluster* option to use Hazelcast.

You can provide a custom Hazelcast configuration file. Define the path to it with *-cp <path>* option in command line startup. 
