package com.challenge.camunda;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.json.JSONObject;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@SpringBootApplication
@RequiredArgsConstructor
@Slf4j
public class CamundaApplication {
	private static final HttpClient httpClient = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_2)
			.connectTimeout(Duration.ofSeconds(60))
			.build();

	public static final String XML_GET_URL = "https://n35ro2ic4d.execute-api.eu-central-1.amazonaws.com/prod/engine-rest/process-definition/key/invoice/xml";


	public static void main(String[] args) {
		if (args.length != 2) {
			System.err.println("Usage: java -jar target/camunda.jar startNodeId endNodeId");
			System.exit(-1);
			return;
		}

		String startNodeId = args[0];
		String endNodeId = args[1];

        String xmlString = fetchXMLFromBPML(XML_GET_URL);

		if (xmlString == null) {
			System.err.println("Failed to fetch BPMN XML from the provided URL");
			System.exit(-1);
		}

		InputStream inputStream = new ByteArrayInputStream(xmlString.getBytes());
		BpmnModelInstance modelInstance = Bpmn.readModelFromStream(inputStream);

		List<String> path = findPath(modelInstance, startNodeId, endNodeId);

		if (path != null) {
			System.out.print("The path from " + startNodeId + " to " + endNodeId + " is: ");
			System.out.println(path);
		} else {
			System.out.println("No path found from " + startNodeId + " to " + endNodeId);
			System.exit(-1);
		}
	}

	private static List<String> findPath(BpmnModelInstance modelInstance,
										 String startNodeId, String endNodeId) {
		Set<String> visitedNodes = new HashSet<>();
		List<String> path = new ArrayList<>();
		path.add(startNodeId);

		if (traverseNodes(modelInstance, startNodeId, endNodeId, visitedNodes, path)) {
			return path;
		} else {
			return null;
		}
	}

	private static boolean traverseNodes(BpmnModelInstance modelInstance, String currentNodeId,
										 String endNodeId, Set<String> visited, List<String> path) {
		if (currentNodeId.equals(endNodeId)) {
			return true;
		}

		visited.add(currentNodeId);

		FlowNode currentNode = modelInstance.getModelElementById(currentNodeId);
		Collection<SequenceFlow> outgoingFlows = currentNode.getOutgoing();

		for (SequenceFlow outgoingFlow : outgoingFlows) {
			String nextNodeId = outgoingFlow.getTarget().getId();
			if (!visited.contains(nextNodeId)) {
				path.add(nextNodeId);
				if (traverseNodes(modelInstance, nextNodeId, endNodeId, visited, path)) {
					return true;
				}
				path.remove(path.size() - 1);
			}
		}

		return false;
	}

	public static String fetchXMLFromBPML(String url) {
		try {
			HttpResponse<String> fetchResponse = handleGetRequest(url);
			JSONObject jsonObject = new JSONObject(fetchResponse.body());

			if (fetchResponse.statusCode() != 200) {
				String errorMessage = jsonObject.optString("error", "Error fetching XML");
				log.error("Error message: {}", errorMessage);
				throw new RuntimeException(errorMessage);
			}
			return jsonObject.optString("bpmn20Xml");
		} catch (Exception e) {
			log.error("Failed to fetch XML from BPML", e);
			throw new RuntimeException("Failed to fetch XML from BPML", e);
		}
	}
	private static HttpResponse<String> handleGetRequest(String url) throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.GET()
				.build();
		return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
	}

}
