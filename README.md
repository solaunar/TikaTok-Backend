# TikaTok-Backend
Distributed application project for the couse of Distributed Systems at AUEB. <br>
Objective of this project was to implement a video streaming/ upload service based on the publisher/ subscriber model. <br>
Users can search for topics (channels, hashtags), subscribe to topics, stream (for now download locally) videos of other users and upload/ delete their own videos. 

### Built With
* [Java 1.16](https://docs.oracle.com/en/java/javase/16/docs/api/index.html)
* [Apache Tika 1.26](https://www.apache.org/dyn/closer.cgi/tika/1.26/tika-app-1.26.jar)

## Nodes Description
### Zookeeper
Synchronization node. Zookeeper is responsible for storing, updating and distributing the [InfoTable](#infotable) to the Brokers. <br>
The Zookeeper is kept as simple as possible for this project, whereas it could be as complicated as in the [Apache Kafka](https://kafka.apache.org/documentation/) platform.

### Broker
Intermediate node. Each Broker node is responsible for a group of topics, which are assigned using the SHA-1 hashing. 
Broker Nodes serve AppNode "client" requests for upload, deletion or streaming (download) of videos. <br>
Brokers, also, request videos from AppNode "publishers" (pull) to satisfy the AppNode "client" request of video steaming from other AppNodes. <br>
In cases of upload/ deletion requests, after handling it, the Brokers have to issue themselves a request to the Zookeeper for the InfoTable to be updated. <br>
Brokers also issue an InfoTable retrieval request each time an AppNode connects to them or requests anything from them, so that they all always have the most updated <br>
version.

### AppNode
Publisher & Subscriber node. The AppNode technically represents a user of the system. <br>
An AppNode node can be both a Publisher (server side, provide videos to clients) and a Subscriber (client side, ask for videos from publishers) <br>
or just Subscribers if the user hasn't published any videos on the platform. <br>
AppNodes as Publishers maintain a server and handle requests from Brokers when a user (another AppNode) requests one of their videos, <br>
in such a case they chunk the video data and send (push) them chunk by chunk to the Broker, which will then send them to the client AppNode. <br>
AppNodes as Subscribers just issue requests to the Brokers for video streaming (download), by searching or subscribing to a topic. <br>
Other requests of AppNodes issued to Brokers are the ones where the user uploads/ deletes a video of theirs, which means they have to update the data about them on the platform.

## InfoTable <a name="infotable"></a>
Infotable is a vital compoment of the system which contains all of the important data for the application to work properly. The Zookeeper is responsible for keeping <br>
this component updated after every update request it receives from the Brokers. The component is distributed to the Brokers each time it's needed, via the Zookeeper.
The important data we mentioned could be:
* The Broker IDs, as they were assigned from the hashing.
* The topics that are assigned to each Broker.
* The available AppNodes that maintain a server side (are Publishers) and the topics for which they have videos published.
* The available videos on the system categorized by topic.

## Usage
### Requirements
   * Any Java distribution over 6.
   * Apache Tika library (link above).

### Directions
1. Clone the project to IntelliJ IDEA.
2. Add the Apache Tika library at the libraries/ modules of the project.
3. Change the IP addresses in the Node.java file, you may add:
   * The same IP for all addresses but different ports for each one.
   * Different IPs for each address (ports can be the same or different).
   * Assign one IP and different ports to Zookeeper and all Brokers and two different IPs for the AppNodes, and every similar combination.
4. Make configurations for the mains and run them in this order:
   * Zookeeper main configuration.
   * Broker1 main configuration.
   * Broker2 main configuration.
   * Broker3 main configuration.
   * At least 2 AppNode configurations (pex AppNode1, AppNode2).
   #### Note: Brokers may run in any order.
5. Follow the intructions on the run windows of the AppNodes to use the app as an actual user. <br>
   You might as well check the other node run windows for the prints.
6. You're all set!

## Collaborators 
* [KonstantinaAn](https://github.com/KonstantinaAn)
* [WagonLee](https://github.com/WagonLee)
* [DebsTheLemon](https://github.com/DebsTheLemon)
* [solaunar](https://github.com/solaunar)

## Contributing
Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

## Licence
[MIT](https://choosealicense.com/licenses/mit/)
