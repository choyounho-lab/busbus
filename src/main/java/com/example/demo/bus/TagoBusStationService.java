package com.example.demo.bus;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class TagoBusStationService {

	private static final String NEARBY_STATION_PATH = "/getCrdntPrxmtSttnList";

	private final String baseUrl;
	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private final String configuredServiceKey;

	@Autowired
	public TagoBusStationService(
		ObjectMapper objectMapper,
		@Value("${tago.station.base-url:https://apis.data.go.kr/1613000/BusSttnInfoInqireService}") String baseUrl,
		@Value("${tago.station.service-key:${TAGO_STATION_SERVICE_KEY:${tago.service-key:${TAGO_SERVICE_KEY:}}}}") String configuredServiceKey
	) {
		this.baseUrl = trimTrailingSlash(baseUrl);
		this.restClient = RestClient.create();
		this.objectMapper = objectMapper;
		this.configuredServiceKey = configuredServiceKey == null ? "" : configuredServiceKey;
	}

	public List<TagoBusStation> fetchNearbyStations(String latitude, String longitude, String serviceKey) {
		require("latitude", latitude);
		require("longitude", longitude);

		return fetchItems(NEARBY_STATION_PATH, Map.of(
			"gpsLati", latitude,
			"gpsLong", longitude,
			"pageNo", "1",
			"numOfRows", "100"
		), serviceKey).stream()
			.map(item -> new TagoBusStation(
				text(item, "citycode"),
				text(item, "nodeid"),
				text(item, "nodenm"),
				text(item, "nodeno"),
				text(item, "gpslati"),
				text(item, "gpslong"),
				item.toString()
			))
			.filter(station -> !station.nodeId().isBlank())
			.toList();
	}

	private List<JsonNode> fetchItems(String path, Map<String, String> params, String serviceKey) {
		String response = restClient.get()
			.uri(URI.create(buildUrl(path, params, serviceKey)))
			.retrieve()
			.body(String.class);

		try {
			JsonNode root = objectMapper.readTree(response);
			assertSuccess(root);
			JsonNode itemNode = root.findValue("item");
			if (itemNode == null || itemNode.isMissingNode() || itemNode.isNull()) {
				return List.of();
			}
			return asList(itemNode);
		} catch (RuntimeException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to parse TAGO station response.", ex);
		}
	}

	private void assertSuccess(JsonNode root) {
		JsonNode resultCode = root.findValue("resultCode");
		if (resultCode == null || resultCode.isNull() || "00".equals(resultCode.asText())) {
			return;
		}

		JsonNode resultMsg = root.findValue("resultMsg");
		throw new IllegalStateException("TAGO station result " + resultCode.asText() + ": "
			+ (resultMsg == null ? "" : resultMsg.asText()));
	}

	private List<JsonNode> asList(JsonNode node) {
		if (!node.isArray()) {
			return List.of(node);
		}

		List<JsonNode> items = new ArrayList<>();
		for (JsonNode item : node) {
			items.add(item);
		}
		return items;
	}

	private String buildUrl(String path, Map<String, String> params, String serviceKey) {
		StringBuilder url = new StringBuilder(baseUrl)
			.append(path)
			.append("?serviceKey=")
			.append(encodeServiceKey(resolveServiceKey(serviceKey)))
			.append("&_type=json");

		params.forEach((key, value) -> url
			.append("&")
			.append(encode(key))
			.append("=")
			.append(encode(value)));

		return url.toString();
	}

	private String resolveServiceKey(String serviceKey) {
		if (serviceKey != null && !serviceKey.isBlank()) {
			return serviceKey.trim();
		}
		if (!configuredServiceKey.isBlank()) {
			return configuredServiceKey.trim();
		}
		throw new IllegalArgumentException("TAGO station serviceKey is required.");
	}

	private String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull()) {
			return "";
		}
		return value.asText();
	}

	private void require(String name, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " is required.");
		}
	}

	private String encodeServiceKey(String value) {
		if (value.contains("%")) {
			return value;
		}
		return encode(value);
	}

	private String encode(String value) {
		return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	private String trimTrailingSlash(String value) {
		if (value == null || value.isBlank()) {
			return "https://apis.data.go.kr/1613000/BusSttnInfoInqireService";
		}
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}
}
