package com.example.demo.bus;

import java.net.URI;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class GbisBusInfoService {

	private static final String ROUTE_INFO_URL =
		"https://apis.data.go.kr/6410000/busrouteservice/v2/getBusRouteInfoItemv2";

	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	public GbisBusInfoService(ObjectMapper objectMapper) {
		this.restClient = RestClient.create();
		this.objectMapper = objectMapper;
	}

	public BusRouteInfo fetchRouteInfo(String routeId, String serviceKey) {
		String response = restClient.get()
			.uri(URI.create(ROUTE_INFO_URL + "?serviceKey=" + serviceKey + "&format=json&routeId=" + routeId))
			.retrieve()
			.body(String.class);

		try {
			JsonNode root = objectMapper.readTree(response);
			JsonNode item = root.findValue("busRouteInfoItem");
			if (item == null || item.isMissingNode() || item.isNull()) {
				throw new IllegalStateException("No busRouteInfoItem in GBIS response.");
			}
			if (item.isArray()) {
				item = item.get(0);
			}

			return new BusRouteInfo(
				ROUTE_INFO_URL,
				"GBIS",
				text(item, "routeTypeName"),
				text(item, "startStationName"),
				text(item, "endStationName"),
				formatTime(text(item, "upFirstTime")),
				formatTime(text(item, "upLastTime")),
				formatTime(text(item, "satUpFirstTime")),
				formatTime(text(item, "satUpLastTime")),
				formatTime(text(item, "sunUpFirstTime")),
				formatTime(text(item, "sunUpLastTime")),
				formatTime(text(item, "sunUpFirstTime")),
				formatTime(text(item, "sunUpLastTime")),
				formatHeadway(text(item, "peekAlloc"), text(item, "nPeekAlloc")),
				"",
				formatHeadway(text(item, "satPeekAlloc"), text(item, "satNPeekAlloc")),
				"",
				formatHeadway(text(item, "sunPeekAlloc"), text(item, "sunNPeekAlloc")),
				"",
				formatHeadway(text(item, "sunPeekAlloc"), text(item, "sunNPeekAlloc")),
				"",
				java.util.List.of(response)
			);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to parse GBIS response.", ex);
		}
	}

	private String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull()) {
			return "";
		}
		return value.asText();
	}

	private String formatTime(String value) {
		String digits = value == null ? "" : value.replaceAll("\\D", "");
		if (digits.length() == 3) {
			digits = "0" + digits;
		}
		if (digits.length() == 4) {
			return digits.substring(0, 2) + ":" + digits.substring(2);
		}
		return value == null ? "" : value;
	}

	private String formatHeadway(String min, String max) {
		if (min == null || min.isBlank()) {
			return "";
		}
		if (max == null || max.isBlank()) {
			max = min;
		}
		return min.replaceAll("\\D", "") + "-" + max.replaceAll("\\D", "") + " 분";
	}
}
