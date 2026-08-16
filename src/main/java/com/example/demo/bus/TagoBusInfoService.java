package com.example.demo.bus;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class TagoBusInfoService {

	private static final String CITY_CODE_PATH = "/getCtyCodeList";
	private static final String ROUTE_NO_PATH = "/getRouteNoList";
	private static final String ROUTE_INFO_PATH = "/getRouteInfoIem";

	private final String baseUrl;
	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private final String configuredServiceKey;

	@Autowired
	public TagoBusInfoService(
		ObjectMapper objectMapper,
		@Value("${tago.route.base-url:https://apis.data.go.kr/1613000/BusRouteInfoInqireService}") String baseUrl,
		@Value("${tago.service-key:${TAGO_SERVICE_KEY:}}") String configuredServiceKey
	) {
		this(RestClient.create(), objectMapper, baseUrl, configuredServiceKey);
	}

	TagoBusInfoService(RestClient restClient, ObjectMapper objectMapper, String baseUrl, String configuredServiceKey) {
		this.baseUrl = trimTrailingSlash(baseUrl);
		this.restClient = restClient;
		this.objectMapper = objectMapper;
		this.configuredServiceKey = configuredServiceKey == null ? "" : configuredServiceKey;
	}

	public List<TagoCityCode> fetchCityCodes(String serviceKey) {
		return fetchItems(CITY_CODE_PATH, Map.of("numOfRows", "1000"), serviceKey).stream()
			.map(item -> new TagoCityCode(
				text(item, "citycode"),
				text(item, "cityname"),
				item.toString()
			))
			.filter(city -> !city.cityCode().isBlank())
			.sorted(Comparator.comparing(TagoCityCode::cityName))
			.toList();
	}

	public List<TagoRouteSummary> fetchRoutes(String cityCode, String serviceKey) {
		return fetchRoutes(cityCode, "", serviceKey);
	}

	public List<TagoRouteSummary> fetchRoutes(String cityCode, String routeNo, String serviceKey) {
		require("cityCode", cityCode);

		Map<String, String> params = routeNo == null || routeNo.isBlank()
			? Map.of("cityCode", cityCode, "numOfRows", "1000")
			: Map.of("cityCode", cityCode, "routeNo", normalizeRouteNo(routeNo), "numOfRows", "100");

		return fetchItems(ROUTE_NO_PATH, params, serviceKey).stream()
			.map(item -> new TagoRouteSummary(
				text(item, "routeid"),
				text(item, "routeno"),
				text(item, "routetp"),
				text(item, "startnodenm"),
				text(item, "endnodenm"),
				item.toString()
			))
			.filter(route -> !route.routeId().isBlank())
			.toList();
	}

	public BusRouteInfo fetchRouteInfo(String routeId, String cityCode, String serviceKey) {
		require("routeId", routeId);
		require("cityCode", cityCode);

		JsonNode item = fetchSingleItem(
			ROUTE_INFO_PATH,
			Map.of("cityCode", cityCode, "routeId", routeId),
			serviceKey
		);

		return toRouteInfo(item, baseUrl + ROUTE_INFO_PATH);
	}

	public BusRouteInfo fetchRouteInfoByRouteNo(String routeNo, String region, String serviceKey) {
		require("routeNo", routeNo);
		require("region", region);

		String cityCode = findCityCode(region, serviceKey);
		String routeId = findRouteId(cityCode, routeNo, serviceKey);
		return fetchRouteInfo(routeId, cityCode, serviceKey);
	}

	private String findCityCode(String region, String serviceKey) {
		String normalizedRegion = normalizeRegion(region);
		List<TagoCityCode> cities = fetchCityCodes(serviceKey);

		return cities.stream()
			.filter(city -> normalizeRegion(city.cityName()).equals(normalizedRegion))
			.findFirst()
			.or(() -> cities.stream()
				.filter(city -> normalizeRegion(city.cityName()).contains(normalizedRegion)
					|| normalizedRegion.contains(normalizeRegion(city.cityName())))
				.findFirst())
			.map(TagoCityCode::cityCode)
			.orElseThrow(() -> new IllegalStateException("No TAGO cityCode for region: " + region));
	}

	private String findRouteId(String cityCode, String routeNo, String serviceKey) {
		String normalizedRouteNo = normalizeRouteNo(routeNo);
		List<TagoRouteSummary> routes = fetchRoutes(cityCode, normalizedRouteNo, serviceKey);

		return routes.stream()
			.filter(route -> sameRouteNo(route.routeNo(), normalizedRouteNo))
			.findFirst()
			.map(TagoRouteSummary::routeId)
			.orElseThrow(() -> new IllegalStateException("No TAGO routeId for cityCode "
				+ cityCode + ", routeNo " + routeNo));
	}

	private BusRouteInfo toRouteInfo(JsonNode item, String url) {
		String weekdayHeadway = formatHeadway(text(item, "intervaltime"));
		String saturdayHeadway = formatHeadway(text(item, "intervalsattime"));
		String sundayHeadway = formatHeadway(text(item, "intervalsuntime"));

		return new BusRouteInfo(
			url,
			"TAGO",
			text(item, "routetp"),
			text(item, "startnodenm"),
			text(item, "endnodenm"),
			formatTime(text(item, "startvehicletime")),
			formatTime(text(item, "endvehicletime")),
			"",
			"",
			"",
			"",
			"",
			"",
			weekdayHeadway,
			"",
			saturdayHeadway,
			"",
			sundayHeadway,
			"",
			sundayHeadway,
			"",
			List.of(item.toString())
		);
	}

	private JsonNode fetchSingleItem(String path, Map<String, String> params, String serviceKey) {
		List<JsonNode> items = fetchItems(path, params, serviceKey);
		if (items.isEmpty()) {
			throw new IllegalStateException("No TAGO item in response.");
		}
		return items.get(0);
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
			throw new IllegalStateException("Failed to parse TAGO response.", ex);
		}
	}

	private void assertSuccess(JsonNode root) {
		JsonNode resultCode = root.findValue("resultCode");
		if (resultCode == null || resultCode.isNull() || "00".equals(resultCode.asText())) {
			return;
		}

		JsonNode resultMsg = root.findValue("resultMsg");
		throw new IllegalStateException("TAGO result " + resultCode.asText() + ": "
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
		throw new IllegalArgumentException("TAGO serviceKey is required. Pass serviceKey, tago.service-key, or TAGO_SERVICE_KEY.");
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

	private String formatHeadway(String value) {
		String digits = value == null ? "" : value.replaceAll("\\D", "");
		if (digits.isBlank()) {
			return value == null ? "" : value;
		}
		return digits + "-" + digits + " 분";
	}

	private String normalizeRouteNo(String routeNo) {
		return routeNo == null ? "" : routeNo.replaceAll("\\([^)]*\\)", "").trim();
	}

	private boolean sameRouteNo(String left, String right) {
		String normalizedLeft = normalizeRouteNo(left);
		String normalizedRight = normalizeRouteNo(right);
		return normalizedLeft.equals(normalizedRight)
			|| stripLeadingZero(normalizedLeft).equals(stripLeadingZero(normalizedRight));
	}

	private String stripLeadingZero(String routeNo) {
		return routeNo.replaceFirst("^0+(?=\\d)", "");
	}

	private String normalizeRegion(String region) {
		return (region == null ? "" : region)
			.replaceAll("\\s+", "")
			.replace("특별시", "")
			.replace("광역시", "")
			.replace("특례시", "")
			.replace("시", "")
			.replace("군", "")
			.replace("구", "");
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
			return "https://apis.data.go.kr/1613000/BusRouteInfoInqireService";
		}
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}
}
